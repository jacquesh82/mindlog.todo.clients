plugins {
    alias(libs.plugins.mindlog.android.library)
    alias(libs.plugins.mindlog.android.hilt)
    alias(libs.plugins.mindlog.android.openapi)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "today.mindlog.todo.core.network"
}

dependencies {
    api(projects.core.datastore)

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    api(libs.retrofit.core)
    api(libs.okhttp)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp.sse)
    debugImplementation(libs.okhttp.logging)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}
