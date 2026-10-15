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
        assertTrue(defaults.basePrompt.contains("CANAL DARK DE CURIOSIDADES"));assertTrue(defaults.basePrompt.contains("DIREÇÃO SONORA"));
        var values=new EditorSettings.Values("dummy-key",folder.toString(),"Meu prompt\nCom instruções e acentos: geração.");
        store.save(values);var restored=new EditorSettings(file).load();assertEquals(values.apiKey,restored.apiKey);assertEquals(values.basePrompt,restored.basePrompt);
        assertEquals(folder.toString(),restored.soundsDirectory);assertFalse(values.toString().contains(values.apiKey));
        assertThrows(IllegalArgumentException.class,()->store.save(new EditorSettings.Values("",folder.resolve("missing").toString(),"Prompt")));
        assertThrows(IllegalArgumentException.class,()->store.save(new EditorSettings.Values("",folder.toString()," ")));
        assertEquals(values.basePrompt,store.load().basePrompt);
        if(Files.getFileStore(file).supportsFileAttributeView("posix")) assertEquals("rw-------",java.nio.file.attribute.PosixFilePermissions.toString(Files.getPosixFilePermissions(file)));
    }
    @Test void malformedPreferencesNeverEchoStoredContent() throws Exception {
        Path file=folder.resolve("settings.json");Files.writeString(file,"{dummy-secret");
        var error=assertThrows(java.io.IOException.class,()->new EditorSettings(file).load());assertFalse(error.getMessage().contains("dummy-secret"));
    }
    @Test void legacyKeyMigrationStaysLocalAndPreservesOtherSettings() throws Exception {
        Path file=folder.resolve("settings.json");var store=new EditorSettings(file);
        store.saveApiKey("dummy-legacy-key");
        var values=store.load();assertEquals("dummy-legacy-key",values.apiKey);
        assertEquals(EditorSettings.DEFAULT_SOUNDS,values.soundsDirectory);
        assertTrue(values.basePrompt.contains("DIREÇÃO SONORA"));
        if(Files.getFileStore(file).supportsFileAttributeView("posix")) assertEquals("rw-------",java.nio.file.attribute.PosixFilePermissions.toString(Files.getPosixFilePermissions(file)));
    }
}
