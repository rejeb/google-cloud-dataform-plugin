plugins {
    id("org.jetbrains.kotlin.jvm") version "2.4.20"
    id("org.jetbrains.intellij.platform") version "2.18.1"
}

group = "io.github.rejeb"
version = "0.2.23"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    implementation(platform("com.google.cloud:libraries-bom:26.85.0"))
    implementation("com.google.cloud:google-cloud-dataform")
    implementation("com.google.cloud:google-cloud-bigquery")
    testImplementation("org.mockito:mockito-core:5.23.0")
    testImplementation("org.mockito:mockito-junit-jupiter:5.23.0")

    testImplementation("org.junit.vintage:junit-vintage-engine:5.10.0")
    intellijPlatform {
        intellijIdea("2026.2")
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
        bundledPlugin("JavaScript")
        bundledPlugin("NodeJS")
        bundledPlugin("com.intellij.database")
        bundledPlugin("org.jetbrains.plugins.yaml")
        bundledPlugin("com.intellij.modules.json")
        bundledPlugin("org.jetbrains.plugins.terminal")
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
                <h3>New</h3>
                <ul>
                    <li>Unit test support for <code>type: "test"</code> actions: run tests from the gutter or a dedicated run configuration, results in the IDE test runner with an expected/actual diff, and a test preview in the SQLX split editor.</li>
                    <li>Inline error messages: Dataform compilation errors and BigQuery dry-run errors are placed on the exact tokens of the SQLX file, with "did you mean" hints and quick fixes; errors that cannot be placed are listed in an editor banner.</li>
                    <li>New SQLX File action gains a Test template.</li>
                </ul>
                <h3>Improvements</h3>
                <ul>
                    <li>Config block: the config schema now accepts the legacy <code>assertions</code> shorthands (<code>uniqueKey: "id"</code>, <code>nonNull: "col"</code>, <code>uniqueKeys: [["a", "b"]]</code>) and dependency targets written as objects, whose keys are now validated.</li>
                    <li>Config block: completing a property that already has a value moves the caret to that value instead of inserting a second one.</li>
                    <li>Config block: completion shows every shape a property accepts (e.g. <code>string[] | string</code>).</li>
                    <li>Lower memory and CPU usage for lineage, schema resolution, expression evaluation and BigQuery result grids.</li>
                </ul>
                <h3>Fixes</h3>
                <ul>
                    <li>Column dependencies, Go to Declaration and usages on Windows.</li>
                    <li>Column resolution for dataset-qualified tables, the multi-argument <code>ref()</code> forms and <code>WITH ${"$"}{include()}</code> templates.</li>
                    <li>Generated config typings wrongly typed arrays of several shapes.</li>
                </ul>
        """.trimIndent()
    }
}

tasks {
    withType<JavaCompile> {
        sourceCompatibility = "21"
        targetCompatibility = "21"
    }
}

sourceSets {
    main {
        java {
            srcDirs("src/main/gen", "src/main/java")
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
}

