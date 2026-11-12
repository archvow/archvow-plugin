package org.archvow.idea;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public final class UiPolishRegression {
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    private static void checkIconPaint(boolean dark) {
        UIManager.put("Panel.background", dark ? new Color(30, 31, 34) : new Color(247, 248, 250));
        JButton button = new JButton(dark ? "Enregistrer la réponse" : "Save answer");
        button.setForeground(dark ? Color.WHITE : new Color(32, 44, 63));
        ArchvowUiIcons.decorate(button);
        require(button.getIcon() != null, "localized button must retain a small icon");
        require("check".equals(button.getClientProperty("archvow.icon.kind")), "semantic icon must be consistent across languages");
        require(button.getIcon().getIconWidth() == 14, "icons should not enlarge action buttons");
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        button.getIcon().paintIcon(button, g, 1, 1);
        g.dispose();
        int painted = 0;
        for (int yy=0; yy<16; yy++) for (int xx=0; xx<16; xx++) if ((image.getRGB(xx,yy) >>> 24) != 0) painted++;
        require(painted > 10, "icon must paint visible vector strokes");
        button.setText(dark ? "Accepter la proposition" : "Accept proposal");
        ArchvowUiIcons.decorate(button);
        require(button.getIcon() != null, "icons survive label changes");
        require(ArchvowUiIcons.kindForLabel("Vérifications") == null || true, "no exception with accents");
    }
    private static int[] overviewPixels(String kind) {
        JButton button = new JButton("");
        button.setForeground(Color.BLACK);
        ArchvowUiIcons.explicit(button, kind);
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        button.getIcon().paintIcon(button, g, 1, 1);
        g.dispose();
        return image.getRGB(0, 0, 16, 16, null, 0, 16);
    }

    public static void main(String[] args) throws Exception {
        String panel = Files.readString(Path.of("src/main/java/org/archvow/idea/ArchvowPanel.java"));
        // 0.25.0: the commands of More, each with its icon (ArchvowOverviewCards.toolbar)
        require(panel.contains("new ArchvowOverviewCards.MenuCommand(\"folder\", ArchvowI18n.text(project, \"Briefs\""),
            "Briefs must use the supported folder icon, not the fallback circle");
        require(panel.contains("new ArchvowOverviewCards.MenuCommand(\"spark\", \"AI review\""),
            "AI review must use the supported spark icon, not the fallback circle");
        require(!panel.contains("\"Project status\", \"État du projet\""), "no Project status strip: the cards say it");
        require(panel.contains("backButton.setText(\"← Briefs\")"),
            "AI skill editor needs a Back to Briefs button");
        SwingUtilities.invokeAndWait(() -> {
            require(!Arrays.equals(overviewPixels("folder"), overviewPixels("check")), "Briefs icon must differ from Check");
            require(!Arrays.equals(overviewPixels("spark"), overviewPixels("check")), "AI review icon must differ from Check");
            require(!Arrays.equals(overviewPixels("spark"), overviewPixels("folder")), "AI review icon must differ from Briefs");
            checkIconPaint(false);
            checkIconPaint(true);
            JButton check = new JButton("Check");
            JButton doctor = new JButton("Doctor");
            JPanel root = new JPanel();
            root.add(check); root.add(doctor);
            ArchvowUiIcons.decorateTree(root);
            require(check.getIcon() != null && doctor.getIcon() != null, "tree decorating should include command buttons");
            require(ArchvowUiIcons.kindForLabel("Parcourir…").equals("folder"), "folder selector icon");
            require(ArchvowUiIcons.kindForLabel("Ouvrir dans l'éditeur ↗").equals("layout"), "editor action icon");
            require(ArchvowUiIcons.kindForLabel("Ajouter un invariant").equals("plus"), "add action icon");
        });
        require("Écart détecté".equals(ArchvowArchitectureView.localizedState("drift", true)), "French translation of drift");
        require("Testé".equals(ArchvowArchitectureView.localizedState("tested", true)), "French translation of tested");
        require("Écart détecté".equals(ArchvowArchitectureView.localizedState("drifted", true)), "French translation of drifted");
        require("drift".equals(ArchvowArchitectureView.localizedState("drift", false)), "English technical state unchanged");
        System.out.println("OK: 12");
    }
}
