package pt.grupovissoma.typesgenerator

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.*
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension


class EntityGeneratorPlugin : Plugin<Project> {
    override fun apply(project: Project): Unit = project.run {
        // Aplicar o plugin KSP

        val extension = extensions.create("entityGen", EntityGenExtension::class.java, objects)

        // Log para debug
        logger.info("EntityGeneratorPlugin aplicado ao projeto: ${project.name}")

    }
}
