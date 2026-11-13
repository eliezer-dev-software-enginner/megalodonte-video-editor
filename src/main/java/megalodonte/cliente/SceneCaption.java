package megalodonte.cliente;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Short on-image text per scene. One Java2D renderer feeds both the preview and the
 * export, so the caption looks the same in both and never depends on FFmpeg fonts.
 */
final class SceneCaption {
    static final int MAX_LENGTH = 90;
    private static final int MAX_WORDS = 8;
    private SceneCaption() {}

    static String validate(String value) {
        String caption = value == null ? "" : value.strip().replaceAll("\\s+", " ");
        if (caption.length() > MAX_LENGTH)
            throw new IllegalArgumentException("O texto breve da cena deve ter até " + MAX_LENGTH + " caracteres.");
        return caption;
    }

    /** Text shown for a scene: blank when the project option is off; otherwise the edited text or a suggestion. */
    static String effective(VideoProject project, VideoProject.Clip clip) {
        if (!project.sceneCaptions || clip == null) return "";
        return clip.caption.isBlank() ? suggest(clip.text) : clip.caption;
    }

    /** Local, deterministic suggestion: first sentence, else its first clause, else its first words. */
    static String suggest(String sceneText) {
        String text = sceneText == null ? "" : sceneText.strip().replaceAll("\\s+", " ");
        if (text.isEmpty()) return "";
        String sentence = text.split("(?<=[.!?:;])\\s", 2)[0].replaceAll("[.!?:;]+$", "").strip();
        if (words(sentence) <= MAX_WORDS) return sentence;
        String clause = sentence.split(",", 2)[0].strip();
        if (words(clause) <= MAX_WORDS) return clause;
        String[] parts = clause.split(" ");
        return String.join(" ", java.util.Arrays.copyOf(parts, MAX_WORDS - 1)).replaceAll("[,;:]+$", "") + "…";
    }

    private static int words(String text) { return text.isBlank() ? 0 : text.split(" ").length; }

    /** Transparent full-frame layer with a lower-left caption box. */
    static BufferedImage render(String caption, int width, int height) {
        var layer = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        if (caption == null || caption.isBlank()) return layer;
        Graphics2D g = layer.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            float size = height * .046f;
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.round(size)));
            FontMetrics metrics = g.getFontMetrics();
            int padX = Math.round(size * .7f), padY = Math.round(size * .45f), accent = Math.max(4, Math.round(size * .16f));
            int maxText = Math.round(width * .62f) - padX * 2 - accent;
            List<String> lines = wrap(caption, metrics, maxText);
            int textWidth = lines.stream().mapToInt(metrics::stringWidth).max().orElse(0);
            int lineHeight = metrics.getHeight();
            int boxWidth = textWidth + padX * 2 + accent, boxHeight = lineHeight * lines.size() + padY * 2;
            int x = Math.round(width * .04f), y = height - Math.round(height * .08f) - boxHeight;
            float radius = size * .45f;
            var box = new RoundRectangle2D.Float(x, y, boxWidth, boxHeight, radius, radius);
            g.setColor(new Color(10, 22, 38, 214));
            g.fill(box);
            g.setClip(box);
            g.setColor(new Color(23, 179, 201));
            g.fillRect(x, y, accent, boxHeight);
            g.setClip(null);
            g.setColor(Color.WHITE);
            int baseline = y + padY + metrics.getAscent();
            for (String line : lines) { g.drawString(line, x + accent + padX, baseline); baseline += lineHeight; }
        } finally { g.dispose(); }
        return layer;
    }

    static byte[] png(String caption, int width, int height) {
        try (var bytes = new ByteArrayOutputStream()) {
            ImageIO.write(render(caption, width, height), "png", bytes);
            return bytes.toByteArray();
        } catch (IOException e) { throw new UncheckedIOException(e); }
    }

    /** Up to two lines; the second is shortened with an ellipsis when the text does not fit. */
    private static List<String> wrap(String caption, FontMetrics metrics, int maxWidth) {
        var lines = new ArrayList<String>();
        var current = new StringBuilder();
        for (String word : caption.split(" ")) {
            String next = current.isEmpty() ? word : current + " " + word;
            if (metrics.stringWidth(next) <= maxWidth || current.isEmpty()) { current.setLength(0); current.append(next); continue; }
            lines.add(current.toString()); current.setLength(0); current.append(word);
        }
        if (!current.isEmpty()) lines.add(current.toString());
        if (lines.size() > 2) {
            String second = String.join(" ", lines.subList(1, lines.size()));
            lines = new ArrayList<>(List.of(lines.getFirst(), second));
        }
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (metrics.stringWidth(line) <= maxWidth) continue;
            while (line.length() > 1 && metrics.stringWidth(line + "…") > maxWidth) line = line.substring(0, line.length() - 1).stripTrailing();
            lines.set(i, line + "…");
        }
        return lines;
    }
}
