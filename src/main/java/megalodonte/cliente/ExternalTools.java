package megalodonte.cliente;

import java.nio.file.Files;
import java.nio.file.Path;

/** Optional local tool overrides. An empty value keeps automatic discovery. */
final class ExternalTools {
    private ExternalTools() { }

    static String validateExecutable(String value,String label) {
        if(value==null || value.isBlank()) return "";
        String name=value.strip();
        Path candidate=Path.of(name);
        if(candidate.isAbsolute() || name.contains("/") || name.contains("\\")) {
            Path absolute=candidate.toAbsolutePath().normalize();
            if(!Files.isRegularFile(absolute) || !Files.isExecutable(absolute))
                throw new IllegalArgumentException(label+": escolha um executável existente.");
            return absolute.toString();
        }
        String path=System.getenv().getOrDefault("PATH","");
        for(String part:path.split(java.util.regex.Pattern.quote(java.io.File.pathSeparator))) {
            if(part.isBlank()) continue;
            Path found=Path.of(part).resolve(name);
            if(Files.isRegularFile(found) && Files.isExecutable(found)) return name;
        }
        throw new IllegalArgumentException(label+": comando não encontrado no PATH.");
    }

    static String validateDirectory(String value,String label) {
        if(value==null || value.isBlank()) return "";
        Path folder=Path.of(value.strip()).toAbsolutePath().normalize();
        if(!Files.isDirectory(folder) || !Files.isReadable(folder))
            throw new IllegalArgumentException("Escolha uma pasta existente e legível para "+label+".");
        return folder.toString();
    }

    static String command(String configured,String automatic) { return configured==null || configured.isBlank() ? automatic : configured; }
}
