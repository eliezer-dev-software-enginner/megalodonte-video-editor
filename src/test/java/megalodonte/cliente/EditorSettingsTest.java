package megalodonte.cliente;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class EditorSettingsTest {
    @TempDir Path folder;

    @Test void savesSharedPreferencesAndPreservesThemAcrossIndependentUpdates() throws Exception {
        var store=new EditorSettings(folder.resolve("settings.json"));var defaults=store.load();
        assertEquals("pt",defaults.narrationLanguage);assertEquals(EditorSettings.DEFAULT_IMAGES,defaults.imagesDirectory);
        var values=new EditorSettings.Values("dummy-key",folder.toString(),"Prompt", "smooth",1.6,"en",50,"","",false,folder.resolve("imagens").toString());
        store.save(values);store.saveApiKey("new-key");store.saveImagesDirectory(folder.resolve("outras imagens").toString());
        var loaded=store.load();assertEquals("new-key",loaded.apiKey);assertEquals("en",loaded.narrationLanguage);
        assertEquals(folder.resolve("outras imagens").toString(),loaded.imagesDirectory);assertEquals("smooth",loaded.defaultVisualEffect);
        assertEquals(1.6,loaded.visualEffectStrength);assertEquals(50,loaded.soundEffectPercent);assertFalse(loaded.notifyTelegram);
        store.saveNarrationLanguage("pt");assertEquals("pt",store.load().narrationLanguage);
        assertThrows(IllegalArgumentException.class,()->store.saveNarrationLanguage("auto"));
        assertFalse(loaded.toString().contains("new-key"));
    }
    @Test void migratesLegacyLanguageWithoutKeepingObsoletePreferences() throws Exception {
        Path file=folder.resolve("settings.json");Files.writeString(file,"{\"whisperLanguage\":\"en\",\"whisperModel\":\"small\",\"pauseCutThreshold\":1.0}");
        var store=new EditorSettings(file);assertEquals("en",store.load().narrationLanguage);store.saveNarrationLanguage("en");
        String saved=Files.readString(file);assertTrue(saved.contains("\"narrationLanguage\""));assertFalse(saved.contains("whisper"));assertFalse(saved.contains("pauseCutThreshold"));
        Files.writeString(file,"{\"whisperLanguage\":\"auto\"}");assertEquals("pt",store.load().narrationLanguage);
    }
    @Test void rejectsInvalidSettingsWithoutReplacingPreviousValues() throws Exception {
        Path file=folder.resolve("settings.json");var store=new EditorSettings(file);
        store.save(new EditorSettings.Values("",folder.toString(),"Prompt"));
        assertThrows(IllegalArgumentException.class,()->store.save(new EditorSettings.Values("",folder.resolve("missing").toString(),"Prompt")));
        assertThrows(IllegalArgumentException.class,()->store.save(new EditorSettings.Values("",folder.toString()," ")));
        assertThrows(IllegalArgumentException.class,()->EditorSettings.validateVisualEffect("invalid"));
        assertThrows(IllegalArgumentException.class,()->EditorSettings.parseSoundEffectPercent("35%"));
        assertEquals("Prompt",store.load().basePrompt);
        Files.writeString(file,"{dummy-secret");assertFalse(assertThrows(java.io.IOException.class,store::load).getMessage().contains("dummy-secret"));
    }
}
