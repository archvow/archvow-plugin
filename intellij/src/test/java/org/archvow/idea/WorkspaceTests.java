package org.archvow.idea;

/**
 * Runs every test of the workspace's plain classes ({@code ./gradlew workspaceTest}, headless): each
 * test is a main() that throws on its first failure. The panel itself needs a running IDE
 * ({@code ./gradlew runIde}).
 */
public final class WorkspaceTests {
    private static final String[] NONE = new String[0];
    private static int passed;

    private interface Test {
        void run() throws Exception;
    }

    public static void main(String[] args) throws Exception {
        run("ArchitectureViewRegression", () -> ArchitectureViewRegression.main(NONE));
        run("AnswerEditorRegression", () -> AnswerEditorRegression.main(NONE));
        run("ArchvowDataViewSmoke", () -> ArchvowDataViewSmoke.main(NONE));
        run("BriefsRegression", () -> BriefsRegression.main(NONE));
        run("ButtonHoverRegression", () -> ButtonHoverRegression.main(NONE));
        run("CommandPrefillRegression", () -> CommandPrefillRegression.main(NONE));
        run("CompactViewsRegression", () -> CompactViewsRegression.main(NONE));
        run("DetailAndLogRegression", () -> DetailAndLogRegression.main(NONE));
        run("EditorTabLookRegression", () -> EditorTabLookRegression.main(NONE));
        run("GlobalCatalogRegression", () -> GlobalCatalogRegression.main(NONE));
        run("InitWizardRegression", () -> InitWizardRegression.main(NONE));
        run("InlineCommandRegression", () -> InlineCommandRegression.main(NONE));
        run("InstallRegression", () -> InstallRegression.main(NONE));
        run("LaunchCommandRegression", () -> LaunchCommandRegression.main(NONE));
        run("MotionRegression", () -> MotionRegression.main(NONE));
        run("NativeChecksAndRulesRegression", () -> NativeChecksAndRulesRegression.main(NONE));
        run("NavigationTabsRegression", () -> NavigationTabsRegression.main(NONE));
        run("ProgressRegression", () -> ProgressRegression.main(NONE));
        run("ProjectReadinessRegression", () -> ProjectReadinessRegression.main(NONE));
        run("ResponsiveOverflowRegression", () -> ResponsiveOverflowRegression.main(NONE));
        run("ResultFormatterRegression", () -> ResultFormatterRegression.main(NONE));
        run("RuleDocsRegression", () -> RuleDocsRegression.main(NONE));
        run("SafetyAndActionsRegression", () -> SafetyAndActionsRegression.main(NONE));
        run("SafetyDetailsRegression", () -> SafetyDetailsRegression.main(NONE));
        run("SplitViewRegression", () -> SplitViewRegression.main(NONE));
        run("StatusPaletteRegression", () -> StatusPaletteRegression.main(NONE));
        run("TaskPromptRegression", () -> TaskPromptRegression.main(NONE));
        run("UsesAndFileRegression", () -> UsesAndFileRegression.main(NONE));
        run("TextDialogRegression", () -> TextDialogRegression.main(NONE));
        run("TextSizeRegression", () -> TextSizeRegression.main(NONE));
        run("ToolWindowLayoutRegression", () -> ToolWindowLayoutRegression.main(NONE));
        run("UiPolishRegression", () -> UiPolishRegression.main(NONE));
        run("ViewNavigationRegression", () -> ViewNavigationRegression.main(NONE));
        run("VersionRegression", () -> VersionRegression.main(NONE));
        run("VowViewerRegression", () -> VowViewerRegression.main(NONE));
        run("VowWatcherRegression", () -> VowWatcherRegression.main(NONE));
        run("WslRegression", () -> WslRegression.main(NONE));
        System.out.println("workspace tests: " + passed + " test classes passed");
    }

    private static void run(String name, Test test) throws Exception {
        try {
            test.run();
            passed++;
        } catch (Exception | AssertionError e) {
            throw new AssertionError(name + " failed: " + e.getMessage(), e);
        }
    }
}
