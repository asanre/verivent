# Agent skills

The repository is also a plugin marketplace for coding agents. Its `verivent` plugin holds one
skill so far:

| Skill | What it does |
|---|---|
| `analytics-events` | Reads the events your app wrote to the console with [`logTo`](integration-analytics-facade.md#console-lines), compares them with the tracking plan and the ticket, and writes the report for the pull request: event, channel, expected, observed and issues. |

## Install

In Claude Code:

```text
/plugin marketplace add asanre/verivent
/plugin install verivent@verivent
```

The skill then runs on its own when you ask about your analytics ("check the events in this
logcat against the plan", "write the analytics section for the PR"), or by name as
`/verivent:analytics-events`.

Other agents that read `SKILL.md` folders can take
[`plugins/verivent/skills/analytics-events`](https://github.com/asanre/verivent/tree/main/plugins/verivent/skills/analytics-events)
as it is: copy the folder wherever your agent looks for skills.

It needs `python3`. `node` is only needed to check the events again: against a plan file other than
the one the app had loaded, or for events logged before a plan was.

## analytics-events

Turn on the console lines in a debug build:

```kotlin
val sink = AnalyticsSink {
    logTo()
}
```

```swift
Verivent.configure(logToConsole: true)
```

Run the flow, then hand the agent the lines: a logcat dump
(`adb logcat -d -v threadtime -s VeriventEvent`), the iOS unified log
(`log show --predicate 'eventMessage BEGINSWITH "VeriventEvent"'`, or `log stream` while you use
the app), or the Xcode console. Point it at the ticket too, if you want the report to say what the
ticket asked for. The agent can also run the commands itself when it can reach the device.

The skill keeps the verdicts the device gave: they are the ones the inspector showed, computed on
the values as your code passed them. It only checks the events again,
with the [npm package](validator-npm.md), when you give it a plan whose version differs from the one
the app had loaded, or when some events were logged before a plan was. Redacted values stay `██` in
the report.

The report is a Markdown table, ready for the pull request:

| Event | Channel | Expected | Observed | Issues | Ticket |
|---|---|---|---|---|---|
| `purchase` | firebase | value, currency, transaction_id, items[].item_id, items[].quantity (integer) | value=42.5, currency=EUR, user_email=██, items=2 items | `transaction_id · Required, but not sent`<br>`user_email · Not in the plan for this event`<br>`items[*].quantity · Expected integer, got string · 1 of 2` | Differs from the ticket: expected transaction_id, observed none |
| `begin_checkout` | server-side | value, currency | — | — | Not observed |
