package com.makambox.studio.ui;

import com.makambox.studio.i18n.I18n;
import com.makambox.studio.i18n.LocalizationService;
import com.makambox.studio.study.FirstFourProfilePreferences;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Window;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Settings for the first-four-pitch similarity reference inventory. */
public final class MakamProfileSettingsDialog extends JDialog {
    private static final Color CRIMSON = new Color(178, 25, 49);

    private final LocalizationService i18n = I18n.get();
    private final FirstFourProfilePreferences preferences;
    private final Map<String, JCheckBox> optionalBoxes = new LinkedHashMap<>();
    private final Set<String> initialSelection;
    private boolean saved;

    private MakamProfileSettingsDialog(Window owner,
                                       FirstFourProfilePreferences preferences) {
        super(owner, ModalityType.APPLICATION_MODAL);
        this.preferences = preferences;
        this.initialSelection = new LinkedHashSet<>(preferences.enabledOptionalDisplayNames());
        setTitle(text("settings.firstFour.title", "First-four-pitch comparison settings"));
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(520, 560));
        setContentPane(buildContent());
        i18n.applyOrientation(this);
        pack();
        setSize(Math.max(560, getWidth()), Math.max(600, getHeight()));
        setLocationRelativeTo(owner);
    }

    /**
     * Shows the modal settings window and reports whether the persisted
     * selection changed.
     */
    public static boolean show(Window owner, FirstFourProfilePreferences preferences) {
        MakamProfileSettingsDialog dialog = new MakamProfileSettingsDialog(owner, preferences);
        dialog.setVisible(true);
        return dialog.saved && !dialog.initialSelection.equals(
                new LinkedHashSet<>(preferences.enabledOptionalDisplayNames()));
    }

    private JPanel buildContent() {
        JPanel root = new JPanel(new BorderLayout(12, 12));
        root.setBorder(BorderFactory.createEmptyBorder(18, 20, 16, 20));

        JPanel introduction = new JPanel();
        introduction.setLayout(new BoxLayout(introduction, BoxLayout.Y_AXIS));
        JLabel title = new JLabel(text("settings.firstFour.heading",
                "First-four-pitch similarity profiles"));
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        introduction.add(title);
        introduction.add(Box.createVerticalStrut(6));
        // Makam/çeşni names are scientific proper names and remain unchanged
        // in every locale.  Showing the actual immutable inventory here also
        // prevents an old translated word such as “six” from becoming stale
        // when the comparison set changes.
        JLabel description = new JLabel("<html>"
                + String.join(" &nbsp;•&nbsp; ", preferences.coreDisplayNames())
                + "</html>");
        description.setForeground(new Color(82, 96, 116));
        description.setAlignmentX(Component.LEFT_ALIGNMENT);
        introduction.add(description);
        root.add(introduction, BorderLayout.NORTH);

        JPanel list = new JPanel();
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setBorder(BorderFactory.createEmptyBorder(3, 4, 3, 12));
        addSectionHeading(list, text("settings.firstFour.core", "Always included"));
        for (String name : preferences.coreDisplayNames()) {
            JCheckBox box = new JCheckBox(name, true);
            box.setEnabled(false);
            box.setToolTipText(text("settings.firstFour.coreHint",
                    "Core profile; it is always included"));
            list.add(box);
        }
        list.add(Box.createVerticalStrut(14));
        addSectionHeading(list, text("settings.firstFour.optional", "Optional profiles"));
        Set<String> enabled = new LinkedHashSet<>(preferences.enabledOptionalDisplayNames());
        for (String name : preferences.optionalDisplayNames()) {
            JCheckBox box = new JCheckBox(name, enabled.contains(name));
            optionalBoxes.put(name, box);
            list.add(box);
        }

        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(BorderFactory.createLineBorder(new Color(222, 224, 229)));
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        root.add(scroll, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.TRAILING, 8, 0));
        JButton defaults = new JButton(text("settings.restoreDefaults", "Restore defaults"));
        defaults.addActionListener(event -> optionalBoxes.values().forEach(box ->
                box.setSelected(false)));
        JButton cancel = new JButton(text("waveEditor.cancel", "Cancel"));
        cancel.addActionListener(event -> dispose());
        JButton save = new JButton(text("settings.save", "Save"));
        save.setUI(new javax.swing.plaf.basic.BasicButtonUI());
        save.setOpaque(true);
        save.setContentAreaFilled(true);
        save.setBackground(CRIMSON);
        save.setForeground(Color.WHITE);
        save.setFont(save.getFont().deriveFont(Font.BOLD));
        save.addActionListener(event -> save());
        actions.add(defaults);
        actions.add(cancel);
        actions.add(save);
        getRootPane().setDefaultButton(save);
        root.add(actions, BorderLayout.SOUTH);
        return root;
    }

    private static void addSectionHeading(JPanel panel, String value) {
        JLabel heading = new JLabel(value, SwingConstants.LEADING);
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, 14f));
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(heading);
        panel.add(Box.createVerticalStrut(5));
    }

    private void save() {
        Set<String> selected = new LinkedHashSet<>();
        optionalBoxes.forEach((name, box) -> {
            if (box.isSelected()) selected.add(name);
        });
        try {
            preferences.setEnabledOptionalDisplayNames(selected);
            saved = true;
            dispose();
        } catch (RuntimeException error) {
            JOptionPane.showMessageDialog(this,
                    text("settings.saveError", "The settings could not be saved."),
                    getTitle(), JOptionPane.ERROR_MESSAGE);
        }
    }

    private String text(String key, String fallback) {
        return i18n.textOrDefault(key, fallback);
    }
}
