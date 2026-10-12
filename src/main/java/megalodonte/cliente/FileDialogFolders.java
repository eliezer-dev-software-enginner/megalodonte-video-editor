package megalodonte.cliente;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Last directory per purpose, persisted across launches; image actions share one entry. */
final class FileDialogFolders {
    private final Path settings;
    private final Map<String,Path> session=new HashMap<>();
    FileDialogFolders() { this(LastProjectStore.settingsDirectory().resolve("file-dialogs.properties")); }
    FileDialogFolders(Path settings) { this.settings=settings.toAbsolutePath(); }
    Optional<Path> folder(FileDialogs.Kind kind) throws IOException {
        String key=key(kind); Path path=session.get(key);
        if(path==null) {
            String value=read().getProperty(key,"");
            if(!value.isBlank()) try { path=Path.of(value); } catch(InvalidPathException ignored) { }
        }
        return path!=null && Files.isDirectory(path) ? Optional.of(path.toAbsolutePath().normalize()) : Optional.empty();
    }
    void remember(FileDialogs.Kind kind,Path folder) throws IOException {
        if(!Files.isDirectory(folder)) throw new IOException("A pasta selecionada não existe.");
        Path absolute=folder.toAbsolutePath().normalize(); String key=key(kind); session.put(key,absolute);
        var props=read(); props.setProperty(key,absolute.toString()); Files.createDirectories(settings.getParent());
        Path temporary=Files.createTempFile(settings.getParent(),".file-dialogs-",".tmp");
        try {
            try(var writer=Files.newBufferedWriter(temporary)) { props.store(writer,"Megalodonte file dialog directories"); }
            try { Files.move(temporary,settings,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
            catch(AtomicMoveNotSupportedException e) { Files.move(temporary,settings,StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }
    private Properties read() throws IOException {
        var props=new Properties();
        if(Files.isRegularFile(settings)) {
            try(var reader=Files.newBufferedReader(settings)) { props.load(reader); }
            catch(IllegalArgumentException ignored) { props.clear(); }
        }
        return props;
    }
    private static String key(FileDialogs.Kind kind) {
        return switch(kind) {
            case IMAGE,IMAGES -> "images";
            case NARRATION -> "narration";
            case EFFECT -> "effects";
            case MUSIC -> "music";
            case PROJECT_OPEN,PROJECT_SAVE -> "projects";
            case VIDEO -> "video";
        };
    }
}
