import CoreDatastore
import Foundation
import XCTest

@testable import CoreNetwork

/// Guards the `/app` sub-path.
///
/// Production and qualif are served under `https://todo…/app/`, while local is
/// served from the root. Anything that resolves a service path *against* the
/// base rather than appending to it silently drops the prefix — and the failure
/// only appears on promotion, because local has no prefix to lose.
///
/// The Android client pins the same four URLs in `ApiUrlTest`. If one of these
/// two files changes, the other is wrong.
final class APIURLTests: XCTestCase {

    private func resolve(
        _ base: String,
        _ path: String,
        _ query: [URLQueryItem] = []
    ) -> String? {
        APIURL.resolve(base: URL(string: base)!, path: path, query: query)?.absoluteString
    }

    func testKeepsTheAppSubPathInProduction() {
        XCTAssertEqual(
            resolve("https://todo.mindlog.today/app/", "api/v1/tasks"),
            "https://todo.mindlog.today/app/api/v1/tasks"
        )
    }

    func testKeepsTheAppSubPathInQualif() {
        XCTAssertEqual(
            resolve("https://todo.gra01.mindlog.today/app/", "api/v1/auth/login"),
            "https://todo.gra01.mindlog.today/app/api/v1/auth/login"
        )
    }

    func testWorksAgainstARootServedLocalServer() {
        XCTAssertEqual(
            resolve("http://localhost:8080/", "api/v1/tasks"),
            "http://localhost:8080/api/v1/tasks"
        )
    }

    func testCarriesTheQueryStringAcross() {
        XCTAssertEqual(
            resolve("https://todo.mindlog.today/app/", "api/v1/tasks", [
                URLQueryItem(name: "completed", value: "false"),
                URLQueryItem(name: "limit", value: "200"),
            ]),
            "https://todo.mindlog.today/app/api/v1/tasks?completed=false&limit=200"
        )
    }

    /// Le piège que la version Android verrouille sous une autre forme :
    /// là-bas, une base sans barre oblique finale fait perdre `/app` parce que
    /// Retrofit *résout* le chemin. Ici on *ajoute* des segments, donc les deux
    /// formes donnent la même URL. C'est le comportement qu'il faut figer :
    /// sans cette assertion, une future réécriture en `URL(string:relativeTo:)`
    /// réintroduirait le bug sans qu'aucun test ne tombe.
    func testATrailingSlashOnTheBaseIsNotLoadBearingHere() {
        XCTAssertEqual(
            resolve("https://todo.mindlog.today/app", "api/v1/tasks"),
            "https://todo.mindlog.today/app/api/v1/tasks"
        )
    }

    /// Un chemin de service absolu effacerait le préfixe. Il est retiré plutôt
    /// que respecté.
    func testALeadingSlashOnThePathIsStripped() {
        XCTAssertEqual(
            resolve("https://todo.mindlog.today/app/", "/api/v1/tasks"),
            "https://todo.mindlog.today/app/api/v1/tasks"
        )
    }

    func testTheEnvironmentDefaultsAllEndInASlash() {
        for environment in MindlogEnvironment.allCases {
            XCTAssertTrue(
                environment.defaultBaseURL.absoluteString.hasSuffix("/"),
                "\(environment.rawValue) default base URL must end in a slash"
            )
        }
    }

    func testTheEnvironmentDefaultsAreTheOnesTheAndroidClientUses() {
        XCTAssertEqual(
            MindlogEnvironment.prod.defaultBaseURL.absoluteString,
            "https://todo.mindlog.today/app/"
        )
        XCTAssertEqual(
            MindlogEnvironment.qualif.defaultBaseURL.absoluteString,
            "https://todo.gra01.mindlog.today/app/"
        )
    }
}
