package pt.grupovissoma.typesgenerator

import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*
import com.google.devtools.ksp.validate
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.*
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
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
                generateTypesForEntity(entity, resolver)
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

    private fun generateTypesForEntity(entity: KSClassDeclaration, resolver: Resolver) {
        val baseName = entity.simpleName.asString()
        val packageName = entity.packageName.asString()
        val typesPackage = "$packageName.types"

        val properties = try {
            entity.getAllProperties().toList()
        } catch (e: Exception) {
            logger.error("Erro ao obter propriedades da entidade $baseName: ${e.message}")
            return
        }

        val idNames = properties
            .filter { it.hasAnnotation(GeneratedValue::class) }
            .map { it.simpleName.asString() }
            .toSet()

        val editable = properties.filter { prop ->
            prop.isMutable &&
                    prop.simpleName.asString() !in idNames &&
                    !prop.hasAnnotation(jakarta.persistence.Version::class)
        }

        if (editable.isEmpty()) {
            logger.warn("Entidade $baseName não tem propriedades editáveis")
            return
        }

        val origin = entity.containingFile ?: run {
            logger.error("Entidade $baseName não tem arquivo de origem")
            return
        }

        logger.info("Gerando tipos para entidade $baseName no pacote $typesPackage")
        generateDataClass("$baseName$inputSuffix", typesPackage, editable, false, origin, resolver)
        generateDataClass("$baseName$updateSuffix", typesPackage, editable, nullableUpdates, origin, resolver)
    }

    private fun generateDataClass(
        className: String,
        pkg: String,
        props: List<KSPropertyDeclaration>,
        nullable: Boolean,
        origin: KSFile,
        resolver: Resolver
    ) {
        val ctor = FunSpec.constructorBuilder()
        val typeBuilder = TypeSpec.classBuilder(className).addModifiers(KModifier.DATA)

        props.forEach { prop ->
            val name = prop.simpleName.asString()
            var typeName = resolvePropertyType(prop, resolver)

            if (nullable && !typeName.isNullable) {
                typeName = typeName.copy(nullable = true)
            }

            ctor.addParameter(
                ParameterSpec.builder(name, typeName).apply {
                    if (nullable) defaultValue("null")
                }.build()
            )
            typeBuilder.addProperty(
                PropertySpec.builder(name, typeName)
                    .initializer(name)
                    .build()
            )
        }

        typeBuilder.primaryConstructor(ctor.build())

        FileSpec.builder(pkg, className)
            .addType(typeBuilder.build())
            .addFileComment("AUTO-GENERATED pelo graphql-types-generator")
            .addFileComment("Não editar manualmente - alterações serão perdidas na próxima geração")
            .build()
            .writeTo(codeGenerator, aggregating = false, originatingKSFiles = listOf(origin))
    }

    private fun resolvePropertyType(prop: KSPropertyDeclaration, resolver: Resolver): TypeName {
        val kstype = try {
            prop.type.resolve()
        } catch (e: Exception) {
            return prop.type.toTypeName()
        }

        val decl = kstype.declaration
        if (decl is KSClassDeclaration && decl.hasAnnotation(Entity::class)) {
            return inputTypeNameFor(decl, kstype.isMarkedNullable)
        }

        if (kstype.arguments.isNotEmpty()) {
            val argType = kstype.arguments.first().type?.resolve()
            if (argType?.declaration is KSClassDeclaration &&
                (argType.declaration as KSClassDeclaration).hasAnnotation(Entity::class)
            ) {
                return collectionInputTypeName(kstype, argType.declaration as KSClassDeclaration, kstype.isMarkedNullable)
            }
        }

        return prop.type.toTypeName()
    }

    private fun inputTypeNameFor(decl: KSClassDeclaration, nullable: Boolean): TypeName {
        val base = decl.simpleName.asString()
        val pkg = decl.packageName.asString()
        val cn = ClassName("$pkg.types", "$base$inputSuffix")
        return if (nullable) cn.copy(nullable = true) else cn
    }

    private fun collectionInputTypeName(
        kstype: KSType,
        decl: KSClassDeclaration,
        nullable: Boolean
    ): TypeName {
        val base = decl.simpleName.asString()
        val pkg = decl.packageName.asString()
        val elem = ClassName("$pkg.types", "$base$inputSuffix")
        val qn = kstype.declaration.qualifiedName?.asString()
        val colType = when (qn) {
            "kotlin.collections.List" -> LIST.parameterizedBy(elem)
            "kotlin.collections.Set" -> SET.parameterizedBy(elem)
            "kotlin.collections.MutableList" -> MUTABLE_LIST.parameterizedBy(elem)
            "kotlin.collections.MutableSet" -> MUTABLE_SET.parameterizedBy(elem)
            "kotlin.collections.Collection" -> COLLECTION.parameterizedBy(elem)
            else -> return kstype.toTypeName()
        }
        return if (nullable) colType.copy(nullable = true) else colType
    }

    private fun KSPropertyDeclaration.hasAnnotation(anno: KClass<*>): Boolean =
        annotations.any { a ->
            val name = a.shortName.asString()
            val fq = a.annotationType.resolve().declaration.qualifiedName?.asString()
            name == anno.simpleName || fq == anno.qualifiedName
        }

    private fun KSClassDeclaration.hasAnnotation(anno: KClass<*>): Boolean =
        annotations.any { a ->
            val name = a.shortName.asString()
            val fq = a.annotationType.resolve().declaration.qualifiedName?.asString()
            name == anno.simpleName || fq == anno.qualifiedName
        }
}
