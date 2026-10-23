"""python3 -m unittest discover -s plugins/verivent/skills/analytics-events/scripts"""
import json
import pathlib
import unittest

from read_lines import messages, read, session, summary

FILES = pathlib.Path(__file__).resolve().parent / "fixtures"


def entry(entry_id, name="search", **extra):
    return json.dumps({"type": "analytics", "id": entry_id, "ts": 1, "name": name, "channel": "firebase",
                       "properties": [{"name": "q", "value": "a b", "redacted": False, "type": "string"}], **extra},
                      ensure_ascii=False, separators=(",", ":"))


def pieces(entry_id, text, size):
    chunks = [text[i:i + size] for i in range(0, len(text), size)]
    return [f"[{entry_id} {i}/{len(chunks)}] {chunk}" for i, chunk in enumerate(chunks, start=1)]


def run(lines):
    entries, notes = read(messages(lines, "capture"))
    return summary(entries, notes)


class RealCaptures(unittest.TestCase):

    def check(self, file, verdicts):
        result = run((FILES / file).read_text(encoding="utf-8").split("\n"))
        self.assertEqual([(event["id"], event["name"], event["verdict"]) for event in result["events"]], verdicts)
        self.assertEqual(result["notes"], [])
        return result

    def test_logcat_joins_the_item_list_and_survives_a_restart(self):
        result = self.check("android-logcat.txt", [
            ("1", "screen_view", "not_checked"), ("2", "purchase", "not_checked"),
            ("3", "screen_view", "breaks"), ("4", "purchase", "breaks"), ("5", "view_item_list", "breaks"),
            ("6", "checkout_started", "complies"), ("7", "add_to_cart", "complies"), ("8", "checkout_started", "complies"),
            ("1", "view_item_list", "breaks"), ("2", "screen_view", "breaks"),
        ])
        self.assertEqual(result["events"][3]["issues"], [
            "transaction_id · Required, but not sent",
            "user_email · Not in the plan for this event",
            "items[*].quantity · Expected integer, got string · 1 of 2",
        ])
        self.assertEqual(result["events"][4]["issues"], ["items[*].item_id · Doesn't match the pattern ^SN-[0-9]+$ · 2 of 60"])

    def test_log_stream_compact_style_is_unescaped(self):
        result = self.check("ios-log-stream.txt", [
            ("1", "screen_view", "not_checked"), ("2", "purchase", "not_checked"),
            ("3", "screen_view", "breaks"), ("4", "purchase", "breaks"), ("5", "view_item_list", "breaks"),
            ("6", "checkout_started", "complies"), ("7", "add_to_cart", "complies"), ("8", "checkout_started", "complies"),
        ])
        items = next(p for p in result["events"][4]["properties"] if p["name"] == "items")
        self.assertTrue(items["value"].startswith('[{"item_id":"SN-1"'))

    def test_log_show_default_style(self):
        self.check("ios-log-show.txt", [
            ("1", "screen_view", "not_checked"), ("2", "purchase", "not_checked"),
            ("3", "screen_view", "breaks"), ("4", "purchase", "breaks"), ("5", "view_item_list", "breaks"),
            ("6", "checkout_started", "complies"), ("7", "add_to_cart", "complies"), ("8", "checkout_started", "complies"),
            ("1", "purchase", "not_checked"), ("2", "screen_view", "not_checked"),
        ])

    def test_redacted_values_stay_redacted(self):
        result = run((FILES / "android-logcat.txt").read_text(encoding="utf-8").split("\n"))
        email = next(p for p in result["events"][3]["properties"] if p["name"] == "user_email")
        self.assertEqual(email, {"name": "user_email", "value": "██", "redacted": True, "type": "string"})


class Formats(unittest.TestCase):

    def test_logcat_brief_and_raw(self):
        result = run([f"I/VeriventEvent( 1234): {entry('1')}", entry("2")])
        self.assertEqual([event["id"] for event in result["events"]], ["1", "2"])

    def test_logcat_long_puts_the_tag_on_its_own_header_line(self):
        result = run(["[ 10-20 15:36:20.703  4012: 4012 I/VeriventEvent ]", entry("1"), ""])
        self.assertEqual([event["id"] for event in result["events"]], ["1"])
        self.assertEqual(result["notes"], [])

    def test_compact_style_escapes_are_read_as_utf8(self):
        escaped = entry("1", tag="x").replace('"x"', '"a\\"b"').replace("\\", "\\134").replace("a b", "\\342\\200\\250")
        result = run([f"VeriventEvent {escaped}"])
        self.assertEqual(result["events"][0]["properties"][0]["value"], "\u2028")

    def test_lists_and_objects_say_their_size(self):
        text = entry("1").replace('"value":"a b","redacted":false,"type":"string"', '"value":"[1,2,3]","redacted":false,"type":"array"')
        self.assertEqual(run([text])["events"][0]["properties"][0]["size"], 3)

    def test_unrelated_lines_are_ignored(self):
        result = run(['Filtering the log data using "composedMessage BEGINSWITH "VeriventEvent""', "D/Other: {}"])
        self.assertEqual(result["events"], [])
        self.assertEqual(result["notes"], [])

    def test_a_raw_backslash_before_digits_is_left_alone(self):
        text = entry("1", extra_value="x").replace('"a b"', '"C:\\\\123"')
        result = run([f"VeriventEvent: {text}"])
        self.assertEqual(result["events"][0]["properties"][0]["value"], "C:\\123")

    def test_a_line_separator_inside_a_value_does_not_split_it(self):
        text = entry("1").replace("a b", "a\u2028b")
        result = run(f"VeriventEvent: {text}\n".split("\n"))
        self.assertEqual(result["events"][0]["properties"][0]["value"], "a\u2028b")


