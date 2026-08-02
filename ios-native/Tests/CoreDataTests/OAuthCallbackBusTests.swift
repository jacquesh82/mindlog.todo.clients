import Foundation
import XCTest

@testable import CoreData

/// Ce que le fournisseur d'identité renvoie porte les jetons de session. Une
/// erreur d'analyse ici ne produit pas un plantage mais une connexion qui
/// échoue sans raison visible — et un `+` transformé en espace suffit.
@MainActor
final class OAuthCallbackBusTests: XCTestCase {

    private func callback(for url: String) async -> OAuthCallback? {
        let bus = OAuthCallbackBus()
        var iterator = bus.callbacks.makeAsyncIterator()
        guard bus.handle(URL(string: url)!) else { return nil }
        return await iterator.next()
    }

    func testTokensTravelInTheFragment() async {
        let result = await callback(
            for: "today.mindlog.todo.native://auth/callback"
                + "#access_token=abc&refresh_token=def&expires_in=900"
        )
        XCTAssertEqual(result, .tokens(accessToken: "abc", refreshToken: "def"))
    }

    func testAnAccountWithoutAnEmailYieldsAPendingToken() async {
        let result = await callback(
            for: "today.mindlog.todo.native://auth/callback#mindlog_id_pending=xyz"
        )
        XCTAssertEqual(result, .needsEmail(pendingToken: "xyz"))
    }

    func testAnErrorIsCarriedThrough() async {
        let result = await callback(
            for: "today.mindlog.todo.native://auth/callback#error=access_denied"
        )
        XCTAssertEqual(result, .failed(reason: "access_denied"))
    }

    func testValuesArePercentDecoded() async {
        let result = await callback(
            for: "today.mindlog.todo.native://auth/callback#error=acc%C3%A8s%20refus%C3%A9"
        )
        XCTAssertEqual(result, .failed(reason: "accès refusé"))
    }

    /// Le piège que `URLComponents(query:)` tend : dans une *query*, `+` est un
    /// espace. Dans un fragment il ne l'est pas, et un jeton base64url peut en
    /// contenir. C'est pourquoi l'analyse est faite paire par paire.
    func testAPlusSignInATokenSurvives() async {
        let result = await callback(
            for: "today.mindlog.todo.native://auth/callback"
                + "#access_token=a+b/c=&refresh_token=d+e"
        )
        XCTAssertEqual(result, .tokens(accessToken: "a+b/c=", refreshToken: "d+e"))
    }

    func testAFragmentWithNothingRecognisableIsRefused() async {
        let bus = OAuthCallbackBus()
        XCTAssertFalse(
            bus.handle(URL(string: "today.mindlog.todo.native://auth/callback#state=1")!)
        )
    }

    func testAURLWithNoFragmentIsRefused() async {
        let bus = OAuthCallbackBus()
        XCTAssertFalse(bus.handle(URL(string: "today.mindlog.todo.native://auth/callback")!))
    }

    /// Les jetons voyagent dans le fragment et non dans la query, précisément
    /// pour ne jamais atteindre un journal serveur. Les lire dans la query
    /// reviendrait à valider ce chemin.
    func testTokensInTheQueryAreIgnored() async {
        let bus = OAuthCallbackBus()
        XCTAssertFalse(
            bus.handle(
                URL(string: "today.mindlog.todo.native://auth/callback?access_token=a&refresh_token=b")!
            )
        )
    }
}
