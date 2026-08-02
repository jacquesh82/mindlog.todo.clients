import CoreDesignSystem
import SwiftUI

@main
struct MindlogTodoApp: App {

    @State private var model = AppModel()

    var body: some Scene {
        WindowGroup {
            RootView(model: model)
                .mindlogTheme()
                .onAppear { model.start() }
                // The mindlog id return leg. `ASWebAuthenticationSession` hands
                // the callback straight back to the sign-in screen in the normal
                // case; this is the path for when the user finished the round
                // trip in Safari instead and came back through the scheme.
                .onOpenURL { model.handle($0) }
        }
    }
}
