package pt.grupovissoma.typesgenerator

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import javax.inject.Inject

open class EntityGenExtension @Inject constructor(objects: ObjectFactory) {
    /**
     * Fully-qualified package name patterns to include (supports Ant-style globs).
     * Default = everything under project group.
     */
    val filters: ListProperty<String> =
        objects.listProperty(String::class.java).convention(listOf("*"))

    /**
     * Whether to generate nullable properties for Update types.
     * Default = true
     */
    val nullableUpdates: Property<Boolean> =
        objects.property(Boolean::class.java).convention(true)

    /**
     * Custom suffix for Input types.
     * Default = "Input"
     */
    val inputSuffix: Property<String> =
        objects.property(String::class.java).convention("Input")

    /**
     * Custom suffix for Update types.
     * Default = "Update"
     */
    val updateSuffix: Property<String> =
        objects.property(String::class.java).convention("Update")
}
