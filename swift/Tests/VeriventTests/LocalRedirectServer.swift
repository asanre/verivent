import Foundation
import Network

/// A one-route HTTP server that answers every request with a 302 to `location`, so the redirect
/// path can be exercised without reaching outside the machine.
final class LocalRedirectServer {

    private let listener: NWListener
    private let queue = DispatchQueue(label: "io.github.asanre.verivent.tests.redirect")
    private let location: String

    init(location: String, port: UInt16) throws {
        self.location = location
        let parameters = NWParameters.tcp
        parameters.allowLocalEndpointReuse = true
        listener = try NWListener(using: parameters, on: NWEndpoint.Port(rawValue: port)!)
    }

    func start() {
        let location = self.location
        let queue = self.queue
        listener.newConnectionHandler = { connection in
            connection.start(queue: queue)
            connection.receive(minimumIncompleteLength: 1, maximumLength: 8192) { _, _, _, _ in
                let response = "HTTP/1.1 302 Found\r\n"
                    + "Location: \(location)\r\n"
                    + "Content-Length: 0\r\n"
                    + "Connection: close\r\n\r\n"
                connection.send(
                    content: Data(response.utf8),
                    completion: .contentProcessed { _ in connection.cancel() }
                )
            }
        }
        listener.start(queue: queue)
    }

    func stop() {
        listener.cancel()
    }
}
