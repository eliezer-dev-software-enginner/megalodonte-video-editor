package megalodonte.cliente;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/** Estimates a safe per-effect mix level; unavailable FFmpeg falls back to a usable default. */
final class SoundLevel {
    private static final Pattern MEAN = Pattern.compile("mean_volume:\\s*(-?[0-9]+(?:\\.[0-9]+)?) dB");
    private SoundLevel() { }

    static double effectVolume(Path file) {
        Process process=null;
        try {
            process=new ProcessBuilder("ffmpeg","-hide_banner","-nostats","-v","info","-i",file.toString(),
                    "-t","5","-af","volumedetect","-f","null","-").redirectErrorStream(true).start();
            if(!process.waitFor(15,TimeUnit.SECONDS))return .5;
            String output=new String(process.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
            var matcher=MEAN.matcher(output);
            if(process.exitValue()!=0 || !matcher.find())return .5;
            double mean=Double.parseDouble(matcher.group(1));
            return Math.max(.25,Math.min(1,Math.pow(10,(-24-mean)/20)));
        } catch(Exception ignored) { return .5; }
        finally { if(process!=null && process.isAlive())process.destroyForcibly(); }
    }
}
