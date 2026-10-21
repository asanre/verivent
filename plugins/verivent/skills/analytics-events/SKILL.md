---
name: analytics-events
description: Check the analytics events an app sent, as Verivent logged them to the console, against the tracking plan and the ticket, and write the report for the pull request (event, channel, expected, observed, issues). Use whenever a developer wants to verify, review or report on analytics/tracking events from logcat, `adb logcat -s VeriventEvent`, the iOS unified log (`log stream`, `log show`, Console.app) or the Xcode console; when they paste lines holding `{"type":"analytics"` or pieces like `[12 1/3]`; when they ask whether a feature's events match the tracking plan or a Jira/Linear ticket; or when they want the analytics section of a PR. Also in Spanish — "comprueba la analítica", "valida los eventos", "revisa el logcat", "informe de analítica para la PR", "¿cumplen los eventos el plan?". Use it even if Verivent isn't named, as long as the console lines carry the VeriventEvent tag.
---

# Analytics events report

Verivent, when the app calls `logTo()` (Kotlin) or `Verivent.configure(logToConsole: true)` (Swift), writes every analytics event it captures as one JSON line in the console. The device has already checked that event against the tracking plan it had loaded, so each line carries its verdict and the validator's own messages. Your job is to collect those lines, trust or re-check the verdicts, put them next to what the ticket asked for, and write a report that a reviewer can read in the PR.

Two things are easy to get wrong, and both mislead a reviewer:

- **The issue messages are Verivent's validator's, verbatim.** They are the same strings the inspector, the web viewer and the npm package show (`transaction_id · Required, but not sent`, `items[*].quantity · Expected integer, got string · 1 of 2`). Don't paraphrase, translate or merge them, even if the report is in another language. A reviewer searching for one of them should find exactly what the app shows.
- **Redacted values stay redacted.** A property the app redacts arrives as `"value":"██","redacted":true`. Show `██`. Don't guess the value from the code, the ticket or anywhere else: the app redacts it because it's personal data.

## 1. Get the lines

Use what the user gives you: a file, pasted text, or a device you can reach. If you need to capture them yourself:

| Where | Command |
|---|---|
| Android (emulator or device) | `adb logcat -d -v threadtime -s VeriventEvent > capture.txt` (dumps what is in the buffer; `adb logcat -c` first to start clean) |
| iOS simulator | `xcrun simctl spawn booted log show --last 15m --predicate 'eventMessage BEGINSWITH "VeriventEvent"' > capture.txt`, or `log stream` with the same predicate while the flow runs |
| iOS device | Console.app filtered by `VeriventEvent`, or `log collect` on the Mac, then `log show` on the archive |
| Xcode | copy the console output |

If there are no `VeriventEvent` lines, the app isn't writing them: ask the developer to turn on `logTo()` in the `AnalyticsSink { }` block, or pass `logToConsole: true` to `Verivent.configure`, in a debug build, and to run the flow again. Lines appear only for events captured while that was on.

## 2. Rebuild the events

```bash
python3 ${CLAUDE_SKILL_DIR}/scripts/read_lines.py capture.txt --session <tmp>/session.json
```

