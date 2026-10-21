import Verivent
import XCTest
@testable import VeriventUITesting

/// `VeriventFlow` against the real server, which the package's own viewer is: the events are logged
/// here instead of by an app, but every check goes over HTTP as from a UI test.
final class VeriventFlowTests: XCTestCase {

    private let server = Server(port: 9398, token: "ui-test")

    override func setUp() async throws {
        try await super.setUp()
        Verivent.startWebViewer(.local, port: server.port, token: server.token)
        for _ in 0..<40 where !server.isUp() { try await Task.sleep(nanoseconds: 50_000_000) }
    }

    override func tearDown() {
        Verivent.stopWebViewer()
        super.tearDown()
    }

    private let baseline = Data("""
        { "flow": "checkout", "steps": [
          { "name": "pay", "events": [ { "name": "purchase", "channel": "firebase",
            "schema": { "properties": { "value": { "const": 9.5 }, "transaction_id": { "const": "{{orderId}}" } } } } ] } ] }
        """.utf8)

    private func start() throws -> VeriventFlow {
        _ = try server.post("/api/baseline/flow", query: ["timeout": "200"], body: baseline, timeout: 10)
        return VeriventFlow(server: server, timeout: 0.2)
    }

    func testAStepWhoseEventsMatchPasses() throws {
        let flow = try start()
        try flow.step("pay") {
            Verivent.log("purchase", channel: "firebase", properties: ["value": 9.5, "transaction_id": "T-1"])
            flow.bind("orderId", "T-1")
        }
        try verdict(server.post("/api/baseline/finish", body: flow.variables(), timeout: 10))
    }

    func testAMismatchThrowsTheReport() throws {
        let flow = try start()
        XCTAssertThrowsError(try flow.step("pay") {
            Verivent.log("purchase", channel: "firebase", properties: ["value": 12, "transaction_id": "T-2"])
            flow.bind("orderId", "T-2")
        }) { error in
            XCTAssertEqual(
                (error as? BaselineMismatch)?.report,
                "checkout › pay\n  ✕ purchase · firebase\n      value · Not the expected value · expects 9.5"
            )
        }
    }

    func testAStepTheBaselineDoesntHaveIsRefused() throws {
        let flow = try start()
        XCTAssertThrowsError(try flow.step("browse") {}) { error in
            XCTAssertEqual((error as? VeriventUsageError)?.description, "400: the baseline of checkout has no step browse")
        }
    }

    func testABoundValueJsonCantCarryIsRefusedInsteadOfCrashing() throws {
        let flow = try start()
        flow.bind("orderId", Date())
        XCTAssertThrowsError(try flow.variables()) { XCTAssertTrue($0 is VeriventUsageError) }
    }

    func testOneEventIsCheckedOnItsOwn() throws {
        Verivent.log("purchase", channel: "firebase", properties: ["currency": "EUR"])
        let check = VeriventEventCheck(server: server, event: ["name": "purchase", "channel": "firebase", "timeout": 200], timeout: 0.2)

        XCTAssertNoThrow(try check.shouldHave("currency", "EUR"))
        XCTAssertThrowsError(try check.shouldHave("currency", "USD"))
    }
}
