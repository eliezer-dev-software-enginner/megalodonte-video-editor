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
        run(project,assets,assets,ready,progress);
    }
    void run(VideoProject project,Path assets,Path library,BiConsumer<Integer,Path> ready,Consumer<String> progress) throws IOException,InterruptedException {
        var pending=pending(project);Files.createDirectories(assets);Files.createDirectories(library);
        for(int index:pending) {
            check();
            String plainScript=VideoProject.withoutAvatarMarkers(project.script);
            String context=plainScript.length()>4000 ? plainScript.substring(0,4000) : plainScript;
            String prompt=prompt(project,context,index);
            String scene=project.clips.get(index).text;
            String configuration=configuration(project);
            String identity="image-v1\n"+configuration+"\n"+context.strip()+"\n"+scene.strip();
            String hash;
            try { hash=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(identity.getBytes(java.nio.charset.StandardCharsets.UTF_8))); }
            catch(java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
            String name=slug(scene)+"-"+hash.substring(0,16)+".png";
            Path shared=library.resolve(name);
            boolean reusable=ImageLibrary.validImage(shared);
            boolean matchedRelatedContext=false;
            if(!reusable) {
                var match=ImageLibrary.find(library,scene,context,configuration);
                if(match.isPresent()){shared=match.get();reusable=true;matchedRelatedContext=true;}
            }
            if(reusable) progress.accept("Reutilizando imagem da cena "+(index+1)+" de "+project.clips.size()+"…");
            else {
                progress.accept("Gerando imagem da cena "+(index+1)+" de "+project.clips.size()+"…");
                byte[] png=client.generate(prompt);check();Path temp=Files.createTempFile(library,".image-",".tmp");
                try { Files.write(temp,png);check();Files.move(temp,shared,StandardCopyOption.REPLACE_EXISTING); }
                finally { Files.deleteIfExists(temp); }
            }
            if(!matchedRelatedContext)ImageLibrary.record(shared,scene,context,configuration);
            Path file=assets.resolve(shared.getFileName());
            if(!shared.toAbsolutePath().normalize().equals(file.toAbsolutePath().normalize())) {
                Path temp=Files.createTempFile(assets,".image-",".tmp");
                try { Files.copy(shared,temp,StandardCopyOption.REPLACE_EXISTING);check();Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING); }
                finally { Files.deleteIfExists(temp); }
            }
            check();ready.accept(index,file.toAbsolutePath());
        }
    }
    /** Built-in instructions used when the project does not define its own image prompt. */
    static final String DEFAULT_PROMPT="Crie uma imagem para um vídeo horizontal 16:9. Composição cinematográfica, sem legendas, letras, logotipos ou marcas d'água. "
        +"Mantenha coerência visual com o roteiro.";
    /** The project prompt replaces only the instructions; script context and scene text are always appended. */
    static String prompt(VideoProject project,String context,int index) {
        String custom=VideoProject.validateImagePrompt(project.imagePrompt);
        String scene="Cena "+(index+1)+": "+project.clips.get(index).text;
        if(custom.isBlank()) return DEFAULT_PROMPT+" Contexto do roteiro: "+context+"\n"+scene;
        return custom+"\n\nContexto do roteiro: "+context+"\n"+scene;
    }
    /** Custom prompts get their own cache identity so the library never reuses an image made under other instructions. */
    private String configuration(VideoProject project) {
        String custom=VideoProject.validateImagePrompt(project.imagePrompt);
        if(custom.isBlank()) return client.cacheIdentity();
        try {
            byte[] digest=java.security.MessageDigest.getInstance("SHA-256").digest(custom.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return client.cacheIdentity()+"\nprompt:"+java.util.HexFormat.of().formatHex(digest).substring(0,16);
        } catch(java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private static String slug(String scene) {
        var stop=Set.of("a","ao","as","com","da","das","de","do","dos","e","em","na","nas","no","nos","o","os","para","por","que","um","uma","the","an","and","at","for","in","of","on","to","with");
        String words=java.text.Normalizer.normalize(scene.toLowerCase(Locale.ROOT),java.text.Normalizer.Form.NFD).replaceAll("\\p{M}+","");
        String value=Arrays.stream(words.split("[^a-z0-9]+")) .filter(word->!word.isBlank()&&!stop.contains(word)).limit(5).collect(java.util.stream.Collectors.joining("-"));
        if(value.isBlank())return "imagem";
        if(value.length()>48)value=value.substring(0,48).replaceAll("-+$","");
        return value;
    }
    private void check() { if(cancelled || Thread.currentThread().isInterrupted()) throw new CancellationException("Geração cancelada."); }
}
