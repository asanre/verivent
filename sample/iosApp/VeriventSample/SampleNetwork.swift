import Foundation
import Snoop

/// The calls the Network section fires, aimed at the same endpoints as the Compose sample's
/// `Sampler` so both demos tell the same story.
enum SampleCall: String, CaseIterable, Identifiable {
    case todo
    case missing
    case createPost
    case formLogin
    case secrets
    case unreachable

    var id: String { rawValue }

    var title: String {
        switch self {
        case .todo: return "GET a todo"
        case .missing: return "GET something missing"
        case .createPost: return "POST a JSON body"
        case .formLogin: return "POST a form with a password"
        case .secrets: return "GET with secrets in header and query"
        case .unreachable: return "GET an unreachable host"
        }
    }

    /// What to look for in the timeline once it has been tapped.
    var hint: String {
        switch self {
        case .todo: return "200, pretty-printed JSON body"
        case .missing: return "404, shown in amber"
        case .createPost: return "201, with the request body captured"
        case .formLogin: return "password comes back as ██"
        case .secrets: return "Authorization, X-Api-Key and api_key all redacted"
        case .unreachable: return "a failed row carrying the error"
        }
    }

    fileprivate var request: URLRequest {
        switch self {
        case .todo:
            return URLRequest(url: URL(string: "https://jsonplaceholder.typicode.com/todos/1")!)
        case .missing:
            return URLRequest(
                url: URL(string: "https://jsonplaceholder.typicode.com/todos/does-not-exist")!
            )
        case .createPost:
            var request = URLRequest(url: URL(string: "https://jsonplaceholder.typicode.com/posts")!)
            request.httpMethod = "POST"
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            request.httpBody = Data(#"{"title":"snoop","body":"hello","userId":1}"#.utf8)
            return request
        case .formLogin:
            var request = URLRequest(url: URL(string: "https://jsonplaceholder.typicode.com/posts")!)
            request.httpMethod = "POST"
            request.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")
            request.httpBody = Data("user=ana&password=hunter2".utf8)
            return request
        case .secrets:
            var request = URLRequest(
                url: URL(string: "https://jsonplaceholder.typicode.com/todos/1?api_key=supersecret")!
            )
            request.setValue("Bearer nope", forHTTPHeaderField: "Authorization")
            request.setValue("also-secret", forHTTPHeaderField: "X-Api-Key")
            return request
        case .unreachable:
            return URLRequest(url: URL(string: "https://snoop.invalid.host.example/timeout")!)
        }
    }
}

/// One `URLSession` with Snoop's interceptor installed on its configuration — the integration point
/// a real app has, and the only thing network capture needs.
final class SampleNetwork {

    static let shared = SampleNetwork()

    private let session: URLSession

    private init() {
        let configuration = URLSessionConfiguration.default
        // The same shape as the Compose sample's `install(Snoop) { … }`: a header rule on top of the
        // built-in ones, and a filter that keeps health checks out of the timeline.
        Snoop.installNetworkInterceptor(
            on: configuration,
            redactHeader: { $0.lowercased() == "x-api-key" },
            keepRequest: { $0.path != "/health" }
        )
        session = URLSession(configuration: configuration)
    }

    func perform(_ call: SampleCall, then note: @escaping (String) -> Void) {
        session.dataTask(with: call.request) { data, response, error in
            let outcome: String
            if let error = error {
                outcome = "failed — \(error.localizedDescription)"
            } else {
                let status = (response as? HTTPURLResponse)?.statusCode ?? 0
                outcome = "\(status), \(data?.count ?? 0) bytes"
            }
            DispatchQueue.main.async { note("\(call.title) → \(outcome)") }
        }.resume()
    }
}
