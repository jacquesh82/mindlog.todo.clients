// swift-tools-version: 6.0
import PackageDescription

// The modules of the client, one target per Android Gradle module. The app
// target itself is not here — it lives in App/ and is assembled by XcodeGen
// (see project.yml), because a SwiftPM target cannot own an Info.plist, an
// entitlements file or a signing identity.
//
// A module exists only when two modules consume it or its build configuration
// genuinely differs — the same rule ../android-native/settings.gradle.kts
// states. CoreModel, CoreDatabase and CoreUI are absent for the reasons given
// there.
let package = Package(
    name: "MindlogTodo",
    // 17 is the floor for @Observable, which every repository and view model
    // here is built on. Below it the whole state layer would have to be
    // ObservableObject/@Published instead.
    platforms: [.iOS(.v17)],
    products: [
        .library(name: "CoreDesignSystem", targets: ["CoreDesignSystem"]),
        .library(name: "CoreDatastore", targets: ["CoreDatastore"]),
        .library(name: "CoreNetwork", targets: ["CoreNetwork"]),
        .library(name: "CoreData", targets: ["CoreData"]),
        .library(name: "FeatureAuth", targets: ["FeatureAuth"]),
        .library(name: "FeatureTasks", targets: ["FeatureTasks"]),
    ],
    targets: [
        .target(name: "CoreDesignSystem"),
        .target(name: "CoreDatastore"),
        .target(name: "CoreNetwork", dependencies: ["CoreDatastore"]),
        .target(name: "CoreData", dependencies: ["CoreNetwork"]),
        // CoreNetwork est déclaré alors que CoreData l'apporterait : les écrans
        // nomment ses types (TodoTask, APIError) et une dépendance transitive
        // qui disparaît un jour casserait des fichiers qui ne l'ont jamais
        // mentionnée.
        .target(
            name: "FeatureAuth",
            dependencies: ["CoreData", "CoreNetwork", "CoreDesignSystem"]
        ),
        .target(
            name: "FeatureTasks",
            dependencies: ["CoreData", "CoreNetwork", "CoreDesignSystem"]
        ),
        .testTarget(
            name: "CoreNetworkTests",
            dependencies: ["CoreNetwork", "CoreDatastore"]
        ),
        .testTarget(name: "CoreDataTests", dependencies: ["CoreData"]),
    ],
    swiftLanguageModes: [.v6]
)
