package megalodonte.cliente;

import java.io.IOException;
import java.nio.file.*;
import java.util.Optional;
import java.util.Properties;

/** Last avatar folder used by the terminal workflow; project-specific choices stay in the manifest. */
final class CliAvatarHistory {
    private final Path file;

    CliAvatarHistory() { this(LastProjectStore.settingsDirectory().resolve("video-cli.properties")); }
    CliAvatarHistory(Path file) { this.file=file.toAbsolutePath().normalize(); }

    Optional<Path> lastDirectory() throws IOException {
        if(!Files.isRegularFile(file)) return Optional.empty();
        var properties=new Properties();
        try(var reader=Files.newBufferedReader(file)) { properties.load(reader); }
        catch(IllegalArgumentException error) { throw new IOException("Histórico de avatares inválido.",error); }
        String value=properties.getProperty("lastAvatarDirectory","");
        if(value.isBlank()) return Optional.empty();
        try { return Optional.of(Path.of(value).toAbsolutePath().normalize()); }
        catch(InvalidPathException error) { throw new IOException("Caminho anterior de avatares inválido.",error); }
    }

    void remember(Path directory) throws IOException {
        Path absolute=directory.toAbsolutePath().normalize();
        if(!Files.isDirectory(absolute)||!Files.isReadable(absolute))
            throw new IOException("Pasta de avatares não encontrada: "+absolute);
        Files.createDirectories(file.getParent());
        var properties=new Properties();
        properties.setProperty("lastAvatarDirectory",absolute.toString());
        Path temporary=Files.createTempFile(file.getParent(),".video-cli-",".tmp");
        try {
            try(var writer=Files.newBufferedWriter(temporary)) { properties.store(writer,"Megalodonte Video Editor CLI"); }
            try { Files.move(temporary,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
            catch(AtomicMoveNotSupportedException error) { Files.move(temporary,file,StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }
}
