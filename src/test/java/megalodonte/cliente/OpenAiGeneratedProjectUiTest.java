package megalodonte.cliente;

import javafx.application.Platform;
import javafx.scene.*;
import javafx.scene.layout.*;
import javafx.scene.image.ImageView;
import megalodonte.base.theme.ThemeManager;
import megalodonte.theme.DefaultTheme;
import megalodonte.script.*;
import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.util.concurrent.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in render of a real API-generated project; this test itself never calls the API. */
class OpenAiGeneratedProjectUiTest {
    @Test void opensActualGeneratedImagesInEditorPreviewAndTimeline() throws Exception {
        String path=System.getProperty("megalodonte.live.project","");
        Assumptions.assumeTrue(!path.isBlank(),"Use -PliveProject para verificar um projeto gerado pela API real");
        Path manifest=Path.of(path).toAbsolutePath();var expected=VideoProject.load(manifest);
        assertFalse(expected.clips.isEmpty());
        System.setProperty("megalodonte.editor.initialProject",manifest.toString());
        System.setProperty("megalodonte.editor.settingsDir",Files.createTempDirectory("openai-ui-settings-").toString());
        var ready=new CountDownLatch(1);Platform.startup(()-> { Platform.setImplicitExit(false);ready.countDown(); }); assertTrue(ready.await(15,TimeUnit.SECONDS));
        var task=new FutureTask<Void>(()-> {
            ThemeManager.setTheme(new DefaultTheme());
            try(var app=new ScriptApplication(AppConfig.load(Editor.class.getClassLoader()),Editor.class.getClassLoader())) {
                var root=(Parent)app.render().getJavaFxNode();new Scene(root,1320,820);app.onMount();((Region)root).resize(1320,820);
                var session=app.current();assertEquals(expected.script,session.states().get("roteiro"));
                session.invoke("generateImages"); assertTrue(session.states().string("mensagem").get().contains("Todas as cenas"));
                var sequence=(megalodonte.components.v2.HorizontalScroll)session.component("timeline");
                assertEquals(expected.clips.size(),((HBox)sequence.getContent()).getChildren().size());
                var effects=(megalodonte.components.v2.HorizontalScroll)session.component("effectsTimeline");
                assertEquals(expected.effects.size(),((Pane)effects.getContent()).getChildren().stream().filter(n->n.getUserData() instanceof Integer).count());
                if(!expected.effects.isEmpty()) {
                    session.states().set("efeitoSelecionado",0);session.invoke("selectEffect");root.applyCss();root.layout();
                    var editorScroll=(megalodonte.components.v2.Scroll)session.component("editorScroll");editorScroll.scrollTo(640);root.layout();
                    var shot=root.snapshot(null,null);var png=new BufferedImage((int)shot.getWidth(),(int)shot.getHeight(),BufferedImage.TYPE_INT_ARGB);
                    for(int y=0;y<png.getHeight();y++)for(int x=0;x<png.getWidth();x++)png.setRGB(x,y,shot.getPixelReader().getArgb(x,y));
                    Files.createDirectories(Path.of("build/preview"));ImageIO.write(png,"png",Path.of("build/preview/live-effects.png").toFile());editorScroll.scrollTo(0);
                }
                for(int i=0;i<expected.clips.size();i++) {
                    session.states().set("selecionada",i);session.invoke("selectScene");root.applyCss();root.layout();
                    assertEquals(expected.clips.get(i).image,session.states().get("imagem"));
                    var preview=(Pane)session.component("preview").getJavaFxNode();var frame=(StackPane)preview.getChildren().getFirst();
                    var image=((ImageView)frame.getChildren().getFirst()).getImage();assertNotNull(image);assertFalse(image.isError());
                    assertEquals(16.0/9,image.getWidth()/image.getHeight(),.001);
                    var snapshot=root.snapshot(null,null);var png=new BufferedImage((int)snapshot.getWidth(),(int)snapshot.getHeight(),BufferedImage.TYPE_INT_ARGB);
                    for(int y=0;y<png.getHeight();y++)for(int x=0;x<png.getWidth();x++)png.setRGB(x,y,snapshot.getPixelReader().getArgb(x,y));
                    Path output=Path.of("build/preview/openai-live-scene-"+(i+1)+".png");Files.createDirectories(output.getParent());ImageIO.write(png,"png",output.toFile());
                }
            }
            return null;
        });
        try { Platform.runLater(task);task.get(30,TimeUnit.SECONDS); }
        finally { Platform.exit();System.clearProperty("megalodonte.editor.initialProject"); }
    }
}
