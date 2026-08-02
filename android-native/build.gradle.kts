// Root build file. Every plugin is declared `apply false` here and applied by
// the convention plugins in `build-logic/` — modules never configure Android,
// Kotlin or Compose directly.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}
