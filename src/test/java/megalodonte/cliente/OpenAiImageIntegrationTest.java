package megalodonte.cliente;

import com.google.gson.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.*;
import static org.junit.jupiter.api.Assertions.*;

class OpenAiImageIntegrationTest {
    @TempDir Path directory;
    @Test void requestResponseFilesAndProjectTimelineStayOrdered() throws Exception {
        var image=new BufferedImage(32,18,BufferedImage.TYPE_INT_RGB); var bytes=new ByteArrayOutputStream();ImageIO.write(image,"png",bytes);
        var prompts=new ArrayList<String>(); var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/v1/images/generations",exchange-> {
            var request=JsonParser.parseString(new String(exchange.getRequestBody().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            assertEquals("Bearer dummy-test-key",exchange.getRequestHeaders().getFirst("Authorization"));
            assertEquals("gpt-image-2",request.get("model").getAsString());assertEquals("1536x864",request.get("size").getAsString());assertEquals(1,request.get("n").getAsInt());
            assertFalse(request.has("response_format"));prompts.add(request.get("prompt").getAsString());
            byte[] response=("{\"data\":[{\"b64_json\":\""+Base64.getEncoder().encodeToString(bytes.toByteArray())+"\"}]}").getBytes();
            exchange.sendResponseHeaders(200,response.length);exchange.getResponseBody().write(response);exchange.close();
        });server.start();
        try {
            var project=VideoProject.fromScript("Cidade ao amanhecer.\n\nBicicleta na praça.");
            var client=new OpenAiImageClient("dummy-test-key","gpt-image-2","1536x864","low",URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/v1/images/generations"));
            new ImageGenerationJob(client).run(project,directory.resolve("generated"),(index,file)->project.clips.get(index).image=file.toString(),progress->{});
            assertEquals(2,prompts.size());assertTrue(prompts.get(0).endsWith("Cidade ao amanhecer."));assertTrue(prompts.get(1).endsWith("Bicicleta na praça."));
            assertNotEquals(project.clips.get(0).image,project.clips.get(1).image);
            for(var clip:project.clips) assertNotNull(ImageIO.read(Path.of(clip.image).toFile()));
            Path manifest=directory.resolve("project.json");project.save(manifest);var loaded=VideoProject.load(manifest);
            var timeline=JsonParser.parseString(loaded.timeline()).getAsJsonArray();assertEquals(2,timeline.size());
            assertTrue(timeline.get(0).getAsJsonObject().get("label").getAsString().contains("Cidade"));
            assertNotNull(ImageIO.read(Path.of(loaded.clips.get(0).image).toFile()));
            assertFalse(Files.readString(manifest).contains("dummy-test-key"));
            assertThrows(IllegalArgumentException.class,()->ImageGenerationJob.pending(project));
        } finally { server.stop(0); }
    }
    @Test void apiErrorsAndInvalidPayloadDoNotProduceImagesOrLeakKeys() throws Exception {
        var code=new AtomicInteger(401);var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",exchange->{exchange.getRequestBody().readAllBytes();byte[] body="{\"data\":[{\"b64_json\":\"bad\"}]}".getBytes();exchange.sendResponseHeaders(code.get(),body.length);exchange.getResponseBody().write(body);exchange.close();});server.start();
        try {
            var client=new OpenAiImageClient("dummy-secret","gpt-image-2","1536x864","low",URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/"));
            assertFalse(assertThrows(IOException.class,()->client.generate("Cena")).getMessage().contains("dummy-secret"));
            code.set(429);assertTrue(assertThrows(IOException.class,()->client.generate("Cena")).getMessage().contains("429"));
            code.set(200);assertThrows(IOException.class,()->client.generate("Cena"));
            var job=new ImageGenerationJob(client);job.cancel();
            assertThrows(java.util.concurrent.CancellationException.class,()->job.run(VideoProject.fromScript("Cena"),directory,(i,p)->fail(),progress->{}));
            assertThrows(IllegalArgumentException.class,()->ImageGenerationJob.pending(VideoProject.fromScript("1\n\n2\n\n3\n\n4")));
        } finally { server.stop(0); }
    }
}
