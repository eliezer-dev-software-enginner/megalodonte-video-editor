package megalodonte.cliente;

import java.nio.file.*;

/** Opt-in live API smoke test; never runs as part of ordinary Gradle tests. */
public final class ImageIntegrationSmoke {
    public static void main(String[] args) throws Exception {
        if(args.length!=2) throw new IllegalArgumentException("Informe roteiro.txt e destino.mvideo.json");
        var client=OpenAiImageClient.fromEnvironment();
        var project=VideoProject.fromScript(Files.readString(Path.of(args[0])));ImageGenerationJob.pending(project);
        Path file=Path.of(args[1]).toAbsolutePath();
        if(Files.exists(file)) throw new IllegalArgumentException("Use um destino novo para o teste de integração.");
        project.save(file);
        Path assets=file.getParent().resolve(file.getFileName()+".assets").resolve("generated");
        new ImageGenerationJob(client).run(project,assets,(index,image)-> {
            project.clips.get(index).image=image.toString();
            try { project.save(file); } catch(java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
        },System.out::println);
        System.out.println("Projeto pronto para abrir no editor: "+file);
    }
}
