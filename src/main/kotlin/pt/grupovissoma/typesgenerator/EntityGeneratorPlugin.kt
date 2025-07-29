package pt.grupovissoma.typesgenerator

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task

class EntityGeneratorPlugin : Plugin<Project> {

    override fun apply(project: Project) = with(project) {

        val extension = extensions.create("entityGen", EntityGenExtension::class.java)


        plugins.withId("com.google.devtools.ksp") {
            configureKspTasks(extension)
        }

        logger.info("✓ EntityGeneratorPlugin aplicado ao projeto: ${project.name}")
    }

    private fun Project.configureKspTasks(extension: EntityGenExtension) {

        afterEvaluate {
            tasks.matching { task ->
                task.javaClass.name.contains("KspTask")
            }.configureEach { kspTask ->

                configureKspTask(kspTask, extension)

                logger.debug("✓ Configurada tarefa KSP: ${kspTask.name}")
            }
        }

        if (providers.gradleProperty("entityGen.debugKsp").isPresent) {
            configureIncrementalLogging()
        }
    }

    private fun Project.configureKspTask(task: Task, extension: EntityGenExtension) {
        try {
            // Usar reflexão para configurar opções KSP
            val kspConfigMethod = task.javaClass.methods.find { it.name == "getKspConfig" }
            val kspConfig = kspConfigMethod?.invoke(task)

            if (kspConfig != null) {
                val argMethod = kspConfig.javaClass.methods.find { it.name == "arg" }

                // Configurar argumentos do processador
                afterEvaluate {
                    argMethod?.invoke(kspConfig, "inputSuffix", extension.inputSuffix.get())
                    argMethod?.invoke(kspConfig, "updateSuffix", extension.updateSuffix.get())
                    argMethod?.invoke(kspConfig, "nullableUpdates", extension.nullableUpdates.get().toString())

                    logger.debug("✓ Argumentos KSP configurados para ${task.name}")
                }
            }
        } catch (e: Exception) {
            logger.debug("⚠ Não foi possível configurar automaticamente a tarefa KSP ${task.name}: ${e.message}")
            // Falha silenciosa - plugin continua a funcionar
        }
    }

    private fun Project.configureIncrementalLogging() {
        tasks.matching { it.javaClass.name.contains("KspTask") }.configureEach { task ->
            try {
                val enableIncrementalMethod = task.javaClass.methods.find { it.name == "setIncremental" }
                enableIncrementalMethod?.invoke(task, true)

                logger.info("✓ KSP incremental logging ativado para ${task.name}")
            } catch (e: Exception) {
                logger.debug("⚠ Não foi possível ativar logging incremental: ${e.message}")
            }
        }
    }
}
