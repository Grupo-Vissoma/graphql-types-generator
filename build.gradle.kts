@file:OptIn(KspExperimental::class)

import com.google.devtools.ksp.KspExperimental
import org.gradle.kotlin.dsl.invoke
import java.net.URI

loadEnvFile()

plugins {
    kotlin("jvm") version "2.2.0"
    `java-gradle-plugin`
    id("com.gradle.plugin-publish") version "1.3.1"
    id("org.sonarqube") version "6.2.0.5505"
    id("jacoco")
    id("com.google.devtools.ksp") version "2.2.0-2.0.2"
    `maven-publish`
}

group = "pt.grupovissoma"
version = "1.0.1"

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
    implementation("com.google.devtools.ksp:symbol-processing-api:2.2.0-2.0.2")
    implementation("com.google.devtools.ksp:symbol-processing-gradle-plugin:2.2.0-2.0.2")
    testImplementation("com.google.devtools.ksp:symbol-processing-gradle-plugin:2.2.0-2.0.2")
    implementation("com.squareup:kotlinpoet:2.2.0")
    implementation("com.squareup:kotlinpoet-ksp:2.2.0")
    implementation(kotlin("reflect"))
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.2.0")
    implementation("jakarta.persistence:jakarta.persistence-api:3.2.0")


    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation(kotlin("test"))
    testImplementation(gradleTestKit())
    testImplementation("jakarta.persistence:jakarta.persistence-api:3.2.0")
//    testImplementation("dev.zacsweers.kctfork:core:0.8.0") // API principal
    testImplementation("dev.zacsweers.kctfork:ksp:0.8.0") // extensão KSP

    testImplementation("com.squareup:kotlinpoet-ksp:2.2.0")
    testImplementation("com.google.devtools.ksp:symbol-processing:2.2.0-2.0.2")
    testImplementation("com.google.devtools.ksp:symbol-processing-api:2.2.0-2.0.2")


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
    toolVersion = "0.8.10"
    reportsDirectory.set(layout.buildDirectory.dir("reports/jacoco"))
}


tasks.jacocoTestReport {
    dependsOn(tasks.withType<Test>())
    reports {
        html.required.set(true)
        xml.required.set(true)  // para Sonar
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
    dependsOn(tasks.jacocoTestReport)
    violationRules {
        rule {
            limit {
                counter = "INSTRUCTION"
                minimum = "0.80".toBigDecimal()      // 80 %
            }
        }
    }
}
tasks.check { dependsOn("jacocoVerify") }            // falha build se cobertura < 80 %


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
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
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
