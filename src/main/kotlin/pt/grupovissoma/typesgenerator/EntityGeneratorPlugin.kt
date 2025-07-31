package pt.grupovissoma.typesgenerator

import com.google.devtools.ksp.gradle.KspExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task

class EntityGeneratorPlugin : Plugin<Project> {

    override fun apply(project: Project) = with(project) {
        val extension = extensions.create("entityGen", EntityGenExtension::class.java)

        configureKspPlugin()

        afterEvaluate {
            configureKspIntegration(extension)
        }

        configureDebugLogging()

        logger.info("EntityGeneratorPlugin aplicado ao projeto: ${project.name}")
    }

    /**
     * Aplica o plugin KSP de forma segura, verificando se já está presente
     */
    private fun Project.configureKspPlugin() {
        try {
            if (!pluginManager.hasPlugin("com.google.devtools.ksp")) {
                pluginManager.apply("com.google.devtools.ksp")
                logger.debug("Plugin KSP aplicado automaticamente")
            }
        } catch (e: Exception) {
            logger.warn("Não foi possível aplicar o plugin KSP automaticamente: ${e.message}")
            logger.warn("  Por favor, aplica manualmente 'com.google.devtools.ksp' no teu build.gradle")
        }
    }

    /**
     * Configura a integração com o KSP usando múltiplas estratégias para máxima compatibilidade
     */
    private fun Project.configureKspIntegration(extension: EntityGenExtension) {
        var configured: Boolean

        // Estratégia 1: Configuração direta através da extensão KSP (preferida)
        configured = tryDirectKspConfiguration(extension)

        // Estratégia 2: Configuração por reflexão como fallback
        if (!configured) {
            configured = tryReflectionKspConfiguration(extension)
        }

        if (configured) {
            logConfigurationSuccess(extension)
        } else {
            logger.error("Falha na configuração do KSP - verifica se o plugin está corretamente aplicado")
        }
    }

    /**
     * Tenta configurar o KSP através da extensão oficial (método preferido)
     */
    private fun Project.tryDirectKspConfiguration(extension: EntityGenExtension): Boolean {
        return try {
            val ksp = extensions.findByType(KspExtension::class.java)

            if (ksp != null) {
                // Configurar argumentos do processador
                ksp.arg("inputSuffix", extension.inputSuffix.get())
                ksp.arg("updateSuffix", extension.updateSuffix.get())
                ksp.arg("nullableUpdates", extension.nullableUpdates.get().toString())
                ksp.arg("filters", extension.filters.get().joinToString(","))

                logger.debug("KSP configurado através da extensão oficial")
                true
            } else {
                logger.debug("Extensão KSP não encontrada")
                false
            }
        } catch (e: Exception) {
            logger.debug("Configuração direta do KSP falhou: ${e.message}")
            false
        }
    }

    /**
     * Configuração alternativa usando reflexão para máxima compatibilidade
     */
    private fun Project.tryReflectionKspConfiguration(extension: EntityGenExtension): Boolean {
        return try {
            var tasksConfigured = 0

            tasks.matching { task ->
                task.javaClass.name.contains("KspTask")
            }.configureEach { kspTask ->
                if (configureKspTaskByReflection(kspTask, extension)) {
                    tasksConfigured++
                    logger.debug("Tarefa KSP configurada por reflexão: ${kspTask.name}")
                }
            }

            if (tasksConfigured > 0) {
                logger.debug("${tasksConfigured} tarefa(s) KSP configurada(s) por reflexão")
                true
            } else {
                logger.debug("Nenhuma tarefa KSP encontrada para configuração")
                false
            }
        } catch (e: Exception) {
            logger.debug("Configuração por reflexão falhou: ${e.message}")
            false
        }
    }

