import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.configureKsp
import com.tschuchort.compiletesting.kspProcessorOptions
import com.tschuchort.compiletesting.kspSourcesDir
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test
import pt.grupovissoma.typesgenerator.EntityTypesSymbolProcessorProvider
import kotlin.test.*

class EntityTypesSymbolProcessorComprehensiveTest {

    @OptIn(ExperimentalCompilerApi::class)
    @Test
    fun `processes entity with all property types correctly`() {
        val source = SourceFile.kotlin(
            "ComplexEntity.kt", """
        package pt.grupovissoma.entity
        import jakarta.persistence.*
        
        @Entity 
        data class ComplexEntity(
            @Id val id: Long,
            var name: String,
            var age: Int,
            var active: Boolean,
            var score: Double,
            var tags: List<String>,
            var metadata: Map<String, Any>,
            @Version var version: Long
        )
        """.trimIndent()
        )

        val compilation = KotlinCompilation().apply {
            sources = listOf(source)
            inheritClassPath = true
            configureKsp(useKsp2 = true) {
                symbolProcessorProviders += EntityTypesSymbolProcessorProvider()
            }
            kspProcessorOptions = mutableMapOf(
                "inputSuffix" to "Input",
                "updateSuffix" to "Update",
                "nullableUpdates" to "true",
                "filters" to "pt.grupovissoma.entity.*"
            )
        }

        val result = compilation.compile()
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        // Verify Input type
        val inputFile = compilation.kspSourcesDir
            .resolve("kotlin/pt/grupovissoma/entity/types/ComplexEntityInput.kt")
        assertTrue(inputFile.exists(), "Input file should be generated")
        
        val inputCode = inputFile.readText()
        assertTrue(inputCode.contains("data class ComplexEntityInput"), "Input class should be generated")
        assertTrue(inputCode.contains("name: String"), "String property should be included")
        assertTrue(inputCode.contains("age: Int"), "Int property should be included")
        assertTrue(inputCode.contains("active: Boolean"), "Boolean property should be included")
        assertTrue(inputCode.contains("score: Double"), "Double property should be included")
        assertTrue(inputCode.contains("tags: List<String>"), "List property should be included")
        assertTrue(inputCode.contains("metadata: Map<String, Any>"), "Map property should be included")
        
        // Verify Update type
        val updateFile = compilation.kspSourcesDir
            .resolve("kotlin/pt/grupovissoma/entity/types/ComplexEntityUpdate.kt")
        assertTrue(updateFile.exists(), "Update file should be generated")
        
        val updateCode = updateFile.readText()
        assertTrue(updateCode.contains("data class ComplexEntityUpdate"), "Update class should be generated")
        assertTrue(updateCode.contains("name: String?"), "String property should be nullable in Update")
        assertTrue(updateCode.contains("age: Int?"), "Int property should be nullable in Update")
        assertTrue(updateCode.contains("active: Boolean?"), "Boolean property should be nullable in Update")
        assertTrue(updateCode.contains("score: Double?"), "Double property should be nullable in Update")
        assertTrue(updateCode.contains("tags: List<String>?"), "List property should be nullable in Update")
        assertTrue(updateCode.contains("metadata: Map<String, Any>?"), "Map property should be nullable in Update")
        
        // Verify that @Id and @Version fields are excluded
        assertFalse(inputCode.contains("id: Long"), "@Id field should be excluded")
        assertFalse(inputCode.contains("version: Long"), "@Version field should be excluded")
        assertFalse(updateCode.contains("id: Long"), "@Id field should be excluded")
        assertFalse(updateCode.contains("version: Long"), "@Version field should be excluded")
    }

