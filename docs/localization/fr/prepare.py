#!/usr/bin/env python3
"""Regenerate the draft French review packet; never edits Android source/resources."""
import argparse
import collections
import csv
import hashlib
import io
import json
from pathlib import Path
import re
import tempfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[3]
PACKET = Path(__file__).resolve().parent
BASELINE = '9ff11f5cba8d589e823ab80e8816898a85e6dc33'
PLACEHOLDER = re.compile(r'%(?:\d+\$)?[-#+ 0,(]*\d*(?:\.\d+)?[a-zA-Z]')
REVIEW_TABLES = {'resource-review.csv': ('key', 'quantity'), 'hardcoded-review.csv': ('id',), 'formatting-review.csv': ('source', 'line')}
PROTECTED = {'Leaf', 'Jellyfin', 'SMB', 'CBZ', 'CBR', 'PDF', 'ZIP', 'RAR', 'Ko-fi'}


def placeholders(text):
    return collections.Counter(PLACEHOLDER.findall(text.replace('%%', '')))


def kotlin_literals(source):
    """Lexical inventory, not Kotlin type/usage analysis. Skip comments; retain templates."""
    def comment(i):
        if source.startswith('//', i):
            end = source.find('\n', i + 2)
            return len(source) if end < 0 else end
        depth, i = 1, i + 2
        while i < len(source) and depth:
            if source.startswith('/*', i):
                depth += 1; i += 2
            elif source.startswith('*/', i):
                depth -= 1; i += 2
            else:
                i += 1
        return i

    def string_end(start):
        raw = source.startswith('"""', start)
        delimiter = '"""' if raw else '"'
        i = start + len(delimiter)
        while i < len(source):
            if not raw and source[i] == '\\':
                i += 2
            elif source.startswith('${', i):
                i = expression_end(i + 2)
            elif source.startswith(delimiter, i):
                return i + len(delimiter), len(delimiter)
            else:
                i += 1
        raise ValueError(f'Unterminated Kotlin string at offset {start}')

    def expression_end(i):
        depth = 1
        while i < len(source) and depth:
            if source.startswith(('//', '/*'), i):
                i = comment(i)
            elif source[i] == '"':
                i, _ = string_end(i)
            elif source[i] == "'":
                i += 1
                while i < len(source):
                    if source[i] == '\\': i += 2
                    elif source[i] == "'": i += 1; break
                    else: i += 1
            else:
                if source[i] == '{': depth += 1
                elif source[i] == '}': depth -= 1
                i += 1
        return i

    i = 0
    while i < len(source):
        if source.startswith(('//', '/*'), i):
            i = comment(i)
        elif source[i] == '"':
            start = i
            i, delimiter = string_end(i)
            yield start, i, source[start + delimiter:i - delimiter]
        elif source[i] == "'":
            i += 1
            while i < len(source):
                if source[i] == '\\': i += 2
                elif source[i] == "'": i += 1; break
                else: i += 1
        else:
            i += 1


def classification(value, prefix, path):
    if value in PROTECTED:
        return 'protected_product_or_format_name'
    if re.search(r'(?:contentDescription\s*=|\bText\(\s*(?:text\s*=)?)\s*$', prefix):
        return 'direct_ui_copy_candidate'
    if not value or not re.search(r'[A-Za-zÀ-ÿ]', value):
        return 'technical_or_empty_candidate'
    if value.startswith(('http:', 'https:', 'content:', 'file:', 'image/', 'application/', 'text/')):
        return 'url_mime_or_path_candidate'
    if '/ui/' in path and (re.search(r'\s', value) or value[:1].isupper()):
        return 'additional_ui_copy_candidate'
    return 'usage_review_required'


def csv_text(rows, fields):
    # Source literals can begin with spreadsheet operators. The pinned source retains the original literal.
    def safe(value):
        text = str(value)
        return "'" + text if text.lstrip().startswith(('=', '+', '-', '@')) else text
    out = io.StringIO(newline='')
    writer = csv.DictWriter(out, fieldnames=fields, quoting=csv.QUOTE_ALL, lineterminator='\n')
    writer.writeheader()
    writer.writerows({k: safe(row.get(k, '')) for k in fields} for row in rows)
    return out.getvalue()


