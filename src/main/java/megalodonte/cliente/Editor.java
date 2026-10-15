package megalodonte.cliente;
import megalodonte.script.ViewScript;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import megalodonte.components.ImagePreview;
import java.nio.file.*;
import java.util.*;
public final class Editor extends ViewScript {
    private VideoProject project = new VideoProject();
    private Path projectFile;
    private String projectRevision;
    private final EditorSettings settingsStore=new EditorSettings();
    private EditorSettings.Values settings=new EditorSettings.Values("",EditorSettings.DEFAULT_SOUNDS,EditorSettings.defaultPrompt());
    private SettingsWindow settingsWindow;
    private boolean settingsLoaded;
    public void openSettings() {
        if(generationBusy()) return;
        pausePlayback();
        if(settingsWindow!=null && settingsWindow.showing()) { settingsWindow.focus();return; }
        settingsWindow=new SettingsWindow(owner(),new EditorSettings.Values(text("openaiKey"),settings.soundsDirectory,settings.basePrompt),values -> {
            settingsStore.save(values);settings=values;settingsLoaded=true;set("openaiKey",values.apiKey);project.openaiApiKey=values.apiKey;
            message("Configurações salvas. Prompt base e biblioteca de sons disponíveis para a geração de vídeo.");
        });
        settingsWindow.show();
    }
    private final FileDialogs dialogs = new FileDialogs();
    private final LastProjectStore lastProject = new LastProjectStore();
    private final AudioPreview audio = new AudioPreview();
    private volatile boolean disposed;
    private VideoExporter exporter;
    private Thread exportThread;
    private ImageGenerationJob imageJob;
    private Thread imageThread;
    public void generateImages() {
        if(imageJob!=null) return;
        OpenAiImageClient client;
        try { ImageGenerationJob.pending(project); client=OpenAiImageClient.fromSessionKey(text("openaiKey")); }
        catch(Exception e) { message(e.getMessage()); return; }
        if(projectFile==null) { message("Salve o projeto antes de gerar imagens para guardar os PNGs junto aos assets."); return; }
        pausePlayback(); project.script=text("roteiro"); project.openaiApiKey=text("openaiKey").strip();
        var snapshot=project.snapshot(); var targetProject=project;
        var job=new ImageGenerationJob(client); imageJob=job;set("gerandoImagens",true);set("iaStatus","Preparando imagens…");
        Path assets=projectFile.getParent().resolve(projectFile.getFileName()+".assets").resolve("generated");
        imageThread=Thread.ofPlatform().daemon(true).name("openai-images").start(()-> {
            String outcome;
            try {
                job.run(snapshot,assets,(index,file)-> {
                    var applied=new java.util.concurrent.CompletableFuture<Void>();
                    javafx.application.Platform.runLater(()-> {
                        if(disposed || imageJob!=job || job.cancelled() || project!=targetProject) { applied.completeExceptionally(new java.util.concurrent.CancellationException());return; }
                        project.clips.get(index).image=file.toString(); refresh();
                        try { project.save(projectFile,projectRevision);projectRevision=VideoProject.revision(projectFile);applied.complete(null); }
                        catch(Exception e) { applied.completeExceptionally(e); }
                    });
                    try { applied.get(); }
                    catch(InterruptedException e) { Thread.currentThread().interrupt();throw new java.util.concurrent.CancellationException(); }
                    catch(java.util.concurrent.ExecutionException e) { throw new IllegalStateException("Imagem anexada, mas não foi possível atualizar o manifesto.",e.getCause()); }
                },progress->javafx.application.Platform.runLater(()-> { if(!disposed && imageJob==job) set("iaStatus",progress); }));
                outcome="Imagens geradas e anexadas às cenas em ordem.";
            } catch(java.util.concurrent.CancellationException | InterruptedException e) { outcome="Geração cancelada. Imagens já anexadas foram mantidas."; }
            catch(Exception e) { outcome="A geração parou: "+e.getMessage()+". As imagens já anexadas foram mantidas."; }
            final String result=outcome;
            javafx.application.Platform.runLater(()-> {
                if(!disposed && imageJob==job) { imageJob=null;imageThread=null;set("gerandoImagens",false);set("iaStatus",result);message(result); }
            });
        });
    }
    public void cancelImages() { if(imageJob!=null) { imageJob.cancel();if(imageThread!=null) imageThread.interrupt();set("iaStatus","Cancelando geração…"); } }
    private boolean generationBusy() {
        if(imageJob==null) return false;
        message("Aguarde ou cancele a geração de imagens antes de alterar o projeto.");return true;
    }

