package megalodonte.cliente;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class VideoCliSoundOptionsTest {
    @Test void soundLibraryRequiresDescriptionFilesBeforeGeneration() throws Exception {
        Path root=Files.createTempDirectory("video-cli-sounds-");
        try {
            assertThrows(IOException.class,()->VideoCli.validateSoundsDirectory(root));
            Path nested=Files.createDirectory(root.resolve("catalogo"));
            Files.writeString(nested.resolve("sons.TXT"),"[chuva.wav]\n#efeito #chuva\n");
            assertEquals(root,VideoCli.validateSoundsDirectory(root));
            assertEquals(25,EditorSettings.parseSoundEffectPercent("25%"));
        } finally {
            try(var files=Files.walk(root)) {
                for(var path:files.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
    }
}