    @OptIn(ExperimentalCompilerApi::class)
    @Test
    fun `processes entity with nullable updates disabled`() {
        val source = SourceFile.kotlin(
            "User.kt", """
        package pt.grupovissoma.entity
        import jakarta.persistence.*
        
        @Entity 
        data class User(
            @Id val id: Long,
            var username: String,
            var email: String
        )
        """.trimIndent()
        )

        val compilation = KotlinCompilation().apply {
            sources = listOf(source)
            inheritClassPath = true
            configureKsp(useKsp2 = true) {
                symbolProcessorProviders += EntityTypesSymbolProcessorProvider()
            }
            kspProcessorOptions = mutableMapOf(
                "inputSuffix" to "Input",
                "updateSuffix" to "Update",
                "nullableUpdates" to "false",
                "filters" to "pt.grupovissoma.entity.*"
            )
        }

        val result = compilation.compile()
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        val updateFile = compilation.kspSourcesDir
            .resolve("kotlin/pt/grupovissoma/entity/types/UserUpdate.kt")
        val updateCode = updateFile.readText()
        
        // Properties should not be nullable when nullableUpdates is false
        assertTrue(updateCode.contains("username: String"), "String property should not be nullable")
        assertTrue(updateCode.contains("email: String"), "String property should not be nullable")
        assertFalse(updateCode.contains("username: String?"), "String property should not be nullable")
        assertFalse(updateCode.contains("email: String?"), "String property should not be nullable")
    }

    @OptIn(ExperimentalCompilerApi::class)
    @Test
    fun `handles entity with no editable properties gracefully`() {
        val source = SourceFile.kotlin(
            "ReadOnlyEntity.kt", """
        package pt.grupovissoma.entity
        import jakarta.persistence.*
        
        @Entity 
        data class ReadOnlyEntity(
            @Id val id: Long,
            val name: String,  // val = immutable
            @Version val version: Long
        )
        """.trimIndent()
        )

        val compilation = KotlinCompilation().apply {
            sources = listOf(source)
            inheritClassPath = true
            configureKsp(useKsp2 = true) {
                symbolProcessorProviders += EntityTypesSymbolProcessorProvider()
            }
            kspProcessorOptions = mutableMapOf(
                "inputSuffix" to "Input",
                "updateSuffix" to "Update",
                "nullableUpdates" to "true",
                "filters" to "pt.grupovissoma.entity.*"
            )
        }

        val result = compilation.compile()
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        // No files should be generated since there are no editable properties
        val inputFile = compilation.kspSourcesDir
            .resolve("kotlin/pt/grupovissoma/entity/types/ReadOnlyEntityInput.kt")
        val updateFile = compilation.kspSourcesDir
            .resolve("kotlin/pt/grupovissoma/entity/types/ReadOnlyEntityUpdate.kt")
        
        assertFalse(inputFile.exists(), "No Input file should be generated for read-only entity")
        assertFalse(updateFile.exists(), "No Update file should be generated for read-only entity")
    }

    @OptIn(ExperimentalCompilerApi::class)
    @Test
    fun `handles entity with only id and version properties`() {
        val source = SourceFile.kotlin(
            "IdOnlyEntity.kt", """
        package pt.grupovissoma.entity
        import jakarta.persistence.*
        
        @Entity 
        data class IdOnlyEntity(
            @Id val id: Long,
            @Version var version: Long
        )
        """.trimIndent()
        )

        val compilation = KotlinCompilation().apply {
            sources = listOf(source)
            inheritClassPath = true
            configureKsp(useKsp2 = true) {
                symbolProcessorProviders += EntityTypesSymbolProcessorProvider()
            }
            kspProcessorOptions = mutableMapOf(
                "inputSuffix" to "Input",
                "updateSuffix" to "Update",
                "nullableUpdates" to "true",
                "filters" to "pt.grupovissoma.entity.*"
            )
        }

        val result = compilation.compile()
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        // No files should be generated since only @Id and @Version properties exist
        val inputFile = compilation.kspSourcesDir
            .resolve("kotlin/pt/grupovissoma/entity/types/IdOnlyEntityInput.kt")
        val updateFile = compilation.kspSourcesDir
            .resolve("kotlin/pt/grupovissoma/entity/types/IdOnlyEntityUpdate.kt")
        
        assertFalse(inputFile.exists(), "No Input file should be generated for ID-only entity")
        assertFalse(updateFile.exists(), "No Update file should be generated for ID-only entity")
    }

