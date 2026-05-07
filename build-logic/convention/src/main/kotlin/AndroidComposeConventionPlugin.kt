import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.findByType

class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
            
            val applicationExtension = extensions.findByType<ApplicationExtension>()
            if (applicationExtension != null) {
                applicationExtension.buildFeatures.compose = true
            }
            
            val libraryExtension = extensions.findByType<LibraryExtension>()
            if (libraryExtension != null) {
                libraryExtension.buildFeatures.compose = true
            }
        }
    }
}
