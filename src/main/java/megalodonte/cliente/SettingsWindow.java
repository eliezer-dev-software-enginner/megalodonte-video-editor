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
    /** Settings stored in the open project's manifest, not in the shared editor preferences. */
    record ProjectOptions(String avatarDirectory,String imagePrompt,boolean sceneCaptions) {}
    @FunctionalInterface interface Apply { void accept(EditorSettings.Values values,ProjectOptions project) throws IOException; }
    private final Stage stage=new Stage();
    private final UiSession session;
    private final javafx.scene.control.CheckBox notifyTelegram=new javafx.scene.control.CheckBox("Notificar no Telegram");
    SettingsWindow(Window owner,EditorSettings.Values values,ProjectOptions project,Apply apply) {
        stage.setTitle("Configurações · Megalodonte Video Editor");
        if(owner!=null) { stage.initOwner(owner); stage.initModality(Modality.WINDOW_MODAL); }
        else stage.initModality(Modality.APPLICATION_MODAL);
        var actions=new Actions().register("saveSettings",view -> {
            try {
                var selected=new EditorSettings.Values(view.states().string("apiKey").get().strip(),
                    view.states().string("soundsDirectory").get().isBlank() ? "" :
                            EditorSettings.validateSounds(view.states().string("soundsDirectory").get()).toString(),
                    view.states().string("basePrompt").get(),
                    EditorSettings.validateVisualEffect(view.states().string("defaultVisualEffect").get()),
                    EditorSettings.parseVisualEffectStrength(view.states().string("visualEffectStrength").get()),
                    EditorSettings.validateNarrationLanguage(view.states().string("narrationLanguage").get()),
                    EditorSettings.parseSoundEffectPercent(view.states().string("soundEffectPercent").get()),
                    view.states().string("ffmpegExecutable").get(),view.states().string("ffprobeExecutable").get(),
                    notifyTelegram.isSelected(),EditorSettings.normalizeImages(view.states().string("imagesDirectory").get()));
                if(selected.basePrompt.isBlank()) throw new IllegalArgumentException("Informe um prompt base.");
                apply.accept(selected,new ProjectOptions(validateAvatarDirectory(view.states().string("avatarDirectory").get()),
                        VideoProject.validateImagePrompt(view.states().string("projectImagePrompt").get()),
                        "on".equals(view.states().string("sceneCaptions").get())));stage.close();
            } catch(IOException | RuntimeException e) { view.states().set("mensagem",e.getMessage()); }
        }).register("cancelSettings",view -> stage.close())
          .register("showGeneral",view -> showSection(view,"General"))
          .register("showVisual",view -> showSection(view,"Visual"))
          .register("showTools",view -> showSection(view,"Tools"))
          .register("showAvatar",view -> showSection(view,"Avatar"))
          .register("showPrompt",view -> showSection(view,"Prompt"))
          .register("showImages",view -> showSection(view,"Images"))
          .register("clearImagePrompt",view -> view.states().set("projectImagePrompt",""))
          .register("resetPrompt",view -> view.states().set("basePrompt",EditorSettings.defaultPrompt()))
          .register("chooseSounds",view -> {
              var chooser=new DirectoryChooser();chooser.setTitle("Selecionar biblioteca de sons");
              try { chooser.setInitialDirectory(FileDialogs.existingFolder(Path.of(view.states().string("soundsDirectory").get())).toFile()); }
              catch(RuntimeException ignored) { }
              var selected=chooser.showDialog(stage);
              if(selected!=null) view.states().set("soundsDirectory",selected.toPath().toAbsolutePath().normalize().toString());
          }).register("chooseImages",view -> {
              var chooser=new DirectoryChooser();chooser.setTitle("Selecionar biblioteca global de imagens");
              try { chooser.setInitialDirectory(FileDialogs.existingFolder(Path.of(view.states().string("imagesDirectory").get())).toFile()); }
              catch(RuntimeException ignored) { }
              var selected=chooser.showDialog(stage);
              if(selected!=null) view.states().set("imagesDirectory",selected.toPath().toAbsolutePath().normalize().toString());
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
        session.states().set("imagesDirectory",values.imagesDirectory);
        session.states().set("avatarDirectory",project.avatarDirectory());
        session.states().set("ffmpegExecutable",values.ffmpegExecutable);
        session.states().set("ffprobeExecutable",values.ffprobeExecutable);
        session.states().set("basePrompt",values.basePrompt);
        session.states().set("projectImagePrompt",project.imagePrompt()==null ? "" : project.imagePrompt());
        session.states().set("sceneCaptionOptions","[{\"value\":\"off\",\"label\":\"Não mostrar texto sobre as imagens\"},{\"value\":\"on\",\"label\":\"Mostrar texto breve sobre a imagem de cada cena\"}]");
        session.states().set("sceneCaptions",project.sceneCaptions() ? "on" : "off");
        session.states().set("defaultImagePrompt",ImageGenerationJob.DEFAULT_PROMPT);
        session.states().set("visualEffectOptions","[{\"value\":\"none\",\"label\":\"Nenhum\"},{\"value\":\"smooth\",\"label\":\"Movimento suave\"},{\"value\":\"particles\",\"label\":\"Partículas\"},{\"value\":\"shake\",\"label\":\"Tremor leve\"}]");
        session.states().set("defaultVisualEffect",values.defaultVisualEffect);
        session.states().set("visualEffectStrength",Double.toString(values.visualEffectStrength));
        session.states().set("narrationLanguageOptions","[{\"value\":\"pt\",\"label\":\"Português brasileiro\"},{\"value\":\"en\",\"label\":\"Inglês americano\"}]");
        session.states().set("narrationLanguage",values.narrationLanguage);
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
        for(String section: new String[]{"General","Visual","Tools","Avatar","Prompt","Images"}) {
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
