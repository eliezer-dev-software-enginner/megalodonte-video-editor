package megalodonte.cliente;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Properties;
import megalodonte.config.RuntimeEnvironment;

/** Sends export results using the user's private local bot configuration. */
final class TelegramExportNotifier {
    private static final String DEFAULT_CHAT_ID="-1003950566907";
    private TelegramExportNotifier() { }

    static void send(String message) throws IOException, InterruptedException {
        var environment=RuntimeEnvironment.forAppOrProject("megalodonte-video-editor");
        String token=environment.get("MEGALODONTE_TELEGRAM_BOT_TOKEN");
        String chat=environment.get("MEGALODONTE_TELEGRAM_CHAT_ID");
        if(token.isBlank()) {
            Path legacy=Path.of(System.getProperty("user.home"),".config","megalodonte","telegram.properties");
            if(Files.isRegularFile(legacy)) {
                var values=new Properties();
                try(var input=Files.newInputStream(legacy)) { values.load(input); }
                token=values.getProperty("botToken","");
                if(chat.isBlank()) chat=values.getProperty("chatId","");
            }
        }
        if(chat.isBlank()) chat=DEFAULT_CHAT_ID;
        if(!token.matches("[0-9]+:[A-Za-z0-9_-]+")) throw new IOException("Bot do Telegram não configurado.");
        if(!chat.matches("-?[0-9]+")) throw new IOException("ID do grupo Telegram inválido.");
        var body=new JsonObject();body.addProperty("chat_id",chat);body.addProperty("text",message);
        var request=HttpRequest.newBuilder(URI.create("https://api.telegram.org/bot"+token+"/sendMessage"))
                .timeout(Duration.ofSeconds(20)).header("Content-Type","application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(),StandardCharsets.UTF_8)).build();
        var client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        var response=client.send(request,HttpResponse.BodyHandlers.ofString());
        if(response.statusCode()!=200 || !response.body().matches("(?s).*\\\"ok\\\"\\s*:\\s*true.*"))
            throw new IOException("Telegram recusou a notificação (HTTP "+response.statusCode()+").");
    }
}
