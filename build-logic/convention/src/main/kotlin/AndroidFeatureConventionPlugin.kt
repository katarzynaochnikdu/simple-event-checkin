import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("md.android.library")
            pluginManager.apply("md.android.library.compose")
            pluginManager.apply("md.hilt")

            dependencies {
                add("implementation", project(":core:core-model"))
                add("implementation", project(":core:core-ui"))
                add("implementation", project(":core:core-network"))
                add("implementation", project(":core:core-database"))
                add("implementation", project(":core:core-datastore"))
            }
        }
    }
}
