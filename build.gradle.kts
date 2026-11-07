import java.util.Base64

plugins {
    id("org.jetbrains.kotlin.jvm") version "2.4.20"
    id("org.jetbrains.intellij.platform") version "2.19.0"
}

group = "io.github.rejeb"
version = "1.0.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    implementation(platform("com.google.cloud:libraries-bom:26.91.0"))
    implementation("com.google.cloud:google-cloud-dataform")
    implementation("com.google.cloud:google-cloud-bigquery")
    testImplementation("org.mockito:mockito-core:5.23.0")
    testImplementation("org.mockito:mockito-junit-jupiter:5.23.0")

    testImplementation("org.junit.vintage:junit-vintage-engine:5.10.0")
    intellijPlatform {
        intellijIdea("2026.2")
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
        pluginVerifier()
        bundledPlugin("JavaScript")
        bundledPlugin("NodeJS")
        bundledPlugin("com.intellij.database")
        bundledPlugin("org.jetbrains.plugins.yaml")
        bundledPlugin("com.intellij.modules.json")
        bundledPlugin("org.jetbrains.plugins.terminal")
        bundledPlugin("Git4Idea")
    }

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testImplementation("org.junit.platform:junit-platform-launcher")
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "262"
        }

        changeNotes = """
                <h2>1.0.0</h2>
                <p>Requires IntelliJ IDEA Ultimate 2026.2 or later. The full list of supported IDEs is on the plugin page.</p>
                <h3>New</h3>
                <ul>
                    <li>BigQuery cost estimates: the compiled query preview shows the bytes each action would process and their estimated on-demand cost, with a total for the file; executed queries and workflow runs show the bytes billed and their cost, per action and per run. Prices come from Google Cloud Billing for the location of each query, or from a fixed price per TiB; capacity (reservation) projects show bytes only. Hover a cost to see how it was priced. Configured under Settings ▸ Tools ▸ Dataform ▸ BigQuery cost, with the currency.</li>
                    <li>Column impact analysis on save: when a saved change renames, removes or retypes a column read downstream, the affected actions and their BigQuery errors are listed per file in the Build tool window, and each downstream error names the change behind it.</li>
                    <li>Rename downstream usages: after renaming a column in its action, the new name is highlighted with a quick fix (and a balloon) that renames every downstream read of the old name through the rename preview.</li>
                    <li>Operations with <code>hasOutput: true</code> are dry-run like tables: their schema is resolved and their BigQuery errors are shown in the editor and in the build.</li>
                    <li>New Dataform Tools tool window: repository and workspace pickers, a Files tab showing the workspace files with their status, a Changes tab (with the number of changed files) to commit and push the workspace, Fetch to Local and Compare with Local on any file, and Open in Google Cloud Console for the workspace or a single file.</li>
                    <li>Commit and push follow how the project relates to the repository's Git remote. A local clone of it commits with Git: the Changes tab's Commit and Push are disabled and, with the Git plugin, link to New Branch. Without a local clone, Commit and Push stay in the workspace, suggest cloning, and ask which branch of the remote receives the commits, the default branch or the one named after the workspace, as in the Dataform console.</li>
                    <li>Workflow run console: a status header, the action tree with durations and a Show Failed Only filter, and per action a Summary, the Executed SQL and a Jobs tab drawing the BigQuery child jobs and statements on a timeline, with the SQL of the selected statement and its failing line marked. The invocation and each job link to the Google Cloud console.</li>
                    <li>Query results in the Services view are grouped under Dataform Queries, with Query, Results and Job Info tabs (bytes, slot time and cost), and a Remove Finished Queries action.</li>
                    <li>SQLX editor toolbar: Compile, Run and Run action buttons, and a switch between the Query, Schema and Lineage views (Test for unit test files).</li>
                    <li>Compiled preview: a header with the compilation time and the estimated total, one block per action with its assertions in their own tab, resizable SQL blocks and a Copy button.</li>
                    <li>Schema view: the output columns of each action as a tree, nested <code>STRUCT</code> fields included, with type, mode and description, and a button to copy a column's path.</li>
                    <li>Build tool window: Dataform compilation errors are shown with the code frame they point to, and a Dataform CLI that fails to start or stops before compiling is explained, with the command to reproduce it.</li>
                    <li>Error and UI freeze reports: when the IDE shows an error or a slowdown notice for this plugin, you can send it to the plugin's page on JetBrains Marketplace after reviewing what it contains. If you turned on automatic error reporting in the IDE, reports are sent without asking.</li>
                    <li>Open a project from an existing Dataform repository: File ▸ New ▸ Project from Version Control (Clone Repository on the Welcome screen) has a Dataform tab listing the repositories of a GCP project and location. A repository with a Git remote is cloned by default, with your local Git credentials; any repository can also be downloaded from one of its workspaces. The new project is linked to the repository in the Dataform tool window. The New Project wizard links to this tab.</li>
                    <li>New plugin logo and file icons; unit test files get their own icon.</li>
                </ul>
                <h3>Improvements</h3>
                <ul>
                    <li>Lineage views restyled: selecting a node highlights its whole upstream and downstream lineage and dims the rest, with a legend, a focus banner, zoom controls and a resizable details panel.</li>
                    <li>Manage Dataform Repositories: the Location and Service Account fields filter their list as you type.</li>
                    <li>The build waits for the schema refresh and lists failing BigQuery dry-runs in the Build tool window, grouped per file; it no longer reports success while queries fail, and no longer opens the first file with an error.</li>
                    <li>Execute query and schema refreshes always use a compilation matching the current sources, waiting for a pending compilation in the background when needed.</li>
                    <li>Running an action from the IDE uses a temporary workspace.</li>
                    <li>Workflow runs started from the IDE execute as the service account set on the repository entry; before, it only applied when the repository was created from the IDE.</li>
                    <li>Compile on save is always on; its setting was removed.</li>
                    <li>Show Column Usages moved to <code>Alt+Shift+U</code>: <code>Ctrl+Alt+Shift+U</code> belongs to Show Diagram in IntelliJ IDEA Ultimate.</li>
                    <li>SQLFluff no longer reorders <code>SELECT</code> columns by default: rule ST06 is excluded unless a config file or <code>--exclude-rules</code> is set.</li>
                    <li>Cost estimates apply BigQuery's 10 MB minimum to queries that read a table, and the tooltip says when it applies.</li>
                    <li>Creating a unit test compiles the project, so its Test view is up to date.</li>
                </ul>
                <h3>Fixes</h3>
                <ul>
                    <li>Tables and incremental tables were shown as views in <code>ref()</code> completion, Quick Documentation and the compiled preview.</li>
                    <li>The Changes tab listed <code>node_modules</code> and files excluded by <code>.gitignore</code>.</li>
                    <li>Assertions could be missing from the preview after a restart, because an outdated compilation was restored from the cache.</li>
                    <li>Long lines of a query block could not be scrolled horizontally.</li>
                    <li>A table name could be matched inside a longer, dashed table name when building schema queries (<code>orders</code> in <code>orders-v2</code>).</li>
                    <li>Rename downstream usages left the unit test inputs mocking a renamed table with the old column name.</li>
                    <li>The Navigate menu had lost its Declaration or Usages item.</li>
                    <li>An underscore in a repository or workspace name was dropped in the Dataform Tools pickers (<code>user_purchase</code> showed as <code>userpurchase</code>).</li>
                    <li>In-place rename failed with an error in SQLX files holding evaluated expressions.</li>
                    <li>The <code>${"$"}{ref("")}</code> and <code>${"$"}{self()}</code> completions left the caret too far in files holding evaluated expressions.</li>
                    <li>SQLX keywords were offered after <code>alias.</code> and while typing digits in a query.</li>
                    <li>The file lineage panel went blank after the editor height changed.</li>
                    <li>Lineage labels mixed upper and lower case action types, and the column lineage line was clipped in the details panel.</li>
                    <li>The Schema view said an assertion's table did not exist yet.</li>
                    <li>A workflow run ending in failure could leave its actions shown as running, with a timer still counting and no failure selected, when the invocation ended just before its actions.</li>
                    <li>Column rename warned that a file could not be opened when a <code>STRUCT</code> field or an operation's column had no select item to rewrite.</li>
                    <li>A repository linked to a Git remote failed with <code>is not hosted internally</code> when no workspace was selected, because Dataform does not serve its files without one. Dataform Tools now shows the branch of the Git remote from the local clone, read-only and marked as not being a Dataform branch; without a local clone it asks for a workspace.</li>
                </ul>
                <h2>0.2.24</h2>
                <ul>
                    <li>Unit test support for <code>type: "test"</code> actions: run tests from the gutter or a dedicated run configuration, results in the IDE test runner with an expected/actual diff, and a test preview in the SQLX split editor.</li>
                    <li>Inline error messages: Dataform compilation errors and BigQuery dry-run errors are placed on the exact tokens of the SQLX file, with "did you mean" hints and quick fixes; errors that cannot be placed are listed in an editor banner.</li>
                    <li>New SQLX File action gains a Test template.</li>
                    <li>Config block: the config schema now accepts the legacy <code>assertions</code> shorthands (<code>uniqueKey: "id"</code>, <code>nonNull: "col"</code>, <code>uniqueKeys: [["a", "b"]]</code>) and dependency targets written as objects, whose keys are now validated.</li>
                    <li>Config block: completing a property that already has a value moves the caret to that value instead of inserting a second one.</li>
                    <li>Config block: completion shows every shape a property accepts (e.g. <code>string[] | string</code>).</li>
                    <li>Lower memory and CPU usage for lineage, schema resolution, expression evaluation and BigQuery result grids.</li>
                    <li>Fixes: column dependencies, Go to Declaration and usages on Windows; column resolution for dataset-qualified tables, the multi-argument <code>ref()</code> forms and <code>WITH ${"$"}{include()}</code> templates; generated config typings wrongly typed arrays of several shapes.</li>
                </ul>
        """.trimIndent()
    }

    pluginVerification {
        ides {
            current()
        }
    }
}

