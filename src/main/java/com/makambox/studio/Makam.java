package com.makambox.studio;

import com.makambox.studio.ui.MakamFrame;
import com.makambox.studio.ui.MakamDocxReportHandler;
import com.makambox.studio.ui.MakamAboutDialog;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.Desktop;

/** Standalone entry point for the guided academic application named Makam. */
public final class Makam {
    private Makam() { }

    public static void main(String[] args) {
        System.setProperty("apple.laf.useScreenMenuBar", "true");
        System.setProperty("apple.awt.application.name", "MakamLab");
        System.setProperty("apple.awt.application.appearance", "system");
        SwingUtilities.invokeLater(() -> {
            try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
            catch (Exception ignored) { }
            UIManager.put("Component.arc", 12);
            UIManager.put("Button.arc", 12);
            MakamFrame frame = new MakamFrame();
            frame.setWordReportHandler(new MakamDocxReportHandler());
            if (Desktop.isDesktopSupported()
                    && Desktop.getDesktop().isSupported(Desktop.Action.APP_ABOUT)) {
                Desktop.getDesktop().setAboutHandler(event -> MakamAboutDialog.show(frame));
            }
            frame.setVisible(true);
        });
    }
}
