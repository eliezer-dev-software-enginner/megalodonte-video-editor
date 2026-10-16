package megalodonte.cliente;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Consumer;

/** Runs the local transcription and alignment scripts without sending audio off-device. */
final class WhisperSync {
    private final Path project;
    private final Path scriptsRoot;
    private final Path python;
    private volatile Process activeProcess;

    WhisperSync(Path project) throws IOException {
        this.project = project.toAbsolutePath().normalize();
        this.scriptsRoot = findScriptsRoot();
        this.python = findPython(scriptsRoot);
    }

    void run(Consumer<String> progress) throws IOException, InterruptedException {
        execute(List.of(python.toString(), scriptsRoot.resolve("tools/transcribe_narration.py").toString(), project.toString()),
                progress, "Whisper falhou ao transcrever o áudio.");
        execute(List.of(python.toString(), scriptsRoot.resolve("tools/sync_narration.py").toString(), project.toString(), "--apply"),
                progress, "Não foi possível alinhar a narração ao roteiro.");
    }

    private void execute(List<String> command, Consumer<String> progress, String failure) throws IOException, InterruptedException {
        var process = new ProcessBuilder(command).directory(scriptsRoot.toFile()).redirectErrorStream(true).start();
        activeProcess = process;
        var output = new StringBuilder();
        try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!output.isEmpty()) output.append('\n');
                output.append(line);
                if (line.contains("s:") || line.contains("salva em")) progress.accept(line);
            }
        } catch (IOException e) {
            process.destroyForcibly();
            throw e;
        } finally {
            activeProcess = null;
        }
        int exit = process.waitFor();
        if (exit != 0) throw new IOException(failure + "\n" + output);
    }

    void cancel() {
        Process process = activeProcess;
        if (process != null) process.destroyForcibly();
    }

    private static Path findScriptsRoot() throws IOException {
        var candidates = new LinkedHashSet<Path>();
        candidates.add(Path.of(System.getProperty("megalodonte.videoEditor.home", ".")).toAbsolutePath().normalize());
        candidates.add(Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize());
        try {
            Path location = Path.of(WhisperSync.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toAbsolutePath();
            Path cursor = Files.isDirectory(location) ? location : location.getParent();
            for (int i = 0; cursor != null && i < 8; i++, cursor = cursor.getParent()) candidates.add(cursor);
        } catch (Exception ignored) { }
        for (Path candidate : candidates) {
            for (Path cursor = candidate; cursor != null; cursor = cursor.getParent()) {
                if (Files.isRegularFile(cursor.resolve("tools/transcribe_narration.py")) &&
                        Files.isRegularFile(cursor.resolve("tools/sync_narration.py"))) return cursor;
            }
        }
        throw new IOException("Não encontrei os scripts do Whisper. Execute o editor a partir da pasta do projeto ou configure megalodonte.videoEditor.home.");
    }

    private static Path findPython(Path root) throws IOException {
        String configured = System.getProperty("megalodonte.whisper.python", "").strip();
        var candidates = new ArrayList<Path>();
        if (!configured.isEmpty()) candidates.add(Path.of(configured));
        for (Path cursor = root; cursor != null; cursor = cursor.getParent()) {
            candidates.add(cursor.resolve(".local-asr/venv/bin/python"));
            candidates.add(cursor.resolve(".local-asr/venv/Scripts/python.exe"));
        }
        for (Path candidate : candidates) if (Files.isExecutable(candidate)) return candidate.toAbsolutePath();
        return Path.of(System.getProperty("os.name", "").toLowerCase().contains("win") ? "python" : "python3");
    }
}
