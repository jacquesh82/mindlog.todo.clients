import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import today.mindlog.todo.TARGET_SDK
import today.mindlog.todo.configureKotlinAndroid

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.library")

        extensions.configure<LibraryExtension> {
            configureKotlinAndroid(this)
            defaultConfig {
                testOptions.targetSdk = TARGET_SDK
                consumerProguardFiles("consumer-rules.pro")
            }
            // Nothing in a library module should reach for BuildConfig unless
            // it opts in; :core:datastore is the one that does.
            buildFeatures.buildConfig = false
        }
    }
}
