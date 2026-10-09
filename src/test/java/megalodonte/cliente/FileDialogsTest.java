package megalodonte.cliente;

import javax.swing.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.io.File;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class FileDialogsTest {
    @TempDir Path directory;
    @Test void everyActionHasCorrectModeFilterAndSelectionCount() throws Exception {
        SwingUtilities.invokeAndWait(()-> {
            for(var kind:FileDialogs.Kind.values()) {
                var chooser=FileDialogs.swingChooser(kind,directory);
                assertEquals(kind.save ? JFileChooser.SAVE_DIALOG : JFileChooser.OPEN_DIALOG,chooser.getDialogType());
                assertEquals(kind.save ? "Save" : "Open",chooser.getApproveButtonText());
                assertEquals(kind.multiple,chooser.isMultiSelectionEnabled()); assertFalse(chooser.isAcceptAllFileFilterUsed());
                for(String ext:kind.extensions) assertTrue(chooser.getFileFilter().accept(new File("arquivo."+ext.toUpperCase())));
                assertFalse(chooser.getFileFilter().accept(new File("arquivo.xyz")));
                assertEquals(directory.toFile(),chooser.getCurrentDirectory());
                if(kind.save) assertEquals(kind.filename,chooser.getSelectedFile().getName());
            }
        });
    }
    @Test void saveExtensionsAndInputValidationHandleCaseAndMissingFiles() throws Exception {
        assertEquals(directory.resolve("video.mp4"),FileSelections.destination(directory.resolve("video"),FileDialogs.Kind.VIDEO));
        assertEquals(directory.resolve("VIDEO.MP4"),FileSelections.destination(directory.resolve("VIDEO.MP4"),FileDialogs.Kind.VIDEO));
        assertEquals(directory.resolve("project.json"),FileSelections.destination(directory.resolve("project"),FileDialogs.Kind.PROJECT_SAVE));
        assertThrows(java.io.IOException.class,()->FileSelections.readable(directory.resolve("missing.png"),FileDialogs.Kind.IMAGE));
        var empty=directory.resolve("empty.png");Files.createFile(empty); assertThrows(java.io.IOException.class,()->FileSelections.readable(empty,FileDialogs.Kind.IMAGE));
        var image=directory.resolve("image.GiF");Files.writeString(image,"content"); assertEquals(image.toRealPath(),FileSelections.readable(image,FileDialogs.Kind.IMAGE));
        assertThrows(java.io.IOException.class,()->FileSelections.readable(image,FileDialogs.Kind.NARRATION));
        var folder=directory.resolve("folder.mp4");Files.createDirectory(folder);assertThrows(java.io.IOException.class,()->FileSelections.destination(folder,FileDialogs.Kind.VIDEO));
    }
    @Test void directoriesPersistSeparatelyAndImageActionsShareHistory() throws Exception {
        Path settings=directory.resolve("settings/file-dialogs.properties"); var store=new FileDialogFolders(settings);
        Path images=Files.createDirectory(directory.resolve("imagens com espaços")), narration=Files.createDirectory(directory.resolve("narração")),music=Files.createDirectory(directory.resolve("música"));
        store.remember(FileDialogs.Kind.IMAGES,images);store.remember(FileDialogs.Kind.NARRATION,narration);store.remember(FileDialogs.Kind.MUSIC,music);
        var restored=new FileDialogFolders(settings);
        assertEquals(images,restored.folder(FileDialogs.Kind.IMAGE).orElseThrow());assertEquals(images,restored.folder(FileDialogs.Kind.IMAGES).orElseThrow());
        assertEquals(narration,restored.folder(FileDialogs.Kind.NARRATION).orElseThrow());assertEquals(music,restored.folder(FileDialogs.Kind.MUSIC).orElseThrow());
        assertTrue(restored.folder(FileDialogs.Kind.VIDEO).isEmpty());
        Path projects=Files.createDirectory(directory.resolve("projetos")),exports=Files.createDirectory(directory.resolve("exports"));
        store.remember(FileDialogs.Kind.PROJECT_SAVE,projects);store.remember(FileDialogs.Kind.VIDEO,exports);
        restored=new FileDialogFolders(settings); assertEquals(projects,restored.folder(FileDialogs.Kind.PROJECT_OPEN).orElseThrow());
        assertEquals(exports,restored.folder(FileDialogs.Kind.VIDEO).orElseThrow());
        Files.delete(images);assertTrue(restored.folder(FileDialogs.Kind.IMAGE).isEmpty());
    }
    @Test void manifestsAndAssetsCannotBeOverwrittenByWrongOperation() throws Exception {
        var project=new VideoProject();Path asset=directory.resolve("asset.json");Files.writeString(asset,"data");project.music.file=asset.toString();
        assertThrows(java.io.IOException.class,()->FileSelections.protectAssets(project,asset,null,false));
        Path manifest=directory.resolve("project.mp4");Files.writeString(manifest,"data");
        assertThrows(java.io.IOException.class,()->FileSelections.protectAssets(project,manifest,manifest,true));
    }
}
