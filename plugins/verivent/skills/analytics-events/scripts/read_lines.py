#!/usr/bin/env python3
"""Rebuild the analytics events Verivent's `logTo` wrote to the console.

Reads logcat (any format), the iOS unified log (`log stream` / `log show`), the Xcode console or
pasted text. Each event is one JSON line, the same entry every Verivent export writes; one longer
than the console limit comes as pieces prefixed `[<id> <i>/<n>] ` that are joined here. Prints the
events with the verdict the device stamped on each, and with --session writes them as an export
(a JSON array of entries) that the npm validator and the web viewer read. Since 0.10.1 every line
also says where it came from (`source`: platform, app version, Verivent version), which an export
states once, in the entry that opens it.

    read_lines.py capture.txt [more.txt ...] [--session session.json]
    adb logcat -d -s VeriventEvent | read_lines.py -
"""
import argparse
import json
import re
import sys
import time

# The tag, then whatever the console puts between it and the message: logcat's "( 1234):" or ":",
# or the single space NSLog leaves on iOS.
TAGGED = re.compile(r"VeriventEvent(?:\(\s*\d+\))?:? (.*)$")
PIECE = re.compile(r"\[(\S+) (\d+)/(\d+)\] ")
BACKSLASH = re.compile(r"\\(.{0,3})")
OCTAL = re.compile(r"[0-7]{3}")
ESCAPED = re.compile(r"(?:\\[0-7]{3})+")
SHOWN_CHARS = 160


def message_of(line):
    """The text logTo wrote on this console line, or None when the line isn't one of its."""
    tagged = TAGGED.search(line)
    # Untagged: logcat -v raw or -v long, or lines pasted without their headers.
    text = tagged.group(1) if tagged else line
    return text if text.startswith('{"type":"analytics"') or PIECE.match(text) else None


def messages(lines, source):
    """[(where, text)] for every logTo line in one capture, as logTo wrote it.

    The unified log's compact style (`log stream`/`log show --style compact`) writes a backslash as
    `\\134`. JSON never puts a digit after a backslash, so a capture is undone when every backslash
    in it is followed by three octal digits; deciding per capture keeps a raw piece that happens to
    start that way intact.
    """
    found = []
    for number, line in enumerate(lines, start=1):
        text = message_of(line.rstrip("\r"))
        if text is not None:
            found.append((f"{source}:{number}", text))
    backslashes = [match.group(1) for _, text in found for match in BACKSLASH.finditer(text)]
    if backslashes and all(OCTAL.fullmatch(after) for after in backslashes):
        found = [(where, ESCAPED.sub(unescape, text)) for where, text in found]
    return found


def unescape(match):
    """A run of `\\ddd` is UTF-8 bytes, so a character outside ASCII comes back whole."""
    octets = bytes(int(match.group(0)[i + 1:i + 4], 8) for i in range(0, len(match.group(0)), 4))
    return octets.decode("utf-8", errors="replace")


def read(found):
    """(entries, notes): every entry in the order its first line appeared, and what went wrong."""
    slots = []  # one per entry, in order; an entry still being joined holds its pieces
    open_pieces = {}  # id -> the slot whose pieces are still arriving
    notes = []

    def parse(text, where):
        try:
            entry = json.loads(text)
        except json.JSONDecodeError as error:
            notes.append(f"{where}: not JSON ({error.msg}), skipped")
            return None
        if not isinstance(entry, dict) or entry.get("type") != "analytics":
            notes.append(f"{where}: not an analytics entry, skipped")
            return None
        return entry

    for where, text in found:
        piece = PIECE.match(text)
        if not piece:
            slots.append(parse(text, where))
            continue
        entry_id, index, total = piece.group(1), int(piece.group(2)), int(piece.group(3))
        slot = open_pieces.get(entry_id)
        # A first piece for an id that is still open: the app restarted and ids began again at 1.
        if slot is None or index == 1:
            if slot is not None:
                notes.append(f"entry {entry_id}: pieces {sorted(slot['parts'])} of {slot['total']} never completed")
            slot = {"id": entry_id, "total": total, "parts": {}, "where": where, "at": len(slots)}
            open_pieces[entry_id] = slot
            slots.append(slot)
        if index in slot["parts"]:
            notes.append(f"{where}: piece {index}/{total} of entry {entry_id} repeated, kept the first")
            continue
        slot["parts"][index] = text[piece.end():]
        if len(slot["parts"]) == slot["total"]:
            joined = "".join(slot["parts"][i] for i in range(1, slot["total"] + 1))
            slots[slot["at"]] = parse(joined, f"entry {entry_id} (from {slot['where']})")
            del open_pieces[entry_id]

    for slot in open_pieces.values():
        notes.append(f"entry {slot['id']}: pieces {sorted(slot['parts'])} of {slot['total']} never completed")
    entries = [slot for slot in slots if slot is not None and "parts" not in slot]
    return entries, notes


