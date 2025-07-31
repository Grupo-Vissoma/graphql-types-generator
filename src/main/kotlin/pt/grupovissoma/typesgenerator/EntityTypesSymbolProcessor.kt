package pt.grupovissoma.typesgenerator

import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*
import com.google.devtools.ksp.validate
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ksp.*
import jakarta.persistence.Entity
import jakarta.persistence.Id
import kotlin.reflect.KClass

class EntityTypesSymbolProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
    options: Map<String, String>
) : SymbolProcessor {

    private val inputSuffix = options["inputSuffix"] ?: "Input"
    private val updateSuffix = options["updateSuffix"] ?: "Update"
    private val nullableUpdates = options["nullableUpdates"]?.toBoolean() ?: true

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val entities = try {
            resolver
                .getSymbolsWithAnnotation(Entity::class.qualifiedName!!)
                .filterIsInstance<KSClassDeclaration>()
                .toList()
        } catch (e: Exception) {
            logger.error("Erro ao obter entidades: ${e.message}")
            return emptyList()
        }

        if (entities.isEmpty()) {
            logger.info("Nenhuma entidade @Entity encontrada")
            return emptyList()
        }

        val deferred = mutableListOf<KSAnnotated>()
        var processedCount = 0

        entities.forEach { entity ->
            if (!entity.validate()) {
                deferred += entity
                return@forEach
            }

            runCatching {
                generateTypesForEntity(entity)
                processedCount++
                logger.info("Tipos gerados para ${entity.qualifiedName?.asString()}")
            }.onFailure { error ->
                logger.error("Erro ao processar ${entity.simpleName.asString()}: ${error.message}", entity)
                deferred += entity
            }
        }

        logger.info("Processamento concluído: $processedCount/${entities.size} entidades processadas")
        return deferred
    }

    private fun generateTypesForEntity(entity: KSClassDeclaration) {
        val baseName = entity.simpleName.asString()
        val packageName = entity.packageName.asString()
        val typesPackage = "$packageName.types"

        val properties = try {
            entity.getAllProperties().toList()
        } catch (e: Exception) {
            logger.error("Erro ao obter propriedades da entidade ${baseName}: ${e.message}")
            return
        }
        
        val idPropertyNames = properties
            .filter { it.hasAnnotation(Id::class) }
            .map { it.simpleName.asString() }
            .toSet()

        val editableProperties = properties.filter { prop ->
            prop.isMutable &&
                    prop.simpleName.asString() !in idPropertyNames &&
                    !prop.hasAnnotation(jakarta.persistence.Version::class) // Excluir campos @Version
        }

        if (editableProperties.isEmpty()) {
            logger.warn("Entidade ${baseName} não tem propriedades editáveis")
            return
        }

        val originatingFile = entity.containingFile
        if (originatingFile == null) {
            logger.error("Entidade ${baseName} não tem arquivo de origem")
            return
        }
        logger.info("Gerando tipos para entidade ${baseName} no pacote $typesPackage")
        // Gerar Input (campos obrigatórios)
        generateDataClass(
            name = "$baseName$inputSuffix",
            packageName = typesPackage,
            properties = editableProperties,
            nullable = false,
            originatingFile = originatingFile
        )

        // Gerar Update (campos opcionais se configurado)
        generateDataClass(
            name = "$baseName$updateSuffix",
            packageName = typesPackage,
            properties = editableProperties,
            nullable = nullableUpdates,
            originatingFile = originatingFile
        )
    }

    private fun generateDataClass(
        name: String,
        packageName: String,
        properties: List<KSPropertyDeclaration>,
        nullable: Boolean,
        originatingFile: KSFile
    ) {
        val constructor = FunSpec.constructorBuilder()
        val classBuilder = TypeSpec.classBuilder(name)
            .addModifiers(KModifier.DATA)

        properties.forEach { property ->
            val propName = property.simpleName.asString()
            var propType = property.type.toTypeName()

            // Aplicar nullabilidade se configurado
            if (nullable && !propType.isNullable) {
                propType = propType.copy(nullable = true)
            }

            // Parâmetro do construtor
            val parameter = ParameterSpec.builder(propName, propType).apply {
                if (nullable) defaultValue("null")
            }.build()
            constructor.addParameter(parameter)

            // Propriedade da classe
            val propertySpec = PropertySpec.builder(propName, propType)
                .initializer(propName)
                .build()
            classBuilder.addProperty(propertySpec)
        }

        classBuilder.primaryConstructor(constructor.build())

        // Gerar e escrever ficheiro
        val fileSpec = FileSpec.builder(packageName, name)
            .addType(classBuilder.build())
            .addFileComment("AUTO-GENERATED pelo graphql-types-generator v${javaClass.`package`.implementationVersion ?: "dev"}")
            .addFileComment("Não editar manualmente - alterações serão perdidas na próxima geração")
            .build()

        // CRÍTICO: declarar ficheiro de origem para processamento incremental
        fileSpec.writeTo(
            codeGenerator = codeGenerator,
            aggregating = false, // Isolating - um output por input
            originatingKSFiles = listOf(originatingFile)
        )
    }

    private fun KSPropertyDeclaration.hasAnnotation(annotationClass: KClass<*>): Boolean {
        return annotations.any { annotation ->
            try {
                val annotationName = annotation.shortName.asString()
                val annotationFqName = annotation.annotationType.resolve()
                    .declaration.qualifiedName?.asString()

                annotationName == annotationClass.simpleName ||
                        annotationFqName == annotationClass.qualifiedName
            } catch (e: Exception) {
                // Log warning but don't fail the entire process
                logger.warn("Erro ao verificar anotação em propriedade ${simpleName.asString()}: ${e.message}")
                false
            }
        }
    }
}