def write_review(output, name, text):
    path = output / name
    if not path.exists():
        path.write_text(text, newline='')


def check_review(name, actual, expected):
    keys = REVIEW_TABLES[name]
    def rows(path):
        with path.open(newline='') as handle:
            data = list(csv.DictReader(handle))
        result = {tuple(row[k] for k in keys): row for row in data}
        if len(data) != len(result):
            raise ValueError(f'Duplicate review identities: {name}')
        return result
    current, fresh = rows(actual), rows(expected)
    if current.keys() != fresh.keys():
        raise ValueError(f'Reconcile changed source rows without discarding reviews: {name}')
    editable = {'decision', 'reviewer', 'reviewed_on', 'notes', 'reviewed_french',
                'proposed_resource_key', 'reviewer_notes'}
    if name == 'hardcoded-review.csv':
        editable.add('french_draft')
    for identity, row in current.items():
        if any(row.get(k) != value for k, value in fresh[identity].items() if k not in editable):
            raise ValueError(f'Review source changed; retain annotations and reconcile: {name}/{identity}')
        revised = row.get('reviewed_french')
        if revised and (placeholders(revised) != placeholders(row['english']) or revised.count('%') != row['english'].count('%')):
            raise ValueError(f'Reviewed placeholders differ: {identity}')
        if name == 'resource-review.csv' and row['decision'] == 'approved' and not (row['reviewer'] and row['reviewed_on']):
            raise ValueError(f'Approval requires reviewer and date: {identity}')