class Pieces(unittest.TestCase):

    def test_pieces_join_in_order_even_when_interleaved(self):
        first, second = pieces("1", entry("1"), 40), pieces("2", entry("2"), 40)
        lines = [line for pair in zip(first, second) for line in pair] + first[len(second):] + second[len(first):]
        result = run([f"VeriventEvent: {line}" for line in lines])
        self.assertEqual(sorted(event["id"] for event in result["events"]), ["1", "2"])
        self.assertEqual(result["notes"], [])

    def test_a_missing_piece_is_reported(self):
        lines = pieces("7", entry("7"), 40)
        result = run(lines[:1] + lines[2:])
        self.assertEqual(result["events"], [])
        self.assertIn("entry 7: pieces", result["notes"][0])
        self.assertIn("never completed", result["notes"][0])

    def test_a_repeated_piece_is_reported_once(self):
        lines = pieces("7", entry("7"), 40)
        result = run(lines[:2] + lines[1:])
        self.assertEqual(len(result["events"]), 1)
        self.assertIn("piece 2/", result["notes"][0])

    def test_a_restart_mid_entry_starts_a_new_one(self):
        old, new = pieces("1", entry("1", "old"), 40), pieces("1", entry("1", "new"), 40)
        result = run(old[:1] + new)
        self.assertEqual([event["name"] for event in result["events"]], ["new"])
        self.assertIn("never completed", result["notes"][0])


class Verdicts(unittest.TestCase):

    def test_verdicts_come_from_the_line(self):
        result = run([
            entry("1", planVersion="1.0.0"),
            entry("2", planVersion="1.0.0", issues=[{"property": "q", "message": "Required, but not sent"}]),
            entry("3"),
        ])
        self.assertEqual([event["verdict"] for event in result["events"]], ["complies", "breaks", "not_checked"])
        self.assertEqual(result["events"][1]["issues"], ["q · Required, but not sent"])

    def test_untyped_lines_and_mixed_plan_versions_are_noted(self):
        old = json.loads(entry("1", planVersion="1.0.0"))
        del old["properties"][0]["type"]
        result = run([json.dumps(old, separators=(",", ":")), entry("2", planVersion="1.1.0")])
        self.assertEqual(result["planVersions"], ["1.0.0", "1.1.0"])
        self.assertTrue(any("different plan versions" in note for note in result["notes"]))
        self.assertTrue(any("before 0.9" in note for note in result["notes"]))


ANDROID = {"platform": "android", "appVersion": "1.0", "veriventVersion": "0.10.1"}
IOS = {"platform": "ios", "appVersion": "1.0", "veriventVersion": "0.10.1"}


class Sessions(unittest.TestCase):

    def test_the_source_opens_the_session_once(self):
        entries, _ = read(messages([entry("1", source=ANDROID), entry("2", source=ANDROID)], "capture"))
        exported, notes = session(entries)
        opener, *events = exported
        self.assertEqual((opener["type"], opener["id"], opener["source"]), ("source", "source", ANDROID))
        self.assertIsInstance(opener["ts"], int)
        self.assertEqual([event["id"] for event in events], ["1", "2"])
        self.assertTrue(all("source" not in event for event in events))
        self.assertEqual(notes, [])

    def test_lines_from_two_sources_keep_their_own(self):
        entries, _ = read(messages([entry("1", source=ANDROID), entry("2", source=IOS)], "capture"))
        exported, notes = session(entries)
        self.assertEqual([event["source"] for event in exported], [ANDROID, IOS])
        self.assertEqual(notes, ["the lines come from 2 sources, so each event keeps its own"])

    def test_new_lines_mixed_with_old_ones_keep_their_own(self):
        entries, _ = read(messages([entry("1"), entry("2", source=ANDROID)], "capture"))
        exported, notes = session(entries)
        self.assertEqual(exported, entries)
        self.assertEqual(notes, ["some lines carry no source (Verivent before 0.10.1), so each event keeps its own"])

    def test_lines_from_before_0_10_1_are_written_as_they_came(self):
        entries, _ = read(messages([entry("1")], "capture"))
        self.assertEqual(session(entries), (entries, []))

    def test_the_summary_names_the_sources(self):
        self.assertEqual(run([entry("1", source=ANDROID), entry("2", source=ANDROID)])["sources"], [ANDROID])


if __name__ == "__main__":
    unittest.main()
