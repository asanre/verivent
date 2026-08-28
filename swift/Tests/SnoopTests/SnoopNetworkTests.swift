import XCTest
@testable import Snoop

/// Covers the `URLSession` capture path end to end on a simulator: a real request goes through
/// `SnoopURLProtocol`, into the Kotlin bridge, and comes back out of the embedded viewer's
/// `/api/entries` as a network entry.
///
/// The traffic is aimed at the viewer's own server — it is a real localhost HTTP endpoint, so no
/// outside network is needed in CI. Unknown paths answer 404, which conveniently gives each test a
/// private path to scope its assertions to: the store is a process-wide singleton.
final class SnoopNetworkTests: XCTestCase {

    private var viewer: URLComponents!

    override func setUp() async throws {
        try await super.setUp()
        viewer = try await startViewer()
    }

    override func tearDown() {
        Snoop.stopWebViewer()
        super.tearDown()
    }

    func testInterceptedRequestIsCaptured() async throws {
        let session = makeSession()
        let path = "/probe-basic"
        _ = try await session.data(from: try viewerURL(path: path))

        let entries = try await networkEntries(path: path)
        // Exactly one: the request we replay through the inner session carries a marker that keeps
        // `canInit` from grabbing it a second time.
        XCTAssertEqual(entries.count, 1, "an intercepted request must be captured exactly once")
        let entry = try XCTUnwrap(entries.first)
        XCTAssertEqual(entry["method"] as? String, "GET")
        XCTAssertEqual(entry["status"] as? Int, 404)
        XCTAssertEqual(entry["state"] as? String, "complete")
        XCTAssertNotNil(entry["durationMillis"], "a completed call should carry its duration")
        // Foundation only exposes the negotiated protocol through URLSessionTaskMetrics.
        XCTAssertEqual(entry["protocol"] as? String, "http/1.1")
    }

    func testHeaderAndQueryRedactionRunOnTheSharedEngine() async throws {
        let session = makeSession(redactHeader: { $0.lowercased() == "x-custom" })
        let path = "/probe-redaction"
        var request = URLRequest(url: try viewerURL(
            path: path,
            extraQuery: [URLQueryItem(name: "api_key", value: "supersecret")]
        ))
        request.setValue("Bearer nope", forHTTPHeaderField: "Authorization")
        request.setValue("also-secret", forHTTPHeaderField: "X-Custom")
        _ = try await session.data(for: request)

        let entries = try await networkEntries(path: path)
        let entry = try XCTUnwrap(entries.first)

        // The built-in query-parameter list is snoop-core's, not a Swift reimplementation.
        let url = try XCTUnwrap(entry["url"] as? String)
        XCTAssertTrue(url.contains("api_key=██"), "expected a redacted api_key in \(url)")
        XCTAssertFalse(url.contains("supersecret"), "the secret must never reach the store")

        let headers = try XCTUnwrap(entry["requestHeaders"] as? [[String: Any]])
        let byName = Dictionary(uniqueKeysWithValues: headers.compactMap { header -> (String, [String: Any])? in
            guard let name = header["name"] as? String else { return nil }
            return (name.lowercased(), header)
        })
        // Authorization comes free with the engine's defaults; X-Custom is the host's own predicate.
        XCTAssertEqual(byName["authorization"]?["value"] as? String, "██")
        XCTAssertEqual(byName["authorization"]?["redacted"] as? Bool, true)
        XCTAssertEqual(byName["x-custom"]?["value"] as? String, "██")
    }

    func testFilteredRequestIsNotCaptured() async throws {
        let path = "/probe-filtered"
        let session = makeSession(keepRequest: { $0.path != path })
        _ = try await session.data(from: try viewerURL(path: path))

        let entries = try await networkEntries(path: path)
        XCTAssertTrue(entries.isEmpty, "a filtered request must never reach the store")
    }

