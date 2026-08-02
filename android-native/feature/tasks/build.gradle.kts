plugins {
    alias(libs.plugins.mindlog.android.feature)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "today.mindlog.todo.feature.tasks"
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
}
