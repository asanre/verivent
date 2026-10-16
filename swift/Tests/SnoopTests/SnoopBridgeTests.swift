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

    func testTrackingPlanStampsIssuesAndRejectsAnInvalidPlan() async throws {
        Snoop.configure()
        try Snoop.loadTrackingPlan("""
        { "version": "1.0.0", "events": [ { "name": "plan_search", "channel": "firebase",
          "schema": { "required": ["search_term"] } } ] }
        """)
        defer { Snoop.clearTrackingPlan() }
        XCTAssertEqual(Snoop.trackingPlanVersion, "1.0.0")
        XCTAssertThrowsError(try Snoop.loadTrackingPlan("{}"))
        XCTAssertEqual(Snoop.trackingPlanVersion, "1.0.0", "a rejected plan leaves the current one in place")

        Snoop.log("plan_search", channel: "firebase")
        Snoop.log("plan_undefined", channel: "firebase")

        let search = try await fetchEntry(named: "plan_search")
        XCTAssertEqual(search["issues"] as? [String], ["missing required: search_term"])
        let undefined = try await fetchEntry(named: "plan_undefined")
        XCTAssertEqual(undefined["annotations"] as? [String], ["not in plan"])
    }

    // MARK: - Helpers

    private func fetchEntry(named name: String) async throws -> [String: Any] {
        let match = try await fetchEntries().first { $0["name"] as? String == name }
        return try XCTUnwrap(match, "no entry named \(name)")
    }

    /// Starts the viewer and reads the timeline back over HTTP, which exercises the embedded Ktor
    /// server on the simulator as well as the capture path.
    ///
    /// The same port every time, restart included. Not the default one: the simulator shares the
    /// host loopback, so a sample app left running would squat it. `start` still reports bind
    /// failures asynchronously, so a served response stays the only trustworthy readiness signal.
    private func fetchEntries() async throws -> [[String: Any]] {
        Snoop.stopWebViewer()
        let base = Snoop.startWebViewer(.local, port: 9395)
        // The URL carries the access token as `?t=`, so the path has to be swapped in rather
        // than appended — every bind is gated, loopback included.
        var components = try XCTUnwrap(URLComponents(string: base))
        components.path = "/api/entries"
        let url = try XCTUnwrap(components.url)

        for _ in 1...50 {
            if let (data, _) = try? await URLSession.shared.data(from: url),
               let entries = try? JSONSerialization.jsonObject(with: data) as? [[String: Any]] {
                return entries
            }
            try await Task.sleep(nanoseconds: 100_000_000)
        }
        XCTFail("the viewer never served a response")
        return []
    }
}