(If `${CLAUDE_SKILL_DIR}` isn't substituted in your agent, use the directory of this file.) Use the scratchpad or a temp directory for `session.json`, not the user's repo.

The script reads any of the formats above, joins the pieces of events too long for one console line (`[<id> <i>/<n>] `, joined by id), undoes the unified log's escaping, and prints JSON:

- `events[]`: `id`, `name`, `channel`, `planVersion`, `verdict`, `issues` (already as `property · message`) and the observed `properties` (`name`, `value`, `redacted`, `type`, and `size` for a list or object; long values are cut in this summary only).
- `verdict`: `complies` (checked, no issues), `breaks` (checked, with issues) or `not_checked` (no `planVersion`: either the device had no plan loaded, or its plan doesn't define this event — the line alone can't tell which).
- `notes`: what went wrong reading the capture — pieces that never completed, lines that aren't JSON, events from Verivent before 0.9 (no property types), events checked against different plan versions. Carry every note into the report: a missing event is a finding too.

Ids restart at 1 every time the app launches, so the same id can appear twice; the script keeps them apart. Don't deduplicate events by id.

`--session` writes the events as a Verivent session export, which the web viewer opens and the npm validator reads.

## 3. Decide whose verdict to report

The device's verdict is the one the app itself showed, computed on the values as the code passed them (a redacted one only by its type, as in a re-check). Keep it unless there's a reason not to:

| Situation | What to do |
|---|---|
| The user gave no plan, and the lines carry a `planVersion` | Report the device's verdicts, and say which plan version they come from. |
| The user gave a plan, and its `version` equals the lines' `planVersion` | Same: the device's verdicts. |
| The user gave a plan with another `version` (they want to know if the events meet the new plan), or some events are `not_checked` and a plan is available | Re-check with Verivent's validator (below). |
| Events are `not_checked` and there is no plan to re-check with | Report them as not checked, and say why it can't be settled: no plan loaded on the device, or the plan doesn't define them. Don't invent a verdict from your own reading of the schema. |

A plan is a JSON file with `version` and `events: [{name, channel, schema}]`. If the user says "the plan" without a path, look for it in their repo (often loaded with `Inspector.loadTrackingPlan` / `Verivent.loadTrackingPlan`) before asking.

### Re-checking with the validator

```bash
node ${CLAUDE_SKILL_DIR}/scripts/check_session.mjs --validator <dir> plan.json <tmp>/session.json
```

It runs Verivent's own validator (the npm package, from 0.9.0) over the session, so the messages are the device's. It prints `planVersion` and, per event, `verdict` (`complies`, `breaks` or `not_in_plan`), `issues` and `untyped`. If the plan file itself is broken, it prints the validator's error for it: report that and stop.

`<dir>` is the package. Find it in this order:

1. a `node_modules/verivent` already in the user's project (pass the project or that folder);
2. a Verivent checkout where `./gradlew :verivent:jsNodeProductionLibraryDistribution` was run: `verivent/build/dist/js/productionLibrary`;
3. the GitHub Release matching the app's Verivent version (0.9.0 or later). This downloads a file, so ask the user first: `npm install --prefix <tmp>/verivent https://github.com/asanre/verivent/releases/download/v<version>/verivent-<version>.tgz`, then pass `<tmp>/verivent`.

If none is possible (no node, no package, the user declines), report the device's verdicts and say the re-check wasn't done.

What the re-check can't see: a redacted value is only checked for its type (the device did the same), and events from Verivent before 0.9 (`untyped: true`) are only checked for which properties are present. Say so in the notes when it applies.

## 4. Read the ticket

The ticket can be a file, pasted text, or an issue you can fetch with a Jira or Linear tool. Pull out, per event: name, channel, the properties it should carry, and any value it pins down (an exact value, a format, "integer", "never sent"). Ignore the rest of the ticket.

For each event, compare the ticket with what was observed. The **Ticket** column only ever says one of:

- `Matches the ticket`
- `Not observed`: the ticket asks for it, and the capture doesn't have it.
- `Differs from the ticket: expected <what the ticket says>, observed <what was sent>`. Keep it to the differences, separated by `;` if there are several.
- `—`: the ticket doesn't mention this event.

These are this skill's own words, so write them in the report's language (`No observado`, …), the same way every time.

The plan and the ticket can disagree: the ticket asks for `screen_name = home` and the plan doesn't allow `home`. Report both facts (the validator's issue in **Issues**, `Matches the ticket` in **Ticket**) and add a note that the plan or the ticket has to change. Don't pick a winner.

## 5. Write the report

Markdown, ready to paste into the PR. Write it in English unless the user writes in another language or the repo's PRs are in another language. The validator's messages stay as they are in any case.

```markdown
### Analytics · <ticket key or flow name>

Plan **<version>** · <checked on the device | re-checked with Verivent's validator against `<plan file>`> · <Android | iOS> · <N> events: <a> comply, <b> break, <c> not in the plan, <d> not checked

| Event | Channel | Expected | Observed | Issues | Ticket |
|---|---|---|---|---|---|
| `purchase` | firebase | value, currency, transaction_id, items[].item_id, items[].quantity (integer) | value=42.5, currency=EUR, user_email=██, items=2 items | `transaction_id · Required, but not sent`<br>`user_email · Not in the plan for this event`<br>`items[*].quantity · Expected integer, got string · 1 of 2` | Differs from the ticket: expected transaction_id, observed none |
| `begin_checkout` | server-side | value, currency | — | — | Not observed |

**Notes**
- …
```

- **Rows**: one per event and channel. Collapse identical repeats into one row with `×N` after the name. Keep separate rows when the same event has different results, such as before and after the plan was loaded.
- **Expected**: what the plan requires (required properties, allowed values, types) plus anything extra the ticket asks. Keep it short.
- **Observed**: `name=value` for the properties, with `██` for redacted ones. For arrays and objects, give the size (`items=60 items`), not the JSON. An event's `sections` (default parameters, user properties) aren't part of the plan, neither on the device nor in a re-check: leave them out, or mention them as context, never as issues.
- **Issues**: one validator line per `<br>`, in backticks. Use `—` when there are none, `not in plan <version>` when the re-check says `not_in_plan`, and `not checked` when there is no verdict at all. Keep the last two apart in the header counts too: an event the plan doesn't define is a finding (add it to the plan or stop sending it), while an unchecked one is a gap in the evidence.
- **Notes**: the script's notes; plan/ticket disagreements; anything that wasn't verified (no re-check, untyped events, a capture that starts mid-flow); where the device and a re-check disagree.

The report states facts. Fixing the code isn't part of it, unless the user asks: then the issues tell you exactly what to change.
