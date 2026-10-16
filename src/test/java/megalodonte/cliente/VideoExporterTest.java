package megalodonte.cliente;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.io.*;
import javax.imageio.ImageIO;
import javax.sound.sampled.*;
import java.awt.image.BufferedImage;
import java.awt.Color;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class VideoExporterTest {
    @TempDir Path directory;
    @BeforeEach void requireFfmpeg() throws Exception {
        try { Assumptions.assumeTrue(new ProcessBuilder("ffmpeg","-version").redirectOutput(ProcessBuilder.Redirect.DISCARD).start().waitFor()==0); }
        catch(IOException e) { Assumptions.assumeTrue(false,"FFmpeg necessário para testes de exportação"); }
    }
    @Test void exportsFullHdOrderedScenesWithNarrationAndQuietLoopingMusic() throws Exception {
        var project=VideoProject.fromScript("Vermelho\n\nAzul");
        project.clips.get(0).image=image("primeira ' imagem.png",Color.RED).toString();
        project.clips.get(1).image=image("segunda imagem.png",Color.BLUE).toString();
        project.clips.forEach(clip->clip.duration=.5);
        project.narration.file=wave("narração.wav",220,.25,.4).toString();
        project.music.file=wave("música.wav",440,.25,.2).toString();
        var output=directory.resolve("vídeo final.mp4"); new VideoExporter().export(project,output,message->{});
        String info=textCommand("ffprobe","-v","error","-show_entries","stream=codec_name,width,height,r_frame_rate:format=duration","-of","default=noprint_wrappers=1",output.toString());
        assertTrue(info.contains("width=1920")); assertTrue(info.contains("height=1080")); assertTrue(info.contains("codec_name=h264")); assertTrue(info.contains("codec_name=aac")); assertTrue(info.contains("r_frame_rate=30/1"));
        assertFrameColor(output,.1,Color.RED); assertFrameColor(output,.7,Color.BLUE);
        byte[] late=binaryCommand("ffmpeg","-v","error","-ss","0.6","-i",output.toString(),"-t","0.2","-vn","-ac","1","-ar","48000","-f","s16le","pipe:1");
        double rms=rms(late); assertTrue(rms>.018 && rms<.035,"Background music at 15%, looped after narration ends: "+rms);
        byte[] early=binaryCommand("ffmpeg","-v","error","-ss","0.05","-i",output.toString(),"-t","0.2","-vn","-ac","1","-ar","48000","-f","s16le","pipe:1");
        assertTrue(rms(early)>rms*4,"Narration should remain substantially louder than music");
    }
    @Test void effectsRespectPlacementTrimOverlapVolumeAndEnd() throws Exception {
        var p=VideoProject.fromScript("Cena");p.clips.getFirst().duration=2;p.clips.getFirst().image=image("effects.png",Color.BLUE).toString();
        var effect=new VideoProject.SoundEffect();effect.file=wave("effect.wav",440,.4,2).toString();effect.start=.4;effect.offset=.2;effect.duration=.8;effect.volume=.25;effect.fadeIn=0;effect.fadeOut=0;p.effects.add(effect);
        var overlap=effect.copy();overlap.start=.8;p.effects.add(overlap);
        var muted=effect.copy();muted.start=0;muted.volume=0;p.effects.add(muted);
        var output=directory.resolve("effects.mp4");new VideoExporter(320,180).export(p,output,m->{});
        double before=audioRms(output,.1),single=audioRms(output,.5),both=audioRms(output,.9),after=audioRms(output,1.8);
        assertTrue(before<.001);assertTrue(single>.045 && single<.055,"Individual gain after mono-to-stereo conversion: "+single);assertTrue(both>single*1.8,"Effects overlap");assertTrue(after<.001);
        effect.file=directory.resolve("missing.wav").toString();assertThrows(IOException.class,()->new VideoExporter(320,180).export(p,output,m->{}));
    }
    private double audioRms(Path output,double start) throws Exception {
        return rms(binaryCommand("ffmpeg","-v","error","-ss",Double.toString(start),"-i",output.toString(),"-t","0.1","-vn","-ac","1","-ar","48000","-f","s16le","pipe:1"));
    }
    @Test void exportsWithoutAudioAndMuteProducesSilence() throws Exception {
        var project=VideoProject.fromScript("Cena"); project.clips.getFirst().image=image("still.png",Color.GREEN).toString(); project.clips.getFirst().duration=.3;
        var silent=directory.resolve("silent.mp4"); new VideoExporter(320,180).export(project,silent,message->{});
        assertEquals("",textCommand("ffprobe","-v","error","-select_streams","a","-show_entries","stream=codec_name","-of","csv=p=0",silent.toString()).strip());
        project.music.file=wave("music.wav",440,.3,.2).toString(); project.music.volume=0;
        var muted=directory.resolve("muted.mp4"); new VideoExporter(320,180).export(project,muted,message->{});
        assertTrue(rms(binaryCommand("ffmpeg","-v","error","-i",muted.toString(),"-vn","-ac","1","-f","s16le","pipe:1"))<.001);
    }
    @Test void animatedExportKeepsDurationAndUsesFadesWithoutAddingAudio() throws Exception {
        var project=VideoProject.fromScript("Cena ilustrativa"); project.animateImages=true;
        project.clips.getFirst().image=image("animated.png",Color.RED).toString(); project.clips.getFirst().duration=1;
        var file=directory.resolve("animated.mp4"); new VideoExporter(320,180).export(project,file,message->{});
        assertEquals(1,Double.parseDouble(textCommand("ffprobe","-v","error","-show_entries","format=duration","-of","csv=p=0",file.toString()).strip()),.04);
        assertFrameColor(file,0,Color.BLACK); assertFrameColor(file,.5,Color.RED);
        assertEquals("",textCommand("ffprobe","-v","error","-select_streams","a","-show_entries","stream=codec_name","-of","csv=p=0",file.toString()).strip());
    }
    @Test void particleCueRendersMovingLights() throws Exception {
        var project=VideoProject.fromScript("Abertura");var clip=project.clips.getFirst();
        clip.image=image("dark.png",Color.BLACK).toString();clip.duration=1;clip.visualEffect="particles";
        var file=directory.resolve("particles.mp4");new VideoExporter(320,180).export(project,file,message->{});
        assertTrue(Files.size(file)>0);
        var first=binaryCommand("ffmpeg","-v","error","-ss","0.1","-i",file.toString(),"-frames:v","1","-f","rawvideo","-pix_fmt","rgb24","pipe:1");
        var later=binaryCommand("ffmpeg","-v","error","-ss","0.8","-i",file.toString(),"-frames:v","1","-f","rawvideo","-pix_fmt","rgb24","pipe:1");
        assertFalse(Arrays.equals(first,later),"Particles should move over time");
    }
    @Test void shakeCueMovesImageSlightly() throws Exception {
        var striped=new BufferedImage(320,180,BufferedImage.TYPE_INT_RGB);
        for(int y=0;y<180;y++)for(int x=0;x<320;x++)striped.setRGB(x,y,((x/8+y/8)%2==0 ? Color.WHITE : Color.BLACK).getRGB());
        var source=directory.resolve("striped.png");ImageIO.write(striped,"png",source.toFile());
        var project=VideoProject.fromScript("Impacto");var clip=project.clips.getFirst();clip.image=source.toString();clip.duration=1;clip.visualEffect="shake";
        var file=directory.resolve("shake.mp4");new VideoExporter(320,180).export(project,file,message->{});
        var first=binaryCommand("ffmpeg","-v","error","-ss","0.1","-i",file.toString(),"-frames:v","1","-f","rawvideo","-pix_fmt","rgb24","pipe:1");
        var later=binaryCommand("ffmpeg","-v","error","-ss","0.8","-i",file.toString(),"-frames:v","1","-f","rawvideo","-pix_fmt","rgb24","pipe:1");
        assertFalse(Arrays.equals(first,later));
    }
    @Test void smoothMotionCueMovesImageOverTime() throws Exception {
        var striped=new BufferedImage(320,180,BufferedImage.TYPE_INT_RGB);
        for(int y=0;y<180;y++)for(int x=0;x<320;x++)striped.setRGB(x,y,((x/8+y/8)%2==0 ? Color.WHITE : Color.BLACK).getRGB());
        var source=directory.resolve("smooth-striped.png");ImageIO.write(striped,"png",source.toFile());
        var project=VideoProject.fromScript("Movimento suave");var clip=project.clips.getFirst();clip.image=source.toString();clip.duration=1;clip.visualEffect="smooth";
        var file=directory.resolve("smooth.mp4");new VideoExporter(320,180).export(project,file,message->{});
        var first=binaryCommand("ffmpeg","-v","error","-ss","0.1","-i",file.toString(),"-frames:v","1","-f","rawvideo","-pix_fmt","rgb24","pipe:1");
        var later=binaryCommand("ffmpeg","-v","error","-ss","0.8","-i",file.toString(),"-frames:v","1","-f","rawvideo","-pix_fmt","rgb24","pipe:1");
        assertFalse(Arrays.equals(first,later),"Smooth motion should change framing during the clip");
    }
    @Test void cancellationAndMissingAssetsPreserveExistingOutput() throws Exception {
        var output=directory.resolve("existing.mp4"); Files.writeString(output,"original");
        var project=VideoProject.fromScript("Cena");
        assertThrows(IOException.class,()->new VideoExporter().export(project,output,message->{}));
        project.clips.getFirst().image=image("still.png",Color.RED).toString(); var exporter=new VideoExporter();
        assertThrows(java.util.concurrent.CancellationException.class,()->exporter.export(project,output,message->exporter.cancel()));
        assertEquals("original",Files.readString(output));
        try(var paths=Files.list(directory)) { assertFalse(paths.anyMatch(path->path.getFileName().toString().startsWith(".megalodonte-export-"))); }
    }
    private Path image(String name,Color color) throws IOException {
        var image=new BufferedImage(320,180,BufferedImage.TYPE_INT_RGB); var graphics=image.createGraphics(); graphics.setColor(color); graphics.fillRect(0,0,320,180); graphics.dispose();
        Path path=directory.resolve(name); ImageIO.write(image,"png",path.toFile()); return path;
    }
    private Path wave(String name,double frequency,double amplitude,double duration) throws Exception {
        byte[] bytes=new byte[(int)(48000*duration)*2];
        for(int i=0;i<bytes.length/2;i++) { short value=(short)(Math.sin(2*Math.PI*frequency*i/48000)*amplitude*32767); bytes[i*2]=(byte)value; bytes[i*2+1]=(byte)(value>>>8); }
        var format=new AudioFormat(48000,16,1,true,false); Path path=directory.resolve(name);
        try(var audio=new AudioInputStream(new ByteArrayInputStream(bytes),format,bytes.length/2)) { AudioSystem.write(audio,AudioFileFormat.Type.WAVE,path.toFile()); } return path;
    }
    private void assertFrameColor(Path file,double seconds,Color expected) throws Exception {
        byte[] bytes=binaryCommand("ffmpeg","-v","error","-ss",Double.toString(seconds),"-i",file.toString(),"-frames:v","1","-f","image2pipe","-vcodec","png","pipe:1");
        var image=ImageIO.read(new ByteArrayInputStream(bytes)); var actual=new Color(image.getRGB(image.getWidth()/2,image.getHeight()/2));
        assertTrue(Math.abs(expected.getRed()-actual.getRed())<10 && Math.abs(expected.getBlue()-actual.getBlue())<10);
    }
    private static double rms(byte[] bytes) {
        double total=0; for(int i=0;i+1<bytes.length;i+=2) { short sample=(short)((bytes[i]&255)|(bytes[i+1]<<8)); double value=sample/32768.0; total+=value*value; }
        return Math.sqrt(total/Math.max(1,bytes.length/2));
    }
    private static String textCommand(String... args) throws Exception { return new String(binaryCommand(args),java.nio.charset.StandardCharsets.UTF_8); }
    private static byte[] binaryCommand(String... args) throws Exception {
        var process=new ProcessBuilder(args).redirectError(ProcessBuilder.Redirect.INHERIT).start(); byte[] bytes=process.getInputStream().readAllBytes(); assertEquals(0,process.waitFor()); return bytes;
    }
}
