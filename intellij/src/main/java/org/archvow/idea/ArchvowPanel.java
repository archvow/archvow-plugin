package org.archvow.idea;

import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import com.google.gson.JsonArray;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.LocalFileSystem;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The archvow workspace, in one of two roles:
 * <ul><li>{@link Role#NAVIGATION}: the Archvow tool window (left): Overview, Components (the tree) and
 *     Commands (the list), to choose;</li>
 * <li>{@link Role#DETAIL}: the ONE editor tab: the chosen component, the chosen command's form, or a
 *     command's result. A click on another component shows it in the same tab.</li></ul>
 * Both build the same Swing parts; each shows its half. The navigation reads the project and hands what
 * archvow said to the editor tab ({@link #adoptFrom}), so the project is read once.
 */
final class ArchvowPanel extends JPanel {
    enum Role { NAVIGATION, DETAIL }

    private static final Set<String> LONG_TEXT = Set.of("description", "answer", "rationale", "statement", "question", "text", "words");
    private static final Set<String> DESTRUCTIVE = Set.of("component remove", "question remove", "rule remove", "hazard remove", "invariant remove",
            "example remove");
    private static final Set<String> READ_ONLY = Set.of("show", "catalog", "next", "check", "audit", "doctor", "changes", "diff", "prompt", "doc", "try");  // try: a scratch folder only (2.1)
    private static final Pattern QUOTED_COMPONENT = Pattern.compile("'([^']+)'");

    /** An AI skill in the Commands tree (0.16.0): opens its launch form. */
    private record SkillNode(String id, String label) implements ArchvowTreeIcons.Kinded {
        @Override public String iconKind() { return "other"; }
        @Override public String toString() { return label; }
    }

    private record NodeComponent(String id, String type, String layer, String container) implements ArchvowTreeIcons.Kinded {
        @Override public String toString() { return id + "  ·  " + type; }
        @Override public String iconKind() { return ArchvowTreeIcons.typeKind(type); }
    }
    private enum QuickKind { ERROR, WARNING, QUESTION, DECISION, SUGGESTION, RESOLVED }
    private static final class QuickItem {
        final QuickKind kind;
        final String title;
        final String detail;
        final String componentId;
        final boolean pending;
        final String actionLabel;
        final String commandLabel;
        final Map<String, String> preset;
        /** For a check diagnostic: what it means, what to do, who, whether it blocks (ArchvowGuidance). */
        final ArchvowGuidance.Guidance guide;

        QuickItem(QuickKind kind, String title, String detail, String componentId, boolean pending,
                  String actionLabel, String commandLabel, Map<String, String> preset) {
            this(kind, title, detail, componentId, pending, actionLabel, commandLabel, preset, null);
        }

        QuickItem(QuickKind kind, String title, String detail, String componentId, boolean pending,
                  String actionLabel, String commandLabel, Map<String, String> preset, ArchvowGuidance.Guidance guide) {
            this.guide = guide;
            this.kind = kind;
            this.title = title;
            this.detail = detail;
            this.componentId = componentId;
            this.pending = pending;
            this.actionLabel = actionLabel;
            this.commandLabel = commandLabel;
            this.preset = preset == null ? Map.of() : Map.copyOf(preset);
        }
    }

    private final Project project;
    private final ArchvowCli cli;
    // Catalog must load even while the project's show/check/next chain is busy.
    // A separate runner never needs a .archvow project or project --root.
    private final ArchvowCli catalogCli;
    private JsonObject liveInitCatalog;
    /** {@code catalog --json} in the project: its template's types and kinds, the project's own templates (0.12.1). */
    private JsonObject projectCatalog;
    /** The project's packs, as workspace --json gives them (archvow 1.26; Overview › Packs). */
    private com.google.gson.JsonArray projectPacks = new com.google.gson.JsonArray();
    /** The first steps of a new project, as workspace --json gives them (archvow 2.1; the Overview's card). */
    private com.google.gson.JsonArray firstStepsJson = new com.google.gson.JsonArray();
    /** The Overview's cards (0.25.0, ArchvowOverviewCards): rebuilt on each refresh. */
    private final JPanel firstStepsHolder = new JPanel(new BorderLayout());
    private final JPanel architectureHolder = new JPanel(new BorderLayout());
    private final JPanel checksHolder = new JPanel(new BorderLayout());
    /** Contract 2.0 (0.24.0): each feature, as show <feature> --json; the Components tab groups by feature or by layer. */
    private Map<String, JsonObject> projectFeatures = new LinkedHashMap<>();
    private boolean groupByFeature = true;
    private ArchvowFeatureView featureView;
    /** The Spec view's "Where it is" (0.24.1): the component's vow, code and test as a tree. */
    private final ArchvowFilePlanView componentFilesView = new ArchvowFilePlanView();
    private String shownFeature = "";
    /** archvow/kinds.json: the kinds of each type for an archvow whose catalog has none (1.17). */
    private JsonObject bundledKinds;
    /** The repository's branches and tags, the current branch, what origin's HEAD names (the comparison base). */
    private List<String> gitRefs = List.of();
    private Map<String, String> gitRefKinds = Map.of();
    /** The repository's first commit: changes --from it reports everything (0.15.0). */
    private String firstCommit = "";
    private String gitCurrent = "", remoteHead = "";
    /** The person chose the base: it no longer follows the best existing branch. */
    private boolean baseTouched;
    /** Edit spec: what the component has; only what differs from it is sent (0.12.1). */
    private Map<String, String> formBaseline = Map.of();
    /** Lines openCommand adds under the command's description (kept when the form is drawn again). */
    private String formHelpExtra = "";
    private ArchvowTemplateSettings templateSettings;
    private boolean buildingForm;
    private String liveInitCatalogScope = "";
    private final ArchvowNavigationTabs tabs = new ArchvowNavigationTabs();
    private final CardLayout componentViewLayout = new CardLayout();
    private JPanel componentViewCards;
    private final java.util.List<JToggleButton> componentViewButtons = new ArrayList<>();
    private ArchvowCompactViews componentViewNav;
    private final ArchvowArchitectureView overviewSummary = new ArchvowArchitectureView();
    private final ArchvowChecksView diagnostics = new ArchvowChecksView(false);
    private final ArchvowChecksView detailDiagnostics = new ArchvowChecksView();
    private final JEditorPane overviewResult = resultHtmlPane();
    private final JEditorPane commandResult = resultHtmlPane();
    private ArchvowInitQuestions initQuestions;

    private final JEditorPane componentResult = resultHtmlPane();
    private JPanel componentResultCard;
    private final JEditorPane quickResult = resultHtmlPane();
    private JPanel overviewResultCard;
    private JPanel commandResultCard;
    private JPanel quickResultCard;
    private JPanel detailHomeCards;
    private JPanel skillDetails;
    private String detailSkillLabel = "";
    private final CardLayout detailHomeLayout = new CardLayout();
    private String detailHomeMode = "result";
    /** A Draft from Briefs returns to the same editor's Briefs browser. */
    private boolean skillBackToBriefs;
    /** 0.18.0: the search (archvow show <name> --json) in the editor tab, the name searched, the tool window's
     *  find field, and the component's Examples view (view 8, shown right after Rules). */
    private ArchvowSearchView searchView;
    private String searchName = "";
    private final JTextField findField = new JTextField();
    private final JButton[] filterButtonAnchor = new JButton[1];
    private ArchvowExamplesView examplesView;
    private JPanel quickSelectionArea;
    private final CardLayout rootLayout = new CardLayout();
    private final JPanel rootCards = new JPanel(rootLayout);
    private ArchvowResultExpansion resultExpansion;
    private JToggleButton expandedResultButton;
    private final JEditorPane welcomeText = htmlPane();
    private JButton welcomeInit;
    /** Install archvow, guided (0.12.0): shown when archvow does not start or does not speak the plugin's contract. */
    private JButton welcomeInstall;
    private final JLabel welcomeInstallCommand = new JLabel();
    private final ArchvowConfirmPanel welcomeConfirmation = new ArchvowConfirmPanel();
    /** 0.25.0: a folder without archvow offers to try it on an example first; its result shows under it. */
    private JPanel welcomeTry;
    private JComponent welcomeAdopt, welcomeFoot;
    private final JEditorPane welcomeResult = htmlPane();
    /** In archvow try's form (0.25.1): where the example will be copied, as the fields say. */
    private final JLabel tryWhere = new JLabel();
    private JScrollPane welcomeResultScroll;
    /** "missing", "version" or "" (archvow speaks the plugin's contract). */
    private String installReason = "";
    private boolean installing;
    private ArchvowProjectReadiness.Status projectStatus;
    private final JEditorPane componentDetails = htmlPane();
    /** The component's actions (Clarify, Open spec, Open code...): hidden on the Other view. */
    private JComponent componentActions;
    {
        // "Uses ...  ·  Used by ..." under the component's name: each name opens that component (ArchvowLinks)
        componentDetails.addHyperlinkListener(event -> {
            if (event.getEventType() != HyperlinkEvent.EventType.ACTIVATED) return;
            String target = ArchvowLinks.component(event.getDescription());
            if (!target.isBlank()) showComponent(target);
            String brief = ArchvowBriefs.briefOf(event.getDescription());
            if (!brief.isBlank()) openBrief(brief);
        });
        // under a result where archvow names the init --force that adds a briefs folder: open that form, prefilled
        for (JEditorPane result : List.of(overviewResult, commandResult, componentResult, quickResult, welcomeResult)) {
            ArchvowHtmlFileIcons.install(result);
            result.addHyperlinkListener(event -> {
                if (event.getEventType() != HyperlinkEvent.EventType.ACTIVATED) return;
                // under archvow try's result (0.25.1): the copy's spec, only where archvow wrote one
                String tried = ArchvowResultFormatter.tryFolder(event.getDescription());
                if (!tried.isBlank()) {
                    openTryCopy(tried);  // a method: this initializer runs before the constructor sets `project`
                    return;
                }
                ArchvowBriefs.AddFolder add = ArchvowBriefs.initOf(event.getDescription());
                if (add != null) openCommand("init", add.values());
                // Overview › Packs: Update / Remove / Check / Add a pack / New pack / See the change open their form
                Map.Entry<String, Map<String, String>> pack = ArchvowPacks.command(event.getDescription());
                if (pack != null) openCommand(pack.getKey(), pack.getValue());
            });
        }
    }
    private final JTextPane decisionDetails = ArchvowDecisionView.create();
    private final JTextPane safetyDetails = ArchvowSafetyView.create();
    private final JEditorPane questionDetails = htmlPane();
    private final JTextArea questionAnswer = new JTextArea(3, 30);
    private final JEditorPane questionInlineResult = resultHtmlPane();
    private JPanel questionInlineResultCard;
    private final DefaultListModel<ArchvowItemCards.Card> questionModel = new DefaultListModel<>();
    private final JList<ArchvowItemCards.Card> questionList = new JList<>(questionModel);
    private final JTextArea decisionAnswer = new JTextArea(3, 30);
    private final JTextArea safetyInput = new JTextArea(3, 30);
    private final JComboBox<String> safetySeverity = new JComboBox<>(new String[] {"high", "critical", "medium", "low"});
    private final JButton decideInline = ArchvowSplitView.primary("");
    private final JButton proposeInline = new JButton();  // Save as a proposal (decision propose), beside Save answer
    private final JButton acceptInline = ArchvowSplitView.link("");
    private final JButton suggestedInline = ArchvowSplitView.link("");
    private final JButton hazardInline = ArchvowSplitView.primary("");
    private final JButton dismissInline = new JButton();
    private final JLabel safetyInputLabel = new JLabel();
    private final JEditorPane decisionInlineResult = resultHtmlPane();
    private final JEditorPane safetyInlineResult = resultHtmlPane();
    private JPanel decisionInlineResultCard, safetyInlineResultCard;
    private final JEditorPane commandQuestionPreview = htmlPane();
    private final JEditorPane assumptionDetails = htmlPane();
    private final DefaultListModel<ArchvowItemCards.Card> assumptionModel = new DefaultListModel<>();
    private final JList<ArchvowItemCards.Card> assumptionList = new JList<>(assumptionModel);
    private final JButton assumptionAcceptButton = ArchvowSplitView.primary("");
    private final JButton assumptionRejectButton = new JButton();
    private final DefaultListModel<ArchvowDataView.Item> dataActionModel = new DefaultListModel<>();
    private final JList<ArchvowDataView.Item> dataActionList = new JList<>(dataActionModel);
    private final JButton dataActionButton = new JButton();
    private final JTextArea dataSelectedDetails = new JTextArea(4, 30);
    private final JButton dataEditDescription = new JButton();
    private final JEditorPane dataInlineResult = resultHtmlPane();
    private JPanel dataInlineResultCard;
    private JPanel dataActionsPanel;
    private final JButton safetySuggestedButton = new JButton();
    private final JButton safetyInvariantButton = new JButton();
    private final JButton changeHazardButton = new JButton();
    private final JButton keepHazardButton = new JButton();
    private final JButton removeHazardButton = new JButton();
    private final JButton removeInvariantButton = new JButton();
    private final JPanel safetySeverityBox = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
    /** What the Safety editor does now: "hazard" (declare), "change" (a recorded hazard), "invariant" (the one a hazard needs), "" (nothing). */
    private String safetyMode = "";
    /** The hazard being changed (its id), while the person changes it; null otherwise. */
    private String changingHazard;
    /** archvow 1.18 gives a safety question's suggestion only while it is open: kept for its invariant (component:number). */
    private final Map<String, String> rememberedInvariants = new java.util.HashMap<>();
    private final JTextPane rulesOnlyDetails = ArchvowDataView.create();
    private final JTextPane rulesDetails = ArchvowDataView.create();
    private final JTextPane vowDetails = ArchvowVowViewer.create();
    /** The Spec view: the task the AI implements (archvow prompt) over the vow it is written from, both read only. */
    private final JTextArea cardDetails = new JTextArea();
    /** archvow 1.19: the answers a question without a safe default offers to start from, one button each. */
    private final JPanel decisionChoices = new JPanel(new ArchvowWrapLayout(FlowLayout.LEFT, 4, 4));
    private final ArchvowColumnAnswerPanel columnAnswerPanel = new ArchvowColumnAnswerPanel(decisionAnswer);
    private final JPanel specCards = new JPanel(new CardLayout());
    private ArchvowSegmented specPart;
    private final DefaultListModel<ArchvowItemCards.Card> decisionsModel = new DefaultListModel<>();
    private final JList<ArchvowItemCards.Card> decisionsList = new JList<>(decisionsModel);
    private final DefaultListModel<ArchvowItemCards.Card> safetyModel = new DefaultListModel<>();
    private final JList<ArchvowItemCards.Card> safetyList = new JList<>(safetyModel);
    /** The component the views were last drawn for: another one starts each view on its first open item. */
    private String renderedComponent = "";
    // 0.16.0: where each item came from and what the AI added that waits for a person (ArchvowAiSkills), the cards
    // shown (all, open, to review), the next skill, and the briefs section by section (archvow 1.20 brief show)
    private ArchvowAiSkills.View aiView = ArchvowAiSkills.view(null, List.of());
    private String originFilter = "all";
    private JsonObject briefStatus;
    private final JPanel suggestStrip = new JPanel(new BorderLayout(10, 0));
    /** Under this width the strip's button goes under its text (the strip is hidden since 0.17.2). */
    static final int STRIP_SIDE_BY_SIDE = 720;
    private final JLabel suggestText = new JLabel();
    private final JButton suggestGo = ArchvowSplitView.primary("");  // 0.17.0: the one filled button of the tab
    private final JButton keepQuestionInline = ArchvowSplitView.link("");
    private final JButton removeQuestionInline = ArchvowSplitView.link("");
    private final JButton keepAiHazardButton = new JButton();
    private final JPanel rulesAiActions = new JPanel(new ArchvowWrapLayout(FlowLayout.LEFT, 5, 2));
    private final JEditorPane briefsPane = htmlPane();
    /** 0.17.0: the line under the views (Show All · Open · To review, and where the work is when a view is done). */
    private final JPanel listFilterLine = new JPanel(new ArchvowWrapLayout(FlowLayout.LEFT, 10, 0));
    private int[] viewPending = new int[6];
    private int[] viewTotal = new int[6];
    /** "decision" / "question" after a save: once that item is answered, its view shows the next open one. */
    private String advance = "";
    /** The safety answer field, its heading and buttons: shown for an open safety question only. */
    private List<JComponent> safetyEditorParts = List.of();
    private final DefaultMutableTreeNode componentsRoot = new DefaultMutableTreeNode(new ArchvowTreeIcons.Node("Architecture", "architecture", ""));
    private final ArchvowComponentTree componentsTree = new ArchvowComponentTree(componentsRoot);
    private String componentFilter = "all";
    private JButton allComponentsFilter, pendingComponentsFilter, resolvedComponentsFilter, addComponentButton;
    private final DefaultListModel<ArchvowGrammar.Command> commandModel = new DefaultListModel<>();
    private final JList<ArchvowGrammar.Command> commandList = new JList<>(commandModel);
    private JTree commandTree;
    /** The command form: as wide as the view, so the lines under the fields wrap instead of running past its edge. */
    private final JPanel form = new ArchvowCommandFormLayout.WidthTrackingPanel(new GridBagLayout());
    private JScrollPane formScroller;
    // Guided form navigation is a view over the original fields.  It never
    // rebuilds or rewrites their values when the person changes stages.
    private final JPanel formNavigation = new JPanel(new BorderLayout(4, 4));
    private final JPanel formStepButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));
    private JScrollPane formStepStrip;
    private final JLabel formStageStatus = new JLabel();
    private final JButton formBack = new JButton();
    private final JButton formNext = new JButton();
    private final Map<String, List<Component>> formStageComponents = new LinkedHashMap<>();
    private final JEditorPane formReview = htmlPane();
    private List<Map.Entry<String, String>> formReviewItems = List.of();
    private int formReviewColumns = -1;
    private String guidedCommand = "";
    private int formStageIndex;
    /** Design A (0.14.0): the form's title, what archvow will do, the command as it is typed, its refusal in place. */
    private final JLabel formTitle = new JLabel();
    private final ArchvowWrapLabel formDoes = new ArchvowWrapLabel();
    private final JTextArea formPreview = new JTextArea(3, 22);
    /** 0.15.0: the files the command writes, under the command (ArchvowFilePlan). */
    private final ArchvowFilePlanView formFiles = new ArchvowFilePlanView();
    private final JPanel formAlerts = new JPanel();
    private JButton formRunButton;
    /** The selected safety question's decision says there is nothing to protect: Not applicable needs no reason. */
    private boolean coveredNothing;
    /** Accept…, shown only for a component the AI created (origin ai). */
    private JButton aiAcceptButton;
    /** 0.17.3: under the title, as VS Code: the command as a chip, then what it does; the prefill note apart. */
    private final JLabel commandChip = new JLabel() {
        @Override public void updateUI() { super.updateUI(); styleCommandChip(this); }
    };
    private final ArchvowWrapLabel commandHelp = new ArchvowWrapLabel();
    private final ArchvowWrapLabel commandNote = new ArchvowWrapLabel();
    private final Map<String, JComponent> fields = new LinkedHashMap<>();
    /** The comparison base: the best existing branch (ArchvowChoices.bestBase) until the person chooses one. */
    private final JTextField branch = new JTextField("", 14);
    private final JLabel projectInfoLabel = new JLabel();
    private final JLabel componentPendingExplanation = new JLabel();
    private final JLabel gitBranchLabel = new JLabel();
    /** On main / master / develop: the advice to make a feature branch, on as many lines as it needs (0.11.4). */
    private final ArchvowWrapLabel featureBranchWarning = new ArchvowWrapLabel();
    /** The briefs folders (init answer `briefs`, archvow 1.17) where --from may draw from (0.12.0). */
    private final JLabel briefFolders = new JLabel();
    private ArchvowConfirmPanel commandConfirmation;
    private ArchvowConfirmPanel componentConfirmation;
    private JPanel decisionConfirmSlot, questionConfirmSlot, safetyConfirmSlot;

    private JButton comparisonBaseButton;
    private final Map<String, Integer> pendingByComponent = new LinkedHashMap<>();
    private final Map<String, Integer> resolvedByComponent = new LinkedHashMap<>();
    private final DefaultListModel<QuickItem> pendingModel = new DefaultListModel<>();
    private final DefaultListModel<QuickItem> resolvedModel = new DefaultListModel<>();
    private final JList<QuickItem> pendingList = new JList<>(pendingModel);
    private final JList<QuickItem> resolvedList = new JList<>(resolvedModel);
    // Only non-classifiable or project-wide entries are displayed in Other.
    // Counts above Overview still reflect all known component + global records.
    private final DefaultListModel<QuickItem> otherPendingModel = new DefaultListModel<>();
    private final DefaultListModel<QuickItem> otherResolvedModel = new DefaultListModel<>();
    private final JList<QuickItem> otherPendingList = new JList<>(otherPendingModel);
    private final JList<QuickItem> otherResolvedList = new JList<>(otherResolvedModel);
    private final DefaultListModel<QuickItem> componentOtherPendingModel = new DefaultListModel<>();
    private final DefaultListModel<QuickItem> componentOtherResolvedModel = new DefaultListModel<>();
    private final JList<QuickItem> componentOtherPendingList = new JList<>(componentOtherPendingModel);
    private final JList<QuickItem> componentOtherResolvedList = new JList<>(componentOtherResolvedModel);
    private final ArchvowNavigationTabs quickTabs = new ArchvowNavigationTabs();
    private final ArchvowNavigationTabs componentOtherTabs = new ArchvowNavigationTabs();
    private final JEditorPane quickDetails = htmlPane();
    private final JEditorPane componentOtherDetails = htmlPane();
    private final ArchvowNavigationTabs narrowOverviewTabs = new ArchvowNavigationTabs();
    private JPanel summaryCard, checkCard;
    /** Overview cards are always visible, even when their detailed editor views are closed. */
    private final JLabel overviewPacksCounts = new JLabel("—");
    private JComponent quickCard;
    private JComponent componentOtherCard;
    private JPanel overviewCenter;
    private JLabel brandSubtitle;
    private JLabel vowsLabel, decisionAnswerLabel;
    private JPanel actionsHeading;
    private final java.util.List<Runnable> languageRefreshers = new ArrayList<>();
    private JLabel brandTitle, languageLabel;
    private final JButton quickPrimary = new JButton();
    private final JButton quickSecondary = new JButton();
    private final JButton componentOtherPrimary = new JButton();
    private final JButton componentOtherSecondary = new JButton();
    private final JLabel logoLabel = new JLabel();
    private final JComboBox<String> languageMode = new JComboBox<>(ArchvowI18n.modes());
    private final JButton openSettingsButton = new JButton();
    private final JButton testArchvowButton = new JButton();

    private JsonObject manifest, check, selectedView;
    private final Map<String, JsonObject> componentSnapshots = new LinkedHashMap<>();
    private NodeComponent selectedComponent;
    private String nextResult = "";
    private String checkFailure = "";
    private boolean refreshing, rebuilding;
    /** archvow --version was compared with the plugin's contract (once per panel). */
    private boolean versionChecked;
    private QuickItem selectedQuickItem;

    private final Role role;
    /** The constructor ran: list selections from now on are a person's (the navigation routes them). */
    private boolean constructed;
    /** The navigation follows the editor tab (a selection made there): no routing back. */
    private boolean syncing;
    private boolean syncingLanguage;  // the language box set by the code, not by a person
    /** The editor tab: what Overview's last command was, for its title. */
    private String resultLabel = "";
    private final JLabel detailKind = new JLabel();
    /** The editor tab: back to the component a command was opened from ("← clean_orders"). */
    private final JButton backButton = new JButton();
    /** That component and its view (Decisions...), or null: the tool window chose what the tab shows. */
    private String backComponent;
    private int backView;
    /** A command opened from the tool window (no way back to a component). */
    private boolean fromToolWindow;
    /** Lives as long as this panel (the project's, or the editor tab's): the vow watcher hangs on it. */
    private final Disposable lifetime = Disposer.newDisposable("Archvow workspace");

    ArchvowPanel(Project project) {
        this(project, Role.NAVIGATION);
    }

    ArchvowPanel(Project project, Role role) {
        super(new BorderLayout());
        this.role = role;
        this.project = project;
        ArchvowRunLog.sink(IDEA_LOG);
        this.cli = new ArchvowCli(project);
        this.catalogCli = new ArchvowCli(project);
        Disposer.register(project, lifetime);
        ArchvowVowWatcher.watch(project, lifetime, () -> {
            if (System.currentTimeMillis() < ownChangeUntil) return true;  // the action just run, already read again
            if (refreshing || cli.isRunning()) return false;  // busy: the watcher tries again
            reload(false, true);
            return true;
        });
        setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        setPreferredSize(new Dimension(640, 600));
        setMinimumSize(new Dimension(240, 300));
        loadSettingsUiState();
        add(buildHeader(), BorderLayout.NORTH);
        buildTabs();
        rootCards.add(tabs, "workspace");
        rootCards.add(buildWelcomeView(), "welcome");
        resultExpansion = new ArchvowResultExpansion(rootCards, rootLayout, "workspace");
        add(rootCards, BorderLayout.CENTER);
        for (ArchvowGrammar.Command command : ArchvowGrammar.COMMANDS) commandModel.addElement(command);
        selectCommand("check");
        refreshStaticTexts();
        ArchvowUiIcons.decorateTree(this);
        if (role == Role.NAVIGATION) tabs.setCompact(true); // after the icons: the narrow tool window has none on its main tabs
        overviewSummary.showLoading(ArchvowI18n.text(project,
            "Loading project architecture…", "Chargement de l'architecture du projet…"));
        overviewSummary.onOpenComponent(id -> {
            if (role == Role.NAVIGATION) { ArchvowEditorTab.showComponent(project, id); return; }
            tabs.setSelectedIndex(1);
            // Tree selection already loads the component; do not launch a second CLI call.
            if (!locate(componentsRoot, new ArrayList<>(), id)) {
                selectedComponent = new NodeComponent(id, "", "", "");
                loadComponent(selectedComponent);
            }
        });
        diagnostics.onInspect(this::openDiagnosticDetails);
        diagnostics.onOpenCode(this::openDiagnosticCode);
        diagnostics.onOpenAll(this::openChecksDocument);
        detailDiagnostics.onOpenCode(this::openDiagnosticCode);
        detailDiagnostics.onOpenAll(this::openChecksDocument);
        detailDiagnostics.onOpenLocation(file -> openViolationFile(file.path(), file.line()));
        detailDiagnostics.onPrepareFix((name, values) -> openCommand(name, values));
        diagnostics.showData(null, "", ArchvowI18n.isFrench(project));
        detailDiagnostics.showData(null, "", ArchvowI18n.isFrench(project));
        // One consistent, lightweight motion language across navigation surfaces.
        // Navigation is now explicit buttons + CardLayout, not custom tab hit-testing.
        for (JList<?> list : List.of(otherPendingList, otherResolvedList, decisionsList,
                questionList, safetyList, assumptionList, commandList)) ArchvowMotion.installList(list);
        ArchvowMotion.installTree(componentsTree);
        commandList.setCellRenderer(new ArchvowMotion.CommandRenderer());
        loadGitBranch();
        addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) { adaptOverviewLayout(); }
        });
        SwingUtilities.invokeLater(this::adaptOverviewLayout);
        if (role == Role.DETAIL) tabs.addChangeListener(e -> afterSelectionChange());
        constructed = true;
    }

    private void loadSettingsUiState() {
        String mode = ArchvowSettings.get(project).getState().languageMode;
        languageMode.setSelectedItem(mode == null || mode.isBlank() ? "EN" : mode);
        languageMode.addActionListener(e -> {
            if (syncingLanguage) return;
            ArchvowSettings.get(project).getState().languageMode = String.valueOf(languageMode.getSelectedItem());
            languageChanged();
            // the other Archvow panel of the project (the editor tab, or the tool window) speaks the new language too,
            // without closing and opening it again
            ArchvowPanel other = role == Role.NAVIGATION ? ArchvowEditorTab.openPanel(project) : navigation();
            if (other != null && other != this) other.languageChanged();
        });
        logoLabel.setIcon(loadLogoIcon(64, 64));
    }

    private void loadGitBranch() {
        gitBranchLabel.setText(ArchvowI18n.text(project, "Branch: detecting…", "Branche : détection…"));
        String root = project.getBasePath();
        if (root == null) { gitBranchLabel.setText("Git: —"); return; }
        new SwingWorker<String[], Void>() {
            @Override protected String[] doInBackground() {
                String current = git(root, "branch", "--show-current");
                String refs = git(root, "for-each-ref", "--format=%(refname)", "refs/heads", "refs/remotes", "refs/tags");
                String head = git(root, "symbolic-ref", "--quiet", "--short", "refs/remotes/origin/HEAD");
                String roots = git(root, "rev-list", "--max-parents=0", "HEAD");
                // several roots (merged histories): the oldest is the last one listed
                String first = "";
                if (roots != null) for (String line : roots.split("\\R")) if (!line.isBlank()) first = line.trim();
                return new String[] {current == null ? "—" : current.isBlank() ? "(detached)" : current.trim(),
                    refs == null ? "" : refs, head == null ? "" : head.trim(), first};
            }
            @Override protected void done() {
                String[] read;
                try { read = get(); }
                catch (Exception ex) { read = new String[] {"—", "", "", ""}; }
                gitBranchLabel.setText("Git: " + read[0]);
                gitBranchLabel.setToolTipText(root);
                updateBranchWarning(read[0]);
                gitCurrent = read[0];
                gitRefs = ArchvowChoices.refNames(read[1]);
                gitRefKinds = ArchvowChoices.refKinds(read[1]);
                remoteHead = read[2];
                firstCommit = read[3];
                followBestBase();
            }
        }.execute();
    }

    /** One git command in the project: its output, or null when it fails (3 s at most). */
    private static String git(String root, String... args) {
        Process process = null;
        try {
            List<String> command = new ArrayList<>();
            command.add("git");
            command.addAll(List.of(args));
            process = new ProcessBuilder(command).directory(new File(root)).redirectErrorStream(false).start();
            byte[] out = process.getInputStream().readAllBytes();
            if (!process.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)) { process.destroyForcibly(); return null; }
            return process.exitValue() == 0 ? new String(out, java.nio.charset.StandardCharsets.UTF_8) : null;
        } catch (Exception ex) {
            if (process != null) process.destroyForcibly();
            return null;
        }
    }

    /**
     * The base Diff, Review and Budget compare with: the best branch that exists here (develop on a Git flow
     * feature branch, else what origin's HEAD names, main, master...), until the person chooses one. It was a
     * fixed origin/main, which a repository without that remote does not have (0.12.1).
     */
    private void followBestBase() {
        if (!baseTouched) {
            boolean gitflow = "gitflow".equals(ArchvowGrammar.str(manifest == null ? null : manifest.getAsJsonObject("init_answers"), "branch_naming"));
            branch.setText(ArchvowChoices.bestBase(gitRefs, gitCurrent, gitflow, remoteHead));
        }
        if (comparisonBaseButton != null) comparisonBaseButton.setText(baseLabel());
    }

    private String baseLabel() {
        String ref = branch.getText().trim();
        if (ref.isEmpty()) return "Base: —";
        String kind = gitRefKinds.get(ref);
        String suffix = "local".equals(kind) ? ArchvowI18n.text(project, " (local)", " (locale)")
            : "remote".equals(kind) ? ArchvowI18n.text(project, " (remote)", " (distante)")
            : "tag".equals(kind) ? " (tag)" : "";
        return "Base: " + ref + suffix;
    }

    /** Why a base cannot be compared with, and which exist ('' when it exists, or Git knows it as a commit). */
    private String refProblem(String ref) {
        String problem = ArchvowChoices.missingRef(ref, gitRefs, ArchvowI18n.isFrench(project));
        if (problem.isBlank() || project.getBasePath() == null) return problem;
        return git(project.getBasePath(), "rev-parse", "--verify", "--quiet", ref.trim() + "^{commit}") != null ? "" : problem;
    }

    /**
     * Choose the comparison base among the branches and tags of this repository (or type another ref);
     * null when cancelled or when it does not exist here (the message says which do).
     */
    private String askBase(String title) {
        JComboBox<String> choice = new JComboBox<>(gitRefs.stream().filter(r -> !r.endsWith("/HEAD")).toArray(String[]::new));
        choice.setEditable(true);
        choice.setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                                       boolean selected, boolean focused) {
                JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, selected, focused);
                String ref = value == null ? "" : value.toString();
                String kind = gitRefKinds.get(ref);
                String suffix = "local".equals(kind) ? ArchvowI18n.text(project, "local branch", "branche locale")
                    : "remote".equals(kind) ? ArchvowI18n.text(project, "remote branch (fetched)", "branche distante (récupérée)")
                    : "tag".equals(kind) ? "tag" : "";
                if (!suffix.isEmpty() && index >= 0) label.setText(ref + " — " + suffix);
                return label;
            }
        });
        choice.setSelectedItem(branch.getText());
        JPanel content = new JPanel(new BorderLayout(0, 6));
        content.add(new JLabel(title), BorderLayout.NORTH);
        content.add(choice, BorderLayout.CENTER);
        content.add(new JLabel(gitRefs.isEmpty() ? ArchvowI18n.text(project,
            "No other ref in this repository yet: nothing to compare with.", "Aucune autre référence Git dans ce dépôt : rien à comparer.")
            : ArchvowI18n.text(project, "Remote refs reflect the last git fetch.", "Les références distantes reflètent le dernier git fetch.")), BorderLayout.SOUTH);
        if (JOptionPane.showConfirmDialog(this, content, "archvow", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return null;
        String ref = comboText(choice);
        if (ref.isBlank()) return null;
        String problem = refProblem(ref);
        if (!problem.isBlank()) { message(problem); return null; }
        return ref;
    }

    private void updateBranchWarning(String current) {
        boolean shared = ArchvowBranchAdvice.needsFeatureBranch(current);
        featureBranchWarning.setVisible(shared);
        if (shared) {
            featureBranchWarning.setText(ArchvowI18n.text(project,
                "⚠ You are on " + current + ". For development, create a feature branch (e.g. feature/my-change) first.",
                "⚠ Branche " + current + " : crée une branche de fonctionnalité (ex. feature/ma-modification) avant de développer."));
            featureBranchWarning.setToolTipText(ArchvowI18n.text(project,
                "Advice only. Archvow does not automatically change Git branches.",
                "Conseil uniquement. Archvow ne change pas automatiquement de branche Git."));
        }
    }

    private void requestConfirmation(String title, String summary, Runnable onConfirm, boolean componentContext) {
        ArchvowConfirmPanel target = componentContext ? componentConfirmation : commandConfirmation;
        if (target == null) return;
        if (componentContext) {
            int active = componentViewNav == null ? -1 : componentViewNav.activeIndex();
            JPanel slot = switch (active) {
                case 0 -> decisionConfirmSlot;
                case 1 -> questionConfirmSlot;
                case 2 -> safetyConfirmSlot;
                default -> null;
            };
            if (slot == null) return; // Never render approval in an unrelated view.
            if (target.getParent() != slot) {
                if (target.getParent() != null) target.getParent().remove(target);
                slot.add(target, BorderLayout.CENTER);
            }
            slot.revalidate();
        }
        target.showConfirmation(title, summary, ArchvowI18n.isFrench(project), onConfirm);
        target.revalidate();
        revalidate(); repaint();
        if (componentContext) SwingUtilities.invokeLater(() ->
            target.scrollRectToVisible(new Rectangle(0, 0, target.getWidth(), target.getHeight())));
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout(12, 0));
        header.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, borderColor()),
            BorderFactory.createEmptyBorder(8, 10, 10, 10)));

        JPanel brand = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        if (role == Role.DETAIL) {
            // Back to the component a command was opened from (Edit spec, + Question...), 0.11.3
            backButton.setVisible(false);
            backButton.addActionListener(e -> goBack());
            brand.add(backButton);
        }
        brand.add(logoLabel);
        JPanel titles = new JPanel();
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
        brandTitle = new JLabel("Archvow");
        brandTitle.setFont(brandTitle.getFont().deriveFont(Font.BOLD, 23f));
        brandSubtitle = translated(new JLabel(),
            "Specifications → understandable components", "Des specs aux composants compréhensibles");
        brandSubtitle.setForeground(new Color(125, 131, 140));
        titles.add(brandTitle);
        titles.add(Box.createVerticalStrut(2));
        titles.add(brandSubtitle);
        String root = project.getBasePath();
        projectInfoLabel.setText("⌂ " + project.getName());
        projectInfoLabel.setFont(projectInfoLabel.getFont().deriveFont(Font.BOLD, 11f));
        projectInfoLabel.setToolTipText(root == null ? project.getName() : root);
        titles.add(Box.createVerticalStrut(3));
        titles.add(projectInfoLabel);
        brand.add(titles);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
        languageLabel = new JLabel(ArchvowI18n.text(project,"Language","Langue"));
        // the language is chosen at the bottom of Overview (0.11.3): the header keeps room for the name
        languageMode.setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                setText(ArchvowI18n.modeLabel(project, String.valueOf(value)));
                return this;
            }
        });
        if (role == Role.DETAIL) {
            // the editor tab names what it shows; the language is chosen in the tool window
            languageLabel.setVisible(false);
            languageMode.setVisible(false);
            detailKind.setForeground(new Color(125, 131, 140));
            detailKind.setFont(detailKind.getFont().deriveFont(Font.BOLD, 11f));
            titles.add(detailKind, 0);
            brandSubtitle.setVisible(false);
        }
        // Shared compact project search, alongside the header utility icons.
        if (role == Role.NAVIGATION) {
            findField.setColumns(10);
            findField.setToolTipText(ArchvowI18n.text(project,
                "Find a function, column, ticket or example (Enter to search)",
                "Rechercher une fonction, une colonne, un ticket ou un exemple (Entrée pour chercher)"));
            findField.addActionListener(e -> showSearch(findField.getText()));
            // A magnifier INSIDE the search field, not a second Find button.
            JPanel searchBox = new JPanel(new BorderLayout(5, 0));
            JLabel searchIcon = new JLabel();
            searchIcon.setIcon(ArchvowUiIcons.iconFor(searchIcon, "search"));
            searchIcon.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 0));
            searchBox.add(searchIcon, BorderLayout.WEST);
            findField.setBorder(BorderFactory.createEmptyBorder(3, 0, 3, 5));
            searchBox.add(findField, BorderLayout.CENTER);
            searchBox.setPreferredSize(new Dimension(148, 30));
            searchBox.setBorder(BorderFactory.createLineBorder(borderColor(), 1, true));
            searchBox.setToolTipText(findField.getToolTipText());
            right.add(searchBox);
            JButton filter = button("", () -> {
                JPopupMenu menu = new JPopupMenu();
                for (String[] choice : new String[][] {
                        {"all", ArchvowI18n.text(project, "All", "Tous")},
                        {"pending", ArchvowI18n.text(project, "Pending", "En attente")},
                        {"resolved", ArchvowI18n.text(project, "Resolved", "Traités")} }) {
                    JMenuItem item = new JMenuItem(choice[1]);
                    item.addActionListener(e -> { tabs.setSelectedIndex(1); setComponentFilter(choice[0]); });
                    menu.add(item);
                }
                menu.show(filterButtonAnchor[0], 0, filterButtonAnchor[0].getHeight());
            });
            filterButtonAnchor[0] = filter;
            ArchvowUiIcons.explicit(filter, "filter");
            filter.setToolTipText(ArchvowI18n.text(project, "Filter components", "Filtrer les composants"));
            filter.getAccessibleContext().setAccessibleName("Filter components");
            compactUtility(filter);
            right.add(filter);
        }
        // Icon-only refresh: avoid the double icon created by decorating "Refresh" and
        // then replacing its label with the glyph ↻.
        JButton refresh = button("", this::reload);
        ArchvowUiIcons.explicit(refresh, "refresh");
        refresh.getAccessibleContext().setAccessibleName("Reload Archvow");
        refresh.setToolTipText(ArchvowI18n.text(project,"Reload Archvow","Recharger Archvow"));
        compactUtility(refresh);
        right.add(refresh);
        JButton logs = button("☷", () -> ArchvowRunLogView.show(project));
        logs.setToolTipText(ArchvowI18n.text(project, "Open Archvow session log", "Ouvrir le journal Archvow"));
        logs.putClientProperty("JButton.buttonType", "toolBarButton");
        compactUtility(logs);
        right.add(logs);
        languageRefreshers.add(() -> logs.setToolTipText(ArchvowI18n.text(project,
            "Open Archvow session log", "Ouvrir le journal Archvow")));

        // The tool window can become very narrow. Keep the utilities usable by moving
        // them below the brand instead of letting the search or logo get clipped.
        header.add(brand, BorderLayout.CENTER);
        header.add(right, BorderLayout.EAST);
        if (role == Role.NAVIGATION) {
            header.addComponentListener(new ComponentAdapter() {
                @Override public void componentResized(ComponentEvent event) {
                    String wanted = header.getWidth() < 470 ? BorderLayout.SOUTH : BorderLayout.EAST;
                    Object where = ((BorderLayout) header.getLayout()).getConstraints(right);
                    if (wanted.equals(where)) return;
                    header.remove(right);
                    header.add(right, wanted);
                    header.revalidate();
                    header.repaint();
                }
            });
        }
        return header;
    }

    private static void compactUtility(JButton button) {
        button.setFocusPainted(false);
        button.setMargin(new Insets(3, 4, 3, 4));
        button.setPreferredSize(new Dimension(30, 30));
        button.setMinimumSize(new Dimension(28, 28));
        button.setBorder(BorderFactory.createLineBorder(ArchvowMotion.dark()
            ? new Color(103, 110, 121) : new Color(200, 207, 220), 1, true));
    }

    private void buildTabs() {
        tabs.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
        JPanel overview = overviewPanel();
        if (role == Role.NAVIGATION) {
            tabs.addTab("Overview", overview);
        } else {
            // the editor tab: index 0 is the result of a command run from the tool window's Overview
            detailHomeCards = new JPanel(detailHomeLayout);
            JPanel resultPane = new JPanel(new BorderLayout());
            resultPane.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
            resultPane.add(overviewResultCard, BorderLayout.CENTER);
            JPanel checksPane = new JPanel(new BorderLayout());
            checksPane.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
            checksPane.add(createCard(detailDiagnostics,
                ArchvowI18n.text(project, "Check & Gates", "Contrôles et validations")), BorderLayout.CENTER);
            detailHomeCards.add(resultPane, "result");
            detailHomeCards.add(checksPane, "checks");
            // A real briefs browser, not the `brief show` command form. Reuse the
            // same section-by-section HTML and links already fed by brief show --json.
            JPanel briefsDetail = new JPanel(new BorderLayout(0, 8));
            briefsDetail.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
            JPanel briefActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 2));
            JButton acceptBrief = button(ArchvowI18n.text(project, "Accept brief…", "Accepter un brief…"),
                () -> openCommand("brief accept", Map.of()));
            acceptBrief.setToolTipText(ArchvowI18n.text(project,
                "Review a changed brief and open the confirmation form; nothing is accepted automatically.",
                "Relire un brief modifié et ouvrir le formulaire de confirmation ; rien n'est accepté automatiquement."));
            briefActions.add(acceptBrief);
            JButton reloadBriefs = button(ArchvowI18n.text(project, "Refresh", "Actualiser"), this::reload);
            briefActions.add(reloadBriefs);
            briefsDetail.add(briefActions, BorderLayout.NORTH);
            briefsDetail.add(createCard(scroll(briefsPane), "Briefs"), BorderLayout.CENTER);
            detailHomeCards.add(briefsDetail, "briefs");
            skillDetails = new JPanel(new BorderLayout());
            skillDetails.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
            detailHomeCards.add(skillDetails, "skill");
            searchView = new ArchvowSearchView(new ArchvowSearchView.Actions() {
                @Override public void openFile(String relative, int line) { openViolationFile(relative, line); }
                @Override public void showComponent(String id) { ArchvowPanel.this.showComponent(id); }
                @Override public void openRule(String code) { openDiagnosticCode(code); }
                @Override public void search(String name) { showSearch(name); }
            });
            detailHomeCards.add(searchView, "search");
            featureView = new ArchvowFeatureView(new ArchvowFeatureView.Actions() {
                @Override public void showComponent(String id) { ArchvowPanel.this.showComponent(id); }
                @Override public void showFeature(String name) { ArchvowPanel.this.showFeature(name); }
                @Override public void openFile(String relative) { openViolationFile(relative, 0); }
                @Override public void openCommand(String command, Map<String, String> values) { ArchvowPanel.this.openCommand(command, values); }
                @Override public void openDoc(String feature) { ArchvowPanel.this.openDoc(feature, List.of()); }
                @Override public void copyPrompt(String feature) { copyFeaturePrompt(feature); }
                @Override public void nextSteps(String feature) { executeDirect("next", List.of("next", "--feature", feature, "--json"), null, false); }
            });
            detailHomeCards.add(featureView, "feature");
            detailHomeLayout.show(detailHomeCards, detailHomeMode);
            tabs.addTab("Result", detailHomeCards);
        }
        tabs.addTab(ArchvowI18n.text(project, "Features", "Fonctionnalités"), componentPanel());
        tabs.addTab("Commands", commandPanel());
        if (role == Role.NAVIGATION) tabs.addTab(ArchvowI18n.text(project, "AI skills", "Skills IA"), aiSkillsPanel());
        tabs.setStripVisible(role == Role.NAVIGATION);
        tabs.setCompact(role == Role.NAVIGATION);
    }

    // ------------------------------------------------------------------ the tool window and the editor tab

    Role role() { return role; }

    /** The editor tab just opened: what the tool window already read, else read now. */
    void openedAsTab() {
        ArchvowPanel nav = navigation();
        if (nav != null && nav.manifest != null) adoptFrom(nav);
        else SwingUtilities.invokeLater(() -> reload(false));
    }

    /** The tool window's panel, when it exists. */
    private ArchvowPanel navigation() {
        ArchvowPanel nav = project.getUserData(ArchvowToolWindowFactory.PANEL);
        return nav == this ? null : nav;
    }

    /** The editor tab: what archvow said, as the tool window read it. */
    void adoptFrom(ArchvowPanel source) {
        if (role != Role.DETAIL || source == null || source == this) return;
        boolean newManifest = manifest != source.manifest;
        manifest = source.manifest;
        check = source.check;
        checkFailure = source.checkFailure;
        briefStatus = source.briefStatus;
        nextResult = source.nextResult;
        componentSnapshots.clear();
        componentSnapshots.putAll(source.componentSnapshots);
        projectCatalog = source.projectCatalog;
        projectPacks = source.projectPacks;
        firstStepsJson = source.firstStepsJson;
        projectFeatures = source.projectFeatures;
        gitRefs = source.gitRefs;
        firstCommit = source.firstCommit;
        gitCurrent = source.gitCurrent;
        gitRefKinds = source.gitRefKinds;
        remoteHead = source.remoteHead;
        if (!baseTouched && !source.baseTouched) followBestBase();
        else if (!baseTouched) { branch.setText(source.branch.getText()); if (comparisonBaseButton != null) comparisonBaseButton.setText(baseLabel()); }
        projectStatus = source.projectStatus;
        if (manifest == null) {
            if (source.projectStatus != null && !source.projectStatus.ready()) showWelcome(source.projectStatus, "");
            return;
        }
        showWorkspace();
        if (newManifest) rebuildTree();
        diagnostics.showData(check, checkFailure, ArchvowI18n.isFrench(project));
        detailDiagnostics.showData(check, checkFailure, ArchvowI18n.isFrench(project));
        updateOverview();
        if (selectedComponent != null) {
            JsonObject fresh = componentSnapshots.get(selectedComponent.id);
            // only when it changed: a person may be typing an answer
            if (fresh != null && !fresh.equals(selectedView)) { selectedView = fresh; renderComponent(fresh); }
        }
        if ("feature".equals(detailHomeMode) && !shownFeature.isBlank()) renderFeature();
    }

    /** The tool window: hands what it read to the editor tab, when the tab is open. */
    private void pushToDetail() {
        if (role != Role.NAVIGATION) return;
        ArchvowPanel detail = ArchvowEditorTab.openPanel(project);
        if (detail != null) detail.adoptFrom(this);
    }

    /** The editor tab: shows a component (a click in the tool window). */
    void showComponent(String id) {
        if (id == null || id.isBlank()) return;
        backComponent = null;
        showWorkspace();
        tabs.setSelectedIndex(1);
        if (componentViewNav != null && componentViewNav.activeIndex() == 7) selectComponentView(0);
        if (selectedComponent == null || !selectedComponent.id.equals(id)
                || componentsTree.getSelectionPath() == null) {
            if (!locate(componentsRoot, new ArrayList<>(), id)) {
                setComponentFilter("all");
                if (!locate(componentsRoot, new ArrayList<>(), id)) {
                    selectedComponent = new NodeComponent(id, "", "", "");
                    loadComponent(selectedComponent);
                }
            }
        }
        afterSelectionChange();
    }

    /** The editor tab: archvow show <file>[:<line>] (ShowFileArchvowAction), then the component that owns
     *  the file; a file no component owns gets archvow's hint (the component add ... --file to run). */
    void showFile(String relative, int line) {
        cli.execute("Show " + relative, List.of("show", ArchvowLinks.showArgument(relative, line), "--json"), null, result -> {
            JsonObject data = ArchvowGrammar.parseObject(result.stdout);
            String id = data == null ? "" : ArchvowGrammar.str(data, "component");
            if (!id.isBlank()) { showComponent(id); return; }
            String hint = data == null ? result.output : ArchvowGrammar.str(data, "hint");
            message(hint.isBlank() ? ArchvowI18n.text(project, "No component owns this file.",
                "Aucun composant ne possède ce fichier.") : hint);
        });
    }

    /** The editor tab (0.18.0): archvow show <name> --json - where a name is in the project, and the problems
     *  naming it; a component's name opens the component, as archvow does. */
    void showSearch(String name) {
        String wanted = name == null ? "" : name.trim();
        if (wanted.isEmpty()) return;
        if (role == Role.NAVIGATION) { ArchvowEditorTab.showSearch(project, wanted); return; }
        cli.execute("Find " + wanted, List.of("show", wanted, "--json"), null, result -> {
            JsonObject data = ArchvowGrammar.parseObject(result.stdout);
            if (data == null) {
                message(result.output.isBlank() ? ArchvowI18n.text(project, "archvow found nothing to show.",
                    "archvow n'a rien trouvé à montrer.") : result.output);
                return;
            }
            String id = ArchvowGrammar.str(data, "component");
            if (!id.isBlank() && !data.has("groups")) { showComponent(id); return; }
            backComponent = null;
            showWorkspace();
            searchName = wanted;
            searchView.show(data, wanted, project.getBasePath(), ArchvowI18n.isFrench(project));
            showDetailHome("search");
            tabs.setSelectedIndex(0);
            afterSelectionChange();
        });
    }

    /** A feature's page in the editor tab (0.24.0, contract 2.0): from the Components tab, a link or the page itself. */
    void showFeature(String name) {
        if (name == null || name.isBlank()) return;
        if (role == Role.NAVIGATION) { ArchvowEditorTab.showFeature(project, name); return; }
        ArchvowPanel nav = navigation();
        if (nav != null && nav.manifest != null) adoptFrom(nav);
        backComponent = null;
        shownFeature = name;
        showWorkspace();
        renderFeature();
        resultLabel = name;
        showDetailHome("feature");
        tabs.setSelectedIndex(0);
        afterSelectionChange();
    }

    private void renderFeature() {
        if (featureView == null) return;
        Map<String, String> openItems = new LinkedHashMap<>();
        for (int i = 0; i < pendingModel.size(); i++) {
            QuickItem item = pendingModel.get(i);
            if (!item.componentId.isBlank()) openItems.putIfAbsent(item.componentId, item.title);
        }
        featureView.show(projectFeatures.get(shownFeature), manifest, pendingByComponent, openItems, ArchvowI18n.isFrench(project));
    }

    /** {@code archvow doc [<feature>]}, then its page in an editor, at the first of {@code headings} it has: Markdown
     *  to read, written into .archvow/doc/ (ignored by Git, never read back - nothing to confirm). */
    private void openDoc(String feature, List<String> headings) {
        List<String> args = feature == null || feature.isBlank() ? List.of("doc") : List.of("doc", feature);
        String page = ".archvow/doc/" + (feature == null || feature.isBlank() ? "project" : feature) + ".md";
        cli.executeAction("doc", args, null, r -> {
            String base = project.getBasePath();
            File file = base == null ? null : new File(base, page);
            if (!r.ok() || file == null || !file.isFile()) {
                message(r.output == null || r.output.isBlank() ? ArchvowI18n.text(project, "archvow doc wrote nothing.", "archvow doc n'a rien écrit.") : r.output);
                return;
            }
            int line = 0;
            try {
                List<String> lines = Files.readAllLines(file.toPath());
                for (int i = 0; i < lines.size() && line == 0; i++)
                    for (String h : headings) if (lines.get(i).startsWith(h)) { line = i + 1; break; }
            } catch (IOException ignored) {
                line = 0;
            }
            openViolationFile(page, line);
        });
    }

    /** {@code archvow prompt --feature <feature>} into the clipboard: the feature in a few lines, then the task of its next component. */
    private void copyFeaturePrompt(String feature) {
        cli.executeAction("prompt", List.of("prompt", "--feature", feature), null, r -> {
            if (!r.ok()) { message(r.output); return; }
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new java.awt.datatransfer.StringSelection(r.stdout), null);
            message(ArchvowI18n.text(project, "The instruction for " + feature + " is in the clipboard: paste it to your AI assistant.",
                "L'instruction pour " + feature + " est dans le presse-papiers : colle-la à ton assistant IA."));
        });
    }

    /** The tool window's header with the find field under it: Enter (or Find) searches the name. */
    private JComponent withFindField(JComponent header) {
        JPanel find = new JPanel(new BorderLayout(6, 0));
        find.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
        JLabel label = translated(new JLabel(), "Find in the project", "Chercher dans le projet");
        label.setForeground(new Color(125, 131, 140));
        findField.setToolTipText(ArchvowI18n.text(project, "A name: a function, a column, a ticket, an example (archvow show <name>)",
            "Un nom : une fonction, une colonne, un ticket, un exemple (archvow show <nom>)"));
        findField.addActionListener(e -> showSearch(findField.getText()));
        JButton go = button(ArchvowI18n.text(project, "Find", "Chercher"), () -> showSearch(findField.getText()));
        languageRefreshers.add(() -> go.setText(ArchvowI18n.text(project, "Find", "Chercher")));
        find.add(label, BorderLayout.NORTH);
        find.add(findField, BorderLayout.CENTER);
        find.add(go, BorderLayout.EAST);
        JPanel both = new JPanel(new BorderLayout());
        both.add(header, BorderLayout.NORTH);
        both.add(find, BorderLayout.SOUTH);
        return both;
    }

    /** The editor tab: runs a command asked in the tool window's Overview, its result in the tab. */
    void runFromOverview(String label, List<String> args, String stdin, boolean refreshAfter) {
        backComponent = null;
        showWorkspace();
        resultLabel = label;
        showDetailHome("result");
        tabs.setSelectedIndex(0);
        executeDirect(label, args, stdin, refreshAfter);
        afterSelectionChange();
    }

    private void showDetailHome(String mode) {
        detailHomeMode = mode == null || mode.isBlank() ? "result" : mode;
        if (detailHomeCards != null) detailHomeLayout.show(detailHomeCards, detailHomeMode);
    }

    void openDiagnosticDetails(int index) {
        if (role == Role.NAVIGATION) {
            ArchvowEditorTab.showChecks(project, index);
            return;
        }
        ArchvowPanel nav = navigation();
        if (nav != null && nav.manifest != null) adoptFrom(nav);
        showWorkspace();
        detailDiagnostics.showData(check, checkFailure, ArchvowI18n.isFrench(project));
        detailDiagnostics.selectDiagnostic(index);
        resultLabel = ArchvowI18n.text(project, "Check & Gates", "Contrôles et validations");
        showDetailHome("checks");
        tabs.setSelectedIndex(0);
        afterSelectionChange();
    }

    /** The editor tab: the counters of the tool window's Overview. */
    void openQuickListFromNavigation(int listIndex) {
        openQuickList(listIndex);
        afterSelectionChange();
    }

    /** The tool window: a check run in the editor tab. */
    void acceptCheck(JsonObject result) {
        if (result == null) return;
        check = result;
        checkFailure = "";
        diagnostics.showData(check, checkFailure, ArchvowI18n.isFrench(project));
        detailDiagnostics.showData(check, checkFailure, ArchvowI18n.isFrench(project));
        tabs.setSelectedIndex(0);
        narrowOverviewTabs.setSelectedIndex(1);
        if (manifest != null) updateOverview();
    }

    /** The Overview folder icon opens the browsable briefs in the shared editor tab. */
    void openBriefsDetails() {
        if (role == Role.NAVIGATION) {
            ArchvowEditorTab.showBriefs(project);
            return;
        }
        ArchvowPanel nav = navigation();
        if (nav != null) adoptFrom(nav);
        backComponent = null;
        showWorkspace();
        resultLabel = "Briefs";
        showDetailHome("briefs");
        tabs.setSelectedIndex(0);
        afterSelectionChange();
    }

    /** Open Checks and the toolbar Check action share the same full CLI result. */
    private void runFullCheck() {
        executeDirect("full check", List.of("check", "--complete", "--json"), null, false);
    }

    /** Check & Gates, in the tool window's Overview. */
    private void showDiagnostics() {
        // Checks are in the editor detail, not in an invisible overview sub-tab.
        openDiagnosticDetails(0);
    }

    /** The tool window follows the editor tab: the shown component or command is selected, nothing is routed. */
    private void follow(String component, String command) {
        if (role != Role.NAVIGATION) return;
        syncing = true;
        try {
            if (component != null) {
                if (component.isBlank() || !locate(componentsRoot, new ArrayList<>(), component)) componentsTree.clearSelection();
                selectedComponent = component.isBlank() ? null : new NodeComponent(component, "", "", "");
            }
            if (command != null) {
                if (command.isBlank()) commandList.clearSelection(); else selectCommand(command);
            }
        } finally { syncing = false; }
    }

    /** The editor tab: its title, its heading, and the tool window's selection follow what it shows. */
    private void afterSelectionChange() {
        if (role != Role.DETAIL) return;
        int index = tabs.getSelectedIndex();
        String kind;
        String what;
        String component = "";
        String command = "";
        if (index == 1) {
            kind = ArchvowI18n.text(project, "Component", "Composant");
            boolean other = componentViewNav != null && componentViewNav.activeIndex() == 7;
            component = selectedComponent == null ? "" : selectedComponent.id;
            what = other ? ArchvowI18n.text(project, "Other", "Autres")
                : component.isBlank() ? ArchvowI18n.text(project, "Components", "Composants") : component;
        } else if (index == 2) {
            kind = ArchvowI18n.text(project, "Command", "Commande");
            ArchvowGrammar.Command selected = commandList.getSelectedValue();
            command = selected == null ? "" : selected.label;
            what = command.isBlank() ? ArchvowI18n.text(project, "Commands", "Commandes") : command;
        } else {
            kind = "search".equals(detailHomeMode) ? ArchvowI18n.text(project, "Find", "Recherche")
                : "briefs".equals(detailHomeMode) ? "Briefs"
                : "checks".equals(detailHomeMode) ? ArchvowI18n.text(project, "Checks", "Contrôles")
                : "skill".equals(detailHomeMode) ? ArchvowI18n.text(project, "AI skill", "Skill IA")
                : ArchvowI18n.text(project, "Result", "Résultat");
            what = "search".equals(detailHomeMode) ? ArchvowI18n.text(project, "find ", "chercher ") + searchName
                : "checks".equals(detailHomeMode)
                ? ArchvowI18n.text(project, "Check & Gates", "Contrôles et validations")
                : "briefs".equals(detailHomeMode) ? "Briefs"
                : "skill".equals(detailHomeMode) ? detailSkillLabel
                : resultLabel.isBlank() ? ArchvowI18n.text(project, "Result", "Résultat") : resultLabel;
        }
        detailKind.setText("ARCHVOW · " + kind.toUpperCase(java.util.Locale.ROOT));
        boolean returnToBriefs = index == 0 && "skill".equals(detailHomeMode) && skillBackToBriefs;
        backButton.setVisible((backComponent != null && index != 1) || returnToBriefs);
        if (returnToBriefs) {
            backButton.setText("← Briefs");
            backButton.setToolTipText(ArchvowI18n.text(project, "Back to Briefs", "Retour aux briefs"));
        } else if (backComponent != null) {
            backButton.setText("← " + backComponent);
            backButton.setToolTipText(ArchvowI18n.text(project, "Back to " + backComponent, "Retour à " + backComponent));
        }
        if (brandTitle != null) brandTitle.setText(index == 2 ? "archvow " + what : what);
        // a component's name is the heading of its head, with its open items (buildComponentHtml)
        boolean named = index == 1 && !component.isBlank() && (componentViewNav == null || componentViewNav.activeIndex() != 7);
        if (brandTitle != null) brandTitle.setVisible(!named);
        ArchvowEditorTab.setTitle(project, "archvow: " + what);
        ArchvowPanel nav = navigation();
        if (nav != null) nav.follow(index == 1 ? component : null, index == 2 ? command : null);
    }


    /** The examples `archvow try` copies (2.1): value, label - as VS Code's TRY_EXAMPLES. */
    private static final List<String[]> TRY_EXAMPLES = List.of(new String[] {"pyspark-pipeline", "PySpark pipeline"},
        new String[] {"fastapi-service", "FastAPI service"}, new String[] {"python-app", "Python app"},
        new String[] {"langgraph-agent", "LangGraph agent"});

    private JPanel buildWelcomeView() {
        JPanel container = new JPanel(new GridBagLayout());
        JPanel column = new JPanel();
        column.setOpaque(false);
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));
        JLabel title = new JLabel(ArchvowI18n.text(project,"Archvow · Getting started","Archvow · Démarrage"));
        title.setFont(title.getFont().deriveFont(Font.BOLD, 17f));
        title.setAlignmentX(0f);
        column.add(title);
        column.add(Box.createVerticalStrut(14));
        JPanel card = new JPanel(new BorderLayout(12, 10));
        card.setAlignmentX(0f);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(borderColor(), 1, true),
            BorderFactory.createEmptyBorder(14, 16, 14, 16)));
        welcomeText.setPreferredSize(new Dimension(420, 100));
        card.add(welcomeText, BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        welcomeInit = button(ArchvowI18n.text(project,"Initialize Archvow","Initialiser Archvow"), () -> {
            showWorkspace();
            openCommand("init", Map.of());
        });
        actions.add(welcomeInit);
        actions.add(button(ArchvowI18n.text(project,"Configure Python","Configurer Python"), () ->
            ShowSettingsUtil.getInstance().showSettingsDialog(project, ArchvowSettingsConfigurable.class)));
        actions.add(button(ArchvowI18n.text(project,"Retry","Réessayer"), () -> { versionChecked = false; reload(); }));
        welcomeInstall = button(ArchvowI18n.text(project, "Install archvow " + ArchvowVersion.KNOWN, "Installer archvow " + ArchvowVersion.KNOWN),
            this::askInstall);
        welcomeInstall.setVisible(false);
        actions.add(welcomeInstall, 0);
        welcomeInstallCommand.setFont(new Font(Font.MONOSPACED, Font.PLAIN, welcomeInstallCommand.getFont().getSize()));
        welcomeInstallCommand.setVisible(false);
        JPanel south = new JPanel();
        south.setLayout(new BoxLayout(south, BoxLayout.Y_AXIS));
        for (JComponent part : List.<JComponent>of(welcomeInstallCommand, actions)) {
            part.setAlignmentX(0f);
            south.add(part);
            south.add(Box.createVerticalStrut(8));
        }
        welcomeAdopt = ArchvowOverviewCards.wrapped(ArchvowI18n.text(project,
            "Code already here? In Initialize, choose Adopt: your modules become components where they are, and Check then shows what the code does that no spec says.",
            "Du code déjà là ? Dans Initialiser, choisis Adopter : tes modules deviennent des composants là où ils sont, et Contrôler montre ensuite ce que le code fait sans qu'aucune spec ne le dise."),
            ArchvowOverviewCards.muted(), 12f);
        south.add(welcomeAdopt);
        welcomeConfirmation.setAlignmentX(0f);
        south.add(welcomeConfirmation);
        card.add(south, BorderLayout.SOUTH);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        column.add(card);
        column.add(Box.createVerticalStrut(14));
        welcomeTry = buildTryCard();
        column.add(welcomeTry);
        welcomeResult.setVisible(true);
        welcomeResultScroll = scroll(welcomeResult);
        welcomeResultScroll.setPreferredSize(new Dimension(420, 260));
        welcomeResultScroll.setAlignmentX(0f);
        welcomeResultScroll.setVisible(false);
        column.add(Box.createVerticalStrut(10));
        column.add(welcomeResultScroll);
        column.add(Box.createVerticalStrut(14));
        welcomeFoot = ArchvowOverviewCards.wrapped(ArchvowI18n.text(project,
            "After Initialize, the Overview shows the first steps. Developers and reviewers who clone a project with archvow start from its README.",
            "Après Initialiser, la Vue d'ensemble montre les premiers pas. Les développeurs et relecteurs qui clonent un projet avec archvow partent de son README."),
            ArchvowOverviewCards.muted(), 12f);
        column.add(welcomeFoot);
        GridBagConstraints gc = new GridBagConstraints();
        gc.weightx = 1; gc.fill = GridBagConstraints.HORIZONTAL;
        gc.insets = new Insets(15, 15, 15, 15);
        container.add(column, gc);
        return container;
    }

    /** "Not sure yet? See it catch a mistake" (0.25.0): archvow try on an example, in a scratch folder. */
    private JPanel buildTryCard() {
        JPanel card = ArchvowOverviewCards.card(borderColor(), 1, ArchvowOverviewCards.dark() ? new Color(38, 38, 43) : new Color(248, 248, 250));
        card.add(ArchvowOverviewCards.label(ArchvowI18n.text(project, "Not sure yet? See it catch a mistake", "Pas encore sûr ? Regarde-le attraper une erreur"),
            ArchvowOverviewCards.text(), Font.BOLD, 14f));
        card.add(Box.createVerticalStrut(10));
        card.add(ArchvowOverviewCards.wrapped(ArchvowI18n.text(project, "On a finished example, in a scratch folder: nothing in this folder changes.",
            "Sur un exemple fini, dans un dossier jetable : rien ne change dans ce dossier."), ArchvowOverviewCards.text(), 13f));
        card.add(Box.createVerticalStrut(10));
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.setAlignmentX(0f);
        row.add(ArchvowOverviewCards.label(ArchvowI18n.text(project, "Example", "Exemple"), ArchvowOverviewCards.muted(), Font.PLAIN, 13f), BorderLayout.WEST);
        JComboBox<String> example = new JComboBox<>(TRY_EXAMPLES.stream().map(e -> e[1]).toArray(String[]::new));
        row.add(example, BorderLayout.CENTER);
        row.add(button(ArchvowI18n.text(project, "Try it", "L'essayer"),
            () -> runTry(TRY_EXAMPLES.get(Math.max(0, example.getSelectedIndex()))[0])), BorderLayout.EAST);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
        card.add(row);
        return card;
    }

    /** archvow try (2.1): on the welcome screen its result shows under the Try card; in a project, as any command. */
    private void runTry(String example) {
        List<String> args = List.of("try", example, "--json");
        ArchvowProjectReadiness.Status status = ArchvowProjectReadiness.inspect(project.getBasePath());
        if (status.ready()) {
            executeDirect("try", args, null, false);
            return;
        }
        if (status.root().isBlank() || !new File(status.root()).isDirectory()) return;
        welcomeResultScroll.setVisible(true);
        welcomeResult.setText(htmlMessage("<b>⏳ " + escape(ArchvowI18n.text(project, "Running", "Exécution"))
            + "</b> · " + escape("archvow " + String.join(" ", args))));
        revalidate();
        cli.executeAction("try", args, null, result -> storeCommandResult(welcomeResult, "try", result));
    }

    private void showWelcome(ArchvowProjectReadiness.Status status, String customError) {
        projectStatus = status;
        String title;
        String detail;
        switch (status.state()) {
            case NO_PROJECT -> {
                title = ArchvowI18n.text(project, "No project", "Aucun projet");
                detail = ArchvowI18n.text(project, "Open an IDE project to use Archvow.",
                    "Ouvre un projet dans IntelliJ ou PyCharm pour utiliser Archvow.");
            }
            case ROOT_MISSING -> {
                title = ArchvowI18n.text(project, "Project directory unavailable", "Dossier projet inaccessible");
                detail = ArchvowI18n.text(project, "The IDE project directory is not accessible.",
                    "Le dossier du projet est introuvable ou inaccessible.");
            }
            case NOT_INITIALIZED -> {
                title = ArchvowI18n.text(project, "Archvow not initialized", "Archvow non initialisé");
                detail = ArchvowI18n.text(project,
                    "This folder does not contain .archvow/project.vow. Initialize Archvow to begin, or open an initialized project.",
                    "Ce dossier ne contient pas .archvow/project.vow. Initialise Archvow pour commencer, ou ouvre un projet déjà initialisé.");
            }
            case SPEC_INACCESSIBLE -> {
                title = ArchvowI18n.text(project, "Specification unavailable", "Spécification inaccessible");
                detail = ArchvowI18n.text(project, ".archvow/project.vow is not readable.",
                    ".archvow/project.vow n'est pas lisible.");
            }
            default -> {
                title = ArchvowI18n.text(project, "Archvow needs attention", "Configuration Archvow à vérifier");
                detail = ArchvowI18n.text(project, "The project is initialized, but Archvow could not load it.",
                    "Le projet est initialisé, mais Archvow n'a pas pu le charger.");
            }
        }
        if ("version".equals(installReason) && status.ready()) {
            title = ArchvowI18n.text(project, "Another archvow version", "Une autre version d'archvow");
            detail = ArchvowI18n.text(project, "This plugin speaks archvow's contract " + ArchvowVersion.CONTRACT
                + " (tested with archvow " + ArchvowVersion.KNOWN + "): the installed archvow does not, so the plugin may misread what it prints.",
                "Ce plugin parle le contrat " + ArchvowVersion.CONTRACT + " d'archvow (testé avec archvow " + ArchvowVersion.KNOWN
                + ") : l'archvow installé ne le parle pas, le plugin peut mal lire ce qu'il affiche.");
        }
        String extra = customError == null || customError.isBlank() ? "" :
            "<p style='color:" + (darkTheme() ? "#FF9CAB" : "#A52440") + ";'>" + escape(customError) + "</p>";
        welcomeText.setText(htmlHead() + ArchvowHtml.heading(escape(title), 1.3, "margin:4px 0 8px 0;") + "<p>" + escape(detail)
            + "</p><p><small>" + escape(status.root()) + "</small></p>" + extra + "</body></html>");
        welcomeText.setCaretPosition(0);
        welcomeInit.setEnabled(status.canInitialize());
        boolean fresh = status.state() == ArchvowProjectReadiness.State.NOT_INITIALIZED;
        for (JComponent part : List.<JComponent>of(welcomeTry, welcomeAdopt, welcomeFoot)) part.setVisible(fresh);
        showInstallCard(status);
        collapseExpandedResult();
        rootLayout.show(rootCards, "welcome");
    }

    /** The Install button and its command, when archvow does not start or does not speak the plugin's contract. */
    private void showInstallCard(ArchvowProjectReadiness.Status status) {
        boolean offered = !installReason.isBlank() && !status.root().isBlank() && new File(status.root()).isDirectory();
        ArchvowInstall.Offer offer = offered ? installOffer(status.root()) : null;
        welcomeInstall.setVisible(offered);
        welcomeInstall.setEnabled(offer != null && !offer.python().isEmpty() && !installing);
        welcomeInstallCommand.setVisible(offered);
        if (offer != null) {
            String text = ArchvowInstall.explain(offer, ArchvowI18n.isFrench(project));
            welcomeInstallCommand.setText("<html>" + escape(text) + (offer.display().isEmpty() ? "" : "<br>" + ArchvowTerminal.html(offer.display())) + "</html>");
            welcomeInstall.setToolTipText(offer.display().isEmpty() ? null : offer.display());
        }
        if (!offered) welcomeConfirmation.hideConfirmation();
    }

    /** What "Install archvow" would run here: the project's venv, the IDE's interpreter, then the setting. */
    private ArchvowInstall.Offer installOffer(String root) {
        ArchvowSettings.Values s = ArchvowSettings.get(project).getState();
        String interpreter = s.useProjectInterpreter ? ArchvowInterpreter.projectPython(project) : "";
        boolean windows = ArchvowWsl.windows();
        String python = ArchvowInstall.python(root, interpreter, s.python, f -> new File(f).isFile(), windows);
        return ArchvowInstall.offer(installReason, ArchvowVersion.KNOWN, python, root,
            ArchvowInstall.source(s.installFrom), ArchvowI18n.isFrench(project), windows);
    }

    /** "Install archvow": shows the command; pip runs only on "Confirm and run". */
    private void askInstall() {
        ArchvowProjectReadiness.Status status = ArchvowProjectReadiness.inspect(project.getBasePath());
        if (installReason.isBlank()) installReason = "missing";
        if (!welcomeText.isShowing()) showWelcome(status, "");
        ArchvowInstall.Offer offer = installOffer(status.root());
        if (offer.python().isEmpty() || installing) return;
        List<String> job = offer.command();
        welcomeConfirmation.showConfirmation(ArchvowI18n.text(project, "Install archvow " + offer.version(), "Installer archvow " + offer.version()),
            offer.display() + "\n\n" + ArchvowI18n.text(project,
                "pip installs archvow into this Python (" + offer.source() + "). The plugin then uses this Python.",
                "pip installe archvow dans ce Python (" + offer.source() + "). Le plugin utilise ensuite ce Python."),
            ArchvowI18n.isFrench(project), () -> runInstall(job, offer.python(), status.root()));
        revalidate(); repaint();
    }

    /** pip, confirmed: no shell, in the project folder, 10 minutes at most; then the plugin uses this Python. */
    private void runInstall(List<String> job, String python, String root) {
        installing = true;
        welcomeInstall.setEnabled(false);
        welcomeText.setText(htmlMessage("<b>⏳ " + escape(ArchvowI18n.text(project, "Installing", "Installation"))
            + "</b> · " + escape(ArchvowInstall.display(job))));
        new SwingWorker<String[], Void>() {
            @Override protected String[] doInBackground() {
                try {
                    ProcessBuilder builder = new ProcessBuilder(job).redirectErrorStream(true)
                        .directory(new File(ArchvowInstall.workingFolder(job, root)));
                    builder.environment().put("PIP_DISABLE_PIP_VERSION_CHECK", "1");
                    Process process = builder.start();
                    process.getOutputStream().close();
                    byte[] out = process.getInputStream().readAllBytes();
                    if (!process.waitFor(600, java.util.concurrent.TimeUnit.SECONDS)) {
                        process.destroyForcibly();
                        return new String[] {"1", "Timeout after 600 seconds / Délai dépassé après 600 secondes."};
                    }
                    return new String[] {Integer.toString(process.exitValue()), new String(out, java.nio.charset.StandardCharsets.UTF_8)};
                } catch (Exception error) {
                    return new String[] {"1", error.getClass().getSimpleName() + ": " + error.getMessage()};
                }
            }
            @Override protected void done() {
                installing = false;
                String[] result;
                try { result = get(); } catch (Exception e) { result = new String[] {"1", e.toString()}; }
                boolean ok = "0".equals(result[0]);
                String tail = shorten(result[1].strip(), 600);
                com.intellij.notification.NotificationGroupManager.getInstance().getNotificationGroup("Archvow")
                    .createNotification(ok
                        ? ArchvowI18n.text(project, "archvow installed into " + python, "archvow installé dans " + python)
                        : ArchvowI18n.text(project, "archvow was not installed (pip exit code " + result[0] + ")",
                            "archvow n'a pas été installé (code de sortie pip " + result[0] + ")"),
                        tail, ok ? com.intellij.notification.NotificationType.INFORMATION : com.intellij.notification.NotificationType.ERROR)
                    .notify(project);
                if (ok) {
                    ArchvowSettings.get(project).getState().python = python;
                    installReason = "";
                    versionChecked = false;
                    reload();
                } else {
                    showWelcome(ArchvowProjectReadiness.inspect(project.getBasePath()), tail);
                }
            }
        }.execute();
    }

    private JPanel overviewPanel() {
        JPanel page = new JPanel(new BorderLayout(5, 5));
        page.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        JPanel top = new JPanel(new BorderLayout(0, 4));
        JPanel overviewHeading = new JPanel(new BorderLayout(0, 5));
        featureBranchWarning.setOpaque(true);
        featureBranchWarning.setBorder(BorderFactory.createEmptyBorder(9, 12, 9, 12));
        featureBranchWarning.setBackground(darkTheme() ? new Color(81, 60, 25) : new Color(255, 245, 218));
        featureBranchWarning.setForeground(darkTheme() ? new Color(255, 205, 125) : new Color(137, 90, 22));
        featureBranchWarning.setVisible(false);
        overviewHeading.add(featureBranchWarning, BorderLayout.NORTH);
        firstStepsHolder.setOpaque(false);
        firstStepsHolder.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));
        overviewHeading.add(firstStepsHolder, BorderLayout.SOUTH);
        briefFolders.setForeground(new Color(107, 114, 128));
        briefFolders.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 6));
        briefFolders.setToolTipText(ArchvowI18n.text(project, "An architect changes them: archvow init <package> --force -a briefs=...",
            "Un architecte les change : archvow init <package> --force -a briefs=..."));
        // Brief folders are managed in Commands; Overview stays a project snapshot.
        top.add(overviewHeading, BorderLayout.NORTH);
        JPanel actionsAndOutput = new JPanel(new BorderLayout(0, 4));
        actionsAndOutput.add(buildToolbar(), BorderLayout.NORTH);
        overviewResultCard = inlineResultCard(overviewResult, () -> overviewResultCard.setVisible(false));
        actionsAndOutput.add(overviewResultCard, BorderLayout.SOUTH);
        top.add(actionsAndOutput, BorderLayout.SOUTH);

        summaryCard = createCard(overviewSummary, ArchvowI18n.text(project, "Architecture", "Architecture"));
        checkCard = createCard(scroll(diagnostics), ArchvowI18n.text(project, "Check & Gates", "Contrôles et validations"));

        // Stable layout: always show the secondary navigation tabs.
        // This is simpler and avoids the blank-state bug caused by reparenting panels
        // when switching between compact and wide modes.
        narrowOverviewTabs.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
        narrowOverviewTabs.addTab("Architecture", summaryCard);
        narrowOverviewTabs.addTab(ArchvowI18n.text(project,"Check & Gates","Contrôles et validations"), checkCard);
        // Briefs (0.16.0, archvow 1.20): every brief section by section - used, not used yet, changed since
        briefsPane.addHyperlinkListener(event -> {
            if (event.getEventType() != HyperlinkEvent.EventType.ACTIVATED) return;
            String link = event.getDescription();
            String brief = ArchvowBriefs.briefOf(link);
            if (!brief.isBlank()) { openBrief(brief); return; }
            String draft = ArchvowBriefs.linkRef(link, ArchvowBriefs.DRAFT_LINK);
            if (!draft.isBlank()) { openSkill("draft-spec", Map.of("brief", draft)); return; }
            String accept = ArchvowBriefs.linkRef(link, ArchvowBriefs.ACCEPT_LINK);
            if (!accept.isBlank()) { openCommand("brief accept", Map.of("brief", accept)); return; }
            ArchvowBriefs.AddFolder add = ArchvowBriefs.initOf(link);
            if (add != null) openCommand("init", add.values());
        });
        // Briefs remain accessible from Commands and their dedicated actions.
        overviewCenter = new JPanel(new BorderLayout());
        // Only the architecture summary is shown in Overview; no component inventory or duplicate sub-tabs.
        overviewSummary.setSummaryOnly(true);
        overviewSummary.onOpenBrowser(() -> tabs.setSelectedIndex(1));
        // A compact two-row overview, identical in purpose to the VS Code sidebar.
        // The detailed architecture/check tables live in Components and the editor, respectively.
        JPanel overviewRows = new JPanel();
        overviewRows.setOpaque(false);
        overviewRows.setLayout(new BoxLayout(overviewRows, BoxLayout.Y_AXIS));
        // 0.25.0: Architecture counts its own items, Checks shows the first finding (ArchvowOverviewCards)
        for (JPanel holder : List.of(architectureHolder, checksHolder)) {
            holder.setOpaque(false);
            holder.setAlignmentX(0f);
            overviewRows.add(holder);
            overviewRows.add(Box.createVerticalStrut(12));
        }
        JPanel packs = overviewSummaryRow("Packs", overviewPacksCounts,
            ArchvowI18n.text(project, "Open Packs →", "Voir les packs →"), this::openPacks);
        packs.setAlignmentX(0f);  // 0.25.1: every row on the same left edge (a row at 0.5 pushed the cards right)
        overviewRows.add(packs);
        // 0.25.1: the warning, First steps, the commands and the cards scroll together when they are taller than
        // the tool window; the language and settings stay at the bottom
        ArchvowOverviewCards.Page content = new ArchvowOverviewCards.Page(new BorderLayout(0, 8));
        content.add(top, BorderLayout.NORTH);
        overviewCenter.add(overviewRows, BorderLayout.NORTH);
        content.add(overviewCenter, BorderLayout.CENTER);
        JScrollPane scrolled = new JScrollPane(content, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrolled.setBorder(BorderFactory.createEmptyBorder());
        scrolled.getVerticalScrollBar().setUnitIncrement(16);
        scrolled.setOpaque(false);
        scrolled.getViewport().setOpaque(false);
        page.add(scrolled, BorderLayout.CENTER);
        page.add(settingsHintPanel(), BorderLayout.SOUTH);
        return page;
    }

    /** The Packs card (0.25.0: no icon, as the Architecture and Checks cards of ArchvowOverviewCards). */
    private JPanel overviewSummaryRow(String title, JLabel counts, String linkText, Runnable open) {
        JPanel card = new JPanel(new BorderLayout(10, 0));
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(borderColor(), 1, true),
            BorderFactory.createEmptyBorder(10, 12, 10, 12)));
        JPanel copy = new JPanel();
        copy.setOpaque(false);
        copy.setLayout(new BoxLayout(copy, BoxLayout.Y_AXIS));
        JLabel heading = new JLabel(title);
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, 14f));
        copy.add(heading);
        counts.setForeground(mutedTextColor());
        counts.setFont(counts.getFont().deriveFont(12f));
        copy.add(counts);
        card.add(copy, BorderLayout.CENTER);
        JButton link = new JButton(linkText);
        link.setForeground(darkTheme() ? new Color(235, 195, 105) : new Color(29, 99, 205));
        link.setContentAreaFilled(false);
        link.setBorderPainted(false);
        link.setFocusPainted(false);
        link.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        link.setToolTipText(linkText);
        link.addActionListener(e -> open.run());
        card.add(link, BorderLayout.EAST);
        // Clicking anywhere in the row is equivalent to using its explicit link.
        java.awt.event.MouseAdapter activateRow = new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) { open.run(); }
        };
        for (Component target : List.of(card, copy, heading, counts)) {
            target.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            target.addMouseListener(activateRow);
        }
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 82));
        return card;
    }

    /**
     * Keep overview navigation stable in all sizes.
     * Responsiveness is now cosmetic only: we hide long labels and reduce visual density,
     * but we no longer move panels between containers.
     */
    private void adaptOverviewLayout() {
        if (!isDisplayable()) return;
        int width = getWidth();
        if (width <= 0) return;
        boolean detail = role == Role.DETAIL; // the editor tab names what it shows; the language is the tool window's
        if (brandSubtitle != null) brandSubtitle.setVisible(!detail && width >= 850);
        if (brandTitle != null) brandTitle.setFont(brandTitle.getFont().deriveFont(Font.BOLD, width < 510 ? 16f : 23f));
        logoLabel.setIcon(loadLogoIcon(width < 510 ? 32 : 48, width < 510 ? 32 : 48));
        if (languageLabel != null) languageLabel.setVisible(!detail);
        languageMode.setVisible(!detail);
        revalidate();
        repaint();
    }

    /** The project's commands (0.25.0): Next and Check named, More with the others - as VS Code's projectToolbar(). */
    private JPanel buildToolbar() {
        JPanel bar = new JPanel(new BorderLayout(8, 4));
        bar.setBorder(BorderFactory.createEmptyBorder(6, 0, 6, 0));
        List<ArchvowOverviewCards.MenuCommand> more = List.of(
            new ArchvowOverviewCards.MenuCommand("health", "Doctor", () -> executeDirect("doctor", List.of("doctor", "--json"), null, false)),
            new ArchvowOverviewCards.MenuCommand("search", "Audit", () -> executeDirect("audit", List.of("audit", "--json"), null, false)),
            new ArchvowOverviewCards.MenuCommand("folder", ArchvowI18n.text(project, "Briefs", "Briefs"), this::openBriefsDetails),
            new ArchvowOverviewCards.MenuCommand("list", "Budget / Next", () -> {
                String ref = askBase(ArchvowI18n.text(project, "Compare this branch's change with:", "Comparer le changement de cette branche avec :"));
                if (ref != null) executeDirect("next --base", List.of("next", "--base", ref, "--json"), null, false);
            }),
            new ArchvowOverviewCards.MenuCommand("compare", "Diff", () ->
                openCommand("diff", branch.getText().isBlank() ? Map.of() : Map.of("base", branch.getText().trim()))),
            new ArchvowOverviewCards.MenuCommand("spark", "AI review", () ->
                openCommand("review", branch.getText().isBlank() ? Map.of() : Map.of("base", branch.getText().trim()))));
        bar.add(ArchvowOverviewCards.toolbar(() -> executeDirect("next", List.of("next", "--json"), null, false),
            this::runFullCheck, more, ArchvowI18n.isFrench(project), null), BorderLayout.CENTER);
        gitBranchLabel.setToolTipText(ArchvowI18n.text(project, "Current Git branch", "Branche Git courante"));
        gitBranchLabel.setForeground(mutedTextColor());
        gitBranchLabel.setBorder(BorderFactory.createEmptyBorder(3, 5, 2, 5));
        return bar;
    }

    private void chooseComparisonBase() {
        String value = askBase(ArchvowI18n.text(project, "Git comparison base (for Diff/Review/Next):", "Base Git de comparaison (pour Diff/Review/Next) :"));
        if (value == null) return;
        baseTouched = true;
        branch.setText(value);
        comparisonBaseButton.setText(baseLabel());
    }

    private static void addMenuItem(JPopupMenu menu, String label, Runnable command) {
        JMenuItem item = new JMenuItem(label);
        item.addActionListener(e -> command.run());
        menu.add(item);
    }

    private JComponent quickTreatmentPanel() {
        otherPendingList.setCellRenderer(new QuickItemRenderer());
        otherPendingList.setFixedCellHeight(72);
        otherPendingList.setVisibleRowCount(5);
        otherResolvedList.setCellRenderer(new QuickItemRenderer());
        otherResolvedList.setFixedCellHeight(72);
        otherResolvedList.setVisibleRowCount(5);
        otherPendingList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                if (quickTabs.getSelectedIndex() == 0) {
                    selectedQuickItem = otherPendingList.getSelectedValue();
                    showQuickItem(selectedQuickItem);
                }
            }
        });
        otherResolvedList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                if (quickTabs.getSelectedIndex() == 1) {
                    selectedQuickItem = otherResolvedList.getSelectedValue();
                    showQuickItem(selectedQuickItem);
                }
            }
        });

        otherPendingList.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) handleQuickPrimaryAction();
            }
        });
        otherResolvedList.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) handleQuickPrimaryAction();
            }
        });
        quickTabs.addChangeListener(e -> {
            QuickItem item = quickTabs.getSelectedIndex() == 0 ? otherPendingList.getSelectedValue() : otherResolvedList.getSelectedValue();
            selectedQuickItem = item;
            showQuickItem(item);
        });
        quickTabs.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
        quickTabs.addTab("Pending", scroll(otherPendingList));
        quickTabs.addTab("Resolved", scroll(otherResolvedList));
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        quickPrimary.addActionListener(e -> handleQuickPrimaryAction());
        quickSecondary.addActionListener(e -> handleQuickSecondaryAction());
        actions.add(quickPrimary);
        actions.add(quickSecondary);

        quickSelectionArea = new JPanel(new BorderLayout(0, 6));
        JScrollPane itemDetails = scroll(quickDetails);
        itemDetails.setPreferredSize(new Dimension(160, 145));
        labelDetails(itemDetails);
        JPanel selectedDetails = new JPanel(new BorderLayout(0, 5));
        selectedDetails.add(itemDetails, BorderLayout.CENTER);
        selectedDetails.add(actions, BorderLayout.SOUTH);
        quickResultCard = inlineResultCard(quickResult, () -> quickResultCard.setVisible(false));
        quickSelectionArea.add(selectedDetails, BorderLayout.NORTH);
        quickSelectionArea.add(quickResultCard, BorderLayout.CENTER);
        quickSelectionArea.setVisible(false); // No blank "No item selected" panel
        JPanel content = new JPanel(new BorderLayout(0, 7));
        JLabel explanation = new JLabel(ArchvowI18n.text(project,
            "Checks, unclassified and project-wide issues; no component assigned unless verified.",
            "Contrôles, éléments non classés et problèmes globaux ; aucun composant attribué sans preuve."));
        explanation.setForeground(mutedTextColor());
        content.add(explanation, BorderLayout.NORTH);
        content.add(quickTabs, BorderLayout.CENTER);
        content.add(quickSelectionArea, BorderLayout.SOUTH);
        return createCard(content, ArchvowI18n.text(project,
            "Other", "Autres"));
    }

    private JComponent componentOtherPanel() {
        componentOtherPendingList.setCellRenderer(new QuickItemRenderer());
        componentOtherPendingList.setFixedCellHeight(72);
        componentOtherPendingList.setVisibleRowCount(5);
        componentOtherResolvedList.setCellRenderer(new QuickItemRenderer());
        componentOtherResolvedList.setFixedCellHeight(72);
        componentOtherResolvedList.setVisibleRowCount(5);
        componentOtherPendingList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && componentOtherTabs.getSelectedIndex() == 0) {
                selectedQuickItem = componentOtherPendingList.getSelectedValue();
                showComponentOtherItem(selectedQuickItem);
            }
        });
        componentOtherResolvedList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && componentOtherTabs.getSelectedIndex() == 1) {
                selectedQuickItem = componentOtherResolvedList.getSelectedValue();
                showComponentOtherItem(selectedQuickItem);
            }
        });
        java.awt.event.MouseAdapter opener = new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) handleQuickPrimaryAction();
            }
        };
        componentOtherPendingList.addMouseListener(opener);
        componentOtherResolvedList.addMouseListener(opener);
        componentOtherTabs.addChangeListener(e -> {
            QuickItem item = componentOtherTabs.getSelectedIndex() == 0
                ? componentOtherPendingList.getSelectedValue() : componentOtherResolvedList.getSelectedValue();
            selectedQuickItem = item;
            showComponentOtherItem(item);
        });
        componentOtherTabs.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
        componentOtherTabs.addTab("Pending", scroll(componentOtherPendingList));
        componentOtherTabs.addTab("Resolved", scroll(componentOtherResolvedList));
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        componentOtherPrimary.addActionListener(e -> handleQuickPrimaryAction());
        componentOtherSecondary.addActionListener(e -> handleQuickSecondaryAction());
        actions.add(componentOtherPrimary);
        actions.add(componentOtherSecondary);

        JScrollPane itemDetails = scroll(componentOtherDetails);
        itemDetails.setPreferredSize(new Dimension(160, 145));
        labelDetails(itemDetails);
        JPanel selectedDetails = new JPanel(new BorderLayout(0, 5));
        selectedDetails.add(itemDetails, BorderLayout.CENTER);
        selectedDetails.add(actions, BorderLayout.SOUTH);
        JPanel content = new JPanel(new BorderLayout(0, 7));
        JLabel explanation = new JLabel(ArchvowI18n.text(project,
            "Only unclassified items from this component. Project checks stay in Check & Gates.",
            "Éléments non classés de ce composant uniquement. Les contrôles globaux restent dans Contrôles et validations."));
        explanation.setForeground(mutedTextColor());
        content.add(explanation, BorderLayout.NORTH);
        content.add(componentOtherTabs, BorderLayout.CENTER);
        content.add(selectedDetails, BorderLayout.SOUTH);
        return createCard(content, ArchvowI18n.text(project,
            "Other", "Autres"));
    }

    private JPanel inlineResultCard(JEditorPane pane, Runnable close) {
        JPanel outer = new JPanel(new BorderLayout(0, 7)) {
            @Override public void updateUI() {
                super.updateUI();
                applyResultCardPalette(this);
            }
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                paintResultCardSurface(g, this);
            }
        };
        applyResultCardPalette(outer);
        JLabel title = new JLabel(ArchvowI18n.text(project, "Command result", "Résultat de la commande"));
        title.setIcon(ArchvowUiIcons.iconFor(title, "terminal"));
        title.setIconTextGap(7);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 12f));
        JPanel controls = new JPanel(new ArchvowWrapLayout(FlowLayout.RIGHT, 5, 1));
        controls.setOpaque(false);
        JToggleButton rawToggle = resultToggleButton(ArchvowI18n.text(project, "Raw output", "Sortie brute"));
        rawToggle.setEnabled(false);
        rawToggle.setToolTipText(ArchvowI18n.text(project,
            "Show the original Archvow output for technical inspection",
            "Afficher la sortie originale d'Archvow pour inspection technique"));
        pane.putClientProperty("archvow.rawToggle", rawToggle);
        rawToggle.addActionListener(e -> showStoredCommandResult(pane));
        controls.add(rawToggle);
        JButton copy = resultActionButton(ArchvowI18n.text(project, "Copy", "Copier"), () -> {
            Object stored = pane.getClientProperty("archvow.resultOutput");
            if (stored instanceof String source && !source.isBlank()) {
                java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
                    new java.awt.datatransfer.StringSelection(source), null);
            }
        });
        copy.setToolTipText(ArchvowI18n.text(project,
            "Copy original output (including the complete AI prompt)",
            "Copier la sortie originale (y compris les instructions IA complètes)"));
        controls.add(copy);
        JButton dismiss = resultActionButton("×", () -> {
            if (resultExpansion != null && resultExpansion.isExpanded()) collapseExpandedResult();
            close.run();
        });
        dismiss.setToolTipText(ArchvowI18n.text(project, "Close result", "Fermer le résultat"));
        JScrollPane scroll = scroll(pane);
        // The result canvas sits inside the quiet surface without a second dark outline.
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setViewportBorder(null);
        scroll.getViewport().setBackground(darkTheme() ? new Color(39, 43, 51) : new Color(249, 251, 254));
        scroll.setPreferredSize(new Dimension(200, RESULT_HEIGHT));
        // Drag changes inline height; Expand instead uses the entire workspace.
        JToggleButton expand = resultToggleButton(ArchvowI18n.text(project, "Expand", "Agrandir"));
        expand.setToolTipText(ArchvowI18n.text(project, "Show the result across the entire editor. Reduce to return to the form.",
            "Afficher le résultat sur toute la largeur de l’éditeur. Réduire pour revenir au formulaire."));
        expand.addActionListener(e -> {
            if (expand.isSelected()) {
                // A taller card in BorderLayout.SOUTH is clipped above the command editor;
                // in the component view it remains confined to the right-hand column.
                // Move the actual result into the full-width workspace until Reduce.
                if (resultExpansion != null && resultExpansion.expand(outer)) {
                    expandedResultButton = expand;
                    Object grip = outer.getClientProperty("archvow.resultGrip");
                    if (grip instanceof JComponent handle) handle.setVisible(false);
                    expand.setText(ArchvowI18n.text(project, "Reduce", "Réduire"));
                } else expand.setSelected(false);
            } else collapseExpandedResult();
        });
        controls.add(expand, 0);
        controls.add(dismiss);
        outer.add(new ArchvowResultHeader(title, controls), BorderLayout.NORTH);
        outer.add(scroll, BorderLayout.CENTER);
        JComponent grip = resultGrip(scroll);
        outer.putClientProperty("archvow.resultGrip", grip);
        outer.add(grip, BorderLayout.SOUTH);
        outer.setVisible(false);
        return outer;
    }

    /** The height of a result card when it opens (it used to stay at this height: 225 px). */
    static final int RESULT_HEIGHT = 225;

    /** Exit full-width presentation before navigation, Close, or a second expansion. */
    private void collapseExpandedResult() {
        JComponent restored = resultExpansion == null ? null : resultExpansion.collapse();
        if (restored != null) {
            Object grip = restored.getClientProperty("archvow.resultGrip");
            if (grip instanceof JComponent handle) handle.setVisible(true);
        }
        if (expandedResultButton != null) {
            expandedResultButton.setSelected(false);
            expandedResultButton.setText(ArchvowI18n.text(project, "Expand", "Agrandir"));
            expandedResultButton = null;
        }
    }

    private void showWorkspace() {
        collapseExpandedResult();
        rootLayout.show(rootCards, "workspace");
    }

    private static void resizeResult(JScrollPane scroll, int height) {
        scroll.setPreferredSize(new Dimension(scroll.getPreferredSize().width, Math.max(80, height)));
        scroll.revalidate();
        Container parent = scroll.getParent();
        while (parent != null && !(parent instanceof JScrollPane) && !(parent instanceof Window)) {
            parent.revalidate();
            parent = parent.getParent();
        }
        scroll.repaint();
    }

    /** The bar under a result: drag it to make the result taller or shorter. */
    static JComponent resultGrip(JScrollPane scroll) {
        JComponent grip = new JComponent() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setColor(UIManager.getColor("Label.disabledForeground") != null ? UIManager.getColor("Label.disabledForeground") : Color.GRAY);
                int x = getWidth() / 2 - 18;
                g2.fillRoundRect(x, getHeight() / 2 - 1, 36, 3, 3, 3);
                g2.dispose();
            }
        };
        grip.setPreferredSize(new Dimension(10, 9));
        grip.setCursor(Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR));
        grip.setToolTipText("Drag to make the result taller or shorter / Tirer pour agrandir ou réduire le résultat");
        java.awt.event.MouseAdapter drag = new java.awt.event.MouseAdapter() {
            private int startY, startHeight;
            @Override public void mousePressed(java.awt.event.MouseEvent e) {
                startY = e.getYOnScreen();
                startHeight = scroll.getHeight() > 0 ? scroll.getHeight() : scroll.getPreferredSize().height;
            }
            @Override public void mouseDragged(java.awt.event.MouseEvent e) {
                resizeResult(scroll, startHeight + e.getYOnScreen() - startY);
            }
        };
        grip.addMouseListener(drag);
        grip.addMouseMotionListener(drag);
        return grip;
    }

    /** Keep the result in context; readers can ask for raw JSON, never see it by default. */
    private void storeCommandResult(JEditorPane destination, String label, ArchvowCli.Result result) {
        if (destination == commandResult) showFormAlerts(result);  // design A: the refusal in place
        destination.putClientProperty("archvow.resultLabel", label);
        destination.putClientProperty("archvow.resultOutput", result.output);
        destination.putClientProperty("archvow.resultExit", result.exitCode);
        destination.putClientProperty("archvow.resultInterrupted", result.interrupted);
        destination.putClientProperty("archvow.resultArguments", String.join(" ", result.arguments));
        Object control = destination.getClientProperty("archvow.rawToggle");
        if (control instanceof JToggleButton toggle) {
            toggle.setSelected(false);
            toggle.setEnabled(result.output != null && !result.output.isBlank());
            toggle.setText(ArchvowI18n.text(project, "Raw output", "Sortie brute"));
        }
        showStoredCommandResult(destination);
    }

    private void showStoredCommandResult(JEditorPane destination) {
        Object output = destination.getClientProperty("archvow.resultOutput");
        if (!(output instanceof String raw)) return;
        String label = String.valueOf(destination.getClientProperty("archvow.resultLabel"));
        Object code = destination.getClientProperty("archvow.resultExit");
        int exit = code instanceof Integer n ? n : -1;
        boolean interrupted = Boolean.TRUE.equals(destination.getClientProperty("archvow.resultInterrupted"));
        String arguments = String.valueOf(destination.getClientProperty("archvow.resultArguments"));
        Object control = destination.getClientProperty("archvow.rawToggle");
        boolean original = control instanceof JToggleButton toggle && toggle.isSelected();
        destination.setText(original
            ? ArchvowResultFormatter.renderRaw(raw, ArchvowI18n.isFrench(project), darkTheme())
            : ArchvowBriefs.withAddFolder(ArchvowResultFormatter.render(label, exit, raw, interrupted,
                arguments, ArchvowI18n.isFrench(project), darkTheme()), raw, ArchvowI18n.isFrench(project)));
        destination.setCaretPosition(0);
    }

    private JComponent settingsHintPanel() {
        JPanel panel = new JPanel(new BorderLayout(6, 0));
        panel.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
        JLabel hint = new JLabel("Python: /path/to/venv/bin/python3");
        hint.setForeground(UIManager.getColor("Label.disabledForeground"));
        hint.setToolTipText("Only the Python executable path / Uniquement le chemin Python. Configure in Settings → Tools → Archvow.");
        panel.add(hint, BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        openSettingsButton.addActionListener(e -> ShowSettingsUtil.getInstance().showSettingsDialog(project, ArchvowSettingsConfigurable.class));
        testArchvowButton.addActionListener(e -> executeDirect("version", List.of("--version"), null, false));
        actions.add(openSettingsButton);
        panel.add(actions, BorderLayout.EAST);
        // the language, here rather than in the header (it cut the project's name in a narrow tool window)
        JPanel language = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        if (languageLabel != null) language.add(languageLabel);
        language.add(languageMode);
        panel.add(language, BorderLayout.NORTH);
        // The full Python help and test are available in Settings → Tools → Archvow.
        return panel;
    }

    private JComponent componentPanel() {
        componentsTree.setRootVisible(true);
        componentsTree.addTreeSelectionListener(event -> {
            if (rebuilding) return;
            Object nodeObject = componentsTree.getLastSelectedPathComponent();
            if (!(nodeObject instanceof DefaultMutableTreeNode node)) return;
            if (node.getUserObject() instanceof ArchvowTreeIcons.Node featureNode && "feature".equals(featureNode.kind())) {
                showFeature(featureNode.text());
                return;
            }
            if (!(node.getUserObject() instanceof NodeComponent comp)) return;
            if (role == Role.NAVIGATION) {
                // the tool window chooses; the component opens in the editor tab
                selectedComponent = comp;
                if (!syncing && constructed) ArchvowEditorTab.showComponent(project, comp.id);
                return;
            }
            // Never confirm an action prepared for a previously selected component.
            if (componentConfirmation != null && selectedComponent != null
                && !selectedComponent.id.equals(comp.id)) componentConfirmation.hideConfirmation();
            selectedComponent = comp;
            loadComponent(comp);
        });
        ArchvowOverflowBar actions = new ArchvowOverflowBar(
            ArchvowI18n.text(project, "More actions ▾", "Autres actions ▾"));
        // 0.17.2 (the user's order): AI, Add, Edit spec, Open code - what you ask the AI first, the one filled
        // button of the tab (the suggestion strip is gone: AI ▾ marks the suggested step); then what you add,
        // change, open. Accept shows before them on a component the AI created.
        aiAcceptButton = ArchvowSplitView.primary(ArchvowI18n.text(project, "Accept…", "Accepter…"));
        aiAcceptButton.setToolTipText(ArchvowI18n.text(project, "The AI created this component: accept it as the team's (archvow component accept)",
            "L'IA a créé ce composant : l'accepter comme celui de l'équipe (archvow component accept)"));
        aiAcceptButton.addActionListener(e -> withComponent(c -> openCommand("component accept", Map.of("component", c.id))));
        aiAcceptButton.setVisible(false);
        actions.addAction(aiAcceptButton);
        // user decision (0.17.5): "✦ Spec IA ▾" in French
        JButton aiSkills = ArchvowSplitView.primary(ArchvowI18n.text(project, "✦ AI spec ▾", "✦ Spec IA ▾"));
        languageRefreshers.add(() -> aiSkills.setText(ArchvowI18n.text(project, "✦ AI spec ▾", "✦ Spec IA ▾")));
        aiSkills.setToolTipText(ArchvowI18n.text(project, "What you ask the AI for this component: it adds, you decide",
            "Ce que tu demandes à l'IA pour ce composant : elle ajoute, tu décides"));
        aiSkills.addActionListener(e -> showAiMenu(aiSkills));
        actions.addAction(aiSkills);
        JButton add = new JButton(ArchvowI18n.text(project, "+ Add ▾", "+ Ajouter ▾"));
        add.putClientProperty(ArchvowUiIcons.NO_ICON, Boolean.TRUE);  // 0.17.3: its "+" says it, no ⊕ beside it
        add.setToolTipText(ArchvowI18n.text(project, "What you add yourself to this component", "Ce que tu ajoutes toi-même à ce composant"));
        add.addActionListener(e -> showAddMenu(add));
        actions.addAction(add);
        actions.addAction(tip(button(ArchvowI18n.text(project, "Edit spec", "Modifier la spec"),
            () -> withComponent(c -> openCommand("component edit", Map.of("component", c.id)))),
            "Change the description, ticket, signature, uses, group, owners or brief (archvow component edit). Decisions, questions and hazards stay as they are: review the ones the change touches.",
            "Changer la description, le ticket, la signature, les dépendances, le groupe, les responsables ou la brief (archvow component edit). Les décisions, questions et hazards restent tels quels : revoir ceux que le changement touche."));
        actions.addAction(tip(button(ArchvowI18n.text(project, "Open code", "Ouvrir le code"), this::openCode),
            "Open this component's Python file (and its test). Change the code there; save, then Check.",
            "Ouvrir le fichier Python de ce composant (et son test). Modifier le code là ; enregistrer, puis Vérifier."));

        JPanel detailCards = new JPanel(componentViewLayout);
        componentViewCards = detailCards;
        componentConfirmation = new ArchvowConfirmPanel();
        // Display every vow section that fits; only overflowing sections appear in More.
        // The overflow is measured from actual button widths, not fixed breakpoints.
        ArchvowCompactViews viewBar = new ArchvowCompactViews(this::selectComponentView, ArchvowI18n.isFrench(project));
        componentViewNav = viewBar;
        /* Explicit navigation buttons + CardLayout avoid a custom tab-strip UI and keep
           clicks reliable on all IDE themes, widths, and scale factors. */
        /* Decisions, Questions, Safety, Assumptions: the items as cards on the left, the selected one's
           answer panel on the right (ArchvowSplitView, stacked when the tab is narrow). When a component
           opens, each view starts on its first open item (renderComponent). */
        decisionsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        decisionsList.setCellRenderer(new ArchvowItemCards.Renderer());
        decisionsList.setFixedCellHeight(ArchvowItemCards.HEIGHT);
        decisionsList.addListSelectionListener(e -> { if (!e.getValueIsAdjusting()) showDecision(); });
        decisionsList.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) decisionAnswer.requestFocusInWindow();
            }
        });
        JPanel decisionsScroll = cardsScroll(decisionsList);
        answerField(decisionAnswer);
        decisionAnswerLabel = translated(new JLabel(), "Your answer or proposal", "Votre réponse ou proposition");
        decisionAnswerLabel.setFont(decisionAnswerLabel.getFont().deriveFont(Font.BOLD, 12f));
        JScrollPane answerScroll = scroll(decisionAnswer);
        answerScroll.setPreferredSize(new Dimension(150, 96));
        decideInline.setText(ArchvowI18n.text(project, "Save answer", "Enregistrer la réponse"));
        proposeInline.setText(ArchvowI18n.text(project, "Save as a proposal", "Enregistrer comme proposition"));
        proposeInline.setToolTipText(ArchvowI18n.text(project, "Recorded, not yet the answer (archvow decision propose)",
            "Enregistrée, pas encore la réponse (archvow decision propose)"));
        acceptInline.setText(ArchvowI18n.text(project, "Accept the AI's proposal", "Accepter la proposition de l'IA"));
        suggestedInline.setText(ArchvowI18n.text(project, "Use the suggestion", "Utiliser la suggestion"));
        decideInline.addActionListener(e -> submitInlineDecision("decision make"));
        proposeInline.addActionListener(e -> submitInlineDecision("decision propose"));
        acceptInline.addActionListener(e -> submitInlineDecision("decision accept"));
        suggestedInline.addActionListener(e -> fillSuggestedAnswer());
        removeQuestionInline.setText(ArchvowI18n.text(project, "Remove question", "Retirer la question"));
        removeQuestionInline.addActionListener(e -> withSelectedDecision("question remove"));
        keepQuestionInline.setText(ArchvowI18n.text(project, "Keep, answer later", "Garder, répondre plus tard"));
        keepQuestionInline.setToolTipText(ArchvowI18n.text(project, "The question stays open, as the team's own (archvow question accept)",
            "La question reste ouverte, comme celle de l'équipe (archvow question accept)"));
        keepQuestionInline.addActionListener(e -> withSelectedDecision("question accept"));
        // 0.17.2: Save as a proposal is a real button beside Save answer (the ▾ menu of 0.17.0 hid it)
        JPanel decisionButtons = answerButtons(List.of(proposeInline), cancelButton(decisionAnswer, this::showDecision), decideInline);
        // the other ways are words where they belong: Use the suggestion, Accept the AI's proposal, Keep / Remove
        // the AI's question - above the field, only those that apply
        JPanel decisionLinks = ArchvowSplitView.linkRow(List.of(suggestedInline, acceptInline, keepQuestionInline, removeQuestionInline));
        decisionConfirmSlot = new JPanel(new BorderLayout());
        decisionConfirmSlot.setOpaque(false);
        JPanel decisionEditor = ArchvowAnswerEditor.create(decisionAnswerLabel, answerScroll,
            decisionButtons, decisionConfirmSlot);
        decisionInlineResultCard = inlineResultCard(decisionInlineResult, () -> decisionInlineResultCard.setVisible(false));
        decisionInlineResultCard.setVisible(false);
        // The question, the field and its buttons stay together; the full text stays selectable above the field.
        // No fixed height on the editor: it grows when the inline confirmation appears.
        addComponentView(viewBar, detailCards, 0, ArchvowI18n.text(project, "Decisions", "Décisions"),
            ArchvowSplitView.create(decisionsList, decisionsScroll, compactSections(answerHeading(decisionDetails), decisionLinks, decisionChoices, columnAnswerPanel, decisionEditor,
                decisionInlineResultCard)));

        // Personal, operational and compliance questions (P1/O2/C1) are also
        // answerable directly, without switching to the Commands tab.
        questionList.setCellRenderer(new ArchvowItemCards.Renderer());
        questionList.setFixedCellHeight(ArchvowItemCards.HEIGHT);
        questionList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        questionList.addListSelectionListener(e -> { if (!e.getValueIsAdjusting()) showQuestion(); });
        questionList.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) questionAnswer.requestFocusInWindow();
            }
        });
        JPanel questionsScroll = cardsScroll(questionList);
        answerField(questionAnswer);
        JScrollPane qAnswerScroll = scroll(questionAnswer);
        qAnswerScroll.setPreferredSize(new Dimension(150, 96));
        questionConfirmSlot = new JPanel(new BorderLayout());
        questionConfirmSlot.setOpaque(false);
        JButton saveQuestion = ArchvowSplitView.primary(ArchvowI18n.text(project, "Save answer", "Enregistrer la réponse"));
        saveQuestion.addActionListener(e -> submitInlineQuestion());
        JPanel questionEditor = ArchvowAnswerEditor.create(
            new JLabel(ArchvowI18n.text(project, "Your answer", "Votre réponse")), qAnswerScroll,
            answerButtons(List.of(), cancelButton(questionAnswer, this::showQuestion), saveQuestion), questionConfirmSlot);
        questionInlineResultCard = inlineResultCard(questionInlineResult, () -> questionInlineResultCard.setVisible(false));
        questionInlineResultCard.setVisible(false);
        addComponentView(viewBar, detailCards, 1, "Questions",
            ArchvowSplitView.create(questionList, questionsScroll, compactSections(answerHeading(questionDetails), questionEditor,
                questionInlineResultCard)));

        safetyList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        safetyList.setCellRenderer(new ArchvowItemCards.Renderer());
        safetyList.setFixedCellHeight(ArchvowItemCards.HEIGHT);
        safetyList.addListSelectionListener(e -> { if (!e.getValueIsAdjusting()) showSafety(); });
        JPanel safetyScroll = cardsScroll(safetyList);
        answerField(safetyInput);
        JPanel safetyHeading = new JPanel(new BorderLayout(6, 0));
        safetyInputLabel.setText(ArchvowI18n.text(project, "Hazard description or reason", "Hazard ou justification"));
        safetyInputLabel.setFont(safetyInputLabel.getFont().deriveFont(Font.BOLD, 12f));
        safetyHeading.add(safetyInputLabel, BorderLayout.WEST);
        safetySeverityBox.add(new JLabel(ArchvowI18n.text(project, "Severity", "Sévérité")));
        safetySeverityBox.add(safetySeverity);
        safetyHeading.add(safetySeverityBox, BorderLayout.EAST);
        JScrollPane safetyInputScroll = scroll(safetyInput);
        safetyInputScroll.setPreferredSize(new Dimension(150, 96));
        hazardInline.setText(ArchvowI18n.text(project, "Declare hazard", "Déclarer un hazard"));
        dismissInline.setText(ArchvowI18n.text(project, "Not applicable", "Non applicable"));
        hazardInline.addActionListener(e -> submitInlineSafety(false));
        dismissInline.addActionListener(e -> submitInlineSafety(true));
        safetySuggestedButton.setText(ArchvowI18n.text(project, "Use proposed hazard + invariant", "Utiliser hazard + invariant proposés"));
        safetySuggestedButton.addActionListener(e -> submitSuggestedSafety());
        safetyInvariantButton.setText(ArchvowI18n.text(project, "Add required invariant", "Ajouter l'invariant requis"));
        safetyInvariantButton.addActionListener(e -> openSelectedSafetyInvariant());
        JButton addInvariant = button(ArchvowI18n.text(project,"Add invariant…","Ajouter un invariant…"),
            this::openAnySelectedSafetyInvariant);
        removeHazardButton.setText(ArchvowI18n.text(project, "Remove hazard…", "Supprimer un hazard…"));
        removeHazardButton.addActionListener(e -> removeSelectedSafetyArtifact(false));
        removeInvariantButton.setText(ArchvowI18n.text(project, "Remove invariant…", "Supprimer un invariant…"));
        removeInvariantButton.addActionListener(e -> removeSelectedSafetyArtifact(true));
        changeHazardButton.setText(ArchvowI18n.text(project, "Change hazard", "Modifier le hazard"));
        changeHazardButton.addActionListener(e -> startHazardChange());
        keepHazardButton.setText(ArchvowI18n.text(project, "Keep it as it is", "Le garder tel quel"));
        keepHazardButton.addActionListener(e -> { changingHazard = null; showSafety(); });
        JButton safetyCancel = cancelButton(safetyInput, this::showSafety);
        JPanel safetyActions = answerButtons(List.of(dismissInline, safetySuggestedButton, keepHazardButton), safetyCancel, hazardInline);
        // what can be done on a question already answered: under the field, apart; the row wraps (a plain
        // FlowLayout hid the buttons that did not fit on its one line)
        JPanel safetyMore = new JPanel(new ArchvowWrapLayout(FlowLayout.LEFT, 5, 2));
        keepAiHazardButton.setText(ArchvowI18n.text(project, "Keep the AI's hazard", "Garder le hazard de l'IA"));
        keepAiHazardButton.setToolTipText(ArchvowI18n.text(project, "Keep this hazard and the invariants the AI added to prevent it (archvow hazard accept)",
            "Garder ce hazard et les invariants que l'IA a ajoutés pour l'empêcher (archvow hazard accept)"));
        keepAiHazardButton.addActionListener(e -> keepAiHazard());
        for (JButton more : List.of(keepAiHazardButton, changeHazardButton, removeHazardButton, addInvariant, removeInvariantButton, safetyInvariantButton))
            safetyMore.add(more);
        safetyMore.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, borderColor()),
            BorderFactory.createEmptyBorder(4, 0, 0, 0)));
        JPanel safetyButtons = new JPanel(new BorderLayout(0, 6));
        safetyButtons.add(safetyActions, BorderLayout.NORTH);
        safetyButtons.add(safetyMore, BorderLayout.SOUTH);
        safetyConfirmSlot = new JPanel(new BorderLayout());
        safetyConfirmSlot.setOpaque(false);
        safetyEditorParts = List.of(safetyHeading, safetyInputScroll, safetyActions);
        JPanel safetyEditor = ArchvowAnswerEditor.create(safetyHeading, safetyInputScroll, safetyButtons, safetyConfirmSlot);
        safetyInlineResultCard = inlineResultCard(safetyInlineResult, () -> safetyInlineResultCard.setVisible(false));
        safetyInlineResultCard.setVisible(false);
        addComponentView(viewBar, detailCards, 2, ArchvowI18n.text(project, "Safety", "Sécurité"),
            ArchvowSplitView.create(safetyList, safetyScroll, compactSections(answerHeading(safetyDetails), safetyEditor,
                safetyInlineResultCard)));

        assumptionList.setCellRenderer(new ArchvowItemCards.Renderer());
        assumptionList.setFixedCellHeight(ArchvowItemCards.HEIGHT);
        assumptionList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        assumptionList.addListSelectionListener(e -> { if (!e.getValueIsAdjusting()) showAssumption(); });
        JPanel assumptionItems = new JPanel(new BorderLayout(0, 6));
        assumptionItems.add(cardsScroll(assumptionList), BorderLayout.CENTER);
        JPanel addAssumption = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        addAssumption.add(button(ArchvowI18n.text(project, "+ Assumption", "+ Hypothèse"),
            () -> withComponent(c -> openCommand("assumption add", Map.of("component", c.id)))));
        assumptionItems.add(addAssumption, BorderLayout.SOUTH);
        assumptionAcceptButton.setText(ArchvowI18n.text(project, "Accept", "Accepter"));
        assumptionRejectButton.setText(ArchvowI18n.text(project, "Reject", "Rejeter"));
        assumptionAcceptButton.addActionListener(e -> reviewSelectedAssumption("assumption accept"));
        assumptionRejectButton.addActionListener(e -> reviewSelectedAssumption("assumption reject"));
        JPanel assumptionActions = answerButtons(List.of(), assumptionRejectButton, assumptionAcceptButton);
        addComponentView(viewBar, detailCards, 3, ArchvowI18n.text(project, "Assumptions", "Hypothèses"),
            ArchvowSplitView.create(assumptionList, assumptionItems, compactSections(answerHeading(assumptionDetails), assumptionActions)));
        addComponentView(viewBar, detailCards, 4, ArchvowI18n.text(project, "Rules", "Règles"), rulesPanel());
        addComponentView(viewBar, detailCards, 5, ArchvowI18n.text(project, "Tasks", "Tâches"), dataPanel());
        addComponentView(viewBar, detailCards, 6, "Spec", specPanel());
        // Only genuinely unclassified or project-wide records live here.
        quickCard = quickTreatmentPanel();
        componentOtherCard = componentOtherPanel();
        addComponentView(viewBar, detailCards, 7,
            ArchvowI18n.text(project, "Other", "Autres"), componentOtherCard);
        examplesView = new ArchvowExamplesView(new ArchvowExamplesView.Actions() {
            @Override public void openFile(String relative, int line) { openViolationFile(relative, line); }
            @Override public void openBrief(String ref) { ArchvowPanel.this.openBrief(ref); }
            @Override public void copy(String text) {
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new java.awt.datatransfer.StringSelection(text), null);
                message(ArchvowI18n.text(project, "Copied.", "Copié."));
            }
            @Override public void record(String component) { openCommand("example add", Map.of("component", component)); }
        });
        addComponentView(viewBar, detailCards, 8, ArchvowI18n.text(project, "Examples", "Exemples"), examplesView, 5);
        JComponent actionStrip = actions;
        componentActions = actionStrip;
        actionStrip.setPreferredSize(new Dimension(0, 39));
        JPanel rightTop = new JPanel(new BorderLayout(0, 5));
        actionsHeading = (JPanel) sectionHeader(ArchvowI18n.text(project, "Actions", "Actions"),
            ArchvowI18n.text(project, "Available on this component", "Disponibles sur ce composant"));
        languageRefreshers.add(() -> {
            if (actionsHeading == null) return;
            Component[] children = actionsHeading.getComponents();
            for (Component child : children) if (child instanceof JLabel label) {
                boolean subtitle = BorderLayout.SOUTH.equals(((BorderLayout) actionsHeading.getLayout()).getConstraints(child));
                label.setText(subtitle ? ArchvowI18n.text(project, "Available on this component", "Disponibles sur ce composant") : "Actions");
            }
        });
        // the component's head over the whole width (name, open items, place, description, links), then its
        // actions; the "Actions" and "Vows" headings are gone (0.11.0), as on the VS Code page
        actionsHeading.setVisible(false);
        JPanel head = new JPanel(new BorderLayout()) {
            @Override public Dimension getPreferredSize() {
                int width = getParent() == null ? 0 : getParent().getWidth();
                if (width <= 0) return super.getPreferredSize();
                componentDetails.setSize(width, Short.MAX_VALUE);
                return new Dimension(width, componentDetails.getPreferredSize().height);
            }
        };
        componentDetails.setOpaque(false);
        componentDetails.setBorder(BorderFactory.createEmptyBorder(4, 6, 0, 6));
        head.setOpaque(false);
        head.add(componentDetails, BorderLayout.CENTER);
        JPanel headAndSuggestion = new JPanel(new BorderLayout(0, 4));
        headAndSuggestion.setOpaque(false);
        headAndSuggestion.add(head, BorderLayout.NORTH);
        suggestStrip.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(ArchvowMotion.accent(), 1, true),
            BorderFactory.createEmptyBorder(7, 10, 7, 10)));
        suggestStrip.add(suggestText, BorderLayout.CENTER);
        // 0.17.1: the button keeps its own size (BorderLayout.EAST stretched it to the strip's height and half its
        // width in a narrow tool window); under the text when the strip is narrow, on its right otherwise
        JPanel goHolder = new JPanel(new GridBagLayout());
        goHolder.setOpaque(false);
        goHolder.add(suggestGo);
        suggestStrip.add(goHolder, BorderLayout.EAST);
        suggestStrip.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override public void componentResized(java.awt.event.ComponentEvent e) {
                boolean narrow = suggestStrip.getWidth() < STRIP_SIDE_BY_SIDE;
                Object where = ((BorderLayout) suggestStrip.getLayout()).getConstraints(goHolder);
                if (narrow == BorderLayout.SOUTH.equals(where)) return;
                suggestStrip.remove(goHolder);
                goHolder.setLayout(narrow ? new FlowLayout(FlowLayout.LEFT, 0, 6) : new GridBagLayout());
                suggestStrip.add(goHolder, narrow ? BorderLayout.SOUTH : BorderLayout.EAST);
                suggestStrip.revalidate();
            }
        });
        suggestStrip.setVisible(false);  // 0.17.2: not shown - AI ▾ marks the suggested step
        rightTop.add(headAndSuggestion, BorderLayout.NORTH);
        rightTop.add(actionStrip, BorderLayout.SOUTH);
        JPanel viewsHeader = new JPanel(new BorderLayout(0, 3));
        // One vow per component; these sections also include checks and project issues.
        vowsLabel = translated(new JLabel(), "Vows", "Vows");
        vowsLabel.setFont(vowsLabel.getFont().deriveFont(Font.BOLD, 13f));
        vowsLabel.setVisible(false);
        viewsHeader.add(viewBar, BorderLayout.CENTER);
        // 0.17.0: All / Open / To review is a line of small words under the views (ArchvowListFilter), never
        // buttons beside them: the views choose what to look at, the line which of its items
        listFilterLine.setOpaque(false);
        listFilterLine.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, UIManager.getColor("Separator.foreground") == null ? Color.GRAY : UIManager.getColor("Separator.foreground")),
            BorderFactory.createEmptyBorder(1, 4, 4, 4)));
        componentPendingExplanation.setForeground(darkTheme() ? new Color(177, 187, 199) : new Color(94, 106, 120));
        componentPendingExplanation.setFont(componentPendingExplanation.getFont().deriveFont(11f));
        componentPendingExplanation.setBorder(BorderFactory.createEmptyBorder(2, 5, 2, 5));
        componentPendingExplanation.setVisible(false);
        JPanel underViews = new JPanel(new BorderLayout());
        underViews.setOpaque(false);
        underViews.add(listFilterLine, BorderLayout.NORTH);
        underViews.add(componentPendingExplanation, BorderLayout.SOUTH);
        viewsHeader.add(underViews, BorderLayout.SOUTH);
        JPanel right = new JPanel(new BorderLayout(0, 8));
        JPanel navigation = new JPanel(new BorderLayout(0, 9));
        navigation.add(rightTop, BorderLayout.NORTH);
        navigation.add(viewsHeader, BorderLayout.SOUTH);
        right.add(navigation, BorderLayout.NORTH);
        right.add(detailCards, BorderLayout.CENTER);
        componentResultCard = inlineResultCard(componentResult, () -> componentResultCard.setVisible(false));
        componentResultCard.setVisible(false);
        right.add(componentResultCard, BorderLayout.SOUTH);
        selectComponentView(0);
        JPanel componentBrowser = new JPanel(new BorderLayout(0, 3));
        JPanel filterRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 2));
        allComponentsFilter = button(ArchvowI18n.text(project, "All", "Tous"), () -> setComponentFilter("all"));
        pendingComponentsFilter = button(ArchvowI18n.text(project, "Pending", "En attente"), () -> setComponentFilter("pending"));
        resolvedComponentsFilter = button(ArchvowI18n.text(project, "Resolved", "Traités"), () -> setComponentFilter("resolved"));
        for (JButton filter : List.of(allComponentsFilter, pendingComponentsFilter, resolvedComponentsFilter)) {
            filter.setMargin(new Insets(3, 6, 3, 6));
            filterRow.add(filter);
        }
        // 0.15.0: the shortcut to component add, and on a layer (right-click) the types of that layer
        addComponentButton = button(ArchvowI18n.text(project, "Add component", "Ajouter un composant"), () -> openCommand("component add", Map.of()));
        addComponentButton.setIcon(ArchvowTreeIcons.icon("add", null));
        addComponentButton.setMargin(new Insets(3, 6, 3, 6));
        addComponentButton.setToolTipText(ArchvowI18n.text(project, "Add a component (archvow component add): its type, name and what it must do. Or right-click a layer.",
            "Ajouter un composant (archvow component add) : son type, son nom et ce qu'il doit faire. Ou clic droit sur une couche."));
        languageRefreshers.add(() -> addComponentButton.setText(ArchvowI18n.text(project, "Add component", "Ajouter un composant")));
        // 0.24.0 (contract 2.0): by feature or by layer, and Add feature
        String byFeature = ArchvowI18n.text(project, "By feature", "Par fonctionnalité");
        String byLayer = ArchvowI18n.text(project, "By layer", "Par couche");
        ArchvowSegmented groupBy = new ArchvowSegmented(List.of(byFeature, byLayer), byFeature, true);
        groupBy.onChange(() -> { groupByFeature = byFeature.equals(groupBy.value()); rebuildTree(); });
        JButton addFeature = button(ArchvowI18n.text(project, "Add feature", "Ajouter une fonctionnalité"), () -> openCommand("feature add", Map.of()));
        addFeature.setIcon(ArchvowTreeIcons.icon("add", null));
        addFeature.setMargin(new Insets(3, 6, 3, 6));
        addFeature.setToolTipText(ArchvowI18n.text(project, "Add a feature (archvow feature add): what the product does, never a ticket",
            "Ajouter une fonctionnalité (archvow feature add) : ce que fait le produit, jamais un ticket"));
        JPanel groupRow = new JPanel(new BorderLayout());
        JPanel groupLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 2));
        groupLeft.add(groupBy);
        groupRow.add(groupLeft, BorderLayout.CENTER);
        JPanel groupRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 3, 2));
        groupRight.add(addFeature);
        groupRow.add(groupRight, BorderLayout.EAST);
        JPanel tools = new JPanel(new BorderLayout());
        tools.add(groupRow, BorderLayout.NORTH);
        tools.add(filterRow, BorderLayout.CENTER);
        JPanel addRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 3, 2));
        addRow.add(addComponentButton);
        tools.add(addRow, BorderLayout.EAST);
        installLayerMenu();
        componentBrowser.add(tools, BorderLayout.NORTH);
        componentBrowser.add(scroll(componentsTree), BorderLayout.CENTER);
        updateFilterButtons();
        // the tool window shows the tree, the editor tab the component
        return role == Role.NAVIGATION ? componentBrowser : right;
    }

    private void setComponentFilter(String filter) {
        if (!List.of("all", "pending", "resolved").contains(filter)) return;
        componentFilter = filter;
        rebuildTree();
        updateFilterButtons();
    }

    private void updateFilterButtons() {
        if (allComponentsFilter == null) return;
        for (JButton filter : List.of(allComponentsFilter, pendingComponentsFilter, resolvedComponentsFilter)) {
            boolean active = filter == ("pending".equals(componentFilter) ? pendingComponentsFilter :
                "resolved".equals(componentFilter) ? resolvedComponentsFilter : allComponentsFilter);
            filter.setEnabled(!active);
            filter.setToolTipText(active ? ArchvowI18n.text(project, "Current filter", "Filtre actif") :
                ArchvowI18n.text(project, "Filter components", "Filtrer les composants"));
        }
    }

    /** The types a person can add in each layer of the project's template (ArchvowFilePlan, as VS Code's addable). */
    private Map<String, List<ArchvowFilePlan.Addable>> addableByLayer() {
        JsonObject catalog = projectCatalog != null ? projectCatalog : ArchvowInitCatalog.bundled();
        return ArchvowFilePlan.addableByLayer(manifest, catalog);
    }

    /** Right-click on a layer (or its empty line): Add a schema, a transformation... to it; a double-click on the empty line too. */
    private void installLayerMenu() {
        componentsTree.addMouseListener(new java.awt.event.MouseAdapter() {
            private String layerAt(java.awt.event.MouseEvent e) {
                TreePath path = componentsTree.getPathForLocation(e.getX(), e.getY());
                if (path == null || !(path.getLastPathComponent() instanceof DefaultMutableTreeNode node)) return "";
                return node.getUserObject() instanceof ArchvowTreeIcons.Node n ? n.layer() : "";
            }
            private void menu(java.awt.event.MouseEvent e) { menu(e, layerAt(e)); }
            private void menu(java.awt.event.MouseEvent e, String layer) {
                List<ArchvowFilePlan.Addable> types = addableByLayer().getOrDefault(layer, List.of());
                if (layer.isEmpty() || types.isEmpty()) return;
                JPopupMenu popup = new JPopupMenu(ArchvowI18n.text(project, "Add to " + layer, "Ajouter à " + layer));
                for (ArchvowFilePlan.Addable t : types) {
                    JMenuItem item = new JMenuItem(ArchvowI18n.text(project, "Add a component: " + t.type() + "…",
                        "Ajouter un composant : " + t.type() + "…"), ArchvowTreeIcons.icon(ArchvowTreeIcons.typeKind(t.type()), null));
                    item.setToolTipText(t.description());
                    item.addActionListener(a -> openCommand("component add", Map.of("type", t.type())));
                    popup.add(item);
                }
                popup.show(componentsTree, e.getX(), e.getY());
            }
            @Override public void mousePressed(java.awt.event.MouseEvent e) { if (e.isPopupTrigger()) menu(e); }
            @Override public void mouseReleased(java.awt.event.MouseEvent e) { if (e.isPopupTrigger()) menu(e); }
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                // 0.17.4: the ⊕ at the right of a layer, as VS Code's: its Add menu
                String plus = SwingUtilities.isLeftMouseButton(e) ? componentsTree.addLayerAt(e.getPoint()) : "";
                if (!plus.isEmpty()) { menu(e, plus); return; }
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                    TreePath path = componentsTree.getPathForLocation(e.getX(), e.getY());
                    if (path != null && path.getLastPathComponent() instanceof DefaultMutableTreeNode node && node.isLeaf()
                        && node.getUserObject() instanceof ArchvowTreeIcons.Node n && !n.layer().isEmpty()) menu(e);
                }
            }
        });
        componentsTree.setToolTipText(ArchvowI18n.text(project, "Right-click a layer to add a component to it", "Clic droit sur une couche pour y ajouter un composant"));
        componentsTree.addTip = layer -> ArchvowI18n.text(project, "Add a component to " + layer, "Ajouter un composant à " + layer);
    }

    private boolean showComponentInTree(String id) {
        if ("pending".equals(componentFilter)) return pendingByComponent.getOrDefault(id, 0) > 0;
        if ("resolved".equals(componentFilter))
            // A component can contain BOTH completed and pending items.
            return resolvedByComponent.getOrDefault(id, 0) > 0;
        return true;
    }

    /** Navigation does not depend on a custom tab UI. Every view button always changes cards. */
    private void addComponentView(ArchvowCompactViews navigation, JPanel cards, int index, String label, JComponent view) {
        addComponentView(navigation, cards, index, label, view, index);
    }

    /** A view of the component, its chip shown at {@code position} (Examples, view 8, after Rules). */
    private void addComponentView(ArchvowCompactViews navigation, JPanel cards, int index, String label, JComponent view, int position) {
        JToggleButton button = new ArchvowMotion.EditorTabButton(label);
        button.setFocusable(true);
        button.setFocusPainted(false);
        button.setToolTipText(ArchvowI18n.text(project,
            "Open " + label + " for the selected component", "Afficher " + label + " du composant sélectionné"));
        button.addActionListener(e -> selectComponentView(index));
        ArchvowMotion.installViewButton(button);
        componentViewButtons.add(button);
        navigation.addView(button, position);
        cards.add(view, Integer.toString(index));
    }

    private void selectComponentView(int index) {
        if (componentViewCards == null || index < 0 || index >= componentViewButtons.size()) return;
        if (componentConfirmation != null && componentViewNav != null
            && index != componentViewNav.activeIndex()) componentConfirmation.hideConfirmation();
        componentViewLayout.show(componentViewCards, Integer.toString(index));
        for (int i = 0; i < componentViewButtons.size(); i++) {
            JToggleButton button = componentViewButtons.get(i);
            button.setSelected(i == index);
            ArchvowMotion.updateViewButton(button);
        }
        if (componentViewNav != null) componentViewNav.setActive(index);
        refreshListFilter();
        // Other is a component-local catch-all: do not duplicate the component heading here
        componentDetails.setVisible(index != 7);
        if (componentActions != null) componentActions.setVisible(index != 7);
        if (actionsHeading != null) actionsHeading.setVisible(index != 7);
        componentViewCards.revalidate();
        componentViewCards.repaint();
        if (constructed && role == Role.DETAIL) afterSelectionChange();
    }

    /** An answer panel: its sections one under the other at their height, scrolled when taller (ArchvowSplitView.stack). */
    private static JScrollPane compactSections(JComponent... sections) {
        return ArchvowSplitView.stack(sections);
    }

    private JComponent rulesPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        JPanel commands = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        commands.add(button(ArchvowI18n.text(project, "Add rule…", "Ajouter une règle…"),
            () -> openCommand("rule add", selectedComponent == null ? Map.of()
                : Map.of("words", selectedComponent.id + "\n"))));
        commands.add(button(ArchvowI18n.text(project, "Edit rule…", "Modifier une règle…"),
            () -> chooseRuleAction("rule edit")));
        commands.add(button(ArchvowI18n.text(project, "Remove rule…", "Supprimer une règle…"),
            () -> chooseRuleAction("rule remove")));
        JPanel top = new JPanel(new BorderLayout(0, 4));
        top.add(commands, BorderLayout.NORTH);
        rulesAiActions.setVisible(false);
        top.add(rulesAiActions, BorderLayout.SOUTH);
        panel.add(top, BorderLayout.NORTH);
        JScrollPane text = scroll(rulesOnlyDetails);
        labelDetails(text);
        panel.add(text, BorderLayout.CENTER);
        return panel;
    }

    private void chooseRuleAction(String command) {
        if (selectedView == null) return;
        java.util.List<JsonObject> rules = ArchvowGrammar.objects(selectedView, "rules");
        java.util.List<String> ids = new ArrayList<>();
        Map<String, String> ruleTexts = new LinkedHashMap<>();
        for (JsonObject rule : rules) {
            String id = ArchvowGrammar.str(rule, "id");
            if (id.isBlank()) continue;
            ids.add(id);
            ruleTexts.put(id, ArchvowGrammar.str(rule, "text"));
        }
        if (ids.isEmpty()) {
            message(ArchvowI18n.text(project, "No rule to edit or remove.", "Aucune règle à modifier ou supprimer."));
            return;
        }
        String selected = (String) JOptionPane.showInputDialog(this,
            ArchvowI18n.text(project,"Select a rule:","Sélectionne une règle :"),
            "Archvow", JOptionPane.QUESTION_MESSAGE, null, ids.toArray(new String[0]), ids.get(0));
        if (selected == null) return;
        if ("rule edit".equals(command)) openCommand(command, Map.of("id", selected, "text", ruleTexts.get(selected)));
        else openCommand(command, Map.of("id", selected));
    }

    private JComponent dataPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        dataActionList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        ArchvowMotion.installList(dataActionList);
        dataActionList.setCellRenderer(new DefaultListCellRenderer() {
            @Override public java.awt.Component getListCellRendererComponent(JList<?> list, Object value,
                    int index, boolean selected, boolean focused) {
                super.getListCellRendererComponent(list, value, index, selected, focused);
                if (value instanceof ArchvowDataView.Item item) {
                    String label = item.kind() == ArchvowDataView.Kind.COLUMN ? "◉ " : "▸ ";
                    setText(label + item.title() + "  ·  " + (item.pending() ?
                        ArchvowI18n.text(project,"Pending", "En attente") :
                        ArchvowI18n.text(project,"Resolved", "Traité")));
                    Color statusColor = ArchvowStatusPalette.statusColor(item.pending());
                    if (!selected) {
                        setBackground(ArchvowStatusPalette.rowBackground(list.getBackground(), statusColor,
                            false, ArchvowMotion.hoverLevel(dataActionList, index)));
                        setForeground(darkTheme() ? new Color(222, 229, 239) : new Color(35, 47, 63));
                    }
                    setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, selected ? 4 : 3, 0, 0, statusColor),
                        BorderFactory.createEmptyBorder(5, 7, 5, 7)));
                }
                return this;
            }
        });
        dataActionList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                ArchvowDataView.Item selected = dataActionList.getSelectedValue();
                dataActionButton.setEnabled(selected != null);
                dataEditDescription.setEnabled(selected != null && selectedComponent != null);
                if (selected == null) {
                    dataSelectedDetails.setText(ArchvowI18n.text(project,
                        "Select a task or column to see its details.",
                        "Sélectionne une tâche ou une colonne pour voir ses détails."));
                } else {
                    String detail = selected.detail();
                    if (detail == null || detail.isBlank()) detail = ArchvowI18n.text(project,
                        "No further description is recorded for this item in the component spec.",
                        "Aucune description supplémentaire n'est enregistrée pour cet élément dans la spec du composant.");
                    dataSelectedDetails.setText(selected.title() + "  ·  " +
                        (selected.pending() ? ArchvowI18n.text(project,"Pending", "En attente") :
                            ArchvowI18n.text(project,"Resolved", "Traité")) + "\n\n" + detail);
                    dataSelectedDetails.setCaretPosition(0);
                    dataActionButton.setText(selected.kind() == ArchvowDataView.Kind.COLUMN ?
                        ArchvowI18n.text(project,"Classify column…", "Classifier la colonne…") :
                        ArchvowI18n.text(project,"Open task prompt", "Ouvrir les instructions de la tâche"));
                }
            }
        });
        dataActionList.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) handleDataAction();
            }
        });
        JScrollPane list = scroll(dataActionList);
        list.setPreferredSize(new Dimension(200, 110));
        JPanel toolbar = new JPanel(new BorderLayout(5,5));
        JLabel label = new JLabel(ArchvowI18n.text(project,"Actions to complete", "Actions à terminer"));
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        toolbar.add(label,BorderLayout.NORTH);
        JPanel listAndDetails = new JPanel(new BorderLayout(0, 4));
        listAndDetails.add(list, BorderLayout.NORTH);
        toolbar.add(listAndDetails, BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT,5,2));
        dataActionButton.setEnabled(false);
        dataActionButton.addActionListener(e -> handleDataAction());
        buttons.add(dataActionButton);

        toolbar.add(buttons,BorderLayout.SOUTH);
        dataSelectedDetails.setEditable(false);
        dataSelectedDetails.setLineWrap(true);
        dataSelectedDetails.setWrapStyleWord(true);
        dataSelectedDetails.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        dataSelectedDetails.setBackground(UIManager.getColor("Panel.background"));
        dataSelectedDetails.setForeground(UIManager.getColor("Label.foreground"));
        dataSelectedDetails.setText(ArchvowI18n.text(project,
            "Select a task or column to see its details.",
            "Sélectionne une tâche ou une colonne pour voir ses détails."));
        JScrollPane detail = scroll(dataSelectedDetails);
        detail.setPreferredSize(new Dimension(170, 90));
        detail.setBorder(BorderFactory.createTitledBorder(
            ArchvowI18n.text(project, "Details and description", "Détails et description")));
        dataEditDescription.setText(ArchvowI18n.text(project,
            "Edit component description…", "Modifier la description du composant…"));
        dataEditDescription.addActionListener(e -> {
            if (selectedComponent != null) openCommand("component edit", Map.of("component", selectedComponent.id));
        });
        dataEditDescription.setEnabled(true);
        JPanel detailsWithAction = new JPanel(new BorderLayout(0, 3));
        detailsWithAction.add(detail, BorderLayout.CENTER);
        JPanel detailFooter = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 0));
        detailFooter.add(dataEditDescription);
        detailsWithAction.add(detailFooter, BorderLayout.SOUTH);
        listAndDetails.add(detailsWithAction, BorderLayout.CENTER);
        dataActionsPanel = toolbar;
        panel.add(toolbar,BorderLayout.NORTH);
        panel.add(scroll(rulesDetails),BorderLayout.CENTER);
        dataInlineResultCard = inlineResultCard(dataInlineResult, () -> dataInlineResultCard.setVisible(false));
        dataInlineResultCard.setVisible(false);
        panel.add(dataInlineResultCard, BorderLayout.SOUTH);
        return panel;
    }

    private void refreshDataActions(JsonObject data) {
        String oldId = dataActionList.getSelectedValue() == null ? "" : dataActionList.getSelectedValue().id();
        dataActionModel.clear();
        // Show both pending and completed tasks, so an item's description remains
        // inspectable after completion. Pending work appears first.
        List<ArchvowDataView.Item> available = ArchvowDataView.items(data);
        for (ArchvowDataView.Item item : available)
            if (item.pending()) dataActionModel.addElement(item);
        for (ArchvowDataView.Item item : available)
            if (!item.pending()) dataActionModel.addElement(item);
        if (dataActionsPanel != null) dataActionsPanel.setVisible(true);
        dataEditDescription.setEnabled(selectedComponent != null);
        int selection = -1;
        for (int i=0;i<dataActionModel.size();i++)
            if (oldId.equals(dataActionModel.get(i).id())) selection=i;
        if (!dataActionModel.isEmpty()) dataActionList.setSelectedIndex(selection < 0 ? 0 : selection);
        if (dataInlineResultCard != null) dataInlineResultCard.setVisible(false);
    }

    private void handleDataAction() {
        ArchvowDataView.Item item = dataActionList.getSelectedValue();
        if (item == null || selectedComponent == null) return;
        if (item.kind() == ArchvowDataView.Kind.COLUMN) {
            // The classification cannot be inferred from the column name.
            openCommand("column set", Map.of("component", selectedComponent.id, "names", item.id()));
            return;
        }
        String componentId = selectedComponent.id;
        final List<String> args;
        try { args = ArchvowTaskPrompt.command(componentId, item.id()); }
        catch (IllegalArgumentException error) {
            // A task key is not always numeric in imported projects. Do not send
            // an invalid --task argument to argparse: show the full card instead.
            runTaskPrompt(List.of("prompt", componentId), componentId, true);
            return;
        }
        runTaskPrompt(args, componentId, false);
    }

    private void runTaskPrompt(List<String> args, String componentId, boolean wholeCard) {
        dataInlineResultCard.setVisible(true);
        dataInlineResult.setText(htmlMessage(escape(ArchvowI18n.text(project,
            "Loading task prompt…", "Chargement des instructions…"))));
        dataInlineResult.setCaretPosition(0);
        dataInlineResultCard.revalidate();
        cli.executeAction("prompt", args, null, r -> {
            if (ArchvowTaskPrompt.needsFullCardFallback(r.exitCode, r.output) && args.contains("--task")) {
                runTaskPrompt(List.of("prompt", componentId), componentId, true);
                return;
            }
            storeCommandResult(dataInlineResult, "prompt", r);
            if (wholeCard && r.ok())
                dataInlineResult.setToolTipText(ArchvowI18n.text(project,
                    "The full component card is shown because a numbered task was unavailable.",
                    "La carte complète est affichée car une tâche numérotée n'était pas disponible."));
            dataInlineResult.setCaretPosition(0);
        });
    }

    private JComponent commandPanel() {
        commandList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        commandList.addListSelectionListener((ListSelectionEvent e) -> {
            if (e.getValueIsAdjusting()) return;
            if (role == Role.NAVIGATION) {
                // the tool window chooses; the form opens in the editor tab
                ArchvowGrammar.Command chosen = commandList.getSelectedValue();
                if (chosen != null && !syncing && constructed) ArchvowEditorTab.showCommand(project, chosen.label, Map.of());
                return;
            }
            formBaseline = Map.of();
            formHelpExtra = "";
            renderForm(Map.of());
            if (constructed) afterSelectionChange();
        });
        JPanel right = new JPanel(new BorderLayout(2, 4));
        JPanel center = new JPanel(new BorderLayout());
        // design A: the title in plain words over archvow's own line, the fields left, what archvow will do right.
        // 0.17.3: as VS Code - the title, then one muted line "[archvow <command>] - what it does" (no scrolled
        // monospaced box: it clipped the lines and ran under the title), then the prefill note when there is one.
        formTitle.setFont(formTitle.getFont().deriveFont(Font.BOLD, formTitle.getFont().getSize2D() + 3f));
        formTitle.setBorder(BorderFactory.createEmptyBorder(6, 4, 2, 4));
        styleCommandChip(commandChip);
        commandHelp.setOpaque(false); commandHelp.setForeground(mutedTextColor());
        commandHelp.setBorder(BorderFactory.createEmptyBorder(3, 0, 0, 0));
        commandNote.setOpaque(false); commandNote.setForeground(mutedTextColor());
        commandNote.setBorder(BorderFactory.createEmptyBorder(4, 4, 0, 4));
        commandNote.setVisible(false);
        JPanel commandLine = new JPanel(new BorderLayout(6, 0));
        commandLine.setOpaque(false);
        commandLine.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));
        JPanel chipHolder = new JPanel(new BorderLayout());
        chipHolder.setOpaque(false);
        chipHolder.add(commandChip, BorderLayout.NORTH);
        commandLine.add(chipHolder, BorderLayout.WEST);
        commandLine.add(commandHelp, BorderLayout.CENTER);
        JPanel head = new JPanel(new BorderLayout(0, 2));
        head.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));
        head.add(formTitle, BorderLayout.NORTH); head.add(commandLine, BorderLayout.CENTER); head.add(commandNote, BorderLayout.SOUTH);
        JPanel formTop = new JPanel(new BorderLayout(0, 5));
        formTop.add(head, BorderLayout.NORTH);
        JPanel stepFooter = new JPanel(new BorderLayout(5, 0));
        formBack.addActionListener(e -> changeFormStage(formStageIndex - 1));
        formNext.addActionListener(e -> changeFormStage(formStageIndex + 1));
        formBack.setText(ArchvowI18n.text(project, "← Previous", "← Précédent"));
        formNext.setText(ArchvowI18n.text(project, "Next →", "Suivant →"));
        // Step navigation and submit actions share a single footer under the form.
        // The strip is strictly one horizontal row (scrolls if narrower than its items).
        stepFooter.add(formStageStatus, BorderLayout.CENTER);
        formStepStrip = new JScrollPane(formStepButtons,
            ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        formStepStrip.setBorder(BorderFactory.createEmptyBorder());
        // Leave room for the buttons *and* the horizontal scrollbar: otherwise
        // Swing clips the top of the steps when the last ones are scrolled into view.
        formStepButtons.setBorder(BorderFactory.createEmptyBorder(7, 0, 1, 0));
        formStepStrip.setPreferredSize(new Dimension(200, 60));
        formNavigation.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
        formStepStrip.getHorizontalScrollBar().setUnitIncrement(22);
        formNavigation.add(formStepStrip, BorderLayout.CENTER);
        formNavigation.add(stepFooter, BorderLayout.SOUTH);
        formNavigation.setVisible(false);
        formTop.add(formNavigation, BorderLayout.CENTER);
        center.add(formTop, BorderLayout.NORTH);
        formScroller = scroll(form);
        formScroller.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        formScroller.getVerticalScrollBar().setUnitIncrement(18);
        formScroller.getViewport().addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent event) {
                if (formReview.isVisible()) refreshFormReviewLayout();
            }
        });
        center.add(formScroller, BorderLayout.CENTER);
        right.add(center, BorderLayout.CENTER);
        right.add(formPane(), BorderLayout.EAST);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 4));
        // The form scrollbar must not run through the bottom action buttons.
        actions.setBorder(BorderFactory.createEmptyBorder(4, 8, 12, 18));
        actions.add(formBack);
        actions.add(formNext);
        actions.add(button(ArchvowI18n.text(project, "Cancel", "Annuler"), () -> { formAlerts.removeAll(); formAlerts.setVisible(false); renderForm(Map.of()); }));
        formRunButton = button(ArchvowI18n.text(project, "Run command", "Exécuter la commande"), this::submitForm);
        actions.add(formRunButton);
        JPanel fieldFooter = new JPanel(new BorderLayout(0, 3));
        fieldFooter.add(formAlerts, BorderLayout.NORTH);
        fieldFooter.add(actions, BorderLayout.SOUTH);
        center.add(fieldFooter, BorderLayout.SOUTH);
        commandResultCard = inlineResultCard(commandResult, () -> commandResultCard.setVisible(false));
        JPanel bottom = new JPanel(new BorderLayout(0, 5));
        formAlerts.setLayout(new BoxLayout(formAlerts, BoxLayout.Y_AXIS));
        formAlerts.setVisible(false);
        commandConfirmation = new ArchvowConfirmPanel();
        bottom.add(commandConfirmation, BorderLayout.NORTH);
        bottom.add(commandResultCard, BorderLayout.CENTER);
        right.add(bottom, BorderLayout.SOUTH);
        // the tool window shows the list, the editor tab the form
        return role == Role.NAVIGATION ? commandBrowser() : right;
    }

    /** Grouped tree built from the shared forms.json contract; unknown commands go to Other. */
    private JComponent commandBrowser() {
        JPanel browser = new JPanel(new BorderLayout(0, 5));
        JTextField filter = new JTextField();
        filter.setToolTipText(ArchvowI18n.text(project, "Filter commands", "Filtrer les commandes"));
        commandTree = new JTree();
        commandTree.setRootVisible(false);
        commandTree.setShowsRootHandles(true);
        commandTree.setCellRenderer(new javax.swing.tree.DefaultTreeCellRenderer() {
            @Override public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected,
                    boolean expanded, boolean leaf, int row, boolean focused) {
                super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, focused);
                Object user = value instanceof DefaultMutableTreeNode node ? node.getUserObject() : value;
                if (user instanceof ArchvowGrammar.Command command) {
                    setText(command.label);
                    setIcon(ArchvowTreeIcons.icon(ArchvowTreeIcons.commandKind(command.path), null));
                } else if (user instanceof ArchvowTreeIcons.Kinded kinded) setIcon(ArchvowTreeIcons.icon(kinded.iconKind(), null));
                setIconTextGap(6);
                return this;
            }
        });
        commandTree.addTreeSelectionListener(e -> {
            Object selected = commandTree.getLastSelectedPathComponent();
            if (selected instanceof DefaultMutableTreeNode node && node.getUserObject() instanceof ArchvowGrammar.Command cmd)
                commandList.setSelectedValue(cmd, true);
            if (selected instanceof DefaultMutableTreeNode node && node.getUserObject() instanceof SkillNode skill) {
                SwingUtilities.invokeLater(() -> { openSkill(skill.id(), Map.of()); commandTree.clearSelection(); });
            }
        });
        Runnable rebuild = () -> {
            String query = filter.getText().strip().toLowerCase(java.util.Locale.ROOT);
            DefaultMutableTreeNode root = new DefaultMutableTreeNode("Commands");
            Set<ArchvowGrammar.Command> known = new LinkedHashSet<>();
            for (ArchvowForms.Group group : ArchvowForms.commandGroups(ArchvowI18n.isFrench(project))) {
                DefaultMutableTreeNode parent = new DefaultMutableTreeNode(new ArchvowTreeIcons.Node(group.label(), ArchvowTreeIcons.groupKind(group.id()), ""));
                for (ArchvowGrammar.Command cmd : ArchvowGrammar.COMMANDS) {
                    if (!group.prefixes().contains(cmd.path.get(0))) continue;
                    known.add(cmd);
                    if (!query.isBlank() && !(cmd.label + " " + cmd.description + " " + group.label()).toLowerCase(java.util.Locale.ROOT).contains(query)) continue;
                    parent.add(new DefaultMutableTreeNode(cmd));
                }
                if (parent.getChildCount() > 0) root.add(parent);
            }
            DefaultMutableTreeNode other = new DefaultMutableTreeNode(new ArchvowTreeIcons.Node(ArchvowI18n.text(project, "Other commands", "Autres commandes"), "other", ""));
            for (ArchvowGrammar.Command cmd : ArchvowGrammar.COMMANDS)
                if (!known.contains(cmd) && (query.isBlank() || (cmd.label + " " + cmd.description).toLowerCase(java.util.Locale.ROOT).contains(query)))
                    other.add(new DefaultMutableTreeNode(cmd));
            if (other.getChildCount() > 0) root.add(other);
            // AI skills have their own fourth navigation tab.
            commandTree.setModel(new DefaultTreeModel(root));
            // Expand only when searching; normal navigation remains compact.
            if (!query.isBlank()) for (int row = 0; row < commandTree.getRowCount(); row++) commandTree.expandRow(row);
        };
        filter.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { rebuild.run(); }
            @Override public void removeUpdate(DocumentEvent e) { rebuild.run(); }
            @Override public void changedUpdate(DocumentEvent e) { rebuild.run(); }
        });
        rebuild.run();
        browser.add(filter, BorderLayout.NORTH);
        browser.add(scroll(commandTree), BorderLayout.CENTER);
        languageRefreshers.add(rebuild);
        return browser;
    }

    /** Always use the viewport's actual width, rather than the very long skill descriptions' preferred width. */
    private static final class ResponsiveSkillsPage extends JPanel implements Scrollable {
        ResponsiveSkillsPage() { super(new BorderLayout(0, 5)); }
        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) { return 16; }
        @Override public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) { return 64; }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }

    /**
     * Match the VS Code AI skills browser: expandable group, count, a short description
     * and the skill id to the right. Every item opens its form in the shared editor.
     */
    private JComponent aiSkillsPanel() {
        JPanel page = new ResponsiveSkillsPage();
        page.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        JPanel group = new JPanel(new BorderLayout(0, 5));
        group.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, borderColor()));
        JPanel groupHeading = new JPanel(new BorderLayout());
        JButton toggle = new JButton();
        toggle.setHorizontalAlignment(SwingConstants.LEFT);
        toggle.setContentAreaFilled(false);
        toggle.setBorder(BorderFactory.createEmptyBorder(6, 4, 6, 4));
        toggle.setFont(toggle.getFont().deriveFont(Font.BOLD, 15f));
        toggle.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        groupHeading.add(toggle, BorderLayout.CENTER);
        JLabel count = new JLabel(Integer.toString(ArchvowAiSkills.SKILLS.size()), SwingConstants.CENTER);
        count.setOpaque(true);
        count.setForeground(mutedTextColor());
        count.setBackground(darkTheme() ? new Color(75, 76, 79) : new Color(228, 231, 236));
        count.setBorder(BorderFactory.createEmptyBorder(2, 9, 2, 9));
        groupHeading.add(count, BorderLayout.EAST);
        group.add(groupHeading, BorderLayout.NORTH);

        JPanel contents = new JPanel();
        contents.setLayout(new BoxLayout(contents, BoxLayout.Y_AXIS));
        JLabel note = new JLabel();
        note.setForeground(mutedTextColor());
        note.setBorder(BorderFactory.createEmptyBorder(0, 14, 8, 0));
        note.setAlignmentX(Component.LEFT_ALIGNMENT);
        contents.add(note);
        JsonObject skills = ArchvowForms.section("ai_skills");
        for (String id : ArchvowAiSkills.SKILLS) {
            JsonObject def = ArchvowSkillForm.def(skills, id);
            if (def == null) continue;
            JPanel item = new JPanel(new BorderLayout(10, 0));
            item.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, borderColor()),
                BorderFactory.createEmptyBorder(8, 10, 8, 8)));
            item.setAlignmentX(Component.LEFT_ALIGNMENT);
            item.setMaximumSize(new Dimension(Integer.MAX_VALUE, 96));
            item.setMinimumSize(new Dimension(0, 96));
            item.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            Color usual = item.getBackground();
            Color over = darkTheme() ? new Color(66, 64, 68) : new Color(227, 233, 241);
            JPanel lines = new JPanel(new BorderLayout(0, 3));
            lines.setOpaque(false);
            lines.setMinimumSize(new Dimension(0, 0));
            JLabel name = new JLabel();
            name.setFont(name.getFont().deriveFont(Font.BOLD, 14f));
            JTextArea description = new JTextArea();
            description.setEditable(false);
            description.setOpaque(false);
            description.setLineWrap(true);
            description.setWrapStyleWord(true);
            description.setRows(3);
            description.setMinimumSize(new Dimension(0, 0));
            description.setPreferredSize(new Dimension(0, 52));
            description.setForeground(mutedTextColor());
            description.setFont(description.getFont().deriveFont(12f));
            lines.add(name, BorderLayout.NORTH);
            lines.add(description, BorderLayout.CENTER);
            JLabel slug = new JLabel(id);
            slug.setForeground(mutedTextColor());
            slug.setFont(slug.getFont().deriveFont(Font.BOLD, 11f));
            slug.setVerticalAlignment(SwingConstants.TOP);
            item.add(lines, BorderLayout.CENTER);
            item.add(slug, BorderLayout.EAST);
            Runnable translate = () -> {
                boolean fr = ArchvowI18n.isFrench(project);
                name.setText(ArchvowSkillForm.pair(def, "label", fr));
                String descriptionText = ArchvowSkillForm.pair(def, "does", fr);
                description.setText(descriptionText);
                description.setToolTipText(descriptionText);
                item.setToolTipText(descriptionText);
            };
            translate.run();
            languageRefreshers.add(translate);
            java.awt.event.MouseAdapter activate = new java.awt.event.MouseAdapter() {
                @Override public void mouseEntered(java.awt.event.MouseEvent e) { item.setBackground(over); }
                @Override public void mouseExited(java.awt.event.MouseEvent e) {
                    Point p = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), item);
                    if (!item.contains(p)) item.setBackground(usual);
                }
                @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                    if (SwingUtilities.isLeftMouseButton(e)) openSkill(id, Map.of());
                }
            };
            for (JComponent part : List.of(item, lines, name, description, slug)) part.addMouseListener(activate);
            item.setFocusable(true);
            item.getAccessibleContext().setAccessibleName(id);
            item.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke("ENTER"), "open-skill");
            item.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke("SPACE"), "open-skill");
            item.getActionMap().put("open-skill", new AbstractAction() {
                @Override public void actionPerformed(java.awt.event.ActionEvent e) { openSkill(id, Map.of()); }
            });
            contents.add(item);
        }
        JPanel body = new JPanel(new BorderLayout());
        body.add(contents, BorderLayout.NORTH);
        group.add(body, BorderLayout.CENTER);
        Runnable heading = () -> {
            toggle.setText((body.isVisible() ? "▾" : "▸") + "  ✧  "
                + ArchvowI18n.text(project, "AI skills", "Skills IA"));
            note.setText(ArchvowI18n.text(project,
                "The AI proposes or adds, you decide.", "L'IA propose ou ajoute, tu décides."));
        };
        heading.run();
        languageRefreshers.add(heading);
        toggle.addActionListener(e -> {
            body.setVisible(!body.isVisible());
            toggle.setText((body.isVisible() ? "▾" : "▸") + "  ✧  "
                + ArchvowI18n.text(project, "AI skills", "Skills IA"));
        });
        page.add(group, BorderLayout.NORTH);
        return scroll(page);
    }

    /** Overview › Packs (archvow 1.26): the project's packs, each with Update / Remove / Check (ArchvowPacks). */
    private void openPacks() {
        if (role == Role.NAVIGATION) {
            ArchvowPanel editor = ArchvowEditorTab.panel(project);
            if (editor != null) editor.openPacks();
            return;
        }
        showWorkspace();
        resultLabel = "Packs";
        showDetailHome("result");
        overviewResult.setText(htmlBare(ArchvowPacks.html(projectPacks, ArchvowI18n.isFrench(project))));
        overviewResult.setCaretPosition(0);
        overviewResultCard.setVisible(true);
        tabs.setSelectedIndex(0);
        afterSelectionChange();
    }

    /** Read-only documentation: the command reference, not `archvow doc` (which writes the project's pages). */
    private void openCommandReference() {
        if (role == Role.NAVIGATION) {
            ArchvowPanel editor = ArchvowEditorTab.panel(project);
            if (editor != null) editor.openCommandReference();
            return;
        }
        StringBuilder html = new StringBuilder("<h2>")
            .append(escape(ArchvowI18n.text(project, "Command reference", "Documentation des commandes")))
            .append("</h2><p>").append(escape(ArchvowI18n.text(project,
                "Read-only reference from the bundled Archvow grammar. The configured CLI remains authoritative.",
                "Référence du catalogue livré avec le plugin. La CLI configurée fait autorité."))).append("</p>");
        Set<ArchvowGrammar.Command> grouped = new LinkedHashSet<>();
        for (ArchvowForms.Group group : ArchvowForms.commandGroups(ArchvowI18n.isFrench(project))) {
            html.append("<h3>").append(escape(group.label())).append("</h3>");
            for (ArchvowGrammar.Command cmd : ArchvowGrammar.COMMANDS) if (group.prefixes().contains(cmd.path.get(0))) {
                grouped.add(cmd);
                appendCommandReference(html, cmd);
            }
        }
        for (ArchvowGrammar.Command cmd : ArchvowGrammar.COMMANDS) if (!grouped.contains(cmd)) appendCommandReference(html, cmd);
        showWorkspace();
        resultLabel = ArchvowI18n.text(project, "Command reference", "Docs des commandes");
        showDetailHome("result");
        overviewResult.setText(htmlBare(html.toString()));
        overviewResult.setCaretPosition(0);
        overviewResultCard.setVisible(true);
        tabs.setSelectedIndex(0);
        afterSelectionChange();
    }

    private void appendCommandReference(StringBuilder html, ArchvowGrammar.Command command) {
        html.append("<h4>archvow ").append(escape(command.label)).append("</h4><p>")
            .append(escape(command.description)).append("</p>");
        if (!command.parameters.isEmpty()) {
            html.append("<p>");
            for (ArchvowGrammar.Param param : command.parameters)
                html.append("<code>").append(escape(param.flags.isEmpty() ? param.name : param.option())).append("</code>")
                    .append(param.required ? " * " : " ");
            html.append("</p>");
        }
    }

    /** Design A: what archvow will do, and the command line as it is typed (the live preview). */
    private JComponent formPane() {
        ArchvowCommandFormLayout.PreviewContent pane = new ArchvowCommandFormLayout.PreviewContent();
        pane.setBorder(BorderFactory.createEmptyBorder(6, 10, 8, 10));
        JLabel heading = new JLabel(ArchvowI18n.text(project, "What archvow will do", "Ce que va faire archvow"));
        heading.setFont(heading.getFont().deriveFont(Font.BOLD));
        languageRefreshers.add(() -> heading.setText(ArchvowI18n.text(project, "What archvow will do", "Ce que va faire archvow")));
        heading.setAlignmentX(LEFT_ALIGNMENT);
        formDoes.setOpaque(false); formDoes.setAlignmentX(LEFT_ALIGNMENT);
        JLabel commandLabel = new JLabel(ArchvowI18n.text(project, "The command", "La commande"));
        languageRefreshers.add(() -> commandLabel.setText(ArchvowI18n.text(project, "The command", "La commande")));
        commandLabel.setForeground(mutedTextColor()); commandLabel.setAlignmentX(LEFT_ALIGNMENT);
        JComponent preview = ArchvowTerminal.wrap(formPreview);
        preview.setAlignmentX(LEFT_ALIGNMENT);
        ArchvowWrapLabel note = formHint(ArchvowI18n.text(project,
            "If archvow refuses (a name already taken, words that protect nothing...), its message shows in red under the form, before anything is written.",
            "Si archvow refuse (un nom déjà pris, des mots qui ne protègent rien...), son message s'affiche en rouge sous le formulaire, avant que rien ne soit écrit."), true);
        pane.addPart(heading); pane.addPart(Box.createVerticalStrut(6)); pane.addPart(formDoes); pane.addPart(Box.createVerticalStrut(8));
        pane.addPart(commandLabel); pane.addPart(preview); pane.addPart(formFiles); pane.addPart(Box.createVerticalStrut(6)); pane.addPart(note);
        // The preview fills the available height beside the form. When init or
        // another command lists more files than fit, scroll the *whole* preview;
        // never put a separate scrollbar inside the terminal command.
        final int preferredSideWidth = ArchvowCommandFormLayout.helpWidth(0);
        JScrollPane scroller = new JScrollPane(pane,
            ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroller.setBorder(BorderFactory.createEmptyBorder());
        scroller.getVerticalScrollBar().setUnitIncrement(18);
        JPanel fullHeight = new JPanel(new BorderLayout()) {
            @Override public Dimension getPreferredSize() {
                Container parent = getParent();
                int sideWidth = parent == null ? preferredSideWidth : ArchvowCommandFormLayout.helpWidth(parent.getWidth());
                return new Dimension(sideWidth, Math.max(200, super.getPreferredSize().height));
            }
        };
        fullHeight.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createEmptyBorder(6, 8, 6, 4),
            BorderFactory.createLineBorder(mutedTextColor())));
        fullHeight.add(scroller, BorderLayout.CENTER);
        return fullHeight;
    }


    /** Shared layout for all command forms, including init and decision make. */
    private void toggleFormSection(JToggleButton toggle, java.util.List<JComponent> parts) {
        ArchvowCommandFormLayout.setSectionExpanded(form, formScroller, toggle, parts);
    }

    /** The command line the form asks for now: written into the pane at each keystroke (no run). */
    private void updatePreview() {
        ArchvowGrammar.Command command = commandList.getSelectedValue();
        if (command == null || buildingForm) return;
        Map<String, String> values = currentValues();
        boolean fr = ArchvowI18n.isFrench(project);
        formPreview.setText(ArchvowForms.commandLine(command, values, fr,
            "init".equals(command.label) && initQuestions != null ? initQuestions.answers() : List.of()));
        if ("try".equals(command.label)) tryWhere.setText((fr ? "Copié dans  " : "Copied to  ")
            + ArchvowOverview.tryWhere(values.get("example"), values.get("dir"), System.getProperty("java.io.tmpdir"), fr));
        // the files it writes: what is new, updated, removed, and the code a new component plans
        String output = ArchvowForms.output(command, values);
        boolean exists = !output.isEmpty() && project.getBasePath() != null && new File(project.getBasePath(), output).exists();
        JsonObject catalog = projectCatalog != null ? projectCatalog : ArchvowInitCatalog.bundled();
        formFiles.showRows(ArchvowFilePlan.plan(command.label, values, manifest, catalog, ArchvowForms.DATA, fr, exists), fr);
        // changes / diff with Save to a file: the button says what it does
        if (formRunButton != null) {
            String verb = ArchvowForms.verb(command.label, fr);
            formRunButton.setText(!output.isEmpty() ? ArchvowI18n.text(project, "Save the report", "Enregistrer le rapport")
                : verb.isEmpty() ? ArchvowI18n.text(project, "Run command", "Exécuter la commande") : verb);
        }
        refreshFormStepIndicators();
    }

    /** Every field of the form updates the preview when it changes. */
    private void watchForPreview(JComponent control) {
        if (control instanceof javax.swing.text.JTextComponent text) {
            text.getDocument().addDocumentListener(new DocumentListener() {
                @Override public void insertUpdate(DocumentEvent e) { updatePreview(); }
                @Override public void removeUpdate(DocumentEvent e) { updatePreview(); }
                @Override public void changedUpdate(DocumentEvent e) { updatePreview(); }
            });
        } else if (control instanceof JComboBox<?> combo) {
            combo.addActionListener(e -> updatePreview());
            if (combo.isEditable() && combo.getEditor().getEditorComponent() instanceof JComponent editor) watchForPreview(editor);
        } else if (control instanceof JCheckBox check) {
            check.addItemListener(e -> updatePreview());
        } else if (control instanceof ArchvowSegmented segmented) {
            segmented.onChange(this::updatePreview);
        }
    }

    /** archvow said no (nothing written) in red, or wrote it with a doubt in amber: above the buttons. */
    private void showFormAlerts(ArchvowCli.Result result) {
        formAlerts.removeAll();
        String refusal = ArchvowForms.refusal(result.exitCode, result.stderr, result.error);
        if (!refusal.isEmpty()) formAlerts.add(formAlert(ArchvowI18n.text(project, "archvow refuses:", "archvow refuse :"), refusal, true));
        for (String warning : ArchvowForms.warnings(result.stdout))
            formAlerts.add(formAlert(ArchvowI18n.text(project, "Written, but:", "Écrit, mais :"), warning, false));
        formAlerts.setVisible(formAlerts.getComponentCount() > 0);
        formAlerts.revalidate(); formAlerts.repaint();
    }

    private JComponent formAlert(String title, String text, boolean error) {
        Color edge = error ? new Color(0xBE, 0x11, 0x00) : new Color(0xB8, 0x95, 0x00);
        Color fill = error ? (darkTheme() ? new Color(0x4A, 0x1F, 0x1C) : new Color(0xFD, 0xEC, 0xEA))
            : (darkTheme() ? new Color(0x4A, 0x3F, 0x1C) : new Color(0xFF, 0xF6, 0xDB));
        JPanel box = new JPanel(new BorderLayout(0, 2));
        box.setBackground(fill);
        box.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(edge), BorderFactory.createEmptyBorder(5, 8, 6, 8)));
        JLabel heading = new JLabel(title);
        heading.setFont(heading.getFont().deriveFont(Font.BOLD));
        ArchvowWrapLabel body = new ArchvowWrapLabel();
        body.setText(text); body.setOpaque(false);
        box.add(heading, BorderLayout.NORTH); box.add(body, BorderLayout.CENTER);
        box.setAlignmentX(LEFT_ALIGNMENT);
        return box;
    }

    /** Everything this panel shows, again in the language of the settings (the box itself included). */
    void languageChanged() {
        String mode = ArchvowSettings.get(project).getState().languageMode;
        syncingLanguage = true;
        try { languageMode.setSelectedItem(mode == null || mode.isBlank() ? "EN" : mode); }
        finally { syncingLanguage = false; }
        refreshStaticTexts();
        updateOverview();
        if (selectedView != null) renderComponent(selectedView);
        revalidate();
        repaint();
    }

    private void refreshStaticTexts() {
        if (componentViewNav != null) {
            componentViewNav.setFrench(ArchvowI18n.isFrench(project));
            if (componentViewButtons.size() >= 8) {
                componentViewButtons.get(4).setText(ArchvowI18n.text(project, "Rules", "Règles"));
                componentViewButtons.get(5).setText(ArchvowI18n.text(project, "Tasks", "Tâches"));
                componentViewButtons.get(6).setText("Spec");
                componentViewButtons.get(7).setText(ArchvowI18n.text(project, "Other", "Autres"));
                if (selectedView != null) refreshViewStatus(selectedView);
                componentViewNav.refresh();
            }
        }
        decideInline.setText(ArchvowI18n.text(project, "Save answer", "Enregistrer la réponse"));
        proposeInline.setText(ArchvowI18n.text(project, "Save as a proposal", "Enregistrer comme proposition"));
        acceptInline.setText(ArchvowI18n.text(project, "Accept the AI's proposal", "Accepter la proposition de l'IA"));
        suggestedInline.setText(ArchvowI18n.text(project, "Use the suggestion", "Utiliser la suggestion"));
        refreshListFilter();
        dismissInline.setText(ArchvowI18n.text(project, "Not applicable", "Non applicable"));
        safetyInvariantButton.setText(ArchvowI18n.text(project, "Add required invariant", "Ajouter l'invariant requis"));
        changeHazardButton.setText(ArchvowI18n.text(project, "Change hazard", "Modifier le hazard"));
        keepHazardButton.setText(ArchvowI18n.text(project, "Keep it as it is", "Le garder tel quel"));
        removeHazardButton.setText(ArchvowI18n.text(project, "Remove hazard…", "Supprimer un hazard…"));
        removeInvariantButton.setText(ArchvowI18n.text(project, "Remove invariant…", "Supprimer un invariant…"));
        if (constructed) showSafety();  // its labels and buttons depend on the selected question
        assumptionAcceptButton.setText(ArchvowI18n.text(project, "Accept", "Accepter"));
        assumptionRejectButton.setText(ArchvowI18n.text(project, "Reject", "Rejeter"));
        if (allComponentsFilter != null) allComponentsFilter.setText(ArchvowI18n.text(project, "All", "Tous"));
        if (pendingComponentsFilter != null) pendingComponentsFilter.setText(ArchvowI18n.text(project, "Pending", "En attente"));
        if (resolvedComponentsFilter != null) resolvedComponentsFilter.setText(ArchvowI18n.text(project, "Resolved", "Traités"));
        languageLabel.setText(ArchvowI18n.text(project, "Language", "Langue"));
        for (Runnable refresh : languageRefreshers) refresh.run();
        refreshOverviewTabTitles();
        if (checkCard != null && checkCard.getClientProperty("archvow.heading") instanceof JLabel title)
            title.setText(ArchvowI18n.text(project, "Check & Gates", "Contrôles et validations"));
        tabs.setTitleAt(0, ArchvowI18n.text(project, "Overview", "Vue d'ensemble"));
        tabs.setTitleAt(1, ArchvowI18n.text(project, "Features", "Fonctionnalités"));
        tabs.setTitleAt(2, ArchvowI18n.text(project, "Commands", "Commandes"));
        quickTabs.setTitleAt(0, ArchvowI18n.text(project, "Pending", "En attente") + " (" + otherPendingModel.size() + ")");
        quickTabs.setTitleAt(1, ArchvowI18n.text(project, "Resolved", "Traités") + " (" + otherResolvedModel.size() + ")");
        componentOtherTabs.setTitleAt(0, ArchvowI18n.text(project, "Pending", "En attente") + " (" + componentOtherPendingModel.size() + ")");
        componentOtherTabs.setTitleAt(1, ArchvowI18n.text(project, "Resolved", "Traités") + " (" + componentOtherResolvedModel.size() + ")");
        openSettingsButton.setText(ArchvowI18n.text(project, "Open settings", "Ouvrir les paramètres"));
        testArchvowButton.setText(ArchvowI18n.text(project, "Test Archvow (--version)", "Tester Archvow (--version)"));
        quickPrimary.setText(ArchvowI18n.text(project, "Treat now", "Traiter maintenant"));
        quickSecondary.setText(ArchvowI18n.text(project, "Go to component", "Aller au composant"));
        componentOtherPrimary.setText(ArchvowI18n.text(project, "Treat now", "Traiter maintenant"));
        componentOtherSecondary.setText(ArchvowI18n.text(project, "Go to component", "Aller au composant"));
        quickSecondary.setIcon(loadLogoIcon(14, 14));
        componentOtherSecondary.setIcon(loadLogoIcon(14, 14));
        openSettingsButton.setIcon(loadLogoIcon(14, 14));
        renderForm(constructed ? currentValues() : Map.of());  // what is typed in an open form stays
        // Reapply semantic icons to dynamically localized button labels.
        ArchvowUiIcons.decorateTree(this);
    }

    private void renderForm(Map<String, String> defaults) {
        ArchvowGrammar.Command command = commandList.getSelectedValue();
        if (command == null) return;
        if (commandConfirmation != null) commandConfirmation.hideConfirmation();
        buildingForm = true;
        try { buildForm(command, defaults == null ? Map.of() : defaults); }
        finally { buildingForm = false; }
        updatePreview();
    }

    /** What every command form offers now (ArchvowChoices, the same as VS Code's choices.ts). */
    private Map<String, Map<String, ArchvowChoices.Field>> choiceBook() {
        JsonObject catalog = projectCatalog != null ? projectCatalog : liveInitCatalog != null ? liveInitCatalog : ArchvowInitCatalog.bundled();
        if (bundledKinds == null) bundledKinds = ArchvowInitCatalog.kinds();
        return ArchvowChoices.book(ArchvowGrammar.COMMANDS, new ArchvowChoices.Input(manifest, componentSnapshots, catalog, bundledKinds,
            gitRefs, branch.getText().trim(), ArchvowI18n.isFrench(project), firstCommit, gitRefKinds));
    }

    private void buildForm(ArchvowGrammar.Command command, Map<String, String> defaults) {
        fields.clear(); form.removeAll();
        formStageComponents.clear();
        templateSettings = null;
        String name = command.label;
        if (!name.equals(guidedCommand)) formStageIndex = 0;
        guidedCommand = name;
        boolean guided = command.parameters.size() > 4 || "init".equals(name) || "pack template".equals(name);
        String extra = switch (name) {
            case "specify" -> ArchvowI18n.text(project, "IDE mode: clarification without interactive terminal.", "Mode IDE : clarification sans terminal interactif.");
            case "rule add" -> ArchvowI18n.text(project, "One argument per line in words.", "Un argument par ligne dans le champ words.");
            default -> "";
        };
        commandChip.setText("archvow " + name);
        commandHelp.setText("- " + command.description + (extra.isEmpty() ? "" : " " + extra));
        commandHelp.setForeground(mutedTextColor());
        commandNote.setText(formHelpExtra.strip());
        commandNote.setForeground(mutedTextColor());
        commandNote.setVisible(!formHelpExtra.isBlank());
        boolean french = ArchvowI18n.isFrench(project);
        String title = ArchvowForms.title(name, french);
        formTitle.setText(title.isEmpty() ? command.description : title);
        formDoes.setText(ArchvowForms.does(name, french));
        String verb = ArchvowForms.verb(name, french);
        if (formRunButton != null) formRunButton.setText(verb.isEmpty() ? ArchvowI18n.text(project, "Run command", "Exécuter la commande") : verb);
        JToggleButton moreToggle = null;
        java.util.List<JComponent> moreParts = new ArrayList<>();
        Map<String, ArchvowChoices.Field> offered = choiceBook().getOrDefault(name, Map.of());
        Map<String, String> values = new LinkedHashMap<>(defaults);
        List<String> own = ArchvowChoices.projectTemplates(manifest);
        if ("init".equals(name) && values.getOrDefault("template", "").isBlank() && !own.isEmpty()) values.put("template", own.get(0));
        int y = 0;
        // the options archvow hides from --help (invariant add's expert checks): apart, closed unless one holds a value
        JToggleButton expertToggle = null;
        java.util.List<JComponent> expertParts = new ArrayList<>();
        for (ArchvowGrammar.Param param : ArchvowForms.ordered(command)) {
            // init: the template is chosen once, in "Template and its questions" (under the package); pack template:
            // its answers and layer folders are in "Settings of <base>", under --extends
            if ("init".equals(name) && "answer".equals(param.name)) continue;
            if ("init".equals(name) && "template".equals(param.name)) {
                fields.put("template", new JTextField(values.getOrDefault("template", "")));  // not shown: the wizard sets it
                continue;
            }
            if ("pack template".equals(name) && ("answer".equals(param.name) || "layer".equals(param.name))) continue;
            String text = values.getOrDefault(param.name, "");
            ArchvowChoices.Field field = param.bool ? null : offered.get(param.name);
            JComponent control;
            if ("uses".equals(param.name) && ("component edit".equals(name) || "component add".equals(name))) {
                Map<String, String> available = new LinkedHashMap<>();
                for (JsonObject container : ArchvowGrammar.objects(manifest, "containers"))
                    for (JsonObject candidate : ArchvowGrammar.objects(container, "components")) {
                        String id = ArchvowGrammar.str(candidate, "id");
                        if (!id.isBlank()) available.put(id, id + "  ·  "
                            + ArchvowGrammar.str(candidate, "type") + "  ·  " + ArchvowGrammar.str(candidate, "layer"));
                    }
                control = new ArchvowUsesPicker(text, available, values.getOrDefault("component", ""), french, this::updatePreview);
            } else if (param.bool) {
                JCheckBox checkbox = new JCheckBox();
                checkbox.setSelected("true".equals(text) || ("init".equals(name) && "no_input".equals(param.name) && !values.containsKey("no_input")));
                control = checkbox;
            } else if (field != null && !"note".equals(field.kind)) {
                control = choiceControl(name, param.name, field, text, values);
            } else if (param.flags.contains("--from")) {
                // the brief files of the declared folders and their headings (archvow's slugs); free text allowed
                JComboBox<String> combo = new JComboBox<>();
                combo.setEditable(true);
                combo.addItem("");
                List<String> declared = ArchvowBriefs.folders(manifest);
                for (String choice : ArchvowBriefs.choices(ArchvowBriefs.files(project.getBasePath(), declared))) combo.addItem(choice);
                combo.setSelectedItem(text);
                combo.setToolTipText(ArchvowBriefs.foldersLine(declared, combo.getItemCount() - 1, ArchvowI18n.isFrench(project)));
                control = combo;
            } else if (!param.choices.isEmpty() && param.choices.size() <= 4 && !param.hidden) {
                control = new ArchvowSegmented(param.choices, text, param.required);  // design A: one button per value
            } else if (!param.choices.isEmpty()) {
                JComboBox<String> combo = new JComboBox<>();
                combo.addItem(""); for (String choice : param.choices) combo.addItem(choice);
                combo.setSelectedItem(text); control = combo;
            } else if (LONG_TEXT.contains(param.name)) {
                JTextArea input = new JTextArea(3, 24);
                input.setText(text); input.setLineWrap(true); input.setWrapStyleWord(true);
                control = input;
            } else control = new JTextField(text, 25);
            fields.put(param.name, control);
            if (!guided && param.hidden && expertToggle == null) {
                boolean filled = command.parameters.stream().anyMatch(p -> p.hidden && !values.getOrDefault(p.name, "").isBlank());
                expertToggle = new JToggleButton(ArchvowI18n.text(project, "Expert checks", "Contrôles experts"), filled);
                expertToggle.setHorizontalAlignment(SwingConstants.LEFT);
                expertToggle.setToolTipText(ArchvowI18n.text(project,
                    "How the invariant is proven when a test is not enough: a model checked or a proof (--verify model_check|proof --evidence <file>), or a rule that enforces it (--verify static --rule <code>). Empty: the weakest test its severity accepts.",
                    "Comment l'invariant est prouvé quand un test ne suffit pas : un modèle vérifié ou une preuve (--verify model_check|proof --evidence <fichier>), ou une règle qui l'impose (--verify static --rule <code>). Vide : le test le plus faible que sa gravité accepte."));
                JToggleButton toggle = expertToggle;
                toggle.addActionListener(e -> toggleFormSection(toggle, expertParts));
                form.add(toggle, wide(y++, 8));
            }
            boolean more = !param.hidden && !ArchvowForms.essential(name, param);
            if (!guided && more && moreToggle == null) {
                boolean filled = command.parameters.stream().anyMatch(p -> !p.hidden && !ArchvowForms.essential(name, p)
                    && !values.getOrDefault(p.name, "").isBlank());
                java.util.List<String> names = new ArrayList<>();
                for (ArchvowGrammar.Param p : command.parameters)
                    if (!p.hidden && !ArchvowForms.essential(name, p)) names.add(ArchvowForms.label(name, p.name, french).toLowerCase(java.util.Locale.ROOT));
                moreToggle = new JToggleButton(ArchvowI18n.text(project, "More options", "Plus d'options") + "  -  " + String.join(", ", names), filled);
                moreToggle.setHorizontalAlignment(SwingConstants.LEFT);
                JToggleButton toggle = moreToggle;
                toggle.addActionListener(e -> toggleFormSection(toggle, moreParts));
                form.add(toggle, wide(y++, 8));
            }
            GridBagConstraints label = new GridBagConstraints();
            label.gridx = 0; label.gridy = y; label.anchor = GridBagConstraints.NORTHWEST;
            label.insets = new Insets(6, 4, 2, 9);
            // design A: the field in plain words, archvow's option small beside it
            String flag = param.flags.isEmpty() ? "" : param.option();
            JComponent labelText = ArchvowCommandFormLayout.fieldLabel(
                ArchvowForms.label(name, param.name, french), flag, param.required, mutedTextColor());
            form.add(labelText, label);
            JPanel cell = new JPanel(new BorderLayout(2, 2));
            cell.add(control instanceof JTextArea ? scroll(control) : control, BorderLayout.CENTER);
            JPanel under = new JPanel();
            under.setLayout(new BoxLayout(under, BoxLayout.Y_AXIS));
            under.setOpaque(false);
            if ("component add".equals(name) && "type".equals(param.name)) {
                // the chosen type: what it is, its name rule; its kinds follow in --kind
                under.add(formHint(text.isBlank()
                    ? ArchvowI18n.text(project, "Choose a type: its name rule and its kinds follow.", "Choisis un type : sa règle de nom et ses sortes suivent.")
                    : ArchvowComponentTypes.nameHelp(text), false));
            }
            if (field != null && "note".equals(field.kind)) {
                String note = ArchvowChoices.noteNow(field, values);
                if (!note.isBlank()) under.add(formHint(note, false));
            }
            Object empty = control.getClientProperty("archvow.empty");
            if (empty instanceof String why && !why.isBlank()) under.add(formWarning(why));
            String hint = ArchvowForms.hint(name, param.name, french);
            String helpText = hint.isEmpty() ? param.help : hint;
            if (field != null && field.note != null && !"note".equals(field.kind))
                helpText += (helpText.isBlank() ? "" : "  ·  ") + field.note;
            if ("component add".equals(name) && "spec".equals(param.name))
                helpText += ArchvowI18n.text(project,
                    "  ·  Example: US-001 or a ticket reference from your backlog (optional; do not enter a .vow path).",
                    "  ·  Exemple : US-001 ou une référence de ticket (facultatif ; pas un chemin .vow).");
            if (!helpText.isBlank()) under.add(formHint(helpText, true));
            if ("try".equals(name) && "example".equals(param.name)) {
                tryWhere.setFont(new Font(Font.MONOSPACED, Font.PLAIN, tryWhere.getFont().getSize()));
                tryWhere.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
                tryWhere.setAlignmentX(0f);
                under.add(tryWhere);
            }
            if (under.getComponentCount() > 0) cell.add(under, BorderLayout.SOUTH);
            if (("init".equals(name) || "try".equals(name)) && "dir".equals(param.name) && control instanceof JTextField pathField) {
                JButton browse = button(ArchvowI18n.text(project, "Browse…", "Parcourir…"), () -> {
                    JFileChooser chooser = new JFileChooser();
                    chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                    chooser.setDialogTitle(ArchvowI18n.text(project,
                        "Choose destination folder", "Choisir le dossier de destination"));
                    String existing = pathField.getText().trim();
                    File home = new File(existing.isBlank() ? System.getProperty("user.home") : existing);
                    if (home.isDirectory()) chooser.setCurrentDirectory(home);
                    if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                        pathField.setText(chooser.getSelectedFile().getAbsolutePath());
                    }
                });
                JPanel pathPicker = new JPanel(new BorderLayout(5, 0));
                pathPicker.add(control, BorderLayout.CENTER);
                pathPicker.add(browse, BorderLayout.EAST);
                cell.remove(control);
                cell.add(pathPicker, BorderLayout.CENTER);
            }
            GridBagConstraints entry = new GridBagConstraints();
            entry.gridx = 1; entry.gridy = y++; entry.weightx = 1; entry.fill = GridBagConstraints.HORIZONTAL;
            entry.insets = new Insets(3, 2, 3, 4); form.add(cell, entry);
            if (guided) {
                String stage = formStageFor(name, param.name, param.hidden,
                    ArchvowForms.essential(name, param));
                List<Component> parts = formStageComponents.computeIfAbsent(stage, unused -> new ArrayList<>());
                parts.add(labelText);
                parts.add(cell);
            }
            if (param.hidden && expertToggle != null) {
                expertParts.add(labelText);
                expertParts.add(cell);
                labelText.setVisible(expertToggle.isSelected());
                cell.setVisible(expertToggle.isSelected());
            }
            if (more && moreToggle != null) {
                moreParts.add(labelText);
                moreParts.add(cell);
                labelText.setVisible(moreToggle.isSelected());
                cell.setVisible(moreToggle.isSelected());
            }
            if ("init".equals(name) && "package".equals(param.name)) {
                // the template and its questions, right under the package's name (0.12.1: it was asked twice)
                initQuestions = new ArchvowInitQuestions(ArchvowI18n.isFrench(project),
                    () -> loadInitCatalog(true), chosen -> {
                        JComponent template = fields.get("template");
                        if (template instanceof JTextField input) input.setText(chosen);
                    }, own.isEmpty() ? "" : own.get(0));
                initQuestions.onChange(this::updatePreview);
                initQuestions.useParentSteps(() -> {
                    if (!buildingForm && initQuestions != null && "init".equals(guidedCommand)) {
                        updateFormStages(true, form.getComponentCount());
                        updatePreview();
                    }
                });
                form.add(initQuestions, wide(y++, 12));
                if (guided) formStageComponents.computeIfAbsent("Template questions", unused -> new ArrayList<>()).add(initQuestions);
            }
            if ("pack template".equals(name) && "extends".equals(param.name)) {
                JsonObject base = baseTemplate(text);
                if (base != null) {
                    templateSettings = new ArchvowTemplateSettings(base, ArchvowI18n.isFrench(project));
                    form.add(templateSettings, wide(y++, 10));
                    if (guided) formStageComponents.computeIfAbsent("Template questions", unused -> new ArrayList<>()).add(templateSettings);
                } else if (!text.isBlank()) {
                    form.add(formWarning(ArchvowI18n.text(project,
                        "The catalog does not list '" + text + "': its settings cannot be shown here; archvow checks them when the command runs.",
                        "Le catalogue ne liste pas « " + text + " » : ses réglages ne peuvent pas être montrés ici ; archvow les vérifie à l'exécution.")), wide(y++, 6));
                }
            }
        }
        if ("init".equals(name)) {
            // Advanced overrides not present in the template catalog remain possible.
            JTextArea extras = new JTextArea(2, 22);
            extras.setLineWrap(true); extras.setWrapStyleWord(true);
            extras.setToolTipText(ArchvowI18n.text(project,
                "Optional extra answers, one KEY=VALUE per line", "Réponses supplémentaires : KEY=VALUE par ligne"));
            extras.setText(values.getOrDefault("answer", ""));
            fields.put("answer", extras);
            JPanel advanced = new JPanel(new BorderLayout(4, 2));
            advanced.setBorder(BorderFactory.createTitledBorder(ArchvowI18n.text(project,
                "Other answers (optional)", "Autres réponses (facultatif)")));
            advanced.add(scroll(extras), BorderLayout.CENTER);
            form.add(advanced, wide(y++, 4));
            if (guided) formStageComponents.computeIfAbsent("Advanced CLI", unused -> new ArrayList<>()).add(advanced);
            loadInitCatalog();
        } else initQuestions = null;
        if ("decision make".equals(name) || "decision propose".equals(name)
            || "decision accept".equals(name) || "question remove".equals(name)) {
            JPanel previewBox = new JPanel(new BorderLayout(3, 3));
            previewBox.setBorder(BorderFactory.createTitledBorder(
                ArchvowI18n.text(project, "Question and status", "Question et statut")));
            previewBox.add(scroll(commandQuestionPreview), BorderLayout.CENTER);
            JButton refresh = button(ArchvowI18n.text(project, "Load question", "Charger la question"), this::loadCommandQuestion);
            previewBox.add(refresh, BorderLayout.SOUTH);
            previewBox.setPreferredSize(new Dimension(220, 165));
            form.add(previewBox, wide(y++, 12));
            if (guided) formStageComponents.computeIfAbsent("Main settings", unused -> new ArrayList<>()).add(previewBox);
            for (String field : List.of("component", "question", "number")) {
                JComponent input = fields.get(field);
                if (input instanceof JTextField textField) {
                    textField.getDocument().addDocumentListener(new DocumentListener() {
                        @Override public void insertUpdate(DocumentEvent e) { updateCommandQuestion(); }
                        @Override public void removeUpdate(DocumentEvent e) { updateCommandQuestion(); }
                        @Override public void changedUpdate(DocumentEvent e) { updateCommandQuestion(); }
                    });
                }
            }
            updateCommandQuestion();
        }
        for (JComponent control : fields.values()) watchForPreview(control);
        // Put Review before the trailing flexible spacer, otherwise Swing pushes
        // the review content to the very bottom of a tall editor.
        updateFormStages(guided, y);
        GridBagConstraints spacer = new GridBagConstraints(); spacer.gridx = 1; spacer.gridy = y + 1; spacer.weighty = 1;
        spacer.fill = GridBagConstraints.VERTICAL; form.add(new JPanel(), spacer);
        ArchvowUiIcons.decorateTree(form);
        form.revalidate(); form.repaint();
    }

    /** Semantic steps derived solely from the real command parameters. */
    private static String formStageFor(String command, String parameter, boolean hidden, boolean essential) {
        if (hidden) return "Expert checks";
        if ("init".equals(command)) return Set.of("package", "description", "dir", "src").contains(parameter)
            ? "Project" : "Advanced CLI";
        if ("component add".equals(command)) {
            if (Set.of("type", "name", "description", "feature").contains(parameter)) return "Basics";
            if (Set.of("kind", "spec", "uses", "container", "group").contains(parameter)) return "Architecture";
            return "Source & advanced";
        }
        if ("component edit".equals(command)) return Set.of("component", "description", "spec", "uses").contains(parameter)
            ? "Identity & changes" : "Advanced changes";
        if ("pack template".equals(command)) return Set.of("pack", "name", "extends", "description").contains(parameter)
            ? "Template basics" : "Other options";
        if ("check".equals(command)) return Set.of("base", "complete", "file", "stdin").contains(parameter)
            ? "Scope" : "Validation options";
        return essential ? "Main settings" : "Other options";
    }

    /** One top-level row for init's dynamically supplied template questions.
     *  The same Swing input widgets remain alive when the page changes. */
    private void synchronizeInitStages() {
        if (!"init".equals(guidedCommand) || initQuestions == null) return;
        List<Component> template = formStageComponents.get("Template questions");
        // Rebuild the mapping on every catalog/template refresh as well as initial load.
        if (template == null && initQuestions.getParent() == form) template = List.of(initQuestions);
        if (template == null) return;
        LinkedHashMap<String, List<Component>> ordered = new LinkedHashMap<>();
        List<Component> projectParts = formStageComponents.get("Project");
        if (projectParts != null) ordered.put("Project", projectParts);
        for (String group : initQuestions.pageNames()) ordered.put(group, template);
        // Custom company templates without a known catalog still need a way to
        // choose or reload the template, even when they have no known questions.
        if (initQuestions.pageNames().isEmpty()) ordered.put("Template questions", template);
        for (Map.Entry<String, List<Component>> entry : formStageComponents.entrySet())
            if (!"Project".equals(entry.getKey()) && !entry.getValue().contains(initQuestions))
                ordered.put(entry.getKey(), entry.getValue());
        formStageComponents.clear();
        formStageComponents.putAll(ordered);
    }

    /** Use the same green/orange status in the stage label and its numbered ring.
     *  Only real required fields can cause an incomplete state. */
    private boolean formStageReady(String stage, Map<String, String> values) {
        if (initQuestions != null) {
            int page = initQuestions.pageNames().indexOf(stage);
            if (page >= 0) return initQuestions.pageReady(page);
        }
        ArchvowGrammar.Command command = commandList.getSelectedValue();
        if (command == null) return false;
        for (ArchvowGrammar.Param param : command.parameters) {
            if (!param.required || param.plugin || ("init".equals(command.label) && "answer".equals(param.name))) continue;
            String matchingStage = formStageFor(command.label, param.name, param.hidden,
                ArchvowForms.essential(command.label, param));
            if (!stage.equals(matchingStage)) continue;
            if (values.getOrDefault(param.name, "").trim().isEmpty()) return false;
        }
        if ("Template questions".equals(stage) && "init".equals(command.label))
            return !values.getOrDefault("template", "").isBlank();
        return true;
    }

    private static Icon formStepNumber(int value, Color status, boolean complete) {
        return new Icon() {
            @Override public int getIconWidth() { return 22; }
            @Override public int getIconHeight() { return 22; }
            @Override public void paintIcon(Component component, Graphics graphics, int x, int y) {
                Graphics2D g = (Graphics2D) graphics.create();
                try {
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g.setColor(status);
                    g.setStroke(new BasicStroke(1.5f));
                    g.drawOval(x + 1, y + 1, 19, 19);
                    g.setFont(component.getFont().deriveFont(Font.BOLD, 11f));
                    if (complete) {
                        g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                        g.drawLine(x + 6, y + 11, x + 9, y + 14);
                        g.drawLine(x + 9, y + 14, x + 16, y + 7);
                    } else {
                        String text = Integer.toString(value);
                        FontMetrics metrics = g.getFontMetrics();
                        g.drawString(text, x + 11 - metrics.stringWidth(text) / 2,
                            y + 11 + (metrics.getAscent() - metrics.getDescent()) / 2);
                    }
                } finally { g.dispose(); }
            }
        };
    }

    private void refreshFormStepIndicators() {
        if (formStepButtons.getComponentCount() == 0 || !formNavigation.isVisible()) return;
        Map<String, String> values = currentValues();
        List<String> steps = new ArrayList<>(formStageComponents.keySet());
        boolean allReady = steps.stream().allMatch(step -> formStageReady(step, values));
        for (int i = 0; i < formStepButtons.getComponentCount(); i++) {
            if (!(formStepButtons.getComponent(i) instanceof JToggleButton button)) continue;
            boolean complete = i == steps.size() ? allReady : formStageReady(steps.get(i), values);
            Color tone = complete
                ? (darkTheme() ? new Color(0x44C79A) : new Color(0x13814F))
                : (darkTheme() ? new Color(0xF2B95D) : new Color(0xB97812));
            boolean selected = i == formStageIndex;
            button.setIcon(formStepNumber(i + 1, tone, complete));
            button.setForeground(complete || selected ? tone : mutedTextColor());
            button.setOpaque(true);
            button.setContentAreaFilled(true);
            button.setBackground(complete
                ? (darkTheme() ? new Color(0x183D30) : new Color(0xE4F4EB))
                : selected ? (darkTheme() ? new Color(0x383229) : new Color(0xFFF3DE))
                : UIManager.getColor("Panel.background"));
            Color border = complete || selected ? tone : UIManager.getColor("Component.borderColor");
            if (border == null) border = mutedTextColor();
            button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(border, 1, true),
                BorderFactory.createEmptyBorder(4, 9, 4, 9)));
            button.setToolTipText(complete
                ? ArchvowI18n.text(project, "Step complete", "Étape complète")
                : ArchvowI18n.text(project, "Required information missing", "Informations obligatoires manquantes"));
        }
    }

    private void updateFormStages(boolean guided, int reviewRow) {
        formNavigation.setVisible(guided && !formStageComponents.isEmpty());
        formBack.setVisible(guided && !formStageComponents.isEmpty());
        formNext.setVisible(guided && !formStageComponents.isEmpty());
        String previousStep = null;
        List<String> before = new ArrayList<>(formStageComponents.keySet());
        if (formStageIndex >= 0 && formStageIndex < before.size()) previousStep = before.get(formStageIndex);
        formStepButtons.removeAll();
        if (!guided || formStageComponents.isEmpty()) return;
        // The template's question groups use these same outer steps. The question
        // control owns the fields (and their values), not a second nested wizard.
        synchronizeInitStages();
        // The review page is a read-only view of the same controls. The original
        // Submit button and all confirmation/validation logic remain untouched.
        List<String> titles = new ArrayList<>(formStageComponents.keySet());
        titles.add("Review");
        if (previousStep != null && titles.contains(previousStep)) formStageIndex = titles.indexOf(previousStep);
        formStageIndex = Math.max(0, Math.min(formStageIndex, titles.size() - 1));
        for (int index = 0; index < titles.size(); index++) {
            final int step = index;
            JToggleButton button = new JToggleButton(formStageTitle(titles.get(index)), index == formStageIndex);
            button.setHorizontalAlignment(SwingConstants.LEFT);
            button.setIconTextGap(7);
            button.setFocusPainted(false);
            button.addActionListener(e -> changeFormStage(step));
            formStepButtons.add(button);
        }
        if (formReview.getParent() != form) {
            formReview.setEditable(false);
            formReview.setOpaque(false);
            form.add(formReview, wide(reviewRow, 8));
        }
        changeFormStage(formStageIndex);
    }

    private String formStageTitle(String title) {
        if (!ArchvowI18n.isFrench(project)) return title;
        return switch (title) {
            case "Project" -> "Projet";
            case "Basics" -> "Essentiel";
            case "Architecture" -> "Architecture";
            case "Source & advanced" -> "Source et options";
            case "Identity & changes" -> "Identité et modifications";
            case "Advanced changes" -> "Modifications avancées";
            case "Template basics" -> "Création du modèle";
            case "Template questions" -> "Questions du modèle";
            case "Advanced CLI" -> "Options CLI avancées";
            case "Scope" -> "Périmètre";
            case "Validation options" -> "Options de validation";
            case "Main settings" -> "Paramètres principaux";
            case "Other options" -> "Autres options";
            case "Expert checks" -> "Contrôles experts";
            case "Review" -> "Récapitulatif";
            default -> title;
        };
    }

    private void changeFormStage(int index) {
        if (formStageComponents.isEmpty()) return;
        List<String> titles = new ArrayList<>(formStageComponents.keySet());
        formStageIndex = Math.max(0, Math.min(index, titles.size()));
        boolean review = formStageIndex == titles.size();
        String currentStage = review ? "Review" : titles.get(formStageIndex);
        // The same question component participates in several parent steps.
        // Set each component's visibility once, not once per step (which used to
        // let the last group hide it regardless of the selected step).
        Set<Component> stageParts = new LinkedHashSet<>();
        for (List<Component> group : formStageComponents.values()) stageParts.addAll(group);
        for (Component component : stageParts)
            component.setVisible(!review && formStageComponents.get(currentStage).contains(component));
        if (!review && initQuestions != null) {
            int page = initQuestions.pageNames().indexOf(currentStage);
            if (page >= 0) initQuestions.selectParentStep(page);
        }
        if (review) {
            List<Map.Entry<String, String>> items = new ArrayList<>();
            Map<String, String> reviewValues = currentValues();
            ArchvowGrammar.Command command = commandList.getSelectedValue();
            String commandName = command == null ? "" : command.label;
            boolean french = ArchvowI18n.isFrench(project);
            for (Map.Entry<String, JComponent> field : fields.entrySet()) {
                String value = reviewValues.getOrDefault(field.getKey(), "");
                if ("init".equals(commandName) && field.getValue() instanceof JCheckBox)
                    value = "true".equals(value) ? (french ? "Oui" : "Yes") : (french ? "Non" : "No");
                items.add(Map.entry(ArchvowForms.label(commandName, field.getKey(), french), value));
            }
            if (initQuestions != null) items.addAll(initQuestions.reviewAnswers());
            if (templateSettings != null) {
                for (String answer : templateSettings.answers()) {
                    int separator = answer.indexOf('=');
                    items.add(Map.entry(separator < 0 ? "-a" : answer.substring(0, separator),
                        separator < 0 ? answer : answer.substring(separator + 1)));
                }
                for (String layer : templateSettings.layerFolders()) items.add(Map.entry("--layer", layer));
            }
            formReviewItems = items;
            formReviewColumns = -1;
            refreshFormReviewLayout();
        }
        formReview.setVisible(review);
        for (int i = 0; i < formStepButtons.getComponentCount(); i++)
            if (formStepButtons.getComponent(i) instanceof JToggleButton b)
                b.setSelected(i == formStageIndex);
        formStageStatus.setText(formStageTitle(review ? "Review" : titles.get(formStageIndex))
            + "  ·  " + (formStageIndex + 1) + " / " + (titles.size() + 1));
        formBack.setVisible(true);
        formNext.setVisible(true);
        formBack.setEnabled(formStageIndex > 0);
        formNext.setEnabled(!review);
        refreshFormStepIndicators();
        // Reveal the active step horizontally after layout without scrolling the form.
        if (formStepStrip != null) SwingUtilities.invokeLater(() -> {
            if (formStageIndex < formStepButtons.getComponentCount()
                && formStepButtons.getComponent(formStageIndex) instanceof JToggleButton active) {
                Rectangle bounds = new Rectangle(active.getBounds());
                bounds.grow(8, 0);
                formStepStrip.getViewport().scrollRectToVisible(bounds);
            }
        });
        formNavigation.revalidate();
        form.revalidate();
        form.repaint();
        if (formScroller != null) SwingUtilities.invokeLater(() -> formScroller.getVerticalScrollBar().setValue(0));
    }

    private void refreshFormReviewLayout() {
        int width = formScroller == null ? 0 : formScroller.getViewport().getWidth();
        int columns = ArchvowFormReviewGrid.columnsForWidth(width);
        if (formReviewColumns == columns) return;
        formReviewColumns = columns;
        formReview.setText(ArchvowFormReviewGrid.html(formReviewItems, darkTheme(), columns));
        formReview.setCaretPosition(0);
    }

    private static GridBagConstraints wide(int y, int top) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0; c.gridy = y; c.gridwidth = 2;
        c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(top, 5, 8, 5);
        return c;
    }

    /** The line under a field: wraps at the form's width, cannot be focused (no caret in it). */
    private static ArchvowWrapLabel formHint(String text, boolean small) {
        ArchvowWrapLabel hint = new ArchvowWrapLabel();
        hint.setText(text);
        hint.setOpaque(false);
        hint.setAlignmentX(LEFT_ALIGNMENT);
        if (small) hint.setFont(hint.getFont().deriveFont(10.5f));
        hint.setForeground(mutedTextColor());
        return hint;
    }

    private static ArchvowWrapLabel formWarning(String text) {
        ArchvowWrapLabel warning = formHint(text, false);
        warning.setForeground(darkTheme() ? new Color(0xE8, 0xB3, 0x4F) : new Color(0x9B, 0x68, 0x18));
        return warning;
    }

    /** pack template: the base chosen in --extends, as the catalog knows it (the project's, then the installed one). */
    private JsonObject baseTemplate(String name) {
        if (name == null || name.isBlank()) return null;
        for (JsonObject catalog : java.util.Arrays.asList(projectCatalog, liveInitCatalog, ArchvowInitCatalog.bundled()))
            for (JsonObject t : ArchvowGrammar.objects(catalog, "templates"))
                if (name.trim().equals(ArchvowGrammar.str(t, "name"))) return t;
        return null;
    }

    /**
     * A field that offers what exists: a list to choose from (a component, a question with its number, a
     * hazard, a kind...) or suggestions with free typing (a branch, a template). The items are the values the
     * command gets; the list shows what each one is.
     */
    private JComponent choiceControl(String command, String param, ArchvowChoices.Field field, String value, Map<String, String> values) {
        List<ArchvowChoices.Choice> choices = ArchvowChoices.choicesNow(field, values);
        Map<String, String> labels = new LinkedHashMap<>();
        for (ArchvowChoices.Choice c : choices) labels.put(c.value(), c.label());
        JComboBox<String> combo = new JComboBox<>();
        readableChoices(combo);
        if ("suggest".equals(field.kind)) {
            combo.setEditable(true);
            for (ArchvowChoices.Choice c : choices) combo.addItem(c.value());
            combo.setSelectedItem(value);
            combo.setRenderer(choiceRenderer(labels, ""));
            if ("pack template".equals(command) && "extends".equals(param))
                combo.addActionListener(e -> { if (!buildingForm) choose(command, param, comboText(combo)); });
            return combo;
        }
        boolean waiting = field.by != null && values.getOrDefault(field.by, "").isBlank();
        if (waiting && value.isBlank()) {
            combo.addItem("");
            combo.setEnabled(false);
            combo.setRenderer(choiceRenderer(Map.of(), "type".equals(field.by)
                ? ArchvowI18n.text(project, "Choose the type first", "Choisis d'abord le type")
                : ArchvowI18n.text(project, "Choose the component first", "Choisis d'abord le composant")));
            return combo;
        }
        if (choices.isEmpty()) {
            // nothing to choose: say why; a value given (a diagnostic, a component added since) can still be typed
            combo.putClientProperty("archvow.empty", field.empty);
            if (value.isBlank() && !"kind".equals(param)) {
                combo.addItem("");
                combo.setEnabled(false);
                combo.setRenderer(choiceRenderer(Map.of(), ArchvowI18n.text(project, "(none)", "(aucun)")));
                return combo;
            }
            combo.setEditable(true);
            combo.setSelectedItem(value);
            return combo;
        }
        combo.addItem("");
        if (!value.isBlank() && !labels.containsKey(value)) combo.addItem(value);
        for (ArchvowChoices.Choice c : choices) combo.addItem(c.value());
        combo.setSelectedItem(value);
        combo.setRenderer(choiceRenderer(labels, "kind".equals(param)
            ? ArchvowI18n.text(project, "— the default —", "— celle par défaut —")
            : ArchvowI18n.text(project, "— choose —", "— choisir —")));
        combo.addActionListener(e -> { if (!buildingForm) choose(command, param, comboText(combo)); });
        return combo;
    }

    /**
     * A list whose items read "clean_orders — transformation": as wide as its field, not as its longest item;
     * its open list wider, up to 760 px, so the items can be read whole.
     */
    private static void readableChoices(JComboBox<String> combo) {
        combo.setPrototypeDisplayValue("x".repeat(24));
        combo.addPopupMenuListener(new javax.swing.event.PopupMenuListener() {
            @Override public void popupMenuWillBecomeVisible(javax.swing.event.PopupMenuEvent e) {
                Object child = combo.getAccessibleContext().getAccessibleChild(0);
                if (!(child instanceof javax.swing.plaf.basic.ComboPopup popup)) return;
                JList<?> list = popup.getList();
                int wanted = Math.min(760, Math.max(combo.getWidth(), list.getPreferredSize().width + 24));
                if (child instanceof JComponent menu && menu.getComponentCount() > 0 && menu.getComponent(0) instanceof JScrollPane pane) {
                    pane.setPreferredSize(new Dimension(wanted, pane.getPreferredSize().height));
                    pane.setMaximumSize(new Dimension(wanted, pane.getPreferredSize().height));
                }
            }
            @Override public void popupMenuWillBecomeInvisible(javax.swing.event.PopupMenuEvent e) { }
            @Override public void popupMenuCanceled(javax.swing.event.PopupMenuEvent e) { }
        });
    }

    private static String comboText(JComboBox<?> combo) {
        Object item = combo.isEditable() ? combo.getEditor().getItem() : combo.getSelectedItem();
        return item == null ? "" : String.valueOf(item).trim();
    }

    /** The list shows what each value is ("clean_orders — transformation"); the empty one, the placeholder. */
    private static ListCellRenderer<Object> choiceRenderer(Map<String, String> labels, String placeholder) {
        return new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focus) {
                String key = value == null ? "" : String.valueOf(value);
                String shown = key.isEmpty() ? placeholder : labels.getOrDefault(key, key);
                Component c = super.getListCellRendererComponent(list, shown, index, selected, focus);
                if (key.isEmpty() && !selected) c.setForeground(mutedTextColor());
                if (c instanceof JComponent j) j.setToolTipText(shown.length() > 60 ? shown : null);
                return c;
            }
        };
    }

    /**
     * A value chosen in a list: it can fill other fields (Edit spec: the component's description, ticket,
     * uses...; rule edit: its text; decision make: the recorded or suggested answer), and a choice that
     * depended on the old component or type goes. The form is drawn again with it.
     */
    private void choose(String command, String param, String value) {
        Map<String, ArchvowChoices.Field> offered = choiceBook().getOrDefault(command, Map.of());
        Map<String, String> now = currentValues();
        now.put(param, value);
        ArchvowChoices.Field field = offered.get(param);
        Map<String, String> fill = Map.of();
        for (ArchvowChoices.Choice c : ArchvowChoices.choicesNow(field, now))
            if (c.value().equals(value) && c.fill() != null) fill = c.fill();
        if ("component edit".equals(command) && "component".equals(param)) {
            now = new LinkedHashMap<>();
            now.put("component", value);
            now.putAll(fill);
            formBaseline = Map.copyOf(fill);
        } else {
            now.putAll(fill);
            for (Map.Entry<String, ArchvowChoices.Field> other : offered.entrySet()) {
                ArchvowChoices.Field f = other.getValue();
                String current = now.getOrDefault(other.getKey(), "");
                if (param.equals(f.by) && "select".equals(f.kind) && !current.isBlank()
                    && ArchvowChoices.choicesNow(f, now).stream().noneMatch(c -> c.value().equals(current))) now.remove(other.getKey());
            }
        }
        Map<String, String> next = now;
        SwingUtilities.invokeLater(() -> renderForm(next));
    }

    /** What the form holds now, field by field. */
    private Map<String, String> currentValues() {
        Map<String, String> values = new LinkedHashMap<>();
        for (String name : fields.keySet()) {
            String v = value(name);
            if (!v.isEmpty()) values.put(name, v);
        }
        if (!values.containsKey("no_input") && fields.get("no_input") instanceof JCheckBox) values.put("no_input", "");
        return values;
    }

    private void loadInitCatalog() {
        loadInitCatalog(false);
    }

    /**
     * The init form is also used in PyCharm's Welcome Screen: there is no Archvow
     * project there. Populate the built-in questions of the archvow the plugin was built for immediately,
     * and replace them with the installed CLI catalog whenever it responds.
     * Only the real CLI can discover external/company templates.
     */
    private void loadInitCatalog(boolean force) {
        ArchvowInitQuestions wizard = initQuestions;
        if (wizard == null) return;
        String requested = value("template");
        String scope = catalogScope(requested);
        if (!force && liveInitCatalog != null && scope.equals(liveInitCatalogScope)) {
            wizard.setCatalog(liveInitCatalog, requested);
            wizard.setCatalogSource(ArchvowI18n.text(project,
                "Loaded from installed Archvow CLI.", "Chargé depuis Archvow installé."));
            return;
        }

        JsonObject bundled = ArchvowInitCatalog.bundled();
        if (bundled != null) {
            wizard.setCatalog(bundled, requested);
            wizard.setCatalogSource(ArchvowI18n.text(project,
                "Built-in archvow " + ArchvowVersion.KNOWN + " templates · Checking installed catalog…",
                "Modèles intégrés archvow " + ArchvowVersion.KNOWN + " · Vérification du catalogue installé…"));
        } else wizard.setLoading();

        // The catalog has its OWN CLI runner. Do not reject it when a project
        // command happens to be loading, and do not launch a duplicate catalog.
        if (catalogCli.isRunning()) return;
        final String requestedScope = scope;
        catalogCli.executeGlobalCatalog(scope, result -> {
            String completedScope = requestedScope;
            JsonObject loaded = ArchvowGrammar.parseObject(result.stdout);
            boolean valid = result.ok() && ArchvowInitCatalog.hasTemplates(loaded);
            if (valid) {
                liveInitCatalog = loaded;
                liveInitCatalogScope = completedScope == null ? "" : completedScope;
            }
            ArchvowInitQuestions current = initQuestions;
            if (current == null) return;
            String currentRequested = value("template");
            String currentScope = catalogScope(currentRequested);
            if (!currentScope.equals(completedScope)) {
                // The user changed an external template while the catalog was loading.
                loadInitCatalog(false);
                return;
            }
            if (valid) {
                current.setCatalog(loaded, currentRequested);
                current.setCatalogSource(ArchvowI18n.text(project,
                    "Loaded from installed Archvow CLI.", "Chargé depuis Archvow installé."));
            } else if (bundled != null) {
                current.setCatalogSource(ArchvowI18n.text(project,
                    "Built-in archvow " + ArchvowVersion.KNOWN + " catalog (offline). Install/configure Archvow to load company templates.",
                    "Catalogue archvow " + ArchvowVersion.KNOWN + " intégré (hors ligne). Configure Archvow pour charger les modèles d'entreprise."));
            } else current.setError(result.output);
        });
    }

    /** Only a directory for a custom template extends catalog discovery. */
    private static String catalogScope(String requested) {
        if (requested == null || requested.isBlank()) return "";
        try {
            Path path = Path.of(requested).toAbsolutePath().normalize();
            return java.nio.file.Files.isRegularFile(path.resolve("template.yaml"))
                ? path.toString() : "";
        } catch (java.nio.file.InvalidPathException ex) { return ""; }
    }

    private String value(String name) {
        JComponent control = fields.get(name);
        if (control instanceof ArchvowUsesPicker picker) return picker.value();
        if (control instanceof JCheckBox check) return check.isSelected() ? "true" : "";
        if (control instanceof ArchvowSegmented segmented) return segmented.value();
        if (control instanceof JComboBox<?> combo) return comboText(combo);
        if (control instanceof JTextArea area) return area.getText().trim();
        if (control instanceof JTextField text) return text.getText().trim();
        return "";
    }

    private void submitForm() {
        ArchvowGrammar.Command command = commandList.getSelectedValue();
        if (command == null) return;
        if ("specify".equals(command.label)) {
            String id = value("component");
            if (id.isBlank() && selectedComponent != null) id = selectedComponent.id;
            if (id.isBlank()) { tabs.setSelectedIndex(1); message(ArchvowI18n.text(project, "Select a component first.", "Sélectionne d'abord un composant.")); return; }
            selectedComponent = new NodeComponent(id, "", "", ""); guidedNext(); return;
        }
        // The component is known but the rule text is a human decision; never run
        // a malformed "rule add <component>" just because scope was prefilled.
        if ("rule add".equals(command.label) && !value("words").isBlank()
            && value("words").split("\\R").length == 1
            && componentSnapshots.containsKey(value("words").trim())) {
            message(ArchvowI18n.text(project,
                "Add the rule text on a second line after the component id.",
                "Ajoute le texte de la règle sur la deuxième ligne après l'identifiant du composant."));
            return;
        }
        List<String> args = new ArrayList<>(command.path);
        // Edit spec opened with what the component has: a field left as it was is not sent (no change recorded)
        boolean edit = "component edit".equals(command.label) && !formBaseline.isEmpty();
        int changed = 0;
        String output = "";
        for (ArchvowGrammar.Param param : command.parameters) {
            String text = value(param.name);
            if (param.plugin) {  // the plugin's own field (Save to a file): never on archvow's line
                if ("output".equals(param.name)) output = text.trim();
                continue;
            }
            if (edit && !param.required && !text.isBlank() && formBaseline.containsKey(param.name)
                && text.equals(formBaseline.get(param.name).trim())) continue;
            if (!param.required && !text.isBlank()) changed++;
            // a branch that does not exist here: say which exist, rather than git's "Not a valid object name"
            if (!text.isBlank() && ("base".equals(param.name) || ("changes".equals(command.label) && ("start".equals(param.name) || "end".equals(param.name))))) {
                String problem = refProblem(text);
                if (!problem.isBlank()) { message(problem); return; }
            }
            if ("pack template".equals(command.label) && templateSettings != null && ("answer".equals(param.name) || "layer".equals(param.name))) {
                if ("answer".equals(param.name) && !templateSettings.problem().isBlank()) { message(templateSettings.problem()); return; }
                for (String item : "answer".equals(param.name) ? templateSettings.answers() : templateSettings.layerFolders()) {
                    args.add(param.option()); args.add(item);
                }
                continue;
            }
            if ("prompt".equals(command.label) && "task".equals(param.name) && !text.isBlank()) {
                String parsed = ArchvowTaskPrompt.taskNumber(text);
                if (parsed.isBlank()) {
                    message(ArchvowI18n.text(project,
                        "Enter a positive task number, e.g. 1 or T1.",
                        "Saisis un numéro de tâche positif, par exemple 1 ou T1."));
                    return;
                }
                text = parsed;
            }
            if (text.isBlank() && param.required) { message("Required field / Champ obligatoire : " + param.name); return; }
            if (param.bool) {
                if ("true".equals(text)) args.add(param.option());
            } else if (!text.isBlank()) {
                if (param.multiToken) {
                    for (String item : text.split("\\R")) if (!item.isBlank()) args.add(item.trim());
                } else if (param.append) {
                    for (String item : text.split("\\R")) if (!item.isBlank()) {
                        if (!param.flags.isEmpty()) args.add(param.option()); args.add(item.trim());
                    }
                } else {
                    if (!param.flags.isEmpty()) args.add(param.option());
                    args.add(text);
                }
            }
        }
        if (edit && changed == 0) {
            message(ArchvowI18n.text(project, "Nothing changed: change a field first (a blank field keeps its value).",
                "Rien n'a changé : modifie d'abord un champ (un champ vide garde sa valeur)."));
            return;
        }
        if ("init".equals(command.label) && initQuestions != null) {
            // Explicit advanced entries win over the wizard's defaults.
            java.util.Set<String> advancedKeys = new java.util.HashSet<>();
            for (String line : value("answer").split("\\R")) {
                String key = line.split("=", 2)[0].trim();
                if (!key.isBlank()) advancedKeys.add(key);
            }
            for (String answer : initQuestions.answers()) {
                if (!advancedKeys.contains(answer.split("=", 2)[0])) {
                    args.add("--answer"); args.add(answer);
                }
            }
        }
        if ("init".equals(command.label) && !args.contains("--no-input")) args.add("--no-input");
        if (!output.isEmpty()) {
            // changes / diff with Save to a file: the report as archvow prints it for a person, written to the file
            if (ArchvowForms.markdownOutput(command) && !args.contains("--markdown")) args.add("--markdown");
            writeReport(command.label, List.copyOf(args), output);
            return;
        }
        if (command.json && !args.contains("--json")) args.add("--json");
        boolean destructive = DESTRUCTIVE.contains(command.label);
        boolean mutating = destructive || ("completion".equals(command.label) ? args.contains("--install") : !READ_ONLY.contains(command.path.get(0)));
        // The confirmation is displayed inline after all arguments have been prepared.
        if (destructive && !args.contains("--yes")) args.add("--yes");
        String stdin = null;
        if ("check".equals(command.label) && args.contains("--stdin")) {
            var editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
            var chosen = FileEditorManager.getInstance(project).getSelectedFiles();
            if (editor == null || chosen.length == 0) { message("Open the file to check in an editor / Ouvre le fichier à vérifier dans un éditeur."); return; }
            String absolute = chosen[0].getPath();
            if (args.contains("--file")) {
                String requested = args.get(args.indexOf("--file") + 1);
                File resolved = new File(requested);
                if (!resolved.isAbsolute()) resolved = new File(project.getBasePath(), requested);
                try {
                    if (!resolved.getCanonicalPath().equals(new File(absolute).getCanonicalPath())) {
                        message("--file must match the open file / --file doit correspondre au fichier ouvert."); return;
                    }
                } catch (IOException e) { message(e.getMessage()); return; }
            } else { args.add("--file"); args.add(absolute); }
            stdin = editor.getDocument().getText();
        }
        final String finalStdin = stdin;
        final List<String> prepared = List.copyOf(args);
        if (mutating) {
            requestConfirmation(command.label, ArchvowConfirmPanel.preview(prepared),
                () -> executeDirect(command.label, prepared, finalStdin, true), false);
        } else executeDirect(command.label, prepared, finalStdin, false);
    }

    /** changes / diff with Save to a file: archvow's report written to the project's file, then opened (0.15.0). */
    private void writeReport(String label, List<String> args, String output) {
        String root = project.getBasePath();
        if (root == null) return;
        Path base = Path.of(root).toAbsolutePath().normalize();
        Path file = base.resolve(output).normalize();
        if (!file.startsWith(base)) {
            message(ArchvowI18n.text(project, output + " is outside the project: choose a file inside it.", output + " est hors du projet : choisis un fichier à l'intérieur."));
            return;
        }
        commandResultCard.setVisible(true);
        commandResult.setText(htmlMessage("<b>⏳ " + escape(ArchvowI18n.text(project, "Running", "Exécution")) + "</b> · "
            + escape(ArchvowForms.commandLine(commandList.getSelectedValue(), currentValues(), ArchvowI18n.isFrench(project)))));
        cli.executeAction(label, args, null, result -> {
            if (!result.ok()) { storeCommandResult(commandResult, label, result); return; }
            try {
                java.nio.file.Files.createDirectories(file.getParent());
                String text = result.stdout.endsWith("\n") ? result.stdout : result.stdout + "\n";
                java.nio.file.Files.writeString(file, text, java.nio.charset.StandardCharsets.UTF_8);
            } catch (IOException e) { message(e.getMessage()); return; }
            long lines = result.stdout.lines().filter(l -> !l.isBlank()).count();
            String relative = base.relativize(file).toString();
            commandResult.setText(htmlMessage("<b>✔ " + escape(ArchvowI18n.text(project, "Saved " + relative + " (" + lines + " lines)",
                "Enregistré " + relative + " (" + lines + " lignes)")) + "</b> · " + escape("archvow " + String.join(" ", args))));
            var found = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(file);
            if (found != null) FileEditorManager.getInstance(project).openFile(found, true);
        });
    }

    private void selectCommand(String name) {
        for (int i = 0; i < commandModel.size(); i++) {
            if (commandModel.get(i).label.equals(name)) { commandList.setSelectedIndex(i); return; }
        }
    }

    private void openCommand(String name, Map<String, String> initial) {
        if (role == Role.NAVIGATION) {
            ArchvowEditorTab.showCommand(project, name, initial == null ? Map.of() : initial);
            return;
        }
        showWorkspace();
        String knownComponent = selectedComponent == null ? "" : selectedComponent.id;
        if (role == Role.DETAIL) {
            // from a component (Edit spec, + Question, Accept...): Back returns to it; from the tool window: none
            boolean onComponent = tabs.getSelectedIndex() == 1 && !knownComponent.isBlank()
                && (componentViewNav == null || componentViewNav.activeIndex() != 7);
            if (fromToolWindow) backComponent = null;
            else if (onComponent) { backComponent = knownComponent; backView = componentViewNav == null ? 0 : componentViewNav.activeIndex(); }
        }
        String target = initial == null ? knownComponent : initial.getOrDefault("component", knownComponent);
        JsonObject snapshot = componentSnapshots.get(target);
        Map<String, String> known = new java.util.LinkedHashMap<>(
            ArchvowCommandPrefill.prepare(name, initial, knownComponent, snapshot, componentEntry(target)));
        if ("component add".equals(name) && !known.containsKey("container")) {
            // The currently selected component supplies a grounded container, not a
            // made-up new component name/description.
            String container = containerForComponent(knownComponent);
            if (!container.isBlank()) known.put("container", container);
        }
        selectCommand(name);
        if (commandList.getSelectedValue() == null || !commandList.getSelectedValue().label.equals(name)) {
            message("Unknown command / Commande inconnue : " + name); return;
        }
        // Edit spec: what the component has now (not what a diagnostic asks to change): only differences are sent
        formBaseline = "component edit".equals(name) ? whatTheComponentHas(target) : Map.of();
        formHelpExtra = "";
        if ("component edit".equals(name) && snapshot != null) {
            formHelpExtra += "\n\n" + ArchvowI18n.text(project,
                "The fields hold what the component has: change what must change; only that is sent. The decisions, hazards and "
                    + "examples recorded stay (from archvow 1.18, a person is asked to look at them again after such a change).",
                "Les champs contiennent ce que le composant a : change ce qui doit changer ; seul cela est envoyé. Les décisions, "
                    + "hazards et exemples enregistrés restent (depuis archvow 1.18, une personne doit les revoir après un tel changement).");
        } else if (!known.isEmpty()) {
            formHelpExtra += "\n" + ArchvowI18n.text(project,
                "Known information prefilled from the current spec or diagnostic. Review before running; blank optional fields are unchanged.",
                "Informations connues préremplies depuis la spec ou le diagnostic. Vérifie avant d'exécuter ; les champs optionnels vides restent inchangés.");
        }
        renderForm(known);
        tabs.setSelectedIndex(2);
        afterSelectionChange();
    }

    /** Edit spec's baseline: the fields the component already has, as the choice of the component fills them. */
    private Map<String, String> whatTheComponentHas(String id) {
        ArchvowChoices.Field field = choiceBook().getOrDefault("component edit", Map.of()).get("component");
        for (ArchvowChoices.Choice c : ArchvowChoices.choicesNow(field, Map.of()))
            if (c.value().equals(id)) return c.fill() == null ? Map.of() : c.fill();
        return Map.of();
    }

    /** The editor tab: a command chosen in the tool window (its form, prefilled with what is known). */
    void showCommand(String name, Map<String, String> initial) {
        fromToolWindow = true;
        try { openCommand(name, initial); } finally { fromToolWindow = false; }
    }

    /** The component's entry in the spec (module, ticket, owners...), or null. */
    private JsonObject componentEntry(String id) {
        for (JsonObject c : ArchvowGrammar.objects(manifest, "containers"))
            for (JsonObject x : ArchvowGrammar.objects(c, "components"))
                if (id != null && id.equals(ArchvowGrammar.str(x, "id"))) return x;
        return null;
    }

    /** Back: the component a command was opened from, on the view it was on. */
    private void goBack() {
        if (skillBackToBriefs && "skill".equals(detailHomeMode)) {
            skillBackToBriefs = false;
            openBriefsDetails();
            return;
        }
        String id = backComponent;
        int view = backView;
        backComponent = null;
        if (id == null) return;
        showComponent(id);
        selectComponentView(view);
        afterSelectionChange();
    }

    private void executeDirect(String label, List<String> args, String stdin, boolean refreshAfter) {
        executeDirect(label, args, stdin, refreshAfter, false);
    }
    private void executeDirect(String label, List<String> args, String stdin, boolean refreshAfter, boolean fromQuickItem) {
        boolean initialization = args.contains("init") && !args.isEmpty() && "init".equals(args.get(0));
        boolean version = args.equals(List.of("--version"));
        ArchvowProjectReadiness.Status status = ArchvowProjectReadiness.inspect(project.getBasePath());
        if (!status.ready() && !(initialization && status.canInitialize())
            && !(version && !status.root().isBlank() && new File(status.root()).isDirectory())) {
            showWelcome(status, "");
            return;
        }
        if (role == Role.NAVIGATION) {
            // Overview's commands: their result in the editor tab
            ArchvowPanel detail = ArchvowEditorTab.panel(project);
            if (detail != null) { detail.runFromOverview(label, args, stdin, refreshAfter); return; }
        }
        int origin = tabs.getSelectedIndex();
        // The component-local Other actions run inside Components, not Overview.
        boolean quick = fromQuickItem && origin == 1 && selectedQuickItem != null;
        JEditorPane destination = origin == 2 ? commandResult : quick ? quickResult :
            origin == 1 ? componentResult : overviewResult;
        JPanel resultCard = origin == 2 ? commandResultCard : quick ? quickResultCard :
            origin == 1 ? componentResultCard : overviewResultCard;
        if (resultCard == null) return;
        resultCard.setVisible(true);
        if (quick && quickSelectionArea != null) quickSelectionArea.setVisible(true);
        destination.setText(htmlMessage("<b>⏳ " + escape(ArchvowI18n.text(project, "Running", "Exécution"))
            + "</b> · " + escape("archvow " + String.join(" ", args))));
        destination.setCaretPosition(0);
        revalidate();
        cli.executeAction(label, args, stdin, result -> {
            storeCommandResult(destination, label, result);
            destination.setToolTipText(String.join(" ", result.arguments));
            if (!args.isEmpty() && "check".equals(args.get(0)) && !result.interrupted) {
                JsonObject parsed = ArchvowGrammar.parseObject(result.stdout);
                if (parsed != null && parsed.has("violations")) {
                    check = parsed;
                    checkFailure = "";
                    diagnostics.showData(check, checkFailure, ArchvowI18n.isFrench(project));
                    detailDiagnostics.showData(check, checkFailure, ArchvowI18n.isFrench(project));
                    if (origin == 0) narrowOverviewTabs.setSelectedIndex(1);
                    ArchvowPanel nav = role == Role.DETAIL ? navigation() : null;
                    if (nav != null) nav.acceptCheck(parsed);
                    // Reconcile component markers with the most recent engine check.
                    if (manifest != null) updateOverview();
                }
            }
            if (initialization && result.ok()) {
                com.intellij.notification.NotificationGroupManager.getInstance().getNotificationGroup("Archvow")
                    .createNotification(ArchvowI18n.text(project, "Archvow initialized successfully. The project architecture is ready.",
                        "Archvow initialisé avec succès. L’architecture du projet est prête."), com.intellij.notification.NotificationType.INFORMATION)
                    .notify(project);
                reload(false);
            }
            else if (refreshAfter && result.ok()) {
                ownLoad = true;  // the vow change that follows is this action's: the watcher stays quiet
                ownChangeUntil = System.currentTimeMillis() + 3000;
                reload(false);
            }
        });
    }

    /** Until when a vow change comes from the plugin's own action, already read again (0.19.0). */
    private volatile long ownChangeUntil;

    /** The load after the plugin's own action (0.21.0): the vow watcher stays quiet a moment after it ends. */
    private boolean ownLoad;

    /** The last {@code workspace --json} answer's digest, and the archvow that gave it (--since, 0.21.0). */
    private String answeredDigest, answeredBy;

    /** The session log's lines in idea.log too: reads at debug level, actions at info, failures as warnings (0.19.0). */
    private static final ArchvowRunLog.Sink IDEA_LOG = new ArchvowRunLog.Sink() {
        private final com.intellij.openapi.diagnostic.Logger log = com.intellij.openapi.diagnostic.Logger.getInstance(ArchvowRunLog.class);
        @Override public void info(String line) { log.info(line); }
        @Override public void debug(String line) { log.debug(line); }
        @Override public void warn(String line) { log.warn(line); }
    };

    /**
     * archvow's version and contract: {@code archvow version --json} (1.23); an older archvow has no {@code version}
     * command, its --version gives the number (and no contract: it is older than the plugin needs).
     */
    private void askVersion(java.util.function.Consumer<ArchvowVersion.Said> then) {
        cli.execute("Version", List.of("version", "--json"), null, result -> {
            ArchvowVersion.Said said = ArchvowVersion.said(result.ok() ? result.stdout : "");
            if (!said.version().isBlank()) {
                then.accept(said);
                return;
            }
            cli.execute("Version", List.of("--version"), null, old -> then.accept(new ArchvowVersion.Said(ArchvowVersion.number(old.output), "")));
        });
    }

    /** The session log's header, once archvow said its version (0.19.0) and contract (0.20.0). */
    private void logSession(ArchvowVersion.Said said) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("plugin", ArchvowVersion.plugin());
        fields.put("archvow", said.version());
        if (!said.contract().isBlank()) fields.put("contract", said.contract());
        fields.put("python", cli.program());
        fields.put("project", String.valueOf(project.getBasePath()));
        fields.put("ide", com.intellij.openapi.application.ApplicationInfo.getInstance().getFullApplicationName());
        ArchvowRunLog.header(project, fields);
    }

    /** After archvow said its version: why the install would be offered, and the notification with "Install archvow". */
    private void versionChecked(ArchvowVersion.Said said) {
        installReason = ArchvowInstall.reason(said);
        String warning = ArchvowVersion.warning(said, ArchvowVersion.CONTRACT, ArchvowVersion.KNOWN, ArchvowI18n.isFrench(project));
        if (warning.isBlank()) return;
        var note = com.intellij.notification.NotificationGroupManager.getInstance().getNotificationGroup("Archvow")
            .createNotification(warning, com.intellij.notification.NotificationType.WARNING);
        note.addAction(com.intellij.notification.NotificationAction.createSimpleExpiring(
            ArchvowI18n.text(project, "Install archvow " + ArchvowVersion.KNOWN, "Installer archvow " + ArchvowVersion.KNOWN),
            this::askInstall));
        note.notify(project);
    }

    /** Stops watching the vows (an editor tab closed). */
    void dispose() { Disposer.dispose(lifetime); }

    void reload() { reload(role == Role.NAVIGATION); }
    private void reload(boolean switchToOverview) { reload(switchToOverview, false); }

    /**
     * Reads the project: {@code archvow workspace --json} (archvow 1.25), the spec, every component, check, next, the
     * catalog and the briefs in one process. {@code keep} (a vow changed under the plugin): --since the last answer,
     * archvow says {@code unchanged} while no file changed and the panel keeps what it shows; never after an action
     * or when a person asks.
     */
    private void reload(boolean switchToOverview, boolean keep) {
        if (role == Role.DETAIL) {
            // the tool window reads the project and hands it over; alone, the tab reads it itself
            ArchvowPanel nav = navigation();
            if (nav != null) {
                if (nav.manifest != null && !nav.refreshing) adoptFrom(nav);
                nav.reload(false, keep);
                return;
            }
        }
        if (refreshing || cli.isRunning()) return;
        loadGitBranch();
        ArchvowProjectReadiness.Status status = ArchvowProjectReadiness.inspect(project.getBasePath());
        projectStatus = status;
        if (!status.ready()) {
            manifest = null; check = null; selectedView = null; selectedComponent = null;
            nextResult = ""; checkFailure = ""; componentSnapshots.clear();
            pendingModel.clear(); resolvedModel.clear();
            pendingByComponent.clear(); resolvedByComponent.clear();
            componentsTree.putClientProperty("archvow.pendingCounts", Map.of());
            componentsTree.putClientProperty("archvow.resolvedCounts", Map.of());
            if (quickSelectionArea != null) quickSelectionArea.setVisible(false);
            if (overviewResultCard != null) overviewResultCard.setVisible(false);
            if (commandResultCard != null) commandResultCard.setVisible(false);
            answeredDigest = null;
            if (!versionChecked && status.canInitialize() && !cli.isRunning()) {
                // not initialized yet: is archvow there to initialize it? (0.12.0: the welcome card offers to install it)
                versionChecked = true;
                askVersion(said -> {
                    installReason = ArchvowInstall.reason(said);
                    showWelcome(ArchvowProjectReadiness.inspect(project.getBasePath()), "");
                });
            }
            showWelcome(status, "");
            pushToDetail();
            return;
        }
        showWorkspace();
        if (!versionChecked) {
            versionChecked = true;
            askVersion(said -> {
                versionChecked(said);
                logSession(said);
                reload(switchToOverview, keep);
            });
            return;
        }
        refreshing = true;
        ArchvowRunLog.refreshStarted(project, "Refresh");
        if (switchToOverview) tabs.setSelectedIndex(0);
        List<String> arguments = new ArrayList<>(List.of("workspace", "--json"));
        if (keep && manifest != null && answeredDigest != null && cli.program().equals(answeredBy))
            arguments.addAll(List.of("--since", answeredDigest));
        else overviewSummary.showLoading(ArchvowI18n.text(project, "Loading Archvow architecture…", "Chargement de l'architecture Archvow…"));
        // one command: ArchvowCli runs one at a time and refuses a second one (0.16.0 started `brief show` beside
        // `check`: check was refused, next, the catalog and the components never ran); one process has no chain
        cli.execute("Workspace", arguments, null, read -> {
            boolean own = ownLoad;
            ownLoad = false;
            if (read.interrupted) {
                refreshing = false;
                ArchvowRunLog.refreshEnded(project);
                overviewSummary.showFailure(ArchvowI18n.text(project,
                    "Loading cancelled or timed out. You can retry with Refresh.",
                    "Chargement annulé ou délai dépassé. Tu peux relancer avec Rafraîchir."));
                return;
            }
            WorkspaceAnswer answer = WorkspaceAnswer.read(read.stdout);
            if (read.ok() && answer != null && answer.unchanged) {  // what the panel shows still holds
                refreshing = false;
                ArchvowRunLog.refreshEnded(project);
                return;
            }
            if (!read.ok() || answer == null || answer.unchanged) {
                answeredDigest = null;
                refreshing = false;
                ArchvowRunLog.refreshEnded(project);
                String detail = read.output == null ? "" : read.output.trim();
                String hint = ArchvowResultFormatter.advice(detail, ArchvowI18n.isFrench(project));
                String explanation = !hint.isBlank() ? hint :
                    ArchvowI18n.text(project,
                        "Archvow could not read the specification. Verify Python, CLI installation and .archvow/project.vow.",
                        "Archvow n'a pas pu lire la spécification. Vérifie Python, l'installation du CLI et .archvow/project.vow.");
                showWelcome(status, !hint.isBlank() ? hint : explanation +
                    (detail.isBlank() ? "" : "\n" + shorten(detail, 280)));
                pushToDetail();
                return;
            }
            answeredDigest = answer.digest;
            answeredBy = cli.program();
            projectFeatures = answer.features;
            manifest = answer.spec; rebuildTree();
            check = answer.check;
            checkFailure = "";
            nextResult = answer.next;
            // the project's catalog: its template's types and kinds, its own templates (component add, pack template)
            if (ArchvowInitCatalog.hasTemplates(answer.catalog)) projectCatalog = answer.catalog;
            projectPacks = answer.packs;  // archvow 1.26: the project's packs
            firstStepsJson = answer.firstSteps;  // archvow 2.1: the first steps of a new project
            projectFeatures = answer.features;  // archvow 2.0: each feature
            componentSnapshots.clear();
            componentSnapshots.putAll(answer.snapshots);
            briefStatus = answer.briefs;  // archvow 1.20: what each brief section gives
            followBestBase();
            updateOverview();
            refreshing = false;
            if (own) ownChangeUntil = System.currentTimeMillis() + 3000;
            ArchvowRunLog.refreshEnded(project);
            if (role == Role.DETAIL && selectedComponent != null) loadComponent(selectedComponent);
            pushToDetail();
        });
    }

    private void rebuildTree() {
        String previous = selectedComponent == null ? null : selectedComponent.id;
        rebuilding = true;
        componentsRoot.removeAllChildren();
        if (groupByFeature) {
            buildFeatureTree();
            ((DefaultTreeModel) componentsTree.getModel()).reload();
            for (int i = 0; i < componentsTree.getRowCount(); i++) componentsTree.expandRow(i);
            if (previous != null) locate(componentsRoot, new ArrayList<>(), previous);
            rebuilding = false;
            return;
        }
        for (JsonObject container : ArchvowGrammar.objects(manifest, "containers")) {
            String containerId = ArchvowGrammar.str(container, "id");
            DefaultMutableTreeNode containerNode = new DefaultMutableTreeNode(new ArchvowTreeIcons.Node(containerId, "system", ""));
            Map<String, DefaultMutableTreeNode> layers = new LinkedHashMap<>();
            // 0.15.0: under All, the template's layers in their order, an empty one too (it can get its first component)
            if ("all".equals(componentFilter) && componentsRoot.getChildCount() == 0)
                for (String layer : addableByLayer().keySet()) {
                    DefaultMutableTreeNode layerNode = new DefaultMutableTreeNode(new ArchvowTreeIcons.Node(layer, "layer", layer));
                    layers.put(layer, layerNode); containerNode.add(layerNode);
                }
            for (JsonObject component : ArchvowGrammar.objects(container, "components")) {
                String id = ArchvowGrammar.str(component, "id");
                if (!showComponentInTree(id)) continue;
                String layer = ArchvowGrammar.str(component, "layer");
                DefaultMutableTreeNode layerNode = layers.get(layer);
                if (layerNode == null) {
                    layerNode = new DefaultMutableTreeNode(new ArchvowTreeIcons.Node(layer, "layer", layer));
                    layers.put(layer, layerNode); containerNode.add(layerNode);
                }
                layerNode.add(new DefaultMutableTreeNode(new NodeComponent(id,
                    ArchvowGrammar.str(component, "type"), layer, containerId)));
            }
            for (DefaultMutableTreeNode layerNode : layers.values())
                if (layerNode.getChildCount() == 0) layerNode.add(new DefaultMutableTreeNode(new ArchvowTreeIcons.Node(
                    ArchvowI18n.text(project, "No component yet", "Pas encore de composant"),
                    "add", ((ArchvowTreeIcons.Node) layerNode.getUserObject()).layer())));
            if (containerNode.getChildCount() > 0) componentsRoot.add(containerNode);
        }
        ((DefaultTreeModel) componentsTree.getModel()).reload();
        for (int i = 0; i < componentsTree.getRowCount(); i++) componentsTree.expandRow(i);
        if (previous != null) locate(componentsRoot, new ArrayList<>(), previous);
        rebuilding = false;
    }

    /** By feature (0.24.0): each feature - what the product does first, by name, then shared - with its components. */
    private void buildFeatureTree() {
        List<String> names = new ArrayList<>();
        for (JsonObject f : ArchvowGrammar.objects(manifest, "features")) names.add(ArchvowGrammar.str(f, "name"));
        names.sort((a, b) -> "shared".equals(a) ? 1 : "shared".equals(b) ? -1 : a.compareTo(b));
        for (String name : names) {
            DefaultMutableTreeNode featureNode = new DefaultMutableTreeNode(new ArchvowTreeIcons.Node(name, "feature", ""));
            for (JsonObject container : ArchvowGrammar.objects(manifest, "containers")) {
                String containerId = ArchvowGrammar.str(container, "id");
                for (JsonObject component : ArchvowGrammar.objects(container, "components")) {
                    String id = ArchvowGrammar.str(component, "id");
                    if (!name.equals(ArchvowGrammar.str(component, "feature")) || !showComponentInTree(id)) continue;
                    featureNode.add(new DefaultMutableTreeNode(new NodeComponent(id, ArchvowGrammar.str(component, "type"),
                        ArchvowGrammar.str(component, "layer"), containerId)));
                }
            }
            if (featureNode.getChildCount() == 0 && !"all".equals(componentFilter)) continue;
            if (featureNode.getChildCount() == 0) featureNode.add(new DefaultMutableTreeNode(new ArchvowTreeIcons.Node(
                ArchvowI18n.text(project, "No component yet", "Pas encore de composant"), "add", "")));
            componentsRoot.add(featureNode);
        }
    }

    private boolean locate(DefaultMutableTreeNode node, List<Object> path, String id) {
        path.add(node);
        if (node.getUserObject() instanceof NodeComponent comp && comp.id.equals(id)) {
            componentsTree.setSelectionPath(new TreePath(path.toArray())); return true;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (locate((DefaultMutableTreeNode) node.getChildAt(i), path, id)) return true;
        }
        path.remove(path.size() - 1); return false;
    }

    private void loadComponent(NodeComponent component) {
        componentDetails.setText(htmlMessage("<b>" + escape(ArchvowI18n.text(project,"Loading", "Chargement")) + "</b>: " + escape(component.id)));
        JsonObject cached = componentSnapshots.get(component.id);
        if (cached != null) {
            selectedView = cached;
            renderComponent(cached);
        }
        cli.execute("Component " + component.id, List.of("show", component.id, "--json"), null, result -> {
            if (selectedComponent == null || !selectedComponent.id.equals(component.id)) return;
            JsonObject data = ArchvowGrammar.componentView(result.stdout, component.id);
            if (data == null) {
                boolean french = ArchvowI18n.isFrench(project);
                componentDetails.setText(htmlMessage(escape(ArchvowGrammar.parseObject(result.stdout) == null ? result.output
                    : french ? "« " + component.id + " » n'est plus un composant du projet." : "'" + component.id + "' is no longer a component of the project.")));
                return;
            }
            componentSnapshots.put(component.id, data);
            selectedView = data;
            renderComponent(data);
        });
    }

    private void renderComponent(JsonObject data) {
        if (constructed && role == Role.DETAIL) SwingUtilities.invokeLater(this::afterSelectionChange);
        componentDetails.setText(buildComponentHtml(data));
        if (aiAcceptButton != null) aiAcceptButton.setVisible("ai".equals(ArchvowGrammar.str(data, "origin")));
        componentDetails.setCaretPosition(0);
        // the same component drawn again (after an answer, a reload): each view keeps its card; another
        // component: each view starts on its first open item, the answer panel ready
        String id = ArchvowGrammar.str(data, "component");
        boolean another = !id.equals(renderedComponent);
        renderedComponent = id;
        boolean fr = ArchvowI18n.isFrench(project);
        aiView = ArchvowAiSkills.view(data, launched(id));
        decisionsList.putClientProperty("archvow.french", fr);
        safetyList.putClientProperty("archvow.french", fr);
        List<JsonObject> decisionRows = ArchvowGrammar.objects(data, "decisions");
        List<ArchvowItemCards.Card> decisionCards = new ArrayList<>();
        for (int i = 0; i < decisionRows.size(); i++) {
            ArchvowItemCards.Card card = ArchvowItemCards.decision(decisionRows.get(i), fr)
                .withOrigin(i < aiView.decisions.size() ? aiView.decisions.get(i) : null);
            if (ArchvowItemCards.shown(card, originFilter)) decisionCards.add(card);
        }
        if (another) advance = "";
        if (fill(decisionsList, decisionsModel, decisionCards, another, advance.startsWith("D") ? advance.substring(1) : null)) advance = "";
        if (fill(questionList, questionModel, ArchvowGrammar.objects(data, "questions").stream()
            .map(q -> ArchvowItemCards.question(q, fr)).toList(), another, advance.startsWith("Q") ? advance.substring(1) : null)) advance = "";
        List<JsonObject> safetyRows = ArchvowGrammar.objects(data, "safety");
        List<ArchvowItemCards.Card> safetyCards = new ArrayList<>();
        for (JsonObject q : safetyRows) {
            ArchvowAiSkills.Origin origin = null;
            for (JsonObject h : ArchvowGrammar.objects(q, "hazards")) {
                ArchvowAiSkills.Origin o = aiView.hazards.get(ArchvowGrammar.str(h, "id"));
                if (origin == null || (o != null && o.toReview())) origin = o;
            }
            ArchvowItemCards.Card card = ArchvowItemCards.safety(q, fr).withOrigin(origin);
            if (ArchvowItemCards.shown(card, originFilter)) safetyCards.add(card);
        }
        fill(safetyList, safetyModel, safetyCards, another);
        fill(assumptionList, assumptionModel, ArchvowGrammar.objects(data, "assumptions").stream()
            .map(a -> ArchvowItemCards.assumption(a, fr)).toList(), another);
        showAssumption();
        ArchvowDataView.renderRules(rulesOnlyDetails, data, aiView.rules, ArchvowI18n.isFrench(project));
        if (examplesView != null) {
            examplesView.show(data, fr);
            if (componentViewButtons.size() > 8) {
                JToggleButton chip = componentViewButtons.get(8);
                int[] counts = ArchvowExamples.counts(data);
                chip.setText(ArchvowExamples.label(data, fr));  // always shown, as Decisions and Questions
                chip.setForeground(counts[2] > 0 ? new Color(207, 34, 46) : null);
            }
        }
        refreshRulesAiActions();
        refreshSuggestion();
        ArchvowDataView.render(rulesDetails, data, ArchvowI18n.isFrench(project));
        refreshDataActions(data);
        ArchvowVowViewer.display(vowDetails,
            ArchvowVowViewer.read(project.getBasePath(), ArchvowGrammar.str(data, "component")));
        readCard(ArchvowGrammar.str(data, "component"));
        boolean frFiles = ArchvowI18n.isFrench(project);
        componentFilesView.showRows(ArchvowFilePlan.componentFiles(manifest, ArchvowGrammar.str(data, "component"), frFiles), frFiles,
            frFiles ? "Où il est" : "Where it is");
        cardDetails.setCaretPosition(0);
        assumptionDetails.setCaretPosition(0);
        rulesDetails.setCaretPosition(0);
        rulesOnlyDetails.setCaretPosition(0);
        vowDetails.setCaretPosition(0);
        showDecision();
        showSafety();
        showQuestion();
        refreshViewStatus(data);
        refreshComponentOtherModels();
        updateComponentPendingExplanation();
    }

    /** A view's cards, its selection kept (same component) or on its first open item (another one). */
    private static void fill(JList<ArchvowItemCards.Card> list, DefaultListModel<ArchvowItemCards.Card> model,
                             List<ArchvowItemCards.Card> cards, boolean another) {
        fill(list, model, cards, another, null);
    }

    /**
     * The same; {@code saved} (0.17.0, Clarify is no button any more): the number of the item just saved - once it
     * is answered, the next open one is selected. True when it moved on (the save is done with).
     */
    private static boolean fill(JList<ArchvowItemCards.Card> list, DefaultListModel<ArchvowItemCards.Card> model,
                                List<ArchvowItemCards.Card> cards, boolean another, String saved) {
        ArchvowItemCards.Card previous = list.getSelectedValue();
        model.clear();
        for (ArchvowItemCards.Card card : cards) model.addElement(card);
        int first = ArchvowItemCards.firstOpen(cards);
        int index = another || previous == null ? first : ArchvowItemCards.indexOf(cards, previous.number(), first);
        boolean moved = false;
        if (saved != null && !another && index >= 0 && cards.get(index).number().equals(saved) && !cards.get(index).open()) {
            int next = ArchvowItemCards.nextOpenAfter(cards, index);
            if (next >= 0) index = next;
            moved = true;
        }
        if (index >= 0) {
            list.setSelectedIndex(index);
            list.ensureIndexIsVisible(index);
        } else list.clearSelection();
        return moved;
    }

    private void refreshViewStatus(JsonObject data) {
        // The status is visible where the work lives; no aggregate Status tab.
        int[] pending = new int[6];
        int[] total = new int[6];
        for (JsonObject d : ArchvowGrammar.objects(data, "decisions")) {
            total[0]++;
            if (ArchvowGrammar.str(d, "answer").isBlank()) pending[0]++;
        }
        for (JsonObject q : ArchvowGrammar.objects(data, "questions")) {
            total[1]++;
            if (ArchvowGrammar.str(q, "answer").isBlank()) pending[1]++;
        }
        for (JsonObject q : ArchvowGrammar.objects(data, "safety")) {
            total[2]++;
            if (ArchvowSafetyState.pending(q)) pending[2]++;
        }
        for (JsonObject a : ArchvowGrammar.objects(data, "assumptions")) {
            total[3]++;
            String state = ArchvowGrammar.str(a, "status").toLowerCase(java.util.Locale.ROOT);
            if (!state.equals("accepted") && !state.equals("rejected")) pending[3]++;
        }
        // Same entries drive the count, the visible content and the global pending list.
        List<ArchvowDataView.Item> dataItems = ArchvowDataView.items(data);
        total[4] = ArchvowGrammar.objects(data, "rules").size();
        total[5] = dataItems.size();
        pending[5] = (int) dataItems.stream().filter(ArchvowDataView.Item::pending).count();
        String[] names = {ArchvowI18n.text(project,"Decisions","Décisions"), "Questions",
            ArchvowI18n.text(project,"Safety","Sécurité"),
            ArchvowI18n.text(project,"Assumptions","Hypothèses"),
            ArchvowI18n.text(project,"Rules","Règles"),
            ArchvowI18n.text(project,"Tasks","Tâches")};
        for (int i = 0; i < 6 && i < componentViewButtons.size(); i++) {
            JToggleButton tab = componentViewButtons.get(i);
            int ai = i == 0 ? aiView.count("decisions") : i == 2 ? aiView.count("safety") : i == 3 ? aiView.count("assumptions")
                : i == 4 ? aiView.count("rules") : 0;
            String label = names[i] + (pending[i] > 0 ? " · " + pending[i] :
                total[i] > 0 ? " ✓" : "") + (ai > 0 ? "  ● " + ai : "");
            tab.setText(label);
            tab.setToolTipText(pending[i] > 0 ? pending[i] + " " +
                ArchvowI18n.text(project,"pending items","éléments en attente") :
                ArchvowI18n.text(project,"No open items","Aucun élément ouvert"));
        }
        if (componentViewButtons.size() > 7) {
            int otherPending = componentOtherPendingModel.size();
            int otherTotal = componentOtherPendingModel.size() + componentOtherResolvedModel.size();
            JToggleButton other = componentViewButtons.get(7);
            other.setText(ArchvowI18n.text(project, "Other", "Autres")
                + (otherPending > 0 ? " · " + otherPending : otherTotal > 0 ? " ✓" : ""));
            other.setToolTipText(otherPending > 0
                ? ArchvowI18n.text(project,
                    "Component-local uncategorised items pending.",
                    "Éléments locaux au composant encore en attente.")
                : ArchvowI18n.text(project,
                    "No component-local uncategorised item.",
                    "Aucun élément local au composant dans cette vue."));
        }
        if (componentViewNav != null) componentViewNav.refresh();
        viewPending = pending;
        viewTotal = total;
        refreshListFilter();
    }

    /** The line under the views for the active one (ArchvowListFilter): the words, then where the work is. */
    private void refreshListFilter() {
        listFilterLine.removeAll();
        int view = componentViewNav == null ? 0 : componentViewNav.activeIndex();
        boolean shown = selectedView != null && ArchvowListFilter.applies(view);
        listFilterLine.setVisible(shown);
        componentPendingExplanation.setVisible(!shown && componentPendingExplanation.getText() != null
            && !componentPendingExplanation.getText().isBlank());
        if (!shown) { listFilterLine.revalidate(); return; }
        boolean fr = ArchvowI18n.isFrench(project);
        int open = view < viewPending.length ? viewPending[view] : 0;
        int review = view == 0 ? aiView.count("decisions") : view == 2 ? aiView.count("safety") : aiView.count("rules");
        String now = ArchvowListFilter.normalized(originFilter, view, open, review);
        if (!now.equals(originFilter)) { originFilter = now; if (selectedView != null) SwingUtilities.invokeLater(() -> renderComponent(selectedView)); }
        JLabel show = new JLabel(ArchvowI18n.text(project, "Show", "Afficher"));
        show.setForeground(UIManager.getColor("Label.disabledForeground"));
        show.setFont(show.getFont().deriveFont(11f));
        listFilterLine.add(show);
        for (String[] w : ArchvowListFilter.words(view, open, review)) {
            boolean on = w[0].equals(originFilter);
            int n = Integer.parseInt(w[3]);
            String tone = "review".equals(w[0]) ? "#5B8DEF" : "#C98A3C";
            JButton word = ArchvowSplitView.link("");
            word.setText("<html>" + (on ? "<u><b>" : "") + escape(fr ? w[2] : w[1]) + (on ? "</b></u>" : "")
                + (n > 0 ? " <b style='color:" + tone + "'>" + n + "</b>" : "") + "</html>");
            word.setFont(word.getFont().deriveFont(12f));
            word.putClientProperty("archvow.filter", w[0]);
            word.addActionListener(e -> { originFilter = w[0]; if (selectedView != null) renderComponent(selectedView); });
            listFilterLine.add(word);
        }
        int next = ArchvowListFilter.doneThenGoTo(view, viewPending, viewTotal, componentOtherPendingModel.size());
        if (next != -2) {
            String[] names = {ArchvowI18n.text(project, "Decisions", "Décisions"), "Questions", ArchvowI18n.text(project, "Safety", "Sécurité"),
                ArchvowI18n.text(project, "Assumptions", "Hypothèses"), ArchvowI18n.text(project, "Rules", "Règles"),
                ArchvowI18n.text(project, "Tasks", "Tâches"), "Spec", ArchvowI18n.text(project, "Other", "Autres")};
            JLabel done = new JLabel(names[view] + ArchvowI18n.text(project, ": all done", " : tout est traité"));
            done.setForeground(darkTheme() ? new Color(116, 213, 167) : new Color(15, 132, 81));
            done.setFont(done.getFont().deriveFont(12f));
            listFilterLine.add(Box.createHorizontalStrut(18));
            listFilterLine.add(done);
            if (next >= 0) {
                int n = next == 7 ? componentOtherPendingModel.size() : viewPending[next];
                JButton go = ArchvowSplitView.link(names[next] + (fr ? " : " + n + " ouvert" + (n > 1 ? "s" : "") + " ›" : ": " + n + " open ›"));
                go.setFont(go.getFont().deriveFont(12f));
                go.addActionListener(e -> selectComponentView(next));
                listFilterLine.add(go);
            }
        }
        listFilterLine.revalidate();
        listFilterLine.repaint();
    }

    private String firstMeaningfulLabel(JsonObject obj) {
        for (String key : List.of("id", "name", "question", "task", "kind", "text")) {
            String value = ArchvowGrammar.str(obj, key);
            if (!value.isBlank()) return shorten(value, 64);
        }
        return ArchvowI18n.text(project, "item", "élément");
    }

    private String sectionHeader(String title, int count) {
        return ArchvowHtml.heading(escape(title) + " " + chip(Integer.toString(count), "#F4F5F7", "#455468"), 1.1, "margin:14px 0 8px 0;");
    }

    private String emptyState() {
        return "<div style='padding:8px 0; color:#66707D;'>" + escape(ArchvowI18n.text(project, "Nothing to display.", "Rien à afficher.")) + "</div>";
    }

    /** The component's head in the editor tab, over the whole width: its name and open items, then
     *  type · layer · module · ticket · state, its progress bar, its description, what it uses and what uses
     *  it (links), its business examples. The VS Code page shows the same head (componentDetail()). */
    private String buildComponentHtml(JsonObject data) {
        String id = ArchvowGrammar.str(data,"component");
        boolean fr = ArchvowI18n.isFrench(project);
        JsonObject entry = null;
        for (JsonObject c : ArchvowGrammar.objects(manifest, "containers"))
            for (JsonObject x : ArchvowGrammar.objects(c, "components"))
                if (id.equals(ArchvowGrammar.str(x, "id"))) entry = x;
        int open = pendingByComponent.getOrDefault(id, 0);
        String badge = open > 0
            ? "<span style='background-color:" + (darkTheme() ? "#4A3B16" : "#FFF1D7") + "; color:" + (darkTheme() ? "#EBB55A" : "#9B6818") + "'>&nbsp;"
                + open + " " + escape(open == 1 ? ArchvowI18n.text(project, "open item", "élément ouvert")
                    : ArchvowI18n.text(project, "open items", "éléments ouverts")) + "&nbsp;</span>"
            : "<span style='color:" + (darkTheme() ? "#74D5A7" : "#0F8451") + "'>✓ " + escape(ArchvowI18n.text(project, "resolved", "traité")) + "</span>";
        java.util.List<String> meta = new ArrayList<>();
        for (String part : List.of(ArchvowGrammar.str(data, "type"),
                ArchvowGrammar.str(data, "layer").isBlank() ? ArchvowGrammar.str(entry, "layer") : ArchvowGrammar.str(data, "layer"),
                ArchvowGrammar.str(entry, "module").isBlank() ? "" : "module " + ArchvowGrammar.str(entry, "module"),
                ArchvowGrammar.str(entry, "spec").isBlank() ? "" : "ticket " + ArchvowGrammar.str(entry, "spec"),
                ArchvowGrammar.str(data, "status"))) if (!part.isBlank()) meta.add(part);
        StringBuilder html = new StringBuilder(htmlBareHead());
        // archvow 1.19, modes guided and free: a component the AI added is the AI's until a person accepts it
        boolean byAi = "ai".equals(ArchvowGrammar.str(data, "origin"));
        String aiBadge = byAi ? "&nbsp;<span style='background-color:" + (darkTheme() ? "#1F3550" : "#E3EEFB") + "; color:" + (darkTheme() ? "#80BBF9" : "#2366BC")
            + "'>&nbsp;" + escape(ArchvowI18n.text(project, "AI: to review", "IA : à relire")) + "&nbsp;</span>" : "";
        String state = ArchvowGrammar.str(data, "status");
        if (state.isBlank()) state = ArchvowGrammar.str(entry, "status");
        boolean isGoodState = List.of("verified", "implemented", "tested").contains(state.toLowerCase(java.util.Locale.ROOT));
        String stateColor = isGoodState ? (darkTheme() ? "#90DC9A" : "#187A43")
            : (darkTheme() ? "#E8A35C" : "#AE641A");
        String stateBadge = state.isBlank() ? "" : "&nbsp;<span style='color:" + stateColor + "'>" + escape(state) + "</span>";
        html.append("<div style='").append(ArchvowHtml.size(1.6)).append("'><b>").append(escape(id)).append("</b>&nbsp;&nbsp;<span style='").append(ArchvowHtml.size(0.85)).append("'>").append(stateBadge)
            .append(badge).append(aiBadge).append("</span></div>");
        if (byAi) {
            String who = ArchvowGrammar.str(data, "created_by");
            html.append("<div style='color:").append(darkTheme() ? "#EBB55A" : "#9B6818").append("; margin-top:3px'><b>")
                .append(escape(ArchvowI18n.text(project, "Created by the AI", "Créé par l'IA") + (who.isBlank() ? "" : " (" + who + ")"))).append("</b> - ")
                .append(escape(ArchvowI18n.text(project,
                    "read what it must do, its type and its uses; change it with Edit spec if needed, then Accept. The release gate fails until a person accepts it (ARCH043).",
                    "lire ce qu'il doit faire, son type et ce qu'il utilise ; le changer avec Modifier la spec si besoin, puis Accepter. Le contrôle de livraison échoue tant qu'une personne ne l'a pas accepté (ARCH043).")))
                .append("</div>");
        }
        html.append("<div style='color:").append(mutedHex()).append("; margin-top:2px'>").append(escape(String.join(" · ", meta))).append("</div>");
        String description = ArchvowGrammar.str(data, "description");
        // one bar: decisions made, safety answered, tasks done, examples tested (0.12.0, ArchvowProgress)
        html.append(ArchvowProgress.html(data, fr, darkTheme()));
        if (!description.isBlank()) html.append("<p style='margin-top:6px'>").append(escape(description)).append("</p>");
        html.append(ArchvowBriefs.drawnFromHtml(data, fr));
        html.append(ArchvowLinks.html(data, fr));
        html.append(ArchvowLinks.examplesHtml(data, fr));
        java.util.List<String> missing = new ArrayList<>();
        if (data != null && data.has("missing") && data.get("missing").isJsonArray())
            for (com.google.gson.JsonElement m : data.getAsJsonArray("missing"))
                missing.add(m.isJsonPrimitive() ? m.getAsString() : m.toString());
        if (!missing.isEmpty()) html.append("<p style='color:#B07E24'>").append(escape(ArchvowI18n.text(project, "Missing", "Manquant")))
            .append(": ").append(escape(String.join(", ", missing))).append("</p>");
        return html.append("</body></html>").toString();
    }

    private String decisionBlock(JsonObject d) {
        String answer = ArchvowGrammar.str(d,"answer");
        boolean done = !answer.isBlank();
        String question = ArchvowGrammar.str(d,"question");
        String number = ArchvowGrammar.str(d,"number");
        String color = done ? "#13835B" : "#B07E24";
        StringBuilder out = new StringBuilder();
        out.append("<div style='border-left:4px solid ").append(color).append("; padding:7px; margin:6px 0;'>")
            .append(chip("D" + number, "#E7EDFA", "#1E63B8"))
            .append(chip(done ? ArchvowI18n.text(project,"Answered","Répondu") : ArchvowI18n.text(project,"Pending","En attente"),
                done ? "#E5F4EA" : "#FFF1D7", color))
            .append("<p><b>").append(escape(question)).append("</b></p>");
        if (done) out.append("<p><b>").append(escape(ArchvowI18n.text(project,"Answer", "Réponse"))).append(":</b> ").append(escape(answer)).append("</p>");
        String proposed = ArchvowGrammar.str(d,"proposed");
        if (!proposed.isBlank()) out.append("<p><i>").append(escape(ArchvowI18n.text(project,"Proposal", "Proposition"))).append(": ").append(escape(proposed)).append("</i></p>");
        return out.append("</div>").toString();
    }

    /** Swing JEditorPane renders old-style HTML tables much more reliably than CSS divs. */
    private String safetyBlock(JsonObject q) {
        if (q == null) return "<p>" + escape(ArchvowI18n.text(project,
            "Select a safety question to see details.", "Choisis une question de sécurité pour afficher les détails.")) + "</p>";
        boolean pending = ArchvowSafetyState.pending(q);
        String accent = pending ? "#C89035" : "#18845F";
        String bg = darkTheme() ? "#30353D" : "#F2F5FA";
        String bodyColor = darkTheme() ? "#E5ECF5" : "#263448";
        String muted = darkTheme() ? "#ACB9CB" : "#59697C";
        StringBuilder out = new StringBuilder();
        out.append("<table width='100%' cellspacing='0' cellpadding='9'><tr><td bgcolor='")
            .append(bg).append("'><font color='").append(accent).append("'><b>")
            .append("S").append(escape(ArchvowGrammar.str(q,"number"))).append(" · ")
            .append(escape(ArchvowSafetyState.status(q))).append("</b></font>")
            .append("<p><font color='").append(bodyColor).append("'><b>")
            .append(escape(ArchvowGrammar.str(q,"question"))).append("</b></font></p>");
        String suggestedHazard = ArchvowSafetyState.suggestedHazard(q);
        String suggestedInvariant = ArchvowSafetyState.suggestedInvariant(q);
        if (!suggestedHazard.isBlank()) {
            out.append("<p><font color='").append(muted).append("'><b>")
                .append(escape(ArchvowI18n.text(project,"Proposed hazard", "Hazard proposé")))
                .append("</b></font><br><font color='").append(bodyColor).append("'>")
                .append(escape(suggestedHazard)).append("</font></p>");
        }
        if (!suggestedInvariant.isBlank()) {
            out.append("<p><font color='").append(muted).append("'><b>")
                .append(escape(ArchvowI18n.text(project,"Proposed invariant", "Invariant proposé")))
                .append("</b></font><br><font color='").append(bodyColor).append("'>")
                .append(escape(suggestedInvariant)).append("</font></p>");
        }
        java.util.List<JsonObject> hazards = ArchvowGrammar.objects(q, "hazards");
        if (hazards.isEmpty() && suggestedHazard.isBlank() && "open".equalsIgnoreCase(ArchvowGrammar.str(q,"status"))) {
            out.append("<p><font color='").append(accent).append("'><b>")
                .append(escape(ArchvowI18n.text(project,
                    "No hazard recorded yet. Declare one or mark the question not applicable.",
                    "Aucun hazard déclaré. Déclare un hazard ou indique que la question ne s'applique pas.")))
                .append("</b></font></p>");
        }
        for (JsonObject hazard : hazards) {
            String id = ArchvowGrammar.str(hazard, "id");
            String severity = ArchvowGrammar.str(hazard, "severity");
            out.append("<hr><p><font color='").append(bodyColor).append("'><b>")
                .append(escape(ArchvowI18n.text(project,"Hazard", "Hazard"))).append(" ")
                .append(escape(id)).append(" · ").append(escape(severity))
                .append("</b><br>").append(escape(ArchvowGrammar.str(hazard,"description")))
                .append("</font></p>");
            java.util.List<JsonObject> invariants = ArchvowGrammar.objects(hazard,"prevented_by");
            for (JsonObject invariant : invariants) {
                out.append("<p><font color='").append(accent).append("'><b>")
                    .append("↳ ").append(escape(ArchvowI18n.text(project,"Invariant", "Invariant")))
                    .append(" ").append(escape(ArchvowGrammar.str(invariant,"id")))
                    .append("</b></font> <font color='").append(bodyColor).append("'>")
                    .append(escape(ArchvowGrammar.str(invariant,"statement")))
                    .append("</font></p>");
            }
            if (ArchvowSafetyState.requiresInvariant(hazard) && invariants.isEmpty()) {
                out.append("<p><font color='#C89035'><b>ARCH030 — ")
                    .append(escape(ArchvowI18n.text(project,
                        "An invariant is required before this hazard can be considered protected.",
                        "Un invariant est obligatoire pour que ce hazard soit considéré protégé.")))
                    .append("</b></font></p>");
            }
        }
        String dismissal = ArchvowGrammar.str(q,"dismissed");
        if (!dismissal.isBlank()) out.append("<p><font color='").append(bodyColor).append("'><b>")
            .append(escape(ArchvowI18n.text(project,"Not applicable", "Non applicable")))
            .append(":</b> ").append(escape(dismissal)).append("</font></p>");
        return out.append("</td></tr></table>").toString();
    }

    private String buildDecisionHtml(JsonObject d) {
        return htmlHead() + decisionBlock(d) + "</body></html>";
    }
    private String buildSafetyHtml(JsonObject q) {
        return htmlHead() + safetyBlock(q) + "</body></html>";
    }

    private String prettyProperty(JsonObject data, String key) { return ArchvowGrammar.pretty(data.get(key)); }
    private JsonObject selectedRecord(String key, int index) {
        List<JsonObject> rows = ArchvowGrammar.objects(selectedView, key);
        // Decisions and Safety may hide cards (All / Open / To review): the card's number finds its row
        JList<ArchvowItemCards.Card> list = "decisions".equals(key) ? decisionsList : "safety".equals(key) ? safetyList : null;
        if (list != null) {
            if (index < 0 || index >= list.getModel().getSize()) return null;
            String number = list.getModel().getElementAt(index).number();
            for (JsonObject row : rows) {
                String n = ArchvowGrammar.str(row, "number");
                if (number.equals(n) || number.equals("S" + n)) return row;
            }
            return null;
        }
        return index >= 0 && index < rows.size() ? rows.get(index) : null;
    }
    private static void selectRecordByNumber(JList<?> list, List<JsonObject> rows, String number) {
        int selected = 0;
        if (!number.isBlank()) {
            for (int i = 0; i < rows.size(); i++) {
                if (number.equals(ArchvowGrammar.str(rows.get(i), "number"))) { selected = i; break; }
            }
        }
        if (!rows.isEmpty()) list.setSelectedIndex(selected);
        else list.clearSelection();
    }

    private void showDecision() {
        JsonObject row = selectedRecord("decisions", decisionsList.getSelectedIndex());
        boolean available = row != null;
        ArchvowAiSkills.Origin origin = row == null ? null : decisionOrigin(row);
        ArchvowDecisionView.display(decisionDetails, row, origin, ArchvowI18n.isFrench(project), darkTheme());
        boolean asked = origin != null && "ai".equals(origin.tag()) && origin.toReview() && !origin.proposal();
        keepQuestionInline.setEnabled(asked);
        removeQuestionInline.setEnabled(asked);
        decisionDetails.setCaretPosition(0);
        decisionAnswer.setEnabled(available);
        decideInline.setEnabled(available);
        proposeInline.setEnabled(available && ArchvowGrammar.str(row, "answer").isBlank());
        acceptInline.setEnabled(available && ArchvowGrammar.str(row, "answer").isBlank()
            && !ArchvowGrammar.str(row, "proposed").isBlank());
        suggestedInline.setEnabled(available && !ArchvowGrammar.str(row, "suggested").isBlank());
        // only what applies to this decision is offered next to Cancel / Save answer
        for (JButton other : List.of(removeQuestionInline, keepQuestionInline, proposeInline, acceptInline, suggestedInline))
            other.setVisible(other.isEnabled());
        if (suggestedInline.getParent() != null) suggestedInline.getParent().revalidate();
        if (proposeInline.getParent() != null) proposeInline.getParent().revalidate();
        if (available) {
            String answer = ArchvowGrammar.str(row, "answer");
            String suggestion = ArchvowGrammar.str(row, "proposed");
            if (suggestion.isBlank()) suggestion = ArchvowGrammar.str(row, "suggested");
            recorded(decisionAnswer, answer.isBlank() ? suggestion : answer);
        } else recorded(decisionAnswer, "");
        decisionChoices.removeAll();
        boolean offers = available && ArchvowGrammar.str(row, "answer").isBlank() && row.has("choices") && row.get("choices").isJsonArray()
            && row.getAsJsonArray("choices").size() > 0;
        if (offers) {
            JLabel intro = new JLabel(ArchvowI18n.text(project, "No default (it depends on this project) - to start from:",
                "Pas de défaut (cela dépend de ce projet) - pour partir de :"));
            intro.setFont(intro.getFont().deriveFont(Font.BOLD));
            decisionChoices.add(intro);
            for (var choice : row.getAsJsonArray("choices")) {
                String text = choice.getAsString();
                decisionChoices.add(button(text, () -> { decisionAnswer.setText(text); decisionAnswer.requestFocusInWindow(); }));
            }
        }
        decisionChoices.setVisible(offers);
        columnAnswerPanel.showFor(selectedView, row, ArchvowI18n.isFrench(project));
        decisionChoices.revalidate(); decisionChoices.repaint();
        if (decisionInlineResultCard != null) decisionInlineResultCard.setVisible(false);
    }

    private void showQuestion() {
        JsonObject row = selectedRecord("questions", questionList.getSelectedIndex());
        String q = row == null ? "" : ArchvowGrammar.str(row, "question");
        String answer = row == null ? "" : ArchvowGrammar.str(row, "answer");
        String key = row == null ? "" : ArchvowGrammar.str(row, "key");
        String about = row == null ? "" : String.join(" · ", java.util.stream.Stream.of(ArchvowGrammar.str(row, "title"),
            ArchvowGrammar.str(row, "rule")).filter(x -> !x.isBlank()).toList());
        String recorded = row == null || answer.isBlank() ? "" : ArchvowItemCards.byOn(row);
        if (key.isBlank() && row != null) key = ArchvowGrammar.str(row, "number");
        questionDetails.setText(row == null ? htmlBare("<i>" + escape(ArchvowI18n.text(project,
            "Select a question.", "Sélectionne une question.")) + "</i>")
            : htmlBare(answerHead(answer.isBlank() ? ArchvowI18n.text(project, "Answer question ", "Répondre à la question ") + key
                    : "Question " + key, answer.isBlank() ? "" : ArchvowI18n.text(project, "● ANSWERED", "● RÉPONDUE"))
                + (about.isBlank() ? "" : "<div style='color:" + mutedHex() + "'>" + escape(about) + "</div>")
                + "<p style='" + ArchvowHtml.size(1.15) + "; margin-top:4px'>" + escape(q) + "</p>"
                + (recorded.isBlank() ? "" : "<div style='color:" + mutedHex() + "'>"
                    + escape(ArchvowI18n.text(project, "Recorded by ", "Enregistrée par ") + recorded) + "</div>")));
        questionDetails.setCaretPosition(0);
        questionAnswer.setEnabled(row != null);
        recorded(questionAnswer, !answer.isBlank() ? answer : ArchvowGrammar.str(row, "suggested"));
        if (questionInlineResultCard != null) questionInlineResultCard.setVisible(false);
    }

    private void submitInlineQuestion() {
        JsonObject row = selectedRecord("questions", questionList.getSelectedIndex());
        if (row == null || selectedComponent == null) return;
        String key = ArchvowGrammar.str(row, "key");
        if (key.isBlank()) key = ArchvowGrammar.str(row, "number");
        String answer = questionAnswer.getText().trim();
        if (answer.isBlank()) {
            message(ArchvowI18n.text(project, "Enter an answer first.", "Saisis d'abord une réponse.")); return;
        }
        advance = "Q" + key;  // once answered, the next open question shows
        List<String> args;
        try { args = ArchvowInlineCommands.decide(selectedComponent.id, key, answer); }
        catch (IllegalArgumentException ex) { message(ex.getMessage()); return; }
        String confirmation = key + " — " + ArchvowGrammar.str(row, "question") + "\n\n"
            + ArchvowI18n.text(project, "Answer", "Réponse") + ": " + answer;
        String id = selectedComponent.id;
        final String questionKey = key;
        requestConfirmation("decision make", confirmation,
            () -> runInlineComponentAction("decision make", args, id, questionKey, false,
                questionInlineResult, questionInlineResultCard), true);
    }

    private void showAssumption() {
        JsonObject row = selectedRecord("assumptions", assumptionList.getSelectedIndex());
        String state = ArchvowGrammar.str(row, "status").toLowerCase(java.util.Locale.ROOT);
        boolean pending = row != null && !state.equals("accepted") && !state.equals("rejected");
        assumptionAcceptButton.setEnabled(pending);
        assumptionRejectButton.setEnabled(pending);
        assumptionAcceptButton.setVisible(pending);
        assumptionRejectButton.setVisible(pending);
        if (row == null) {
            assumptionDetails.setText(htmlBare("<i>" + escape(ArchvowI18n.text(project,
                "No assumption selected.", "Aucune hypothèse sélectionnée.")) + "</i>"));
            return;
        }
        String number = "A" + ArchvowGrammar.str(row, "number");
        assumptionDetails.setText(htmlBare(answerHead(pending
                ? ArchvowI18n.text(project, "Review assumption ", "Examiner l'hypothèse ") + number
                : ArchvowI18n.text(project, "Assumption ", "Hypothèse ") + number,
            pending ? "" : "● " + state.toUpperCase(java.util.Locale.ROOT))
            + "<p style='" + ArchvowHtml.size(1.15) + "; margin-top:4px'>" + escape(ArchvowGrammar.str(row, "text")) + "</p>"));
        assumptionDetails.setCaretPosition(0);
    }

    private void reviewSelectedAssumption(String command) {
        JsonObject row = selectedRecord("assumptions", assumptionList.getSelectedIndex());
        if (row == null || selectedComponent == null) return;
        String number = ArchvowGrammar.str(row, "number");
        if (number.isBlank()) return;
        openCommand(command, Map.of("component", selectedComponent.id, "number", number));
    }

    /** The CLI has hazard/invariant add + remove, not edit. Deletion is confirmed by submitForm. */
    /** Additional invariants can be added even when a hazard is already protected. */
    private void openAnySelectedSafetyInvariant() {
        JsonObject row = selectedRecord("safety", safetyList.getSelectedIndex());
        if (row == null || selectedComponent == null) return;
        java.util.List<String> ids = new ArrayList<>();
        for (JsonObject hazard : ArchvowGrammar.objects(row, "hazards")) {
            String id = ArchvowGrammar.str(hazard, "id");
            if (!id.isBlank()) ids.add(id);
        }
        if (ids.isEmpty()) {
            message(ArchvowI18n.text(project,
                "Declare a hazard before adding an invariant.",
                "Déclare un hazard avant d'ajouter un invariant."));
            return;
        }
        String selected = (String) JOptionPane.showInputDialog(this,
            ArchvowI18n.text(project,"Protect which hazard?", "Quel hazard protéger ?"),
            "Archvow", JOptionPane.QUESTION_MESSAGE, null, ids.toArray(new String[0]), ids.get(0));
        if (selected == null) return;
        Map<String,String> form = new LinkedHashMap<>();
        form.put("component", selectedComponent.id);
        form.put("prevents", selected);
        String suggestion = ArchvowSafetyState.suggestedInvariant(row);
        if (!suggestion.isBlank()) form.put("statement", suggestion);
        openCommand("invariant add", form);
    }

    private void removeSelectedSafetyArtifact(boolean invariant) {
        JsonObject row = selectedRecord("safety", safetyList.getSelectedIndex());
        if (row == null || selectedComponent == null) return;
        java.util.List<String> ids = new ArrayList<>();
        for (JsonObject hazard : ArchvowGrammar.objects(row, "hazards")) {
            if (!invariant) {
                String id = ArchvowGrammar.str(hazard, "id");
                if (!id.isBlank()) ids.add(id);
            } else for (JsonObject guard : ArchvowGrammar.objects(hazard, "prevented_by")) {
                String id = ArchvowGrammar.str(guard, "id");
                if (!id.isBlank() && !ids.contains(id)) ids.add(id);
            }
        }
        if (ids.isEmpty()) {
            message(ArchvowI18n.text(project,
                "No recorded item to remove. Use Add to create one.",
                "Aucun élément enregistré à supprimer. Utilise Ajouter pour en créer un."));
            return;
        }
        String chosen = (String) JOptionPane.showInputDialog(this,
            ArchvowI18n.text(project,
                invariant ? "Select the invariant to remove:" : "Select the hazard to remove:",
                invariant ? "Choisis l'invariant à supprimer :" : "Choisis le hazard à supprimer :"),
            "Archvow", JOptionPane.QUESTION_MESSAGE, null, ids.toArray(new String[0]), ids.get(0));
        if (chosen == null) return;
        // show --json gives the id with its component (clean_orders.H1): never prefix it twice
        openCommand(invariant ? "invariant remove" : "hazard remove",
            Map.of("id", ArchvowCommandPrefill.itemId(selectedComponent.id, chosen)));
    }

    private void showSafety() {
        JsonObject row = selectedRecord("safety", safetyList.getSelectedIndex());
        boolean available = row != null;
        ArchvowSafetyView.display(safetyDetails, row, aiView.hazards, ArchvowI18n.isFrench(project), darkTheme());
        String aiHazard = row == null ? "" : waitingHazard(row);
        keepAiHazardButton.setEnabled(!aiHazard.isEmpty());
        keepAiHazardButton.setVisible(!aiHazard.isEmpty());
        boolean open = available && "open".equalsIgnoreCase(ArchvowGrammar.str(row, "status"));
        String unprotected = available ? ArchvowSafetyState.unprotectedHazard(row) : "";
        java.util.List<JsonObject> hazards = available ? ArchvowGrammar.objects(row, "hazards") : java.util.List.of();
        JsonObject changing = null;
        for (JsonObject h : hazards) if (ArchvowGrammar.str(h, "id").equals(changingHazard)) changing = h;
        if (changing == null) changingHazard = null;  // done, or another question: back to the normal view
        String rememberKey = (selectedComponent == null ? "" : selectedComponent.id) + ":" + (available ? ArchvowGrammar.str(row, "number") : "");
        String suggestedInvariant = available ? ArchvowSafetyState.suggestedInvariant(row) : "";
        if (!suggestedInvariant.isBlank()) rememberedInvariants.put(rememberKey, suggestedInvariant);
        else suggestedInvariant = rememberedInvariants.getOrDefault(rememberKey, "");
        // one editor, three uses: declare the hazard; change a recorded one; write the invariant a high or
        // critical hazard needs, right here, filled with the suggestion (no jump to the Commands form)
        safetyMode = changing != null ? "change" : open ? "hazard" : !unprotected.isBlank() ? "invariant" : "";
        boolean editing = !safetyMode.isEmpty();
        for (JComponent part : safetyEditorParts) part.setVisible(editing);
        safetySeverityBox.setVisible(!"invariant".equals(safetyMode));
        safetyInput.setEnabled(editing);
        safetySeverity.setEnabled(editing);
        hazardInline.setEnabled(editing);
        boolean declaring = "hazard".equals(safetyMode);
        dismissInline.setVisible(declaring);
        dismissInline.setEnabled(declaring);
        keepHazardButton.setVisible("change".equals(safetyMode));
        String proposedHazard = available ? ArchvowSafetyState.suggestedHazard(row) : "";
        safetySuggestedButton.setVisible(declaring && !proposedHazard.isBlank());
        safetySuggestedButton.setEnabled(declaring && !proposedHazard.isBlank());
        boolean suggestsInvariant = available && !ArchvowSafetyState.suggestedInvariant(row).isBlank();
        // archvow 1.19: a decision covers the question - one Confirm (hazard + the decision as its invariant),
        // or, when it says there is nothing to protect, Confirm not applicable (the decision is the reason)
        JsonObject covering = available && open ? ArchvowSafetyState.coveringDecision(row) : null;
        coveredNothing = covering != null && !ArchvowSafetyState.notApplicable(row).isBlank();
        String coveringNumber = covering == null ? "" : ArchvowGrammar.str(covering, "number");
        safetySuggestedButton.setText(covering != null && !coveredNothing
            ? ArchvowI18n.text(project, "Confirm (decision " + coveringNumber + ")", "Confirmer (décision " + coveringNumber + ")")
            : suggestsInvariant ? ArchvowI18n.text(project,
            "Use proposed hazard + invariant", "Utiliser hazard + invariant proposés") : ArchvowI18n.text(project,
            "Use proposed hazard", "Utiliser le hazard proposé"));
        if (coveredNothing) {
            safetySuggestedButton.setVisible(false);
            dismissInline.setText(ArchvowI18n.text(project, "Confirm not applicable (decision " + coveringNumber + ")",
                "Confirmer non applicable (décision " + coveringNumber + ")"));
        } else dismissInline.setText(ArchvowI18n.text(project, "Not applicable", "Non applicable"));
        safetySuggestedButton.setToolTipText(available ? ArchvowSafetyState.preview(row) : "");
        switch (safetyMode) {
            case "change" -> {
                safetyInputLabel.setText(ArchvowI18n.text(project, "Change hazard ", "Modifier le hazard ") + changingHazard);
                hazardInline.setText(ArchvowI18n.text(project, "Save the change", "Enregistrer la modification"));
                safetySeverity.setSelectedItem(ArchvowGrammar.str(changing, "severity").toLowerCase(java.util.Locale.ROOT));
                recorded(safetyInput, ArchvowGrammar.str(changing, "description"));
            }
            case "invariant" -> {
                safetyInputLabel.setText(ArchvowI18n.text(project, "Invariant - what is always true, so that ", "Invariant - ce qui est toujours vrai, pour que ")
                    + unprotected + ArchvowI18n.text(project, " cannot happen", " ne puisse pas arriver"));
                hazardInline.setText(ArchvowI18n.text(project, "Add invariant", "Ajouter l'invariant"));
                recorded(safetyInput, suggestedInvariant);
            }
            default -> {
                safetyInputLabel.setText(ArchvowI18n.text(project, "Hazard description or reason", "Hazard ou justification"));
                hazardInline.setText(ArchvowI18n.text(project, "Declare hazard", "Déclarer un hazard"));
                recorded(safetyInput, open && !coveredNothing ? proposedHazard : "");
            }
        }
        // archvow 1.19: hazard edit changes a hazard in place - same id, its invariants stay (and become to review)
        boolean anyGuarded = false, anyFree = false, anyInvariant = false;
        for (JsonObject h : hazards) {
            boolean guarded = !ArchvowGrammar.objects(h, "prevented_by").isEmpty();
            anyGuarded |= guarded;
            anyFree |= !guarded;
            anyInvariant |= guarded;
        }
        changeHazardButton.setVisible(!hazards.isEmpty() && changing == null);
        changeHazardButton.setEnabled(anyFree || anyGuarded);
        changeHazardButton.setToolTipText(anyGuarded ? ArchvowI18n.text(project,
                "Change its words or severity: same id, its invariants stay and become to review (hazard edit)",
                "Changer ses mots ou sa gravité : même id, ses invariants restent et deviennent à revoir (hazard edit)")
            : ArchvowI18n.text(project, "Change its words or severity: same id (hazard edit)", "Changer ses mots ou sa gravité : même id (hazard edit)"));
        removeHazardButton.setVisible(!hazards.isEmpty());
        removeInvariantButton.setVisible(anyInvariant);
        safetyInvariantButton.setVisible(false);  // replaced by the invariant field above
        if (safetyInlineResultCard != null) safetyInlineResultCard.setVisible(false);
        safetyInput.revalidate();
    }

    /** Change hazard: the field takes its text and severity; saving removes it and records it again. */
    private void startHazardChange() {
        JsonObject row = selectedRecord("safety", safetyList.getSelectedIndex());
        if (row == null || selectedComponent == null) return;
        java.util.List<String> free = new ArrayList<>();  // every recorded hazard: hazard edit keeps its invariants
        for (JsonObject h : ArchvowGrammar.objects(row, "hazards")) free.add(ArchvowGrammar.str(h, "id"));
        if (free.isEmpty()) return;
        String chosen = free.size() == 1 ? free.get(0) : (String) JOptionPane.showInputDialog(this,
            ArchvowI18n.text(project, "Select the hazard to change:", "Choisis le hazard à modifier :"),
            "Archvow", JOptionPane.QUESTION_MESSAGE, null, free.toArray(new String[0]), free.get(0));
        if (chosen == null) return;
        changingHazard = chosen;
        showSafety();
        safetyInput.requestFocusInWindow();
    }

    private void submitHazardChange(JsonObject row) {
        String componentId = selectedComponent.id;
        String number = ArchvowGrammar.str(row, "number");
        String word = ArchvowGrammar.str(row, "guide_word");
        if (word.isBlank()) word = "S" + number;
        String description = safetyInput.getText().trim();
        String severity = String.valueOf(safetySeverity.getSelectedItem());
        if (description.isBlank()) {
            message(ArchvowI18n.text(project, "Write what must never happen.", "Écris ce qui ne doit jamais arriver.")); return;
        }
        List<String> edit;
        try { edit = ArchvowInlineCommands.editHazard(componentId, changingHazard, description, severity); }
        catch (IllegalArgumentException ex) { message(ex.getMessage()); return; }
        String summary = "S" + number + " — " + ArchvowGrammar.str(row, "question") + "\n\n"
            + ArchvowI18n.text(project, "Change " + edit.get(2) + " to (" + severity + "): " + description
                    + "\nSame id; the invariants that protect it become to review.",
                "Modifier " + edit.get(2) + " en (" + severity + ") : " + description
                    + "\nMême id ; les invariants qui le protègent deviennent à revoir.");
        requestConfirmation("hazard edit", summary, () -> {
            changingHazard = null;
            runInlineComponentAction("hazard edit", edit, componentId, number, true);
        }, true);
    }

    private void submitInlineInvariant(JsonObject row) {
        String componentId = selectedComponent.id;
        String number = ArchvowGrammar.str(row, "number");
        String hazard = ArchvowSafetyState.unprotectedHazard(row);
        List<String> args;
        try { args = ArchvowInlineCommands.invariant(componentId, safetyInput.getText(), hazard); }
        catch (IllegalArgumentException ex) {
            message(ArchvowI18n.text(project, "Write what is always true.", "Écris ce qui est toujours vrai.")); return;
        }
        requestConfirmation("invariant add", "S" + number + " — " + ArchvowGrammar.str(row, "question") + "\n\n"
                + ArchvowI18n.text(project, "Add this invariant, protecting " + hazard + "?", "Ajouter cet invariant, qui protège " + hazard + " ?")
                + "\n\n" + args.get(3),
            () -> runInlineComponentAction("invariant add", args, componentId, number, true), true);
    }

    private void submitSuggestedSafety() {
        JsonObject row = selectedRecord("safety", safetyList.getSelectedIndex());
        if (row == null || selectedComponent == null || ArchvowSafetyState.suggestedHazard(row).isBlank()) return;
        String word = ArchvowGrammar.str(row, "guide_word");
        if (word.isBlank()) { message("Safety guide word unavailable / Guide word de sécurité indisponible."); return; }
        String proposal = ArchvowSafetyState.preview(row);
        if (ArchvowSafetyState.suggestedInvariant(row).isBlank()) proposal += "\n" + ArchvowI18n.text(project,
            "No invariant suggestion: a high-severity hazard will still need an invariant.",
            "Aucun invariant suggéré : un hazard élevé nécessitera encore un invariant.");
        // Without a description, archvow records the template's suggestion.
        // At high severity it also records the suggested invariant when one exists.
        String componentId = selectedComponent.id;
        String number = ArchvowGrammar.str(row, "number");
        requestConfirmation("hazard add", proposal + "\n" + ArchvowI18n.text(project,
            "Accept the suggested hazard and applicable invariant?", "Accepter le hazard suggéré et son invariant éventuel ?"),
            () -> runInlineComponentAction("hazard add",
                ArchvowInlineCommands.hazard(componentId, word, "high", ""), componentId, number, true), true);
    }

    private void openSelectedSafetyInvariant() {
        JsonObject row = selectedRecord("safety", safetyList.getSelectedIndex());
        if (selectedComponent == null || row == null) return;
        String id = ArchvowSafetyState.unprotectedHazard(row);
        if (id.isBlank()) return;
        Map<String, String> values = new java.util.LinkedHashMap<>();
        values.put("component", selectedComponent.id);
        values.put("prevents", id);
        String suggestion = ArchvowSafetyState.suggestedInvariant(row);
        if (!suggestion.isBlank()) values.put("statement", suggestion);
        openCommand("invariant add", values);
    }

    private void fillSuggestedAnswer() {
        JsonObject row = selectedRecord("decisions", decisionsList.getSelectedIndex());
        if (row != null) decisionAnswer.setText(ArchvowGrammar.str(row, "suggested"));
    }

    private void submitInlineDecision(String action) {
        JsonObject row = selectedRecord("decisions", decisionsList.getSelectedIndex());
        if (row == null || selectedComponent == null) return;
        String id = selectedComponent.id;
        String number = ArchvowGrammar.str(row, "number");
        String question = ArchvowGrammar.str(row, "question");
        String answer = decisionAnswer.getText().trim();
        if (!"decision accept".equals(action) && answer.isBlank()) {
            message(ArchvowI18n.text(project, "Enter an answer first.", "Saisis d'abord une réponse.")); return;
        }
        if ("decision accept".equals(action) && ArchvowGrammar.str(row, "proposed").isBlank()) {
            message(ArchvowI18n.text(project, "No AI proposal to accept.", "Aucune proposition IA à accepter.")); return;
        }
        if (!"decision propose".equals(action)) advance = "D" + number;  // once answered, the next open decision shows
        if ("decision propose".equals(action) && !ArchvowGrammar.str(row, "answer").isBlank()) {
            message(ArchvowI18n.text(project, "This decision is already answered.", "Cette décision a déjà une réponse.")); return;
        }
        List<String> args;
        try {
            args = switch (action) {
                case "decision accept" -> ArchvowInlineCommands.accept(id, number);
                case "decision propose" -> ArchvowInlineCommands.propose(id, number, answer);
                default -> ArchvowInlineCommands.decide(id, number, answer);
            };
        } catch (IllegalArgumentException ex) { message(ex.getMessage()); return; }
        String confirmation = "D" + number + " — " + question + "\n\n"
            + ("decision accept".equals(action) ? "Proposal / Proposition: " + ArchvowGrammar.str(row,"proposed")
                : "Answer / Réponse: " + answer)
            + "\n\n" + ArchvowI18n.text(project,
                "This changes the Archvow specification. Confirm?",
                "Cette action modifie la spécification Archvow. Confirmer ?");
        requestConfirmation(action, confirmation,
            () -> runInlineComponentAction(action, args, id, number, false), true);
    }

    private void submitInlineSafety(boolean dismiss) {
        JsonObject row = selectedRecord("safety", safetyList.getSelectedIndex());
        if (row == null || selectedComponent == null) return;
        if (!dismiss && "invariant".equals(safetyMode)) { submitInlineInvariant(row); return; }
        if (!dismiss && "change".equals(safetyMode)) { submitHazardChange(row); return; }
        if (!"open".equalsIgnoreCase(ArchvowGrammar.str(row, "status"))) return;
        String id = selectedComponent.id;
        String number = ArchvowGrammar.str(row, "number");
        String word = ArchvowGrammar.str(row, "guide_word");
        if (word.isBlank()) word = "S" + number;
        String description = safetyInput.getText().trim();
        if (dismiss && description.isBlank() && !coveredNothing) {
            message(ArchvowI18n.text(project, "Explain why this hazard is not applicable.",
                "Justifie pourquoi ce hazard n'est pas applicable.")); return;
        }
        List<String> args;
        try {
            args = dismiss ? ArchvowInlineCommands.dismiss(id, word, description)
                : ArchvowInlineCommands.hazard(id, word, String.valueOf(safetySeverity.getSelectedItem()), description);
        } catch (IllegalArgumentException ex) { message(ex.getMessage()); return; }
        String confirmation = "S" + number + " — " + ArchvowGrammar.str(row,"question") + "\n\n"
            + (dismiss ? (description.isBlank() ? ArchvowSafetyState.notApplicable(row) + "\n" : "")
                       + ArchvowI18n.text(project, "Mark as not applicable?", "Déclarer non applicable ?")
                       : ArchvowI18n.text(project, "Declare this hazard?", "Déclarer ce hazard ?"));
        if (!dismiss && ("critical".equalsIgnoreCase(String.valueOf(safetySeverity.getSelectedItem()))
            || "high".equalsIgnoreCase(String.valueOf(safetySeverity.getSelectedItem())))
            && !description.isBlank()) confirmation += "\n\n" + ArchvowI18n.text(project,
            "Important: this high/critical hazard will stay pending until an invariant is added.",
            "Important : ce hazard élevé/critique restera en attente jusqu'à l'ajout d'un invariant.");
        String name = dismiss ? "hazard dismiss" : "hazard add";
        requestConfirmation(name, confirmation,
            () -> runInlineComponentAction(name, args, id, number, true), true);
    }

    private void runInlineComponentAction(String label, List<String> args, String componentId,
                                          String number, boolean safety) {
        runInlineComponentAction(label, args, componentId, number, safety,
            safety ? safetyInlineResult : decisionInlineResult,
            safety ? safetyInlineResultCard : decisionInlineResultCard);
    }

    private void runInlineComponentAction(String label, List<String> args, String componentId,
                                          String number, boolean safety,
                                          JEditorPane destination, JPanel resultCard) {
        if (resultCard == null) return;
        resultCard.setVisible(true);
        destination.setText(htmlMessage("<b>" + escape(ArchvowI18n.text(project, "Running…", "Exécution…")) + "</b>"));
        cli.executeAction(label, args, null, result -> {
            storeCommandResult(destination, label, result);
            if (result.ok()) {
                // Update the selected component in place, no navigation to Commands.
                cli.execute("Component " + componentId, List.of("show", componentId, "--json"), null, shown -> {
                    JsonObject updated = ArchvowGrammar.componentView(shown.stdout, componentId);
                    if (updated == null) return;
                    componentSnapshots.put(componentId, updated);
                    updateOverview(); // Recompute pending/resolved labels from current component data.
                    if (selectedComponent != null && componentId.equals(selectedComponent.id)) {
                        selectedView = updated;
                        renderComponent(updated);
                        // The result stays visible after the editor updates.
                        resultCard.setVisible(true);
                    }
                });
            }
        });
    }

    private void updateCommandQuestion() {
        ArchvowGrammar.Command command = commandList.getSelectedValue();
        if (command == null) return;
        if (!Set.of("decision make", "decision propose", "decision accept", "question remove").contains(command.label)) return;
        String component = value("component");
        String number = command.label.equals("decision accept") || command.label.equals("question remove") ? value("number") : value("question");
        JsonObject snapshot = componentSnapshots.get(component);
        if (snapshot == null && selectedView != null && component.equals(ArchvowGrammar.str(selectedView, "component"))) snapshot = selectedView;
        String hint;
        if (component.isBlank() || number.isBlank()) {
            hint = ArchvowI18n.text(project, "Choose the component, then the question: it shows here with its answer.",
                "Choisis le composant, puis la question : elle s'affiche ici avec sa réponse.");
        } else if (snapshot == null) {
            hint = ArchvowI18n.text(project,
                "Question not loaded. Click 'Load question' below.",
                "Question non chargée. Clique sur « Charger la question ».");
        } else {
            // a question's number is its decision's (question add appends one); P1, O2... are the other questions
            JsonObject matching = null;
            for (JsonObject row : ArchvowGrammar.objects(snapshot, "decisions")) {
                if (number.equals(ArchvowGrammar.str(row, "number"))) { matching = row; break; }
            }
            if (matching == null && command.label.equals("decision make")) {
                for (JsonObject row : ArchvowGrammar.objects(snapshot, "questions")) {
                    if (number.equals(ArchvowGrammar.str(row, "key")) || number.equals(ArchvowGrammar.str(row, "number"))) {
                        matching = row; break;
                    }
                }
            }
            if (matching == null) hint = ArchvowI18n.text(project, "Question not found for this component.",
                "Question introuvable pour ce composant.");
            else {
                String questionText = ArchvowGrammar.str(matching, "question");
                String answer = ArchvowGrammar.str(matching, "answer");
                String proposed = ArchvowGrammar.str(matching, "proposed");
                hint = "<b>" + escape(component + " · " + number) + "</b> "
                    + chip(answer.isBlank() ? ArchvowI18n.text(project, "Pending", "En attente") : ArchvowI18n.text(project, "Answered", "Répondu"),
                        answer.isBlank() ? "#FFF1D7" : "#E5F4EA", answer.isBlank() ? "#9B6818" : "#13835B")
                    + "<p><b>" + escape(questionText) + "</b></p>"
                    + (answer.isBlank() ? "" : "<p>" + escape(ArchvowI18n.text(project,"Answer","Réponse")) + ": " + escape(answer) + "</p>")
                    + (proposed.isBlank() ? "" : "<p>" + escape(ArchvowI18n.text(project,"AI proposal","Proposition IA")) + ": " + escape(proposed) + "</p>");
            }
        }
        commandQuestionPreview.setText(htmlMessage(hint));
        commandQuestionPreview.setCaretPosition(0);
    }

    private void loadCommandQuestion() {
        String component = value("component");
        if (component.isBlank()) { updateCommandQuestion(); return; }
        commandQuestionPreview.setText(htmlMessage("<i>" + escape(ArchvowI18n.text(project, "Loading…", "Chargement…")) + "</i>"));
        cli.execute("Component " + component, List.of("show", component, "--json"), null, result -> {
            JsonObject json = ArchvowGrammar.componentView(result.stdout, component);
            if (json != null) {
                componentSnapshots.put(component, json);
                renderForm(currentValues());   // its questions are now in the lists
            } else commandQuestionPreview.setText(htmlMessage("<b>" + escape(result.output) + "</b>"));
        });
    }

    private void actionDecision(String command) {
        if (selectedComponent == null) return;
        JsonObject row = selectedRecord("decisions", decisionsList.getSelectedIndex());
        if (row == null) { message("Select a decision / Sélectionne une décision"); return; }
        Map<String, String> preset = new HashMap<>();
        preset.put("component", selectedComponent.id);
        preset.put("question", ArchvowGrammar.str(row, "number"));
        preset.put("number", ArchvowGrammar.str(row, "number"));
        if (command.equals("decision make")) preset.put("answer", ArchvowGrammar.str(row, "suggested"));
        else if (command.equals("decision propose")) preset.put("answer", ArchvowGrammar.str(row, "proposed"));
        openCommand(command, preset);
    }
    private void actionSafety(String command) {
        if (selectedComponent == null) return;
        JsonObject row = selectedRecord("safety", safetyList.getSelectedIndex());
        if (row == null) { message("Select a safety question / Sélectionne une question de sécurité"); return; }
        openCommand(command, Map.of("component", selectedComponent.id,
            "guide_word", "S" + ArchvowGrammar.str(row, "number"), "severity", "high"));
    }
    private void guidedNext() {
        if (selectedComponent == null) { message("Select a component / Sélectionne un composant."); return; }
        String id = selectedComponent.id;
        if (selectedView != null && id.equals(ArchvowGrammar.str(selectedView, "component"))) {
            chooseNext(selectedView); return;
        }
        cli.execute("Clarify " + id, List.of("show", id, "--json"), null, res -> {
            JsonObject json = ArchvowGrammar.parseObject(res.stdout);
            if (json == null) { message(res.output); return; }
            selectedView = json; renderComponent(json); chooseNext(json);
        });
    }
    private void chooseNext(JsonObject data) {
        tabs.setSelectedIndex(1);
        for (int i = 0; i < ArchvowGrammar.objects(data, "decisions").size(); i++) {
            JsonObject row = ArchvowGrammar.objects(data, "decisions").get(i);
            if (ArchvowGrammar.str(row, "answer").isBlank()) {
                selectComponentView(0);
                decisionsList.setSelectedIndex(i);
                showDecision();
                decisionAnswer.requestFocusInWindow();
                return;
            }
        }
        for (int i = 0; i < ArchvowGrammar.objects(data, "questions").size(); i++) {
            JsonObject row = ArchvowGrammar.objects(data, "questions").get(i);
            if (ArchvowGrammar.str(row, "answer").isBlank()) {
                selectComponentView(1);
                questionList.setSelectedIndex(i);
                showQuestion();
                questionAnswer.requestFocusInWindow();
                return;
            }
        }
        for (int i = 0; i < ArchvowGrammar.objects(data, "safety").size(); i++) {
            JsonObject row = ArchvowGrammar.objects(data, "safety").get(i);
            if ("open".equalsIgnoreCase(ArchvowGrammar.str(row, "status"))) {
                selectComponentView(2);
                safetyList.setSelectedIndex(i);
                showSafety();
                safetyInput.requestFocusInWindow();
                return;
            }
        }
        message(ArchvowI18n.text(project,
            "All decision and safety questions are answered for this component.",
            "Toutes les décisions et questions de sécurité sont traitées pour ce composant."));
    }

    /**
     * Spec: one view of the component's spec - the task the AI implements (archvow prompt: nothing generated is
     * committed since archvow 2.0) and the vow it is written from. One place to read them.
     */
    private JComponent specPanel() {
        boolean fr = ArchvowI18n.isFrench(project);
        specPart = new ArchvowSegmented(List.of(fr ? "La tâche que l'IA implémente" : "The task the AI implements",
            fr ? "Le vow dont elle est écrite" : "The vow it is written from"), fr ? "La tâche que l'IA implémente" : "The task the AI implements", true);
        cardDetails.setEditable(false); cardDetails.setLineWrap(true); cardDetails.setWrapStyleWord(true);
        cardDetails.setFont(new Font(Font.MONOSPACED, Font.PLAIN, cardDetails.getFont().getSize()));
        specCards.add(scroll(cardDetails), "card");
        specCards.add(scroll(vowDetails), "vow");
        specPart.onChange(() -> ((CardLayout) specCards.getLayout()).show(specCards, specPart.buttons().get(0).isSelected() ? "card" : "vow"));
        languageRefreshers.add(() -> {  // the switch follows the language; which one is pressed stays
            specPart.buttons().get(0).setText(ArchvowI18n.text(project, "The task the AI implements", "La tâche que l'IA implémente"));
            specPart.buttons().get(1).setText(ArchvowI18n.text(project, "The vow it is written from", "Le vow dont elle est écrite"));
        });
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        bar.add(specPart);
        bar.add(button(ArchvowI18n.text(project, "Open in the editor", "Ouvrir dans l'éditeur"), () -> {
            if (specPart.buttons().get(0).isSelected()) copyText(cardDetails.getText()); else openVowFile();
        }));
        bar.add(button(ArchvowI18n.text(project, "Project vow", "Vow du projet"), this::openProjectVow));
        JLabel readOnly = new JLabel(ArchvowI18n.text(project, "read only: change it with the commands (Edit spec, + Question, + Rule...)",
            "en lecture seule : la changer avec les commandes (Modifier la spec, + Question, + Règle...)"));
        readOnly.setForeground(mutedTextColor());
        bar.add(readOnly);
        JPanel top = new JPanel(new BorderLayout(0, 2));
        top.add(bar, BorderLayout.NORTH);
        top.add(componentFilesView, BorderLayout.CENTER);
        JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.add(top, BorderLayout.NORTH);
        panel.add(specCards, BorderLayout.CENTER);
        return panel;
    }

    /** The task the AI implements, as {@code archvow prompt <component>} gives it (nothing generated is committed since 2.0). */
    private void readCard(String component) {
        if (component == null || component.isBlank()) { cardDetails.setText(""); return; }
        cardDetails.setText(ArchvowI18n.text(project, "Loading the task…", "Chargement de la tâche…"));
        cli.executeAction("prompt", List.of("prompt", component), null, r -> {
            if (selectedComponent == null || !component.equals(selectedComponent.id)) return;
            cardDetails.setText(r.ok() ? r.stdout : r.output);
            cardDetails.setCaretPosition(0);
        });
    }

    private void copyText(String text) {
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new java.awt.datatransfer.StringSelection(text == null ? "" : text), null);
        message(ArchvowI18n.text(project, "Copied.", "Copié."));
    }

    private void openVowFile() {
        if (selectedComponent == null || project.getBasePath() == null) return;
        Path vow = ArchvowLayout.componentVow(Path.of(project.getBasePath()), selectedComponent.id);
        showFirstFile(vow == null ? List.of() : List.of(vow.toFile()), "Spec not found / Spec introuvable : " + selectedComponent.id);
    }

    private void openCode() {
        if (selectedComponent == null || project.getBasePath() == null) return;
        String root = project.getBasePath();
        JsonObject container = null, component = null;
        for (JsonObject c : ArchvowGrammar.objects(manifest, "containers")) {
            for (JsonObject entry : ArchvowGrammar.objects(c, "components")) {
                if (selectedComponent.id.equals(ArchvowGrammar.str(entry, "id"))) {
                    container = c; component = entry; break;
                }
            }
            if (component != null) break;
        }
        if (component == null || container == null) {
            message("Code location unknown / Emplacement du code inconnu pour " + selectedComponent.id);
            return;
        }
        String module = ArchvowGrammar.str(component, "module").replace('.', '/');
        String parent = ArchvowGrammar.str(container, "path");
        String prefix = parent.isBlank() ? "src" : parent.split("/", 2)[0];
        showFirstFile(List.of(new File(root, prefix + "/" + module + ".py"),
                new File(root, "src/" + module + ".py"),
                new File(root, parent + "/" + selectedComponent.id + ".py")),
            "Code not found / Code introuvable pour " + selectedComponent.id + "\nModule : " + module);
    }
    /** The spec of the example archvow try copied (0.25.1): only a .archvow/project.vow in the folder it printed. */
    private void openTryCopy(String folder) {
        showFirstFile(List.of(new File(folder, ".archvow/project.vow")), ArchvowI18n.text(project,
            "The example copy is gone: run Try again.", "La copie de l'exemple n'est plus là : relance l'essai."));
    }

    private void showFirstFile(List<File> candidates, String fallback) {
        for (File file : candidates) {
            if (!file.isFile()) continue;
            var virtual = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
            if (virtual != null) { FileEditorManager.getInstance(project).openFile(virtual, true); return; }
        }
        message(fallback);
    }

    private void updateOverview() {
        if (manifest == null) return;
        List<String> declared = ArchvowBriefs.folders(manifest);
        boolean frBriefs = ArchvowI18n.isFrench(project);
        int due = ArchvowBriefs.due(briefStatus);
        briefFolders.setText(ArchvowBriefs.foldersLine(declared, ArchvowBriefs.files(project.getBasePath(), declared).size(), frBriefs)
            + (due > 0 ? " · " + (frBriefs ? due + " section" + (due == 1 ? "" : "s") + " à regarder →" : due + " section" + (due == 1 ? "" : "s") + " to look at →") : ""));
        briefFolders.setCursor(Cursor.getPredefinedCursor(due > 0 ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
        String pkg = ArchvowGrammar.objects(manifest, "containers").isEmpty() ? "" : ArchvowGrammar.str(ArchvowGrammar.objects(manifest, "containers").get(0), "package");
        briefsPane.setText("<html><body style='" + ArchvowHtml.size(1.0) + "'>" + ArchvowBriefs.statusHtml(briefStatus, pkg, frBriefs) + "</body></html>");
        briefsPane.setCaretPosition(0);
        List<JsonObject> statuses = ArchvowGrammar.objects(check, "components");
        rebuildQuickModels(statuses);
        // Native Swing table: the declared component inventory is always visible,
        // even if `check --json` omits its optional per-component status list.
        Map<String, String> componentStates = new LinkedHashMap<>();
        for (JsonObject c : ArchvowGrammar.objects(manifest, "containers"))
            for (JsonObject node : ArchvowGrammar.objects(c, "components")) {
                String id = ArchvowGrammar.str(node, "id");
                String state = ArchvowGrammar.str(node, "status");
                JsonObject snapshot = componentSnapshots.get(id);
                if (snapshot != null && !ArchvowGrammar.str(snapshot, "status").isBlank())
                    state = ArchvowGrammar.str(snapshot, "status");
                if (!id.isBlank() && !state.isBlank()) componentStates.put(id, state);
            }
        for (JsonObject status : statuses) {
            String id = ArchvowGrammar.str(status, "component");
            String state = ArchvowGrammar.str(status, "status");
            if (!id.isBlank() && !state.isBlank()) componentStates.put(id, state);
        }
        componentsTree.putClientProperty("archvow.componentStatuses", Map.copyOf(componentStates));
        componentsTree.repaint();
        overviewSummary.showData(manifest, check, componentSnapshots, pendingByComponent,
            pendingModel.size(), resolvedModel.size(), ArchvowI18n.isFrench(project));
        boolean fr = ArchvowI18n.isFrench(project);
        overviewPacksCounts.setText(ArchvowPacks.summary(projectPacks, fr));
        overviewPacksCounts.setForeground(ArchvowPacks.changed(projectPacks) > 0 ? ArchvowStatusPalette.statusColor(true) : mutedTextColor());
        diagnostics.showData(check, checkFailure, ArchvowI18n.isFrench(project));
        detailDiagnostics.showData(check, checkFailure, ArchvowI18n.isFrench(project));
        refreshOverviewTabTitles();
        refreshComponentOtherModels();
        refreshOverviewCards();
    }

    /** The Overview's cards (0.25.0): First steps, Architecture's own items, Checks' first finding. */
    private void refreshOverviewCards() {
        boolean fr = ArchvowI18n.isFrench(project);
        int open = 0;
        for (int i = 0; i < pendingModel.size(); i++) if (ownItem(pendingModel.get(i))) open++;
        int done = 0;
        for (int i = 0; i < resolvedModel.size(); i++) if (ownItem(resolvedModel.get(i))) done++;
        int projectItems = 0;
        for (int i = 0; i < otherPendingModel.size(); i++) {
            QuickItem item = otherPendingModel.get(i);
            if (item.componentId.isBlank() && ownItem(item)) projectItems++;
        }
        int components = 0;
        for (JsonObject container : ArchvowGrammar.objects(manifest, "containers"))
            components += ArchvowGrammar.objects(container, "components").size();
        ArchvowOverview.FirstSteps steps = ArchvowOverview.firstSteps(firstStepsJson);
        ArchvowOverviewCards.Actions actions = overviewActions();
        JPanel first = ArchvowOverviewCards.firstSteps(steps, fr, actions);
        firstStepsHolder.removeAll();
        if (first != null) firstStepsHolder.add(first, BorderLayout.CENTER);
        firstStepsHolder.setVisible(first != null);
        architectureHolder.removeAll();
        architectureHolder.add(ArchvowOverviewCards.architecture(components, done, open, projectItems, first != null, fr, actions), BorderLayout.CENTER);
        checksHolder.removeAll();
        checksHolder.add(ArchvowOverviewCards.checks(ArchvowGrammar.number(check, "errors"), ArchvowGrammar.number(check, "warnings"),
            ArchvowOverview.firstFinding(check), fr, actions), BorderLayout.CENTER);
        for (JPanel holder : List.of(firstStepsHolder, architectureHolder, checksHolder)) {
            holder.revalidate();
            holder.repaint();
        }
    }

    /** An item of the components' own work - a decision, a question, a task - not a check finding. */
    private static boolean ownItem(QuickItem item) {
        return item.guide == null && !"open-diagnostics".equals(item.commandLabel);
    }

    private ArchvowOverviewCards.Actions overviewActions() {
        return new ArchvowOverviewCards.Actions() {
            @Override public void openComponents() { tabs.setSelectedIndex(1); }
            @Override public void openItems(int list) { openQuickList(list); }
            @Override public void openProjectItems() { openProjectWork(); }
            @Override public void openChecks() { runFullCheck(); }
            @Override public void openFile(String path, int line) { openViolationFile(path, line); }
            @Override public void step(ArchvowOverview.FirstStep step) {
                if (step.command().startsWith("archvow specify ")) {
                    openCommand("specify", Map.of("component", step.command().substring("archvow specify ".length()).trim()));
                    return;
                }
                switch (step.step()) {
                    case "feature" -> openCommand("feature add", Map.of());
                    case "component" -> openCommand("component add", Map.of());
                    case "build" -> runFullCheck();
                    default -> { }
                }
            }
            @Override public void tryExample() {
                executeDirect("try", List.of("try", "pyspark-pipeline", "--json"), null, false);
            }
        };
    }

    private void refreshOverviewTabTitles() {
        String architecture = "Architecture";
        String checks = ArchvowI18n.text(project, "Check & Gates", "Contrôles et validations");
        int checksPending = 0;
        for (int i = 0; i < pendingModel.size(); i++) {
            QuickItem item = pendingModel.get(i);
            if (item.guide != null || "open-diagnostics".equals(item.commandLabel)) checksPending++;
        }
        int architecturePending = pendingModel.size() - checksPending;
        // Counts partition the exact pending model: architecture + checks = pending.
        // ArchvowNavigationTabs.setTitleAt already ignores out-of-range indices.
        narrowOverviewTabs.setTitleAt(0, architecture + " · " + architecturePending);
        narrowOverviewTabs.setTitleAt(1, checks + " · " + checksPending);
        int due = ArchvowBriefs.due(briefStatus);
        // The Briefs tab is no longer duplicated in Overview.
    }

    private void openProjectVow() {
        String root = project.getBasePath();
        if (root == null) return;
        Path filePath = Path.of(root, ArchvowLayout.PROJECT_VOW);
        if (!java.nio.file.Files.isRegularFile(filePath)) {
            message(ArchvowI18n.text(project,
                "Project vow not available: " + ArchvowLayout.PROJECT_VOW,
                "Vow du projet indisponible : " + ArchvowLayout.PROJECT_VOW));
            return;
        }
        var file = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(filePath.toFile());
        if (file != null) FileEditorManager.getInstance(project).openFile(file, true);
    }

    /** The project doc's Checks section: archvow doc writes it, then it opens there. */
    private void openChecksDocument() {
        openDoc("", List.of("## Checks", "## Contrôles"));
    }

    private void openDiagnosticCode(String code) {
        String validated = ArchvowRuleDocs.canonical(code);
        if (validated.isBlank()) return;
        String projectRoot = project.getBasePath();
        ArchvowRuleDocs.Reference match = projectRoot == null ? null :
            ArchvowRuleDocs.findProjectReference(Path.of(projectRoot), validated);
        if (match != null) {
            var file = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(match.path().toFile());
            if (file != null) {
                FileEditorManager.getInstance(project).openTextEditor(
                    new OpenFileDescriptor(project, file, match.offset()), true);
                return;
            }
        }
        String description = ArchvowRuleDocs.explain(validated,ArchvowI18n.isFrench(project));
        message(validated + "\n\n" + (description.isBlank() ?
            ArchvowI18n.text(project, "No bundled definition for this project-specific code.",
                "Aucune définition intégrée pour ce code propre au projet.") : description)
            + "\n\n" + ArchvowI18n.text(project,
                "Project reference: .archvow/doc/project.md (Checks); write it with archvow doc.",
                "Référence du projet : .archvow/doc/project.md (Contrôles) ; écris-la avec archvow doc."));
    }

    /** Every pending card is grounded in a real unresolved engine record. */
    private void rebuildQuickModels(List<JsonObject> statuses) {
        pendingModel.clear();
        resolvedModel.clear();
        otherPendingModel.clear();
        otherResolvedModel.clear();
        Set<String> keys = new LinkedHashSet<>();
        for (JsonObject violation : ArchvowGrammar.objects(check, "violations")) {
            String code = ArchvowGrammar.str(violation, "code");
            String description = ArchvowGrammar.str(violation, "message");
            if (description.isBlank()) description = ArchvowGrammar.pretty(violation);
            if (code.isBlank()) code = "CHECK";
            String file = ArchvowGrammar.str(violation, "file");
            String referenced = ArchvowGrammar.str(violation, "component");
            if (!componentSnapshots.containsKey(referenced))
                referenced = ArchvowIssueAction.quotedComponent(description, componentSnapshots.keySet());
            ArchvowIssueAction.Fix fix = ArchvowIssueAction.fix(code, description, referenced);
            // ARCH030 is already represented by an actionable incomplete safety item.
            // Avoid counting the same missing invariant twice in global Pending.
            if ("ARCH030".equals(code) && fix != null && !referenced.isBlank()) {
                boolean inSafety = false;
                JsonObject snapshot = componentSnapshots.get(referenced);
                for (JsonObject question : ArchvowGrammar.objects(snapshot, "safety"))
                    if (!ArchvowSafetyState.unprotectedHazard(question).isBlank()) inSafety = true;
                if (inSafety) continue;
            }
            QuickKind severity = "warning".equalsIgnoreCase(ArchvowGrammar.str(violation,"severity")) ? QuickKind.WARNING : QuickKind.ERROR;
            int line = ArchvowGrammar.number(violation, "line");
            boolean french = ArchvowI18n.isFrench(project);
            ArchvowGuidance.Guidance guide = ArchvowGuidance.guide(code, ArchvowGrammar.str(violation, "severity"), description,
                file, line, ArchvowGuidance.rules(french), ArchvowGrammar.COMMANDS, french);
            // no fix of its own: the command the message names, as a prefilled form; else the file; else the list
            ArchvowGuidance.GuideCommand named = fix == null ? guide.command() : null;
            ArchvowGuidance.FileRef open = guide.file();
            String action = fix != null ? ("ARCH019".equals(code) ? "schema-create" : fix.command())
                : named != null ? named.name() : open == null ? "open-diagnostics" : "open-file";
            Map<String,String> values = fix != null ? fix.values() : named != null ? named.values()
                : open == null ? Map.of() : Map.of("file", open.path(), "line", String.valueOf(open.line()));
            String label = fix != null ? fix.label()
                : named != null ? ArchvowI18n.text(project, "Prepare: ", "Préparer : ") + shorten(named.text(), 60)
                : open == null ? ArchvowI18n.text(project,"Open diagnostics", "Ouvrir le diagnostic")
                : ArchvowI18n.text(project, "Open ", "Ouvrir ") + open.path() + (open.line() > 0 ? ":" + open.line() : "");
            addQuickItem(keys, new QuickItem(severity, code + " · " + shorten(description, 70),
                description + (file.isBlank() ? "" : "\n\n" + file + ":" + line),
                referenced, true, label, action, values, guide));
        }
        // Project-level records have no component. Keep them visible instead of assigning
        // a guessed component or silently dropping them from the global pending total.
        if (manifest != null) {
            for (String category : List.of("decisions", "questions", "assumptions", "tasks")) {
                for (JsonObject record : ArchvowGrammar.objects(manifest, category)) {
                    String title = ArchvowGrammar.str(record, "question");
                    if (title.isBlank()) title = ArchvowGrammar.str(record, "text");
                    if (title.isBlank()) title = ArchvowGrammar.str(record, "summary");
                    if (title.isBlank()) title = ArchvowGrammar.pretty(record);
                    String state = ArchvowGrammar.str(record, "status").toLowerCase(java.util.Locale.ROOT);
                    String answer = ArchvowGrammar.str(record, "answer");
                    boolean closed = category.equals("tasks") ? ArchvowGrammar.flag(record, "done") :
                        category.equals("assumptions") ? state.equals("accepted") || state.equals("rejected") :
                        !answer.isBlank() || state.equals("resolved") || state.equals("answered");
                    addQuickItem(keys, new QuickItem(closed ? QuickKind.RESOLVED : QuickKind.QUESTION,
                        ArchvowI18n.text(project, "Project", "Projet") + " · " + category + " · " + shorten(title, 65),
                        title + (answer.isBlank() ? "" : "\nAnswer / Réponse: " + answer),
                        "", !closed,
                        ArchvowI18n.text(project, "Open project specification", "Ouvrir la spécification du projet"),
                        "open-project-vow", Map.of()));
                }
            }
        }
        if (check == null && !checkFailure.isBlank()) {
            addQuickItem(keys, new QuickItem(QuickKind.WARNING,
                ArchvowI18n.text(project,"Check not available", "Vérification indisponible"),
                checkFailure, "", true,
                ArchvowI18n.text(project,"Open diagnostics", "Ouvrir le diagnostic"),
                "open-diagnostics", Map.of()));
        }
        for (JsonObject data : componentSnapshots.values()) {
            String id = ArchvowGrammar.str(data, "component");
            if (id.isBlank()) continue;
            for (JsonObject decision : ArchvowGrammar.objects(data, "decisions")) {
                String number = ArchvowGrammar.str(decision, "number");
                String question = ArchvowGrammar.str(decision, "question");
                String proposed = ArchvowGrammar.str(decision, "proposed");
                String answer = ArchvowGrammar.str(decision, "answer");
                boolean pending = answer.isBlank();
                boolean suggested = !proposed.isBlank();
                addQuickItem(keys, new QuickItem(pending ? (suggested ? QuickKind.SUGGESTION : QuickKind.DECISION) : QuickKind.RESOLVED,
                    id + " · D" + number + " " + shorten(question, 60),
                    question + "\n" + (pending ? (suggested ? "Proposal / Proposition: " + proposed : "Suggested / Suggéré: " + ArchvowGrammar.str(decision,"suggested")) : "Answer / Réponse: " + answer),
                    id, pending,
                    pending ? (suggested ? ArchvowI18n.text(project,"Accept proposal", "Accepter la proposition") : ArchvowI18n.text(project,"Answer", "Répondre"))
                        : ArchvowI18n.text(project,"Open component", "Ouvrir le composant"),
                    !pending ? "component-show" : suggested ? "decision accept" : "decision make",
                    !pending ? Map.of("view","decision","number",number) : suggested ? Map.of("component",id,"number",number,"view","decision")
                        : Map.of("component",id,"question",number,"answer",ArchvowGrammar.str(decision,"suggested"),"view","decision")));
            }
            for (JsonObject q : ArchvowGrammar.objects(data, "questions")) {
                String key = ArchvowGrammar.str(q, "key");
                String text = ArchvowGrammar.str(q, "question");
                boolean pending = ArchvowGrammar.str(q,"answer").isBlank();
                addQuickItem(keys,new QuickItem(pending ? QuickKind.QUESTION : QuickKind.RESOLVED,
                    id + " · " + key + " " + shorten(text,60), text + "\n" + (pending ? "" : "Answer / Réponse: " + ArchvowGrammar.str(q,"answer")),
                    id,pending,pending ? ArchvowI18n.text(project,"Answer", "Répondre") : ArchvowI18n.text(project,"Open component", "Ouvrir le composant"),
                    pending ? "decision make" : "component-show", pending ? Map.of("component",id,"question",key,"view","question") : Map.of("view","question","question",key)));
            }
            for (JsonObject safety : ArchvowGrammar.objects(data, "safety")) {
                String number = ArchvowGrammar.str(safety,"number");
                String question = ArchvowGrammar.str(safety,"question");
                boolean pending = ArchvowSafetyState.pending(safety);
                String missingProtection = ArchvowSafetyState.unprotectedHazard(safety);
                String word = "S"+number;
                String safetyAction = pending ? (missingProtection.isBlank() ? "hazard add" : "invariant add") : "component-show";
                String title = id + " · " + word + " " + shorten(question,60);
                String detail = question + "\n" + ArchvowSafetyState.preview(safety);
                Map<String,String> preset = new java.util.LinkedHashMap<>();
                preset.put("component", id);
                preset.put("guide_word", word);
                preset.put("view", "safety");
                if (!missingProtection.isBlank()) preset.put("prevents", missingProtection);
                addQuickItem(keys,new QuickItem(pending ? QuickKind.QUESTION : QuickKind.RESOLVED,
                    title, detail, id, pending,
                    pending ? (missingProtection.isBlank() ? ArchvowI18n.text(project,"Review hazard", "Examiner le hazard")
                        : ArchvowI18n.text(project,"Add required invariant", "Ajouter l'invariant requis"))
                        : ArchvowI18n.text(project,"Open component", "Ouvrir le composant"),
                    safetyAction, preset));
            }
            for (JsonObject assumption : ArchvowGrammar.objects(data, "assumptions")) {
                String number=ArchvowGrammar.str(assumption,"number");
                String status=ArchvowGrammar.str(assumption,"status").toLowerCase(java.util.Locale.ROOT);
                boolean pending = !status.equals("accepted") && !status.equals("rejected");
                String title=ArchvowGrammar.str(assumption,"text");
                addQuickItem(keys,new QuickItem(pending ? QuickKind.SUGGESTION : QuickKind.RESOLVED,
                    id + " · A"+number+ " " + shorten(title,60), title + "\nStatus: " + status,
                    id,pending,pending ? ArchvowI18n.text(project,"Accept", "Accepter") : ArchvowI18n.text(project,"Open component", "Ouvrir le composant"),
                    pending ? "assumption accept" : "component-show",pending ? Map.of("component",id,"number",number,"view","assumption") : Map.of("view","assumption","number",number)));
            }
            for (ArchvowDataView.Item item : ArchvowDataView.items(data)) {
                if (item.kind() != ArchvowDataView.Kind.COLUMN) continue;
                boolean pending = item.pending();
                String name = item.id();
                addQuickItem(keys, new QuickItem(pending ? QuickKind.QUESTION : QuickKind.RESOLVED,
                    id + " · " + shorten(name, 60), item.detail(), id, pending,
                    pending ? ArchvowI18n.text(project, "Classify", "Classifier") : ArchvowI18n.text(project, "Open component", "Ouvrir le composant"),
                    pending ? "column set" : "component-show",
                    pending ? Map.of("component", id, "names", name, "view", "data") : Map.of("view", "data")));
            }
            // `missing` can be JSON objects or plain strings, depending on CLI version.
            JsonElement missingRecords = data.get("missing");
            if (missingRecords != null && missingRecords.isJsonArray()) {
                for (JsonElement missing : missingRecords.getAsJsonArray()) {
                    if (missing == null || missing.isJsonNull()) continue;
                    String detail = missing.isJsonObject() ?
                        ArchvowGrammar.str(missing.getAsJsonObject(), "message") : missing.getAsString();
                    if (detail.isBlank() && missing.isJsonObject())
                        detail = ArchvowGrammar.str(missing.getAsJsonObject(), "text");
                    if (detail.isBlank()) detail = ArchvowGrammar.pretty(missing);
                    boolean schemaMissing = "schema".equalsIgnoreCase(detail.trim());
                    // 'missing: schema' and ARCH019 describe the same contract gap.
                    // Show one actionable row rather than duplicate "Create schema" entries.
                    if (schemaMissing && hasSchemaDiagnostic(id)) continue;
                    addQuickItem(keys, new QuickItem(QuickKind.WARNING,
                        id + " · " + ArchvowI18n.text(project, "Missing contract", "Contrat manquant") + " · " + shorten(detail, 65),
                        detail, id, true,
                        schemaMissing ? ArchvowI18n.text(project,"Create schema", "Créer un schéma")
                            : ArchvowI18n.text(project,"Open component", "Ouvrir le composant"),
                        schemaMissing ? "schema-create" : "component-show", Map.of("view", "other")));
                }
            }
            for (ArchvowDataView.Item item : ArchvowDataView.items(data)) {
                if (item.kind() != ArchvowDataView.Kind.TASK) continue;
                boolean pending = item.pending();
                addQuickItem(keys, new QuickItem(pending ? QuickKind.SUGGESTION : QuickKind.RESOLVED,
                    id + " · " + item.id() + " " + shorten(item.title(), 60),
                    item.title() + (item.detail().isBlank() ? "" : "\n" + item.detail()),
                    id, pending,
                    pending ? ArchvowI18n.text(project, "View task prompt", "Voir la tâche") : ArchvowI18n.text(project, "Open component", "Ouvrir le composant"),
                    pending ? "prompt" : "component-show", Map.of("view", "data")));
            }
        }
        // Do not turn 'hazards' counts or arbitrary NEXT prose into unfinished items.
        // A hazard may be a completed safety answer, and NEXT is a recommendation, not a status.
        quickTabs.setTitleAt(0, ArchvowI18n.text(project,"Pending", "En attente") + " (" + otherPendingModel.size() + ")");
        quickTabs.setTitleAt(1, ArchvowI18n.text(project,"Resolved", "Traités") + " (" + otherResolvedModel.size() + ")");
        if (componentViewButtons.size() > 6) {
            componentViewButtons.get(7).setText(ArchvowI18n.text(project,
                "Other", "Autres") + (otherPendingModel.isEmpty() ? "" : " · " + otherPendingModel.size()));
            componentViewButtons.get(7).setToolTipText(ArchvowI18n.text(project,
                "Unclassified and project-wide issues", "Éléments non classés et problèmes du projet"));
        }
        updateComponentStatusColors();
        if (!"all".equals(componentFilter)) rebuildTree();
        if (quickTabs.getSelectedIndex() == 0 && !otherPendingModel.isEmpty()) otherPendingList.setSelectedIndex(0);
        else if (quickTabs.getSelectedIndex() == 1 && !otherResolvedModel.isEmpty()) otherResolvedList.setSelectedIndex(0);
        else if (!otherPendingModel.isEmpty()) { quickTabs.setSelectedIndex(0); otherPendingList.setSelectedIndex(0); }
        else if (!otherResolvedModel.isEmpty()) { quickTabs.setSelectedIndex(1); otherResolvedList.setSelectedIndex(0); }
        else showQuickItem(null);
    }

    private boolean hasSchemaDiagnostic(String componentId) {
        if (componentId == null || componentId.isBlank()) return false;
        for (JsonObject violation : ArchvowGrammar.objects(check, "violations")) {
            if (!"ARCH019".equalsIgnoreCase(ArchvowGrammar.str(violation, "code"))) continue;
            String referenced = ArchvowGrammar.str(violation, "component");
            if (!componentSnapshots.containsKey(referenced))
                referenced = ArchvowIssueAction.quotedComponent(ArchvowGrammar.str(violation, "message"), componentSnapshots.keySet());
            if (componentId.equals(referenced)) return true;
        }
        return false;
    }

    /** Status is derived only from actual pending/resolved items, not hazard counts. */
    private void updateComponentStatusColors() {
        pendingByComponent.clear();
        resolvedByComponent.clear();
        for (int i = 0; i < pendingModel.size(); i++) {
            QuickItem item = pendingModel.get(i);
            if (!item.componentId.isBlank())
                pendingByComponent.merge(item.componentId, 1, Integer::sum);
        }
        for (int i = 0; i < resolvedModel.size(); i++) {
            QuickItem item = resolvedModel.get(i);
            if (!item.componentId.isBlank())
                resolvedByComponent.merge(item.componentId, 1, Integer::sum);
        }
        componentsTree.putClientProperty("archvow.pendingCounts", Map.copyOf(pendingByComponent));
        componentsTree.putClientProperty("archvow.resolvedCounts", Map.copyOf(resolvedByComponent));
        componentsTree.repaint();
        updateComponentPendingExplanation();
    }

    /** Explain why the component total can exceed the Rules & data count. */
    private void updateComponentPendingExplanation() {
        if (selectedComponent == null) {
            componentPendingExplanation.setVisible(false);
            return;
        }
        String id = selectedComponent.id;
        int total = 0;
        int data = 0;
        int other = 0;
        for (int i = 0; i < pendingModel.size(); i++) {
            QuickItem item = pendingModel.get(i);
            if (!id.equals(item.componentId)) continue;
            total++;
            String category = item.preset.getOrDefault("view", "other");
            if (category.equals("data")) data++;
            if (category.equals("other")) other++;
        }
        String label = ArchvowI18n.isFrench(project) ?
            total + " en attente pour " + id + " · " + data + " dans Données et tâches" +
                (other > 0 ? " · " + other + " dans Autres" : "") :
            total + " pending for " + id + " · " + data + " in Data & tasks" +
                (other > 0 ? " · " + other + " in Other" : "");
        componentPendingExplanation.setText(label);
        componentPendingExplanation.setToolTipText(ArchvowI18n.text(project,
            "The component total includes all its pending categories and active diagnostics.",
            "Le total du composant inclut toutes ses catégories en attente et les diagnostics actifs."));
        componentPendingExplanation.setVisible(total > 0 && !listFilterLine.isVisible());
    }

    private void addQuickItem(Set<String> keys, QuickItem item) {
        String key=item.pending + "|" + item.commandLabel + "|" + item.componentId + "|" +
            item.preset + "|" + item.title + "|" + item.detail;
        if (!keys.add(key)) return;
        (item.pending ? pendingModel : resolvedModel).addElement(item);
        if (otherCategory(item))
            (item.pending ? otherPendingModel : otherResolvedModel).addElement(item);
    }

    /** Known component work belongs to Decisions, Questions, Safety, Assumptions or Rules & data.
     * Diagnostics, unclassified component records and project-level records belong to Other.
     */
    private static boolean otherCategory(QuickItem item) {
        if (item.componentId.isBlank()) return true;
        return switch (item.commandLabel) {
            case "decision make", "decision propose", "decision accept", "hazard add", "hazard dismiss",
                "assumption accept", "assumption reject", "invariant add", "column set", "prompt" -> false;
            case "component-show" -> item.kind == QuickKind.WARNING || item.kind == QuickKind.ERROR;
            // Unknown future commands should remain visible in Other, not disappear.
            default -> true;
        };
    }

    /** Local Other/Project: keep only real component-local leftovers, not global check diagnostics. */
    private static boolean componentOtherCategory(QuickItem item) {
        return item != null && !item.componentId.isBlank()
            && "other".equals(item.preset.getOrDefault("view", ""))
            && item.guide == null;
    }

    private void refreshComponentOtherModels() {
        componentOtherPendingModel.clear();
        componentOtherResolvedModel.clear();
        String componentId = selectedComponent == null ? "" : selectedComponent.id;
        if (componentId.isBlank()) {
            if (componentViewButtons.size() > 7) componentViewButtons.get(7).setText(ArchvowI18n.text(project, "Other", "Autres"));
            showComponentOtherItem(null);
            return;
        }
        for (int i = 0; i < pendingModel.size(); i++) {
            QuickItem item = pendingModel.get(i);
            if (componentId.equals(item.componentId) && componentOtherCategory(item)) componentOtherPendingModel.addElement(item);
        }
        for (int i = 0; i < resolvedModel.size(); i++) {
            QuickItem item = resolvedModel.get(i);
            if (componentId.equals(item.componentId) && componentOtherCategory(item)) componentOtherResolvedModel.addElement(item);
        }
        if (componentViewButtons.size() > 7) {
            int otherPending = componentOtherPendingModel.size();
            int otherTotal = componentOtherPendingModel.size() + componentOtherResolvedModel.size();
            JToggleButton other = componentViewButtons.get(7);
            other.setText(ArchvowI18n.text(project, "Other", "Autres")
                + (otherPending > 0 ? " · " + otherPending : otherTotal > 0 ? " ✓" : ""));
            other.setToolTipText(otherPending > 0
                ? ArchvowI18n.text(project,
                    "Component-local uncategorised items pending.",
                    "Éléments locaux au composant encore en attente.")
                : ArchvowI18n.text(project,
                    "No component-local uncategorised item.",
                    "Aucun élément local au composant dans cette vue."));
        }
        componentOtherTabs.setTitleAt(0, ArchvowI18n.text(project, "Pending", "En attente") + " (" + componentOtherPendingModel.size() + ")");
        componentOtherTabs.setTitleAt(1, ArchvowI18n.text(project, "Resolved", "Traités") + " (" + componentOtherResolvedModel.size() + ")");
        if (componentOtherTabs.getSelectedIndex() == 0 && !componentOtherPendingModel.isEmpty()) componentOtherPendingList.setSelectedIndex(0);
        else if (componentOtherTabs.getSelectedIndex() == 1 && !componentOtherResolvedModel.isEmpty()) componentOtherResolvedList.setSelectedIndex(0);
        else if (!componentOtherPendingModel.isEmpty()) {
            componentOtherTabs.setSelectedIndex(0);
            componentOtherPendingList.setSelectedIndex(0);
        } else if (!componentOtherResolvedModel.isEmpty()) {
            componentOtherTabs.setSelectedIndex(1);
            componentOtherResolvedList.setSelectedIndex(0);
        } else showComponentOtherItem(null);
    }

    private void showQuickItem(QuickItem item) {
        if (quickSelectionArea != null) quickSelectionArea.setVisible(item != null);
        if (quickResultCard != null) quickResultCard.setVisible(false);
        if (item == null) {
            quickDetails.setText("");
            quickPrimary.setEnabled(false);
            quickSecondary.setEnabled(false);
            quickSecondary.setVisible(false);
            return;
        }
        quickDetails.setText(htmlHead() + ArchvowHtml.heading(escape(item.title), 1.1, "margin:4px 0 6px 0;")
            + chip(item.pending ? ArchvowI18n.text(project,"Pending","En attente") :
                ArchvowI18n.text(project,"Resolved","Traité"),
                item.pending ? "#FFF1D7" : "#E5F4EA", item.pending ? "#9B6818" : "#13835B")
            + (item.guide == null ? "" : "<p style='margin:6px 0 0 0;'><i>" + escape(ArchvowI18n.text(project, "archvow says", "archvow dit")) + "</i></p>")
            + "<p style='white-space:pre-wrap;'>" + escape(item.detail).replace("\n", "<br>") + "</p>"
            + (item.componentId.isBlank() ? "" : "<p><b>" +
                escape(ArchvowI18n.text(project,"Component","Composant")) + ":</b> " + escape(item.componentId) + "</p>")
            + (item.guide == null ? "" : guideHtml(item.guide))
            + "</body></html>");
        quickDetails.setCaretPosition(0);
        quickPrimary.setEnabled(true);
        quickPrimary.setText(item.actionLabel);
        String secondary = switch(item.commandLabel) {
            case "hazard add" -> ArchvowI18n.text(project,"Not applicable", "Non applicable");
            case "brief accept" -> ArchvowI18n.text(project, "Review the brief", "Relire la brief");
            case "assumption accept" -> ArchvowI18n.text(project,"Reject", "Rejeter");
            case "decision accept" -> ArchvowI18n.text(project,"Edit answer", "Autre réponse");
            case "schema-create" -> ArchvowI18n.text(project,"Edit reader dependencies", "Modifier les dépendances du lecteur");
            case "open-file", "component edit", "invariant add" -> item.componentId.isBlank() ? "" :
                ArchvowI18n.text(project,"Open component", "Ouvrir le composant");
            // a diagnostic about a file, treated by a command: the file too
            default -> item.guide != null && item.guide.file() != null
                ? ArchvowI18n.text(project, "Open ", "Ouvrir ") + item.guide.file().path()
                    + (item.guide.file().line() > 0 ? ":" + item.guide.file().line() : "")
                : ""; // No duplicated "Open component" buttons.
        };
        quickSecondary.setVisible(!secondary.isBlank());
        quickSecondary.setEnabled(!secondary.isBlank());
        quickSecondary.setText(secondary);
    }

    private void showComponentOtherItem(QuickItem item) {
        if (item == null) {
            componentOtherDetails.setText(htmlMessage(ArchvowI18n.text(project,
                "No component-specific uncategorised item.",
                "Aucun élément local au composant dans cette vue.")));
            componentOtherPrimary.setEnabled(false);
            componentOtherPrimary.setText(ArchvowI18n.text(project, "Treat now", "Traiter maintenant"));
            componentOtherSecondary.setVisible(false);
            return;
        }
        showQuickItem(item);
        componentOtherDetails.setText(quickDetails.getText());
        componentOtherDetails.setCaretPosition(0);
        componentOtherPrimary.setEnabled(quickPrimary.isEnabled());
        componentOtherPrimary.setText(quickPrimary.getText());
        componentOtherSecondary.setVisible(quickSecondary.isVisible());
        componentOtherSecondary.setEnabled(quickSecondary.isEnabled());
        componentOtherSecondary.setText(quickSecondary.getText());
    }

    /** Only use a container explicitly declared in the architecture manifest. */
    private String containerForComponent(String componentId) {
        if (componentId == null || componentId.isBlank() || manifest == null) return "";
        for (JsonObject container : ArchvowGrammar.objects(manifest, "containers")) {
            for (JsonObject item : ArchvowGrammar.objects(container, "components")) {
                if (componentId.equals(ArchvowGrammar.str(item, "id")))
                    return ArchvowGrammar.str(container, "id");
            }
        }
        return "";
    }

    private void handleQuickPrimaryAction() {
        QuickItem item = selectedQuickItem;
        if (item == null) return;
        switch (item.commandLabel) {
            // the invariant a hazard needs is written in the component's Safety view, filled with the suggestion
            case "decision make", "decision accept", "decision propose", "hazard add", "hazard dismiss", "invariant add" ->
                openQuickItemInline(item);
            case "schema-create" -> {
                Map<String, String> context = new java.util.LinkedHashMap<>();
                context.put("type", "schema");
                String container = containerForComponent(item.componentId);
                if (!container.isBlank()) context.put("container", container);
                openCommand("component add", context);
            }
            case "component edit" -> openCommand(item.commandLabel, item.preset);
            case "open-file" -> openViolationFile(item);
            case "open-diagnostics" -> showDiagnostics();
            case "open-project-vow" -> openProjectVow();
            case "component-show" -> goToQuickComponent();
            case "prompt" -> executeDirect("prompt", List.of("prompt", item.componentId), null, false, true);
            default -> openCommand(item.commandLabel, item.preset);
        }
    }

    private void openQuickItemInline(QuickItem item) {
        if (item == null || item.componentId.isBlank()) return;
        tabs.setSelectedIndex(1);
        setComponentFilter("all");
        if (!locate(componentsRoot, new ArrayList<>(), item.componentId)) {
            selectedComponent = new NodeComponent(item.componentId, "", "", "");
        }
        JsonObject snapshot = componentSnapshots.get(item.componentId);
        if (snapshot == null) {
            if (selectedComponent != null) loadComponent(selectedComponent);
            return;
        }
        selectedView = snapshot;
        renderComponent(snapshot);
        if (item.commandLabel.startsWith("hazard") || item.commandLabel.equals("invariant add")) {
            selectComponentView(2);
            String word = item.preset.getOrDefault("guide_word", "");
            String number = word.startsWith("S") ? word.substring(1) : word;
            selectRecordByNumber(safetyList, ArchvowGrammar.objects(snapshot, "safety"), number);
            showSafety();
        } else {
            String number = item.preset.getOrDefault("number", item.preset.getOrDefault("question", ""));
            boolean keyed = !number.matches("[0-9]+") && !number.isBlank();
            if (keyed) {
                selectComponentView(1);
                for (int i = 0; i < ArchvowGrammar.objects(snapshot, "questions").size(); i++) {
                    if (number.equals(ArchvowGrammar.str(ArchvowGrammar.objects(snapshot, "questions").get(i), "key"))) {
                        questionList.setSelectedIndex(i);
                        break;
                    }
                }
                showQuestion();
            } else {
                selectComponentView(0);
                selectRecordByNumber(decisionsList, ArchvowGrammar.objects(snapshot, "decisions"), number);
                showDecision();
            }
        }
    }

    private void handleQuickSecondaryAction() {
        QuickItem item=selectedQuickItem;
        if (item==null) return;
        switch (item.commandLabel) {
            case "hazard add" -> openCommand("hazard dismiss", Map.of(
                "component", item.componentId, "guide_word", item.preset.getOrDefault("guide_word", "")));
            case "assumption accept" -> openCommand("assumption reject", item.preset);
            case "decision accept" -> openCommand("decision make", Map.of(
                "component", item.componentId, "question", item.preset.getOrDefault("number", "")));
            case "schema-create" -> openCommand("component edit", Map.of("component", item.componentId));
            case "brief accept" -> openBrief(item.preset.getOrDefault("brief", ""));
            case "open-file", "component edit", "invariant add" -> goToQuickComponent();
            default -> {
                if (item.guide != null && item.guide.file() != null)
                    openViolationFile(item.guide.file().path(), item.guide.file().line());
                else goToQuickComponent();
            }
        }
    }

    /** A brief at its heading ("specs/x.md#section"), inside the project only. */
    private void openBrief(String ref) {
        String base = project.getBasePath();
        if (ref == null || ref.isBlank() || base == null) return;
        String relative = ref.split("#", 2)[0];
        int line = ArchvowBriefs.line(ArchvowBriefs.files(base, ArchvowBriefs.folders(manifest)), ref);
        try {
            File root = new File(base).getCanonicalFile();
            File file = new File(root, relative).getCanonicalFile();
            if (!file.toPath().startsWith(root.toPath()) || !file.isFile()) {
                message(ArchvowI18n.text(project, "Brief not found: " + relative, "Brief introuvable : " + relative));
                return;
            }
            var virtual = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
            if (virtual != null) new OpenFileDescriptor(project, virtual, Math.max(0, line - 1), 0).navigate(true);
        } catch (IOException ex) { message(ex.getMessage()); }
    }

    private void openViolationFile(QuickItem item) {
        int line;
        try { line = Integer.parseInt(item.preset.getOrDefault("line", "0")); } catch (NumberFormatException e) { line = 0; }
        openViolationFile(item.preset.getOrDefault("file", ""), line);
    }

    /** A project file a diagnostic is about, at its line (1-based; 0: the top). */
    private void openViolationFile(String relative, int line) {
        String base=project.getBasePath();
        if (relative.isBlank() || base == null) {
            message(ArchvowI18n.text(project, "Diagnostic location is not available.",
                "Emplacement du diagnostic indisponible."));
            return;
        }
        try {
            File root = new File(base).getCanonicalFile();
            File file = new File(root, relative).getCanonicalFile();
            if (!file.toPath().startsWith(root.toPath()) || !file.exists()) {
                message(ArchvowI18n.text(project, "Diagnostic location not found: ",
                    "Emplacement du diagnostic introuvable : ") + relative);
                return; // Never reset the selected diagnostic when its path is missing.
            }
            var virtual = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
            if (virtual == null) {
                message(ArchvowI18n.text(project, "Cannot locate: ", "Emplacement introuvable : ") + relative);
                return;
            }
            if (file.isDirectory()) {
                // ARCH040 may point to .claude itself: switch the sidebar to Project,
                // instead of leaving it split between Project and Archvow.
                revealProjectFolder(virtual);
            } else if (file.isFile() && line > 0) {
                new OpenFileDescriptor(project, virtual, line - 1, 0).navigate(true);
            } else if (file.isFile()) {
                FileEditorManager.getInstance(project).openFile(virtual, true);
            } else {
                message(ArchvowI18n.text(project, "Unsupported diagnostic location: ",
                    "Emplacement du diagnostic non pris en charge : ") + relative);
            }
        } catch(IOException ex) { message(ex.getMessage()); }
    }

    /** Reveal a configuration directory as VS Code's revealInExplorer does: Project takes over
     *  the sidebar, while the Archvow editor tab (and selected check) stays open. */
    private void revealProjectFolder(com.intellij.openapi.vfs.VirtualFile directory) {
        ToolWindowManager manager = ToolWindowManager.getInstance(project);
        ToolWindow projectWindow = manager.getToolWindow("Project");
        if (projectWindow == null) {
            message(ArchvowI18n.text(project,
                "Project explorer is not available.", "L'explorateur du projet est indisponible."));
            return;
        }
        ToolWindow archvowWindow = manager.getToolWindow("Archvow");
        // Wait until Archvow is hidden, then until Project is active. Otherwise the
        // two windows can stay stacked and the folder receives no visible focus.
        ArchvowProjectFolderReveal.handOff(
            archvowWindow != null && archvowWindow.isVisible()
                ? archvowWindow::hide : Runnable::run,
            projectWindow::activate,
            () -> ProjectView.getInstance(project).select(null, directory, true));
    }

    /** A diagnostic's guidance: what it means, what to do, the command, who, whether it blocks, then. */
    private String guideHtml(ArchvowGuidance.Guidance g) {
        StringBuilder html = new StringBuilder("<table cellpadding='2' style='margin-top:6px;'>");
        java.util.function.BiConsumer<String, String> row = (name, text) -> html.append("<tr><td valign='top' nowrap><b>")
            .append(escape(name)).append("</b></td><td>").append(escape(text).replaceAll("`([^`]+)`", "<code>$1</code>")).append("</td></tr>");
        row.accept(ArchvowI18n.text(project, "What it means", "Ce que cela veut dire"), g.meaning());
        row.accept(ArchvowI18n.text(project, "What to do", "Que faire"), g.todo());
        if (g.command() != null) {
            String note = (g.command().missing().isEmpty() ? "" : ArchvowI18n.text(project, " - the form leaves for you to fill in: ",
                " - le formulaire vous laisse remplir : ") + String.join(", ", g.command().missing()))
                + (g.command().readOnly() ? ArchvowI18n.text(project, " - it changes nothing.", " - elle ne change rien.")
                : ArchvowI18n.text(project, " - prepared in a form; nothing runs before you confirm.", " - préparée dans un formulaire ; rien ne tourne avant votre confirmation."));
            html.append("<tr><td valign='top' nowrap><b>")
                .append(escape(ArchvowI18n.text(project, "The command", "La commande")))
                .append("</b></td><td>").append(ArchvowTerminal.html(g.command().text()))
                .append("<small>").append(escape(note)).append("</small></td></tr>");
        }
        row.accept(ArchvowI18n.text(project, "Who", "Qui"), g.who());
        row.accept(ArchvowI18n.text(project, "Blocks?", "Bloquant ?"), g.blocking());
        row.accept(ArchvowI18n.text(project, "Then", "Ensuite"), g.then());
        return html.append("</table>").toString();
    }

    private void goToQuickComponent() {
        QuickItem item=selectedQuickItem;
        if (item == null || item.componentId.isBlank()) return;
        tabs.setSelectedIndex(1);
        setComponentFilter("all");
        if (!locate(componentsRoot,new ArrayList<>(),item.componentId)) {
            selectedComponent=new NodeComponent(item.componentId,"","","");
            loadComponent(selectedComponent);
        }
    }

    /** Counters navigate to the component work itself, not to an aggregate Status view. */
    private void openQuickList(int listIndex) {
        if (role == Role.NAVIGATION) {
            // the tool window shows the components with such work; the editor tab opens the first of them
            ArchvowPanel detail = ArchvowEditorTab.panel(project);
            tabs.setSelectedIndex(1);
            if (detail == null) return;
            detail.openQuickListFromNavigation(listIndex);
            setComponentFilter(detail.componentFilter);
            follow(detail.selectedComponent == null ? "" : detail.selectedComponent.id, null);
            return;
        }
        showWorkspace();
        tabs.setSelectedIndex(1);
        boolean pending = listIndex == 0;
        DefaultListModel<QuickItem> all = pending ? pendingModel : resolvedModel;
        QuickItem firstComponent = null;
        for (int i = 0; i < all.size(); i++) {
            QuickItem candidate = all.get(i);
            if (!candidate.componentId.isBlank() && !otherCategory(candidate)) {
                firstComponent = candidate;
                break;
            }
        }
        if (firstComponent != null) {
            setComponentFilter(pending ? "pending" : "resolved");
            if (locate(componentsRoot, new ArrayList<>(), firstComponent.componentId)) {
                JsonObject snapshot = componentSnapshots.get(firstComponent.componentId);
                if (snapshot != null) {
                    selectedComponent = new NodeComponent(firstComponent.componentId, "", "", "");
                    selectedView = snapshot;
                    renderComponent(snapshot);
                }
                selectComponentView(viewForItem(firstComponent));
                selectRecordFromItem(firstComponent);
            } else {
                setComponentFilter("all");
                selectComponentView(7);
            }
        } else {
            // Only project-wide or unclassified items remain. Never bind these to a guessed component.
            setComponentFilter("all");
            selectComponentView(7);
            quickTabs.setSelectedIndex(listIndex);
            JList<QuickItem> list = pending ? otherPendingList : otherResolvedList;
            if (list.getModel().getSize() > 0) {
                list.setSelectedIndex(0);
                list.ensureIndexIsVisible(0);
            }
        }
    }

    /** Overview › Architecture › Project items: the Other view, on the first project-wide item (0.25.0). */
    private void openProjectWork() {
        if (role == Role.NAVIGATION) {
            ArchvowPanel detail = ArchvowEditorTab.panel(project);
            tabs.setSelectedIndex(1);
            if (detail == null) return;
            detail.openProjectWork();
            detail.afterSelectionChange();
            return;
        }
        showWorkspace();
        tabs.setSelectedIndex(1);
        setComponentFilter("all");
        selectComponentView(7);
        quickTabs.setSelectedIndex(0);
        for (int i = 0; i < otherPendingModel.size(); i++) {
            QuickItem item = otherPendingModel.get(i);
            if (item.componentId.isBlank() && ownItem(item)) {
                otherPendingList.setSelectedIndex(i);
                otherPendingList.ensureIndexIsVisible(i);
                return;
            }
        }
    }

    private static int viewForItem(QuickItem item) {
        String view = item.preset.getOrDefault("view", "");
        return switch (view) {
            case "decision" -> 0;
            case "question" -> 1;
            case "safety" -> 2;
            case "assumption" -> 3;
            case "data" -> 5;
            case "other" -> 7;
            default -> switch (item.commandLabel) {
                case "decision accept", "decision propose" -> 0;
                case "decision make" -> item.preset.getOrDefault("question", "").matches("[0-9]+") ? 0 : 1;
                case "hazard add", "hazard dismiss", "invariant add" -> 2;
                case "assumption accept", "assumption reject" -> 3;
                case "column set", "prompt" -> 5;
                default -> 6;
            };
        };
    }

    private void selectRecordFromItem(QuickItem item) {
        JsonObject snapshot = componentSnapshots.get(item.componentId);
        if (snapshot == null) return;
        int view = viewForItem(item);
        if (view == 0) selectRecordByNumber(decisionsList,
            ArchvowGrammar.objects(snapshot, "decisions"),
            item.preset.getOrDefault("number", item.preset.getOrDefault("question", "")));
        else if (view == 1) {
            String key = item.preset.getOrDefault("question", "");
            for (int i = 0; i < ArchvowGrammar.objects(snapshot, "questions").size(); i++) {
                if (key.equals(ArchvowGrammar.str(ArchvowGrammar.objects(snapshot,"questions").get(i), "key"))) {
                    questionList.setSelectedIndex(i);
                    break;
                }
            }
        } else if (view == 2) {
            String word = item.preset.getOrDefault("guide_word", "");
            selectRecordByNumber(safetyList, ArchvowGrammar.objects(snapshot,"safety"),
                word.startsWith("S") ? word.substring(1) : word);
        } else if (view == 3) {
            selectRecordByNumber(assumptionList, ArchvowGrammar.objects(snapshot,"assumptions"),
                item.preset.getOrDefault("number", ""));
        }
    }

    private String componentFromViolation(String message) {
        if (message == null) return "";
        Matcher matcher = QUOTED_COMPONENT.matcher(message);
        return matcher.find() ? matcher.group(1) : "";
    }

    private String quickSuggestionForViolation(String code, String message) {
        if ("ARCH004".equals(code)) {
            String component = componentFromViolation(message);
            if (!component.isBlank()) {
                return ArchvowI18n.text(project,"Compare the code signature with the specification; choose which is authoritative.",
                    "Comparer la signature du code avec la spécification, puis choisir la référence.");
            }
        }
        return ArchvowI18n.text(project,
            "Use the quick action button to open the relevant component or command.",
            "Utilise le bouton d'action rapide pour ouvrir le composant ou la commande pertinente.");
    }

    private interface ComponentAction { void run(NodeComponent component); }
    private void withComponent(ComponentAction action) {
        if (selectedComponent == null) { message("Select a component first / Sélectionne d'abord un composant."); return; }
        action.run(selectedComponent);
    }
    // ------------------------------------------------------------------ AI skills (0.16.0)

    /** The skills a person started on a component from here (kept in the project), so the suggestion moves on. */
    private List<String> launched(String component) {
        String raw = com.intellij.ide.util.PropertiesComponent.getInstance(project).getValue("archvow.launched." + component, "");
        return raw.isBlank() ? List.of() : List.of(raw.split(","));
    }

    private void recordLaunched(String component, String skill) {
        if (component == null || component.isBlank()) return;
        Set<String> done = new LinkedHashSet<>(launched(component));
        done.add(skill);
        com.intellij.ide.util.PropertiesComponent.getInstance(project).setValue("archvow.launched." + component, String.join(",", done));
    }

    /** "All AI skills": the five, in the order of the work, the suggested one marked, why one cannot run yet. */
    private void showAiMenu(Component anchor) {
        boolean fr = ArchvowI18n.isFrench(project);
        JsonObject skills = ArchvowForms.section("ai_skills");
        String suggested = aiView.suggest == null ? "" : aiView.suggest.skill();
        List<ArchvowMenu.Item> work = new ArrayList<>();
        for (String id : ArchvowAiSkills.SKILLS) {
            JsonObject def = ArchvowSkillForm.def(skills, id);
            if (def == null) continue;
            String[] blocked = aiView.blocked.get(id);
            String line = blocked != null ? blocked[fr ? 1 : 0] : ArchvowSkillForm.pair(def, "does", fr);
            work.add(new ArchvowMenu.Item(ArchvowSkillForm.pair(def, "label", fr), line, id, blocked == null, id.equals(suggested),
                fr ? "SUGGÉRÉ" : "SUGGESTED", () -> openSkill(id, Map.of())));
        }
        List<ArchvowMenu.Item> copy = List.of(new ArchvowMenu.Item(
            ArchvowI18n.text(project, "Copy the card for another AI", "Copier la fiche pour une autre IA"),
            ArchvowI18n.text(project, "for an assistant that cannot read the project's files", "pour un assistant qui ne lit pas les fichiers du projet"),
            "prompt", () -> withComponent(c -> executeDirect("prompt", List.of("prompt", c.id), null, false))));
        showMenu(ArchvowMenu.create(ArchvowI18n.text(project, "WHAT THE AI CAN DO · in the order of the work",
            "CE QUE L'IA PEUT FAIRE · dans l'ordre du travail"), List.of(work, copy)), anchor);
    }

    /** "+ Add": what a person adds to this component (a new component is added from the tree, 0.17.0). */
    private void showAddMenu(Component anchor) {
        String[][] items = {{"A question", "Une question", "question add"}, {"A rule", "Une règle", "rule add"},
            {"An assumption", "Une hypothèse", "assumption add"}};
        List<ArchvowMenu.Item> rows = new ArrayList<>();
        for (String[] it : items)
            rows.add(new ArchvowMenu.Item(ArchvowI18n.text(project, it[0], it[1]), "", it[2], () -> withComponent(c -> openCommand(it[2],
                "rule add".equals(it[2]) ? Map.of("words", c.id + "\n") : Map.of("component", c.id)))));
        showMenu(ArchvowMenu.create(null, List.of(rows)), anchor);
    }

    /** A menu under its button (or under the actions when the button went into More actions). */
    private void showMenu(JPopupMenu menu, Component anchor) {
        Component at = anchor != null && anchor.isShowing() ? anchor : componentActions;
        if (at != null && at.isShowing()) menu.show(at, 0, at.getHeight());
    }

    /** The AI skill form lives in the shared editor tab, like command forms and checks. */
    void openSkill(String skill, Map<String, String> initial) {
        if (role == Role.NAVIGATION) {
            ArchvowEditorTab.showSkill(project, skill, initial);
            return;
        }
        skillBackToBriefs = "briefs".equals(detailHomeMode);
        ArchvowPanel nav = navigation();
        if (nav != null && nav.manifest != null) adoptFrom(nav);
        boolean fr = ArchvowI18n.isFrench(project);
        Map<String, String> values = new LinkedHashMap<>(initial);
        if (!values.containsKey("component") && selectedComponent != null)
            values.put("component", selectedComponent.id);
        List<String> components = new ArrayList<>();
        for (JsonObject c : ArchvowGrammar.objects(manifest, "containers"))
            for (JsonObject comp : ArchvowGrammar.objects(c, "components"))
                components.add(ArchvowGrammar.str(comp, "id"));
        List<String> briefs = new ArrayList<>();
        if (briefStatus != null) for (JsonObject f : ArchvowGrammar.objects(briefStatus, "files")) {
            briefs.add(ArchvowGrammar.str(f, "file"));
            for (JsonObject r : ArchvowGrammar.objects(f, "sections"))
                if (!ArchvowGrammar.str(r, "section").isBlank())
                    briefs.add(ArchvowGrammar.str(f, "file") + "#" + ArchvowGrammar.str(r, "section"));
        }
        List<String> tools = ArchvowGrammar.strings(manifest, "ai_tools");
        JsonObject skills = ArchvowForms.section("ai_skills");
        JsonObject def = ArchvowSkillForm.def(skills, skill);
        detailSkillLabel = def == null ? skill : ArchvowSkillForm.pair(def, "label", fr);
        ArchvowSkillForm form = new ArchvowSkillForm(skills, skill, values, components, briefs, tools,
            c -> {
                JsonObject view = componentSnapshots.get(c);
                return view == null ? null : ArchvowAiSkills.view(view, launched(c)).blocked.get(skill);
            }, fr, (text, chosen) -> {
                Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new java.awt.datatransfer.StringSelection(text), null);
                recordLaunched(chosen.get("component"), skill);
                if (selectedView != null) renderComponent(selectedView);
                message(ArchvowI18n.text(project,
                    "Copied: paste it into your AI assistant. The AI's proposals still require your review.",
                    "Copié : colle le texte dans ton assistant IA. Ses propositions restent soumises à ta validation."));
            }, () -> {
                showDetailHome("result");
                afterSelectionChange();
            });
        // A scrollable form tracks the editor width instead of keeping a large
        // preferred width that cuts off the right-hand actions and prompt text.
        JScrollPane scroll = new JScrollPane(form);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createLineBorder(borderColor(), 1, true));
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        skillDetails.removeAll();
        skillDetails.add(scroll, BorderLayout.CENTER);
        skillDetails.revalidate();
        skillDetails.repaint();
        backComponent = null;
        showWorkspace();
        showDetailHome("skill");
        tabs.setSelectedIndex(0);
        afterSelectionChange();
    }

    /** The suggestion strip of the component's head: what the AI added first, then the next skill, with why. */
    private void refreshSuggestion() {
        boolean fr = ArchvowI18n.isFrench(project);
        ArchvowAiSkills.Suggestion sg = aiView.suggest;
        suggestStrip.setVisible(false);  // 0.17.2: no strip (AI ▾ marks the suggested step)
        for (var l : suggestGo.getActionListeners()) suggestGo.removeActionListener(l);
        if (sg == null) return;
        boolean review = "to-review".equals(sg.skill());
        JsonObject def = ArchvowSkillForm.def(ArchvowForms.section("ai_skills"), sg.skill());
        String name = def == null ? sg.skill() : ArchvowSkillForm.pair(def, "label", fr);
        suggestText.setText("<html><b>" + escape(review ? ArchvowI18n.text(project, "The AI added things for you to review", "L'IA a ajouté des éléments à relire")
            : ArchvowI18n.text(project, "Suggested next with AI: ", "Suggéré ensuite avec l'IA : ") + name) + "</b><br>"
            + escape(sg.reason()[fr ? 1 : 0]) + "</html>");
        suggestGo.setText(review ? ArchvowI18n.text(project, "Show them", "Les afficher") : name + "…");
        suggestGo.addActionListener(e -> {
            if (!review) { openSkill(sg.skill(), Map.of()); return; }
            originFilter = "review";
            int view = aiView.count("decisions") > 0 ? 0 : aiView.count("safety") > 0 ? 2 : aiView.count("rules") > 0 ? 4
                : aiView.count("assumptions") > 0 ? 3 : 0;
            if (selectedView != null) renderComponent(selectedView);
            selectComponentView(view);
        });
    }

    /** Rules: Keep / Remove for each rule the AI added that no person kept yet. */
    private void refreshRulesAiActions() {
        rulesAiActions.removeAll();
        for (var entry : aiView.rules.entrySet()) {
            if (!entry.getValue().toReview()) continue;
            String id = entry.getKey();
            rulesAiActions.add(button(ArchvowI18n.text(project, "Keep " + id, "Garder " + id), () -> openCommand("rule accept", Map.of("id", id))));
            rulesAiActions.add(button(ArchvowI18n.text(project, "Remove " + id, "Retirer " + id), () -> openCommand("rule remove", Map.of("id", id))));
        }
        rulesAiActions.setVisible(rulesAiActions.getComponentCount() > 0);
        rulesAiActions.revalidate();
        rulesAiActions.repaint();
    }

    /** The selected decision's origin (its row's place in the component's decisions). */
    private ArchvowAiSkills.Origin decisionOrigin(JsonObject row) {
        List<JsonObject> rows = ArchvowGrammar.objects(selectedView, "decisions");
        int i = rows.indexOf(row);
        return i >= 0 && i < aiView.decisions.size() ? aiView.decisions.get(i) : null;
    }

    /** A command on the selected decision (question accept / remove), its number filled. */
    private void withSelectedDecision(String command) {
        JsonObject row = selectedRecord("decisions", decisionsList.getSelectedIndex());
        if (row == null || selectedComponent == null) return;
        openCommand(command, Map.of("component", selectedComponent.id, "number", ArchvowGrammar.str(row, "number")));
    }

    /** The id of the safety question's hazard the AI added (or protected) and no person kept yet, else "". */
    private String waitingHazard(JsonObject safetyRow) {
        for (JsonObject h : ArchvowGrammar.objects(safetyRow, "hazards")) {
            ArchvowAiSkills.Origin o = aiView.hazards.get(ArchvowGrammar.str(h, "id"));
            if (o != null && o.toReview()) return ArchvowGrammar.str(h, "id");
        }
        return "";
    }

    private void keepAiHazard() {
        JsonObject row = selectedRecord("safety", safetyList.getSelectedIndex());
        String id = row == null ? "" : waitingHazard(row);
        if (!id.isEmpty()) openCommand("hazard accept", Map.of("id", id));
    }

    private void message(String text) {
        // a long or several-line text (what was tried, archvow's own error): a dialog sized to it, copiable
        if (text != null && (text.length() > 140 || text.strip().contains("\n"))) {
            ArchvowTextDialog.show(this, "Archvow", text, null, ArchvowI18n.isFrench(project));
            return;
        }
        JOptionPane.showMessageDialog(this, text, "Archvow", JOptionPane.INFORMATION_MESSAGE);
    }

    /** The cards of a view, on the left of its split view: no frame, no horizontal scroll; "Nothing to
     *  display." while the view has no item (ArchvowSplitView.cards). */
    private JPanel cardsScroll(JList<?> list) {
        list.setOpaque(false);
        JScrollPane pane = new JScrollPane(list, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        pane.setBorder(BorderFactory.createEmptyBorder());
        pane.setMinimumSize(new Dimension(0, 80));
        return ArchvowSplitView.cards(list, pane, () -> ArchvowI18n.text(project, "Nothing to display.", "Rien à afficher."));
    }

    /** An answer field: wrapped lines, the editor's monospaced font. */
    private static void answerField(JTextArea field) {
        field.setLineWrap(true);
        field.setWrapStyleWord(true);
        field.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        field.setBorder(BorderFactory.createEmptyBorder(6, 7, 6, 7));
    }

    /** The top of an answer panel: what is answered and its question, at the height of its text (at most 280). */
    private static JComponent answerHeading(JComponent detail) {
        detail.setBorder(BorderFactory.createEmptyBorder(2, 2, 6, 2));
        detail.setOpaque(false);
        JScrollPane pane = new JScrollPane(detail, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER) {
            @Override public Dimension getPreferredSize() {
                int width = Math.max(200, getWidth() > 0 ? getWidth() : 360);
                detail.setSize(width, Short.MAX_VALUE);
                return new Dimension(width, Math.min(Math.max(detail.getPreferredSize().height + 4, 48), 280));
            }
        };
        pane.setBorder(BorderFactory.createEmptyBorder());
        pane.setOpaque(false);
        pane.getViewport().setOpaque(false);
        return pane;
    }

    /** The buttons under an answer field: Cancel and the main one on the right, the others on the left. */
    private static JPanel answerButtons(List<JButton> others, JButton cancel, JComponent main) {
        java.util.List<JComponent> right = new ArrayList<>();
        if (cancel != null) right.add(cancel);
        right.add(main);
        return ArchvowSplitView.actions(new ArrayList<>(others), right);
    }

    /**
     * Undo changes (was "Cancel", whose purpose was not obvious): pressable only once the field's text
     * differs from the recorded one; it closes an open confirmation and puts that text back. Nothing is
     * saved or removed.
     */
    private JButton cancelButton(javax.swing.text.JTextComponent field, Runnable restore) {
        JButton undo = ArchvowSplitView.link(ArchvowI18n.text(project, "Undo", "Annuler"));  // 0.17.0: a word, not a button
        undo.addActionListener(e -> {
            if (componentConfirmation != null) componentConfirmation.hideConfirmation();
            restore.run();
        });
        Runnable state = () -> {
            boolean edited = !field.getText().equals(String.valueOf(field.getClientProperty(RECORDED)));
            undo.setEnabled(edited && field.isEnabled());
            undo.setToolTipText(edited
                ? ArchvowI18n.text(project, "Put back the recorded text in the field (nothing is saved or removed)",
                    "Remettre le texte enregistré dans le champ (rien n'est enregistré ni supprimé)")
                : ArchvowI18n.text(project, "Nothing to undo: the field holds the recorded text",
                    "Rien à annuler : le champ contient le texte enregistré"));
        };
        field.putClientProperty(UNDO_STATE, state);
        field.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { state.run(); }
            @Override public void removeUpdate(DocumentEvent e) { state.run(); }
            @Override public void changedUpdate(DocumentEvent e) { state.run(); }
        });
        field.addPropertyChangeListener("enabled", e -> state.run());
        state.run();
        return undo;
    }

    private static final String RECORDED = "archvow.recorded";
    private static final String UNDO_STATE = "archvow.undoState";

    /** The text an answer field opens with (Undo changes puts it back). */
    private static void recorded(javax.swing.text.JTextComponent field, String text) {
        field.putClientProperty(RECORDED, text == null ? "" : text);
        field.setText(text == null ? "" : text);
        if (field.getClientProperty(UNDO_STATE) instanceof Runnable state) state.run();
    }

    private static JTextArea area() {
        JTextArea a = new JTextArea();
        a.setEditable(false); a.setLineWrap(true); a.setWrapStyleWord(true);
        a.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        a.setBorder(BorderFactory.createEmptyBorder(7, 9, 7, 9));
        return a;
    }
    private <T extends JLabel> T translated(T label, String english, String french) {
        Runnable update = () -> label.setText(ArchvowI18n.text(project, english, french));
        languageRefreshers.add(update);
        update.run();
        return label;
    }

    /** A button with what it does, in the IDE's language. */
    private JButton tip(JButton button, String english, String french) {
        button.setToolTipText("<html><body style='width:320px'>" + escape(ArchvowI18n.text(project, english, french)) + "</body></html>");
        return button;
    }

    private static JButton button(String text, Runnable action) {
        JButton b = new JButton(text);
        // The IDE Look-and-Feel handles button borders, states and contrast.
        // Never force a white or opaque rectangle in dark mode.
        b.setMargin(new Insets(4, 10, 4, 10));
        ArchvowUiIcons.decorate(b);
        b.addActionListener(event -> action.run());
        return b;
    }
    private static JScrollPane scroll(java.awt.Component component) { return new JScrollPane(component); }
    private static JEditorPane htmlPane() {
        JEditorPane pane = new JEditorPane("text/html", "");
        pane.setEditable(false);
        pane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        pane.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        pane.setBackground(UIManager.getColor("Panel.background"));
        pane.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        return pane;
    }
    /** Result content alone gets rounded native card painting; other HTML views are unchanged. */
    private static JEditorPane resultHtmlPane() {
        JEditorPane pane = new ArchvowResultPane();
        pane.setEditable(false);
        pane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        pane.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        pane.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        return pane;
    }
    private static JLabel badgeLabel() {
        JLabel label = new JLabel();
        label.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        return label;
    }
    private void labelDetails(JScrollPane scroll) {
        Runnable update = () -> scroll.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(borderColor()),
            ArchvowI18n.text(project, "Details", "Détails")));
        update.run();
        languageRefreshers.add(update);
    }

    private static JPanel createCard(Component child, String title) {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(borderColor()),
            BorderFactory.createEmptyBorder(8, 10, 10, 10)));
        panel.setBackground(UIManager.getColor("Panel.background"));
        JLabel head = new JLabel(title);
        String headingIcon = ArchvowUiIcons.kindForLabel(title);
        if (headingIcon != null) {
            head.setIcon(ArchvowUiIcons.iconFor(head, headingIcon));
            head.setIconTextGap(7);
        }
        panel.putClientProperty("archvow.heading", head);
        head.setFont(head.getFont().deriveFont(Font.BOLD, 16f));
        panel.add(head, BorderLayout.NORTH);
        panel.add(child, BorderLayout.CENTER);
        return panel;
    }
    private static String shorten(String text, int max) {
        if (text == null) return "";
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }
    private static boolean darkTheme() {
        Color base = UIManager.getColor("Panel.background");
        return base != null && (base.getRed() * .299 + base.getGreen() * .587 + base.getBlue() * .114) < 128;
    }
    private static Color borderColor() {
        Color named = UIManager.getColor("Component.borderColor");
        if (named != null) return darkTheme() ? ArchvowMotion.blend(named, Color.WHITE, .08) : named;
        return darkTheme() ? new Color(68, 74, 88) : new Color(220, 225, 230);
    }
    /** Quiet, rounded command-results surface; no blue frame or double dark border. */
    private static void applyResultCardPalette(JPanel card) {
        card.setOpaque(false);
        card.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
    }

    private static void paintResultCardSurface(Graphics graphics, JComponent card) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color background = UIManager.getColor("Panel.background");
            if (background == null) background = darkTheme() ? new Color(39, 40, 44) : Color.WHITE;
            Color fill = ArchvowMotion.blend(background, ArchvowMotion.accent(), darkTheme() ? .075 : .035);
            Color outline = ArchvowMotion.blend(fill, darkTheme() ? Color.WHITE : Color.BLACK,
                darkTheme() ? .14 : .12);
            int w = card.getWidth() - 1, h = card.getHeight() - 1;
            if (w < 1 || h < 1) return;
            g.setColor(fill);
            g.fillRoundRect(0, 0, w, h, 13, 13);
            g.setColor(outline);
            g.drawRoundRect(0, 0, w, h, 13, 13);
        } finally { g.dispose(); }
    }

    /** Result actions only: understated rounded buttons, with the existing labels and icons. */
    private static void styleResultAction(AbstractButton button) {
        // Do not install the generic animated border: it makes result buttons look boxed in.
        button.putClientProperty("archvow.button.hoverInstalled", Boolean.TRUE);
        button.setOpaque(false);
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        button.setFocusPainted(false);
        button.setRolloverEnabled(true);
        button.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
    }

    private static void paintResultActionSurface(Graphics graphics, AbstractButton button) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color base = UIManager.getColor("Panel.background");
            if (base == null) base = darkTheme() ? new Color(39, 40, 44) : Color.WHITE;
            Color ink = darkTheme() ? Color.WHITE : Color.BLACK;
            Color fill = ArchvowMotion.blend(base, ink, darkTheme() ? .09 : .045);
            ButtonModel model = button.getModel();
            if (model.isSelected()) fill = ArchvowMotion.blend(fill, ArchvowMotion.accent(), .16);
            if (model.isRollover() && button.isEnabled())
                fill = ArchvowMotion.blend(fill, ArchvowMotion.accent(), .12);
            if (model.isPressed() && model.isArmed())
                fill = ArchvowMotion.blend(fill, ArchvowMotion.accent(), .22);
            Color outline = ArchvowMotion.blend(fill, ink, darkTheme() ? .16 : .14);
            if (!button.isEnabled()) {
                fill = ArchvowMotion.blend(fill, base, .35);
                outline = ArchvowMotion.blend(outline, base, .35);
            }
            if (button.isFocusOwner()) outline = ArchvowMotion.blend(outline, ArchvowMotion.accent(), .4);
            int w = button.getWidth() - 1, h = button.getHeight() - 1;
            if (w < 1 || h < 1) return;
            g.setColor(fill);
            g.fillRoundRect(0, 0, w, h, 9, 9);
            g.setColor(outline);
            g.drawRoundRect(0, 0, w, h, 9, 9);
        } finally { g.dispose(); }
    }

    private static JButton resultActionButton(String text, Runnable action) {
        JButton button = new JButton(text) {
            @Override protected void paintComponent(Graphics g) {
                paintResultActionSurface(g, this);
                super.paintComponent(g);
            }
        };
        styleResultAction(button);
        ArchvowUiIcons.decorate(button);
        button.addActionListener(e -> action.run());
        return button;
    }

    private static JToggleButton resultToggleButton(String text) {
        JToggleButton button = new JToggleButton(text) {
            @Override protected void paintComponent(Graphics g) {
                paintResultActionSurface(g, this);
                super.paintComponent(g);
            }
        };
        styleResultAction(button);
        ArchvowUiIcons.decorate(button);
        return button;
    }

    /** The command as typed, a code chip (VS Code's <code>): monospaced, a tinted background, a thin outline. */
    private static void styleCommandChip(JLabel chip) {
        Font base = UIManager.getFont("Label.font");
        int size = base == null ? 12 : Math.max(11, base.getSize() - 1);
        chip.setFont(new Font(Font.MONOSPACED, Font.PLAIN, size));
        Color panel = UIManager.getColor("Panel.background");
        Color text = UIManager.getColor("Label.foreground");
        if (panel == null) panel = darkTheme() ? new Color(43, 45, 48) : Color.WHITE;
        if (text == null) text = darkTheme() ? Color.WHITE : Color.BLACK;
        chip.setOpaque(true);
        chip.setBackground(ArchvowMotion.blend(panel, text, darkTheme() ? .12 : .07));
        chip.setForeground(text);
        chip.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(ArchvowMotion.blend(panel, text, darkTheme() ? .22 : .16)),
            BorderFactory.createEmptyBorder(1, 5, 1, 5)));
    }

    private static Color mutedTextColor() {
        return darkTheme() ? new Color(162, 172, 186) : new Color(85, 95, 110);
    }
    private static Color buttonBackground() {
        Color base = UIManager.getColor("Button.background");
        if (base == null) base = UIManager.getColor("Panel.background");
        return darkTheme() ? ArchvowMotion.blend(base, Color.WHITE, .05) : ArchvowMotion.blend(base, Color.WHITE, .35);
    }
    private static JComponent sectionHeader(String title, String subtitle) {
        JPanel panel = new JPanel(new BorderLayout(0, 1));
        panel.setOpaque(false);
        JLabel head = new JLabel(title);
        head.setFont(head.getFont().deriveFont(Font.BOLD, 12f));
        JLabel sub = new JLabel(subtitle);
        sub.setForeground(mutedTextColor());
        sub.setFont(sub.getFont().deriveFont(11f));
        panel.setBorder(BorderFactory.createEmptyBorder(2, 2, 1, 2));
        panel.add(head, BorderLayout.NORTH);
        panel.add(sub, BorderLayout.SOUTH);
        return panel;
    }
    private static String htmlHead() {
        return ArchvowHtml.body(darkTheme() ? "#DBE3EC" : "#243044", darkTheme() ? "#292B30" : "#FFFFFF");
    }
    /** As htmlHead, on the panel's own background (the component head, the answer panels). */
    private static String htmlBareHead() {
        return ArchvowHtml.body(darkTheme() ? "#DBE3EC" : "#243044", null);
    }
    private static String htmlBare(String body) {
        return htmlBareHead() + body + "</body></html>";
    }
    private static String htmlMessage(String body) {
        return htmlHead() + body + "</body></html>";
    }
    private static String escape(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
    /** An answer panel's heading as HTML: "ANSWER QUESTION P1", then its state in green. */
    private static String answerHead(String text, String state) {
        return "<div style='color:" + mutedHex() + "; " + ArchvowHtml.size(0.9) + "'><b>"
            + escape(text.toUpperCase(java.util.Locale.ROOT)) + "</b>"
            + (state.isBlank() ? "" : "&nbsp;&nbsp;&nbsp;<b style='color:" + (darkTheme() ? "#74D5A7" : "#0F8451") + "'>" + escape(state) + "</b>")
            + "</div>";
    }

    private static String mutedHex() {
        return darkTheme() ? "#A4B1C0" : "#5D6A7B";
    }

    private static String chip(String label, String bg, String fg) {
        return "<span style='display:inline-block; margin:0 8px 8px 0; padding:4px 10px; border-radius:12px; background:" + bg + "; color:" + fg + ";'><b>" + escape(label) + "</b></span>";
    }
    private static String statusChip(String value) {
        String normalized = value == null ? "" : value.toLowerCase();
        String bg = "#EAF3FF", fg = "#0B57D0";
        if (normalized.contains("verified") || normalized.contains("implemented")) { bg = "#E8F7EF"; fg = "#107A42"; }
        else if (normalized.contains("missing") || normalized.contains("error")) { bg = "#FFE9EC"; fg = "#B01E2D"; }
        else if (normalized.contains("partial") || normalized.contains("warn")) { bg = "#FFF4DB"; fg = "#A16000"; }
        return chip(value, bg, fg);
    }

    private static ImageIcon loadLogoIcon(int width, int height) {
        try (InputStream in = ArchvowPanel.class.getResourceAsStream("/archvow-logo.png")) {
            if (in == null) return null;
            BufferedImage image = ImageIO.read(in);
            if (image == null) return null;
            Image scaled = image.getScaledInstance(width, height, Image.SCALE_SMOOTH);
            return new ImageIcon(scaled);
        } catch (IOException e) {
            return null;
        }
    }

    private static final class QuickItemRenderer extends JPanel implements ListCellRenderer<QuickItem> {
        private final JLabel title = new JLabel();
        private final JLabel detail = new JLabel();
        private final JLabel badge = new JLabel();
        QuickItemRenderer() {
            super(new BorderLayout(4, 2));
            title.setFont(title.getFont().deriveFont(Font.BOLD, 12f));
            title.setOpaque(false);
            detail.setFont(detail.getFont().deriveFont(11f));
            detail.setOpaque(false);
            detail.setForeground(UIManager.getColor("Label.foreground"));
            JPanel first = new JPanel(new BorderLayout(4, 0));
            first.setOpaque(false);
            first.add(title, BorderLayout.CENTER);
            badge.setOpaque(true);
            badge.setFont(badge.getFont().deriveFont(Font.BOLD, 10f));
            badge.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
            first.add(badge, BorderLayout.EAST);
            add(first, BorderLayout.NORTH);
            add(detail, BorderLayout.CENTER);
        }
        @Override public java.awt.Component getListCellRendererComponent(JList<? extends QuickItem> list, QuickItem value,
                                                                             int index, boolean isSelected, boolean cellHasFocus) {
            int available = Math.max(130, list.getWidth() - 35);
            title.setForeground(darkTheme() ? new Color(229, 234, 240) : new Color(28, 36, 46));
            title.setText(shorten(value.title, Math.max(15, (available - 80) / 7)));
            String snippet = shorten(value.detail.replace('\n', ' ').trim(), Math.max(25, (available / 6) * 2));
            detail.setForeground(darkTheme() ? new Color(198, 206, 216) : new Color(65, 75, 88));
            detail.setText("<html><body width='" + Math.max(90, available - 12) + "'>" + escape(snippet) + "</body></html>");
            detail.setToolTipText(value.detail);
            title.setToolTipText(value.title);
            Color bg = switch (value.kind) {
                case ERROR -> new Color(255, 244, 245);
                case WARNING -> new Color(255, 249, 233);
                case QUESTION -> new Color(255, 249, 233);
                case DECISION -> new Color(238, 245, 255);
                case SUGGESTION -> new Color(249, 242, 255);
                case RESOLVED -> new Color(238, 249, 242);
            };
            Color border = switch (value.kind) {
                case ERROR -> new Color(225, 120, 132);
                case WARNING -> new Color(222, 168, 74);
                case QUESTION -> new Color(222, 168, 74);
                case DECISION -> new Color(118, 158, 227);
                case SUGGESTION -> new Color(166, 123, 221);
                case RESOLVED -> new Color(99, 183, 132);
            };
            String status = switch (value.kind) {
                case ERROR -> "✕";
                case WARNING -> "!";
                case QUESTION -> "?";
                case DECISION -> "D";
                case SUGGESTION -> "!";
                case RESOLVED -> "✓";
            };
            setOpaque(true);
            if (darkTheme()) {
                bg = switch(value.kind) {
                    case ERROR -> new Color(70, 42, 48);
                    case WARNING, QUESTION -> new Color(65, 55, 38);
                    case DECISION -> new Color(40, 49, 69);
                    case SUGGESTION -> new Color(58, 45, 69);
                    case RESOLVED -> new Color(38, 65, 52);
                };
                title.setForeground(new Color(229, 234, 240));
                detail.setForeground(new Color(198, 206, 216));
            }
            float hover = ArchvowMotion.hoverLevel(list, index);
            setBackground(isSelected ? ArchvowMotion.blend(bg, ArchvowMotion.accent(),
                darkTheme() ? .36 : .25) : ArchvowMotion.blend(bg, list.getBackground(), hover));
            badge.setText(status);
            badge.setBackground(border);
            badge.setForeground(Color.WHITE);
            setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, isSelected ? 5 : 3, 1, 0,
                    isSelected ? ArchvowMotion.accent() : border),
                BorderFactory.createEmptyBorder(6, 8, 5, 8)));
            setPreferredSize(new Dimension(available, 70));
            return this;
        }
    }

}
