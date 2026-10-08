package megalodonte.cliente;

import com.google.gson.*;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;

/** Project model is independent from the UI and future AI providers. */
public final class VideoProject {
    public static final class Clip {
        public String text;
        public String image = "";
        public double duration = 5;
        public Clip(String text) { this.text = text; }
    }
    public String script = "";
    public final List<Clip> clips = new ArrayList<>();
    public static VideoProject fromScript(String script) {
        var project = new VideoProject(); project.script = script;
        for (String paragraph : script.strip().split("\\R\\s*\\R")) if (!paragraph.isBlank()) project.clips.add(new Clip(paragraph.strip()));
        return project;
    }
    public double duration() { return clips.stream().mapToDouble(clip -> clip.duration).sum(); }
    public String timeline() {
        var items = new JsonArray();
        for (Clip clip : clips) {
            var item = new JsonObject(); item.addProperty("label", clip.text.length() > 70 ? clip.text.substring(0,70) + "…" : clip.text);
            item.addProperty("source", clip.image); item.addProperty("duration", clip.duration); items.add(item);
        }
        return items.toString();
    }
    /** Copies assets into the project folder; JSON is replaced only after assets are ready. */
    public void save(Path file) throws IOException {
        Path absolute = file.toAbsolutePath(); Files.createDirectories(absolute.getParent());
        Path assets = absolute.getParent().resolve(absolute.getFileName() + ".assets"); Files.createDirectories(assets);
        var root = new JsonObject(); root.addProperty("version", 1); root.addProperty("script", script); var scenes = new JsonArray();
        for (Clip clip : clips) {
            var scene = new JsonObject(); scene.addProperty("text", clip.text); scene.addProperty("duration", clip.duration);
            String image = "";
            if (!clip.image.isBlank()) {
                Path source = Path.of(clip.image);
                if (!Files.isRegularFile(source)) throw new IOException("Imagem ausente: " + source);
                // UUID prevents an interrupted save from overwriting assets referenced by the old manifest.
                Path target = assets.resolve(UUID.randomUUID() + "-" + source.getFileName()); Files.copy(source, target);
                image = absolute.getParent().relativize(target).toString();
            }
            scene.addProperty("image", image); scenes.add(scene);
        }
        root.add("clips", scenes);
        Path temporary = Files.createTempFile(absolute.getParent(), ".video-project-", ".tmp");
        try {
            Files.writeString(temporary, new GsonBuilder().setPrettyPrinting().create().toJson(root));
            try { Files.move(temporary, absolute, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }
    public static VideoProject load(Path file) throws IOException {
        try {
            var root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            if (root.get("version").getAsInt() != 1) throw new IllegalArgumentException("Versão incompatível");
            var project = new VideoProject(); project.script = root.get("script").getAsString();
            for (var element : root.getAsJsonArray("clips")) {
                var scene = element.getAsJsonObject(); var clip = new Clip(scene.get("text").getAsString());
                clip.duration = scene.get("duration").getAsDouble();
                if (!Double.isFinite(clip.duration) || clip.duration < .1 || clip.duration > 3600) throw new IllegalArgumentException("Duração inválida");
                String image = scene.get("image").getAsString();
                if (!image.isBlank()) {
                    Path parent = file.toAbsolutePath().getParent(); Path resolved = parent.resolve(image).normalize();
                    if (Path.of(image).isAbsolute() || !resolved.startsWith(parent)) throw new IllegalArgumentException("Caminho de imagem fora do projeto");
                    clip.image = resolved.toString();
                }
                project.clips.add(clip);
            }
            return project;
        } catch (RuntimeException e) { throw new IOException("Projeto inválido: " + e.getMessage(), e); }
    }
}
