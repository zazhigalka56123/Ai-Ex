import org.gradle.api.Project
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.getByType

internal val Project.catalog: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun Project.lib(alias: String): Provider<MinimalExternalModuleDependency> =
    catalog.findLibrary(alias).orElseThrow { IllegalArgumentException("Нет библиотеки '$alias' в libs.versions.toml") }

internal fun Project.catalogVersion(alias: String): String =
    catalog.findVersion(alias).orElseThrow { IllegalArgumentException("Нет версии '$alias' в libs.versions.toml") }.requiredVersion
