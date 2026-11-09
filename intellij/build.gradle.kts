// The archvow plugin for JetBrains IDEs: the archvow workspace (org.archvow.idea) and the .vow
// language (org.archvow.vow), in one plugin.
// Build: ./gradlew buildPlugin  ->  build/distributions/archvow-intellij-<version>.zip
// Needs a JDK 21 (IntelliJ's own runtime works: JAVA_HOME=".../IntelliJ IDEA.app/Contents/jbr/Contents/Home").
// Tests: ./gradlew check  (coreTest: the .vow reader; workspaceTest: the workspace's plain classes)
plugins {
    java
    id("org.jetbrains.intellij.platform") version "2.19.0"
}

group = "org.archvow"
version = providers.gradleProperty("pluginVersion").get()

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity(providers.gradleProperty("platformVersion").get())
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

intellijPlatform {
    buildSearchableOptions = false
    // Stable uploads use the default channel; opt-in betas go to the
    // JetBrains Marketplace `beta` repository.
    publishing {
        channels = if (providers.environmentVariable("ARCHVOW_MARKETPLACE_CHANNEL").orNull == "beta")
            listOf("beta") else emptyList()
    }
    pluginConfiguration {
        ideaVersion {
            sinceBuild = providers.gradleProperty("sinceBuild")
            untilBuild = provider { null }
        }
    }
}

// What archvow says about itself, recorded in ../contract/cli by contract/tools/regenerate.py: the command
// grammar (the Commands forms), the template catalog (init, offline), the meaning of each check code.
tasks.named<ProcessResources>("processResources") {
    from(layout.projectDirectory.dir("../contract/cli")) {
        include("commands.json", "catalog.json", "kinds.json", "rules.md", "rules.fr.md", "version.txt")
        into("archvow")
    }
    from(layout.projectDirectory.dir("../contract/forms")) {   // how each command's form is laid out, the trees' icons
        include("forms.json", "icons.json")
        into("archvow")
    }
}

// The tests need no IntelliJ running and no test framework: each is a main() that throws on the first
// failure. The shared samples (../contract/vows) are archvow's own vows.
val contract = layout.projectDirectory.dir("../contract")
// What they run with: the compiled classes and, after them, the IntelliJ Platform's jars (Gson, the
// openapi classes the workspace's plain classes name). The IntelliJ Platform Gradle plugin puts those on
// the compile classpath only, so the test runtime classpath alone fails on the first one
// (NoClassDefFoundError: com/google/gson/JsonElement).
val testClasspath = sourceSets["test"].runtimeClasspath + sourceSets["main"].compileClasspath

// The plain-Java reader of .vow files (org.archvow.vow.core) on the shared sample vows.
val coreTest by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Tests the .vow reader on the shared sample vows"
    classpath = testClasspath
    mainClass.set("org.archvow.vow.core.CoreTests")
    args(contract.dir("vows").asFile.path)
}

// The workspace's classes that hold no IntelliJ state (formatting, prefill, status, views), headless.
val workspaceTest by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Tests the workspace's plain classes, headless"
    classpath = testClasspath
    mainClass.set("org.archvow.idea.WorkspaceTests")
    jvmArgs("-Djava.awt.headless=true")
}

// Writes ../contract/vow-reader/expected.json: what this reader gives on the samples and on broken vows.
// The VS Code extension's reader is tested against it. Run after a change of org.archvow.vow.core.
val contractDump by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Writes the .vow reader's contract for the VS Code extension"
    classpath = testClasspath
    mainClass.set("org.archvow.vow.core.DumpContract")
    args(contract.dir("vows").asFile.path, contract.file("vow-reader/expected.json").asFile.path)
}

tasks.named("check") {
    dependsOn(coreTest, workspaceTest)
}

tasks.named<Test>("test") {
    enabled = false  // the tests are coreTest and workspaceTest
}
