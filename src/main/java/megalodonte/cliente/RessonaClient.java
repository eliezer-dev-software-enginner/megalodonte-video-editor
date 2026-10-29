package megalodonte.cliente;

import com.google.gson.*;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.function.Consumer;

/** Local Ressoa HTTP integration; one job per scene keeps narration aligned with the storyboard. */
final class RessonaClient {
    private final URI base;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    RessonaClient(String url) {
        base = URI.create(url.endsWith("/") ? url : url + "/");
        if (!"http".equals(base.getScheme()) && !"https".equals(base.getScheme()))
            throw new IllegalArgumentException("A URL do Ressoa precisa usar HTTP ou HTTPS.");
        if (base.getHost() == null) throw new IllegalArgumentException("URL do Ressoa inválida.");
    }

    boolean available() {
        try { request("GET", "api/status", null, Duration.ofSeconds(4)); return true; }
        catch (Exception ignored) { return false; }
    }

    List<JsonObject> engines() throws IOException, InterruptedException {
        var root = JsonParser.parseString(new String(request("GET", "api/engines", null, Duration.ofSeconds(15)))).getAsJsonObject();
        var result = new ArrayList<JsonObject>();
        for(var engine:root.getAsJsonArray("engines")) result.add(engine.getAsJsonObject());
        return result;
    }

    List<JsonObject> voices(String engine) throws IOException, InterruptedException {
        var root = JsonParser.parseString(new String(request("GET", "api/voices", null, Duration.ofSeconds(15)))).getAsJsonObject();
        var result = new ArrayList<JsonObject>();
        for (var voice : root.getAsJsonArray("voices")) {
            var item = voice.getAsJsonObject();
            for (var supported : item.getAsJsonArray("engine_ids"))
                if (engine.equals(supported.getAsString())) { result.add(item); break; }
        }
        return result;
    }

    void synthesize(String text, String engine, String voice, Path target, Consumer<String> progress)
            throws IOException, InterruptedException {
        synthesize(text,engine,voice,"",target,progress);
    }

    void synthesize(String text, String engine, String voice, String referenceText, Path target, Consumer<String> progress)
            throws IOException, InterruptedException {
        var body = new JsonObject();
        body.addProperty("text", text);
        body.addProperty("engine_id", engine);
        body.addProperty("voice_id", voice);
        if(!referenceText.isBlank()) body.addProperty("reference_text",referenceText);
        body.addProperty("language", "pt");
        body.addProperty("pause_ms", 0);
        body.addProperty("auto_delivery", false);
        var job = JsonParser.parseString(new String(request("POST", "api/jobs", body.toString(), Duration.ofSeconds(30)))).getAsJsonObject();
        String id = job.get("id").getAsString();
        if (!id.matches("[a-f0-9]{32}")) throw new IOException("O Ressoa retornou um ID de trabalho inválido.");
        long deadline = System.nanoTime() + Duration.ofMinutes(45).toNanos();
        String status = "";
        while (System.nanoTime() < deadline) {
            job = JsonParser.parseString(new String(request("GET", "api/jobs/" + id, null, Duration.ofSeconds(15)))).getAsJsonObject();
            String current = job.get("status").getAsString();
            if (!current.equals(status)) { progress.accept("Ressoa: " + current); status = current; }
            if ("completed".equals(current)) break;
            if (Set.of("failed", "cancelled", "interrupted").contains(current))
                throw new IOException("O Ressoa não concluiu a narração: " + current + " — " + error(job));
            Thread.sleep(1500);
        }
        if (!"completed".equals(status)) throw new IOException("Tempo limite de 45 minutos para a narração no Ressoa.");
        Files.createDirectories(target.toAbsolutePath().getParent());
        Path temporary=Files.createTempFile(target.toAbsolutePath().getParent(),".ressona-",".wav");
        try {
            var download=HttpRequest.newBuilder(base.resolve("api/jobs/"+id+"/audio?format=wav"))
                    .timeout(Duration.ofMinutes(10)).GET().build();
            var response=http.send(download,HttpResponse.BodyHandlers.ofFile(temporary));
            if(response.statusCode()!=200) throw new IOException("Ressoa não entregou o WAV: HTTP "+response.statusCode());
            if(Files.size(temporary)<44) throw new IOException("O Ressoa retornou um WAV vazio.");
            byte[] header=new byte[4];
            try(var file=Files.newInputStream(temporary)) { if(file.read(header)!=4) throw new IOException("WAV incompleto."); }
            if(header[0]!='R'||header[1]!='I'||header[2]!='F'||header[3]!='F') throw new IOException("O Ressoa não retornou um WAV válido.");
            Files.move(temporary,target,StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporary); }
    }

    private static String error(JsonObject job) {
        return job.has("error") && !job.get("error").isJsonNull() ? job.get("error").getAsString() : "sem detalhe";
    }

    private byte[] request(String method, String path, String body, Duration timeout) throws IOException, InterruptedException {
        var builder = HttpRequest.newBuilder(base.resolve(path)).timeout(timeout);
        if (body == null) builder.GET();
        else builder.header("Content-Type", "application/json; charset=UTF-8").POST(HttpRequest.BodyPublishers.ofString(body));
        var response = http.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String detail = new String(response.body());
            if (detail.length() > 300) detail = detail.substring(0, 300);
            throw new IOException("Ressoa HTTP " + response.statusCode() + ": " + detail);
        }
        return response.body();
    }
}
