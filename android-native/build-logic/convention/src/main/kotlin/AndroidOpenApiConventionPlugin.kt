import com.android.build.api.variant.LibraryAndroidComponentsExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.register
import org.gradle.process.CommandLineArgumentProvider
import today.mindlog.todo.libs

/**
 * Runs openapi-generator over the committed contract snapshot.
 *
 * `openApiCli` is a JavaExec rather than the org.openapi.generator plugin,
 * which does not work under the configuration cache — and that is on.
 */
abstract class GenerateApiModelsTask : JavaExec() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val spec: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty
}

/**
 * Generates the API DTOs from the committed OpenAPI snapshot.
 *
 * Only `models` are generated. openapi-generator's client scaffolding brings
 * its own ApiClient, auth handling and OkHttp setup, none of which survive
 * contact with the TokenAuthenticator and the SSE client in this module. The
 * Retrofit interfaces are thirty lines and stay hand-written on purpose; it is
 * `Task` and its twenty fields that must never be retyped.
 *
 * Wired through the Variant API rather than `sourceSets`: since AGP 9 the
 * source-set DSL rejects lazily-provided directories outright, because Android
 * Studio cannot tell generated from hand-written sources. `addGeneratedSource
 * Directory` states which it is and carries the task dependency, so Kotlin
 * compilation, KSP and lint all order themselves after the generator without
 * being named.
 *
 * The task is registered per variant, which is what that API expects — the run
 * is a few hundred milliseconds over one file.
 */
class AndroidOpenApiConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        val openApiCli = configurations.create("openApiCli")
        dependencies {
            add("openApiCli", libs.findLibrary("openapi-generator-cli").get())
        }

        val specFile = rootProject.layout.projectDirectory.file("openapi/mindlog-todo.openapi.json")

        extensions.getByType<LibraryAndroidComponentsExtension>().onVariants { variant ->
            val taskName = "generate${variant.name.replaceFirstChar(Char::uppercase)}ApiModels"
            val task = tasks.register<GenerateApiModelsTask>(taskName) {
                group = "openapi"
                description = "Generate Kotlin DTOs from the committed OpenAPI snapshot."
                spec.set(specFile)
                outputDir.set(layout.buildDirectory.dir("generated/openapi/${variant.name}"))

                classpath = openApiCli
                mainClass.set("org.openapitools.codegen.OpenAPIGenerator")
                argumentProviders.add(
                    CommandLineArgumentProvider {
                        listOf(
                            "generate",
                            "-i", spec.get().asFile.absolutePath,
                            "-g", "kotlin",
                            "-o", outputDir.get().asFile.absolutePath,
                            "--global-property", "models,modelDocs=false,modelTests=false",
                            // The contract marks every id `format: uuid`, which
                            // the generator turns into a `@Contextual UUID` —
                            // and kotlinx.serialization then throws at runtime
                            // unless a serializer is registered for it. Ids are
                            // opaque to a client, so String is both simpler and
                            // what actually goes over the wire.
                            "--type-mappings", "UUID=kotlin.String",
                            "--additional-properties",
                            listOf(
                                "packageName=today.mindlog.todo.core.network.model",
                                // Without this the models land in a `.models`
                                // sub-package nobody asked for.
                                "modelPackage=today.mindlog.todo.core.network.model",
                                "serializationLibrary=kotlinx_serialization",
                                // The API speaks ISO 8601 strings; mapping them
                                // to a date type here only adds a conversion
                                // the client must undo before sending back.
                                "dateLibrary=string",
                                // Empty, so the task's output directory *is*
                                // the source root the Variant API is handed.
                                "sourceFolder=",
                            ).joinToString(","),
                        )
                    },
                )
            }

            variant.sources.kotlin?.addGeneratedSourceDirectory(
                task,
                GenerateApiModelsTask::outputDir,
            )
        }
    }
}
