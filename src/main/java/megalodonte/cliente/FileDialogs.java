package megalodonte.cliente;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.io.File;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;

/** Shared dialog contract for every open/save operation in the editor. */
final class FileDialogs {
    enum Kind {
        PROJECT_OPEN("Abrir projeto",false,false,"Projeto Megalodonte",null,"json"),
        PROJECT_SAVE("Salvar projeto",true,false,"Projeto Megalodonte","meu-video.mvideo.json","json"),
        IMAGES("Importar imagens (ordenadas pelo nome)",false,true,"Imagens",null,"png","jpg","jpeg","gif"),
        IMAGE("Imagem da cena selecionada",false,false,"Imagens",null,"png","jpg","jpeg","gif"),
        NARRATION("Anexar narração",false,false,"Áudio",null,"mp3","wav","m4a","aac","flac","ogg"),
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
        List<Path> result=(kind.save || System.getProperty("os.name","").toLowerCase(Locale.ROOT).contains("linux"))
            ? swing(kind,owner,initial) : nativeDialog(kind,owner,initial);
        return result;
    }
    void remember(Kind kind,Path folder) throws java.io.IOException { folders.remember(kind,folder); }
    static Path existingFolder(Path folder) {
        if (folder!=null && Files.isDirectory(folder)) return folder.toAbsolutePath().normalize();
        Path home=Path.of(System.getProperty("user.home")); return Files.isDirectory(home) ? home : null;
    }
    static JFileChooser swingChooser(Kind kind,Path folder) {
        var chooser=new JFileChooser();
        configure(chooser,kind,folder); return chooser;
    }
    private static void configure(JFileChooser chooser,Kind kind,Path folder) {
        if(folder!=null) chooser.setCurrentDirectory(folder.toFile());
        chooser.setDialogType(kind.save ? JFileChooser.SAVE_DIALOG : JFileChooser.OPEN_DIALOG);
        chooser.setDialogTitle(kind.title); chooser.setApproveButtonText(kind.save ? "Save" : "Open");
        chooser.setMultiSelectionEnabled(kind.multiple); chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false); chooser.setFileFilter(new FileNameExtensionFilter(kind.description,kind.extensions));
        if(kind.filename!=null) chooser.setSelectedFile(new File(chooser.getCurrentDirectory(),kind.filename));
    }
    private static List<Path> nativeDialog(Kind kind,Window owner,Path folder) {
        var chooser=new FileChooser(); chooser.setTitle(kind.title);
        if(folder!=null) chooser.setInitialDirectory(folder.toFile()); if(kind.filename!=null) chooser.setInitialFileName(kind.filename);
        var patterns=new ArrayList<String>();
        for(String extension:kind.extensions) caseVariants(extension,"",patterns);
        var filter=new FileChooser.ExtensionFilter(kind.description,patterns); chooser.getExtensionFilters().add(filter); chooser.setSelectedExtensionFilter(filter);
        if(kind.multiple) { var files=chooser.showOpenMultipleDialog(owner); return files==null ? List.of() : files.stream().map(File::toPath).toList(); }
        File file=kind.save ? chooser.showSaveDialog(owner) : chooser.showOpenDialog(owner); return file==null ? List.of() : List.of(file.toPath());
    }
    private static void caseVariants(String remaining,String prefix,List<String> result) {
        if(remaining.isEmpty()) { result.add("*."+prefix); return; }
        char c=remaining.charAt(0); caseVariants(remaining.substring(1),prefix+c,result);
        if(Character.isLetter(c)) caseVariants(remaining.substring(1),prefix+Character.toUpperCase(c),result);
    }
    private static List<Path> swing(Kind kind,Window owner,Path folder) {
        if(!Platform.canStartNestedEventLoop()) throw new IllegalStateException("O seletor não pode abrir durante uma atualização de layout.");
        Object key=new Object(); var result=new AtomicReference<List<Path>>(List.of()); var error=new AtomicReference<Throwable>();
        var dialog=new AtomicReference<JDialog>(); var root=owner!=null && owner.getScene()!=null ? owner.getScene().getRoot() : null;
        boolean disabled=root!=null && root.isDisable(); if(root!=null) root.setDisable(true);
        var ownerVisible=new AtomicBoolean(owner==null || owner.isShowing());
        final double x=owner==null ? 0 : owner.getX(), y=owner==null ? 0 : owner.getY();
        final double width=owner==null ? 0 : owner.getWidth(), height=owner==null ? 0 : owner.getHeight();
        ChangeListener<Boolean> hidden=(obs,old,visible)-> { ownerVisible.set(visible); if(!visible) SwingUtilities.invokeLater(()-> { var current=dialog.get(); if(current!=null) current.dispose(); }); };
        if(owner!=null) owner.showingProperty().addListener(hidden);
        SwingUtilities.invokeLater(()-> {
            try {
                if(!ownerVisible.get()) return;
                var chooser=new JFileChooser() {
                    @Override protected JDialog createDialog(java.awt.Component parent) {
                        var window=super.createDialog(parent); dialog.set(window);
                        if(owner!=null) window.setLocation((int)(x+(width-window.getWidth())/2),(int)(y+(height-window.getHeight())/2));
                        return window;
                    }
                };
                configure(chooser,kind,folder);
                int answer=kind.save ? chooser.showSaveDialog(null) : chooser.showOpenDialog(null);
                if(answer==JFileChooser.APPROVE_OPTION) {
                    File[] files=kind.multiple ? chooser.getSelectedFiles() : new File[]{chooser.getSelectedFile()};
                    result.set(Arrays.stream(files).filter(Objects::nonNull).map(File::toPath).toList());
                }
            } catch(Throwable problem) { error.set(problem); }
            finally { Platform.runLater(()->Platform.exitNestedEventLoop(key,null)); }
        });
        try { Platform.enterNestedEventLoop(key); }
        finally { if(owner!=null) owner.showingProperty().removeListener(hidden); if(root!=null) root.setDisable(disabled); }
        if(error.get()!=null) throw new IllegalStateException("Não foi possível abrir o seletor de arquivos",error.get());
        return result.get();
    }
}
