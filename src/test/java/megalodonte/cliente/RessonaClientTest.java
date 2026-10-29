package megalodonte.cliente;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class RessonaClientTest {
    @Test void generatesOneSceneThroughRessonaProtocol() throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        var body=new AtomicReference<String>();
        String id="0123456789abcdef0123456789abcdef";
        server.createContext("/api/status",exchange->{ exchange.sendResponseHeaders(200,2);exchange.getResponseBody().write("{}".getBytes());exchange.close(); });
        server.createContext("/api/engines",exchange->{
            byte[] response="{\"engines\":[{\"id\":\"supertonic3\",\"name\":\"Supertonic 3\",\"available\":true,\"status\":\"ready\"},{\"id\":\"omnivoice\",\"name\":\"OmniVoice\",\"available\":false,\"status\":\"missing\"}]}".getBytes();
            exchange.sendResponseHeaders(200,response.length);exchange.getResponseBody().write(response);exchange.close();
        });
        server.createContext("/api/voices",exchange->{
            byte[] response="{\"voices\":[{\"id\":\"supertonic3-M1\",\"name\":\"M1\",\"engine_ids\":[\"supertonic3\"]}]}".getBytes();
            exchange.sendResponseHeaders(200,response.length);exchange.getResponseBody().write(response);exchange.close();
        });
        server.createContext("/api/jobs",exchange->{
            if(exchange.getRequestMethod().equals("POST")) {
                body.set(new String(exchange.getRequestBody().readAllBytes()));
                byte[] response=("{\"id\":\""+id+"\"}").getBytes();
                exchange.sendResponseHeaders(201,response.length);exchange.getResponseBody().write(response);
            } else {
                byte[] response="{\"status\":\"completed\"}".getBytes();
                exchange.sendResponseHeaders(200,response.length);exchange.getResponseBody().write(response);
            }
            exchange.close();
        });
        server.createContext("/api/jobs/"+id+"/audio",exchange->{
            byte[] wav=new byte[44];wav[0]='R';wav[1]='I';wav[2]='F';wav[3]='F';
            exchange.sendResponseHeaders(200,wav.length);exchange.getResponseBody().write(wav);exchange.close();
        });
        server.start();
        try {
            var client=new RessonaClient("http://127.0.0.1:"+server.getAddress().getPort());
            assertTrue(client.available());
            assertEquals(2,client.engines().size());
            assertTrue(client.engines().getFirst().get("available").getAsBoolean());
            assertEquals("supertonic3-M1",client.voices("supertonic3").getFirst().get("id").getAsString());
            Path file=Files.createTempFile("ressona-test-",".wav");
            try {
                client.synthesize("Primeira cena.","supertonic3","supertonic3-M1",file,ignored->{});
                assertEquals(44,Files.size(file));
                assertTrue(body.get().contains("\"pause_ms\":0"));
                assertTrue(body.get().contains("\"auto_delivery\":false"));
                assertTrue(body.get().contains("Primeira cena."));
            } finally { Files.deleteIfExists(file); }
        } finally { server.stop(0); }
    }

    @Test void localImagesMustCoverScenesWithoutAvatar() throws Exception {
        var project=VideoProject.fromScript("Primeira cena.\n\nSegunda cena.");
        Path folder=Files.createTempDirectory("video-cli-images-");
        try {
            Files.write(folder.resolve("001.png"),new byte[]{1});
            assertThrows(java.io.IOException.class,()->VideoCli.assignImages(project,folder));
            Files.write(folder.resolve("002.png"),new byte[]{1});
            VideoCli.assignImages(project,folder);
            assertTrue(project.clips.get(0).image.endsWith("001.png"));
            assertTrue(project.clips.get(1).image.endsWith("002.png"));
        } finally {
            try(var files=Files.list(folder)) { for(var file:files.toList()) Files.delete(file); }
            Files.delete(folder);
        }
    }

    @Test void scriptTerminatorAcceptsMixedCaseAndSurroundingSpaces() {
        assertTrue(VideoCli.isScriptTerminator("::fim"));
        assertTrue(VideoCli.isScriptTerminator("::Fim"));
        assertTrue(VideoCli.isScriptTerminator("  ::FIM  "));
        assertFalse(VideoCli.isScriptTerminator("A palavra ::fim está no roteiro."));
    }
}
