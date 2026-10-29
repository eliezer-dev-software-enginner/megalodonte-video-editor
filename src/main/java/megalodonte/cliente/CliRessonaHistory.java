package megalodonte.cliente;

import java.io.IOException;
import java.nio.file.*;
import java.util.Properties;

/** Per-user Ressoa choices for the terminal workflow; voice choices belong to their engine. */
final class CliRessonaHistory {
    private final Path file;

    CliRessonaHistory() { this(LastProjectStore.settingsDirectory().resolve("video-cli-ressona.properties")); }
    CliRessonaHistory(Path file) { this.file=file.toAbsolutePath().normalize(); }

    String engine() throws IOException { return read().getProperty("lastEngine",""); }
    String voice(String engine) throws IOException { return read().getProperty("voice."+engine,""); }

    void remember(String engine,String voice) throws IOException {
        if(!engine.matches("[a-zA-Z0-9_-]+") || voice.isBlank()) throw new IllegalArgumentException("Motor ou voz inválidos.");
        var properties=read();
        properties.setProperty("lastEngine",engine);
        properties.setProperty("voice."+engine,voice);
        Files.createDirectories(file.getParent());
        Path temporary=Files.createTempFile(file.getParent(),".video-cli-ressona-",".tmp");
        try {
            try(var writer=Files.newBufferedWriter(temporary)) { properties.store(writer,"Megalodonte Video Editor CLI — Ressoa"); }
            try { Files.move(temporary,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
            catch(AtomicMoveNotSupportedException error) { Files.move(temporary,file,StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }

    private Properties read() throws IOException {
        var properties=new Properties();
        if(Files.isRegularFile(file)) {
            try(var reader=Files.newBufferedReader(file)) { properties.load(reader); }
            catch(IllegalArgumentException error) { throw new IOException("Preferências do Ressoa inválidas.",error); }
        }
        return properties;
    }
}
