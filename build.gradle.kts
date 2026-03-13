import org.gradle.kotlin.dsl.implementation

plugins {
    id("org.jetbrains.kotlin.jvm") version "2.3.10"
    id("org.jetbrains.intellij.platform") version "2.12.0"
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.10"
}

group = "io.github.rejeb"
version = "0.2.8"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    implementation(platform("com.google.cloud:libraries-bom:26.77.0"))
    implementation("com.google.cloud:google-cloud-dataform")
    implementation("com.google.cloud:google-cloud-bigquery")
    testImplementation("junit:junit:4.13.2")

    testImplementation("org.junit.vintage:junit-vintage-engine:5.10.0")
    intellijPlatform {
        intellijIdea("2025.3.3")
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
        bundledPlugin("com.intellij.java")
        bundledPlugin("JavaScript")
        bundledPlugin("NodeJS")
        bundledPlugin("com.intellij.database")
        bundledPlugin("org.jetbrains.plugins.yaml")
        bundledPlugin("com.intellij.modules.json")

        composeUI()
    }

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "252"
        }

        changeNotes = """
                <ul>
                    <li>Add autocomplete on bigQuery tables created with Dataform code.</li>
                    <li>Add table schema view tab.</li>
                    <li>Add indexes caches.</li>
                    <li>Fixing issues.</li>
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

sourceSets{
    main{
        java{
            srcDirs("src/main/gen","src/main/java")
        }
    }
    test{
        java{
            srcDirs("src/test/java")
        }
    }
}

tasks.test {
    useJUnitPlatform()
}

