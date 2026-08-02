package today.mindlog.todo

import org.gradle.api.Project

/**
 * Which deployment a build points at, selected with `-PmindlogEnv=…`.
 *
 * Same convention as the Capacitor shell (`../android/scripts/gradle.sh`), so
 * both Android clients are driven the same way and can sit on one device side
 * by side — their application ids differ.
 */
data class MindlogEnv(
    val name: String,
    val baseUrl: String,
    val applicationIdSuffix: String,
    val label: String,
)

/** `prod` is the default so an un-flagged release build is never a staging one. */
fun Project.mindlogEnv(): MindlogEnv =
    when (val raw = (findProperty("mindlogEnv") as String? ?: "prod").lowercase()) {
        // The trailing slash is load-bearing: these URLs carry the `/app`
        // sub-path, and Retrofit drops the last segment of a base URL that does
        // not end in one. See ApiUrlTest in :core:network.
        "prod" -> MindlogEnv(
            name = "prod",
            baseUrl = "https://todo.mindlog.today/app/",
            applicationIdSuffix = "",
            label = "mindlog todo",
        )
        "qualif" -> MindlogEnv(
            name = "qualif",
            baseUrl = "https://todo.gra01.mindlog.today/app/",
            applicationIdSuffix = ".testing",
            label = "mindlog todo (qualif)",
        )
        // 10.0.2.2 is the host machine as seen from the emulator. Cleartext is
        // permitted only for the debug build type, and only for this host.
        "local" -> MindlogEnv(
            name = "local",
            baseUrl = "http://10.0.2.2:8080/",
            applicationIdSuffix = ".testing",
            label = "mindlog todo (local)",
        )
        else -> error("Unknown -PmindlogEnv=$raw — expected one of: prod, qualif, local")
    }