    @OptIn(ExperimentalCompilerApi::class)
    @Test
    fun `processes multiple entities in same package`() {
        val source1 = SourceFile.kotlin(
            "Product.kt", """
        package pt.grupovissoma.entity
        import jakarta.persistence.*
        
        @Entity 
        data class Product(
            @Id val id: Long,
            var name: String,
            var price: Double
        )
        """.trimIndent()
        )
        
        val source2 = SourceFile.kotlin(
            "Category.kt", """
        package pt.grupovissoma.entity
        import jakarta.persistence.*
        
        @Entity 
        data class Category(
            @Id val id: Long,
            var name: String,
            var description: String
        )
        """.trimIndent()
        )

        val compilation = KotlinCompilation().apply {
            sources = listOf(source1, source2)
            inheritClassPath = true
            configureKsp(useKsp2 = true) {
                symbolProcessorProviders += EntityTypesSymbolProcessorProvider()
            }
            kspProcessorOptions = mutableMapOf(
                "inputSuffix" to "Input",
                "updateSuffix" to "Update",
                "nullableUpdates" to "true",
                "filters" to "pt.grupovissoma.entity.*"
            )
        }

        val result = compilation.compile()
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        // Verify both entities are processed
        val productInputFile = compilation.kspSourcesDir
            .resolve("kotlin/pt/grupovissoma/entity/types/ProductInput.kt")
        val productUpdateFile = compilation.kspSourcesDir
            .resolve("kotlin/pt/grupovissoma/entity/types/ProductUpdate.kt")
        val categoryInputFile = compilation.kspSourcesDir
            .resolve("kotlin/pt/grupovissoma/entity/types/CategoryInput.kt")
        val categoryUpdateFile = compilation.kspSourcesDir
            .resolve("kotlin/pt/grupovissoma/entity/types/CategoryUpdate.kt")
        
        assertTrue(productInputFile.exists(), "ProductInput should be generated")
        assertTrue(productUpdateFile.exists(), "ProductUpdate should be generated")
        assertTrue(categoryInputFile.exists(), "CategoryInput should be generated")
        assertTrue(categoryUpdateFile.exists(), "CategoryUpdate should be generated")
        
        // Verify content
        val productInputCode = productInputFile.readText()
        val categoryInputCode = categoryInputFile.readText()
        
        assertTrue(productInputCode.contains("data class ProductInput"), "ProductInput class should be generated")
        assertTrue(categoryInputCode.contains("data class CategoryInput"), "CategoryInput class should be generated")
        assertTrue(productInputCode.contains("name: String"), "Product name property should be included")
        assertTrue(productInputCode.contains("price: Double"), "Product price property should be included")
        assertTrue(categoryInputCode.contains("name: String"), "Category name property should be included")
        assertTrue(categoryInputCode.contains("description: String"), "Category description property should be included")
    }