    @Override public void onDestroy() {
        if(settingsWindow!=null) settingsWindow.close();
        disposed = true; if(imageJob!=null) imageJob.cancel();if(imageThread!=null) imageThread.interrupt(); if (playback != null) playback.stop(); playing = false;
        audio.close(); if (exporter != null) exporter.cancel(); if (exportThread != null) exportThread.interrupt();
    }
    private void audioError(String error) { if (!disposed) message(error); }
    private void refreshAudio() {
        set("narracaoNome",project.narration.file.isBlank() ? "Nenhuma narração anexada" : "Narração: " + Path.of(project.narration.file).getFileName());
        set("musicaNome",project.music.file.isBlank() ? "Nenhuma música anexada" : "Música: " + Path.of(project.music.file).getFileName());
        set("volumeNarracao",Double.toString(project.narration.volume*100)); set("volumeMusica",Double.toString(project.music.volume*100));
        set("semNarracao",project.narration.file.isBlank()); set("semMusica",project.music.file.isBlank());
    }
    private VideoProject.SoundEffect selectedEffect() {
        int i=(Integer)get("efeitoSelecionado");return i>=0 && i<project.effects.size() ? project.effects.get(i) : null;
    }
    private void refreshEffects() {
        set("efeitosTimeline",project.effectsTimeline());
        var effect=selectedEffect();set("semEfeito",effect==null);
        set("efeitoNome",effect==null ? "Selecione um som para ajustar" : Path.of(effect.file).getFileName().toString());
        set("efeitoInicio",Double.toString(effect==null ? playhead : effect.start));
        set("efeitoRecorte",Double.toString(effect==null ? 0 : effect.offset));
        set("efeitoDuracao",Double.toString(effect==null ? 3 : effect.duration));
        set("efeitoVolume",Double.toString(effect==null ? 15 : effect.volume*100));
        set("efeitoFadeIn",Double.toString(effect==null ? .1 : effect.fadeIn));
        set("efeitoFadeOut",Double.toString(effect==null ? .2 : effect.fadeOut));
    }
    public void selectEffect() {
        pausePlayback();var effect=selectedEffect();if(effect!=null) {playhead=Math.min(effect.start,project.duration());audio.seek(playhead);updatePlayhead();}refreshEffects();
    }
    public void addEffect() {
        if(generationBusy()) return;
        if(project.clips.isEmpty()) {message("Crie cenas antes de adicionar efeitos.");return;}
        var files=chooseFiles(FileDialogs.Kind.EFFECT);if(files.isEmpty())return;
        addEffectFile(files.getFirst());
    }
    void addEffectFile(Path path) {
        pausePlayback();var effect=new VideoProject.SoundEffect();effect.file=path.toAbsolutePath().toString();
        effect.start=Math.min(playhead,Math.max(0,project.duration()-.1));effect.duration=Math.min(3,project.duration()-effect.start);
        effect.fadeIn=Math.min(.1,effect.duration/4);effect.fadeOut=Math.min(.2,effect.duration/4);
        project.effects.add(effect);set("efeitoSelecionado",project.effects.size()-1);refreshEffects();audio.load(project,this::audioError);
        message("Som adicionado na posição atual. Ajuste a duração e clique em Aplicar efeito.");
    }
    private double effectNumber(String state) {return Double.parseDouble(text(state).strip().replace(',','.'));}
    public void applyEffect() {
        if(generationBusy()) return;var current=selectedEffect();if(current==null)return;
        try {
            var value=current.copy();value.start=effectNumber("efeitoInicio");value.offset=effectNumber("efeitoRecorte");value.duration=effectNumber("efeitoDuracao");
            value.volume=VideoProject.volumeFromPercent(text("efeitoVolume"));value.fadeIn=effectNumber("efeitoFadeIn");value.fadeOut=effectNumber("efeitoFadeOut");value.validate();
            if(value.start>=project.duration())throw new IllegalArgumentException("O efeito deve começar dentro do vídeo.");
            pausePlayback();project.effects.set((Integer)get("efeitoSelecionado"),value);refreshEffects();audio.load(project,this::audioError);
            message("Efeito atualizado. O áudio é limitado ao fim do vídeo e do arquivo de origem.");
        } catch(IllegalArgumentException e) { message("Não foi possível aplicar o efeito: "+e.getMessage()); }
    }
    public void removeEffect() {
        if(generationBusy() || selectedEffect()==null)return;
        pausePlayback();int i=(Integer)get("efeitoSelecionado");project.effects.remove(i);set("efeitoSelecionado",Math.min(i,project.effects.size()-1));
        refreshEffects();audio.load(project,this::audioError);message("Efeito removido.");
    }
    public void attachNarration() { if(generationBusy()) return; attachAudio(project.narration); }
    public void attachMusic() { if(generationBusy()) return; attachAudio(project.music); }
    private void attachAudio(VideoProject.AudioTrack track) {
        if (!readVolumes()) return;
        var kind=track==project.narration ? FileDialogs.Kind.NARRATION : FileDialogs.Kind.MUSIC;
        var files=chooseFiles(kind); if(files.isEmpty()) return;
        java.io.File file=files.getFirst().toFile();
        pausePlayback(); track.file = file.getAbsolutePath(); refreshAudio(); message("Áudio anexado. Ajuste os volumes e clique em Aplicar volumes."); audio.load(project,this::audioError);
    }
    public void removeNarration() { if(generationBusy()) return; pausePlayback(); project.narration.file=""; refreshAudio(); audio.load(project,this::audioError); }
    public void removeMusic() { if(generationBusy()) return; pausePlayback(); project.music.file=""; refreshAudio(); audio.load(project,this::audioError); }
    private boolean readVolumes() {
        try {
            double narration = VideoProject.volumeFromPercent(text("volumeNarracao"));
            double music = VideoProject.volumeFromPercent(text("volumeMusica"));
            project.narration.volume = narration; project.music.volume = music; audio.volumes(project); return true;
        } catch (IllegalArgumentException e) { message("Informe volumes entre 0 e 100%."); return false; }
    }
    public void applyVolumes() { if (readVolumes()) message("Volumes aplicados à prévia e à exportação."); }
    public void exportVideo() { if(generationBusy()) return;
        if (exporter != null || !readVolumes()) return;
        pausePlayback();
        var files=chooseFiles(FileDialogs.Kind.VIDEO); if(files.isEmpty()) return;
        Path target=files.getFirst();
        try { FileSelections.protectAssets(project,target,projectFile,true); }
        catch (Exception e) { message(e.getMessage()); return; }
        if (Files.exists(target) && !confirm("Substituir o vídeo existente? " + target.getFileName())) return;
        pausePlayback(); project.script=text("roteiro"); var snapshot=project.snapshot(); final Path output=target;
        exporter=new VideoExporter(); var job=exporter; set("exportando",true); set("exportStatus","Preparando exportação…");
        exportThread=Thread.ofPlatform().daemon(true).name("megalodonte-export").start(() -> {
            String result;
            try { job.export(snapshot,output,progress -> javafx.application.Platform.runLater(() -> { if (!disposed) set("exportStatus",progress); })); result="Vídeo exportado: "+output; }
            catch(java.util.concurrent.CancellationException e) { result="Exportação cancelada."; }
            catch(Exception e) { result="Não foi possível exportar: "+e.getMessage(); }
            final String outcome=result;
            javafx.application.Platform.runLater(() -> { if (!disposed) { exporter=null; exportThread=null; set("exportando",false); set("exportStatus",outcome); message(outcome); } });
        });
    }
    public void cancelExport() { if (exporter != null) { exporter.cancel(); if (exportThread != null) exportThread.interrupt(); set("exportStatus","Cancelando exportação…"); } }

