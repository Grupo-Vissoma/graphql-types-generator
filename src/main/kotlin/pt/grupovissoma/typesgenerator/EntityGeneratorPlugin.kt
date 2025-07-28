package pt.grupovissoma.typesgenerator


import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.kotlin.dsl.*

class EntityGeneratorPlugin : Plugin<Project> {
    override fun apply(project: Project): Unit = project.run {
        val extension = extensions.create(
            "entityGen", EntityGenExtension::class.java, objects
        )

        val generateTask = tasks.register(
            "generateEntityTypes", GenerateEntityTypesTask::class
        ) {
            group = "code generation"
            description = "Generates *Input and *Update classes from @Entity"
            outputDir.set(layout.buildDirectory.dir("generated/types"))
            filters.set(extension.filters)
        }

        // add generated sources to the Kotlin sourceSet
        val sourceSets = extensions.getByName("sourceSets") as SourceSetContainer
        sourceSets.named("main") {
            java.srcDir(generateTask.map { it.outputDir })
        }

        // ensure generation runs before compilation
        tasks.named("compileKotlin") { dependsOn(generateTask) }
        tasks.named("compileJava")   { dependsOn(generateTask) }

    }
}