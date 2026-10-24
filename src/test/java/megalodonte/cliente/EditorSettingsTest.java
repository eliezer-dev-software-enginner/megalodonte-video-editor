package megalodonte.cliente;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
class EditorSettingsTest {
    @TempDir Path folder;
    @Test void defaultsPersistPromptAndSoundsAndProtectPriorPreferencesOnError() throws Exception {
        Path file=folder.resolve("preferences/settings.json");var store=new EditorSettings(file);
        var defaults=store.load();assertEquals(EditorSettings.DEFAULT_SOUNDS,defaults.soundsDirectory);
        assertEquals(1.0,defaults.pauseCutThreshold);
        assertTrue(defaults.basePrompt.contains("PRODUTOR DO PROJETO"));assertTrue(defaults.basePrompt.contains("audio.effects"));
        var values=new EditorSettings.Values("dummy-key",folder.toString(),"Meu prompt\nCom instruções e acentos: geração.",1.8,"smooth",1.6,"medium","auto",7,6,false,50);
        store.save(values);var restored=new EditorSettings(file).load();assertEquals(values.apiKey,restored.apiKey);assertEquals(values.basePrompt,restored.basePrompt);
        assertEquals(1.8,restored.pauseCutThreshold);
        assertEquals("smooth",restored.defaultVisualEffect);assertEquals(1.6,restored.visualEffectStrength);
        assertEquals("medium",restored.whisperModel);assertEquals("auto",restored.whisperLanguage);
        assertEquals(7,restored.whisperBeamSize);assertEquals(6,restored.whisperCpuThreads);assertFalse(restored.whisperVad);
        assertEquals(50,restored.soundEffectPercent);
        assertEquals(folder.toString(),restored.soundsDirectory);assertFalse(values.toString().contains(values.apiKey));
        assertThrows(IllegalArgumentException.class,()->store.save(new EditorSettings.Values("",folder.resolve("missing").toString(),"Prompt")));
        assertThrows(IllegalArgumentException.class,()->store.save(new EditorSettings.Values("",folder.toString()," ")));
        assertThrows(IllegalArgumentException.class,()->store.save(new EditorSettings.Values("",folder.toString(),"Prompt",Double.NaN)));
        assertThrows(IllegalArgumentException.class,()->EditorSettings.parsePauseCutThreshold("31"));
        assertEquals(1.5,EditorSettings.parsePauseCutThreshold("1,5"));
        assertEquals(values.basePrompt,store.load().basePrompt);
        store.saveApiKey("migrated-key");assertEquals("medium",store.load().whisperModel);
        assertFalse(store.load().whisperVad);
        assertEquals(50,store.load().soundEffectPercent);
        assertEquals(25,EditorSettings.parseSoundEffectPercent("25% de efeitos sonoros"));
        assertThrows(IllegalArgumentException.class,()->EditorSettings.parseSoundEffectPercent("35% de efeitos sonoros"));
        assertThrows(IllegalArgumentException.class,()->store.save(new EditorSettings.Values("",folder.toString(),"Prompt",1,"invalid",1)));
        assertThrows(IllegalArgumentException.class,()->EditorSettings.parseVisualEffectStrength("3.1"));
        assertThrows(IllegalArgumentException.class,()->EditorSettings.parseWhisperNumber("0","Busca",1,10));
        assertThrows(IllegalArgumentException.class,()->EditorSettings.validateWhisper("unknown","pt",5,4));
        if(Files.getFileStore(file).supportsFileAttributeView("posix")) assertEquals("rw-------",java.nio.file.attribute.PosixFilePermissions.toString(Files.getPosixFilePermissions(file)));
    }
    @Test void malformedPreferencesNeverEchoStoredContent() throws Exception {
        Path file=folder.resolve("settings.json");Files.writeString(file,"{dummy-secret");
        var error=assertThrows(java.io.IOException.class,()->new EditorSettings(file).load());assertFalse(error.getMessage().contains("dummy-secret"));
    }
    @Test void legacyKeyMigrationStaysLocalAndPreservesOtherSettings() throws Exception {
        Path file=folder.resolve("settings.json");var store=new EditorSettings(file);
        store.save(new EditorSettings.Values("",folder.toString(),"Prompt",2.4));
        store.saveApiKey("dummy-legacy-key");
        var values=store.load();assertEquals("dummy-legacy-key",values.apiKey);
        assertEquals(folder.toString(),values.soundsDirectory);
        assertEquals("Prompt",values.basePrompt);
        assertEquals(2.4,values.pauseCutThreshold);
        if(Files.getFileStore(file).supportsFileAttributeView("posix")) assertEquals("rw-------",java.nio.file.attribute.PosixFilePermissions.toString(Files.getPosixFilePermissions(file)));
    }
    @Test void olderSettingsWithoutPauseValueUseDefault() throws Exception {
        Path file=folder.resolve("settings.json");
        Files.writeString(file,"{\"soundsDirectory\":\""+folder.toString().replace("\\","\\\\")+"\",\"basePrompt\":\"Prompt\"}");
        assertEquals(1.0,new EditorSettings(file).load().pauseCutThreshold);
        assertEquals("none",new EditorSettings(file).load().defaultVisualEffect);
        assertEquals(1.0,new EditorSettings(file).load().visualEffectStrength);
        var legacy=new EditorSettings(file).load();
        assertEquals("small",legacy.whisperModel);assertEquals("pt",legacy.whisperLanguage);
        assertEquals(5,legacy.whisperBeamSize);assertEquals(4,legacy.whisperCpuThreads);assertTrue(legacy.whisperVad);
        assertEquals(100,legacy.soundEffectPercent);
    }
}
