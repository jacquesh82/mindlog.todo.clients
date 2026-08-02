plugins {
    alias(libs.plugins.mindlog.android.feature)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "today.mindlog.todo.feature.auth"
}

dependencies {
    // Chrome Custom Tabs: the mindlog id round trip must happen in the
    // browser, not a WebView — identity providers reject WebViews, and the
    // browser session is shared with the rest of the device.
    implementation(libs.androidx.browser)
    implementation(libs.kotlinx.serialization.json)
}
