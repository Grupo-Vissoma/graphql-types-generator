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
    private val options: Map<String, String>
) : SymbolProcessor {

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val entities = resolver
            .getSymbolsWithAnnotation(Entity::class.qualifiedName!!)
            .filterIsInstance<KSClassDeclaration>()
            .toList()

        if (entities.isEmpty()) return emptyList()

        val deferred = mutableListOf<KSAnnotated>()

        entities.forEach { entity ->
            if (!entity.validate()) {
                deferred += entity
                return@forEach
            }
            runCatching { generateTypes(entity) }
                .onFailure {
                    logger.error("✗ ${entity.simpleName.asString()}: ${it.message}", entity)
                    deferred += entity
                }
        }
        return deferred
    }

    private fun generateTypes(entity: KSClassDeclaration) {
        val baseName = entity.simpleName.asString()
        val pkg = entity.packageName.asString()
        val outPkg = "$pkg.types"

        val props = entity.getAllProperties().toList()
        val idPropNames = props
            .filter { p -> p.hasAnnotation(Id::class) }
            .map { it.simpleName.asString() }
            .toSet()

        val nonIdProps = props.filter {
            it.isMutable && it.simpleName.asString() !in idPropNames
        }

        // Input (non-nullable)
        writeDataClass(
            name = "${baseName}${options["inputSuffix"] ?: "Input"}",
            packageName = outPkg,
            properties = nonIdProps,
            nullable = false,
            originating = entity.containingFile!!
        )

        // Update (nullable)
        writeDataClass(
            name = "${baseName}${options["updateSuffix"] ?: "Update"}",
            packageName = outPkg,
            properties = nonIdProps,
            nullable = true,
            originating = entity.containingFile!!
        )
    }

    private fun writeDataClass(
        name: String,
        packageName: String,
        properties: List<KSPropertyDeclaration>,
        nullable: Boolean,
        originating: KSFile
    ) {
        val ctor = FunSpec.constructorBuilder()
        val klass = TypeSpec.classBuilder(name)
            .addModifiers(KModifier.DATA)

        properties.forEach { prop ->
            val pName = prop.simpleName.asString()
            var pType = prop.type.toTypeName()
            if (nullable && !pType.isNullable) pType = pType.copy(nullable = true)

            ctor.addParameter(
                ParameterSpec.builder(pName, pType)
                    .apply { if (nullable) defaultValue("null") }
                    .build()
            )
            klass.addProperty(
                PropertySpec.builder(pName, pType)
                    .initializer(pName)
                    .build()
            )
        }

        klass.primaryConstructor(ctor.build())

        FileSpec.builder(packageName, name)
            .addType(klass.build())
            .addFileComment("AUTO-GENERATED – não editar.")
            .build()
            .writeTo(
                codeGenerator,
                aggregating = false,
                originatingKSFiles = listOf(originating)
            )
    }
}

/* helper */
private fun KSPropertyDeclaration.hasAnnotation(klass: KClass<*>): Boolean =
    annotations.any {
        it.shortName.asString() == klass.simpleName ||
                it.annotationType.resolve()
                    .declaration.qualifiedName?.asString() == klass.qualifiedName
    }