    private javafx.animation.AnimationTimer playback;
    private boolean playing;
    private double playhead;
    private long lastFrame;
    public void togglePlay() {
        if (playing) { pausePlayback(); return; }
        if (project.clips.isEmpty() || !readVolumes()) return;
        if (playhead >= project.duration()) playhead = 0;
        playing = true; lastFrame = 0; set("playLabel", "Ⅱ Pausar");
        if (playback == null) playback = new javafx.animation.AnimationTimer() {
            @Override public void handle(long now) {
                if (lastFrame != 0) advancePlayback((now-lastFrame)/1_000_000_000.0);
                lastFrame = now;
            }
        };
        playback.start(); audio.play(playhead); updatePlayhead();
    }
    public void rewind() { pausePlayback(); playhead = 0; audio.seek(0); updatePlayhead(); }
    private void pausePlayback() { audio.pause(); playing = false; if (playback != null) playback.stop(); lastFrame = 0; set("playLabel", "▶ Play"); }
    // Deterministic step used by the animation clock and playback tests.
    void advancePlayback(double seconds) {
        if (!playing) return;
        playhead = Math.min(project.duration(),playhead + Math.max(0,seconds));
        if (playhead >= project.duration()) pausePlayback();
        updatePlayhead();
    }
    private void updatePlayhead() {
        audio.position(playhead);
        double start = 0; int active = -1;
        for (int i=0;i<project.clips.size();i++) {
            active=i; start += project.clips.get(i).duration;
            if (playhead < start) break;
        }
        if (active != index()) { set("selecionada",active); refreshSelection(); }
        refreshVisualPreview();
        set("tempo", String.format(Locale.ROOT,"%.1fs / %.1fs",playhead,project.duration()));
    }
    private void refreshVisualPreview() {
        int selected=index();var clip=selected();if(clip==null)return;
        double start=0;for(int i=0;i<selected;i++)start+=project.clips.get(i).duration;
        ((ImagePreview)component("preview")).setVisualEffect(clip.visualEffect,selected,playhead-start);
    }
    private void timelineChanged() { pausePlayback(); playhead = 0; audio.seek(0); }

