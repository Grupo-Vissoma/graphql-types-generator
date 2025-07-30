import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EntityGeneratorPluginFunctionalTest {

    private lateinit var testProjectDir: File

    @BeforeEach
    fun setup() {
        testProjectDir = createTempDir(prefix = "pluginFT")
        File(testProjectDir, "settings.gradle.kts").writeText("rootProject.name = \"demo\"")
        File(testProjectDir, "build.gradle.kts").writeText(
            """
            plugins {
                id("pt.grupovissoma.typesgenerator")
                kotlin("jvm") version "2.2.0"
            }
            repositories { mavenCentral() }
            """.trimIndent()
        )
        // ficheiro de exemplo de entidade
        File(testProjectDir, "src/main/kotlin/Dummy.kt").apply {
            parentFile.mkdirs()
            writeText(
                """
                package demo
                import jakarta.persistence.*
                @Entity data class Dummy(@Id val id: Long, var name: String)
                """.trimIndent()
            )
        }
    }

    @Test
    fun `plugin aplica se sem erros e gera tipos`() {
        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .withArguments("build", "--stacktrace")
            .forwardOutput()
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":compileKotlin")?.outcome)
        val generated = File(testProjectDir, "build/generated/ksp/main/kotlin/demo/types/DummyInput.kt")
        assertTrue(generated.exists(), "Esperava DummyInput.kt gerado")
    }
}