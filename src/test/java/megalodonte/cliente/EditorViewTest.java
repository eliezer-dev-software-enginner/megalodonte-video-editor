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
        System.setProperty("megalodonte.editor.settingsDir",Files.createTempDirectory("editor-preferences-").toString());
        var ready = new CountDownLatch(1); Platform.startup(() -> { Platform.setImplicitExit(false); ready.countDown(); }); assertTrue(ready.await(15, TimeUnit.SECONDS));
        var task = new FutureTask<Void>(() -> {
            ThemeManager.setTheme(new DefaultTheme());
            try (var app = new ScriptApplication(AppConfig.load(Editor.class.getClassLoader()), Editor.class.getClassLoader())) {
                var root = (Parent) app.render().getJavaFxNode(); new Scene(root,1320,820); app.onMount();
                var session = app.current();
                assertEquals("15.0",session.states().get("volumeMusica"));
                session.states().set("volumeMusica","101"); session.invoke("applyVolumes"); assertTrue(session.states().string("mensagem").get().contains("entre 0 e 100"));
                session.states().set("volumeMusica","15"); session.states().set("volumeNarracao","80"); session.invoke("applyVolumes");
                assertTrue(session.states().string("mensagem").get().contains("Volumes aplicados"));
                assertEquals("Exportar vídeo",((Button)session.component("exportButton").getJavaFxNode()).getText());
                assertTrue(((Button)session.component("cancelExportButton").getJavaFxNode()).isVisible()==false);
                // Cancel a real save picker while a JavaFX heartbeat runs: no frozen FX thread or saved preference.
                var chooserObserved=new java.util.concurrent.atomic.AtomicBoolean();
                var heartbeat=new java.util.concurrent.atomic.AtomicBoolean();
                var pulse=new javafx.animation.PauseTransition(javafx.util.Duration.millis(80)); pulse.setOnFinished(event->heartbeat.set(true)); pulse.play();
                javax.swing.SwingUtilities.invokeLater(()-> {
                    var timer=new javax.swing.Timer(180,null);
                    timer.addActionListener(event-> {
                        for(var window:java.awt.Window.getWindows()) if(window.isShowing()) {
                            var chooser=findChooser(window);
                            if(chooser!=null) {
                                chooserObserved.set(chooser.getDialogType()==javax.swing.JFileChooser.SAVE_DIALOG && "Save".equals(chooser.getApproveButtonText()));
                                chooser.cancelSelection(); timer.stop(); return;
                            }
                        }
                    }); timer.start();
                });
                session.invoke("saveProject");
                assertTrue(chooserObserved.get()); assertTrue(heartbeat.get(),"JavaFX must remain responsive while picker is open");
                assertTrue(new LastProjectStore().lastSaved().isEmpty());
                var area = (TextArea) session.component("scriptInput").getJavaFxNode();
                ((Button) session.component("createScenes").getJavaFxNode()).fire(); assertTrue(session.states().string("mensagem").get().contains("obrigatório"));
                area.setText("Uma cidade ao amanhecer.\n\nUma pessoa atravessa a praça.\n\nO sol ilumina os prédios.");
                ((Button) session.component("createScenes").getJavaFxNode()).fire(); assertEquals(0, session.states().get("selecionada"));
                var fixture = Path.of("build/preview/fixture.png").toAbsolutePath(); Files.createDirectories(fixture.getParent());
                ImageIO.write(new BufferedImage(16,16,BufferedImage.TYPE_INT_RGB), "png", fixture.toFile());
                session.states().set("imagem", fixture.toString());
                var preview = (Pane) session.component("preview").getJavaFxNode();
                assertNotNull(((javafx.scene.image.ImageView)((StackPane)preview.getChildren().getFirst()).getChildren().getFirst()).getImage());
                session.states().set("imagem", "");
                var timeline = (megalodonte.components.v2.HorizontalScroll) session.component("timeline"); var cards = (HBox) timeline.getContent(); assertEquals(3,cards.getChildren().size());
                session.states().set("duracao", "8,5"); session.invoke("applyDuration"); assertTrue(session.states().string("resumo").get().contains("18.5"));
                session.invoke("moveRight"); assertEquals(1,session.states().get("selecionada")); assertTrue(session.states().string("cenaTexto").get().contains("cidade"));
                session.states().set("duracao", "NaN"); session.invoke("applyDuration"); assertTrue(session.states().string("mensagem").get().contains("Informe"));
                session.invoke("removeScene"); assertEquals(2,cards.getChildren().size());
                for (int[] size : new int[][]{{1320,820},{1180,780},{920,600},{1920,1080}}) {
                    ((Region)root).resize(size[0],size[1]); root.applyCss(); root.layout();
                    var scrollNode = session.component("scriptScroll").getJavaFxNode();
                    var bounds = root.sceneToLocal(scrollNode.localToScene(scrollNode.getBoundsInLocal()));
                    assertTrue(root.getLayoutBounds().getWidth() <= size[0] && root.getLayoutBounds().getHeight() <= size[1], "Root must fit window");
                    assertTrue(bounds.getMaxX() <= size[0] && bounds.getMaxY() <= size[1], "Script bounds: " + bounds);
                    var frame = (StackPane)preview.getChildren().getFirst();
                    assertEquals(16.0/9,frame.getWidth()/frame.getHeight(), .001);
                    var screenshot = root.snapshot(null,null); var png = new BufferedImage((int)screenshot.getWidth(),(int)screenshot.getHeight(),BufferedImage.TYPE_INT_ARGB);
                    for(int y=0;y<png.getHeight();y++)for(int x=0;x<png.getWidth();x++)png.setRGB(x,y,screenshot.getPixelReader().getArgb(x,y));
                    Path path = Path.of("build/preview/editor-"+size[0]+".png"); Files.createDirectories(path.getParent()); ImageIO.write(png,"png",path.toFile());
                }
                session.invoke("rewind");
                ((Button)session.component("playButton").getJavaFxNode()).fire();
                assertEquals("Ⅱ Pausar",session.states().get("playLabel"));
                ((Button)session.component("playButton").getJavaFxNode()).fire();
                assertEquals("▶ Play",session.states().get("playLabel"));
                assertEquals("0.0s / 10.0s",session.states().get("tempo"));
                var editorScroll = (megalodonte.components.v2.Scroll)session.component("editorScroll");
                ((Region)root).resize(1320,820); root.applyCss(); root.layout(); editorScroll.scrollTo(10000); root.layout();
                var volumeBounds = root.sceneToLocal(session.component("musicVolume").getJavaFxNode().localToScene(session.component("musicVolume").getJavaFxNode().getBoundsInLocal()));
                var viewportBounds = root.sceneToLocal(editorScroll.getJavaFxNode().localToScene(editorScroll.getJavaFxNode().getBoundsInLocal()));
                assertTrue(volumeBounds.getMinY()>=viewportBounds.getMinY() && volumeBounds.getMaxY()<=viewportBounds.getMaxY());
                var audioImage = root.snapshot(null,null); var audioPng = new BufferedImage((int)audioImage.getWidth(),(int)audioImage.getHeight(),BufferedImage.TYPE_INT_ARGB);
                for(int y=0;y<audioPng.getHeight();y++)for(int x=0;x<audioPng.getWidth();x++)audioPng.setRGB(x,y,audioImage.getPixelReader().getArgb(x,y));
                ImageIO.write(audioPng,"png",Path.of("build/preview/editor-audio.png").toFile());
                ((Region)root).resize(920,600); root.applyCss(); root.layout();
                editorScroll.scrollTo(10000); assertTrue(editorScroll.getScrollY()>0); editorScroll.scrollTo(0);
                // A long sequence must remain wider than its viewport and accept wheel gestures.
                session.states().set("timeline", "[" + String.join(",", java.util.Collections.nCopies(20,"{\"label\":\"Cena\",\"source\":\"\",\"duration\":5}")) + "]");
                root.applyCss(); root.layout(); timeline.getJavaFxNode().getScene().getRoot().layout();
                timeline.scrollTo(1000); assertTrue(timeline.getScrollX()>0);
                double before = timeline.getScrollX();
                javafx.event.Event.fireEvent(timeline.getJavaFxNode(),new javafx.scene.input.ScrollEvent(javafx.scene.input.ScrollEvent.SCROLL,0,0,0,0,false,false,false,false,false,false,0,-60,0,-60,javafx.scene.input.ScrollEvent.HorizontalTextScrollUnits.NONE,0,javafx.scene.input.ScrollEvent.VerticalTextScrollUnits.NONE,0,0,null));
                assertTrue(timeline.getScrollX()>before);
                session.invoke("removeScene"); session.invoke("removeScene"); assertEquals(-1,session.states().get("selecionada")); assertEquals(true,session.states().get("semSelecao"));
            }
            return null;
        });
        try {
            Platform.runLater(task); task.get(30,TimeUnit.SECONDS);
            var playbackCheck = new FutureTask<CompletableFuture<Void>>(() -> {
                var done = new CompletableFuture<Void>();
                var app = new ScriptApplication(AppConfig.load(Editor.class.getClassLoader()),Editor.class.getClassLoader());
                new Scene((Parent)app.render().getJavaFxNode(),1320,820); app.onMount();
                var session = app.current(); session.states().set("roteiro","Cena inicial\n\nCena final"); session.invoke("onSubmit");
                session.states().set("duracao","0.1"); session.invoke("applyDuration");
                session.states().set("selecionada",1); session.invoke("selectScene");
                session.states().set("duracao","0.1"); session.invoke("applyDuration"); session.invoke("rewind"); session.invoke("togglePlay");
                var wait = new javafx.animation.PauseTransition(javafx.util.Duration.millis(700));
                wait.setOnFinished(event -> {
                    try {
                        assertEquals(1,session.states().get("selecionada"));
                        assertEquals("0.2s / 0.2s",session.states().get("tempo"));
                        assertEquals("▶ Play",session.states().get("playLabel"));
                        session.invoke("togglePlay"); assertEquals("0.0s / 0.2s",session.states().get("tempo"));
                        session.invoke("rewind"); assertEquals(0,session.states().get("selecionada"));
                        done.complete(null);
                    } catch (Throwable error) { done.completeExceptionally(error); }
                    finally { app.close(); }
                }); wait.play(); return done;
            });
            Platform.runLater(playbackCheck); playbackCheck.get(10,TimeUnit.SECONDS).get(10,TimeUnit.SECONDS);
            var restoreCheck=new FutureTask<Void>(() -> {
                var stored=VideoProject.fromScript("Roteiro restaurado\n\nSegunda cena"); stored.music.volume=.07;
                Path file=Path.of(System.getProperty("megalodonte.editor.settingsDir"),"restaurado.json"); stored.save(file);
                new LastProjectStore().remember(file);
                try (var app=new ScriptApplication(AppConfig.load(Editor.class.getClassLoader()),Editor.class.getClassLoader())) {
                    app.render(); app.onMount(); var session=app.current();
                    assertEquals(stored.script,session.states().get("roteiro"));
                    assertEquals(7,Double.parseDouble(session.states().string("volumeMusica").get()),.0001);
                    assertTrue(session.states().string("resumo").get().startsWith("2 cenas"));
                    assertTrue(session.states().string("mensagem").get().contains("restaurado"));
                    session.states().set("roteiro","Salvo no projeto restaurado"); session.invoke("saveProject");
                    assertEquals("Salvo no projeto restaurado",VideoProject.load(file).script);
                }
                Files.delete(file);
                try (var app=new ScriptApplication(AppConfig.load(Editor.class.getClassLoader()),Editor.class.getClassLoader())) {
                    app.render(); app.onMount();
                    assertEquals("",app.current().states().get("roteiro"));
                    assertTrue(app.current().states().string("mensagem").get().contains("abriu vazio"));
                }
                return null;
            }); Platform.runLater(restoreCheck); restoreCheck.get(10,TimeUnit.SECONDS);
        } finally { Platform.exit(); }
    }
    private static javax.swing.JFileChooser findChooser(java.awt.Component component) {
        if(component instanceof javax.swing.JFileChooser chooser) return chooser;
        if(component instanceof java.awt.Container container) for(var child:container.getComponents()) {
            var found=findChooser(child); if(found!=null) return found;
        }
        return null;
    }

}
