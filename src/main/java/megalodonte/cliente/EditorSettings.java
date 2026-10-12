package megalodonte.cliente;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;

/** Shared editor preferences; project credentials remain supported for compatibility. */
final class EditorSettings {
    static final String DEFAULT_SOUNDS = "/home/eliezer/Desktop/backup/sounds";
    static final class Values {
        final String apiKey, soundsDirectory, basePrompt;
        Values(String apiKey, String soundsDirectory, String basePrompt) {
            this.apiKey=apiKey; this.soundsDirectory=soundsDirectory; this.basePrompt=basePrompt;
        }
        @Override public String toString() { return "Configurações do editor"; }
    }
    private final Path file;
    EditorSettings() { this(LastProjectStore.settingsDirectory().resolve("editor-settings.json")); }
    EditorSettings(Path file) { this.file=file.toAbsolutePath(); }
    boolean exists() { return Files.isRegularFile(file); }
    static String defaultPrompt() {
        try(var stream=EditorSettings.class.getResourceAsStream("/prompts/base-video.md")) {
            if(stream==null) throw new IllegalStateException("Prompt base padrão ausente.");
            return new String(stream.readAllBytes(),StandardCharsets.UTF_8);
        } catch(IOException e) { throw new UncheckedIOException(e); }
    }
    Values load() throws IOException {
        if(!exists()) return new Values("",DEFAULT_SOUNDS,defaultPrompt());
        try {
            var json=JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            return new Values(value(json,"openaiApiKey",""),value(json,"soundsDirectory",DEFAULT_SOUNDS),value(json,"basePrompt",defaultPrompt()));
        } catch(RuntimeException e) { throw new IOException("Arquivo de configurações inválido."); }
    }
    void save(Values values) throws IOException {
        Path directory=validateSounds(values.soundsDirectory);
        if(values.basePrompt.isBlank()) throw new IllegalArgumentException("Informe um prompt base.");
        Files.createDirectories(file.getParent());
        var json=new JsonObject(); json.addProperty("openaiApiKey",values.apiKey.strip());
        json.addProperty("soundsDirectory",directory.toString()); json.addProperty("basePrompt",values.basePrompt);
        Path temp=Files.createTempFile(file.getParent(),".editor-settings-",".tmp");
        try {
            try { Files.setPosixFilePermissions(temp,PosixFilePermissions.fromString("rw-------")); }
            catch(UnsupportedOperationException ignored) { }
            Files.writeString(temp,new GsonBuilder().setPrettyPrinting().create().toJson(json)+"\n");
            try { Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
            catch(AtomicMoveNotSupportedException e) { Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temp); }
    }
    static Path validateSounds(String value) {
        if(value.isBlank()) throw new IllegalArgumentException("Informe o diretório de sons.");
        Path folder=Path.of(value.strip()).toAbsolutePath().normalize();
        if(!Files.isDirectory(folder) || !Files.isReadable(folder)) throw new IllegalArgumentException("Escolha um diretório de sons existente e legível.");
        return folder;
    }
    private static String value(JsonObject json,String name,String fallback) {
        return json.has(name) && !json.get(name).isJsonNull() ? json.get(name).getAsString() : fallback;
    }
}
