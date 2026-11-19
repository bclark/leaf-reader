"""Offline packet checks; no Android, translation-service or device calls."""
import csv
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import prepare


class FrenchPacketTests(unittest.TestCase):
    def test_kotlin_comments_escaped_quotes_chars_and_nested_templates(self):
        code = r'''// Text("not copy")
/* outer "ignored" /* inner "also ignored" */ */
val brace = '}'
Text("Hello \\\"reader\\\"")
Text("Page ${if (ready) "one" else "two"}")
val raw = """Multiple
lines ${value}"""
'''
        values = [value for _, _, value in prepare.kotlin_literals(code)]
        self.assertEqual(len(values), 3)
        self.assertIn('reader', values[0])
        self.assertEqual(values[1], 'Page ${if (ready) "one" else "two"}')
        self.assertEqual(values[2], 'Multiple\nlines ${value}')

    def test_placeholder_types_positions_and_literal_percent(self):
        self.assertEqual(prepare.placeholders('%1$s · %2$d%%'), prepare.placeholders('%2$d%% : %1$s'))
        self.assertNotEqual(prepare.placeholders('%1$s'), prepare.placeholders('%1$d'))
        self.assertNotEqual(prepare.placeholders('%1$s %1$s'), prepare.placeholders('%1$s'))

    def test_csv_source_operators_are_text(self):
        text = prepare.csv_text([{'literal': '=1+1'}], ['literal'])
        self.assertEqual(list(csv.DictReader(text.splitlines()))[0]['literal'], "'=1+1")

    def test_regeneration_preserves_review_and_rejects_broken_revision(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            resources = root / 'app/src/main/res/values'
            resources.mkdir(parents=True)
            (resources / 'strings.xml').write_text('<resources><string name="hello">Hello %1$s</string></resources>')
            packet = root / 'packet'
            packet.mkdir()
            (packet / 'translations.json').write_text('{"hello":"Bonjour %1$s"}')
            with patch.object(prepare, 'ROOT', root), patch.object(prepare, 'PACKET', packet):
                prepare.generate(packet)
                review = packet / 'resource-review.csv'
                with review.open(newline='') as handle:
                    reader = csv.DictReader(handle)
                    fields, rows = reader.fieldnames, list(reader)
                rows[0].update(reviewed_french='Bonjour %1$s !', decision='approved',
                               reviewer='Fixture reviewer', reviewed_on='2026-11-19')
                with review.open('w', newline='') as handle:
                    writer = csv.DictWriter(handle, fieldnames=fields)
                    writer.writeheader(); writer.writerows(rows)
                before = review.read_bytes()
                prepare.generate(packet)
                self.assertEqual(review.read_bytes(), before)
                expected = root / 'expected'
                prepare.generate(expected)
                prepare.check_review('resource-review.csv', review, expected / review.name)
                rows[0]['reviewed_french'] = 'Bonjour'
                with review.open('w', newline='') as handle:
                    writer = csv.DictWriter(handle, fieldnames=fields)
                    writer.writeheader(); writer.writerows(rows)
                with self.assertRaisesRegex(ValueError, 'placeholders'):
                    prepare.check_review('resource-review.csv', review, expected / review.name)


if __name__ == '__main__':
    unittest.main()
