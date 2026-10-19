# Verivent

[![Maven Central](https://img.shields.io/maven-central/v/io.github.asanre/verivent)](https://central.sonatype.com/namespace/io.github.asanre)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0)
![Platforms](https://img.shields.io/badge/platforms-Android%20%7C%20iOS-informational)

> Formerly **Snoop**: versions up to 0.7.0 were published as `io.github.asanre:snoop`.

**Verivent** is an on-device inspector for **network traffic and analytics events**, built for Compose
Multiplatform. Both streams land on one timeline, so you can see a request and the events around it
without leaving the app — on Android and iOS.

Network inspection in KMP is a solved problem, but nothing unifies network + analytics in one
Compose surface, and no tool inspects analytics events independently of the vendor SDK. Verivent does
both.

## Features

- **Unified timeline** — network calls and analytics events interleaved, filterable by type and
  channel, searchable, and collapsible into per-screen groups opened by the analytics event of your
  choice (`screen_view` by default).
- **Rich detail** — headers, pretty-printed JSON bodies, timing, cURL export; event parameters with
  channel and annotations.
- **Redaction at capture** — headers, query parameters and form-body fields are replaced with a
  visible `██` before anything reaches the store; raw secrets never land in memory.
- **Web viewer** — an embedded HTTP server streams the same timeline to a desktop browser over SSE.
- **Live notification** — a persistent notification lists the latest captures and reopens the
  inspector (Android and iOS).
- **No-op mirrors** — release builds swap in API-identical stubs that capture nothing and drop the
  UI/retention overhead.

## Modules

`verivent` carries the core and the analytics sink; add `verivent-ui` for the on-device inspector and one
adapter per HTTP client.

| Artifact | What it does |
|---|---|
| `verivent` | Models, store, redaction, the web viewer and the `log(name, channel, properties, sections)` analytics sink |
| `verivent-ui` | Compose Multiplatform inspector UI and the live notifications |
| `verivent-ktor` | Ktor client plugin that captures traffic |
| `verivent-*-no-op` | Empty mirrors with the identical API for release builds |

`verivent-ui` is the only artifact that depends on Compose — leave it out and you get the web viewer
with no Compose in your graph.

See [Getting started](getting_started.md) to install, then [Integration](integration.md) to open the
inspector. Apps written entirely in Swift start from [Native iOS](native-ios.md) instead.
