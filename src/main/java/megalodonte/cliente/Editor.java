package megalodonte.cliente;
import megalodonte.script.ViewScript;
import javafx.stage.FileChooser;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import java.nio.file.*;
import java.util.*;
public final class Editor extends ViewScript {
    private VideoProject project = new VideoProject();
    private Path projectFile;
    private final AudioPreview audio = new AudioPreview();
    private volatile boolean disposed;
    private VideoExporter exporter;
    private Thread exportThread;
    @Override public void onDestroy() {
        disposed = true; if (playback != null) playback.stop(); playing = false;
        audio.close(); if (exporter != null) exporter.cancel(); if (exportThread != null) exportThread.interrupt();
    }
    private void audioError(String error) { if (!disposed) message(error); }
    private void refreshAudio() {
        set("narracaoNome",project.narration.file.isBlank() ? "Nenhuma narração anexada" : "Narração: " + Path.of(project.narration.file).getFileName());
        set("musicaNome",project.music.file.isBlank() ? "Nenhuma música anexada" : "Música: " + Path.of(project.music.file).getFileName());
        set("volumeNarracao",Double.toString(project.narration.volume*100)); set("volumeMusica",Double.toString(project.music.volume*100));
        set("semNarracao",project.narration.file.isBlank()); set("semMusica",project.music.file.isBlank());
    }
    public void attachNarration() { attachAudio(project.narration,"Anexar narração"); }
    public void attachMusic() { attachAudio(project.music,"Adicionar música de fundo"); }
    private void attachAudio(VideoProject.AudioTrack track,String title) {
        if (!readVolumes()) return;
        var chooser = new FileChooser(); chooser.setTitle(title);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Áudio", "*.mp3", "*.wav", "*.m4a", "*.aac", "*.flac", "*.ogg", "*.MP3", "*.WAV"));
        var file = chooser.showOpenDialog(null); if (file == null) return;
        if (!Files.isRegularFile(file.toPath())) { message("Arquivo de áudio não encontrado."); return; }
        pausePlayback(); track.file = file.getAbsolutePath(); refreshAudio(); message("Áudio anexado. Ajuste os volumes e clique em Aplicar volumes."); audio.load(project,this::audioError);
    }
    public void removeNarration() { pausePlayback(); project.narration.file=""; refreshAudio(); audio.load(project,this::audioError); }
    public void removeMusic() { pausePlayback(); project.music.file=""; refreshAudio(); audio.load(project,this::audioError); }
    private boolean readVolumes() {
        try {
            double narration = VideoProject.volumeFromPercent(text("volumeNarracao"));
            double music = VideoProject.volumeFromPercent(text("volumeMusica"));
            project.narration.volume = narration; project.music.volume = music; audio.volumes(project); return true;
        } catch (IllegalArgumentException e) { message("Informe volumes entre 0 e 100%."); return false; }
    }
    public void applyVolumes() { if (readVolumes()) message("Volumes aplicados à prévia e à exportação."); }
    public void exportVideo() {
        if (exporter != null || !readVolumes()) return;
        var chooser = new FileChooser(); chooser.setTitle("Exportar vídeo MP4 · YouTube 16:9"); chooser.setInitialFileName("meu-video.mp4");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Vídeo MP4", "*.mp4"));
        var file = chooser.showSaveDialog(null); if (file == null) return;
        Path target = file.toPath().toAbsolutePath();
        if (!target.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".mp4")) target=target.resolveSibling(target.getFileName()+".mp4");
        if (projectFile != null && target.equals(projectFile.toAbsolutePath())) { message("Escolha um arquivo de saída diferente do manifesto do projeto."); return; }
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
        set("tempo", String.format(Locale.ROOT,"%.1fs / %.1fs",playhead,project.duration()));
    }
    private void timelineChanged() { pausePlayback(); playhead = 0; audio.seek(0); }

    @Override public void onMount() { refreshAudio(); refresh(); }
    @Override public void onSubmit() {
        if (!project.clips.isEmpty() && !confirm("Recriar cenas? As imagens e os ajustes atuais serão substituídos.")) return;
        timelineChanged(); var previous=project; project = VideoProject.fromScript(text("roteiro"));
        project.narration.file=previous.narration.file; project.narration.volume=previous.narration.volume;
        project.music.file=previous.music.file; project.music.volume=previous.music.volume; set("selecionada", project.clips.isEmpty() ? -1 : 0); refresh();
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
        set("timeline", project.timeline()); set("resumo", project.clips.size() + " cenas · " + String.format(Locale.ROOT, "%.1f segundos", project.duration())); refreshSelection();
    }
    private void refreshSelection() {
        var clip = selected(); set("semSelecao", clip == null); set("imagem", clip == null ? "" : clip.image);
        set("cenaTexto", clip == null ? "Nenhuma cena selecionada" : "Cena " + (index() + 1) + " · " + (clip.text.length() > 160 ? clip.text.substring(0,160) + "…" : clip.text));
        set("duracao", clip == null ? "5" : Double.toString(clip.duration));
    }
    public void applyDuration() {
        var clip = selected(); if (clip == null) return;
        try { double value = Double.parseDouble(text("duracao").replace(',', '.'));
            if (!Double.isFinite(value) || value < .1 || value > 3600) throw new NumberFormatException();
            timelineChanged(); clip.duration = value; refresh(); message("Duração atualizada.");
        } catch (NumberFormatException e) { message("Informe uma duração entre 0,1 e 3600 segundos."); }
    }
    public void moveLeft() { move(-1); }
    public void moveRight() { move(1); }
    private void move(int delta) { int i = index(), target = i + delta;
        if (selected() == null || target < 0 || target >= project.clips.size()) return;
        timelineChanged(); Collections.swap(project.clips, i, target); set("selecionada", target); refresh();
    }
    public void removeScene() { if (selected() == null) return; timelineChanged(); int i = index(); project.clips.remove(i); set("selecionada", Math.min(i, project.clips.size() - 1)); refresh(); }
    public void importImages() {
        var chooser = imageChooser("Importar imagens (ordenadas pelo nome)"); var files = chooser.showOpenMultipleDialog(null); if (files == null) return;
        try {
            var paths = files.stream().map(java.io.File::toPath).sorted(Comparator.comparing(p -> p.getFileName().toString())).toList();
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
    public void replaceImage() {
        var clip = selected(); if (clip == null) return; var file = imageChooser("Imagem da cena selecionada").showOpenDialog(null); if (file == null) return;
        if (new Image(file.toURI().toString(), 64, 64, true, true, false).isError()) { message("Não foi possível ler a imagem."); return; }
        timelineChanged(); clip.image = file.getAbsolutePath(); refresh(); message("Imagem substituída.");
    }
    public void saveProject() {
        if (!readVolumes()) return;
        if (projectFile == null) { var chooser = projectChooser("Salvar projeto"); chooser.setInitialFileName("meu-video.mvideo.json");
            var file = chooser.showSaveDialog(null); if (file == null) return; projectFile = file.toPath(); }
        try { project.script = text("roteiro"); project.save(projectFile); message("Projeto salvo: " + projectFile); }
        catch (Exception e) { message("Não foi possível salvar: " + e.getMessage()); projectFile = null; }
    }
    public void openProject() {
        var file = projectChooser("Abrir projeto").showOpenDialog(null); if (file == null) return;
        try { var loaded = VideoProject.load(file.toPath());
            if ((!project.clips.isEmpty() || !text("roteiro").isBlank()) && !confirm("Abrir outro projeto e descartar alterações não salvas?")) return;
            timelineChanged(); project = loaded; refreshAudio(); audio.load(project,this::audioError); projectFile = file.toPath(); set("roteiro", project.script); set("selecionada", project.clips.isEmpty() ? -1 : 0); refresh();
            long missing = project.clips.stream().filter(c -> !c.image.isBlank() && !Files.isRegularFile(Path.of(c.image))).count();
            missing += java.util.stream.Stream.of(project.narration.file,project.music.file).filter(path -> !path.isBlank() && !Files.isRegularFile(Path.of(path))).count();
            message(missing > 0 ? "Projeto aberto. " + missing + " assets ausentes; anexe novamente as imagens ou os áudios." : "Projeto aberto.");
        } catch (Exception e) { message("Não foi possível abrir: " + e.getMessage()); }
    }
    private static FileChooser imageChooser(String title) { var chooser = new FileChooser(); chooser.setTitle(title); chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Imagens", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.PNG", "*.JPG", "*.JPEG")); return chooser; }
    private static FileChooser projectChooser(String title) { var chooser = new FileChooser(); chooser.setTitle(title); chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Projeto Megalodonte", "*.json")); return chooser; }
    private static boolean confirm(String message) { return new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.CANCEL, ButtonType.OK).showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK; }
}
