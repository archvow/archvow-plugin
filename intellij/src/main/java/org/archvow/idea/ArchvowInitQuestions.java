package org.archvow.idea;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.UIManager;

/**
 * Native Swing init wizard. Data comes from archvow catalog --json, not a hardcoded
 * template. It only emits documented -a KEY=VALUE overrides. The template is chosen here, once (0.12.1:
 * the form's --template field is gone, it was a second place): a template of the catalog, or the folder
 * of a company template. In a project, its own template comes first (init --force there).
 */
final class ArchvowInitQuestions extends JPanel {
    private final JComboBox<String> templatePicker = new JComboBox<>();
    private final JPanel questions = new JPanel(new GridBagLayout());
    private final JLabel status = new JLabel();
    private final JCheckBox expanded = new JCheckBox();
    private final Map<String, JComponent> inputs = new LinkedHashMap<>();
    private final Map<String, String> retained = new LinkedHashMap<>();
    private final List<JsonObject> catalog = new ArrayList<>();
    private JsonObject active;
    private Consumer<String> selectedTemplate;
    private boolean changing;
    private final boolean french;
    private final JTextField folder = new JTextField(22);
    private String preferred = "";

    ArchvowInitQuestions(boolean french, Runnable reloadCatalog, Consumer<String> selectedTemplate) {
        this(french, reloadCatalog, selectedTemplate, "");
    }

