plugins {
    alias(libs.plugins.mindlog.android.library)
    alias(libs.plugins.mindlog.android.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "today.mindlog.todo.core.data"
}

dependencies {
    api(projects.core.network)
    api(projects.core.datastore)

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
