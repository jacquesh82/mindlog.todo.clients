plugins {
    alias(libs.plugins.mindlog.android.feature)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "today.mindlog.todo.feature.notes"
}

dependencies {
    // Le contenu d'une page est un document JSON `{ mode, boxes, markdown }` :
    // l'écran doit le lire et le réécrire sans perdre ce qu'il n'édite pas.
    implementation(libs.kotlinx.serialization.json)
}
