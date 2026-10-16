package megalodonte.cliente;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.function.Consumer;

/** Local MP4 exporter. ProcessBuilder arguments never pass through a shell. */
public final class VideoExporter {
    private final int width, height;
    private volatile boolean cancelled;
    private volatile Process process;
    public VideoExporter() { this(1920,1080); }
    VideoExporter(int width,int height) { this.width=width; this.height=height; }
    public void cancel() { cancelled=true; var running=process; if(running!=null) running.destroyForcibly(); }
    public void export(VideoProject project, Path output, Consumer<String> progress) throws IOException {
        if(project.clips.isEmpty()) throw new IOException("Crie cenas e anexe as imagens antes de exportar.");
        Path target=output.toAbsolutePath().normalize();
        for(var clip:project.clips) {
            if(clip.image.isBlank() || !Files.isRegularFile(Path.of(clip.image))) throw new IOException("Anexe uma imagem para cada cena antes de exportar.");
            if(!Double.isFinite(clip.duration) || clip.duration<.1 || clip.duration>3600) throw new IOException("Duração de cena inválida.");
            try { VideoProject.validateVisualEffect(clip.visualEffect); } catch(IllegalArgumentException e) { throw new IOException(e.getMessage(),e); }
            rejectOutputSource(target,clip.image);
        }
        for(var track:List.of(project.narration,project.music)) {
            double maxGain=track==project.narration ? VideoProject.MAX_NARRATION_GAIN : 1;
            if(!Double.isFinite(track.volume) || track.volume<0 || track.volume>maxGain) throw new IOException("Volume inválido.");
            if(!track.file.isBlank()) { if(!Files.isRegularFile(Path.of(track.file))) throw new IOException("Áudio ausente: "+track.file); rejectOutputSource(target,track.file); }
        }
        for(var effect:project.effects) {
            try { effect.validate(); } catch(IllegalArgumentException e) { throw new IOException(e.getMessage(),e); }
            if(effect.file.isBlank() || !Files.isRegularFile(Path.of(effect.file))) throw new IOException("Efeito sonoro ausente: "+effect.file);
            rejectOutputSource(target,effect.file);
        }
        Files.createDirectories(target.getParent());
        Path work=Files.createTempDirectory(target.getParent(),".megalodonte-export-");
        try {
            var concat=new StringBuilder("ffconcat version 1.0\n");
            for(int i=0;i<project.clips.size();i++) {
                checkCancelled(); var clip=project.clips.get(i);
                progress.accept("Exportando cena "+(i+1)+" de "+project.clips.size()+"…");
                var image=ImageIO.read(Path.of(clip.image).toFile());
                if(image==null) throw new IOException("Imagem inválida: "+clip.image);
                var frame=work.resolve("frame.png"); ImageIO.write(image,"png",frame.toFile()); image.flush();
                String name=String.format(Locale.ROOT,"scene-%06d.mp4",i);
                long frames=Math.max(1,Math.round(clip.duration*30));
                String filter="scale="+width+":"+height+":force_original_aspect_ratio=decrease,pad="+width+":"+height+":(ow-iw)/2:(oh-ih)/2:black,setsar=1";
                if(project.animateImages) {
                    String zoom="1+0.05*on/"+Math.max(1,frames-1);
                    filter="scale="+(width*2)+":"+(height*2)+":force_original_aspect_ratio=increase,crop="+(width*2)+":"+(height*2)
                        +",zoompan=z='"+zoom+"':x='iw/2-iw/zoom/2':y='ih/2-ih/zoom/2':d=1:s="+width+"x"+height+":fps=30,setsar=1";
                    double fade=Math.min(.25,clip.duration/4);
                    filter+=",fade=t=in:st=0:d="+fade+",fade=t=out:st="+Math.max(0,frames/30.0-fade)+":d="+fade;
                }
                if(clip.visualEffect.equals("particles")) filter+=particleFilter(i);
                if(clip.visualEffect.equals("shake")) {
                    int marginX=Math.max(2,(int)Math.round(width/120.0))*2, marginY=Math.max(2,(int)Math.round(height/120.0))*2;
                    filter+=",crop="+(width-marginX)+":"+(height-marginY)
                        +":x='"+(marginX/2)+"+"+(marginX/4)+"*sin(n*0.31)':y='"+(marginY/2)+"+"+(marginY/4)+"*cos(n*0.27)',scale="+width+":"+height+",setsar=1";
                }
                var command=command(); Collections.addAll(command,"-loop","1","-framerate","30","-i",frame.toString(),
                    "-frames:v",Long.toString(frames),
                    "-vf",filter,
                    "-c:v","libx264","-preset","fast","-crf","20","-pix_fmt","yuv420p","-threads","2","-an",work.resolve(name).toString());
                run(command,work); concat.append("file '").append(name).append("'\n");
            }
            Path list=work.resolve("scenes.ffconcat"); Files.writeString(list,concat);
            progress.accept("Combinando cenas e áudio…");
            var command=command(); Collections.addAll(command,"-f","concat","-safe","1","-i",list.toString());
            var filters=new ArrayList<String>(); var labels=new ArrayList<String>(); int input=1;
            String duration=Double.toString(project.duration());
            for(var track:List.of(project.narration,project.music)) {
                if(track.file.isBlank()) continue;
                if(track==project.music) Collections.addAll(command,"-stream_loop","-1");
                Collections.addAll(command,"-i",Path.of(track.file).toAbsolutePath().toString());
                String label="a"+input;
                filters.add("["+input+":a:0]aresample=48000,aformat=channel_layouts=stereo,volume="+Double.toString(track.volume)+",apad,atrim=duration="+duration+",asetpts=PTS-STARTPTS["+label+"]");
                labels.add("["+label+"]"); input++;
            }
            for(var effect:project.effects) {
                if(effect.start>=project.duration()) continue;
                Collections.addAll(command,"-i",Path.of(effect.file).toAbsolutePath().toString());
                String label="a"+input;
                String filter="["+input+":a:0]aresample=48000,aformat=channel_layouts=stereo,atrim=start="+effect.offset+":duration="+effect.duration+",asetpts=PTS-STARTPTS,volume="+effect.volume;
                if(effect.fadeIn>0) filter+=",afade=t=in:d="+effect.fadeIn;
                if(effect.fadeOut>0) filter+=",afade=t=out:st="+(effect.duration-effect.fadeOut)+":d="+effect.fadeOut;
                filter+=",adelay="+Math.round(effect.start*48000)+"S:all=1,asetpts=N/SR/TB,apad,atrim=duration="+duration+"["+label+"]";
                filters.add(filter);labels.add("["+label+"]");input++;
            }
            Collections.addAll(command,"-map","0:v:0","-c:v","copy");
            if(!labels.isEmpty()) {
                filters.add(String.join("",labels)+(labels.size()>1 ? "amix=inputs="+labels.size()+":duration=longest:normalize=0," : "")+"alimiter=limit=0.95:level=false:latency=true[audio]");
                Collections.addAll(command,"-filter_complex",String.join(";",filters),"-map","[audio]","-c:a","aac","-b:a","192k","-ar","48000","-ac","2");
            } else command.add("-an");
            Path result=work.resolve("result.mp4");
            Collections.addAll(command,"-t",duration,"-movflags","+faststart",result.toString()); run(command,work); checkCancelled();
            try { Files.move(result,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
            catch(AtomicMoveNotSupportedException e) { Files.move(result,target,StandardCopyOption.REPLACE_EXISTING); }
        } finally {
            try(var paths=Files.walk(work)) { for(Path path:paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path); }
        }
    }
    private static void rejectOutputSource(Path output,String source) throws IOException {
        Path input=Path.of(source).toAbsolutePath().normalize();
        if(output.equals(input) || (Files.exists(output) && Files.isSameFile(output,input))) throw new IOException("Escolha um arquivo de saída diferente dos assets do projeto.");
    }
    /** Sparse, deterministic drifting lights; kept away from a central portrait in the opening. */
    private String particleFilter(int scene) {
        var result=new StringBuilder();var random=new Random(0x5eedL+scene);
        for(int i=0;i<26;i++) {
            int x=scene==0 ? (i%2==0 ? random.nextInt(width/4) : width*3/4+random.nextInt(width/4)) : random.nextInt(width);
            int y=random.nextInt(height), speed=8+random.nextInt(20), size=14+random.nextInt(11);
            result.append(",drawtext=text='·':fontcolor=white@0.5:fontsize=").append(size)
                .append(":x='mod(").append(x).append("+").append(speed).append("*t,w)':y='mod(")
                .append(y).append("-").append(speed).append("*t+h,h)'");
        }
        return result.toString();
    }
    private static ArrayList<String> command() {
        return new ArrayList<>(List.of("ffmpeg","-hide_banner","-loglevel","error","-nostdin","-y","-filter_threads","1","-filter_complex_threads","1"));
    }
    private void run(List<String> command,Path work) throws IOException {
        checkCancelled(); Path log=work.resolve("ffmpeg.log");
        try {
            process=new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile()).start();
            int code=process.waitFor(); checkCancelled();
            if(code!=0) { String error=Files.readString(log); throw new IOException("FFmpeg: "+error.substring(Math.max(0,error.length()-1200))); }
        } catch(InterruptedException e) { Thread.currentThread().interrupt(); cancel(); throw new CancellationException("Exportação cancelada."); }
        catch(IOException e) {
            if(process==null) throw new IOException("Não foi possível iniciar o FFmpeg. Instale-o e disponibilize o comando ffmpeg no PATH.",e);
            throw e;
        } finally {
            var running=process;
            if(running!=null && running.isAlive()) {
                running.destroyForcibly(); boolean interrupted=Thread.interrupted();
                try { running.waitFor(5,java.util.concurrent.TimeUnit.SECONDS); }
                catch(InterruptedException e) { interrupted=true; }
                finally { if(interrupted) Thread.currentThread().interrupt(); }
            }
            process=null;
        }
    }
    private void checkCancelled() { if(cancelled || Thread.currentThread().isInterrupted()) throw new CancellationException("Exportação cancelada."); }
}
