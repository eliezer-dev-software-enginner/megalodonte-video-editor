package megalodonte.cliente;

import com.google.gson.*;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import javax.imageio.ImageIO;

/** Images API adapter. Credentials are never persisted in a project or printed in errors. */
final class OpenAiImageClient {
    private final HttpClient http;
    private final URI endpoint;
    private final String key,model,size,quality;
    OpenAiImageClient(String key,String model,String size,String quality,URI endpoint) {
        if(key==null || key.isBlank()) throw new IllegalArgumentException("Informe a chave da API no editor ou configure OPENAI_API_KEY no ambiente.");
        this.key=key.strip();this.model=model;this.size=size;this.quality=quality;this.endpoint=endpoint;
        http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).followRedirects(HttpClient.Redirect.NEVER).build();
    }
    static OpenAiImageClient fromEnvironment() { return fromSessionKey(""); }
    static OpenAiImageClient fromSessionKey(String sessionKey) {
        String key=sessionKey==null || sessionKey.isBlank() ? System.getenv("OPENAI_API_KEY") : sessionKey;
        return new OpenAiImageClient(key,setting("OPENAI_IMAGE_MODEL","gpt-image-2"),
            setting("OPENAI_IMAGE_SIZE","1536x864"),setting("OPENAI_IMAGE_QUALITY","low"),URI.create("https://api.openai.com/v1/images/generations"));
    }
    private static String setting(String name,String fallback) { String value=System.getenv(name);return value==null || value.isBlank() ? fallback : value.strip(); }
    String cacheIdentity() { return model+"\n"+size+"\n"+quality; }
    byte[] generate(String prompt) throws IOException,InterruptedException {
        var body=new JsonObject();body.addProperty("model",model);body.addProperty("prompt",prompt);body.addProperty("n",1);
        body.addProperty("size",size);body.addProperty("quality",quality);body.addProperty("output_format","png");
        var request=HttpRequest.newBuilder(endpoint).timeout(Duration.ofMinutes(6))
            .header("Authorization","Bearer "+key).header("Content-Type","application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
        var response=http.send(request,HttpResponse.BodyHandlers.ofByteArray());
        if(response.statusCode()!=200) {
            String message=switch(response.statusCode()) {
            case 401 -> "A OpenAI rejeitou a chave da API (401).";
            case 403 -> "A conta não tem acesso ao modelo de imagens solicitado (403).";
            case 429 -> "A OpenAI informou limite ou saldo indisponível (429). Nenhuma retentativa automática foi feita.";
            default -> "A geração de imagem falhou na OpenAI (HTTP "+response.statusCode()+").";
            };
            String detail=errorDetail(response.body());
            if(!detail.isBlank()) message += " Detalhe: " + detail;
            throw new IOException(message);
        }
        if(response.body().length>64*1024*1024) throw new IOException("Resposta de imagem acima do limite de tamanho.");
        try {
            var json=JsonParser.parseString(new String(response.body(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            var data=json.getAsJsonArray("data");if(data==null || data.size()!=1) throw new IllegalArgumentException();
            byte[] bytes=Base64.getDecoder().decode(data.get(0).getAsJsonObject().get("b64_json").getAsString());
            var image=ImageIO.read(new ByteArrayInputStream(bytes));if(image==null) throw new IllegalArgumentException();
            var png=new ByteArrayOutputStream(); ImageIO.write(image,"png",png);image.flush();return png.toByteArray();
        } catch(RuntimeException e) { throw new IOException("A API não devolveu uma imagem válida em base64."); }
    }
    private String errorDetail(byte[] body) {
        try {
            var error=JsonParser.parseString(new String(body,java.nio.charset.StandardCharsets.UTF_8))
                .getAsJsonObject().getAsJsonObject("error");
            if(error==null || !error.has("message")) return "";
            String detail=error.get("message").getAsString().replace(key,"[chave ocultada]").replaceAll("\\s+"," ").strip();
            return detail.length()>400 ? detail.substring(0,400)+"…" : detail;
        } catch(RuntimeException ignored) { return ""; }
    }
}
