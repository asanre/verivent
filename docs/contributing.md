# Contributing

Thanks for helping improve Snoop.

## Build & check

Everything runs through Gradle. The one command that must stay green:

```bash
./gradlew check
```

It compiles every module (Android + iOS), runs the tests, and — via `:snoop-parity-check` —
compiles a shared source file against both the real and the no-op modules, so any API drift between
them fails the build. Run it on macOS so the Kotlin/Native iOS targets compile.

Useful targeted tasks:

```bash
./gradlew :sample:composeApp:assembleDebug                        # the Android sample
./gradlew :sample:composeApp:linkDebugFrameworkIosSimulatorArm64  # the iOS framework
./gradlew dokkaGenerate                                           # API reference into build/dokka/html
```

## The Swift package

`swift/` and `sample/iosApp` build outside Gradle, so nothing in `./gradlew check` covers them
locally — the `swift` job in CI does. Assemble the XCFramework first: `swift/Package.swift` links it
by path, and SwiftPM resolves the package graph before anything else runs, so a missing framework
fails at resolution rather than at link time.

```bash
./gradlew :snoop-native:assembleSnoopKitReleaseXCFramework
SIM=$(xcrun simctl list devices available -j | jq -er '[.devices[][] | select(.name | test("^iPhone"))] | first | .udid')
(cd swift && xcodebuild test -scheme Snoop -destination "id=$SIM")            # the bridge tests
(cd sample/iosApp && xcodebuild build -scheme SnoopSample -destination "id=$SIM")
(cd sample/composeApp/iosApp && xcodebuild build -scheme ComposeSample -destination "id=$SIM")
```

The simulator is looked up rather than named because `-destination 'name=iPhone 16'` fails unless
the OS is pinned too, and the pair that exists differs from machine to machine.

Two things that bite:

- **A stale `snoop-native/build/`.** The tests link whatever XCFramework is sitting there, so an old
  one passes while testing nothing current. Assemble before you trust a green run.
- **`has been modified since the module file was built`.** Xcode's module cache still holds the
  previous XCFramework. Delete the project's folder under `~/Library/Developer/Xcode/DerivedData`
  and run again.

## Conventions

- **No-op parity is mandatory.** Every public symbol in `snoop`/`snoop-ktor`
  has a matching declaration in its `-no-op` module. When you change a public API, update the mirror
  and the references in `snoop-parity-check/src/ParityCheck.kt`.
- **One concept per file**, named after its primary declaration.
- **Idiomatic Kotlin** — immutable data, pure functions, exhaustive `when`.

## Docs

The site is MkDocs Material; pages live under `docs/`. Preview locally:

```bash
pip install mkdocs-material
mkdocs serve
```

Releasing to Maven Central is documented in [releasing.md](https://github.com/asanre/snoop/blob/main/docs/releasing.md).
