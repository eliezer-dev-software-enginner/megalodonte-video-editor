package megalodonte.cliente;

import javafx.scene.Scene;
import megalodonte.base.theme.ThemeColors;
import megalodonte.base.theme.ThemeManager;
import megalodonte.theme.DefaultTheme;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Persisted desktop appearance; the Lua palette matches the Android editor. */
public final class EditorTheme extends DefaultTheme {
    private static final Path CHOICE = LastProjectStore.settingsDirectory().resolve("appearance.txt");
    private static final String DARK_CSS = EditorTheme.class.getResource("/themes/moon.css").toExternalForm();
    private static final String LIGHT_CSS = EditorTheme.class.getResource("/themes/sun.css").toExternalForm();
    private final boolean dark;

    public EditorTheme() { this(isDark()); }
    private EditorTheme(boolean dark) { this.dark = dark; }

    public static boolean isDark() {
        try { return Files.isRegularFile(CHOICE) && Files.readString(CHOICE).strip().equals("lua"); }
        catch (IOException ignored) { return false; }
    }

    public static void select(boolean dark) throws IOException {
        Files.createDirectories(CHOICE.getParent());
        Files.writeString(CHOICE, dark ? "lua\n" : "sol\n");
        ThemeManager.setTheme(new EditorTheme(dark));
    }

    public static void style(Scene scene) {
        if (scene == null) return;
        scene.getStylesheets().removeAll(DARK_CSS, LIGHT_CSS);
        scene.getStylesheets().add(isDark() ? DARK_CSS : LIGHT_CSS);
    }

    @Override public ThemeColors colors() {
        if (!dark) return new ThemeColors("#f5f8fc", "#ffffff", "#087c90", "#526779",
                "#172b3f", "#526779", "#d8e5ee", "#526779", "#d2edf1",
                "#087c90", "#eaf2f8", "#10b981", "#f59e0b", "#ef4444");
        return new ThemeColors("#101a2b", "#19273b", "#46c7d5", "#adbdcc",
                "#ecf4fc", "#adbdcc", "#32465f", "#adbdcc", "#24566a",
                "#46c7d5", "#24354a", "#10b981", "#f59e0b", "#ef4444");
    }
}
