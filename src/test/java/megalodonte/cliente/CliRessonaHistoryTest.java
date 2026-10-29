package megalodonte.cliente;

import org.junit.jupiter.api.Test;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class CliRessonaHistoryTest {
    @Test void remembersEngineAndEachEnginesVoice() throws Exception {
        Path root=Files.createTempDirectory("video-cli-ressona-");
        try {
            var history=new CliRessonaHistory(root.resolve("config/video-cli-ressona.properties"));
            assertEquals("",history.engine());
            history.remember("supertonic3","supertonic3-M1");
            history.remember("chatterbox","builtin");
            var reopened=new CliRessonaHistory(root.resolve("config/video-cli-ressona.properties"));
            assertEquals("chatterbox",reopened.engine());
            assertEquals("supertonic3-M1",reopened.voice("supertonic3"));
            assertEquals("builtin",reopened.voice("chatterbox"));
        } finally {
            try(var paths=Files.walk(root)) {
                for(var path:paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
    }
}
