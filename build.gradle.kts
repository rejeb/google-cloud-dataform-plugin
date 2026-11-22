import java.util.Base64

plugins {
    id("org.jetbrains.kotlin.jvm") version "2.4.20"
    id("org.jetbrains.intellij.platform") version "2.19.0"
}

group = "io.github.rejeb"
version = "1.1.1"

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
    implementation("com.google.cloud:google-cloud-datalineage")
    testImplementation("org.mockito:mockito-core:5.23.0")
    testImplementation("org.mockito:mockito-junit-jupiter:5.23.0")

    testImplementation("org.junit.vintage:junit-vintage-engine:5.10.0")
    intellijPlatform {
        intellijIdea("2026.3")
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
        pluginVerifier()
        bundledPlugin("JavaScript")
        bundledPlugin("NodeJS")
        bundledPlugin("com.intellij.database")
        bundledPlugin("org.jetbrains.plugins.yaml")
        bundledPlugin("com.intellij.modules.json")
        bundledPlugin("org.jetbrains.plugins.terminal")
        bundledPlugin("com.intellij.platform.vcs")
        bundledModule("intellij.platform.externalProcessAuthHelper")
        bundledModule("intellij.platform.buildView")
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

        description = descriptorHtml("description.html")
        changeNotes = descriptorHtml("change-notes.html")
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

fun descriptorHtml(fileName: String): Provider<String> =
    providers.fileContents(layout.projectDirectory.file("src/main/descriptor/$fileName")).asText
        .map { it.replaceFirst(Regex("^\\s*<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "").trim() }

tasks {
    withType<JavaCompile> {
        sourceCompatibility = "25"
        targetCompatibility = "25"
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

val runIde_2026_2 by intellijPlatformTesting.runIde.registering {
    version = "2026.2"
}