    @OptIn(ExperimentalCompilerApi::class)
    @Test
    fun `handles entity with custom annotations correctly`() {
        val source = SourceFile.kotlin(
            "AnnotatedEntity.kt", """
        package pt.grupovissoma.entity
        import jakarta.persistence.*
        
        @Entity 
        data class AnnotatedEntity(
            @Id val id: Long,
            @Column(name = "user_name") var name: String,
            @Transient var tempField: String,
            @Version var version: Long
        )
        """.trimIndent()
        )

        val compilation = KotlinCompilation().apply {
            sources = listOf(source)
            inheritClassPath = true
            configureKsp(useKsp2 = true) {
                symbolProcessorProviders += EntityTypesSymbolProcessorProvider()
            }
            kspProcessorOptions = mutableMapOf(
                "inputSuffix" to "Input",
                "updateSuffix" to "Update",
                "nullableUpdates" to "true",
                "filters" to "pt.grupovissoma.entity.*"
            )
        }

        val result = compilation.compile()
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        val inputFile = compilation.kspSourcesDir
            .resolve("kotlin/pt/grupovissoma/entity/types/AnnotatedEntityInput.kt")
        val inputCode = inputFile.readText()
        
        // @Column and @Transient properties should be included (they're not @Id or @Version)
        assertTrue(inputCode.contains("name: String"), "@Column property should be included")
        assertTrue(inputCode.contains("tempField: String"), "@Transient property should be included")
        
        // @Id and @Version should be excluded
        assertFalse(inputCode.contains("id: Long"), "@Id field should be excluded")
        assertFalse(inputCode.contains("version: Long"), "@Version field should be excluded")
    }

    @OptIn(ExperimentalCompilerApi::class)
    @Test
    fun `generates correct package structure`() {
        val source = SourceFile.kotlin(
            "DeepEntity.kt", """
        package com.example.deep.nested.entity
        import jakarta.persistence.*
        
        @Entity 
        data class DeepEntity(
            @Id val id: Long,
            var name: String
        )
        """.trimIndent()
        )

        val compilation = KotlinCompilation().apply {
            sources = listOf(source)
            inheritClassPath = true
            configureKsp(useKsp2 = true) {
                symbolProcessorProviders += EntityTypesSymbolProcessorProvider()
            }
            kspProcessorOptions = mutableMapOf(
                "inputSuffix" to "Input",
                "updateSuffix" to "Update",
                "nullableUpdates" to "true",
                "filters" to "com.example.deep.nested.entity.*"
            )
        }

        val result = compilation.compile()
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        val inputFile = compilation.kspSourcesDir
            .resolve("kotlin/com/example/deep/nested/entity/types/DeepEntityInput.kt")
        val updateFile = compilation.kspSourcesDir
            .resolve("kotlin/com/example/deep/nested/entity/types/DeepEntityUpdate.kt")
        
        assertTrue(inputFile.exists(), "Input file should be generated in correct package")
        assertTrue(updateFile.exists(), "Update file should be generated in correct package")
        
        val inputCode = inputFile.readText()
        assertTrue(inputCode.contains("package com.example.deep.nested.entity.types"), 
                  "Package should be correctly generated")
    }

    @OptIn(ExperimentalCompilerApi::class)
    @Test
    fun `handles empty entity list gracefully`() {
        val source = SourceFile.kotlin(
            "NonEntity.kt", """
        package pt.grupovissoma.entity
        
        data class NonEntity(
            val id: Long,
            var name: String
        )
        """.trimIndent()
        )

        val compilation = KotlinCompilation().apply {
            sources = listOf(source)
            inheritClassPath = true
            configureKsp(useKsp2 = true) {
                symbolProcessorProviders += EntityTypesSymbolProcessorProvider()
            }
            kspProcessorOptions = mutableMapOf(
                "inputSuffix" to "Input",
                "updateSuffix" to "Update",
                "nullableUpdates" to "true",
                "filters" to "pt.grupovissoma.entity.*"
            )
        }

        val result = compilation.compile()
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        // No files should be generated since there are no @Entity classes
        val inputFile = compilation.kspSourcesDir
            .resolve("kotlin/pt/grupovissoma/entity/types/NonEntityInput.kt")
        val updateFile = compilation.kspSourcesDir
            .resolve("kotlin/pt/grupovissoma/entity/types/NonEntityUpdate.kt")
        
        assertFalse(inputFile.exists(), "No Input file should be generated for non-entity")
        assertFalse(updateFile.exists(), "No Update file should be generated for non-entity")
    }
} 