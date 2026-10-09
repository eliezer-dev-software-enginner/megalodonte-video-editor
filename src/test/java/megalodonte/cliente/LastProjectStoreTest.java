package megalodonte.cliente;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class LastProjectStoreTest {
    @TempDir Path directory;
    @Test void persistsAbsolutePathWithSpacesAndAccentsAcrossInstances() throws Exception {
        Path preferences=directory.resolve("settings/last-project.properties"), project=directory.resolve("vídeo com espaços.json");
        var store=new LastProjectStore(preferences); assertTrue(store.lastSaved().isEmpty());
        Files.writeString(project,"{}"); store.remember(project);
        assertEquals(project.toAbsolutePath(),new LastProjectStore(preferences).lastSaved().orElseThrow());
        Path another=directory.resolve("outro.json"); Files.writeString(another,"{}"); store.remember(another);
        assertEquals(another,new LastProjectStore(preferences).lastSaved().orElseThrow());
    }
    @Test void missingSaveNeverReplacesLastRememberedProject() throws Exception {
        var store=new LastProjectStore(directory.resolve("settings.properties")); Path file=directory.resolve("saved.json"); Files.writeString(file,"{}"); store.remember(file);
        assertThrows(java.io.IOException.class,()->store.remember(directory.resolve("missing.json")));
        assertEquals(file,store.lastSaved().orElseThrow());
        Files.delete(file); assertEquals(file,store.lastSaved().orElseThrow());
    }
}
