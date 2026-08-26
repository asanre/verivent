import Foundation
import SnoopKit

/// Snoop for native iOS hosts: a vendor-agnostic analytics sink plus the web viewer.
///
/// Wraps the Kotlin bridge so nothing Objective-C shaped reaches the call site — no `KotlinBoolean`
/// in your closures, no `.shared` singletons, no `Int32` ports.
public enum Snoop {

    // MARK: - Events

    /// An event as you described it, handed to the `keepEvent` filter.
    public struct Event {
        public let name: String
        public let channel: String
        public let properties: [String: Any]

        fileprivate init(_ raw: SnoopEvent) {
            self.name = raw.name
            self.channel = raw.channel
            self.properties = raw.properties as? [String: Any] ?? [:]
        }
    }

    /// Install filtering, redaction and timeline grouping. Grouping parameters left out keep their
    /// current values; a closure left out is cleared, so pass both when adjusting one.
    ///
    /// Both closures are retained until the next `configure` call — for the life of the process in
    /// practice — so capture `[weak self]` if one reaches into an object you expect to go away.
    ///
    /// - Parameters:
    ///   - groupByEvent: event that opens a new group in the viewer; `nil` turns grouping off.
    ///     Applied as events are captured, so it does not regroup what was captured before.
    ///   - groupLabelProperty: property of that event whose value labels the group. When `nil`, or
    ///     absent from the event, the group is labelled with the event's own name.
    public static func configure(
        keepEvent: ((Event) -> Bool)? = nil,
        redactProperty: ((String) -> Bool)? = nil,
        groupByEvent: String? = SnoopEvents.shared.groupByEvent,
        groupLabelProperty: String? = SnoopEvents.shared.groupLabelProperty
    ) {
        SnoopEvents.shared.configure(
            keepEvent: keepEvent.map { predicate in
                { raw in KotlinBoolean(bool: predicate(Event(raw))) }
            },
            redactProperty: redactProperty.map { predicate in
                { key in KotlinBoolean(bool: predicate(key)) }
            },
            groupByEvent: groupByEvent,
            groupLabelProperty: groupLabelProperty
        )
    }

    /// Record one event. Wire this to your own analytics facade, alongside the real SDK call.
    public static func log(_ name: String, channel: String, properties: [String: Any] = [:]) {
        SnoopEvents.shared.log(name: name, channel: channel, properties: properties)
    }

    // MARK: - Web viewer

    /// Where the viewer listens. `.lan` and `.host` are reachable off-device; `.local` is loopback
    /// only. All of them are token-gated — loopback is shared with every other app on the device.
    public enum Bind {
        case local
        case lan
        case host(String)

        fileprivate var raw: any SnoopViewerBind {
            switch self {
            case .local: return SnoopViewerBindLocal.shared
            case .lan: return SnoopViewerBindLan.shared
            case .host(let address): return SnoopViewerBindHost(address: address)
            }
        }
    }

    /// Start the viewer (idempotent). Returns the URL to open, token included.
    ///
    /// The bind happens asynchronously, so a failure (port taken) shows up in the device console
    /// afterwards rather than here; `isWebViewerRunning` flips back to false when it does.
    @discardableResult
    public static func startWebViewer(
        _ bind: Bind = .local,
        port: Int? = nil,
        token: String? = nil
    ) -> String {
        SnoopViewer.shared.start(
            bind: bind.raw,
            port: port.map(Int32.init) ?? SnoopViewer.shared.defaultPort,
            token: token
        )
    }

    public static func stopWebViewer() {
        SnoopViewer.shared.stop()
    }

    public static var isWebViewerRunning: Bool { SnoopViewer.shared.isRunning }

    /// The access token every request must carry as `?t=`, or nil when no viewer is running.
    /// Already baked into the URL `startWebViewer` returns; exposed for hand-built requests.
    public static var webViewerToken: String? { SnoopViewer.shared.token }

    /// The URL to reach this device from another machine, or nil on a `.local` bind.
    public static var lanURL: String? { SnoopViewer.shared.lanUrl() }
}
