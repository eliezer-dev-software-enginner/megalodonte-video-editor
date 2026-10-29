package megalodonte.cliente;

import org.junit.jupiter.api.Test;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class CliAvatarHistoryTest {
    @Test void remembersOnlyAnExistingAvatarFolder() throws Exception {
        Path root=Files.createTempDirectory("video-cli-avatar-");
        try {
            var history=new CliAvatarHistory(root.resolve("settings/video-cli.properties"));
            assertTrue(history.lastDirectory().isEmpty());
            assertThrows(java.io.IOException.class,()->history.remember(root.resolve("missing")));
            Path avatars=Files.createDirectory(root.resolve("avatares"));
            history.remember(avatars);
            assertEquals(avatars,history.lastDirectory().orElseThrow());
        } finally {
            try(var files=Files.walk(root)) {
                for(var path:files.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
    }
}
