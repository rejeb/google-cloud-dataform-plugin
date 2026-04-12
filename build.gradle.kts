plugins {
    id("org.jetbrains.kotlin.jvm") version "2.3.10"
    id("org.jetbrains.intellij.platform") version "2.12.0"
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.10"
}

group = "io.github.rejeb"
version = "0.2.12"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    implementation(platform("com.google.cloud:libraries-bom:26.79.0"))
    implementation("com.google.cloud:google-cloud-dataform")
    implementation("com.google.cloud:google-cloud-bigquery")
    testImplementation("org.mockito:mockito-core:5.11.0")
    testImplementation("org.mockito:mockito-junit-jupiter:5.11.0")

    testImplementation("org.junit.vintage:junit-vintage-engine:5.10.0")
    intellijPlatform {
        intellijIdea("2026.1")
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
        bundledPlugin("JavaScript")
        bundledPlugin("NodeJS")
        bundledPlugin("com.intellij.database")
        bundledPlugin("org.jetbrains.plugins.yaml")
        bundledPlugin("com.intellij.modules.json")
        bundledPlugin("org.jetbrains.plugins.terminal")
        composeUI()
    }

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "261"
        }

        changeNotes = """
                <ul>
                    <li>Fix issues.</li>
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

