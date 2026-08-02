import AuthenticationServices
import CoreData
import CoreDesignSystem
import SwiftUI

public struct LoginView: View {

    @State private var viewModel: LoginViewModel
    @Environment(\.webAuthenticationSession) private var webAuthentication

    public init(viewModel: LoginViewModel) {
        _viewModel = State(initialValue: viewModel)
    }

    public var body: some View {
        ScrollView {
            VStack(spacing: 0) {
                Text("mindlog todo")
                    .font(.mindlogTitleLarge)
                    .padding(.bottom, 32)

                if viewModel.pendingToken != nil {
                    pendingEmailForm
                } else {
                    credentialsForm
                }

                if let error = viewModel.error {
                    Text(error)
                        .font(.mindlogBodyMedium)
                        .foregroundStyle(MindlogColor.error)
                        .padding(.top, 16)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
            }
            .padding(24)
            .frame(maxWidth: .infinity)
        }
        .scrollBounceBehavior(.basedOnSize)
        .background(MindlogColor.background)
        .onAppear { viewModel.start() }
    }

    // MARK: - Email and password

    private var credentialsForm: some View {
        VStack(spacing: 12) {
            TextField("Email", text: $viewModel.email)
                .textContentType(.username)
                .keyboardType(.emailAddress)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .submitLabel(.next)
                .onChange(of: viewModel.email) { viewModel.onFieldEdited() }
                .textFieldStyle(.roundedBorder)

            SecureField("Password", text: $viewModel.password)
                .textContentType(.password)
                .submitLabel(.done)
                .onSubmit { viewModel.signIn() }
                .onChange(of: viewModel.password) { viewModel.onFieldEdited() }
                .textFieldStyle(.roundedBorder)

            Button(action: viewModel.signIn) {
                Group {
                    if viewModel.busy {
                        ProgressView().tint(MindlogColor.onPrimary)
                    } else {
                        Text("Sign in")
                    }
                }
                .frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent)
            .disabled(!viewModel.canSubmit)
            .padding(.top, 8)

            // Only drawn when the deployment says it has mindlog id configured;
            // otherwise the button would lead straight to a 503.
            if viewModel.providers.mindlogId {
                Button("Continue with mindlog id") { startMindlogId(create: false) }
                    .buttonStyle(.bordered)
                    .frame(maxWidth: .infinity)
                    .disabled(viewModel.busy)

                Button("Create a mindlog account") { startMindlogId(create: true) }
                    .buttonStyle(.borderless)
                    .disabled(viewModel.busy)
            }
        }
    }

    // MARK: - mindlog id without an email address

    private var pendingEmailForm: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Your mindlog account has no email address. Add one to finish signing in.")
                .font(.mindlogBodyMedium)

            TextField("Email", text: $viewModel.pendingEmail)
                .textContentType(.emailAddress)
                .keyboardType(.emailAddress)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .submitLabel(.done)
                .onSubmit { viewModel.submitPendingEmail() }
                .textFieldStyle(.roundedBorder)

            Button("Finish") { viewModel.submitPendingEmail() }
                .buttonStyle(.borderedProminent)
                .frame(maxWidth: .infinity)
                .disabled(viewModel.busy || viewModel.pendingEmail.isEmpty)
                .padding(.top, 8)
        }
    }

    /// Opens the identity provider in an authentication session.
    ///
    /// `ASWebAuthenticationSession` and not a `WKWebView`: providers reject
    /// embedded web views outright, and this one shares Safari's cookie store,
    /// so a user already signed in to mindlog id is not asked twice. It is the
    /// same reasoning that puts the Android client in a Chrome Custom Tab — with
    /// one thing the Custom Tab cannot do, which is hand the callback URL back
    /// to the caller instead of relying on the deep link.
    private func startMindlogId(create: Bool) {
        guard let url = viewModel.mindlogIdURL(create: create) else { return }
        Task {
            do {
                let callback = try await webAuthentication.authenticate(
                    using: url,
                    callbackURLScheme: viewModel.callbackScheme
                )
                viewModel.handleCallback(callback)
            } catch {
                // Cancelling is the common case and is not an error worth
                // showing; anything else surfaces through the callback bus.
                viewModel.mindlogIdCancelled()
            }
        }
    }
}
