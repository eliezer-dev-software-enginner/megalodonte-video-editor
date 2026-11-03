package megalodonte.cliente;

import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.stage.Stage;
import javafx.stage.Window;
import megalodonte.base.theme.ThemeManager;
import megalodonte.theme.DefaultTheme;
import megalodonte.script.AppConfig;
import megalodonte.script.ScriptApplication;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class NarrationSettingsViewTest {
    @TempDir Path directory;
    @Test void editorSavesNarrationLanguageAndOffersSyncRetry() throws Exception {
        Assumptions.assumeTrue(System.getenv("DISPLAY") != null, "Display necessário para teste JavaFX");
        var previous=System.getProperty("megalodonte.editor.settingsDir");
        System.setProperty("megalodonte.editor.settingsDir",directory.toString());
        var ready=new CountDownLatch(1);
        try { Platform.startup(()->{Platform.setImplicitExit(false);ready.countDown();}); }
        catch(IllegalStateException started) { Platform.runLater(ready::countDown); }
        assertTrue(ready.await(15,TimeUnit.SECONDS));
        var task=new FutureTask<Void>(()->{
            ThemeManager.setTheme(new DefaultTheme());
            try(var app=new ScriptApplication(AppConfig.load(Editor.class.getClassLoader()),Editor.class.getClassLoader())) {
                new Scene((Parent)app.render().getJavaFxNode());app.onMount();
                assertEquals("Repetir sincronização",((Button)app.current().component("syncNarrationButton").getJavaFxNode()).getText());
                app.current().invoke("openSettings");
                var stage=(Stage)Window.getWindows().stream().filter(w->w instanceof Stage s && s.getTitle().startsWith("Configurações")).findFirst().orElseThrow();
                var root=stage.getScene().getRoot();root.applyCss();root.layout();
                @SuppressWarnings("unchecked") var language=(ComboBox<String>)root.lookupAll(".combo-box").stream().filter(n->n instanceof ComboBox<?> c && c.getItems().containsAll(List.of("pt","en"))).findFirst().orElseThrow();
                language.setValue("en");
                ((Button)root.lookupAll(".button").stream().filter(n->n instanceof Button b && b.getText().equals("Salvar configurações")).findFirst().orElseThrow()).fire();
                assertFalse(stage.isShowing());assertEquals("en",new EditorSettings().load().narrationLanguage);
            }
            return null;
        });
        try {Platform.runLater(task);task.get(30,TimeUnit.SECONDS);}
        finally {if(previous==null)System.clearProperty("megalodonte.editor.settingsDir");else System.setProperty("megalodonte.editor.settingsDir",previous);}
    }
}
