package com.makambox.studio.ui;

import java.awt.Dialog;
import java.awt.FileDialog;
import java.awt.Frame;
import java.awt.Window;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;

/** Uses the operating system's standard open/save panels instead of Swing's custom chooser. */
final class NativeFileDialogs {
    private NativeFileDialogs() {}

    static Path open(Window owner, String title, String... extensions) {
        FileDialog dialog = create(owner, title, FileDialog.LOAD);
        String[] normalized = Arrays.stream(extensions)
                .map(value -> value.toLowerCase(Locale.ROOT).replaceFirst("^\\.", "")).toArray(String[]::new);
        dialog.setFilenameFilter((directory, name) -> {
            String lower = name.toLowerCase(Locale.ROOT);
            for (String extension : normalized) if (lower.endsWith("." + extension)) return true;
            return false;
        });
        dialog.setVisible(true);
        return selected(dialog);
    }

    static Path save(Window owner, String title, String defaultName) {
        FileDialog dialog = create(owner, title, FileDialog.SAVE);
        dialog.setFile(defaultName);
        dialog.setVisible(true);
        return selected(dialog);
    }

    private static FileDialog create(Window owner, String title, int mode) {
        if (owner instanceof Frame frame) return new FileDialog(frame, title, mode);
        if (owner instanceof Dialog dialog) return new FileDialog(dialog, title, mode);
        return new FileDialog((Frame) null, title, mode);
    }

    private static Path selected(FileDialog dialog) {
        if (dialog.getFile() == null) return null;
        return Path.of(dialog.getDirectory(), dialog.getFile());
    }
}
