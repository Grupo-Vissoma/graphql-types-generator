import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.configureKsp
import com.tschuchort.compiletesting.kspProcessorOptions
import com.tschuchort.compiletesting.kspSourcesDir
import com.tschuchort.compiletesting.kspWithCompilation
import com.tschuchort.compiletesting.symbolProcessorProviders
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import pt.grupovissoma.typesgenerator.EntityTypesSymbolProcessorProvider
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import java.io.File
import java.nio.file.Files

class EntityTypesSymbolProcessorTest {


    @OptIn(ExperimentalCompilerApi::class)
    @Test
    fun `gera Input e Update com campos correctos`() {
        val source = SourceFile.kotlin(
            "Author.kt", """
        package pt.grupovissoma.entity
        import jakarta.persistence.*
        @Entity 
        data class Author(
            @Id val id: Long,
            var first: String,
            var last:  String
        )
        """.trimIndent()
        )

        val compilation = KotlinCompilation().apply {
            sources = listOf(source)
            inheritClassPath = true
            configureKsp(useKsp2 = true) {
                symbolProcessorProviders += EntityTypesSymbolProcessorProvider()
                incremental = true
            }
            kspProcessorOptions = mutableMapOf(
                "inputSuffix" to "In",
                "updateSuffix" to "Up",
                "nullableUpdates" to "true",
                "filters" to "pt.grupovissoma.entity.*"
            )
            verbose = true

        }

        val result = compilation.compile()
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        val generated = compilation.kspSourcesDir
            .resolve("kotlin/pt/grupovissoma/entity/types/AuthorIn.kt")
        assertTrue(generated.exists(), "ficheiro gerado não encontrado")

        val code = generated.readText()
        assertTrue(code.contains("data class AuthorIn"), "classe Input ausente")
        assertTrue(code.contains("first: String"), "campo obrigatório ausente")
        assertTrue(code.contains("last: String"), "campo obrigatório ausente")
    }


}