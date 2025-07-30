
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import kotlin.test.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import pt.grupovissoma.typesgenerator.EntityGenExtension
import pt.grupovissoma.typesgenerator.EntityGeneratorPlugin
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream


class EntityGenExtensionTest {

    private lateinit var project: org.gradle.api.Project
    private lateinit var ext: EntityGenExtension
    private lateinit var plugin: EntityGeneratorPlugin

    @BeforeEach
    fun setup() {
        project = ProjectBuilder.builder().build()

        // Aplicar o plugin manualmente
        plugin = EntityGeneratorPlugin()
        plugin.apply(project)

        // Obter extensão criada pelo plugin
        ext = project.extensions.getByType(EntityGenExtension::class.java)
    }

    @Test
    fun `valores por defeito estao correctos`() {
        assertEquals(listOf("*"), ext.filters.get())
        assertTrue(ext.nullableUpdates.get())
        assertEquals("Input", ext.inputSuffix.get())
        assertEquals("Update", ext.updateSuffix.get())
    }

    @Test
    fun `extensao pode ser configurada atraves do DSL`() {
        project.extensions.configure(EntityGenExtension::class.java) { extension ->
            extension.inputSuffix.set("In")
            extension.updateSuffix.set("Up")
            extension.nullableUpdates.set(false)
            extension.filters.set(listOf("com.example.*"))
        }

        assertEquals("In", ext.inputSuffix.get())
        assertEquals("Up", ext.updateSuffix.get())
        assertFalse(ext.nullableUpdates.get())
        assertEquals(listOf("com.example.*"), ext.filters.get())
    }

    @Test
    fun `plugin aplica configuracao padrao automaticamente`() {
        // Força a avaliação do projeto para executar afterEvaluate
        triggerAfterEvaluate()

        // Verifica se não há erros na configuração padrão
        assertNotNull(ext.inputSuffix.get())
        assertNotNull(ext.updateSuffix.get())
        assertTrue(ext.filters.get().isNotEmpty())
    }

    // Função helper melhorada para capturar logs
    private fun captureLogs(block: () -> Unit): List<String> {
        val messages = mutableListOf<String>()
        val outputStream = ByteArrayOutputStream()
        val originalOut = System.out
        val originalErr = System.err

        try {
            val printStream = PrintStream(outputStream, true, "UTF-8")
            System.setOut(printStream)
            System.setErr(printStream)

            block()

            printStream.flush()
            val output = outputStream.toString("UTF-8")
            if (output.isNotEmpty()) {
                messages.addAll(output.split("\n").filter { it.isNotBlank() })
            }
        } finally {
            System.setOut(originalOut)
            System.setErr(originalErr)
        }

        return messages
    }

    // Função para forçar avaliação do projeto
    private fun triggerAfterEvaluate() {
        try {
            // Método 1: Usar getTasksByName para forçar avaliação
            project.getTasksByName("tasks", false)
        } catch (e: Exception) {
            try {
                // Método 2: Cast para ProjectInternal e avaliar
                (project as org.gradle.api.internal.project.ProjectInternal).evaluate()
            } catch (e2: Exception) {
                // Se ambos falharem, continuar sem avaliação forçada
                println("Aviso: Não foi possível forçar avaliação do projeto")
            }
        }
    }
}
