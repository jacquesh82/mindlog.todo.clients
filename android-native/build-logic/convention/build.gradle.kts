plugins {
    `kotlin-dsl`
}

group = "today.mindlog.todo.buildlogic"

// Must match the JVM the Android plugins target, or `kotlin-dsl` compiles the
// convention plugins against a different bytecode level than it applies them.
kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    // `compileOnly`: these plugins are on the classpath of the *consuming*
    // build, not bundled into this one.
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
    compileOnly(libs.ksp.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "mindlog.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidApplicationCompose") {
            id = "mindlog.android.application.compose"
            implementationClass = "AndroidApplicationComposeConventionPlugin"
        }
        register("androidLibrary") {
            id = "mindlog.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidLibraryCompose") {
            id = "mindlog.android.library.compose"
            implementationClass = "AndroidLibraryComposeConventionPlugin"
        }
        register("androidHilt") {
            id = "mindlog.android.hilt"
            implementationClass = "AndroidHiltConventionPlugin"
        }
        register("androidFeature") {
            id = "mindlog.android.feature"
            implementationClass = "AndroidFeatureConventionPlugin"
        }
        register("androidOpenApi") {
            id = "mindlog.android.openapi"
            implementationClass = "AndroidOpenApiConventionPlugin"
        }
    }
}
