package pt.grupovissoma.typesgenerator

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import javax.inject.Inject

open class EntityGenExtension @Inject constructor(objects: ObjectFactory) {
    /**
     * Fully-qualified package name patterns to include (supports Ant-style globs).
     * Default = everything under project group.
     */
    val filters: ListProperty<String> =
        objects.listProperty(String::class.java).convention(listOf("*"))
}