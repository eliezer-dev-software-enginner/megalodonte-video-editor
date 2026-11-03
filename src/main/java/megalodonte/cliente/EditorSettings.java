package megalodonte.cliente;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;

/** Shared local editor preferences; credentials are never written to project manifests. */
final class EditorSettings {
    static final String DEFAULT_SOUNDS="";
    static final String DEFAULT_IMAGES=Path.of(System.getProperty("user.home"),"Pictures","Megalodonte Video Editor").toString();
    static final class Values {
        final String apiKey,soundsDirectory,basePrompt,defaultVisualEffect,narrationLanguage;
        final String ffmpegExecutable,ffprobeExecutable,imagesDirectory;
        final double visualEffectStrength;
        final int soundEffectPercent;
        final boolean notifyTelegram;
        Values(String apiKey,String soundsDirectory,String basePrompt) {
            this(apiKey,soundsDirectory,basePrompt,"none",1,"pt",100,"","",true,DEFAULT_IMAGES);
        }
        Values(String apiKey,String soundsDirectory,String basePrompt,String defaultVisualEffect,double visualEffectStrength,
               String narrationLanguage,int soundEffectPercent,String ffmpegExecutable,String ffprobeExecutable,
               boolean notifyTelegram,String imagesDirectory) {
            this.apiKey=apiKey;this.soundsDirectory=soundsDirectory;this.basePrompt=basePrompt;
            this.defaultVisualEffect=defaultVisualEffect;this.visualEffectStrength=visualEffectStrength;
            this.narrationLanguage=narrationLanguage;this.soundEffectPercent=soundEffectPercent;
            this.ffmpegExecutable=ffmpegExecutable;this.ffprobeExecutable=ffprobeExecutable;
            this.notifyTelegram=notifyTelegram;this.imagesDirectory=imagesDirectory;
        }
        Values withImagesDirectory(String directory) {
            return new Values(apiKey,soundsDirectory,basePrompt,defaultVisualEffect,visualEffectStrength,narrationLanguage,soundEffectPercent,ffmpegExecutable,ffprobeExecutable,notifyTelegram,directory);
        }
        Values withNarrationLanguage(String language) {
            return new Values(apiKey,soundsDirectory,basePrompt,defaultVisualEffect,visualEffectStrength,language,soundEffectPercent,ffmpegExecutable,ffprobeExecutable,notifyTelegram,imagesDirectory);
        }
        @Override public String toString() {return "Configurações do editor";}
    }
    private final Path file;
    EditorSettings() {this(LastProjectStore.settingsDirectory().resolve("editor-settings.json"));}
    EditorSettings(Path file) {this.file=file.toAbsolutePath();}
    boolean exists() {return Files.isRegularFile(file);}
    static String defaultPrompt() {
        try(var stream=EditorSettings.class.getResourceAsStream("/prompts/base-video.md")) {
            if(stream==null)throw new IllegalStateException("Prompt base padrão ausente.");
            return new String(stream.readAllBytes(),StandardCharsets.UTF_8);
        } catch(IOException e) {throw new UncheckedIOException(e);}
    }
    Values load() throws IOException {
        if(!exists())return new Values("",DEFAULT_SOUNDS,defaultPrompt());
        try {
            var json=JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            String effect=validateVisualEffect(value(json,"defaultVisualEffect","none"));
            double strength=json.has("visualEffectStrength")?json.get("visualEffectStrength").getAsDouble():1;
            VideoProject.validateVisualEffectStrength(strength);
            String legacy=value(json,"whisperLanguage","pt");
            String language=value(json,"narrationLanguage",Set.of("pt","en").contains(legacy)?legacy:"pt");
            validateNarrationLanguage(language);
            int percent=json.has("soundEffectPercent")?json.get("soundEffectPercent").getAsInt():100;
            validateSoundEffectPercent(percent);
            return new Values(value(json,"openaiApiKey",""),value(json,"soundsDirectory",DEFAULT_SOUNDS),value(json,"basePrompt",defaultPrompt()),
                    effect,strength,language,percent,value(json,"ffmpegExecutable",""),value(json,"ffprobeExecutable",""),
                    json.has("notifyTelegram")?json.get("notifyTelegram").getAsBoolean():true,value(json,"imagesDirectory",DEFAULT_IMAGES));
        } catch(RuntimeException e) {throw new IOException("Arquivo de configurações inválido.");}
    }
    void save(Values values) throws IOException {
        String sounds=values.soundsDirectory.isBlank()?"":validateSounds(values.soundsDirectory).toString();
        if(values.basePrompt.isBlank())throw new IllegalArgumentException("Informe um prompt base.");
        validateVisualEffect(values.defaultVisualEffect);VideoProject.validateVisualEffectStrength(values.visualEffectStrength);
        validateNarrationLanguage(values.narrationLanguage);validateSoundEffectPercent(values.soundEffectPercent);
        write(new Values(values.apiKey,sounds,values.basePrompt,values.defaultVisualEffect,values.visualEffectStrength,values.narrationLanguage,
                values.soundEffectPercent,ExternalTools.validateExecutable(values.ffmpegExecutable,"FFmpeg"),
                ExternalTools.validateExecutable(values.ffprobeExecutable,"FFprobe"),values.notifyTelegram,normalizeImages(values.imagesDirectory)));
    }
    void saveApiKey(String apiKey) throws IOException {
        var v=load();write(new Values(apiKey,v.soundsDirectory,v.basePrompt,v.defaultVisualEffect,v.visualEffectStrength,v.narrationLanguage,
                v.soundEffectPercent,v.ffmpegExecutable,v.ffprobeExecutable,v.notifyTelegram,v.imagesDirectory));
    }
    void saveImagesDirectory(String directory) throws IOException {write(load().withImagesDirectory(normalizeImages(directory)));}
    void saveNarrationLanguage(String language) throws IOException {write(load().withNarrationLanguage(validateNarrationLanguage(language)));}
    private void write(Values v) throws IOException {
        Files.createDirectories(file.getParent());var json=new JsonObject();json.addProperty("openaiApiKey",v.apiKey.strip());
        json.addProperty("soundsDirectory",v.soundsDirectory);json.addProperty("imagesDirectory",v.imagesDirectory);json.addProperty("basePrompt",v.basePrompt);
        json.addProperty("defaultVisualEffect",v.defaultVisualEffect);json.addProperty("visualEffectStrength",v.visualEffectStrength);
        json.addProperty("narrationLanguage",v.narrationLanguage);json.addProperty("soundEffectPercent",v.soundEffectPercent);
        json.addProperty("notifyTelegram",v.notifyTelegram);json.addProperty("ffmpegExecutable",v.ffmpegExecutable);json.addProperty("ffprobeExecutable",v.ffprobeExecutable);
        Path temp=Files.createTempFile(file.getParent(),".editor-settings-",".tmp");
        try {
            try {Files.setPosixFilePermissions(temp,PosixFilePermissions.fromString("rw-------"));} catch(UnsupportedOperationException ignored) {}
            Files.writeString(temp,new GsonBuilder().setPrettyPrinting().create().toJson(json)+"\n");
            try {Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException e) {Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING);}
        } finally {Files.deleteIfExists(temp);}
    }
    static Path validateSounds(String value) {
        if(value.isBlank())throw new IllegalArgumentException("Informe o diretório de sons.");
        Path folder=Path.of(value.strip()).toAbsolutePath().normalize();
        if(!Files.isDirectory(folder)||!Files.isReadable(folder))throw new IllegalArgumentException("Escolha um diretório de sons existente e legível.");
        return folder;
    }
    static String normalizeImages(String value) {
        if(value==null||value.isBlank())throw new IllegalArgumentException("Informe o diretório global de imagens.");
        Path path=Path.of(value.strip()).toAbsolutePath().normalize();
        if(Files.exists(path)&&!Files.isDirectory(path))throw new IllegalArgumentException("O diretório de imagens não é uma pasta.");
        return path.toString();
    }
    static String validateNarrationLanguage(String language) {
        if(!Set.of("pt","en").contains(language))throw new IllegalArgumentException("Escolha português brasileiro ou inglês americano.");
        return language;
    }
    static String validateVisualEffect(String value) {
        if(!Set.of("none","smooth","particles","shake").contains(value))throw new IllegalArgumentException("Escolha um efeito visual válido.");
        return value;
    }
    static double parseVisualEffectStrength(String value) {
        try {return VideoProject.validateVisualEffectStrength(Double.parseDouble(value.strip().replace(',','.')));}
        catch(NumberFormatException e) {throw new IllegalArgumentException("Informe intensidade visual entre 0,25 e 3.");}
    }
    static int validateSoundEffectPercent(int value) {
        if(!Set.of(0,25,50,75,100).contains(value))throw new IllegalArgumentException("Escolha 0%, 25%, 50%, 75% ou 100% de efeitos sonoros.");
        return value;
    }
    static int parseSoundEffectPercent(String value) {
        try {return validateSoundEffectPercent(Integer.parseInt(value.strip().split("%",2)[0].strip()));}
        catch(NumberFormatException e) {throw new IllegalArgumentException("Escolha um percentual de efeitos sonoros válido.");}
    }
    private static String value(JsonObject json,String name,String fallback) {
        return json.has(name)&&!json.get(name).isJsonNull()?json.get(name).getAsString():fallback;
    }
}
