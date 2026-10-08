package megalodonte.cliente;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
class VideoProjectTest {
    @TempDir Path directory;
    @Test void paragraphsPreserveOrderAndDurations() {
        var project = VideoProject.fromScript("Primeira linha\ncontinuação\n\nSegunda cena\r\n\r\nTerceira");
        assertEquals(3, project.clips.size()); assertEquals("Primeira linha\ncontinuação", project.clips.getFirst().text);
        assertEquals(15, project.duration()); assertEquals(0, VideoProject.fromScript(" \n ").clips.size());
    }
    @Test void savedProjectCarriesAssetsAndSurvivesRemovalOfOriginal() throws Exception {
        var image = directory.resolve("original.png"); Files.write(image, new byte[]{1,2,3});
        var project = VideoProject.fromScript("Cena um\n\nCena dois"); project.clips.getFirst().image = image.toString(); project.clips.getFirst().duration = 7.5;
        var file = directory.resolve("video.mvideo.json"); project.save(file); Files.delete(image);
        var loaded = VideoProject.load(file); assertEquals(2, loaded.clips.size()); assertEquals(12.5, loaded.duration());
        assertArrayEquals(new byte[]{1,2,3}, Files.readAllBytes(Path.of(loaded.clips.getFirst().image)));
        loaded.save(file); assertEquals(2, VideoProject.load(file).clips.size());
    }
    @Test void rejectsUnsafeAssetPathAndInvalidDuration() throws Exception {
        var file = directory.resolve("bad.json");
        Files.writeString(file, "{\"version\":1,\"script\":\"\",\"clips\":[{\"text\":\"a\",\"image\":\"../outside.png\",\"duration\":5}]}");
        assertThrows(java.io.IOException.class, () -> VideoProject.load(file));
        Files.writeString(file, "{\"version\":1,\"script\":\"\",\"clips\":[{\"text\":\"a\",\"image\":\"\",\"duration\":-1}]}");
        assertThrows(java.io.IOException.class, () -> VideoProject.load(file));
    }
}
