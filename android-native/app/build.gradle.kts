plugins {
    alias(libs.plugins.mindlog.android.application)
    alias(libs.plugins.mindlog.android.application.compose)
    alias(libs.plugins.mindlog.android.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "today.mindlog.todo"
}

dependencies {
    implementation(projects.core.designsystem)
    implementation(projects.core.data)
    implementation(projects.feature.auth)
    implementation(projects.feature.ai)
    implementation(projects.feature.calendar)
    implementation(projects.feature.notes)
    implementation(projects.feature.tasks)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
}