def generate(output):
    output.mkdir(parents=True, exist_ok=True)
    translations = json.loads((PACKET / 'translations.json').read_text())
    resource_rows, names = [], set()
    sources = sorted((ROOT / 'app/src/main/res/values').glob('*.xml'))
    hashes = {}
    for source in sources:
        translated = ET.Element('resources')
        translated.append(ET.Comment(' DRAFT French: fluent review and UI integration/QA pending. Not active app resources. '))
        for entry in ET.parse(source).getroot():
            if entry.tag not in ('string', 'plurals', 'string-array') or entry.get('translatable') == 'false':
                continue
            name = entry.attrib['name']
            if name in names:
                raise ValueError(f'Duplicate default resource {name}')
            names.add(name)
            if name not in translations:
                raise ValueError(f'Missing French draft {name}')
            if entry.tag == 'string' and len(entry):
                raise ValueError(f'Markup requires manual preservation: {name}')
            target = ET.SubElement(translated, entry.tag, entry.attrib)
            variants = [(None, entry)] if entry.tag == 'string' else [(item.get('quantity', str(i)), item) for i, item in enumerate(entry)]
            for quantity, original in variants:
                english = ''.join(original.itertext())
                french = translations[name] if quantity is None else translations[name][quantity]
                if not isinstance(french, str) or not french.strip():
                    raise ValueError(f'Empty draft {name}/{quantity}')
                if placeholders(english) != placeholders(french) or english.count('%') != french.count('%'):
                    raise ValueError(f'Placeholder mismatch {name}/{quantity}')
                if collections.Counter(re.findall(r'\\+[ntr]', english)) != collections.Counter(re.findall(r'\\+[ntr]', french)):
                    raise ValueError(f'Escaped whitespace mismatch {name}/{quantity}')
                literals = re.findall(r'https?://[^\s)<>]+|\.(?:cbz|cbr|zip|rar|pdf)\b', english)
                if any(token not in french for token in literals):
                    raise ValueError(f'URL or format extension changed: {name}')
                for token in PROTECTED:
                    if re.search(r'(?<![A-Za-z])' + re.escape(token) + r'(?![A-Za-z])', english) and token not in french:
                        raise ValueError(f'Protected term missing: {name}/{token}')
                if re.search(r"(?<!\\)'", french):
                    raise ValueError(f'Android apostrophe needs escaping: {name}')
                if quantity is None:
                    target.text = french
                else:
                    ET.SubElement(target, 'item', original.attrib).text = french
                resource_rows.append({'key': name, 'quantity': quantity or '', 'english': english,
                    'french_draft': french, 'source': source.relative_to(ROOT).as_posix(),
                    'placeholders': ' | '.join(sorted(placeholders(english).elements())),
                    'reviewed_french': '', 'decision': 'unreviewed', 'reviewer': '', 'reviewed_on': '', 'notes': ''})
        if len(translated) > 1:
            target_path = output / 'draft-values-fr' / source.name
            target_path.parent.mkdir(parents=True, exist_ok=True)
            ET.indent(translated, space='    ')
            ET.ElementTree(translated).write(target_path, encoding='utf-8', xml_declaration=True)
            with target_path.open('ab') as handle:
                handle.write(b'\n')
        hashes[source.relative_to(ROOT).as_posix()] = hashlib.sha256(source.read_bytes()).hexdigest()
    if set(translations) != names:
        raise ValueError(f'Unknown French keys: {sorted(set(translations) - names)}')
    write_review(output, 'resource-review.csv', csv_text(resource_rows,
        ['key', 'quantity', 'english', 'french_draft', 'source', 'placeholders', 'reviewed_french', 'decision', 'reviewer', 'reviewed_on', 'notes']))

    literals, formatters = [], []
    kotlin_files = sorted(path for source_set in ('main', 'full', 'fdroid') for path in (ROOT / f'app/src/{source_set}/java').rglob('*.kt')) + sorted((ROOT / 'shared/src/commonMain').rglob('*.kt'))
    for source in kotlin_files:
        relative = source.relative_to(ROOT).as_posix()
        text = source.read_text()
        hashes[relative] = hashlib.sha256(source.read_bytes()).hexdigest()
        for index, (start, end, value) in enumerate(kotlin_literals(text), 1):
            line = text.count('\n', 0, start) + 1
            context = text[max(0, start - 110):min(len(text), end + 90)]
            literals.append({'id': f'{relative}:{line}:{index}', 'source': relative, 'line': line,
                'literal': value.replace('\r', r'\r').replace('\n', r'\n'), 'context': context.replace('\r', r'\r').replace('\n', r'\n'), 'classification': classification(value, text[max(0, start-110):start], relative),
                'decision': 'needs_usage_review', 'proposed_resource_key': '', 'french_draft': '', 'reviewer_notes': ''})
        for line, value in enumerate(text.splitlines(), 1):
            if re.search(r'Locale\.US|SimpleDateFormat|String\.format|\.name\.lowercase\(', value):
                formatters.append({'source': relative, 'line': line, 'code': value.strip(), 'decision': 'review_display_vs_storage_contract'})
    write_review(output, 'hardcoded-review.csv', csv_text(literals,
        ['id', 'source', 'line', 'literal', 'context', 'classification', 'decision', 'proposed_resource_key', 'french_draft', 'reviewer_notes']))
    write_review(output, 'formatting-review.csv', csv_text(formatters, ['source', 'line', 'code', 'decision']))
    summary = {'baseline': BASELINE, 'locale': 'fr', 'status': 'DRAFT — no fluent review or app integration',
        'resource_entries': len(names), 'resource_review_rows': len(resource_rows),
        'kotlin_files_scanned': len(kotlin_files), 'literal_rows': len(literals),
        'literal_classifications': dict(sorted(collections.Counter(x['classification'] for x in literals).items())),
        'formatting_rows': len(formatters), 'source_sha256': hashes,
        'limits': ['Lexical candidates are not proof of reachability or exhaustive visible-copy coverage.',
                   'Nested expressions remain inside their parent template; manually review their conditional text.',
                   'Server/comic/user data and protocol/storage identifiers must not be translated.',
                   'Draft XML is outside app/src; French is not enabled or advertised.']}
    output.joinpath('inventory.json').write_text(json.dumps(summary, ensure_ascii=False, indent=2) + '\n')
    return summary


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true', help='Verify reproducible generated files without editing them')
    args = parser.parse_args()
    if args.check:
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp)
            result = generate(path)
            for item in path.rglob('*'):
                if not item.is_file():
                    continue
                relative = item.relative_to(path)
                if item.name in REVIEW_TABLES:
                    check_review(item.name, PACKET / relative, item)
                elif item.read_bytes() != (PACKET / relative).read_bytes():
                    raise SystemExit(f'Stale generated file: {relative}')
    else:
        result = generate(PACKET)
    print(json.dumps({k: v for k, v in result.items() if k != 'source_sha256'}, ensure_ascii=False, indent=2))
