plugins {
    alias(libs.plugins.mindlog.android.library)
    alias(libs.plugins.mindlog.android.library.compose)
}

android {
    namespace = "today.mindlog.todo.core.designsystem"
}

dependencies {
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.core.ktx)
}
