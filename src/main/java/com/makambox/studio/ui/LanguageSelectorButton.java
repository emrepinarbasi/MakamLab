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
 * Compact language-code button backed by a scrollable, alphabetically sorted language
 * popup.  The class deliberately depends only on adapter functions, keeping it
 * reusable by the application's i18n service without coupling the UI to the
 * language catalogue implementation.
 */
public final class LanguageSelectorButton<T> extends AutoFitButton {
    private final Function<T, String> codeProvider;
    private final Function<T, String> nameProvider;
    private final List<T> options = new ArrayList<>();
    private Consumer<T> selectionListener = ignored -> {};
    private Supplier<Locale> sortLocaleProvider = Locale::getDefault;
    private T selectedOption;
    private String selectorLabel = "Select language";
    private String selectedLanguagePrefix = "Language";

    public LanguageSelectorButton(Function<T, String> codeProvider,
                                  Function<T, String> nameProvider) {
        super("—");
        this.codeProvider = Objects.requireNonNull(codeProvider, "codeProvider");
        this.nameProvider = Objects.requireNonNull(nameProvider, "nameProvider");
        setName("makam-language-selector");
        styleUtilityButton(this);
        setIconTextGap(7);
        setHorizontalAlignment(SwingConstants.CENTER);
        setFont(getFont().deriveFont(Font.BOLD, 15f));
        setMinimumAutoFitFontSize(12f);
        setMargin(new java.awt.Insets(2, 8, 2, 8));
        setPreferredSize(new Dimension(80, 46));
        setMinimumSize(new Dimension(72, 42));
        getAccessibleContext().setAccessibleName(selectorLabel);
        addActionListener(event -> showLanguagePopup());
    }

    static void styleUtilityButton(javax.swing.JButton button) {
        button.setUI(new javax.swing.plaf.basic.BasicButtonUI() {
            @Override public void paint(java.awt.Graphics graphics, javax.swing.JComponent component) {
                var g = (java.awt.Graphics2D) graphics.create();
                try {
                    g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                            java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                    var shape = new java.awt.geom.RoundRectangle2D.Double(
                            .5, .5, component.getWidth() - 1, component.getHeight() - 1, 13, 13);
                    g.setColor(button.getModel().isPressed() ? new Color(219, 232, 240)
                            : button.getModel().isRollover() ? new Color(235, 243, 248) : Color.WHITE);
                    g.fill(shape);
                    g.setColor(button.hasFocus() ? new Color(25, 107, 142) : new Color(205, 216, 224));
                    g.draw(shape);
                } finally {
                    g.dispose();
                }
                super.paint(graphics, component);
            }
        });
        button.setOpaque(false);
        button.setContentAreaFilled(false);
        button.setRolloverEnabled(true);
        button.setBorder(BorderFactory.createEmptyBorder(3, 7, 3, 7));
        button.setForeground(new Color(31, 49, 65));
    }

    public void setOptions(Collection<? extends T> values, T selected) {
        options.clear();
        options.addAll(values);
        setSelectedOption(selected);
    }

    public void setSelectedOption(T selected) {
        selectedOption = selected;
        if (selected == null) {
            setText("—");
            setIcon(null);
            setToolTipText(selectorLabel);
            getAccessibleContext().setAccessibleName(selectorLabel);
            return;
        }
        String code = codeProvider.apply(selected);
        setText(code);
        setIcon(LanguageFlagIcon.forCode(code));
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
        list.setComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT);
        if (selectedOption != null) list.setSelectedValue(selectedOption, true);

        JPopupMenu popup = new JPopupMenu();
        popup.setName("makam-language-popup");
        popup.setLayout(new BorderLayout());
        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setPreferredSize(new Dimension(210, Math.max(40, Math.min(sorted.size(), 8) * list.getFixedCellHeight())));
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
        private final JLabel code = new JLabel("", SwingConstants.CENTER);
        private final JLabel name = new JLabel();

        private LanguageRenderer() {
            super(new BorderLayout(10, 0));
            setBorder(BorderFactory.createEmptyBorder(4, 7, 4, 9));
            code.setPreferredSize(new Dimension(40, 30));
            code.setFont(code.getFont().deriveFont(Font.BOLD, 13f));
            name.setFont(name.getFont().deriveFont(13f));
            add(code, BorderLayout.WEST);
            add(name, BorderLayout.CENTER);
            setComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT);
        }

        @Override public Component getListCellRendererComponent(JList<? extends T> list,
                                                                 T value,
                                                                 int index,
                                                                 boolean selected,
                                                                 boolean focus) {
            String languageCode = codeProvider.apply(value);
            code.setIcon(LanguageFlagIcon.forCode(languageCode));
            code.setText(code.getIcon() == null ? languageCode : "");
            name.setText(nameProvider.apply(value));
            Color background = selected ? list.getSelectionBackground() : list.getBackground();
            Color foreground = selected ? list.getSelectionForeground() : list.getForeground();
            setBackground(background);
            code.setForeground(foreground);
            name.setForeground(foreground);
            return this;
        }
    }
}
