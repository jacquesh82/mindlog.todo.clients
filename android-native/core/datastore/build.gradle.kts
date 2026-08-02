import today.mindlog.todo.mindlogEnv

plugins {
    alias(libs.plugins.mindlog.android.library)
    alias(libs.plugins.mindlog.android.hilt)
}

val env = mindlogEnv()

android {
    namespace = "today.mindlog.todo.core.datastore"

    // The environment enters the app here and nowhere else. It is only a
    // *default*: ServerStore lets the URL be overridden at runtime, which is
    // how a debug build can be pointed at qualif without recompiling.
    buildFeatures.buildConfig = true
    defaultConfig {
        buildConfigField("String", "MINDLOG_ENV", "\"${env.name}\"")
        buildConfigField("String", "DEFAULT_BASE_URL", "\"${env.baseUrl}\"")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
