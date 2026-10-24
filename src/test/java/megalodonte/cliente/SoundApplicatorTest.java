package megalodonte.cliente;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class SoundApplicatorTest {
    @TempDir Path folder;

    @Test void catalogTagsChooseGenericBackgroundMusicAndSceneEffectsAndPersist() throws Exception {
        Path library=Files.createDirectories(folder.resolve("sounds"));
        Path musicDir=Files.createDirectories(library.resolve("user-music"));
        Path effectDir=Files.createDirectories(library.resolve("user-effects"));
        Path music=audio(musicDir,"my-own-calm-track.mp3");
        Path spaceEffect=audio(effectDir,"custom-electronic-sweep.wav");
        Path rainEffect=audio(effectDir,"rain-transition.wav");
        Files.writeString(musicDir.resolve("TXT.txt"),"[my-own-calm-track.mp3]\nFundo musical tranquilo\n#trilha #calmo #relaxante #espaco\n");
        Files.writeString(effectDir.resolve("TXT.txt"),"[custom-electronic-sweep.wav]\nSom de transição eletrônico\n#efeito #espaco #nave\n\n[rain-transition.wav]\nChuva na cidade\n#efeito #chuva #cidade\n");

        var project=VideoProject.fromScript("Uma viagem calma pelo espaço.\n\nA nave atravessa uma cidade sob a chuva.");
        project.clips.forEach(clip->clip.duration=12);
        Path manifest=folder.resolve("video.mvideo.json");project.save(manifest);
        String revision=VideoProject.revision(manifest);

        var additions=SoundApplicator.apply(project,library);
        assertTrue(additions.musicAdded());assertEquals(music.toAbsolutePath().toString(),project.music.file);assertEquals(.08,project.music.volume);
        assertEquals(2,additions.effectsAdded().size());
        assertEquals(spaceEffect.toAbsolutePath().toString(),project.effects.get(0).file);
        assertEquals(rainEffect.toAbsolutePath().toString(),project.effects.get(1).file);
        assertTrue(project.effects.stream().allMatch(effect->effect.volume==.07));
        SoundProjectStore.saveAdditions(project,manifest,revision,additions);

        var saved=JsonParser.parseString(Files.readString(manifest)).getAsJsonObject();
        assertEquals(2,saved.getAsJsonObject("audio").getAsJsonArray("effects").size());
        try(var assets=Files.list(folder.resolve("video.mvideo.json.assets"))) { assertEquals(3,assets.count()); }
        assertEquals(12,VideoProject.load(manifest).clips.getFirst().duration);
        assertEquals(0,SoundApplicator.apply(VideoProject.load(manifest),library).addedCount(),"second application must be idempotent");
    }

    @Test void requiresCatalogTagsInsteadOfGuessingFromUncataloguedFilenames() throws Exception {
        Path library=Files.createDirectories(folder.resolve("uncatalogued"));
        audio(library,"unknown.wav");
        var project=VideoProject.fromScript("Uma cena sobre o oceano.");
        var error=assertThrows(java.io.IOException.class,()->SoundApplicator.apply(project,library));
        assertTrue(error.getMessage().contains("TXT"));
    }

    @Test void percentageLimitsSceneEffectsWithoutLimitingBackgroundMusic() throws Exception {
        Path library=Files.createDirectories(folder.resolve("coverage"));
        audio(library,"music.mp3");
        Files.writeString(library.resolve("TXT.txt"),"[music.mp3]\n#trilha #jornada\n");
        StringBuilder catalog=new StringBuilder(Files.readString(library.resolve("TXT.txt")));
        for(String word:new String[]{"chuva","vento","floresta","oceano"}) {
            audio(library,word+".wav");catalog.append('[').append(word).append(".wav]\n#efeito #").append(word).append('\n');
        }
        Files.writeString(library.resolve("TXT.txt"),catalog);
        var project=VideoProject.fromScript("Uma jornada na chuva.\n\nSopra o vento.\n\nA floresta desperta.\n\nO oceano se move.");
        assertTrue(SoundApplicator.apply(project,library,0).musicAdded());
        assertEquals(0,project.effects.size());
        assertEquals(1,SoundApplicator.apply(project,library,25).effectsAdded().size());
        assertEquals(1,project.effects.size());
        assertEquals(1,SoundApplicator.apply(project,library,50).effectsAdded().size());
        assertEquals(2,project.effects.size());
        assertEquals(0,SoundApplicator.apply(project,library,25).effectsAdded().size());
        assertEquals(2,SoundApplicator.apply(project,library,100).effectsAdded().size());
        assertEquals(0,SoundApplicator.apply(project,library,100).addedCount());
    }

    private static Path audio(Path root,String name) throws Exception {
        Path file=root.resolve(name);Files.writeString(file,"fixture audio");return file;
    }
}
