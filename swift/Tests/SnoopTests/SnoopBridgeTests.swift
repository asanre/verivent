import XCTest
@testable import Snoop

/// Phase 0.3 of the native-iOS plan: proves the two assumptions the rest of the work rests on —
/// that the XCFramework is consumable as an SPM binaryTarget, and that `[String: Any]` survives the
/// crossing into Kotlin's `Map<String, Any?>` with mixed value types.
///
/// The store is a process-wide singleton, so assertions are scoped to each test's own event names
/// rather than to the whole timeline.
final class SnoopBridgeTests: XCTestCase {

    override func tearDown() {
        Snoop.stopWebViewer()
        super.tearDown()
    }

    func testMixedPropertyTypesSurviveTheBridge() async throws {
        Snoop.configure(redactProperty: { $0 == "token" })
        Snoop.log("purchase", channel: "firebase", properties: [
            "sku": "SKU-9",
            "value": 42.5,
            "count": 3,
            "first": true,
            "token": "secret",
        ])

        let event = try await fetchEntry(named: "purchase")
        XCTAssertEqual(event["type"] as? String, "analytics")
        XCTAssertEqual(event["channel"] as? String, "firebase")

        let properties = try XCTUnwrap(event["properties"] as? [[String: Any]])
        let byName = Dictionary(uniqueKeysWithValues: properties.compactMap { property -> (String, [String: Any])? in
            guard let name = property["name"] as? String else { return nil }
            return (name, property)
        })

        // Every Swift value type crosses into Kotlin and comes back as the string the store holds.
        XCTAssertEqual(byName["sku"]?["value"] as? String, "SKU-9")
        XCTAssertEqual(byName["value"]?["value"] as? String, "42.5")
        XCTAssertEqual(byName["count"]?["value"] as? String, "3")
        XCTAssertEqual(byName["first"]?["value"] as? String, "true")

        // Redaction runs on the shared Kotlin engine, not a Swift reimplementation.
        XCTAssertEqual(byName["token"]?["value"] as? String, "██")
        XCTAssertEqual(byName["token"]?["redacted"] as? Bool, true)
    }

    func testFilterRejectsEvents() async throws {
        Snoop.configure(keepEvent: { !$0.name.hasPrefix("debug_") })
        Snoop.log("debug_ping", channel: "internal")
        Snoop.log("checkout", channel: "amplitude")

        let names = try await fetchEntries().compactMap { $0["name"] as? String }
        XCTAssertTrue(names.contains("checkout"), "an allowed event should be captured")
        XCTAssertFalse(names.contains("debug_ping"), "a filtered event must never reach the store")
    }

    // MARK: - Helpers

    private func fetchEntry(named name: String) async throws -> [String: Any] {
        let match = try await fetchEntries().first { $0["name"] as? String == name }
        return try XCTUnwrap(match, "no entry named \(name)")
    }

    /// Starts the viewer and reads the timeline back over HTTP, which exercises the embedded Ktor
    /// server on the simulator as well as the capture path.
    ///
    /// A fresh port per attempt, because a stopped socket lingers in TIME_WAIT and `start` reports
    /// bind failures asynchronously — the only trustworthy readiness signal is a served response.
    private func fetchEntries() async throws -> [[String: Any]] {
        for _ in 1...5 {
            Snoop.stopWebViewer()
            let base = Snoop.startWebViewer(.local, port: Int.random(in: 20_000...60_000))
            guard let url = URL(string: "\(base)/api/entries") else { continue }

            for _ in 1...10 {
                if let (data, _) = try? await URLSession.shared.data(from: url),
                   let entries = try? JSONSerialization.jsonObject(with: data) as? [[String: Any]] {
                    return entries
                }
                try await Task.sleep(nanoseconds: 100_000_000)
            }
        }
        XCTFail("the viewer never served a response")
        return []
    }
}
