import CoreDesignSystem
import FeatureAuth
import FeatureTasks
import SwiftUI

/// Le garde d'authentification, et rien d'autre.
///
/// Android exprime la même chose avec un `NavHost` de trois destinations et un
/// `LaunchedEffect` qui navigue selon `signedIn`. Ici un `switch` suffit : les
/// deux écrans ne sont pas empilés l'un sur l'autre — on ne revient pas
/// « en arrière » sur l'écran de connexion depuis la liste — donc il n'y a
/// aucune pile à gérer.
struct RootView: View {

    let model: AppModel

    var body: some View {
        switch model.signedIn {
        case .none:
            // Encore en train de restaurer la session. L'écran de lancement est
            // toujours visible sous ce voile, ce qui évite le clignotement d'un
            // écran de connexion affiché puis remplacé.
            ProgressView()
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(MindlogColor.background)

        case .some(false):
            LoginView(
                viewModel: LoginViewModel(
                    authRepository: model.container.authRepository,
                    callbackBus: model.container.callbackBus
                )
            )

        case .some(true):
            TasksView(
                viewModel: TasksViewModel(
                    taskRepository: model.container.taskRepository,
                    navigationRepository: model.container.navigationRepository,
                    authRepository: model.container.authRepository
                )
            )
        }
    }
}
