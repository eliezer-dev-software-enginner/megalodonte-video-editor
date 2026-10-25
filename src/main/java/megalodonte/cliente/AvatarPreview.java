package megalodonte.cliente;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import megalodonte.components.ImagePreview;

import java.io.IOException;
import java.util.Objects;

/** Project-specific avatar layer over the reusable video preview. */
final class AvatarPreview {
    private final ImageView image = new ImageView();
    private final Label backgroundHint;
    private String directory = "", filename = "";

    AvatarPreview(ImagePreview preview) {
        var frame = (StackPane) ((Pane) preview.getJavaFxNode()).getChildren().getFirst();
        backgroundHint = (Label) frame.getChildren().get(1);
        image.setPreserveRatio(true);
        image.setMouseTransparent(true);
        image.fitHeightProperty().bind(frame.heightProperty().multiply(.72));
        StackPane.setAlignment(image, Pos.BOTTOM_RIGHT);
        frame.widthProperty().addListener((ignored, oldWidth, width) ->
                StackPane.setMargin(image, new Insets(0, width.doubleValue() * .025, 0, 0)));
        frame.getChildren().add(image);
    }

    /** Returns a new validation error once per changed avatar, if any. */
    String show(String directory, String filename, boolean hasBackground) {
        backgroundHint.setVisible(!hasBackground && (filename == null || filename.isBlank()));
        if (Objects.equals(this.directory, directory) && Objects.equals(this.filename, filename)) return null;
        this.directory = directory;
        this.filename = filename;
        image.setImage(null);
        if (filename == null || filename.isBlank()) return null;
        try {
            var path = AvatarFiles.resolve(directory, filename);
            var loaded = new Image(path.toUri().toString());
            if (loaded.isError()) throw new IOException("Imagem de avatar inválida: " + filename);
            image.setImage(loaded);
            backgroundHint.setVisible(false);
            return null;
        } catch (IOException error) {
            backgroundHint.setVisible(!hasBackground);
            return error.getMessage();
        }
    }
}