    func testRequestBodyIsCaptured() async throws {
        let session = makeSession()
        let path = "/probe-body"
        var request = URLRequest(url: try viewerURL(path: path))
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = Data(#"{"hello":"world"}"#.utf8)
        _ = try await session.data(for: request)

        let entries = try await networkEntries(path: path)
        let entry = try XCTUnwrap(entries.first)
        XCTAssertEqual(entry["method"] as? String, "POST")
        // URLSession turns `httpBody` into a stream before the protocol sees it, so this only
        // passes because the stream is drained and re-attached.
        XCTAssertEqual(entry["requestBody"] as? String, #"{"hello":"world"}"#)
    }

    func testFormBodySecretsAreRedacted() async throws {
        let session = makeSession()
        let path = "/probe-form"
        var request = URLRequest(url: try viewerURL(path: path))
        request.httpMethod = "POST"
        request.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")
        request.httpBody = Data("user=ana&password=hunter2".utf8)
        _ = try await session.data(for: request)

        let entries = try await networkEntries(path: path)
        let entry = try XCTUnwrap(entries.first)
        XCTAssertEqual(entry["requestBody"] as? String, "user=ana&password=██")
    }

    func testTransportFailureIsRecorded() async throws {
        let session = makeSession()
        let path = "/probe-failure"
        // Port 9 is discard: nothing is listening, so this fails at the transport.
        let url = try XCTUnwrap(URL(string: "http://127.0.0.1:9\(path)"))
        _ = try? await session.data(from: url)

        let entries = try await networkEntries(path: path)
        let entry = try XCTUnwrap(entries.first)
        XCTAssertEqual(entry["state"] as? String, "failed")
        XCTAssertNotNil(entry["error"], "a transport failure should carry its message")
    }

    func testEachRedirectHopGetsItsOwnEntry() async throws {
        // The hop lands on the viewer itself, which answers 200 — so a working redirect leaves two
        // rows, and a broken one hangs instead of returning.
        let target = try viewerURL(path: "/api/entries")
        let server = try LocalRedirectServer(location: target.absoluteString, port: 9396)
        server.start()
        defer { server.stop() }

        let session = makeSession()
        let path = "/probe-redirect"
        let url = try XCTUnwrap(URL(string: "http://127.0.0.1:9396\(path)"))
        let (_, response) = try await session.data(from: url)

        XCTAssertEqual(
            (response as? HTTPURLResponse)?.statusCode,
            200,
            "the redirect should be followed through to the target"
        )

        let hop = try await networkEntries(path: path)
        XCTAssertEqual(hop.count, 1, "the 302 itself should be one row")
        XCTAssertEqual(hop.first?["status"] as? Int, 302)
        XCTAssertEqual(hop.first?["state"] as? String, "complete")

        let followed = try await networkEntries(path: "/api/entries")
        XCTAssertFalse(followed.isEmpty, "the followed hop should be captured as its own row")
    }

    // MARK: - Helpers

    private func makeSession(
        redactHeader: ((String) -> Bool)? = nil,
        keepRequest: ((Snoop.Request) -> Bool)? = nil
    ) -> URLSession {
        let configuration = URLSessionConfiguration.ephemeral
        Snoop.installNetworkInterceptor(
            on: configuration,
            redactHeader: redactHeader,
            keepRequest: keepRequest
        )
        return URLSession(configuration: configuration)
    }

    /// Port 9395 rather than the default: the simulator shares the host loopback, so a sample app
    /// left running would squat 9394. The bind is asynchronous, so a served response is the only
    /// trustworthy readiness signal.
    private func startViewer() async throws -> URLComponents {
        Snoop.stopWebViewer()
        let base = Snoop.startWebViewer(.local, port: 9395)
        let components = try XCTUnwrap(URLComponents(string: base))
        var probe = components
        probe.path = "/api/entries"
        let url = try XCTUnwrap(probe.url)

        for _ in 1...50 {
            if let (_, response) = try? await URLSession.shared.data(from: url),
               (response as? HTTPURLResponse)?.statusCode == 200 {
                return components
            }
            try await Task.sleep(nanoseconds: 100_000_000)
        }
        XCTFail("the viewer never served a response")
        return components
    }

    /// Keeps the token the viewer URL already carries — every bind is gated, loopback included.
    private func viewerURL(path: String, extraQuery: [URLQueryItem] = []) throws -> URL {
        var components = try XCTUnwrap(viewer)
        components.path = path
        components.queryItems = (components.queryItems ?? []) + extraQuery
        return try XCTUnwrap(components.url)
    }

    /// Read back over `URLSession.shared`, which carries no interceptor, so reading the timeline
    /// never shows up in it.
    private func networkEntries(path: String) async throws -> [[String: Any]] {
        var components = try XCTUnwrap(viewer)
        components.path = "/api/entries"
        let url = try XCTUnwrap(components.url)
        let (data, _) = try await URLSession.shared.data(from: url)
        let entries = try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [[String: Any]])
        return entries.filter { $0["type"] as? String == "network" && $0["path"] as? String == path }
    }
}
