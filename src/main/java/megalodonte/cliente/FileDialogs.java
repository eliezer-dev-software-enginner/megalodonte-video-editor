package megalodonte.cliente;

import javafx.application.Platform;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import java.io.File;
import java.nio.file.*;
import java.util.*;

/** Shared dialog contract for every open/save operation in the editor. */
final class FileDialogs {
    enum Kind {
        PROJECT_OPEN("Abrir projeto",false,false,"Projeto Megalodonte",null,"json"),
        PROJECT_SAVE("Salvar projeto",true,false,"Projeto Megalodonte","meu-video.mvideo.json","json"),
        IMAGES("Importar imagens (ordenadas pelo nome)",false,true,"Imagens",null,"png","jpg","jpeg","gif"),
        IMAGE("Imagem da cena selecionada",false,false,"Imagens",null,"png","jpg","jpeg","gif"),
        NARRATION("Anexar narração",false,false,"Áudio",null,"mp3","wav","m4a","aac","flac","ogg"),
        EFFECT("Adicionar efeito sonoro",false,false,"Áudio",null,"mp3","wav","m4a","aac","flac","ogg"),
        MUSIC("Adicionar música de fundo",false,false,"Áudio",null,"mp3","wav","m4a","aac","flac","ogg"),
        VIDEO("Exportar vídeo MP4 · YouTube 16:9",true,false,"Vídeo MP4","meu-video.mp4","mp4");
        final String title, description, filename; final boolean save, multiple; final String[] extensions;
        Kind(String title,boolean save,boolean multiple,String description,String filename,String... extensions) {
            this.title=title;this.save=save;this.multiple=multiple;this.description=description;this.filename=filename;this.extensions=extensions;
        }
    }
    private final FileDialogFolders folders=new FileDialogFolders();
    List<Path> choose(Kind kind,Window owner,Path projectFolder) throws java.io.IOException {
        if (!Platform.isFxApplicationThread()) throw new IllegalStateException("Diálogos devem ser abertos na thread JavaFX.");
        Path remembered=null;
        try { remembered=folders.folder(kind).orElse(null); } catch(java.io.IOException ignored) { }
        Path initial=existingFolder(remembered==null ? projectFolder : remembered);
        return nativeDialog(kind,owner,initial);
    }
    void remember(Kind kind,Path folder) throws java.io.IOException { folders.remember(kind,folder); }
    static Path existingFolder(Path folder) {
        if (folder!=null && Files.isDirectory(folder)) return folder.toAbsolutePath().normalize();
        Path home=Path.of(System.getProperty("user.home")); return Files.isDirectory(home) ? home : null;
    }
    private static List<Path> nativeDialog(Kind kind,Window owner,Path folder) {
        var chooser=nativeChooser(kind,folder);
        if(kind.multiple) { var files=chooser.showOpenMultipleDialog(owner); return files==null ? List.of() : files.stream().map(File::toPath).toList(); }
        File file=kind.save ? chooser.showSaveDialog(owner) : chooser.showOpenDialog(owner); return file==null ? List.of() : List.of(file.toPath());
    }
    static FileChooser nativeChooser(Kind kind,Path folder) {
        var chooser=new FileChooser(); chooser.setTitle(kind.title);
        if(folder!=null) chooser.setInitialDirectory(folder.toFile()); if(kind.filename!=null) chooser.setInitialFileName(kind.filename);
        var patterns=new ArrayList<String>();
        for(String extension:kind.extensions) caseVariants(extension,"",patterns);
        var filter=new FileChooser.ExtensionFilter(kind.description,patterns); chooser.getExtensionFilters().add(filter); chooser.setSelectedExtensionFilter(filter);
        return chooser;
    }
    private static void caseVariants(String remaining,String prefix,List<String> result) {
        if(remaining.isEmpty()) { result.add("*."+prefix); return; }
        char c=remaining.charAt(0); caseVariants(remaining.substring(1),prefix+c,result);
        if(Character.isLetter(c)) caseVariants(remaining.substring(1),prefix+Character.toUpperCase(c),result);
    }
}
