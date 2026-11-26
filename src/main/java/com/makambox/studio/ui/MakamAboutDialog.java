package com.makambox.studio.ui;

import com.makambox.studio.i18n.LocalizationService;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.net.URI;

/** Localized authorship, contact and component-by-component license notice. */
public final class MakamAboutDialog {
    private static final String CONTACT = "emrepinarbasi@gmail.com";

    private static final String[][] LICENSES = {
            {"MakamLab V1.1", "GPL-3.0-or-later"},
            {"MakamBox v1.0", "GPL-3.0-or-later"},
            {"JLayer 1.0.1", "LGPL-2.1"},
            {"FFmpeg 9.0.2 (Windows audio component)", "LGPL-2.1-or-later"},
            {"Apache POI / POI OOXML 6.0.0", "Apache-2.0"},
            {"Apache XMLBeans 5.4.1", "Apache-2.0"},
            {"Apache Log4j API / Core 2.26.1", "Apache-2.0"},
            {"Apache Commons Codec / Collections / Compress / IO / Math", "Apache-2.0"},
            {"SparseBitSet 1.3", "Apache-2.0"},
            {"curvesapi 1.08", "BSD-3-Clause"},
            {"curvesapi — Apache Harmony Crossing", "Apache-2.0"},
            {"OpenJDK / JetBrains Runtime 21", "GPL-2.0 with Classpath Exception"}
    };

    private MakamAboutDialog() { }

    static String[][] diagnosticLicenseRows() {
        String[][] copy = new String[LICENSES.length][];
        for (int index = 0; index < LICENSES.length; index++) {
            copy[index] = LICENSES[index].clone();
        }
        return copy;
    }

    public static void show(Component owner) {
        LocalizationService i18n = LocalizationService.getInstance();
        java.awt.Window parent = owner == null ? null : SwingUtilities.getWindowAncestor(owner);
        JDialog dialog = new JDialog(parent,
                i18n.textOrDefault("about.title", "About MakamLab"),
                java.awt.Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setName("makamlab-about-dialog");
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        JPanel content = new JPanel(new BorderLayout(0, 12));
        content.setBorder(BorderFactory.createEmptyBorder(16, 18, 14, 18));

        JPanel identity = new JPanel(new GridLayout(0, 1, 0, 4));
        JLabel title = new JLabel(i18n.textOrDefault("about.title", "About MakamLab"),
                SwingConstants.CENTER);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));
        identity.add(title);
        identity.add(centered(i18n.textOrDefault("about.version",
                "Version {0} • {1}", "1.1.3", "2026")));
        identity.add(centered(i18n.textOrDefault("about.developer",
                "Developed by {0}.", "Prof. Dr. Emre PINARBAŞI")));
        identity.add(centered(i18n.textOrDefault("about.makamboxAttribution",
                "Uses theory and reference data derived from the MakamBox project.")));
        content.add(identity, BorderLayout.NORTH);

        DefaultTableModel model = new DefaultTableModel(
                new Object[]{i18n.textOrDefault("about.component", "Component"),
                        i18n.textOrDefault("about.license", "License")}, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
        for (String[] row : LICENSES) model.addRow(row);
        JTable table = new JTable(model);
        table.setName("makamlab-license-table");
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(24);
        table.setFillsViewportHeight(true);
        table.getColumnModel().getColumn(0).setPreferredWidth(420);
        table.getColumnModel().getColumn(1).setPreferredWidth(260);
        JScrollPane licenses = new JScrollPane(table);
        licenses.setPreferredSize(new Dimension(720, 360));
        licenses.setBorder(BorderFactory.createTitledBorder(i18n.textOrDefault(
                "about.license.title", "Licenses and third-party components")));
        content.add(licenses, BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout(10, 8));
        JTextArea notice = new JTextArea(i18n.textOrDefault("about.licenseNotice",
                "Full license texts and third-party notices for the components used by MakamLab "
                        + "are included with the application."));
        notice.setEditable(false);
        notice.setOpaque(false);
        notice.setLineWrap(true);
        notice.setWrapStyleWord(true);
        notice.setRows(3);
        footer.add(notice, BorderLayout.CENTER);

        JPanel actions = new JPanel();
        JButton email = new AutoFitButton(i18n.textOrDefault("about.contact", "Contact: {0}", CONTACT));
        email.setToolTipText("mailto:" + CONTACT);
        email.addActionListener(event -> openEmail());
        JButton close = new AutoFitButton(i18n.textOrDefault("about.close", "Close"));
        close.addActionListener(event -> dialog.dispose());
        actions.add(email);
        actions.add(close);
        footer.add(actions, BorderLayout.SOUTH);
        content.add(footer, BorderLayout.SOUTH);

        dialog.setContentPane(content);
        i18n.applyOrientation(content);
        // The license identifiers themselves are left-to-right technical data.
        table.setComponentOrientation(java.awt.ComponentOrientation.LEFT_TO_RIGHT);
        dialog.pack();
        dialog.setMinimumSize(new Dimension(700, 620));
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
    }

    private static JLabel centered(String value) {
        return new JLabel(value, SwingConstants.CENTER);
    }

    private static void openEmail() {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().mail(URI.create("mailto:" + CONTACT));
            }
        } catch (Exception ignored) {
            // The visible address can still be copied when no mail client exists.
        }
    }
}
