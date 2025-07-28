plugins {
    kotlin("jvm") version "2.1.21"
    `java-gradle-plugin`
    `kotlin-dsl`
    id("com.gradle.plugin-publish") version "1.3.1"
}

group = "pt.grupovissoma"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.github.classgraph:classgraph:4.8.181")
    implementation("com.squareup:kotlinpoet:2.2.0")
    implementation(kotlin("reflect"))

    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.gradle:test-kit")
    testImplementation(kotlin("test"))
}

gradlePlugin {

    plugins {
        create("typesGenerator") {
            id = "pt.grupovissoma.typesgenerator"
            displayName = "GraphQL Entity Input/Update Generator"
            description = "Generates *Input and *Update Kotlin types from JPA entities"
            implementationClass = "pt.grupovissoma.typesgenerator.TypesGeneratorPlugin"

        }
    }

}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}