val oauthClientId = providers.gradleProperty("dataformOAuthClientId").orElse("")
val oauthClientSecret = providers.gradleProperty("dataformOAuthClientSecret").orElse("")
val oauthResourcesDir = layout.buildDirectory.dir("generated/oauth-resources")

val generateOAuthClientResource by tasks.registering(WriteProperties::class) {
    destinationFile = oauthResourcesDir.map {
        it.file("io/github/rejeb/dataform/language/gcp/auth/oauth-client.properties")
    }
    property("clientId", oauthClientId.map(::base64))
    property("clientSecret", oauthClientSecret.map(::base64))
}

fun base64(value: String): String =
    Base64.getEncoder().encodeToString(value.toByteArray(Charsets.UTF_8))

tasks {
    withType<JavaCompile> {
        sourceCompatibility = "21"
        targetCompatibility = "21"
    }
    processResources {
        dependsOn(generateOAuthClientResource)
    }
    buildPlugin {
        doFirst {
            check(oauthClientId.get().isNotBlank() && oauthClientSecret.get().isNotBlank()) {
                "OAuth client missing: set dataformOAuthClientId and dataformOAuthClientSecret " +
                        "in ~/.gradle/gradle.properties or as ORG_GRADLE_PROJECT_ environment variables."
            }
        }
    }
}

sourceSets {
    main {
        java {
            srcDirs("src/main/gen", "src/main/java")
        }
        resources {
            srcDir(oauthResourcesDir)
        }
    }
    test {
        java {
            srcDirs("src/test/java")
        }
    }
}

tasks.test {
    useJUnitPlatform()
    systemProperty("idea.suppressed.plugins.id", "org.jetbrains.plugins.vue")
    systemProperty("idea.plugins.host", "http://localhost:0")
    systemProperty("dataform.plugin.oauth.clientId", "test-client-id.apps.googleusercontent.com")
    systemProperty("dataform.plugin.oauth.clientSecret", "test-client-secret")
}

