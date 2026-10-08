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
    @Override public void onMount() { refresh(); }
    @Override public void onSubmit() {
        if (!project.clips.isEmpty() && !confirm("Recriar cenas? As imagens e os ajustes atuais serão substituídos.")) return;
        project = VideoProject.fromScript(text("roteiro")); set("selecionada", project.clips.isEmpty() ? -1 : 0); refresh();
        message("Cenas criadas localmente, uma por parágrafo. Importe as imagens na ordem desejada.");
    }
    public void selectScene() { refreshSelection(); }
    private int index() { return (Integer) get("selecionada"); }
    private VideoProject.Clip selected() { int i = index(); return i >= 0 && i < project.clips.size() ? project.clips.get(i) : null; }
    private void refresh() {
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
            clip.duration = value; refresh(); message("Duração atualizada.");
        } catch (NumberFormatException e) { message("Informe uma duração entre 0,1 e 3600 segundos."); }
    }
    public void moveLeft() { move(-1); }
    public void moveRight() { move(1); }
    private void move(int delta) { int i = index(), target = i + delta;
        if (selected() == null || target < 0 || target >= project.clips.size()) return;
        Collections.swap(project.clips, i, target); set("selecionada", target); refresh();
    }
    public void removeScene() { if (selected() == null) return; int i = index(); project.clips.remove(i); set("selecionada", Math.min(i, project.clips.size() - 1)); refresh(); }
    public void importImages() {
        var chooser = imageChooser("Importar imagens (ordenadas pelo nome)"); var files = chooser.showOpenMultipleDialog(null); if (files == null) return;
        try {
            var paths = files.stream().map(java.io.File::toPath).sorted(Comparator.comparing(p -> p.getFileName().toString())).toList();
            for (Path path : paths) if (new Image(path.toUri().toString(), 64, 64, true, true, false).isError()) throw new IllegalArgumentException("Imagem inválida: " + path.getFileName());
            int cursor = 0;
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
        clip.image = file.getAbsolutePath(); refresh(); message("Imagem substituída.");
    }
    public void saveProject() {
        if (projectFile == null) { var chooser = projectChooser("Salvar projeto"); chooser.setInitialFileName("meu-video.mvideo.json");
            var file = chooser.showSaveDialog(null); if (file == null) return; projectFile = file.toPath(); }
        try { project.script = text("roteiro"); project.save(projectFile); message("Projeto salvo: " + projectFile); }
        catch (Exception e) { message("Não foi possível salvar: " + e.getMessage()); projectFile = null; }
    }
    public void openProject() {
        var file = projectChooser("Abrir projeto").showOpenDialog(null); if (file == null) return;
        try { var loaded = VideoProject.load(file.toPath());
            if ((!project.clips.isEmpty() || !text("roteiro").isBlank()) && !confirm("Abrir outro projeto e descartar alterações não salvas?")) return;
            project = loaded; projectFile = file.toPath(); set("roteiro", project.script); set("selecionada", project.clips.isEmpty() ? -1 : 0); refresh();
            long missing = project.clips.stream().filter(c -> !c.image.isBlank() && !Files.isRegularFile(Path.of(c.image))).count();
            message(missing > 0 ? "Projeto aberto. " + missing + " imagens ausentes; use Trocar imagem." : "Projeto aberto.");
        } catch (Exception e) { message("Não foi possível abrir: " + e.getMessage()); }
    }
    private static FileChooser imageChooser(String title) { var chooser = new FileChooser(); chooser.setTitle(title); chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Imagens", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.PNG", "*.JPG", "*.JPEG")); return chooser; }
    private static FileChooser projectChooser(String title) { var chooser = new FileChooser(); chooser.setTitle(title); chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Projeto Megalodonte", "*.json")); return chooser; }
    private static boolean confirm(String message) { return new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.CANCEL, ButtonType.OK).showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK; }
}