    /**
     * Configura uma tarefa KSP específica usando reflexão
     */
    private fun Project.configureKspTaskByReflection(task: Task, extension: EntityGenExtension): Boolean {
        return try {
            // Tentar obter a configuração KSP da tarefa
            val kspConfigMethod = task.javaClass.methods.find { it.name == "getKspConfig" }
            val kspConfig = kspConfigMethod?.invoke(task)

            if (kspConfig != null) {
                val argMethod = kspConfig.javaClass.methods.find { it.name == "arg" }

                if (argMethod != null) {
                    // Aplicar todos os argumentos de forma segura
                    try {
                        argMethod.invoke(kspConfig, "inputSuffix", extension.inputSuffix.get())
                        argMethod.invoke(kspConfig, "updateSuffix", extension.updateSuffix.get())
                        argMethod.invoke(kspConfig, "nullableUpdates", extension.nullableUpdates.get().toString())
                        argMethod.invoke(kspConfig, "filters", extension.filters.get().joinToString(","))
                        true
                    } catch (e: Exception) {
                        logger.debug("Erro ao aplicar argumentos KSP: ${e.message}")
                        false
                    }
                } else {
                    logger.debug("Método 'arg' não encontrado na configuração KSP")
                    false
                }
            } else {
                logger.debug("Configuração KSP não encontrada na tarefa ${task.name}")
                false
            }
        } catch (e: Exception) {
            logger.debug("Erro ao configurar tarefa KSP ${task.name}: ${e.message}")
            false
        }
    }

    /**
     * Configura logging de debug e funcionalidades avançadas
     */
    private fun Project.configureDebugLogging() {
        // Ativar logging incremental se solicitado
        if (providers.gradleProperty("entityGen.debugKsp").isPresent) {
            afterEvaluate {
                tasks.matching { it.javaClass.name.contains("KspTask") }.configureEach { task ->
                    enableIncrementalLogging(task)
                }
            }
        }

        // Configurar validação de configurações se em modo debug
        if (providers.gradleProperty("entityGen.validateConfig").isPresent) {
            afterEvaluate {
                validateConfiguration()
            }
        }
    }

    /**
     * Ativa logging incremental numa tarefa KSP específica
     */
    private fun Project.enableIncrementalLogging(task: Task) {
        try {
            val enableIncrementalMethod = task.javaClass.methods.find { it.name == "setIncremental" }
            enableIncrementalMethod?.invoke(task, true)

            logger.info("KSP incremental logging ativado para ${task.name}")
        } catch (e: Exception) {
            logger.debug("Não foi possível ativar logging incremental para ${task.name}: ${e.message}")
        }
    }

    /**
     * Valida a configuração do plugin e reporta possíveis problemas
     */
    private fun Project.validateConfiguration() {
        val extension = extensions.getByType(EntityGenExtension::class.java)

        try {
            // Verificar se os sufixos são válidos
            val inputSuffix = extension.inputSuffix.get()
            val updateSuffix = extension.updateSuffix.get()

            if (inputSuffix == updateSuffix) {
                logger.warn("InputSuffix e UpdateSuffix são iguais ('$inputSuffix') - isto pode causar conflitos")
            }

            if (inputSuffix.isBlank() || updateSuffix.isBlank()) {
                logger.warn("Sufixos em branco podem causar problemas na geração de código")
            }

            // Verificar filtros
            val filters = extension.filters.get()
            if (filters.isEmpty()) {
                logger.info("Nenhum filtro configurado - todos os tipos serão processados")
            } else {
                logger.info("Filtros ativos: ${filters.joinToString(", ")}")
            }

            logger.debug("Validação de configuração concluída")

        } catch (e: Exception) {
            logger.warn("Erro durante validação da configuração: ${e.message}")
        }
    }

    /**
     * Regista o sucesso da configuração com detalhes para debug
     */
    private fun Project.logConfigurationSuccess(extension: EntityGenExtension) {
        if (logger.isInfoEnabled) {
            logger.lifecycle(
                """
                
                > GraphQL Types Generator – Configuração ativa:
                    inputSuffix     = '${extension.inputSuffix.get()}'
                    updateSuffix    = '${extension.updateSuffix.get()}'
                    nullableUpdates = ${extension.nullableUpdates.get()}
                    filters         = [${extension.filters.get().joinToString(", ") { "'$it'" }}]
                    projeto         = ${project.path}
                
                """.trimIndent()
            )
        }
    }
}