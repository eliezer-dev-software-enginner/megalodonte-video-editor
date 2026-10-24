package megalodonte.cliente;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;

/** Shared local editor preferences; credentials are never written to project manifests. */
final class EditorSettings {
    static final String DEFAULT_SOUNDS = "/home/eliezer/Desktop/backup/sounds";
    static final double DEFAULT_PAUSE_CUT_THRESHOLD = 1.0;
    static final class Values {
        final String apiKey, soundsDirectory, basePrompt;
        final double pauseCutThreshold;
        final String defaultVisualEffect;
        final double visualEffectStrength;
        final String whisperModel, whisperLanguage;
        final int whisperBeamSize, whisperCpuThreads;
        final boolean whisperVad;
        Values(String apiKey, String soundsDirectory, String basePrompt) {
            this(apiKey,soundsDirectory,basePrompt,DEFAULT_PAUSE_CUT_THRESHOLD);
        }
        Values(String apiKey, String soundsDirectory, String basePrompt,double pauseCutThreshold) {
            this(apiKey,soundsDirectory,basePrompt,pauseCutThreshold,"none",1.0);
        }
        Values(String apiKey, String soundsDirectory, String basePrompt,double pauseCutThreshold,
               String defaultVisualEffect,double visualEffectStrength) {
            this(apiKey,soundsDirectory,basePrompt,pauseCutThreshold,defaultVisualEffect,visualEffectStrength,"small","pt",5,4,true);
        }
        Values(String apiKey, String soundsDirectory, String basePrompt,double pauseCutThreshold,
               String defaultVisualEffect,double visualEffectStrength,String whisperModel,String whisperLanguage,
               int whisperBeamSize,int whisperCpuThreads,boolean whisperVad) {
            this.apiKey=apiKey; this.soundsDirectory=soundsDirectory; this.basePrompt=basePrompt;
            this.pauseCutThreshold=pauseCutThreshold;
            this.defaultVisualEffect=defaultVisualEffect;
            this.visualEffectStrength=visualEffectStrength;
            this.whisperModel=whisperModel;this.whisperLanguage=whisperLanguage;
            this.whisperBeamSize=whisperBeamSize;this.whisperCpuThreads=whisperCpuThreads;this.whisperVad=whisperVad;
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
            double threshold=json.has("pauseCutThreshold") ? json.get("pauseCutThreshold").getAsDouble() : DEFAULT_PAUSE_CUT_THRESHOLD;
            validatePauseCutThreshold(threshold);
            String effect=value(json,"defaultVisualEffect","none");
            validateVisualEffect(effect);
            double strength=json.has("visualEffectStrength") ? json.get("visualEffectStrength").getAsDouble() : 1.0;
            VideoProject.validateVisualEffectStrength(strength);
            String model=value(json,"whisperModel","small"),language=value(json,"whisperLanguage","pt");
            int beam=json.has("whisperBeamSize")?json.get("whisperBeamSize").getAsInt():5;
            int threads=json.has("whisperCpuThreads")?json.get("whisperCpuThreads").getAsInt():4;
            boolean vad=json.has("whisperVad")?json.get("whisperVad").getAsBoolean():true;
            validateWhisper(model,language,beam,threads);
            return new Values(value(json,"openaiApiKey",""),value(json,"soundsDirectory",DEFAULT_SOUNDS),value(json,"basePrompt",defaultPrompt()),threshold,effect,strength,model,language,beam,threads,vad);
        } catch(RuntimeException e) { throw new IOException("Arquivo de configurações inválido."); }
    }
    void save(Values values) throws IOException {
        Path directory=validateSounds(values.soundsDirectory);
        if(values.basePrompt.isBlank()) throw new IllegalArgumentException("Informe um prompt base.");
        validatePauseCutThreshold(values.pauseCutThreshold);
        validateVisualEffect(values.defaultVisualEffect);
        VideoProject.validateVisualEffectStrength(values.visualEffectStrength);
        validateWhisper(values.whisperModel,values.whisperLanguage,values.whisperBeamSize,values.whisperCpuThreads);
        write(new Values(values.apiKey,directory.toString(),values.basePrompt,values.pauseCutThreshold,
                values.defaultVisualEffect,values.visualEffectStrength,values.whisperModel,values.whisperLanguage,
                values.whisperBeamSize,values.whisperCpuThreads,values.whisperVad));
    }
    /** Migrate a legacy project key without requiring its old sound folder to exist. */
    void saveApiKey(String apiKey) throws IOException {
        var current=load();write(new Values(apiKey,current.soundsDirectory,current.basePrompt,current.pauseCutThreshold,
                current.defaultVisualEffect,current.visualEffectStrength,current.whisperModel,current.whisperLanguage,
                current.whisperBeamSize,current.whisperCpuThreads,current.whisperVad));
    }
    private void write(Values values) throws IOException {
        Files.createDirectories(file.getParent());
        var json=new JsonObject(); json.addProperty("openaiApiKey",values.apiKey.strip());
        json.addProperty("soundsDirectory",values.soundsDirectory); json.addProperty("basePrompt",values.basePrompt);
        json.addProperty("pauseCutThreshold",values.pauseCutThreshold);
        json.addProperty("defaultVisualEffect",values.defaultVisualEffect);
        json.addProperty("visualEffectStrength",values.visualEffectStrength);
        json.addProperty("whisperModel",values.whisperModel);json.addProperty("whisperLanguage",values.whisperLanguage);
        json.addProperty("whisperBeamSize",values.whisperBeamSize);json.addProperty("whisperCpuThreads",values.whisperCpuThreads);
        json.addProperty("whisperVad",values.whisperVad);
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
    static double parsePauseCutThreshold(String value) {
        try { return validatePauseCutThreshold(Double.parseDouble(value.strip().replace(',', '.'))); }
        catch(NumberFormatException e) { throw new IllegalArgumentException("Informe a pausa mínima em segundos (0,1 a 30)."); }
    }
    static double validatePauseCutThreshold(double value) {
        if(!Double.isFinite(value) || value<.1 || value>30)
            throw new IllegalArgumentException("A pausa mínima deve ficar entre 0,1 e 30 segundos.");
        return value;
    }
    static String validateVisualEffect(String value) {
        if(!java.util.Set.of("none","smooth","particles","shake").contains(value))
            throw new IllegalArgumentException("Escolha um efeito visual válido.");
        return value;
    }
    static double parseVisualEffectStrength(String value) {
        try { return VideoProject.validateVisualEffectStrength(Double.parseDouble(value.strip().replace(',','.'))); }
        catch(NumberFormatException e) { throw new IllegalArgumentException("Informe intensidade visual entre 0,25 e 3."); }
    }
    static void validateWhisper(String model,String language,int beam,int threads) {
        if(!java.util.Set.of("tiny","base","small","medium","large-v3").contains(model))
            throw new IllegalArgumentException("Escolha um modelo Whisper válido.");
        if(!java.util.Set.of("pt","auto").contains(language))
            throw new IllegalArgumentException("Escolha português ou detecção automática de idioma.");
        if(beam<1 || beam>10) throw new IllegalArgumentException("A busca do Whisper deve ficar entre 1 e 10.");
        if(threads<1 || threads>16) throw new IllegalArgumentException("As threads do Whisper devem ficar entre 1 e 16.");
    }
    static int parseWhisperNumber(String value,String name,int min,int max) {
        try { int number=Integer.parseInt(value.strip());if(number>=min&&number<=max)return number; }
        catch(NumberFormatException ignored) { }
        throw new IllegalArgumentException(name+" deve ficar entre "+min+" e "+max+".");
    }
    private static String value(JsonObject json,String name,String fallback) {
        return json.has(name) && !json.get(name).isJsonNull() ? json.get(name).getAsString() : fallback;
    }
}
