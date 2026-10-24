package megalodonte.cliente;

import javafx.scene.media.*;
import javafx.util.Duration;
import java.nio.file.Path;
import java.util.function.Consumer;

/** Narration, music and individually scheduled effects. Export uses FFmpeg and does not depend on preview codecs. */
final class AudioPreview implements AutoCloseable {
    private MediaPlayer narration, music;
    private static final double EFFECT_PRELOAD_SECONDS = 3;
    private static final class Cue {
        final VideoProject.SoundEffect effect;
        MediaPlayer player;
        boolean active, ready;
        Cue(VideoProject.SoundEffect effect) { this.effect=effect; }
    }
    private final java.util.List<Cue> cues=new java.util.ArrayList<>();
    private boolean playing;
    private double position;
    private Consumer<String> error;
    void load(VideoProject project, Consumer<String> error) {
        close();
        this.error=error;
        narration = create(project.narration,false,error);
        music = create(project.music,true,error);
        for(var effect:project.effects)cues.add(new Cue(effect.copy()));
        updateCues(false);
    }
    private MediaPlayer create(VideoProject.AudioTrack track, boolean loop, Consumer<String> error) {
        if (track.file.isBlank()) return null;
        try {
            var player = new MediaPlayer(new Media(Path.of(track.file).toUri().toString()));
            setGain(player,track.volume);
            if (loop) player.setCycleCount(MediaPlayer.INDEFINITE);
            player.setOnReady(() -> { if (playing) playAt(player,position,loop); else seek(player,position,loop); });
            player.setOnError(() -> error.accept("Áudio indisponível na prévia: " + player.getError().getMessage()));
            return player;
        } catch (RuntimeException exception) { error.accept("Áudio indisponível na prévia: " + exception.getMessage()); return null; }
    }
    void play(double seconds) { position = seconds; playing = true; playAt(narration,seconds,false); playAt(music,seconds,true);updateCues(true); }
    void position(double seconds) { position = seconds;updateCues(false); }
    void pause() { playing = false; if (narration != null) narration.pause(); if (music != null) music.pause();for(var cue:cues) { if(cue.player!=null)cue.player.pause();cue.active=false; } }
    void seek(double seconds) { position = seconds; seek(narration,seconds,false); seek(music,seconds,true);updateCues(true); }
    void volumes(VideoProject project) { if (narration != null) setGain(narration,project.narration.volume); if (music != null) setGain(music,project.music.volume); }
    /** JavaFX volume is capped at 1.0, so use a flat equalizer gain for narration amplification in preview. */
    private static void setGain(MediaPlayer player,double gain) {
        var equalizer=player.getAudioEqualizer();
        if(gain>1) {
            double db=Math.min(12,20*Math.log10(gain));
            for(var band:equalizer.getBands()) band.setGain(db);
            equalizer.setEnabled(true);
            player.setVolume(1);
        } else {
            equalizer.setEnabled(false);
            player.setVolume(gain);
        }
    }
    private void updateCues(boolean forceSeek) { for(var cue:cues) updateCue(cue,forceSeek); }
    private void updateCue(Cue cue,boolean forceSeek) {
        boolean near=position>=cue.effect.start-EFFECT_PRELOAD_SECONDS && position<cue.effect.start+cue.effect.duration;
        if(!near) {
            if(cue.player!=null)dispose(cue.player);
            cue.player=null;cue.ready=false;cue.active=false;return;
        }
        if(cue.player==null) {
            var track=new VideoProject.AudioTrack(cue.effect.volume);track.file=cue.effect.file;
            cue.player=create(track,false,error);
            if(cue.player==null)return;
            cue.player.setOnReady(()-> { cue.ready=true;updateCue(cue,true); });
            if(cue.player.getStatus()==MediaPlayer.Status.READY) cue.ready=true;
        }
        if(!cue.ready)return;
        boolean active=cue.effect.active(position);
        if(!active) { if(cue.active) cue.player.pause();cue.active=false;return; }
        cue.player.setVolume(cue.effect.gain(position));
        if(forceSeek || !cue.active) {
            cue.player.seek(Duration.seconds(cue.effect.offset+position-cue.effect.start));
            if(playing) cue.player.play(); else cue.player.pause();
        }
        cue.active=active;
    }
    private static void playAt(MediaPlayer player,double seconds,boolean loop) {
        if(player==null) return;
        double total=player.getTotalDuration().toSeconds();
        if(!loop && Double.isFinite(total) && total>0 && seconds>=total) { player.pause(); return; }
        seek(player,seconds,loop); player.play();
    }
    private static void seek(MediaPlayer player, double seconds, boolean loop) {
        if (player == null) return;
        double total = player.getTotalDuration().toSeconds();
        if (Double.isFinite(total) && total > 0) player.seek(Duration.seconds(loop ? seconds % total : Math.min(seconds,total)));
    }
    int loadedEffectPlayers() { return (int)cues.stream().filter(cue->cue.player!=null).count(); }
    @Override public void close() { playing = false; for(var cue:cues) dispose(cue.player);cues.clear();dispose(narration); dispose(music); narration = null; music = null; position = 0;error=null; }
    private static void dispose(MediaPlayer player) { if (player != null) { player.setOnReady(null); player.setOnError(null); player.stop(); player.dispose(); } }
}
