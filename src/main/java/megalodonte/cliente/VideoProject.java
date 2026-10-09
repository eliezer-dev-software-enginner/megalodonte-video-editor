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
    public static final class AudioTrack {
        public String file = "";
        public double volume;
        public AudioTrack(double volume) { this.volume = volume; }
    }
    public final AudioTrack narration = new AudioTrack(1.0);
    public final AudioTrack music = new AudioTrack(.15);
    public static double volumeFromPercent(String text) {
        double percent = Double.parseDouble(text.strip().replace(',', '.'));
        if (!Double.isFinite(percent) || percent < 0 || percent > 100) throw new IllegalArgumentException("Volume deve estar entre 0 e 100%.");
        return percent / 100;
    }
    public VideoProject snapshot() {
        var copy = new VideoProject(); copy.script = script;
        copy.narration.file = narration.file; copy.narration.volume = narration.volume;
        copy.music.file = music.file; copy.music.volume = music.volume;
        for (Clip clip : clips) { var next = new Clip(clip.text); next.image = clip.image; next.duration = clip.duration; copy.clips.add(next); }
        return copy;
    }
    public static final String ASPECT_RATIO = "16:9";
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
        var root = new JsonObject(); root.addProperty("version", 1); root.addProperty("aspectRatio",ASPECT_RATIO); root.addProperty("script", script); var scenes = new JsonArray();
        for (Clip clip : clips) {
            var scene = new JsonObject(); scene.addProperty("text", clip.text); scene.addProperty("duration", clip.duration);
            String image = copyAsset(clip.image, absolute, assets);
            scene.addProperty("image", image); scenes.add(scene);
        }
        root.add("clips", scenes);
        var audio = new JsonObject();
        audio.add("narration", audioJson(narration,absolute,assets));
        audio.add("music", audioJson(music,absolute,assets)); root.add("audio", audio);
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
            if (root.has("aspectRatio") && !ASPECT_RATIO.equals(root.get("aspectRatio").getAsString())) throw new IllegalArgumentException("Somente vídeos horizontais 16:9 são suportados");
            var project = new VideoProject(); project.script = root.get("script").getAsString();
            for (var element : root.getAsJsonArray("clips")) {
                var scene = element.getAsJsonObject(); var clip = new Clip(scene.get("text").getAsString());
                clip.duration = scene.get("duration").getAsDouble();
                if (!Double.isFinite(clip.duration) || clip.duration < .1 || clip.duration > 3600) throw new IllegalArgumentException("Duração inválida");
                String image = scene.get("image").getAsString();
                clip.image = resolveAsset(image,file);
                project.clips.add(clip);
            }
            if (root.has("audio")) {
                var audio = root.getAsJsonObject("audio");
                readAudio(audio.getAsJsonObject("narration"),project.narration,file);
                readAudio(audio.getAsJsonObject("music"),project.music,file);
            }
            return project;
        } catch (RuntimeException e) { throw new IOException("Projeto inválido: " + e.getMessage(), e); }
    }
    private static String copyAsset(String sourceName, Path manifest, Path assets) throws IOException {
        if (sourceName.isBlank()) return "";
        Path source = Path.of(sourceName);
        if (!Files.isRegularFile(source)) throw new IOException("Asset ausente: " + source);
        Path target = assets.resolve(UUID.randomUUID() + "-" + source.getFileName()); Files.copy(source,target);
        return manifest.getParent().relativize(target).toString();
    }
    private static JsonObject audioJson(AudioTrack track, Path manifest, Path assets) throws IOException {
        validateVolume(track.volume);
        var json = new JsonObject(); json.addProperty("file",copyAsset(track.file,manifest,assets)); json.addProperty("volume",track.volume); return json;
    }
    private static void readAudio(JsonObject json, AudioTrack track, Path manifest) {
        if (json == null) return;
        track.volume = json.get("volume").getAsDouble(); validateVolume(track.volume);
        track.file = resolveAsset(json.get("file").getAsString(),manifest);
    }
    private static void validateVolume(double volume) {
        if (!Double.isFinite(volume) || volume < 0 || volume > 1) throw new IllegalArgumentException("Volume inválido");
    }
    private static String resolveAsset(String name, Path manifest) {
        if (name.isBlank()) return "";
        Path parent = manifest.toAbsolutePath().getParent(); Path resolved = parent.resolve(name).normalize();
        if (Path.of(name).isAbsolute() || !resolved.startsWith(parent)) throw new IllegalArgumentException("Caminho de asset fora do projeto");
        return resolved.toString();
    }

}
