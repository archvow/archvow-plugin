package org.archvow.idea;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Session-only CLI execution log, per project (any object identifies it: the IntelliJ project). No command
 * arguments or captured project content.
 */
final class ArchvowRunLog {
    private static final int MAX_EVENTS = 120;
    private static final Map<Object, ArrayDeque<String>> EVENTS = Collections.synchronizedMap(new WeakHashMap<>());
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
    }

    static String snapshot(Object project, boolean fr) {
        synchronized (EVENTS) {
            ArrayDeque<String> queue = EVENTS.get(project);
            if (queue == null || queue.isEmpty()) return fr ? "Aucune commande exécutée pendant cette session." : "No commands executed in this session.";
            return String.join("\n", queue);
        }
    }

    static void show(Component parent, Object project, boolean fr) {
        JTextArea log = new JTextArea(snapshot(project, fr), 18, 66);
        log.setEditable(false);
        log.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        log.setCaretPosition(0);
        JScrollPane scroll = new JScrollPane(log);
        JPanel content = new JPanel(new BorderLayout(0, 8));
        content.add(new JLabel(fr ? "Journal de la session Archvow · aucune donnée de spécification enregistrée"
                : "Archvow session log · specification content is not recorded"), BorderLayout.NORTH);
        content.add(scroll, BorderLayout.CENTER);
        Object[] choices = {fr ? "Fermer" : "Close", fr ? "Effacer" : "Clear"};
        int choice = JOptionPane.showOptionDialog(parent, content, fr ? "Journal Archvow" : "Archvow log",
            JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, choices, choices[0]);
        if (choice == 1) {
            synchronized (EVENTS) { EVENTS.remove(project); }
        }
    }
}
