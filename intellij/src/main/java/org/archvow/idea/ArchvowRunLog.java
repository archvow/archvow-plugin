package org.archvow.idea;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;

import javax.swing.*;
import java.awt.*;
import java.lang.ref.WeakReference;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Session-only CLI execution log, per project. No command arguments or captured project content.
 * The bottom Archvow Output tool window displays these same events without a modal dialog.
 */
final class ArchvowRunLog {
    private static final int MAX_EVENTS = 120;
    private static final String WINDOW_ID = "Archvow Output";
    private static final Map<Object, ArrayDeque<String>> EVENTS = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Object, WeakReference<LogView>> VIEWS = Collections.synchronizedMap(new WeakHashMap<>());
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private ArchvowRunLog() { }

    static void record(Object project, String commandLabel, int exit, boolean cancelled, long durationMillis) {
        String safeName = commandLabel == null ? "Archvow" : commandLabel.replaceAll("[\r\n\t]", " ");
        if (safeName.length() > 70) safeName = safeName.substring(0, 70) + "…";
        String event = LocalDateTime.now().format(TIME) + "  " + safeName + "  ·  "
            + (cancelled ? "cancelled" : "exit " + exit) + "  ·  " + durationMillis + " ms";
        synchronized (EVENTS) {
            ArrayDeque<String> queue = EVENTS.computeIfAbsent(project, unused -> new ArrayDeque<>());
            if (queue.size() == MAX_EVENTS) queue.removeFirst();
            queue.addLast(event);
        }
        // A hidden or unopened output window does not need to be brought to the foreground.
        // Refresh the already-created view on the Swing thread when new CLI events arrive.
        WeakReference<LogView> reference;
        synchronized (VIEWS) { reference = VIEWS.get(project); }
        if (reference != null) SwingUtilities.invokeLater(() -> {
            LogView view = reference.get();
            if (view != null) view.refresh();
        });
    }

    static String snapshot(Object project, boolean fr) {
        synchronized (EVENTS) {
            ArrayDeque<String> queue = EVENTS.get(project);
            if (queue == null || queue.isEmpty()) return fr ? "Aucune commande exécutée pendant cette session." : "No commands executed in this session.";
            return String.join("\n", queue);
        }
    }

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
        private final JButton clear = new JButton();
        private final JTextArea log = new JTextArea();
        private final JScrollPane scroll = new JScrollPane(log);

        LogView(Project project) {
            super(new BorderLayout(0, 8));
            this.project = project;
            setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
            JPanel header = new JPanel(new BorderLayout(8, 0));
            header.add(note, BorderLayout.CENTER);
            clear.addActionListener(e -> {
                synchronized (EVENTS) { EVENTS.remove(project); }
                refresh();
            });
            header.add(clear, BorderLayout.EAST);
            add(header, BorderLayout.NORTH);
            log.setEditable(false);
            log.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
            log.getAccessibleContext().setAccessibleName("Archvow session log");
            add(scroll, BorderLayout.CENTER);
            refresh();
        }

        void refresh() {
            boolean fr = ArchvowI18n.isFrench(project);
            note.setText(fr ? "Journal de la session Archvow · aucune donnée de spécification enregistrée"
                : "Archvow session log · specification content is not recorded");
            clear.setText(fr ? "Effacer" : "Clear");
            String text = snapshot(project, fr);
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
