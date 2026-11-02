package megalodonte.cliente;

import java.awt.image.BufferedImage;
import java.nio.file.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ImageLibraryTest {
    @TempDir Path directory;

    @Test void reusesSameSceneOnlyForRelatedContextAndSameConfiguration() throws Exception {
        Path file=directory.resolve("cidade-amanhecer.png");ImageIO.write(new BufferedImage(32,18,BufferedImage.TYPE_INT_RGB),"png",file.toFile());
        String scene="Uma cidade brasileira ao amanhecer, vista de cima.";
        String first=scene+" Prédios modernos cercam uma praça iluminada pelos primeiros raios solares.";
        String related=scene+" Prédios modernos cercam uma praça iluminada pelos primeiros raios dourados.";
        ImageLibrary.record(file,scene,first,"modelo-tamanho-qualidade");
        assertEquals(file,ImageLibrary.find(directory,scene,related,"modelo-tamanho-qualidade").orElseThrow());
        assertTrue(ImageLibrary.find(directory,scene,"Uma estação espacial distante entre planetas.","modelo-tamanho-qualidade").isEmpty());
        assertTrue(ImageLibrary.find(directory,"Uma cidade à noite.",related,"modelo-tamanho-qualidade").isEmpty());
        assertTrue(ImageLibrary.find(directory,scene,related,"outro-modelo").isEmpty());
    }
}
