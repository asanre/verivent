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

## Conventions

- **No-op parity is mandatory.** Every public symbol in `snoop-core`/`snoop-ktor`/`snoop-analytics`
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
