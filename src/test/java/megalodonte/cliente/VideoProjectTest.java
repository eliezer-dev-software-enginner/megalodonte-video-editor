package megalodonte.cliente;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
class VideoProjectTest {
    @TempDir Path directory;
    @Test void repeatedSaveReusesProjectAssetsWithoutGrowingNames() throws Exception {
        var source=directory.resolve("ressona-narracao-2026-10-24-a1fe5f19.mp3");
        Files.write(source,new byte[]{1,2,3});
        var project=new VideoProject();project.narration.file=source.toString();
        var effect=new VideoProject.SoundEffect();effect.file=source.toString();project.effects.add(effect);
        var manifest=directory.resolve("video.mvideo.json");project.save(manifest);
        var loaded=VideoProject.load(manifest);
        var stored=Path.of(loaded.narration.file);
        assertEquals(stored,Path.of(loaded.effects.getFirst().file));
        for(int i=0;i<5;i++) { loaded.save(manifest);loaded=VideoProject.load(manifest); }
        assertEquals(stored,Path.of(loaded.narration.file));
        try(var files=Files.list(manifest.resolveSibling(manifest.getFileName()+".assets"))) {
            assertEquals(1,files.count());
        }
        assertEquals("ressona-narracao-2026-10-24-a1fe5f19.mp3",Editor.assetDisplayName(
            "12345678-1234-1234-1234-123456789abc-12345678-1234-1234-1234-123456789abc-ressona-narracao-2026-10-24-a1fe5f19.mp3"));
    }
    @Test void projectNeverSavesApiKeyAndCanSanitizeLegacyMetadata() throws Exception {
        var project = VideoProject.fromScript("Cena"); project.animateImages = true;
        assertTrue(project.snapshot().animateImages);
        var file = directory.resolve("key.mvideo.json"); project.save(file);
        assertFalse(Files.readString(file).contains("openaiApiKey"));
        assertTrue(VideoProject.load(file).animateImages);
        Files.writeString(file, "{\"version\":1,\"script\":\"\",\"clips\":[],\"metadata\":{\"openaiApiKey\":\"dummy-project-key\",\"other\":\"keep\"}}");
        String revision=VideoProject.revision(file);
        assertEquals("dummy-project-key",VideoProject.legacyApiKey(file));
        assertTrue(VideoProject.removeLegacyApiKey(file,revision));
        assertFalse(Files.readString(file).contains("dummy-project-key"));
        assertFalse(Files.readString(file).contains("openaiApiKey"));
        assertTrue(Files.readString(file).contains("\"other\": \"keep\""));
        assertThrows(java.io.IOException.class,()->VideoProject.removeLegacyApiKey(file,revision));
        assertFalse(VideoProject.load(file).animateImages);
    }
    @Test void effectsArePortableIndependentAndValidated() throws Exception {
        var p=VideoProject.fromScript("Cena");var sound=directory.resolve("effect.wav");Files.write(sound,new byte[]{1,2,3});
        var effect=new VideoProject.SoundEffect();effect.file=sound.toString();effect.start=1;effect.offset=2;effect.duration=2;effect.volume=.2;effect.fadeIn=.4;effect.fadeOut=.5;p.effects.add(effect);
        var copy=p.snapshot();copy.effects.getFirst().start=3;assertEquals(1,effect.start);
        var file=directory.resolve("effects.json");p.save(file);Files.delete(sound);
        var restored=VideoProject.load(file).effects.getFirst();assertEquals(2,restored.offset);assertEquals(.2,restored.volume);assertEquals(.4,restored.fadeIn);assertTrue(Files.isRegularFile(Path.of(restored.file)));
        assertFalse(effect.active(.9));assertTrue(effect.active(1));assertFalse(effect.active(3));assertEquals(.1,effect.gain(1.2),.0001);assertEquals(.1,effect.gain(2.75),.0001);
        effect.start=Double.NaN;assertThrows(IllegalArgumentException.class,effect::validate);
        effect.start=0;effect.fadeOut=5;assertThrows(IllegalArgumentException.class,effect::validate);
        assertThrows(java.io.IOException.class,()->FileSelections.protectAssets(VideoProject.load(file),Path.of(restored.file),file,true));
    }
    @Test void paragraphsPreserveOrderAndDurations() {
        var project = VideoProject.fromScript("Primeira linha\ncontinuação\n\nSegunda cena\r\n\r\nTerceira");
        assertEquals(3, project.clips.size()); assertEquals("Primeira linha\ncontinuação", project.clips.getFirst().text);
        assertEquals(15, project.duration()); assertEquals(0, VideoProject.fromScript(" \n ").clips.size());
    }
    @Test void visualParticleCueSurvivesSaveAndSnapshot() throws Exception {
        var project=VideoProject.fromScript("Abertura");project.clips.getFirst().visualEffect="particles";
        assertEquals("particles",project.snapshot().clips.getFirst().visualEffect);
        var file=directory.resolve("visual.json");project.save(file);
        assertEquals("particles",VideoProject.load(file).clips.getFirst().visualEffect);
        assertThrows(IllegalArgumentException.class,()->VideoProject.validateVisualEffect("snow"));
    }
    @Test void smoothMotionCueSurvivesSaveAndSnapshot() throws Exception {
        var project=VideoProject.fromScript("Abertura");project.clips.getFirst().visualEffect="smooth";project.visualEffectStrength=1.7;
        assertEquals("smooth",project.snapshot().clips.getFirst().visualEffect);
        assertEquals(1.7,project.snapshot().visualEffectStrength);
        var file=directory.resolve("smooth.json");project.save(file);
        assertEquals("smooth",VideoProject.load(file).clips.getFirst().visualEffect);
        assertEquals(1.7,VideoProject.load(file).visualEffectStrength);
        assertThrows(IllegalArgumentException.class,()->VideoProject.validateVisualEffectStrength(3.1));
    }
    @Test void staleEditorCannotOverwriteExternallyUpdatedManifest() throws Exception {
        var file=directory.resolve("shared.mvideo.json");var first=VideoProject.fromScript("Versão inicial");first.save(file);
        var loaded=VideoProject.load(file);String revision=VideoProject.revision(file);
        var external=VideoProject.fromScript("Narração e cenas novas");external.save(file);
        loaded.script="Alteração na janela antiga";
        var error=assertThrows(java.io.IOException.class,()->loaded.save(file,revision));
        assertTrue(error.getMessage().contains("mudou no disco"));
        assertEquals("Narração e cenas novas",VideoProject.load(file).script);
        loaded.save(file,VideoProject.revision(file));
        assertEquals("Alteração na janela antiga",VideoProject.load(file).script);
    }
    @Test void savedProjectCarriesAssetsAndSurvivesRemovalOfOriginal() throws Exception {
        var image = directory.resolve("original.png"); Files.write(image, new byte[]{1,2,3});
        var project = VideoProject.fromScript("Cena um\n\nCena dois"); project.clips.getFirst().image = image.toString(); project.clips.getFirst().duration = 7.5;
        var narration=directory.resolve("voice.wav"); Files.write(narration,new byte[]{4,5,6});
        var music=directory.resolve("music.mp3"); Files.write(music,new byte[]{7,8,9});
        project.narration.file=narration.toString(); project.music.file=music.toString(); project.music.volume=.12;
        var file = directory.resolve("video.mvideo.json"); project.save(file); Files.delete(image); Files.delete(narration); Files.delete(music);
        assertTrue(Files.readString(file).contains("16:9"));
        var loaded = VideoProject.load(file); assertEquals(2, loaded.clips.size()); assertEquals(12.5, loaded.duration());
        assertEquals(.12,loaded.music.volume); assertEquals(1,loaded.narration.volume);
        assertArrayEquals(new byte[]{4,5,6},Files.readAllBytes(Path.of(loaded.narration.file)));
        assertArrayEquals(new byte[]{7,8,9},Files.readAllBytes(Path.of(loaded.music.file)));
        assertArrayEquals(new byte[]{1,2,3}, Files.readAllBytes(Path.of(loaded.clips.getFirst().image)));
        loaded.save(file); assertEquals(2, VideoProject.load(file).clips.size());
    }
    @Test void rejectsUnsafeAssetPathAndInvalidDuration() throws Exception {
        var file = directory.resolve("bad.json");
        Files.writeString(file, "{\"version\":1,\"script\":\"\",\"clips\":[{\"text\":\"a\",\"image\":\"../outside.png\",\"duration\":5}]}");
        assertThrows(java.io.IOException.class, () -> VideoProject.load(file));
        Files.writeString(file, "{\"version\":1,\"script\":\"\",\"clips\":[{\"text\":\"a\",\"image\":\"\",\"duration\":-1}]}");
        assertThrows(java.io.IOException.class, () -> VideoProject.load(file));
        Files.writeString(file, "{\"version\":1,\"aspectRatio\":\"9:16\",\"script\":\"\",\"clips\":[]}");
        assertThrows(java.io.IOException.class, () -> VideoProject.load(file));
    }
    @Test void defaultsAndPercentVolumesAreValidated() throws Exception {
        var project=new VideoProject(); assertEquals(.15,project.music.volume); assertEquals(1,project.narration.volume);
        assertEquals(.125,VideoProject.volumeFromPercent("12,5")); assertEquals(0,VideoProject.volumeFromPercent("0"));
        assertEquals(2.5,VideoProject.narrationGainFromPercent("250"));
        assertThrows(IllegalArgumentException.class,()->VideoProject.narrationGainFromPercent("401"));
        for(String value:new String[]{"NaN","Infinity","-1","101","abc"}) assertThrows(IllegalArgumentException.class,()->VideoProject.volumeFromPercent(value));
        var file=directory.resolve("legacy.json"); Files.writeString(file,"{\"version\":1,\"script\":\"\",\"clips\":[]}");
        var legacy=VideoProject.load(file); assertEquals(.15,legacy.music.volume); assertEquals("",legacy.narration.file);
    }
    @Test void amplifiedNarrationGainSurvivesProjectSaveAndLoad() throws Exception {
        var narration=directory.resolve("quiet-voice.wav");Files.write(narration,new byte[]{1,2,3});
        var project=new VideoProject();project.narration.file=narration.toString();project.narration.volume=2.5;
        var file=directory.resolve("amplified.mvideo.json");project.save(file);
        assertEquals(2.5,VideoProject.load(file).narration.volume);
        project.music.volume=1.01;
        assertThrows(IllegalArgumentException.class,()->project.save(directory.resolve("bad-music.mvideo.json")));
    }

}
