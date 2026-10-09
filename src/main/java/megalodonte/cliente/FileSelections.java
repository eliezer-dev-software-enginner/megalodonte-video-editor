package megalodonte.cliente;

import java.io.IOException;
import java.nio.file.*;
import java.util.Locale;
import java.util.stream.Stream;

/** Validation is independent of file-dialog filters, which are only presentation. */
final class FileSelections {
    static Path readable(Path file,FileDialogs.Kind kind) throws IOException {
        Path path=file.toAbsolutePath().normalize();
        if(!Files.isRegularFile(path) || !Files.isReadable(path)) throw new IOException("Arquivo inexistente ou sem permissão de leitura: "+path);
        String name=path.getFileName().toString().toLowerCase(Locale.ROOT);
        if(Stream.of(kind.extensions).noneMatch(ext->name.endsWith("."+ext))) throw new IOException("Formato não aceito para "+kind.description+": "+path.getFileName());
        if(Files.size(path)==0) throw new IOException("Arquivo vazio: "+path.getFileName());
        return path.toRealPath();
    }
    static Path destination(Path file,FileDialogs.Kind kind) throws IOException {
        if(!kind.save) throw new IllegalArgumentException("Operação não é de salvamento");
        Path path=file.toAbsolutePath().normalize(); String name=path.getFileName().toString();
        if(!name.toLowerCase(Locale.ROOT).endsWith("."+kind.extensions[0])) path=path.resolveSibling(name+"."+kind.extensions[0]);
        if(Files.exists(path) && (!Files.isRegularFile(path) || !Files.isWritable(path))) throw new IOException("Destino não é um arquivo gravável: "+path);
        if(path.getParent()==null || !Files.isDirectory(path.getParent()) || !Files.isWritable(path.getParent())) throw new IOException("Pasta de destino inexistente ou sem permissão de escrita.");
        return path;
    }
    static void protectAssets(VideoProject project,Path destination,Path manifest,boolean exporting) throws IOException {
        if(exporting && manifest!=null && same(destination,manifest)) throw new IOException("O vídeo não pode substituir o manifesto do projeto.");
        var images=project.clips.stream().map(clip->clip.image);
        for(String name:Stream.concat(images,Stream.of(project.narration.file,project.music.file)).filter(value->!value.isBlank()).toList())
            if(same(destination,Path.of(name))) throw new IOException("O destino não pode substituir um asset do projeto.");
    }
    private static boolean same(Path a,Path b) throws IOException {
        return a.toAbsolutePath().normalize().equals(b.toAbsolutePath().normalize()) || (Files.exists(a) && Files.exists(b) && Files.isSameFile(a,b));
    }
}
