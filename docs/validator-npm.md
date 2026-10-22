# Validator in the browser

The tracking plan validator is also an npm package, built from the same Kotlin sources as the
libraries. A web tool checks events with the same rules and the same messages as the device: what
the inspector says on the phone, the browser says too.

It's attached to every GitHub Release from 0.9.0 on as `verivent-<version>.tgz`; it isn't on the npm
registry.

```bash
npm install https://github.com/asanre/verivent/releases/download/v0.9.0/verivent-0.9.0.tgz
```

The package is an ES module with TypeScript definitions:

```ts
import { Plan } from "verivent";

const plan = new Plan(planJson);          // throws an Error with the device's message if it's invalid
plan.version;                             // "1.4.0"
plan.events;                              // [{ name, channel }]

// One event, from its properties as a JSON object. null when the plan doesn't define it.
plan.check("play", "firebase", JSON.stringify({ tracks }))
    ?.map((issue) => issue.line);         // ["tracks[*].duration_ms · Expected integer, got string · 60 of 60"]

// A session exported from the inspector or the web viewer: a JSON array of analytics events.
// Console lines carry the same events one per line; join their pieces and wrap them in [ ] first.
for (const event of plan.checkSession(exportJson)) {
    event.verdict;                        // "complies", "breaks" or "not_in_plan"
    event.issues;                         // [{ path, message, count, total, line }]
    event.untyped;                        // exported before 0.9: properties only checked for presence
}
```

`count` and `total` are set when the path goes through a list: `count` of the `total` items break
it. A redacted property in an exported session is only checked for its type, as on the device.

The JS target only exists for this package: apps written in Kotlin/JS aren't a supported target of
the `verivent` artifact, and the no-op modules don't have it.
