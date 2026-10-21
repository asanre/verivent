// swift-tools-version: 5.9
//
// Local development manifest: links the XCFramework straight out of the Gradle build, so
// `./gradlew :verivent-native:assembleVeriventKitReleaseXCFramework` then `xcodebuild test` from this
// directory exercises the real binary. The published manifest is rendered from
// Package.swift.template and points at a release asset instead.
import PackageDescription

let package = Package(
    name: "Verivent",
    platforms: [.iOS(.v14)],
    products: [
        .library(name: "Verivent", targets: ["Verivent"]),
        .library(name: "VeriventUITesting", targets: ["VeriventUITesting"]),
    ],
    targets: [
        .binaryTarget(
            name: "VeriventKit",
            path: "../verivent-native/build/XCFrameworks/release/VeriventKit.xcframework"
        ),
        .target(name: "Verivent", dependencies: ["VeriventKit"]),
        // For UI test targets: only XCTest and URLSession, nothing of Verivent in the test runner.
        .target(name: "VeriventUITesting"),
        .testTarget(name: "VeriventTests", dependencies: ["Verivent"]),
        .testTarget(name: "VeriventUITestingTests", dependencies: ["Verivent", "VeriventUITesting"]),
    ]
)
