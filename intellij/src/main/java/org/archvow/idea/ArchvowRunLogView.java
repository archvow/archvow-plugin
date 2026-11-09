package org.archvow.idea;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The bottom "Archvow Output" tool window: the session log of {@link ArchvowRunLog}, non-modal, refreshed as
 * archvow calls end. Clear empties it; "Copy for support" copies the session header and the lines, a person's
 * answers and rationales hidden when the box is ticked (0.19.0).
 */
final class ArchvowRunLogView {
    private static final String WINDOW_ID = "Archvow Output";
    private static final Map<Object, WeakReference<LogView>> VIEWS = Collections.synchronizedMap(new WeakHashMap<>());

    static {
        // A hidden or unopened output window is not brought to the foreground: an existing view refreshes
        // on the Swing thread when its project's lines change.
        ArchvowRunLog.onChange(project -> {
            WeakReference<LogView> reference;
            synchronized (VIEWS) { reference = VIEWS.get(project); }
            if (reference != null) SwingUtilities.invokeLater(() -> {
                LogView view = reference.get();
                if (view != null) view.refresh();
            });
        });
    }

    private ArchvowRunLogView() { }

    static void show(Project project) {
        if (project.isDisposed()) return;
        ToolWindow window = ToolWindowManager.getInstance(project).getToolWindow(WINDOW_ID);
        if (window == null) return;
        window.activate(() -> {
            WeakReference<LogView> reference;
            synchronized (VIEWS) { reference = VIEWS.get(project); }
            LogView view = reference == null ? null : reference.get();
            if (view != null) view.refresh();
        });
    }

    /** Called only when IntelliJ creates the bottom tool window content. */
    static JComponent createView(Project project) {
        LogView view = new LogView(project);
        synchronized (VIEWS) { VIEWS.put(project, new WeakReference<>(view)); }
        return view;
    }

    private static final class LogView extends JPanel {
        private final Project project;
        private final JLabel note = new JLabel();
        private final JCheckBox hide = new JCheckBox("", true);
        private final JButton copy = new JButton();
        private final JButton clear = new JButton();
        private final JTextArea log = new JTextArea();
        private final JScrollPane scroll = new JScrollPane(log);

        LogView(Project project) {
            super(new BorderLayout(0, 8));
            this.project = project;
            setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
            JPanel header = new JPanel(new BorderLayout(8, 0));
            header.add(note, BorderLayout.CENTER);
            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
            buttons.add(hide);
            buttons.add(copy);
            buttons.add(clear);
            copy.addActionListener(e -> Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(ArchvowRunLog.supportText(project, hide.isSelected())), null));
            clear.addActionListener(e -> ArchvowRunLog.clear(project));
            header.add(buttons, BorderLayout.EAST);
            add(header, BorderLayout.NORTH);
            log.setEditable(false);
            log.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
            log.getAccessibleContext().setAccessibleName("Archvow session log");
            add(scroll, BorderLayout.CENTER);
            refresh();
        }

        void refresh() {
            boolean fr = ArchvowI18n.isFrench(project);
            note.setText(fr ? "Journal de la session Archvow · ▶ tes actions, ✖ les échecs, une ligne par rafraîchissement (le détail : idea.log)"
                : "Archvow session log · ▶ your actions, ✖ failures, one line per refresh (the detail: idea.log)");
            hide.setText(fr ? "masquer mes réponses" : "hide my answers");
            copy.setText(fr ? "Copier pour le support" : "Copy for support");
            clear.setText(fr ? "Effacer" : "Clear");
            String text = ArchvowRunLog.snapshot(project, fr);
            if (text.equals(log.getText())) return;
            JScrollBar bar = scroll.getVerticalScrollBar();
            boolean follow = log.getDocument().getLength() == 0
                || bar.getValue() + bar.getVisibleAmount() >= bar.getMaximum() - 16;
            int previousScroll = bar.getValue();
            log.setText(text);
            if (follow) log.setCaretPosition(text.length());
            else SwingUtilities.invokeLater(() -> bar.setValue(previousScroll));
        }
    }
}
