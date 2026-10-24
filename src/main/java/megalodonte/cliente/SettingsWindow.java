package megalodonte.cliente;

import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.*;
import megalodonte.jsonc.*;
import java.io.IOException;
import java.nio.file.Path;

/** Native window host; every setting and control is defined by JSONC. */
final class SettingsWindow implements AutoCloseable {
    @FunctionalInterface interface Apply { void accept(EditorSettings.Values values) throws IOException; }
    private final Stage stage=new Stage();
    private final UiSession session;
    SettingsWindow(Window owner,EditorSettings.Values values,Apply apply) {
        stage.setTitle("Configurações · Megalodonte Video Editor");
        if(owner!=null) { stage.initOwner(owner); stage.initModality(Modality.WINDOW_MODAL); }
        else stage.initModality(Modality.APPLICATION_MODAL);
        var actions=new Actions().register("saveSettings",view -> {
            try {
                var selected=new EditorSettings.Values(view.states().string("apiKey").get().strip(),
                    EditorSettings.validateSounds(view.states().string("soundsDirectory").get()).toString(),
                    view.states().string("basePrompt").get(),
                    EditorSettings.parsePauseCutThreshold(view.states().string("pauseCutThreshold").get()),
                    EditorSettings.validateVisualEffect(view.states().string("defaultVisualEffect").get()),
                    EditorSettings.parseVisualEffectStrength(view.states().string("visualEffectStrength").get()));
                if(selected.basePrompt.isBlank()) throw new IllegalArgumentException("Informe um prompt base.");
                apply.accept(selected);stage.close();
            } catch(IOException | RuntimeException e) { view.states().set("mensagem",e.getMessage()); }
        }).register("cancelSettings",view -> stage.close())
          .register("resetPrompt",view -> view.states().set("basePrompt",EditorSettings.defaultPrompt()))
          .register("chooseSounds",view -> {
              var chooser=new DirectoryChooser();chooser.setTitle("Selecionar biblioteca de sons");
              try { chooser.setInitialDirectory(FileDialogs.existingFolder(Path.of(view.states().string("soundsDirectory").get())).toFile()); }
              catch(RuntimeException ignored) { }
              var selected=chooser.showDialog(stage);
              if(selected!=null) view.states().set("soundsDirectory",selected.toPath().toAbsolutePath().normalize().toString());
          });
        session=UiDefinition.fromResource(SettingsWindow.class,"/views/settings.view.jsonc").open(actions);
        session.states().set("apiKey",values.apiKey);session.states().set("soundsDirectory",values.soundsDirectory);
        session.states().set("basePrompt",values.basePrompt);
        session.states().set("pauseCutThreshold",Double.toString(values.pauseCutThreshold));
        session.states().set("visualEffectOptions","[{\"value\":\"none\",\"label\":\"Nenhum\"},{\"value\":\"smooth\",\"label\":\"Movimento suave\"},{\"value\":\"particles\",\"label\":\"Partículas\"},{\"value\":\"shake\",\"label\":\"Tremor leve\"}]");
        session.states().set("defaultVisualEffect",values.defaultVisualEffect);
        session.states().set("visualEffectStrength",Double.toString(values.visualEffectStrength));
        stage.setScene(new Scene((Parent)session.render().getJavaFxNode(),760,680));
        stage.setMinWidth(740);stage.setMinHeight(520);
        stage.setOnHidden(event -> { session.states().set("apiKey",""); session.close();stage.setScene(null); });
    }
    void show() { stage.show(); }
    boolean showing() { return stage.isShowing(); }
    void focus() { stage.toFront(); }
    UiSession session() { return session; }
    Stage stage() { return stage; }
    @Override public void close() { if(stage.isShowing()) stage.close(); }
}
