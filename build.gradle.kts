@file:OptIn(KspExperimental::class)

import com.google.devtools.ksp.KspExperimental
import org.gradle.kotlin.dsl.invoke
import java.net.URI

loadEnvFile()

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.java.gradle.plugin)
    alias(libs.plugins.gradle.plugin.publish)
    alias(libs.plugins.sonarqube)
    alias(libs.plugins.jacoco)
    alias(libs.plugins.ksp)
    alias(libs.plugins.maven.publish)
}

group = "pt.grupovissoma"
version = "1.0.0"

repositories {
    mavenCentral()
    mavenLocal {
        content {
            includeGroup("pt.grupovissoma")
        }
    }
}

sonar {
    properties {
        property("sonar.host.url", getEnvOrProperty("SONAR_HOST_URL"))
        property("sonar.projectKey", getEnvOrProperty("SONAR_PROJECT_KEY"))
        property("sonar.projectName", "graphql-types-generator")
        property("sonar.token", getEnvOrProperty("SONAR_TOKEN"))
    }
}

dependencies {
    implementation(libs.ksp.symbol.processing.api)
    implementation(libs.ksp.symbol.processing.gradle.plugin)
    implementation(libs.bundles.kotlinpoet)
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.jakarta.persistence.api)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.bundles.testing)
    testImplementation(gradleTestKit())
    testImplementation(libs.jakarta.persistence.api)
    testImplementation(libs.kctfork.ksp)
    testImplementation(libs.ksp.symbol.processing.gradle.plugin)
}

gradlePlugin {
    plugins {
        create("typesGenerator") {
            id = "pt.grupovissoma.typesgenerator"
            displayName = "GraphQL Entity Input/Update Generator"
            description = "Generates *Input and *Update Kotlin types from JPA entities"
            implementationClass = "pt.grupovissoma.typesgenerator.EntityGeneratorPlugin"
        }
    }
}

tasks.test {
    useJUnitPlatform()
}

/* ---------- Jacoco ---------- */
jacoco {
    toolVersion = libs.versions.jacoco.get()
    reportsDirectory.set(layout.buildDirectory.dir("reports/jacoco"))
}

tasks.jacocoTestReport {
    dependsOn(tasks.withType<Test>())
    reports {
        html.required.set(true)
        xml.required.set(true) // para Sonar
        csv.required.set(false)
    }

    classDirectories.setFrom(
        files(classDirectories.files.map {
            fileTree(it) {
                exclude(
                    "**/kt/generated/**",
                    "**/*Test*",
                    "**/pt/grupovissoma/typesgenerator/internal/**"
                )
            }
        })
    )
}

tasks.register<JacocoCoverageVerification>("jacocoVerify") {
    group = "verification"
    description = "Verifies code coverage using Jacoco"
    dependsOn(tasks.jacocoTestReport)
    violationRules {
        rule {
            limit {
                counter = "INSTRUCTION"
                minimum = "0.80".toBigDecimal() // 80%
            }
        }
    }
}

tasks.check {
    dependsOn("jacocoVerify")
}

publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            url = URI.create("https://maven.pkg.github.com/grupo-vissoma/graphql-types-generator")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
    if (System.getenv("CI") != "true") {
        publications {
            create<MavenPublication>("maven") {
                from(components["java"])
            }
        }
    }
}

kotlin {
    jvmToolchain(21)
}

fun loadEnvFile(path: String = ".env") {
    val file = File(path)
    if (!file.exists()) return
    file.forEachLine {
        val (key, value) = it.split("=", limit = 2)
        System.setProperty(key.trim(), value.trim())
    }
}

fun getEnvOrProperty(name: String): String =
    System.getenv(name) ?: System.getProperty(name) ?: error("Variable '$name' is missing")
