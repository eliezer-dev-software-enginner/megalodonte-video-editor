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
            var second=VideoProject.fromScript("Cidade ao amanhecer.\n\nBicicleta na praça.");
            new ImageGenerationJob(client).run(second,directory.resolve("other-project"),directory.resolve("generated"),(index,file)->second.clips.get(index).image=file.toString(),progress->{});
            assertEquals(2,prompts.size(),"As cenas com mesmo contexto devem reutilizar a biblioteca");
            assertTrue(Files.isRegularFile(Path.of(second.clips.getFirst().image)));
            assertTrue(Path.of(second.clips.getFirst().image).getFileName().toString().startsWith("cidade-amanhecer-"));
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
            assertEquals(List.of(0,1,2,3),ImageGenerationJob.pending(VideoProject.fromScript("1\n\n2\n\n3\n\n4")));
        } finally { server.stop(0); }
    }
    @Test void projectImagePromptReplacesDefaultInstructionsAndPersists() throws Exception {
        var image=new BufferedImage(32,18,BufferedImage.TYPE_INT_RGB); var bytes=new ByteArrayOutputStream();ImageIO.write(image,"png",bytes);
        var prompts=new ArrayList<String>(); var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",exchange-> {
            var request=JsonParser.parseString(new String(exchange.getRequestBody().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            prompts.add(request.get("prompt").getAsString());
            byte[] response=("{\"data\":[{\"b64_json\":\""+Base64.getEncoder().encodeToString(bytes.toByteArray())+"\"}]}").getBytes();
            exchange.sendResponseHeaders(200,response.length);exchange.getResponseBody().write(response);exchange.close();
        });server.start();
        try {
            var client=new OpenAiImageClient("dummy-test-key","gpt-image-2","1536x864","low",URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/"));
            Path library=directory.resolve("library");
            var standard=VideoProject.fromScript("Monitor de lotes.");
            new ImageGenerationJob(client).run(standard,directory.resolve("a"),library,(i,f)->standard.clips.get(i).image=f.toString(),p->{});
            assertTrue(prompts.get(0).startsWith(ImageGenerationJob.DEFAULT_PROMPT+" Contexto do roteiro: "));
            var custom=VideoProject.fromScript("Monitor de lotes.");custom.imagePrompt="  Ilustração corporativa flat em azul.  ";
            new ImageGenerationJob(client).run(custom,directory.resolve("b"),library,(i,f)->custom.clips.get(i).image=f.toString(),p->{});
            assertEquals(2,prompts.size(),"Um prompt próprio não reaproveita imagem gerada com o prompt padrão");
            assertTrue(prompts.get(1).startsWith("Ilustração corporativa flat em azul.\n\nContexto do roteiro: "));
            assertFalse(prompts.get(1).contains(ImageGenerationJob.DEFAULT_PROMPT));
            assertTrue(prompts.get(1).endsWith("Cena 1: Monitor de lotes."));
            var again=VideoProject.fromScript("Monitor de lotes.");again.imagePrompt="Ilustração corporativa flat em azul.";
            new ImageGenerationJob(client).run(again,directory.resolve("c"),library,(i,f)->again.clips.get(i).image=f.toString(),p->{});
            assertEquals(2,prompts.size(),"O mesmo prompt próprio reaproveita a biblioteca");
            Path manifest=directory.resolve("custom.mvideo.json");custom.save(manifest);
            assertEquals("Ilustração corporativa flat em azul.",VideoProject.load(manifest).imagePrompt);
            assertEquals("Ilustração corporativa flat em azul.",VideoProject.load(manifest).snapshot().imagePrompt);
            custom.imagePrompt="x".repeat(VideoProject.MAX_IMAGE_PROMPT+1);
            assertThrows(IllegalArgumentException.class,()->custom.save(directory.resolve("too-long.mvideo.json")));
        } finally { server.stop(0); }
    }
}