    @Override public void onMount() {
        try { settings=settingsStore.load();settingsLoaded=settingsStore.exists();set("openaiKey",settings.apiKey); }
        catch(java.io.IOException e) { message("Não foi possível carregar as configurações: "+e.getMessage()); }
        refreshAudio(); refresh();
        try {
            String initial=System.getProperty("megalodonte.editor.initialProject","");
            var last=initial.isBlank() ? lastProject.lastSaved() : Optional.of(Path.of(initial));
            if (last.isPresent()) {
                Path file=last.get();
                var loaded=VideoProject.load(file); applyProject(loaded,file);
                message((initial.isBlank() ? "Último projeto salvo restaurado: " : "Projeto aberto: ") + file + missingAssetsMessage());
            }
        } catch (Exception e) { message("Não foi possível restaurar o último projeto salvo. O editor abriu vazio: " + e.getMessage()); }
    }
    @Override public void onSubmit() { if(generationBusy()) return;
        if (!project.clips.isEmpty() && !confirm("Recriar cenas? As imagens e os ajustes atuais serão substituídos.")) return;
        timelineChanged(); var previous=project; project = VideoProject.fromScript(text("roteiro")); project.openaiApiKey=text("openaiKey").strip(); project.animateImages=previous.animateImages;
        project.narration.file=previous.narration.file; project.narration.volume=previous.narration.volume;
        project.music.file=previous.music.file; project.music.volume=previous.music.volume;project.effects.addAll(previous.effects);audio.load(project,this::audioError); set("selecionada", project.clips.isEmpty() ? -1 : 0); refresh();
        message("Cenas criadas localmente, uma por parágrafo. Importe as imagens na ordem desejada.");
    }
    public void selectScene() {
        pausePlayback(); playhead = 0;
        for (int i=0;i<index();i++) playhead += project.clips.get(i).duration;
        audio.seek(playhead); refreshSelection(); set("tempo",String.format(Locale.ROOT,"%.1fs / %.1fs",playhead,project.duration()));
    }
    private int index() { return (Integer) get("selecionada"); }
    private VideoProject.Clip selected() { int i = index(); return i >= 0 && i < project.clips.size() ? project.clips.get(i) : null; }
    private void refresh() {
        set("semCenas",project.clips.isEmpty());
        set("tempo",String.format(Locale.ROOT,"%.1fs / %.1fs",playhead,project.duration()));
        refreshEffects();set("timeline", project.timeline()); set("resumo", project.clips.size() + " cenas · " + String.format(Locale.ROOT, "%.1f segundos", project.duration())); refreshSelection();
    }
    private void refreshSelection() {
        var clip = selected(); set("semSelecao", clip == null); set("imagem", clip == null ? "" : clip.image);
        set("cenaTexto", clip == null ? "Nenhuma cena selecionada" : "Cena " + (index() + 1) + (clip.visualEffect.isBlank() ? "" : clip.visualEffect.equals("shake") ? " · ✦ Tremor leve" : " · ✦ Partículas") + " · " + (clip.text.length() > 160 ? clip.text.substring(0,160) + "…" : clip.text));
        set("duracao", clip == null ? "5" : Double.toString(clip.duration));
        if(clip!=null)refreshVisualPreview();
        else ((ImagePreview)component("preview")).setVisualEffect("",0,0);
    }
    public void applyDuration() { if(generationBusy()) return;
        var clip = selected(); if (clip == null) return;
        try { double value = Double.parseDouble(text("duracao").replace(',', '.'));
            if (!Double.isFinite(value) || value < .1 || value > 3600) throw new NumberFormatException();
            timelineChanged(); clip.duration = value; refresh(); message("Duração atualizada.");
        } catch (NumberFormatException e) { message("Informe uma duração entre 0,1 e 3600 segundos."); }
    }
    public void moveLeft() { move(-1); }
    public void moveRight() { move(1); }
    private void move(int delta) { if(generationBusy()) return; int i = index(), target = i + delta;
        if (selected() == null || target < 0 || target >= project.clips.size()) return;
        timelineChanged(); Collections.swap(project.clips, i, target); set("selecionada", target); refresh();
    }
    public void removeScene() { if(generationBusy()) return; if (selected() == null) return; timelineChanged(); int i = index(); project.clips.remove(i); set("selecionada", Math.min(i, project.clips.size() - 1)); refresh(); }
    public void importImages() { if(generationBusy()) return;
        var files=chooseFiles(FileDialogs.Kind.IMAGES); if(files.isEmpty()) return;
        try {
            var paths=files.stream().distinct().sorted(Comparator.comparing(p -> p.getFileName().toString(),String.CASE_INSENSITIVE_ORDER)).toList();
            for (Path path : paths) if (new Image(path.toUri().toString(), 64, 64, true, true, false).isError()) throw new IllegalArgumentException("Imagem inválida: " + path.getFileName());
            timelineChanged(); int cursor = 0;
            for (Path path : paths) {
                while (cursor < project.clips.size() && !project.clips.get(cursor).image.isBlank()) cursor++;
                if (cursor == project.clips.size()) project.clips.add(new VideoProject.Clip(path.getFileName().toString()));
                project.clips.get(cursor++).image = path.toAbsolutePath().toString();
            }
            if (selected() == null) set("selecionada", 0); refresh(); message(paths.size() + " imagens anexadas em ordem de nome.");
        } catch (RuntimeException e) { message(e.getMessage()); }
    }
    public void replaceImage() { if(generationBusy()) return;
        var clip=selected(); if(clip==null) return;
        var files=chooseFiles(FileDialogs.Kind.IMAGE); if(files.isEmpty()) return;
        Path file=files.getFirst();
        try {
            if(new Image(file.toUri().toString(),64,64,true,true,false).isError()) throw new IllegalArgumentException("Não foi possível ler a imagem.");
            timelineChanged(); clip.image=file.toString(); refresh(); message("Imagem substituída.");
        } catch(RuntimeException e) { message("Não foi possível trocar a imagem: " + e.getMessage()); }
    }
    public void saveProject() { if(generationBusy()) return;
        if(!readVolumes()) return;
        Path target=projectFile;
        if(target==null) {
            var files=chooseFiles(FileDialogs.Kind.PROJECT_SAVE); if(files.isEmpty()) return;
            target=files.getFirst();
            try { FileSelections.protectAssets(project,target,projectFile,false); }
            catch(Exception e) { message(e.getMessage()); return; }
            if(Files.exists(target) && !confirm("Substituir o projeto existente? " + target.getFileName())) return;
        }
        try {
            FileSelections.protectAssets(project,target,projectFile,false);
            project.script=text("roteiro"); project.openaiApiKey=text("openaiKey").strip(); project.save(target,projectFile==null ? null : projectRevision); projectFile=target;projectRevision=VideoProject.revision(target);
        } catch(Exception e) { message("Não foi possível salvar: " + e.getMessage()); return; }
        try { lastProject.remember(projectFile); message("Projeto salvo: " + projectFile); }
        catch(Exception e) { message("Projeto salvo: " + projectFile + ". Não foi possível guardar o caminho para a próxima inicialização: " + e.getMessage()); }
    }
    public void openProject() { if(generationBusy()) return;
        var files=chooseFiles(FileDialogs.Kind.PROJECT_OPEN); if(files.isEmpty()) return;
        Path file=files.getFirst();
        try { var loaded = VideoProject.load(file);
            if ((!project.clips.isEmpty() || !text("roteiro").isBlank() || !project.narration.file.isBlank() || !project.music.file.isBlank()) && !confirm("Abrir outro projeto e descartar alterações não salvas?")) return;
            applyProject(loaded,file); message("Projeto aberto." + missingAssetsMessage());
        } catch (Exception e) { message("Não foi possível abrir: " + e.getMessage()); }
    }
    private void applyProject(VideoProject loaded,Path file) throws java.io.IOException {
        timelineChanged(); project=loaded; projectFile=file.toAbsolutePath().normalize();
        projectRevision=VideoProject.revision(projectFile);
        refreshAudio(); audio.load(project,this::audioError); set("roteiro",project.script); set("openaiKey",settingsLoaded ? settings.apiKey : project.openaiApiKey);
        set("efeitoSelecionado",project.effects.isEmpty() ? -1 : 0);set("selecionada",project.clips.isEmpty() ? -1 : 0); refresh();
    }
    private String missingAssetsMessage() {
        long missing=project.clips.stream().filter(c -> !c.image.isBlank() && !Files.isRegularFile(Path.of(c.image))).count();
        missing+=project.effects.stream().filter(e->!Files.isRegularFile(Path.of(e.file))).count();
        missing+=java.util.stream.Stream.of(project.narration.file,project.music.file).filter(path -> !path.isBlank() && !Files.isRegularFile(Path.of(path))).count();
        return missing>0 ? " " + missing + " assets ausentes; anexe novamente as imagens ou os áudios." : "";
    }
    private javafx.stage.Window owner() {
        var context=megalodonte.application.MegalodonteApp.getCurrentContext();
        return context==null ? null : context.javafxStage();
    }
    private List<Path> chooseFiles(FileDialogs.Kind kind) {
        pausePlayback();
        try {
            var selected=dialogs.choose(kind,owner(),kind==FileDialogs.Kind.EFFECT ? Path.of(settings.soundsDirectory) : projectFile==null ? null : projectFile.getParent());
            if(disposed) return List.of();
            var validated=new ArrayList<Path>();
            for(Path file:selected) validated.add(kind.save ? FileSelections.destination(file,kind) : FileSelections.readable(file,kind));
            if(!validated.isEmpty()) {
                try { dialogs.remember(kind,selected.getFirst().toAbsolutePath().normalize().getParent()); }
                catch(java.io.IOException e) { message("Arquivo selecionado, mas não foi possível guardar a pasta: " + e.getMessage()); }
            }
            return List.copyOf(validated);
        } catch(Exception e) { if(!disposed) message("Não foi possível selecionar o arquivo: " + e.getMessage()); return List.of(); }
    }
    private boolean confirm(String message) {
        var alert=new Alert(Alert.AlertType.CONFIRMATION,message,ButtonType.CANCEL,ButtonType.OK);
        var window=owner(); if(window!=null) alert.initOwner(window);
        return alert.showAndWait().orElse(ButtonType.CANCEL)==ButtonType.OK;
    }
}
