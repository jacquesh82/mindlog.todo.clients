import Foundation
import XCTest

@testable import CoreNetwork

/// Le client Android reçoit ce découpage d'okhttp-sse et n'a donc rien à
/// tester. Ici il est écrit à la main, alors il est vérifié : une trame perdue
/// ne se voit pas — l'application reste simplement en retard sur le serveur,
/// silencieusement, jusqu'au prochain rechargement manuel.
final class SSEFrameDecoderTests: XCTestCase {

    private func frames(of lines: [String]) -> [String] {
        var decoder = SSEFrameDecoder()
        return lines.compactMap { decoder.feed($0) }
    }

    func testASingleDataLineFollowedByABlankLineIsAFrame() {
        XCTAssertEqual(
            frames(of: [#"data: {"entity":"task","action":"update"}"#, ""]),
            [#"{"entity":"task","action":"update"}"#]
        )
    }

    func testTheHeartbeatIsNotAFrame() {
        // Le serveur envoie `: ping`. Pris pour une donnée, il produirait une
        // erreur de décodage à chaque battement.
        XCTAssertEqual(frames(of: [": ping", ""]), [])
    }

    func testABlankLineWithNothingPendingProducesNothing() {
        XCTAssertEqual(frames(of: ["", "", ""]), [])
    }

    func testSeveralDataLinesAreJoinedByANewline() {
        XCTAssertEqual(
            frames(of: ["data: {", #"data:   "entity": "task""#, "data: }", ""]),
            ["{\n  \"entity\": \"task\"\n}"]
        )
    }

    func testOnlyTheFirstSpaceAfterTheColonIsASeparator() {
        XCTAssertEqual(frames(of: ["data:  deux espaces", ""]), [" deux espaces"])
    }

    func testDataWithNoSpaceAfterTheColonIsRead() {
        XCTAssertEqual(frames(of: ["data:collé", ""]), ["collé"])
    }

    func testFieldsThisServerDoesNotUseAreIgnored() {
        XCTAssertEqual(
            frames(of: ["id: 42", "event: change", "retry: 3000", "data: x", ""]),
            ["x"]
        )
    }

    func testConsecutiveFramesDoNotBleedIntoEachOther() {
        XCTAssertEqual(
            frames(of: ["data: un", "", "data: deux", ""]),
            ["un", "deux"]
        )
    }

    func testAnUnterminatedFrameIsNotEmitted() {
        // Le flux coupé au milieu d'une trame ne doit pas livrer une moitié de
        // JSON ; la reconnexion repart d'une trame entière.
        XCTAssertEqual(frames(of: ["data: {\"entity\":\"ta"]), [])
    }
}
