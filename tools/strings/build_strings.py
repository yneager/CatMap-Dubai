"""Generates values/strings.xml and values-ar/strings.xml from the tables, and checks them against the code."""
import importlib.util
import io
import json
import os
import re
import sys
import xml.etree.ElementTree as ET

HERE = os.path.dirname(os.path.abspath(__file__))
PROJECT = sys.argv[1]
CHECK = "--check" in sys.argv[2:]
RES = os.path.join(PROJECT, "app", "src", "main", "res")
SRC = os.path.join(PROJECT, "app", "src", "main", "java")

entries = []
for part in "abcdefghi":
    spec = importlib.util.spec_from_file_location(f"strings_{part}", os.path.join(HERE, f"strings_{part}.py"))
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    entries.extend(mod.S)

keys = [k for k, _, _ in entries]
dupes = {k for k in keys if keys.count(k) > 1}
if dupes:
    sys.exit(f"Duplicate keys: {sorted(dupes)}")

used = set()
for root, _, files in os.walk(SRC):
    for f in files:
        if f.endswith(".kt"):
            used |= set(re.findall(r"R\.string\.([A-Za-z0-9_]+)", open(os.path.join(root, f), encoding="utf-8").read()))
missing = sorted(used - set(keys))
unused = sorted(set(keys) - used - {"app_name"})
print("missing:", missing)
print("unused:", unused)
if missing:
    sys.exit("Missing translations; resource files were not changed.")


def esc(text: str) -> str:
    text = text.replace("\\", "\\\\").replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    text = text.replace("'", "\\'").replace('"', '\\"').replace("\n", "\\n")
    if text.startswith("@") or text.startswith("?"):
        text = "\\" + text
    return text


def placeholders(s):
    return sorted(re.findall(r"%\d+\$[sd]", s))


for k, en, ar in entries:
    if placeholders(en) != placeholders(ar):
        sys.exit(f"Placeholder mismatch in {k}: {placeholders(en)} vs {placeholders(ar)}")

PLURALS_EN = """    <plurals name="unread_notifications">
        <item quantity="one">%d unread notification</item>
        <item quantity="other">%d unread notifications</item>
    </plurals>
"""
PLURALS_AR = """    <plurals name="unread_notifications">
        <item quantity="zero">لا توجد إشعارات غير مقروءة</item>
        <item quantity="one">إشعار واحد غير مقروء</item>
        <item quantity="two">إشعاران غير مقروءين</item>
        <item quantity="few">%d إشعارات غير مقروءة</item>
        <item quantity="many">%d إشعاراً غير مقروء</item>
        <item quantity="other">%d إشعار غير مقروء</item>
    </plurals>
"""


def write(path, index, plurals):
    with io.StringIO(newline="\n") as out:
        out.write('<?xml version="1.0" encoding="utf-8"?>\n')
        out.write("<!-- Generated from the translation tables; keep English and Arabic in sync. -->\n")
        out.write('<resources xmlns:tools="http://schemas.android.com/tools" tools:ignore="TypographyEllipsis,TypographyDashes">\n')
        for entry in entries:
            out.write(f'    <string name="{entry[0]}">{esc(entry[index])}</string>\n')
        out.write(plurals)
        out.write("</resources>\n")
        generated = out.getvalue()
    write_or_check(path, generated)


def write_or_check(path, generated):
    expected = generated.encode("utf-8")
    # Git checks Android XML out as CRLF on this Windows workspace. Preserve its
    # existing newline convention, so regeneration also preserves the raw bytes.
    if path.endswith(".xml") and os.path.isfile(path):
        with open(path, "rb") as previous:
            if b"\r\n" in previous.read():
                expected = expected.replace(b"\n", b"\r\n")
    if CHECK:
        if not os.path.isfile(path) or open(path, "rb").read() != expected:
            sys.exit(f"Generated strings are stale: {path}; run tools/strings/build_strings.py .")
        return
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as out:
        out.write(expected)


write(os.path.join(RES, "values", "strings.xml"), 1, PLURALS_EN)
write(os.path.join(RES, "values-ar", "strings.xml"), 2, PLURALS_AR)
print(f"{'checked' if CHECK else 'wrote'} {len(entries)} strings per language")

# Keep web-only entries outside Android's missing/unused checks and XML.
spec = importlib.util.spec_from_file_location("strings_web", os.path.join(HERE, "strings_web.py"))
web_module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(web_module)
web_entries = entries + web_module.S
web_keys = [key for key, _, _ in web_entries]
web_dupes = {key for key in web_keys if web_keys.count(key) > 1}
if web_dupes:
    sys.exit(f"Duplicate web keys: {sorted(web_dupes)}")
for key, en, ar in web_module.S:
    if placeholders(en) != placeholders(ar):
        sys.exit(f"Placeholder mismatch in {key}: {placeholders(en)} vs {placeholders(ar)}")
for language, index, plural_xml in (("en", 1, PLURALS_EN), ("ar", 2, PLURALS_AR)):
    strings = {entry[0]: entry[index] for entry in web_entries}
    strings["unread_notifications"] = {
        item.attrib["quantity"]: item.text for item in ET.fromstring(plural_xml)
    }
    generated = json.dumps(strings, ensure_ascii=False, indent=2) + "\n"
    write_or_check(os.path.join(PROJECT, "web", "src", "i18n", language + ".json"), generated)
print(f"{'checked' if CHECK else 'wrote'} {len(web_entries)} web strings plus unread_notifications per language")
