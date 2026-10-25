package megalodonte.cliente;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Resolves exact avatar filenames below a project's configured library. */
final class AvatarFiles {
    private AvatarFiles() { }

    static Path resolve(String directory, String filename) throws IOException {
        if (filename == null || filename.isBlank()) return null;
        if (!filename.matches("[\\p{L}\\p{N}_-]+\\.png"))
            throw new IOException("Nome de avatar inválido: " + filename);
        if (directory == null || directory.isBlank())
            throw new IOException("Configure a pasta de avatares deste projeto em Configurações → Avatar.");
        Path root = Path.of(directory).toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) throw new IOException("Pasta de avatares ausente: " + root);
        List<Path> matches;
        try (var files = Files.walk(root)) {
            matches = files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().equals(filename)).toList();
        }
        if (matches.isEmpty()) throw new IOException("Avatar não encontrado: " + filename);
        if (matches.size() > 1) throw new IOException("Avatar duplicado em subpastas: " + filename);
        return matches.getFirst();
    }
}
