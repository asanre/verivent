import Foundation
import Verivent

/// The Developer screen's network buttons, the same on both samples.
enum DemoCall: String, CaseIterable, Identifiable {
    case get, notFound, postJson, postForm, secrets, resetToken, unreachable

    var id: String { rawValue }

    var title: String {
        switch self {
        case .get: return "GET a todo"
        case .notFound: return "GET something missing"
        case .postJson: return "POST a JSON body"
        case .postForm: return "POST a form with a password"
        case .secrets: return "GET with secrets in header and query"
        case .resetToken: return "GET a path with a reset token"
        case .unreachable: return "GET an unreachable host"
        }
    }

    fileprivate var request: URLRequest {
        switch self {
        case .get: return URLRequest(url: URL(string: "\(host)/todos/1")!)
        case .notFound: return URLRequest(url: URL(string: "\(host)/todos/does-not-exist")!)
        case .postJson: return post("application/json", #"{"title":"verivent","body":"demo","userId":1}"#)
        case .postForm: return post("application/x-www-form-urlencoded", "user=ana&password=hunter2")
        case .secrets:
            var request = URLRequest(url: URL(string: "\(host)/todos/1?api_key=supersecret")!)
            request.setValue("Bearer nope", forHTTPHeaderField: "Authorization")
            request.setValue("also-secret", forHTTPHeaderField: "X-Api-Key")
            return request
        case .resetToken: return URLRequest(url: URL(string: "\(host)/reset/9f8e7d6c")!)
        case .unreachable: return URLRequest(url: URL(string: "https://verivent.invalid.host.example/timeout")!)
        }
    }
}

private let host = "https://jsonplaceholder.typicode.com"

private func post(_ contentType: String, _ body: String) -> URLRequest {
    var request = URLRequest(url: URL(string: "\(host)/posts")!)
    request.httpMethod = "POST"
    request.setValue(contentType, forHTTPHeaderField: "Content-Type")
    request.httpBody = Data(body.utf8)
    return request
}

/// One `URLSession` with Verivent's interceptor installed on its configuration — the integration point
/// a real app has, and the only thing network capture needs.
final class SampleNetwork {

    static let shared = SampleNetwork()

    private let session: URLSession

    private init() {
        let configuration = URLSessionConfiguration.default
        // The same rules as the Compose sample's `install(Verivent) { … }`.
        Verivent.installNetworkInterceptor(
            on: configuration,
            redactHeader: { $0.lowercased() == "x-api-key" },
            redactPath: { $0.replacingOccurrences(of: #"^/reset/[^/]+"#, with: "/reset/██", options: .regularExpression) },
            keepRequest: { $0.path != "/health" }
        )
        session = URLSession(configuration: configuration)
    }

    func perform(_ call: DemoCall) {
        session.dataTask(with: call.request).resume()
    }

    /// The order the shop sends when it's paid, to a demo endpoint: network next to the purchase event.
    func placeOrder(_ cart: Cart, orderId: String) {
        session.dataTask(with: post("application/json", #"{"order":"\#(orderId)","items":\#(cart.size)}"#)).resume()
    }
}
