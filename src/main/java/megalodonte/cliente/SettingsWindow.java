package megalodonte.cliente;

import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.*;
import megalodonte.jsonc.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Native window host; every setting and control is defined by JSONC. */
final class SettingsWindow implements AutoCloseable {
    @FunctionalInterface interface Apply { void accept(EditorSettings.Values values,String avatarDirectory) throws IOException; }
    private final Stage stage=new Stage();
    private final UiSession session;
    private final javafx.scene.control.CheckBox notifyTelegram=new javafx.scene.control.CheckBox("Notificar no Telegram");
    SettingsWindow(Window owner,EditorSettings.Values values,String avatarDirectory,Apply apply) {
        stage.setTitle("Configurações · Megalodonte Video Editor");
        if(owner!=null) { stage.initOwner(owner); stage.initModality(Modality.WINDOW_MODAL); }
        else stage.initModality(Modality.APPLICATION_MODAL);
        var actions=new Actions().register("saveSettings",view -> {
            try {
                var selected=new EditorSettings.Values(view.states().string("apiKey").get().strip(),
                    view.states().string("soundsDirectory").get().isBlank() ? "" :
                            EditorSettings.validateSounds(view.states().string("soundsDirectory").get()).toString(),
                    view.states().string("basePrompt").get(),
                    EditorSettings.parsePauseCutThreshold(view.states().string("pauseCutThreshold").get()),
                    EditorSettings.validateVisualEffect(view.states().string("defaultVisualEffect").get()),
                    EditorSettings.parseVisualEffectStrength(view.states().string("visualEffectStrength").get()),
                    view.states().string("whisperModel").get(),view.states().string("whisperLanguage").get(),
                    EditorSettings.parseWhisperNumber(view.states().string("whisperBeamSize").get(),"A busca do Whisper",1,10),
                    EditorSettings.parseWhisperNumber(view.states().string("whisperCpuThreads").get(),"As threads do Whisper",1,16),
                    Boolean.parseBoolean(view.states().string("whisperVad").get()),
                    EditorSettings.parseSoundEffectPercent(view.states().string("soundEffectPercent").get()),
                        view.states().string("pythonExecutable").get(),view.states().string("ffmpegExecutable").get(),
                        view.states().string("ffprobeExecutable").get(),view.states().string("whisperScriptsDirectory").get(),
                        view.states().string("whisperModelsDirectory").get(),notifyTelegram.isSelected());
                if(selected.basePrompt.isBlank()) throw new IllegalArgumentException("Informe um prompt base.");
                apply.accept(selected,validateAvatarDirectory(view.states().string("avatarDirectory").get()));stage.close();
            } catch(IOException | RuntimeException e) { view.states().set("mensagem",e.getMessage()); }
        }).register("cancelSettings",view -> stage.close())
          .register("showGeneral",view -> showSection(view,"General"))
          .register("showVisual",view -> showSection(view,"Visual"))
          .register("showWhisper",view -> showSection(view,"Whisper"))
          .register("showTools",view -> showSection(view,"Tools"))
          .register("showAvatar",view -> showSection(view,"Avatar"))
          .register("showPrompt",view -> showSection(view,"Prompt"))
          .register("resetPrompt",view -> view.states().set("basePrompt",EditorSettings.defaultPrompt()))
          .register("chooseSounds",view -> {
              var chooser=new DirectoryChooser();chooser.setTitle("Selecionar biblioteca de sons");
              try { chooser.setInitialDirectory(FileDialogs.existingFolder(Path.of(view.states().string("soundsDirectory").get())).toFile()); }
              catch(RuntimeException ignored) { }
              var selected=chooser.showDialog(stage);
              if(selected!=null) view.states().set("soundsDirectory",selected.toPath().toAbsolutePath().normalize().toString());
          }).register("chooseAvatar",view -> {
              var chooser=new DirectoryChooser();chooser.setTitle("Selecionar pasta de avatares do projeto");
              try { chooser.setInitialDirectory(FileDialogs.existingFolder(Path.of(view.states().string("avatarDirectory").get())).toFile()); }
              catch(RuntimeException ignored) { }
              var selected=chooser.showDialog(stage);
              if(selected!=null) view.states().set("avatarDirectory",selected.toPath().toAbsolutePath().normalize().toString());
          });
        session=UiDefinition.fromResource(SettingsWindow.class,"/views/settings.view.jsonc").open(actions);
        notifyTelegram.setSelected(values.notifyTelegram);
        session.states().set("apiKey",values.apiKey);session.states().set("soundsDirectory",values.soundsDirectory);
        session.states().set("avatarDirectory",avatarDirectory);
        session.states().set("pythonExecutable",values.pythonExecutable);
        session.states().set("ffmpegExecutable",values.ffmpegExecutable);
        session.states().set("ffprobeExecutable",values.ffprobeExecutable);
        session.states().set("whisperScriptsDirectory",values.whisperScriptsDirectory);
        session.states().set("whisperModelsDirectory",values.whisperModelsDirectory);
        session.states().set("basePrompt",values.basePrompt);
        session.states().set("pauseCutThreshold",Double.toString(values.pauseCutThreshold));
        session.states().set("visualEffectOptions","[{\"value\":\"none\",\"label\":\"Nenhum\"},{\"value\":\"smooth\",\"label\":\"Movimento suave\"},{\"value\":\"particles\",\"label\":\"Partículas\"},{\"value\":\"shake\",\"label\":\"Tremor leve\"}]");
        session.states().set("defaultVisualEffect",values.defaultVisualEffect);
        session.states().set("visualEffectStrength",Double.toString(values.visualEffectStrength));
        session.states().set("whisperModelOptions","[{\"value\":\"tiny\",\"label\":\"Tiny (rápido)\"},{\"value\":\"base\",\"label\":\"Base\"},{\"value\":\"small\",\"label\":\"Small (padrão)\"},{\"value\":\"medium\",\"label\":\"Medium\"},{\"value\":\"large-v3\",\"label\":\"Large v3 (mais pesado)\"}]");
        session.states().set("whisperLanguageOptions","[{\"value\":\"pt\",\"label\":\"Português\"},{\"value\":\"auto\",\"label\":\"Detectar automaticamente\"}]");
        session.states().set("whisperVadOptions","[{\"value\":\"true\",\"label\":\"Ligado\"},{\"value\":\"false\",\"label\":\"Desligado\"}]");
        session.states().set("whisperModel",values.whisperModel);session.states().set("whisperLanguage",values.whisperLanguage);
        session.states().set("whisperBeamSize",Integer.toString(values.whisperBeamSize));
        session.states().set("whisperCpuThreads",Integer.toString(values.whisperCpuThreads));
        session.states().set("whisperVad",Boolean.toString(values.whisperVad));
        session.states().set("soundEffectPercentOptions","[\"0% de efeitos sonoros\",\"25% de efeitos sonoros\",\"50% de efeitos sonoros\",\"75% de efeitos sonoros\",\"100% de efeitos sonoros\"]");
        session.states().set("soundEffectPercent",values.soundEffectPercent+"% de efeitos sonoros");
        var root=(Parent)session.render().getJavaFxNode();
        ((javafx.scene.layout.Pane)session.component("settingsGeneralPanel").getJavaFxNode()).getChildren().add(notifyTelegram);
        stage.setScene(new Scene(root,940,680));
        EditorTheme.style(stage.getScene());
        stage.setMinWidth(820);stage.setMinHeight(520);
        showSection(session,"General");
        stage.setOnHidden(event -> { session.states().set("apiKey",""); session.close();stage.setScene(null); });
    }
    private static void showSection(UiSession view,String selected) {
        for(String section: new String[]{"General","Visual","Whisper","Tools","Avatar","Prompt"}) {
            boolean active=section.equals(selected);
            view.states().set("show"+section,active);
            var button=(javafx.scene.control.Button)view.component("settingsNav"+section).getJavaFxNode();
            button.setPrefWidth(176);
            var colors=megalodonte.base.theme.ThemeManager.theme().colors();
            button.setStyle(active
                    ? "-fx-background-color: "+colors.primary()+"; -fx-text-fill: "+
                      (EditorTheme.isDark() ? "#101a2b" : "white")+"; -fx-font-weight: bold;"
                    : "-fx-background-color: transparent; -fx-text-fill: "+colors.textPrimary()+";");
        }
    }
    static String validateAvatarDirectory(String value) {
        if(value==null || value.isBlank()) return "";
        Path folder=Path.of(value.strip()).toAbsolutePath().normalize();
        if(!Files.isDirectory(folder) || !Files.isReadable(folder))
            throw new IllegalArgumentException("Escolha uma pasta de avatares existente e legível.");
        return folder.toString();
    }
    void show() { stage.show(); }
    boolean showing() { return stage.isShowing(); }
    void focus() { stage.toFront(); }
    UiSession session() { return session; }
    Stage stage() { return stage; }
    @Override public void close() { if(stage.isShowing()) stage.close(); }
}
