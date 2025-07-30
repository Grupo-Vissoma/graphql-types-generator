import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EntityGeneratorPluginFunctionalTest {

    private lateinit var testProjectDir: File

    companion object {
        @BeforeAll
        @JvmStatic
        fun publishPluginToLocal() {
            val process = ProcessBuilder("./gradlew", "publishToMavenLocal")
                .directory(File(".")) // raiz do projeto do plugin
                .inheritIO()
                .start()
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                throw RuntimeException("Falha ao publicar plugin no mavenLocal, código de saída: $exitCode")
            }
        }

        @AfterAll
        @JvmStatic
        fun cleanup() {
            val mavenLocal = File(System.getProperty("user.home"), ".m2/repository/pt/grupovissoma/types-generator")
            if (mavenLocal.exists()) {
                mavenLocal.deleteRecursively()
            }

        }


    }


    @BeforeEach
    fun setup() {
        testProjectDir = createTempDirectory(prefix = "pluginFT")
            .toFile()
            .apply {
                deleteOnExit()
            }
        File(testProjectDir, "settings.gradle.kts").writeText("rootProject.name = \"demo\"")
        File(testProjectDir, "build.gradle.kts").writeText(
            """
            plugins {
                id("pt.grupovissoma.typesgenerator")
                kotlin("jvm") version "2.2.0"
                id("com.google.devtools.ksp") version "2.2.0-2.0.2"
            }
            repositories { 
                mavenLocal()
                mavenCentral() 
            }
            dependencies {
                implementation("org.jetbrains.kotlin:kotlin-stdlib")
                implementation("jakarta.persistence:jakarta.persistence-api:3.2.0")
                ksp("pt.grupovissoma:graphql-types-generator:+")
            }
            
            entityGen {
                filters.set(listOf("demo.*"))
                nullableUpdates.set(true)
                inputSuffix.set("Input")
                updateSuffix.set("Update")
            }
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