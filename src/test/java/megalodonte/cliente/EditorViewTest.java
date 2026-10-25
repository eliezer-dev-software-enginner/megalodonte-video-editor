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
        var ready = new CountDownLatch(1);
        try { Platform.startup(() -> { Platform.setImplicitExit(false); ready.countDown(); }); }
        catch(IllegalStateException alreadyStarted) { Platform.runLater(() -> { Platform.setImplicitExit(false); ready.countDown(); }); }
        assertTrue(ready.await(15, TimeUnit.SECONDS));
        var task = new FutureTask<Void>(() -> {
            ThemeManager.setTheme(new DefaultTheme());
            try (var app = new ScriptApplication(AppConfig.load(Editor.class.getClassLoader()), Editor.class.getClassLoader())) {
                var root = (Parent) app.render().getJavaFxNode(); new Scene(root,1320,820); app.onMount();
                var session = app.current();
                assertEquals("Configurações",((Button)session.component("settingsButton").getJavaFxNode()).getText());
                assertEquals("Fechar projeto",((Button)session.component("closeProjectButton").getJavaFxNode()).getText());
                assertEquals("Aplicar sons",((Button)session.component("applySoundsButton").getJavaFxNode()).getText());
                assertThrows(IllegalArgumentException.class,()->session.component("openaiKeyInput"));
                Path reusableEffect=Path.of("build/preview/reusable-effect.wav").toAbsolutePath();
                Files.createDirectories(reusableEffect.getParent());
                byte[] silence=new byte[16000];
                var soundFormat=new javax.sound.sampled.AudioFormat(8000,16,1,true,false);
                try(var soundStream=new javax.sound.sampled.AudioInputStream(new java.io.ByteArrayInputStream(silence),soundFormat,8000)) {
                    javax.sound.sampled.AudioSystem.write(soundStream,javax.sound.sampled.AudioFileFormat.Type.WAVE,reusableEffect.toFile());
                }
                var manyEffects=new VideoProject();
                for(int i=0;i<100;i++) { var effect=new VideoProject.SoundEffect();effect.file=reusableEffect.toString();effect.start=i*10+.4;manyEffects.effects.add(effect); }
                try(var effectPreview=new AudioPreview()) {
                    effectPreview.load(manyEffects,error->fail(error));
                    assertTrue(effectPreview.loadedEffectPlayers()<=1);
                    effectPreview.seek(500);assertTrue(effectPreview.loadedEffectPlayers()<=1);
                    effectPreview.seek(0);assertTrue(effectPreview.loadedEffectPlayers()<=1);
                }
                session.invoke("openSettings");
                var settingsStage=(javafx.stage.Stage)javafx.stage.Window.getWindows().stream()
                    .filter(w->w instanceof javafx.stage.Stage s && s.getTitle().startsWith("Configurações")).findFirst().orElseThrow();
                var settingsRoot=settingsStage.getScene().getRoot(); settingsRoot.applyCss();settingsRoot.layout();
                var keyField=(PasswordField)settingsRoot.lookup(".password-field");
                var folderField=settingsRoot.lookupAll(".text-field").stream().filter(n->n instanceof TextField field && "Diretório de sons e efeitos".equals(field.getPromptText())).map(n->(TextField)n).findFirst().orElseThrow();
                var pauseField=settingsRoot.lookupAll(".text-field").stream().filter(n->n instanceof TextField field && "1,0".equals(field.getPromptText())).map(n->(TextField)n).findFirst().orElseThrow();
                var promptField=(TextArea)settingsRoot.lookup(".text-area");
                assertEquals(EditorSettings.DEFAULT_SOUNDS,folderField.getText());
                assertEquals("1.0",pauseField.getText());
                assertTrue(promptField.getText().contains("PRODUTOR DO PROJETO"));assertTrue(promptField.getText().contains("PLANO SONORO POR CENA"));
                for(String section:new String[]{"Efeitos visuais","Whisper","Avatar","Prompt base","Geral"}) {
                    var navigation=settingsRoot.lookupAll(".button").stream()
                        .filter(n->n instanceof Button b && b.getText().equals(section)).map(n->(Button)n).findFirst().orElseThrow();
                    navigation.fire();
                    assertTrue(navigation.getStyle().contains("#2563eb"));
                }
                assertEquals(EditorSettings.DEFAULT_SOUNDS,folderField.getText());
                var effectCoverage=settingsRoot.lookupAll(".combo-box").stream()
                    .filter(n->n instanceof ComboBox<?> box && box.getItems().contains("50% de efeitos sonoros"))
                    .map(n->(ComboBox<String>)n).findFirst().orElseThrow();
                assertEquals("100% de efeitos sonoros",effectCoverage.getValue());
                effectCoverage.setValue("50% de efeitos sonoros");
                keyField.setText("dummy-settings-key");promptField.setText("Prompt editado e salvo");
                folderField.setText("/diretorio-inexistente-para-teste");
                var saveSettings=settingsRoot.lookupAll(".button").stream().filter(n->n instanceof Button b && b.getText().equals("Salvar configurações")).map(n->(Button)n).findFirst().orElseThrow();
                saveSettings.fire();assertTrue(settingsStage.isShowing());assertFalse(new EditorSettings().exists());
                var soundsFolder=Files.createTempDirectory("editor-sounds-");folderField.setText(soundsFolder.toString());
                pauseField.setText("31");saveSettings.fire();assertTrue(settingsStage.isShowing());assertFalse(new EditorSettings().exists());
                pauseField.setText("1,7");
                var settingsShot=settingsRoot.snapshot(null,null);var settingsPng=new BufferedImage((int)settingsShot.getWidth(),(int)settingsShot.getHeight(),BufferedImage.TYPE_INT_ARGB);
                for(int y=0;y<settingsPng.getHeight();y++)for(int x=0;x<settingsPng.getWidth();x++)settingsPng.setRGB(x,y,settingsShot.getPixelReader().getArgb(x,y));
                Files.createDirectories(Path.of("build/preview"));ImageIO.write(settingsPng,"png",Path.of("build/preview/settings-window.png").toFile());
                saveSettings.fire();assertFalse(settingsStage.isShowing());assertEquals("dummy-settings-key",session.states().get("openaiKey"));
                var persisted=new EditorSettings().load();assertEquals(soundsFolder.toString(),persisted.soundsDirectory);assertEquals("Prompt editado e salvo",persisted.basePrompt);
                assertEquals(1.7,persisted.pauseCutThreshold);
                assertEquals(50,persisted.soundEffectPercent);
                try(var restarted=new ScriptApplication(AppConfig.load(Editor.class.getClassLoader()),Editor.class.getClassLoader())) {
                    restarted.render();restarted.onMount();assertEquals("dummy-settings-key",restarted.current().states().get("openaiKey"));
                }
                session.invoke("openSettings");
                var reopened=(javafx.stage.Stage)javafx.stage.Window.getWindows().stream().filter(w->w instanceof javafx.stage.Stage s && s.getTitle().startsWith("Configurações")).findFirst().orElseThrow();
                reopened.getScene().getRoot().applyCss();((TextArea)reopened.getScene().getRoot().lookup(".text-area")).setText("Não salvar");reopened.close();
                assertEquals("Prompt editado e salvo",new EditorSettings().load().basePrompt);
                Files.delete(Path.of(System.getProperty("megalodonte.editor.settingsDir"),"editor-settings.json"));session.states().set("openaiKey","");
                assertEquals("15.0",session.states().get("volumeMusica"));
                session.states().set("volumeMusica","101"); session.invoke("applyVolumes"); assertTrue(session.states().string("mensagem").get().contains("entre 0 e 100"));
                session.states().set("volumeMusica","15"); session.states().set("volumeNarracao","80"); session.invoke("applyVolumes");
                assertTrue(session.states().string("mensagem").get().contains("Volumes aplicados"));
                assertEquals("Exportar vídeo",((Button)session.component("exportButton").getJavaFxNode()).getText());
                assertEquals("Sincronizar com whisper",((Button)session.component("syncNarrationButton").getJavaFxNode()).getText());
                assertTrue(session.component("syncNarrationButton").getJavaFxNode().isDisabled());
                assertTrue(((Button)session.component("cancelExportButton").getJavaFxNode()).isVisible()==false);
                var area = (TextArea) session.component("scriptInput").getJavaFxNode();
                ((Button) session.component("createScenes").getJavaFxNode()).fire(); assertTrue(session.states().string("mensagem").get().contains("obrigatório"));
                area.setText("Uma cidade ao amanhecer.\n\nUma pessoa atravessa a praça.\n\nO sol ilumina os prédios.");
                ((Button) session.component("createScenes").getJavaFxNode()).fire(); assertEquals(0, session.states().get("selecionada"));
                var fixture = Path.of("build/preview/fixture.png").toAbsolutePath(); Files.createDirectories(fixture.getParent());
                ImageIO.write(new BufferedImage(16,16,BufferedImage.TYPE_INT_RGB), "png", fixture.toFile());
                session.states().set("imagem", fixture.toString());
                var preview = (megalodonte.components.ImagePreview) session.component("preview");
                assertTrue(preview.hasImage());
                ((Region)root).resize(1320,820);root.applyCss();root.layout();
                var previewFrame=(javafx.scene.layout.StackPane)((Pane)preview.getJavaFxNode()).getChildren().getFirst();
                assertNotNull(previewFrame.getClip(),"The 16:9 preview frame must clip animated image movement");
                assertEquals(previewFrame.getWidth(),((javafx.scene.shape.Rectangle)previewFrame.getClip()).getWidth(),.01);
                assertEquals(previewFrame.getHeight(),((javafx.scene.shape.Rectangle)previewFrame.getClip()).getHeight(),.01);
                var overlay=(javafx.scene.canvas.Canvas)((javafx.scene.layout.StackPane)((Pane)preview.getJavaFxNode()).getChildren().getFirst()).getChildren().stream()
                    .filter(javafx.scene.canvas.Canvas.class::isInstance).findFirst().orElseThrow();
                preview.setVisualEffect("particles",0,0);
                var first=overlay.snapshot(null,null);preview.setVisualEffect("particles",0,3);
                var later=overlay.snapshot(null,null);int changed=0;
                for(int y=0;y<(int)first.getHeight();y++)for(int x=0;x<(int)first.getWidth();x++)
                    if(first.getPixelReader().getArgb(x,y)!=later.getPixelReader().getArgb(x,y))changed++;
                assertTrue(changed>0,"Particle overlay must move in the editor preview");
                var imageNode=(javafx.scene.image.ImageView)((javafx.scene.layout.StackPane)((Pane)preview.getJavaFxNode()).getChildren().getFirst()).getChildren().getFirst();
                preview.setVisualEffect("smooth",0,0);double initialScale=imageNode.getScaleX();
                preview.setVisualEffect("smooth",0,3);
                assertTrue(imageNode.getScaleX()>initialScale);assertNotEquals(0,imageNode.getTranslateX());
                session.states().set("imagem", "");
                var timeline = (megalodonte.components.ImageSequence) session.component("timeline"); assertEquals(3,timeline.itemCount());
                session.states().set("duracao", "8,5"); session.invoke("applyDuration"); assertTrue(session.states().string("resumo").get().contains("18.5"));
                session.invoke("moveRight"); assertEquals(1,session.states().get("selecionada")); assertTrue(session.states().string("cenaTexto").get().contains("cidade"));
                session.states().set("duracao", "NaN"); session.invoke("applyDuration"); assertTrue(session.states().string("mensagem").get().contains("Informe"));
                session.invoke("removeScene"); assertEquals(2,timeline.itemCount());
                for (int[] size : new int[][]{{1320,820},{1180,780},{920,600},{1920,1080}}) {
                    ((Region)root).resize(size[0],size[1]); root.applyCss(); root.layout();
                    var scrollNode = session.component("scriptScroll").getJavaFxNode();
                    var bounds = root.sceneToLocal(scrollNode.localToScene(scrollNode.getBoundsInLocal()));
                    assertTrue(root.getLayoutBounds().getWidth() <= size[0] && root.getLayoutBounds().getHeight() <= size[1], "Root must fit window");
                    assertTrue(bounds.getMaxX() <= size[0] && bounds.getMaxY() <= size[1], "Script bounds: " + bounds);
                    assertEquals(16.0/9,preview.viewportAspectRatio(), .001);
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
                stored.visualEffectStrength=1.5;
                stored.clips.forEach(clip->clip.visualEffect="smooth");
                Path sound=Path.of(System.getProperty("megalodonte.editor.settingsDir"),"cue.wav");
                Files.write(sound,new byte[]{1,2,3});
                for(int i=0;i<4;i++) {var effect=new VideoProject.SoundEffect();effect.file=sound.toString();effect.start=1;stored.effects.add(effect);}
                Path file=Path.of(System.getProperty("megalodonte.editor.settingsDir"),"restaurado.json"); stored.save(file);
                var legacy=com.google.gson.JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                var metadata=new com.google.gson.JsonObject();metadata.addProperty("openaiApiKey","dummy-restored-key");legacy.add("metadata",metadata);
                Files.writeString(file,legacy.toString());
                new LastProjectStore().remember(file);
                try (var app=new ScriptApplication(AppConfig.load(Editor.class.getClassLoader()),Editor.class.getClassLoader())) {
                    app.render(); app.onMount(); var session=app.current();
                    assertEquals(stored.script,session.states().get("roteiro"));
                    assertEquals("smooth",session.states().get("motionEffect"));
                    assertEquals(1.5,VideoProject.load(file).visualEffectStrength);
                    assertEquals("dummy-restored-key",session.states().get("openaiKey"));
                    assertEquals("dummy-restored-key",new EditorSettings().load().apiKey);
                    assertFalse(Files.readString(file).contains("openaiApiKey"));
                    assertEquals(7,Double.parseDouble(session.states().string("volumeMusica").get()),.0001);
                    assertTrue(session.states().string("resumo").get().startsWith("2 cenas"));
                    assertTrue(session.states().string("mensagem").get().contains("restaurado"));
                    var effects=(megalodonte.components.SoundSequence)session.component("effectsTimeline");
                    assertEquals(4,effects.itemCount());
                    assertTrue(effects.viewportMinimumHeight()>=314,"Overlapping effects remain visible");
                    session.states().set("efeitoSelecionado",1);session.invoke("selectEffect");
                    session.states().set("efeitoInicio","2.5");session.states().set("efeitoDuracao","1.5");session.states().set("efeitoVolume","8");session.invoke("applyEffect");
                    session.states().set("efeitoInicio","NaN");session.invoke("applyEffect");assertTrue(session.states().string("mensagem").get().contains("Não foi possível"));
                    session.invoke("selectEffect");assertEquals("2.5",session.states().get("efeitoInicio"));
                    session.states().set("roteiro","Salvo no projeto restaurado"); session.invoke("saveProject");
                    assertFalse(Files.readString(file).contains("openaiApiKey"));
                    assertEquals("Salvo no projeto restaurado",VideoProject.load(file).script);
                    var saved=VideoProject.load(file);assertEquals(4,saved.effects.size());assertEquals(2.5,saved.effects.get(1).start);assertEquals(.08,saved.effects.get(1).volume);
                    session.invoke("removeEffect");session.invoke("saveProject");assertEquals(3,VideoProject.load(file).effects.size());
                    session.invoke("closeProject");
                    assertEquals("",session.states().get("roteiro"));
                    assertEquals(-1,session.states().get("selecionada"));
                    assertEquals(true,session.states().get("semCenas"));
                    assertEquals("Nenhuma música anexada",session.states().get("musicaNome"));
                    assertTrue(new LastProjectStore().lastSaved().isEmpty());
                }
                Files.delete(file);
                try (var app=new ScriptApplication(AppConfig.load(Editor.class.getClassLoader()),Editor.class.getClassLoader())) {
                    app.render(); app.onMount();
                    assertEquals("",app.current().states().get("roteiro"));
                    assertTrue(app.current().states().string("mensagem").get().contains("Cole o roteiro"));
                }
                return null;
            }); Platform.runLater(restoreCheck); restoreCheck.get(10,TimeUnit.SECONDS);
        } finally { Platform.exit(); }
    }
}
