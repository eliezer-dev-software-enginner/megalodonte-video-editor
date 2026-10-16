package megalodonte.cliente;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.function.*;

/** Sequential small batches preserve scene identity and can resume without regenerating completed scenes. */
final class ImageGenerationJob {
    private final OpenAiImageClient client;
    private volatile boolean cancelled;
    ImageGenerationJob(OpenAiImageClient client) { this.client=client; }
    void cancel() { cancelled=true; }
    boolean cancelled() { return cancelled; }
    static List<Integer> pending(VideoProject project) {
        var indices=new ArrayList<Integer>();
        for(int i=0;i<project.clips.size();i++) if(project.clips.get(i).image.isBlank()) indices.add(i);
        if(project.clips.isEmpty()) throw new IllegalArgumentException("Crie as cenas do roteiro antes de gerar imagens.");
        if(indices.isEmpty()) throw new IllegalArgumentException("Todas as cenas já têm imagem.");
        for(int index:indices) if(project.clips.get(index).text.length()>3000) throw new IllegalArgumentException("Para este teste, use textos de cena com até 3000 caracteres.");
        return indices;
    }
    void run(VideoProject project,Path assets,BiConsumer<Integer,Path> ready,Consumer<String> progress) throws IOException,InterruptedException {
        var pending=pending(project);Files.createDirectories(assets);
        for(int index:pending) {
            check();progress.accept("Gerando imagem da cena "+(index+1)+" de "+project.clips.size()+"…");
            String context=project.script.length()>4000 ? project.script.substring(0,4000) : project.script;
            String prompt="Crie uma imagem para um vídeo horizontal 16:9. Composição cinematográfica, sem legendas, letras, logotipos ou marcas d'água. "
                +"Mantenha coerência visual com o roteiro. Contexto do roteiro: "+context+"\nCena "+(index+1)+": "+project.clips.get(index).text;
            byte[] png=client.generate(prompt);check();
            Path file=assets.resolve(String.format(Locale.ROOT,"scene-%03d-%s.png",index+1,UUID.randomUUID()));
            Path temp=Files.createTempFile(assets,".image-",".tmp");
            try { Files.write(temp,png);check();Files.move(temp,file); }
            finally { Files.deleteIfExists(temp); }
            check();ready.accept(index,file.toAbsolutePath());
        }
    }
    private void check() { if(cancelled || Thread.currentThread().isInterrupted()) throw new CancellationException("Geração cancelada."); }
}
