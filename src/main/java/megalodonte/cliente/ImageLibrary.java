package megalodonte.cliente;

import com.google.gson.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;

/** Metadata-backed, conservative reuse of matching scenes in related scripts. */
final class ImageLibrary {
    private ImageLibrary() {}

    static Optional<Path> find(Path library,String scene,String context,String configuration) throws IOException {
        if(!Files.isDirectory(library))return Optional.empty();
        try(var entries=Files.list(library)) {
            for(Path metadata:entries.filter(path->path.getFileName().toString().endsWith(".image.json")).sorted().toList()) {
                try {
                    var item=JsonParser.parseString(Files.readString(metadata,StandardCharsets.UTF_8)).getAsJsonObject();
                    if(!configuration.equals(item.get("configuration").getAsString()) ||
                            !normalize(scene).equals(normalize(item.get("scene").getAsString())) ||
                            similarity(context,item.get("context").getAsString())<.75)continue;
                    String name=item.get("file").getAsString();
                    if(!Path.of(name).getFileName().toString().equals(name))continue;
                    Path image=library.resolve(name);
                    if(validImage(image))return Optional.of(image);
                } catch(RuntimeException | IOException ignored) { /* Ignore incomplete or unrelated entries. */ }
            }
        }
        return Optional.empty();
    }

    static void record(Path image,String scene,String context,String configuration) throws IOException {
        var item=new JsonObject();item.addProperty("version",1);item.addProperty("file",image.getFileName().toString());
        item.addProperty("scene",scene);item.addProperty("context",context);item.addProperty("configuration",configuration);
        Path metadata=image.resolveSibling(image.getFileName()+".image.json");
        Path temp=Files.createTempFile(image.getParent(),".image-index-",".tmp");
        try {Files.writeString(temp,new GsonBuilder().setPrettyPrinting().create().toJson(item)+"\n",StandardCharsets.UTF_8);Files.move(temp,metadata,StandardCopyOption.REPLACE_EXISTING);}
        finally {Files.deleteIfExists(temp);}
    }

    static boolean validImage(Path image) {
        if(!Files.isRegularFile(image))return false;
        try {var decoded=ImageIO.read(image.toFile());if(decoded==null)return false;decoded.flush();return true;}
        catch(IOException e) {return false;}
    }
    private static String normalize(String text) {return text.strip().replaceAll("\\s+"," ").toLowerCase(Locale.ROOT);}
    static double similarity(String first,String second) {
        if(normalize(first).equals(normalize(second)))return 1;
        var a=words(first);var b=words(second);
        if(a.isEmpty()||b.isEmpty())return 0;
        var common=new HashSet<>(a);common.retainAll(b);var union=new HashSet<>(a);union.addAll(b);
        return (double)common.size()/union.size();
    }
    private static Set<String> words(String text) {
        return Arrays.stream(normalize(text).split("[^\\p{L}\\p{N}]+"))
                .filter(word->word.length()>3).collect(Collectors.toSet());
    }
}
