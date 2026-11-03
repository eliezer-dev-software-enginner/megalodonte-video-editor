package megalodonte.cliente;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;

class SceneTimingTest {
    @Test void cumulativeFrameBoundariesStayAlignedWithNarration() {
        var scenes=new ArrayList<VideoProject.Clip>();
        for(int i=0;i<100;i++) {var clip=new VideoProject.Clip("Cena "+i);clip.duration=.114;scenes.add(clip);}
        long[] frames=VideoExporter.sceneFrames(scenes);long total=0;double elapsed=0;
        for(int i=0;i<frames.length;i++) {
            assertTrue(frames[i]>0);total+=frames[i];elapsed+=scenes.get(i).duration;
            assertTrue(Math.abs(total/30.0-elapsed)<=1.0/60.0+1e-9,"Transição "+i+" acumulou desvio");
        }
        assertEquals(Math.round(elapsed*30),total);
    }
}
