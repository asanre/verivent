# Verivent

[![Maven Central](https://img.shields.io/maven-central/v/io.github.asanre/verivent)](https://central.sonatype.com/namespace/io.github.asanre)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0)
![Platforms](https://img.shields.io/badge/platforms-Android%20%7C%20iOS-informational)

> Formerly **Snoop**: versions up to 0.7.0 were published as `io.github.asanre:snoop`.

**Your mobile analytics, verified on the device.** Verivent checks every analytics event your app
sends against your team's tracking plan, on the phone, with the real values and without uploading
anything. Built for Kotlin Multiplatform (Android and iOS), with a Swift Package for native iOS apps.

A broken event shows up wherever you are testing: in the app, in a desktop browser, in the console
or in a CI test. Issues name the property and what the plan expected, never the value sent:
`items[*].quantity · Expected integer, got string · 1 of 2`.

## Features

- **Tracking plan on the device** — a JSON Schema subset per event and channel, nested as deep as
  your values: `type`, `enum`, `const`, `pattern`, `required`, `additionalProperties`, `items`,
  `minItems` and `contains`. Checked at capture, on the values your code passed.
- **Vendor-agnostic** — your analytics facade calls `log`; Verivent never talks to an analytics SDK.
- **Everywhere you look** — the on-device inspector, the web viewer on your Mac, one JSON line per
  event in the console, and one export format shared by all of them.
- **Tests** — compare a session with a recorded [baseline](testing.md#baseline), or ask the device
  [over HTTP](testing.md#over-http) whether any event broke the plan.
- **The same validator in the browser** — an [npm package](validator-npm.md) with the device's
  rules and messages.
- **Network on the same timeline** — Ktor calls (and `URLSession` on native iOS) next to the events
  they should have produced, with headers, bodies, timing and cURL export.
- **Redaction at capture** — sensitive values are replaced with a visible `██` before anything
  reaches the store; a redacted property keeps its type, and the plan only checks that.
- **No-op mirrors** — release builds swap in API-identical stubs that capture nothing.

## Modules

`verivent` carries the analytics sink, the tracking plan, the baseline and the web viewer; add
`verivent-ui` for the on-device inspector and one adapter per HTTP client.

| Artifact | What it does |
|---|---|
| `verivent` | The analytics sink, the tracking plan, the baseline, the web viewer, the store and redaction |
| `verivent-ui` | Compose Multiplatform inspector UI and the live notifications |
| `verivent-ktor` | Ktor client plugin that captures traffic |
| `verivent-*-no-op` | Empty mirrors with the identical API for release builds |

`verivent-ui` is the only artifact that depends on Compose — leave it out and you get the web viewer
with no Compose in your graph.

See [Getting started](getting_started.md) to install, then [Integration](integration.md) to open the
inspector. Apps written entirely in Swift start from [Native iOS](native-ios.md) instead.
