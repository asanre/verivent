import VeriventUITesting
import XCTest

/// The shop's analytics, sent by buying a desk lamp and checked against the baselines in
/// `sample/shared/baselines`, the same files the Compose sample's `CheckoutAnalyticsTest` checks.
/// `TEST_RUNNER_VERIVENT_RECORD_DIR=$PWD/../shared/baselines xcodebuild test …` records them again.
final class CheckoutAnalyticsTests: XCTestCase {

    private let app = XCUIApplication()
    private var verivent: VeriventApp!

    override func setUpWithError() throws {
        continueAfterFailure = false
        verivent = try VeriventApp.launch(app)
    }

    func testCheckoutSendsItsAnalytics() throws {
        try verivent.flow("checkout", bundle: bundle) { flow in
            buyTheDeskLamp()
            flow.bind("orderId", orderId())
        }
    }

    func testCheckoutSendsItsAnalyticsStepByStep() throws {
        try verivent.flow("checkout_steps", bundle: bundle) { flow in
            try flow.step("browse") { tap("catalog.item.SN-42") }
            try flow.step("buy") {
                tap("product.addToCart")
                tap("cart.checkout")
            }
            try flow.step("pay") {
                tap("payment.pay")
                flow.bind("orderId", orderId())
            }
        }
    }

    func testABrokenParameterNamesItsEventsAndPath() throws {
        try XCTSkipIf(recording, "compares a recorded baseline")
        // A baseline expecting a price the app no longer charges, as after a regression.
        let recorded = try String(contentsOf: XCTUnwrap(bundle.url(forResource: "checkout", withExtension: "json")), encoding: .utf8)
        let edited = recorded.replacingOccurrences(of: "\"const\": 39.9", with: "\"const\": 49.99")
        let broken = FileManager.default.temporaryDirectory.appendingPathComponent("broken")
        try FileManager.default.createDirectory(at: broken, withIntermediateDirectories: true)
        try edited.write(to: broken.appendingPathComponent("checkout_broken.json"), atomically: true, encoding: .utf8)

        XCTAssertThrowsError(try verivent.flow("checkout_broken", bundle: Bundle(path: broken.path), timeout: 1) { flow in
            buyTheDeskLamp()
            flow.bind("orderId", orderId())
        }) { error in
            let report = (error as? BaselineMismatch)?.report ?? "\(error)"
            for event in ["view_item", "add_to_cart", "begin_checkout", "purchase"] {
                XCTAssertTrue(report.contains("✕ \(event) · firebase\n      value · Not the expected value · expects 49.99"), report)
            }
        }
    }

    func testOneEventCheckedOnItsOwn() throws {
        tap("catalog.developer")
        tap("developer.loadPlan")
        app.navigationBars.buttons.element(boundBy: 0).tap()
        buyTheDeskLamp()

        try verivent.lastEvent("checkout_started", on: "gtm").shouldMatchPlan()
        try verivent.lastEvent("purchase", on: "firebase").shouldMatchPlan().shouldHave("currency", "EUR")
    }

    private func buyTheDeskLamp() {
        tap("catalog.item.SN-42")
        tap("product.addToCart")
        tap("cart.checkout")
        tap("payment.pay")
    }

    private func tap(_ identifier: String) {
        let element = app.descendants(matching: .any)[identifier].firstMatch
        XCTAssertTrue(element.waitForExistence(timeout: 5), "\(identifier) never showed")
        element.tap()
    }

    private func orderId() -> String {
        let label = app.staticTexts["confirmation.orderId"]
        XCTAssertTrue(label.waitForExistence(timeout: 5), "the confirmation never showed")
        return label.label
    }

    private var bundle: Bundle { Bundle(for: Self.self) }

    private var recording: Bool { ProcessInfo.processInfo.environment["VERIVENT_RECORD_DIR"] != nil }
}
