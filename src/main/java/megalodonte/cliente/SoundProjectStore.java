package megalodonte.cliente;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.HashMap;

/** Persists only newly selected sound assets, without copying the project's existing (potentially large) images. */
final class SoundProjectStore {
    private SoundProjectStore() { }

    static void saveAdditions(VideoProject project, Path manifest, String expectedRevision,
                              SoundApplicator.Result additions) throws IOException {
        Path absolute = manifest.toAbsolutePath().normalize();
        if (!expectedRevision.equals(VideoProject.revision(absolute)))
            throw new IOException("O projeto mudou no disco. Reabra a versão atual antes de aplicar os sons.");
        var root = JsonParser.parseString(Files.readString(absolute)).getAsJsonObject();
        var audio = root.getAsJsonObject("audio");
        if (audio == null) { audio = new JsonObject(); root.add("audio", audio); }
        Path assets = absolute.getParent().resolve(absolute.getFileName() + ".assets");
        Files.createDirectories(assets);
        if (additions.musicAdded()) audio.add("music", audioTrack(project.music.file, project.music.volume, absolute, assets));
        JsonArray effects = audio.has("effects") && audio.get("effects").isJsonArray()
                ? audio.getAsJsonArray("effects") : new JsonArray();
        var copiedEffects=new HashMap<String,String>();
        for (var effect : additions.effectsAdded()) {
            var json = new com.google.gson.Gson().toJsonTree(effect).getAsJsonObject();
            String saved=copiedEffects.get(effect.file);
            if(saved==null) { saved=copyNewAsset(effect.file,absolute,assets);copiedEffects.put(effect.file,saved); }
            json.addProperty("file",saved);
            effects.add(json);
        }
        if (!additions.effectsAdded().isEmpty() || !audio.has("effects")) audio.add("effects", effects);

        Path temporary = Files.createTempFile(absolute.getParent(), ".video-sounds-", ".tmp");
        try {
            Files.writeString(temporary, new GsonBuilder().setPrettyPrinting().create().toJson(root) + "\n");
            if (!expectedRevision.equals(VideoProject.revision(absolute)))
                throw new IOException("O projeto mudou durante a aplicação dos sons. Reabra a versão atual.");
            try { Files.move(temporary, absolute, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }

    private static JsonObject audioTrack(String source, double volume, Path manifest, Path assets) throws IOException {
        var track = new JsonObject();
        track.addProperty("file", copyNewAsset(source, manifest, assets));
        track.addProperty("volume", volume);
        return track;
    }

    private static String copyNewAsset(String source, Path manifest, Path assets) throws IOException {
        Path file = Path.of(source);
        if (!Files.isRegularFile(file) || !Files.isReadable(file)) throw new IOException("Som ausente ou ilegível: " + file);
        Path target = assets.resolve(UUID.randomUUID() + "-" + file.getFileName());
        Files.copy(file, target);
        return manifest.getParent().relativize(target).toString();
    }
}
