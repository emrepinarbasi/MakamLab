package com.makambox.studio.ui;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.ComponentOrientation;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Compact flag button backed by a scrollable, alphabetically sorted language
 * popup.  The class deliberately depends only on adapter functions, keeping it
 * reusable by the application's i18n service without coupling the UI to the
 * language catalogue implementation.
 */
public final class LanguageSelectorButton<T> extends AutoFitButton {
    private final Function<T, String> flagProvider;
    private final Function<T, String> nameProvider;
    private final List<T> options = new ArrayList<>();
    private Consumer<T> selectionListener = ignored -> {};
    private Supplier<Locale> sortLocaleProvider = Locale::getDefault;
    private T selectedOption;
    private String selectorLabel = "Select language";
    private String selectedLanguagePrefix = "Language";

    public LanguageSelectorButton(Function<T, String> flagProvider,
                                  Function<T, String> nameProvider) {
        super("🌐");
        this.flagProvider = Objects.requireNonNull(flagProvider, "flagProvider");
        this.nameProvider = Objects.requireNonNull(nameProvider, "nameProvider");
        setName("makam-language-selector");
        // AquaButtonUI reserves very wide internal side insets. AutoFitButton
        // then sees almost no drawable width and shrinks a 30 pt flag down to
        // a tiny 9 pt glyph. A compact basic UI keeps the intended Retina-size
        // flag while retaining a clear, conventional button boundary.
        setUI(new javax.swing.plaf.basic.BasicButtonUI());
        setOpaque(true);
        setContentAreaFilled(true);
        setBackground(Color.WHITE);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(205, 208, 214), 1, true),
                BorderFactory.createEmptyBorder(3, 8, 3, 8)));
        setHorizontalAlignment(SwingConstants.CENTER);
        // Emoji flags have a much smaller visible mark than ordinary text on
        // macOS Retina displays. Keep the selected language unmistakable.
        setFont(getFont().deriveFont(Font.PLAIN, 30f));
        setMinimumAutoFitFontSize(24f);
        setMargin(new java.awt.Insets(2, 8, 2, 8));
        setPreferredSize(new Dimension(80, 46));
        setMinimumSize(new Dimension(72, 42));
        getAccessibleContext().setAccessibleName(selectorLabel);
        addActionListener(event -> showLanguagePopup());
    }

    public void setOptions(Collection<? extends T> values, T selected) {
        options.clear();
        options.addAll(values);
        setSelectedOption(selected);
    }

    public void setSelectedOption(T selected) {
        selectedOption = selected;
        if (selected == null) {
            setText("🌐");
            setToolTipText(selectorLabel);
            getAccessibleContext().setAccessibleName(selectorLabel);
            return;
        }
        setText(flagProvider.apply(selected));
        String name = nameProvider.apply(selected);
        setToolTipText(name);
        getAccessibleContext().setAccessibleName(selectedLanguagePrefix + ": " + name);
    }

    public T selectedOption() {
        return selectedOption;
    }

    public void setSelectionListener(Consumer<T> listener) {
        selectionListener = Objects.requireNonNull(listener, "listener");
    }

    public void setSortLocaleProvider(Supplier<Locale> provider) {
        sortLocaleProvider = Objects.requireNonNull(provider, "provider");
    }

    public void setAccessibilityText(String selectorLabel, String selectedLanguagePrefix) {
        this.selectorLabel = Objects.requireNonNull(selectorLabel, "selectorLabel");
        this.selectedLanguagePrefix = Objects.requireNonNull(
                selectedLanguagePrefix, "selectedLanguagePrefix");
        setSelectedOption(selectedOption);
    }

    private void showLanguagePopup() {
        List<T> sorted = sortedOptions();
        DefaultListModel<T> model = new DefaultListModel<>();
        sorted.forEach(model::addElement);
        JList<T> list = new JList<>(model);
        list.setName("makam-language-list");
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setFixedCellHeight(40);
        list.setCellRenderer(new LanguageRenderer());
        // The product requirement keeps the flag in the left column and the
        // language name to its right, even when the selected application
        // language itself uses a right-to-left workspace orientation.
        list.setComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT);
        if (selectedOption != null) list.setSelectedValue(selectedOption, true);

        JPopupMenu popup = new JPopupMenu();
        popup.setName("makam-language-popup");
        popup.setLayout(new BorderLayout());
        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setPreferredSize(new Dimension(310, 510));
        popup.add(scroll, BorderLayout.CENTER);

        Runnable accept = () -> {
            T selected = list.getSelectedValue();
            if (selected == null) return;
            popup.setVisible(false);
            setSelectedOption(selected);
            selectionListener.accept(selected);
        };
        list.addMouseListener(new MouseAdapter() {
            @Override public void mouseReleased(MouseEvent event) {
                if (SwingUtilities.isLeftMouseButton(event)) accept.run();
            }
        });
        list.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent event) {
                if (event.getKeyCode() == KeyEvent.VK_ENTER) {
                    event.consume();
                    accept.run();
                }
            }
        });
        popup.show(this, Math.min(0, getWidth() - scroll.getPreferredSize().width), getHeight() + 2);
        list.requestFocusInWindow();
    }

    private List<T> sortedOptions() {
        Locale locale = sortLocaleProvider.get();
        Collator collator = Collator.getInstance(locale == null ? Locale.getDefault() : locale);
        collator.setStrength(Collator.PRIMARY);
        return options.stream()
                .sorted(Comparator.comparing(nameProvider, collator))
                .toList();
    }

    /** Headless diagnostic used by the menu-order regression test. */
    List<String> diagnosticSortedDisplayNames() {
        return sortedOptions().stream().map(nameProvider).toList();
    }

    /** Headless diagnostic used to verify the selection callback contract. */
    void diagnosticSelect(T value) {
        setSelectedOption(value);
        selectionListener.accept(value);
    }

    private final class LanguageRenderer extends JPanel
            implements javax.swing.ListCellRenderer<T> {
        private final JLabel flag = new JLabel("", SwingConstants.CENTER);
        private final JLabel name = new JLabel();

        private LanguageRenderer() {
            super(new BorderLayout(10, 0));
            setBorder(BorderFactory.createEmptyBorder(4, 7, 4, 9));
            flag.setPreferredSize(new Dimension(40, 30));
            flag.setFont(flag.getFont().deriveFont(24f));
            name.setFont(name.getFont().deriveFont(13f));
            add(flag, BorderLayout.WEST);
            add(name, BorderLayout.CENTER);
            setComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT);
        }

        @Override public Component getListCellRendererComponent(JList<? extends T> list,
                                                                 T value,
                                                                 int index,
                                                                 boolean selected,
                                                                 boolean focus) {
            flag.setText(flagProvider.apply(value));
            name.setText(nameProvider.apply(value));
            Color background = selected ? list.getSelectionBackground() : list.getBackground();
            Color foreground = selected ? list.getSelectionForeground() : list.getForeground();
            setBackground(background);
            flag.setForeground(foreground);
            name.setForeground(foreground);
            return this;
        }
    }
}
