package megalodonte.cliente;
import javafx.application.Platform;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import megalodonte.base.theme.ThemeManager;
import megalodonte.theme.DefaultTheme;
import megalodonte.script.*;
import org.junit.jupiter.api.Test;
import java.util.concurrent.*;
import java.nio.file.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import static org.junit.jupiter.api.Assertions.*;
class EditorViewTest {
    @Test void editsTimelineThroughDeclarativeControlsAndRendersPreview() throws Exception {
        var ready = new CountDownLatch(1); Platform.startup(() -> { Platform.setImplicitExit(false); ready.countDown(); }); assertTrue(ready.await(15, TimeUnit.SECONDS));
        var task = new FutureTask<Void>(() -> {
            ThemeManager.setTheme(new DefaultTheme());
            try (var app = new ScriptApplication(AppConfig.load(Editor.class.getClassLoader()), Editor.class.getClassLoader())) {
                var root = (Parent) app.render().getJavaFxNode(); new Scene(root,1320,820); app.onMount();
                var session = app.current(); var area = (TextArea) session.component("scriptInput").getJavaFxNode();
                ((Button) session.component("createScenes").getJavaFxNode()).fire(); assertTrue(session.states().string("mensagem").get().contains("obrigatório"));
                area.setText("Uma cidade ao amanhecer.\n\nUma pessoa atravessa a praça.\n\nO sol ilumina os prédios.");
                ((Button) session.component("createScenes").getJavaFxNode()).fire(); assertEquals(0, session.states().get("selecionada"));
                var fixture = Path.of("build/preview/fixture.png").toAbsolutePath(); Files.createDirectories(fixture.getParent());
                ImageIO.write(new BufferedImage(16,16,BufferedImage.TYPE_INT_RGB), "png", fixture.toFile());
                session.states().set("imagem", fixture.toString());
                var preview = (StackPane) session.component("preview").getJavaFxNode();
                assertNotNull(((javafx.scene.image.ImageView)preview.getChildren().getFirst()).getImage());
                session.states().set("imagem", "");
                var timeline = (ScrollPane) session.component("timeline").getJavaFxNode(); var cards = (HBox) timeline.getContent(); assertEquals(3,cards.getChildren().size());
                session.states().set("duracao", "8,5"); session.invoke("applyDuration"); assertTrue(session.states().string("resumo").get().contains("18.5"));
                session.invoke("moveRight"); assertEquals(1,session.states().get("selecionada")); assertTrue(session.states().string("cenaTexto").get().contains("cidade"));
                session.states().set("duracao", "NaN"); session.invoke("applyDuration"); assertTrue(session.states().string("mensagem").get().contains("Informe"));
                session.invoke("removeScene"); assertEquals(2,cards.getChildren().size());
                for (int[] size : new int[][]{{1320,820},{1180,780}}) {
                    ((Region)root).resize(size[0],size[1]); root.applyCss(); root.layout();
                    var bounds = root.sceneToLocal(area.localToScene(area.getBoundsInLocal()));
                    assertTrue(root.getLayoutBounds().getWidth() <= size[0] && root.getLayoutBounds().getHeight() <= size[1], "Root must fit window");
                    assertTrue(bounds.getMaxX() <= size[0] && bounds.getMaxY() <= size[1], "Script bounds: " + bounds);
                    var screenshot = root.snapshot(null,null); var png = new BufferedImage((int)screenshot.getWidth(),(int)screenshot.getHeight(),BufferedImage.TYPE_INT_ARGB);
                    for(int y=0;y<png.getHeight();y++)for(int x=0;x<png.getWidth();x++)png.setRGB(x,y,screenshot.getPixelReader().getArgb(x,y));
                    Path path = Path.of("build/preview/editor-"+size[0]+".png"); Files.createDirectories(path.getParent()); ImageIO.write(png,"png",path.toFile());
                }
                session.invoke("removeScene"); session.invoke("removeScene"); assertEquals(-1,session.states().get("selecionada")); assertEquals(true,session.states().get("semSelecao"));
            }
            return null;
        });
        try { Platform.runLater(task); task.get(30,TimeUnit.SECONDS); } finally { Platform.exit(); }
    }
}
