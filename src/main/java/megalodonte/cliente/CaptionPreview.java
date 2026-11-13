package megalodonte.cliente;

import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import megalodonte.components.ImagePreview;

import java.io.ByteArrayInputStream;
import java.util.Objects;

/** Scene caption layer over the 16:9 preview frame, drawn by the same renderer used in the export. */
final class CaptionPreview {
    private final ImageView layer = new ImageView();
    private String caption = "";

    CaptionPreview(ImagePreview preview) {
        var frame = (StackPane) ((Pane) preview.getJavaFxNode()).getChildren().getFirst();
        layer.setMouseTransparent(true);
        layer.setPreserveRatio(false);
        layer.setSmooth(true);
        layer.fitWidthProperty().bind(frame.widthProperty());
        layer.fitHeightProperty().bind(frame.heightProperty());
        frame.getChildren().add(layer);
    }

    void show(String text) {
        String next = text == null ? "" : text;
        if (Objects.equals(caption, next)) return;
        caption = next;
        layer.setImage(next.isBlank() ? null : new Image(new ByteArrayInputStream(SceneCaption.png(next, 1920, 1080))));
    }

    String caption() { return caption; }
}
