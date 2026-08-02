import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import today.mindlog.todo.TARGET_SDK
import today.mindlog.todo.configureKotlinAndroid
import today.mindlog.todo.mindlogEnv
import java.util.Properties

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.application")

        val env = mindlogEnv()

        extensions.configure<ApplicationExtension> {
            configureKotlinAndroid(this)

            // AGP 9 makes generated resources opt-in; `app_name` below is one.
            buildFeatures.resValues = true

            defaultConfig {
                applicationId = "today.mindlog.todo.native${env.applicationIdSuffix}"
                targetSdk = TARGET_SDK
                versionCode = 1
                versionName = "0.1.0"

                resValue("string", "app_name", env.label)

                // The OAuth return leg comes back on a scheme unique to this
                // variant, so prod and testing installs never contend for it —
                // same reasoning as `custom_url_scheme` in the Capacitor shell.
                manifestPlaceholders["authScheme"] = applicationId!!

                testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            }

            // Signing is opt-in: without a keystore.properties the release
            // variant still builds, just unsigned. Keeps CI and fresh clones
            // working without secrets.
            val keystoreFile = rootProject.file("keystore.properties")
            val keystoreProps = Properties().apply {
                if (keystoreFile.exists()) keystoreFile.inputStream().use(::load)
            }
            if (keystoreProps.isNotEmpty()) {
                signingConfigs.create("release") {
                    storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                    storePassword = keystoreProps.getProperty("storePassword")
                    keyAlias = keystoreProps.getProperty("keyAlias")
                    keyPassword = keystoreProps.getProperty("keyPassword")
                }
            }

            buildTypes {
                getByName("release") {
                    isMinifyEnabled = true
                    isShrinkResources = true
                    proguardFiles(
                        getDefaultProguardFile("proguard-android-optimize.txt"),
                        "proguard-rules.pro",
                    )
                    if (keystoreProps.isNotEmpty()) {
                        signingConfig = signingConfigs.getByName("release")
                    }
                }
                getByName("debug") {
                    applicationIdSuffix = ".debug"
                }
            }

            packaging {
                resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
            }
        }
    }
}
