package megalodonte.cliente;

import javafx.scene.media.*;
import javafx.util.Duration;
import java.nio.file.Path;
import java.util.function.Consumer;

/** Two independent preview tracks. Export uses FFmpeg and does not depend on preview codecs. */
final class AudioPreview implements AutoCloseable {
    private MediaPlayer narration, music;
    private boolean playing;
    private double position;
    void load(VideoProject project, Consumer<String> error) {
        close();
        narration = create(project.narration,false,error);
        music = create(project.music,true,error);
    }
    private MediaPlayer create(VideoProject.AudioTrack track, boolean loop, Consumer<String> error) {
        if (track.file.isBlank()) return null;
        try {
            var player = new MediaPlayer(new Media(Path.of(track.file).toUri().toString()));
            player.setVolume(track.volume);
            if (loop) player.setCycleCount(MediaPlayer.INDEFINITE);
            player.setOnReady(() -> { if (playing) playAt(player,position,loop); else seek(player,position,loop); });
            player.setOnError(() -> error.accept("Áudio indisponível na prévia: " + player.getError().getMessage()));
            return player;
        } catch (RuntimeException exception) { error.accept("Áudio indisponível na prévia: " + exception.getMessage()); return null; }
    }
    void play(double seconds) { position = seconds; playing = true; playAt(narration,seconds,false); playAt(music,seconds,true); }
    void position(double seconds) { position = seconds; }
    void pause() { playing = false; if (narration != null) narration.pause(); if (music != null) music.pause(); }
    void seek(double seconds) { position = seconds; seek(narration,seconds,false); seek(music,seconds,true); }
    void volumes(VideoProject project) { if (narration != null) narration.setVolume(project.narration.volume); if (music != null) music.setVolume(project.music.volume); }
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
    @Override public void close() { playing = false; dispose(narration); dispose(music); narration = null; music = null; position = 0; }
    private static void dispose(MediaPlayer player) { if (player != null) { player.setOnReady(null); player.setOnError(null); player.stop(); player.dispose(); } }
}
