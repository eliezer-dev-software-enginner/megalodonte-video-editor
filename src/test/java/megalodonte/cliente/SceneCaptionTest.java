package megalodonte.cliente;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.awt.Color;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class SceneCaptionTest {
    @TempDir Path directory;
    @Test void suggestionsAreShortAndDeterministic() {
        assertEquals("Visão geral",SceneCaption.suggest("Visão geral. Tudo começa quando a Hughes cria um arquivo CSV."));
        assertEquals("Bem-vindo ao manual de utilização da FLU004",
                SceneCaption.suggest("Bem-vindo ao manual de utilização da FLU004, a integração de locação entre o Protheus, o iPaaS e o Workfinity."));
        String longSentence=SceneCaption.suggest("Antes de gerar o pedido o sistema confere o cliente pelo CPF ou CNPJ e a configuração fiscal.");
        assertTrue(longSentence.endsWith("…"));assertTrue(longSentence.split(" ").length<=7);
        assertEquals("",SceneCaption.suggest("  "));
    }
    @Test void effectiveCaptionFollowsProjectOptionAndSceneText() {
        var project=VideoProject.fromScript("Visão geral. Detalhes.\n\nOutra cena.");
        assertEquals("",SceneCaption.effective(project,project.clips.getFirst()));
        project.sceneCaptions=true;
        assertEquals("Visão geral",SceneCaption.effective(project,project.clips.getFirst()));
        project.clips.get(1).caption="Meu texto";
        assertEquals("Meu texto",SceneCaption.effective(project,project.clips.get(1)));
        assertEquals("a b",SceneCaption.validate("  a \n b "));
        assertThrows(IllegalArgumentException.class,()->SceneCaption.validate("x".repeat(SceneCaption.MAX_LENGTH+1)));
    }
    @Test void captionsAndOptionPersistWithProject() throws Exception {
        var project=VideoProject.fromScript("Um.\n\nDois.");
        project.sceneCaptions=true;project.clips.get(1).caption="Etapa dois";
        Path manifest=directory.resolve("p.mvideo.json");project.save(manifest);
        var loaded=VideoProject.load(manifest);
        assertTrue(loaded.sceneCaptions);assertEquals("",loaded.clips.get(0).caption);assertEquals("Etapa dois",loaded.clips.get(1).caption);
        var copy=loaded.snapshot();assertTrue(copy.sceneCaptions);assertEquals("Etapa dois",copy.clips.get(1).caption);
        var root=com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(manifest)).getAsJsonObject();
        root.remove("sceneCaptions");
        for(var scene:root.getAsJsonArray("clips")) scene.getAsJsonObject().remove("caption");
        java.nio.file.Files.writeString(manifest,root.toString());
        var legacy=VideoProject.load(manifest);assertFalse(legacy.sceneCaptions);assertEquals("",legacy.clips.get(1).caption);
    }
    @Test void layerDrawsOnlyTheLowerLeftBox() {
        var layer=SceneCaption.render("Monitor de Integração",1920,1080);
        assertTrue(new Color(layer.getRGB(120,960),true).getAlpha()>150,"Caixa no canto inferior esquerdo");
        assertEquals(0,new Color(layer.getRGB(1800,100),true).getAlpha(),"Resto da camada transparente");
        assertEquals(0,new Color(SceneCaption.render("",320,180).getRGB(10,170),true).getAlpha());
        var wrapped=SceneCaption.render("Texto bem longo ".repeat(20),1920,1080);
        assertEquals(0,new Color(wrapped.getRGB(1400,960),true).getAlpha(),"Largura limitada a cerca de 62% do quadro");
    }
}