    /** {@code projectTemplate}: the template of the project the form is in ('' outside a project). */
    ArchvowInitQuestions(boolean french, Runnable reloadCatalog, Consumer<String> selectedTemplate, String projectTemplate) {
        super(new BorderLayout(0, 8));
        this.french = french;
        this.selectedTemplate = selectedTemplate;
        this.preferred = projectTemplate == null ? "" : projectTemplate;
        setBorder(BorderFactory.createCompoundBorder(BorderFactory.createTitledBorder(
                french ? "Modèle et ses questions" : "Template and its questions"),
                BorderFactory.createEmptyBorder(7, 8, 7, 8)));
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        toolbar.add(new JLabel(french ? "Modèle" : "Template"));
        templatePicker.setPreferredSize(new Dimension(225, 28));
        templatePicker.addActionListener(e -> {
            if (changing) return;
            String name = (String) templatePicker.getSelectedItem();
            if (name == null) return;
            folder.setText("");
            if (selectedTemplate != null) selectedTemplate.accept(name);
            showTemplate(name);
        });
        toolbar.add(templatePicker);
        JButton reload = new JButton(french ? "Actualiser" : "Reload");
        reload.addActionListener(e -> reloadCatalog.run());
        toolbar.add(reload);
        expanded.setText(french ? "Afficher les réglages avancés" : "Show advanced settings");
        expanded.addActionListener(e -> rebuild());
        toolbar.add(expanded);
        JPanel head = new JPanel();
        head.setLayout(new BoxLayout(head, BoxLayout.Y_AXIS));
        toolbar.setAlignmentX(LEFT_ALIGNMENT);
        status.setAlignmentX(LEFT_ALIGNMENT);
        status.setForeground(UIManager.getColor("Label.disabledForeground"));
        head.add(toolbar);
        // or a company template's folder: its questions come from archvow's catalog of that folder
        JPanel custom = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        custom.setAlignmentX(LEFT_ALIGNMENT);
        custom.add(new JLabel(french ? "ou le dossier d'un modèle d'entreprise :" : "or the folder of a company template:"));
        folder.setToolTipText("/path/to/templates/acme-pipeline");
        Runnable useFolder = () -> {
            String path = folder.getText().trim();
            if (path.isEmpty() || selectedTemplate == null) return;
            selectedTemplate.accept(path);
            reloadCatalog.run();
        };
        folder.addActionListener(e -> useFolder.run());
        folder.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override public void focusLost(java.awt.event.FocusEvent e) { useFolder.run(); }
        });
        custom.add(folder);
        head.add(custom);
        if (!preferred.isBlank()) {
            ArchvowWrapLabel here = new ArchvowWrapLabel();
            here.setText(french
                ? "Ce dossier est déjà un projet archvow (modèle " + preferred + ") : init demande --force, qui réécrit les fichiers "
                    + "générés (AGENTS.md, CI, hooks...) et garde la spec commitée."
                : "This folder is already an archvow project (template " + preferred + "): init needs --force, which rewrites the "
                    + "generated files (AGENTS.md, CI, hooks...) and keeps the committed spec.");
            here.setOpaque(false);
            here.setAlignmentX(LEFT_ALIGNMENT);
            head.add(here);
        }
        head.add(status);
        add(head, BorderLayout.NORTH);
        add(questions, BorderLayout.CENTER);
        setLoading();
    }

    void setLoading() {
        status.setText(french ? "Chargement depuis archvow catalog --json…" : "Loading archvow catalog --json…");
    }

    void setError(String message) {
        status.setText((french ? "Catalogue indisponible. Saisis -a KEY=VALUE manuellement. "
            : "Catalog unavailable. Enter -a KEY=VALUE manually. ") + (message == null ? "" : singleLine(message, 130)));
    }

    void setCatalog(JsonObject data, String requested) {
        // Do not discard a person's answers when a live catalog replaces the
        // immediately available bundled reference (or when Reload is clicked).
        // The existing field values are applied only to matching question ids.
        Map<String, String> previous = readInputs();
        String previousName = active == null ? "" : string(active, "name");
        catalog.clear();
        retained.clear();
        JsonArray templates = data != null && data.has("templates") && data.get("templates").isJsonArray()
            ? data.getAsJsonArray("templates") : new JsonArray();
        for (JsonElement el : templates) if (el.isJsonObject()) catalog.add(el.getAsJsonObject());
        changing = true;
        templatePicker.removeAllItems();
        for (JsonObject template : catalog) templatePicker.addItem(string(template, "name"));
        String match = matchedTemplate(requested);
        boolean manualPath = requested != null && !requested.isBlank() && match == null;
        if (match == null && !manualPath && (requested == null || requested.isBlank())) {
            match = matchedTemplate(preferred);   // in a project: its own template
            if (match == null) match = matchedTemplate("python-app"); // archvow's default template
        }
        if (manualPath && !requested.equals(folder.getText().trim())) folder.setText(requested);
        if (match != null) templatePicker.setSelectedItem(match);
        else if (!catalog.isEmpty()) templatePicker.setSelectedIndex(0);
        changing = false;
        String chosen = manualPath ? null : (String) templatePicker.getSelectedItem();
        if (chosen != null && chosen.equals(previousName)) retained.putAll(previous);
        if (!manualPath && (requested == null || requested.isBlank()) && chosen != null && selectedTemplate != null)
            selectedTemplate.accept(chosen);
        status.setText(catalog.size() + (french ? " modèles disponibles. Les valeurs sont modifiables avant init."
            : " templates found. Review their defaults before init."));
        showTemplate(chosen);
        // Do not silently replace a custom absolute template path, or a deliberately
        // blank field, with the first catalog template. The picker is user-operated.
        if (manualPath)
            status.setText(french ? "Modèle personnalisé : conserve le chemin saisi ; les questions ne sont pas connues."
                : "Custom template path: retaining your input; its questions are not in this catalog.");
    }

    void setCatalogSource(String message) {
        status.setText(message);
    }

    private String matchedTemplate(String requested) {
        if (requested == null || requested.isBlank()) return null;
        for (JsonObject t : catalog) {
            String name = string(t, "name");
            String source = string(t, "source");
            if (requested.equals(name) || requested.equals(source) || requested.endsWith("/" + name)) return name;
        }
        return null;
    }

    private void showTemplate(String name) {
        if (active != null && !string(active, "name").equals(name)) {
            retained.clear();
            inputs.clear();
        }
        active = null;
        for (JsonObject t : catalog) if (string(t, "name").equals(name)) { active = t; break; }
        rebuild();
    }

    private void rebuild() {
        retained.putAll(readInputs());
        inputs.clear();
        questions.removeAll();
        if (active == null) { questions.revalidate(); questions.repaint(); return; }
        JsonArray list = active.has("questions") && active.get("questions").isJsonArray()
            ? active.getAsJsonArray("questions") : new JsonArray();
        int y = 0;
        for (JsonElement el : list) {
            if (!el.isJsonObject()) continue;
            JsonObject question = el.getAsJsonObject();
            String id = string(question, "id");
            if (id.isBlank()) continue;
            boolean essential = question.has("ask") && question.get("ask").getAsBoolean();
            if (!essential && !expanded.isSelected()) continue;
            String prompt = string(question, "prompt");
            String def = retained.getOrDefault(id, string(question, "default"));
            List<String> choices = new ArrayList<>();
            if (question.has("choices") && question.get("choices").isJsonArray()) {
                for (JsonElement choice : question.getAsJsonArray("choices"))
                    if (choice.isJsonPrimitive()) choices.add(choice.getAsString());
            }
            JLabel label = new JLabel(id + (essential ? "  •" : ""));
            label.setToolTipText(prompt);
            GridBagConstraints left = new GridBagConstraints();
            left.gridx = 0; left.gridy = y; left.anchor = GridBagConstraints.NORTHWEST;
            left.insets = new Insets(8, 5, 4, 12);
            questions.add(label, left);
            JPanel block = new JPanel(new BorderLayout(0, 3));
            JComponent field;
            boolean multi = question.has("multiple") && question.get("multiple").getAsBoolean();
            if (multi && !choices.isEmpty()) {
                JPanel chips = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
                LinkedHashSet<String> defaults = new LinkedHashSet<>(List.of(def.split(",")));
                for (String choice : choices) {
                    JCheckBox chip = new JCheckBox(choice, defaults.contains(choice));
                    chip.setBorder(BorderFactory.createEmptyBorder(3, 5, 3, 5));
                    chips.add(chip);
                }
                field = chips;
            } else if (!choices.isEmpty()) {
                JComboBox<String> combo = new JComboBox<>();
                for (String choice : choices) combo.addItem(choice);
                if (!def.isBlank() && choices.contains(def)) combo.setSelectedItem(def);
                field = combo;
            } else {
                JTextField free = new JTextField(def, 28);
                field = free;
            }
            inputs.put(id, field);
            block.add(field, BorderLayout.NORTH);
            ArchvowWrapLabel help = new ArchvowWrapLabel();   // wraps at the form's width, no caret in it
            help.setText(prompt);
            help.setOpaque(false);
            help.setFont(help.getFont().deriveFont(11f));
            help.setForeground(UIManager.getColor("Label.disabledForeground"));
            block.add(help, BorderLayout.CENTER);
            GridBagConstraints right = new GridBagConstraints();
            right.gridx = 1; right.gridy = y++; right.weightx = 1;
            right.anchor = GridBagConstraints.NORTHWEST;
            right.fill = GridBagConstraints.HORIZONTAL;
            right.insets = new Insets(5, 0, 3, 5);
            questions.add(block, right);
        }
        GridBagConstraints filler = new GridBagConstraints();
        filler.gridx = 1; filler.gridy = y; filler.weighty = 1; filler.fill = GridBagConstraints.VERTICAL;
        questions.add(new JPanel(), filler);
        questions.revalidate(); questions.repaint();
    }

    /** Return actual Archvow -a KEY=VALUE arguments, not YAML or fabricated commands. */
    List<String> answers() {
        Map<String, String> values = new LinkedHashMap<>(retained);
        values.putAll(readInputs());
        List<String> answers = new ArrayList<>();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (!entry.getValue().isBlank()) answers.add(entry.getKey() + "=" + entry.getValue());
        }
        return answers;
    }

    private Map<String, String> readInputs() {
        Map<String, String> values = new LinkedHashMap<>();
        for (Map.Entry<String, JComponent> entry : inputs.entrySet()) {
            JComponent field = entry.getValue();
            String value;
            if (field instanceof JComboBox<?> combo) {
                value = combo.getSelectedItem() == null ? "" : combo.getSelectedItem().toString();
            } else if (field instanceof JTextField free) {
                value = free.getText().trim();
            } else if (field instanceof JPanel chips) {
                List<String> picked = new ArrayList<>();
                for (java.awt.Component component : chips.getComponents())
                    if (component instanceof JCheckBox cb && cb.isSelected()) picked.add(cb.getText());
                if (picked.contains("none")) picked = List.of("none");
                else if (picked.contains("all")) picked = List.of("all");
                value = String.join(",", picked);
            } else continue;
            values.put(entry.getKey(), value);
        }
        return values;
    }

    private static String string(JsonObject obj, String name) {
        return obj != null && obj.has(name) && !obj.get(name).isJsonNull() ?
            obj.get(name).getAsString() : "";
    }
    private static String singleLine(String s, int n) {
        String flat = s.replace('\n', ' ').trim();
        return flat.length() > n ? flat.substring(0, n) + "…" : flat;
    }
}
