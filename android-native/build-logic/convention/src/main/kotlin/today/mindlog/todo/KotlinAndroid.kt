package today.mindlog.todo

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project

// compileSdk and targetSdk move independently, and here they should: recent
// AndroidX artifacts refuse to be consumed below 37, while targetSdk is what
// opts the app into new runtime behaviour and is better raised deliberately.
internal const val COMPILE_SDK = 37
internal const val MIN_SDK = 26
internal const val TARGET_SDK = 36

/**
 * The single place Android and Kotlin are configured.
 *
 * The archived talk client repeated this block in all twenty-one of its module
 * build files; the point of `build-logic` is that a bump happens once.
 *
 * `minSdk 26` rather than the shell's 23: it covers effectively the whole
 * install base and spares us core-library desugaring for `java.time`.
 * `targetSdk 36` is what Play requires of a new application.
 *
 * Written against AGP 9's `CommonExtension`, which is no longer generic and
 * exposes plain properties rather than configuration lambdas — hence the
 * `.apply` block instead of `compileOptions { … }`.
 *
 * There is no Kotlin block here: since AGP 9 the Android plugin brings Kotlin
 * itself (applying `org.jetbrains.kotlin.android` on top is now an error) and
 * derives the JVM target from `compileOptions` below.
 */
internal fun Project.configureKotlinAndroid(commonExtension: CommonExtension) {
    commonExtension.compileSdk = COMPILE_SDK
    commonExtension.defaultConfig.minSdk = MIN_SDK

    commonExtension.compileOptions.apply {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