def verdict(entry):
    if entry.get("issues"):
        return "breaks"
    return "complies" if "planVersion" in entry else "not_checked"


def shown(prop):
    """The property as captured, with a long value cut and a list or object's size spelled out."""
    value = prop.get("value", "")
    cut = {**prop, "value": value if len(value) <= SHOWN_CHARS else value[:SHOWN_CHARS] + f"… ({len(value)} chars)"}
    if prop.get("type") in ("array", "object") and not prop.get("redacted"):
        try:
            cut["size"] = len(json.loads(value))
        except (json.JSONDecodeError, TypeError):
            pass
    return cut


def sources(entries):
    """Every distinct `source` the lines carry, in the order first seen."""
    seen = []
    for entry in entries:
        if "source" in entry and entry["source"] not in seen:
            seen.append(entry["source"])
    return seen


def session(entries):
    """The entries as an export: one opening `source` entry, and the events without theirs.

    Lines from more than one source (captures of two apps or devices read together) keep their own,
    since one opener can't name them all; the note says so.
    """
    found = sources(entries)
    if len(found) != 1:
        notes = [f"the lines come from {len(found)} sources, so each event keeps its own"] if found else []
        return entries, notes
    opener = {"type": "source", "id": "source", "ts": int(time.time() * 1000), "source": found[0]}
    return [opener] + [{key: value for key, value in entry.items() if key != "source"} for entry in entries], []


def summary(entries, notes):
    events = [
        {
            "id": entry.get("id"),
            "name": entry.get("name"),
            "channel": entry.get("channel"),
            "planVersion": entry.get("planVersion"),
            "verdict": verdict(entry),
            "issues": [f"{issue['property']} · {issue['message']}" for issue in entry.get("issues", [])],
            "properties": [shown(prop) for prop in entry.get("properties", [])],
        }
        for entry in entries
    ]
    versions = sorted({entry["planVersion"] for entry in entries if "planVersion" in entry})
    untyped = [entry.get("id") for entry in entries if any("type" not in prop for prop in entry.get("properties", []))]
    notes = (
        notes
        + ([f"events were checked against different plan versions: {', '.join(versions)}"] if len(versions) > 1 else [])
        + ([f"entries {', '.join(map(str, untyped))} carry no property types (Verivent before 0.9)"] if untyped else [])
    )
    counts = {name: sum(event["verdict"] == name for event in events) for name in ("complies", "breaks", "not_checked")}
    return {"events": events, "planVersions": versions, "sources": sources(entries), "counts": counts, "notes": notes}


def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("captures", nargs="+", help="capture files, or - for stdin")
    parser.add_argument("--session", help="also write the entries here, as an exported session")
    args = parser.parse_args()

    found = []
    unread = []
    for path in args.captures:
        if path == "-":
            text = sys.stdin.read()
        else:
            with open(path, encoding="utf-8", errors="replace") as capture:
                text = capture.read()
        source = "stdin" if path == "-" else path
        # Not splitlines(): it also splits on U+2028 and friends, which a value can hold.
        lines = messages(text.split("\n"), source)
        if not lines and text.strip():
            unread.append(f"{source}: no VeriventEvent lines recognised; a console format this script doesn't read?")
        found += lines

    entries, notes = read(found)
    notes = unread + notes
    if args.session:
        exported, session_notes = session(entries)
        notes += session_notes
        with open(args.session, "w", encoding="utf-8") as file:
            json.dump(exported, file, ensure_ascii=False)
    json.dump(summary(entries, notes), sys.stdout, ensure_ascii=False, indent=2)
    print()


if __name__ == "__main__":
    main()
