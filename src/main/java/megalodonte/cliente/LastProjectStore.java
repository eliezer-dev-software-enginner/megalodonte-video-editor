package megalodonte.cliente;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Small per-user preference, separate from portable project manifests. */
final class LastProjectStore {
    private final Path settings;
    LastProjectStore() { this(settingsDirectory().resolve("last-project.properties")); }
    LastProjectStore(Path settings) { this.settings=settings.toAbsolutePath(); }
    Optional<Path> lastSaved() throws IOException {
        if (!Files.exists(settings)) return Optional.empty();
        var props=new Properties();
        try (var reader=Files.newBufferedReader(settings)) { props.load(reader); }
        catch (IllegalArgumentException e) { throw new IOException("Preferência de projeto inválida",e); }
        String path=props.getProperty("lastSavedProject","");
        if (path.isBlank()) return Optional.empty();
        try { return Optional.of(Path.of(path).toAbsolutePath().normalize()); }
        catch (InvalidPathException e) { throw new IOException("Caminho do último projeto inválido",e); }
    }
    void remember(Path project) throws IOException {
        if (!Files.isRegularFile(project)) throw new IOException("O projeto precisa estar salvo antes de registrar o caminho.");
        Files.createDirectories(settings.getParent());
        var props=new Properties(); props.setProperty("lastSavedProject",project.toAbsolutePath().normalize().toString());
        Path temp=Files.createTempFile(settings.getParent(),".last-project-",".tmp");
        try {
            try (var writer=Files.newBufferedWriter(temp)) { props.store(writer,"Megalodonte Video Editor"); }
            try { Files.move(temp,settings,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temp,settings,StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temp); }
    }
    static Path settingsDirectory() {
        String override=System.getProperty("megalodonte.editor.settingsDir");
        if (override!=null && !override.isBlank()) return Path.of(override);
        String system=System.getProperty("os.name","").toLowerCase(Locale.ROOT);
        if (system.contains("win")) {
            String appData=System.getenv("APPDATA");
            if (appData!=null && !appData.isBlank()) return Path.of(appData,"megalodonte-video-editor");
        }
        String xdg=System.getenv("XDG_CONFIG_HOME");
        if (xdg!=null && !xdg.isBlank() && Path.of(xdg).isAbsolute()) return Path.of(xdg,"megalodonte-video-editor");
        return Path.of(System.getProperty("user.home"),".config","megalodonte-video-editor");
    }
}
