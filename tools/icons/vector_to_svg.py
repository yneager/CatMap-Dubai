"""Generate web artwork from Android resources without modifying Android.

Usage: python tools/icons/vector_to_svg.py [project] [--check] [--png]
SVG conversion uses only Python's standard library. --png also needs Pillow and
the web project's Playwright installation (and Chromium or local Chrome). No server,
external artwork, network request, or runtime icon font is needed.
"""
from __future__ import annotations

import argparse
import json
import math
from pathlib import Path
import re
import subprocess
import tempfile
import xml.etree.ElementTree as ET

ANDROID = "{http://schemas.android.com/apk/res/android}"
SVG = "http://www.w3.org/2000/svg"
ARTWORK = {"cat", "dog", "bird", "rabbit", "paw"}


def attr(element: ET.Element, key: str, default: str = "") -> str:
    return element.get(ANDROID + key, default)


def number(value: str) -> str:
    return f"{float(re.sub(r'(dp|px|sp)$', '', value)):g}"


def colors_from(resources: Path) -> dict[str, str]:
    result: dict[str, str] = {}
    for file in sorted((resources / "values").glob("*.xml")):
        for node in ET.parse(file).getroot().findall("color"):
            if node.text is not None:
                result[node.attrib["name"]] = node.text.strip()
    return result


def color(value: str, colors: dict[str, str], tint: bool) -> tuple[str, float]:
    seen = set()
    while value.startswith("@color/"):
        name = value[7:]
        if name in seen or name not in colors:
            raise ValueError(f"Unresolved or cyclic color reference: {value}")
        seen.add(name)
        value = colors[name]
    if value.startswith("?"):
        return "currentColor", 1.0
    if value in {"@android:color/transparent", "#00000000"}:
        return "none", 0.0
    if value == "@android:color/white":
        value = "#FFFFFF"
    if value == "@android:color/black":
        value = "#000000"
    alpha = 1.0
    if value.startswith("#"):
        digits = value[1:]
        if len(digits) in (3, 4):
            digits = "".join(char * 2 for char in digits)
        if len(digits) == 8:  # Android's #AARRGGBB, not SVG's #RRGGBBAA.
            alpha = int(digits[:2], 16) / 255
            digits = digits[2:]
        if len(digits) != 6 or not re.fullmatch(r"[0-9a-fA-F]{6}", digits):
            raise ValueError(f"Unsupported color: {value}")
        value = "#" + digits.upper()
    elif value not in {"none", "currentColor"}:
        raise ValueError(f"Unsupported color: {value}")
    if alpha == 0:
        return "none", 0.0
    return ("currentColor" if tint else value), alpha


def paint(out: ET.Element, node: ET.Element, colors: dict[str, str], tint: bool,
          android_name: str, svg_name: str, default: str) -> None:
    value, opacity = color(attr(node, android_name, default), colors, tint)
    out.set(svg_name, value)
    opacity *= float(attr(node, svg_name + "Alpha", "1"))
    if value != "none" and opacity != 1:
        out.set(svg_name + "-opacity", f"{opacity:.8g}")


def convert(file: Path, colors: dict[str, str], tint: bool = False) -> ET.Element:
    source = ET.parse(file).getroot()
    if source.tag == "shape":
        # The launcher background is a rectangle shape, not a VectorDrawable.
        if attr(source, "shape", "rectangle") != "rectangle":
            raise ValueError(f"Unsupported launcher shape in {file}")
        root = ET.Element("svg", {"xmlns": SVG, "width": "108", "height": "108", "viewBox": "0 0 108 108"})
        solid = source.find("solid")
        if solid is None:
            raise ValueError(f"No solid fill in {file}")
        value, opacity = color(attr(solid, "color"), colors, False)
        rect = ET.SubElement(root, "rect", {"width": "108", "height": "108", "fill": value})
        if opacity != 1:
            rect.set("fill-opacity", f"{opacity:.8g}")
        return root
    if source.tag != "vector":
        raise ValueError(f"Not an Android vector: {file}")
    width, height = attr(source, "viewportWidth"), attr(source, "viewportHeight")
    root = ET.Element("svg", {
        "xmlns": SVG,
        "width": number(attr(source, "width", width)),
        "height": number(attr(source, "height", height)),
        "viewBox": f"0 0 {number(width)} {number(height)}",
    })
    if attr(source, "autoMirrored") == "true":
        root.set("data-auto-mirrored", "true")
    if float(attr(source, "alpha", "1")) != 1:
        root.set("opacity", attr(source, "alpha"))
    clip_counter = 0

    def children(source_parent: ET.Element, output_parent: ET.Element) -> None:
        nonlocal clip_counter
        current = output_parent
        for node in source_parent:
            if node.tag == "group":
                group = ET.SubElement(current, "g")
                rotation = float(attr(node, "rotation", "0"))
                pivot_x, pivot_y = float(attr(node, "pivotX", "0")), float(attr(node, "pivotY", "0"))
                scale_x, scale_y = float(attr(node, "scaleX", "1")), float(attr(node, "scaleY", "1"))
                translate_x, translate_y = float(attr(node, "translateX", "0")), float(attr(node, "translateY", "0"))
                if (rotation, pivot_x, pivot_y, scale_x, scale_y, translate_x, translate_y) != (0, 0, 0, 1, 1, 0, 0):
                    group.set("transform", f"translate({translate_x + pivot_x:g} {translate_y + pivot_y:g}) rotate({rotation:g}) scale({scale_x:g} {scale_y:g}) translate({-pivot_x:g} {-pivot_y:g})")
                children(node, group)
            elif node.tag == "clip-path":
                clip_counter += 1
                clip_id = f"clip{clip_counter}"
                defs = ET.SubElement(root, "defs")
                clip = ET.SubElement(defs, "clipPath", {"id": clip_id})
                ET.SubElement(clip, "path", {"d": attr(node, "pathData"), "clip-rule": "evenodd" if attr(node, "fillType") == "evenOdd" else "nonzero"})
                current = ET.SubElement(current, "g", {"clip-path": f"url(#{clip_id})"})
            elif node.tag == "path":
                if any(float(attr(node, key, default)) != float(default) for key, default in (("trimPathStart", "0"), ("trimPathEnd", "1"), ("trimPathOffset", "0"))):
                    raise ValueError(f"Non-default trimPath requires conversion before export: {file}")
                path = ET.SubElement(current, "path", {"d": attr(node, "pathData")})
                paint(path, node, colors, tint, "fillColor", "fill", "#00000000")
                paint(path, node, colors, tint, "strokeColor", "stroke", "#00000000")
                if path.get("stroke") != "none":
                    path.set("stroke-width", attr(node, "strokeWidth", "0"))
                    path.set("stroke-linecap", attr(node, "strokeLineCap", "butt"))
                    path.set("stroke-linejoin", attr(node, "strokeLineJoin", "miter"))
                    path.set("stroke-miterlimit", attr(node, "strokeMiterLimit", "4"))
                path.set("fill-rule", "evenodd" if attr(node, "fillType") == "evenOdd" else "nonzero")
                if len(node):
                    raise ValueError(f"Nested path resource (for example a gradient) is not supported: {file}")
            else:
                raise ValueError(f"Unsupported vector child {node.tag} in {file}")

    children(source, root)
    return root


def serialize(node: ET.Element) -> str:
    return '<!-- Generated from Android artwork by tools/icons/vector_to_svg.py. -->\n' + ET.tostring(node, encoding="unicode") + "\n"


def write(file: Path, content: str, check: bool) -> None:
    expected = content.encode("utf-8")
    if check:
        if not file.exists() or file.read_bytes() != expected:
            raise SystemExit(f"Stale generated icon: {file}; run tools/icons/vector_to_svg.py")
    else:
        file.parent.mkdir(parents=True, exist_ok=True)
        file.write_bytes(expected)


def registry(project: Path, files: list[Path]) -> str:
    kotlin = (project / "app/src/main/java/com/catmap/dubai/ui/icons/CatMapIcons.kt").read_text(encoding="utf-8")
    constants = re.findall(r"val\s+(\w+)\s*=\s*R\.drawable\.(ic_cm_\w+)", kotlin)
    names = {constant: drawable.removeprefix("ic_cm_") for constant, drawable in constants}
    svg_names = [file.stem for file in files]
    lines = ["// Generated from CatMapIcons.kt and Badges.kt. Do not edit by hand."]
    for stem in svg_names:
        lines.append(f"import {stem}Svg from './generated/{stem}.svg?raw';")
        lines.append(f"import {stem}OriginalSvg from './generated/{stem}.original.svg?raw';")
        lines.append(f"import {stem}Url from './generated/{stem}.original.svg?url';")
    lines += ["", "export const iconSvgs = {"]
    for stem in svg_names:
        lines.append(f"  '{stem.removeprefix('ic_cm_')}': {stem}Svg,")
    lines += ["} as const;", "export type IconName = keyof typeof iconSvgs;", "", "export const iconOriginalSvgs: Record<IconName, string> = {"]
    for stem in svg_names:
        lines.append(f"  '{stem.removeprefix('ic_cm_')}': {stem}OriginalSvg,")
    lines += ["};", "export const iconUrls: Record<IconName, string> = {"]
    for stem in svg_names:
        lines.append(f"  '{stem.removeprefix('ic_cm_')}': {stem}Url,")
    lines += ["};", "", "export function catMapSvg(icon: IconName, original = false): string {", "  return original ? iconOriginalSvgs[icon] : iconSvgs[icon];", "}", "export function catMapUrl(icon: IconName): string { return iconUrls[icon]; }", ""]
    for method, enum_name in (("animal", "AnimalType"), ("category", "PostCategory"), ("layer", "MapLayer")):
        match = re.search(rf"fun {method}\([^{{]+\{{(.*?)\n    }}", kotlin, re.S)
        if match is None:
            raise ValueError(f"Could not extract {method} registry from Kotlin")
        pairs = re.findall(rf"{enum_name}\.(\w+)\s*->\s*(\w+)", match.group(1))
        mapping = {key.lower(): names[value] for key, value in pairs}
        lines.append(f"const {method}Icons: Record<string, IconName> = {json.dumps(mapping, ensure_ascii=False)};")
    badges = (project / "app/src/main/java/com/catmap/dubai/gamification/Badges.kt").read_text(encoding="utf-8")
    badge_pairs = re.findall(r"^\s+(\w+)\([^\n]*?R\.drawable\.(ic_cm_\w+)", badges, re.M)
    lines += [f"const badgeIcons: Record<string, IconName> = {json.dumps({key.lower(): value.removeprefix('ic_cm_') for key, value in badge_pairs})};",
              "export const factIcons = { age: 'age', ageGroup: 'age', color: 'colour', colour: 'colour', breed: 'breed', sex: 'sex', size: 'size', microchip: 'microchip' } as const satisfies Record<string, IconName>;",
              "export const CatMapIcons = {"]
    for constant, drawable in constants:
        lines.append(f"  {constant}: '{drawable.removeprefix('ic_cm_')}',")
    lines += [f"  all: {json.dumps(list(names.values()))} as IconName[],",
              "  animal: (key: string): IconName => animalIcons[key.toLowerCase()] ?? 'paw',",
              "  category: (key: string): IconName => categoryIcons[key.toLowerCase()] ?? 'note',",
              "  layer: (key: string): IconName => layerIcons[key.toLowerCase()] ?? 'paw',",
              "  badge: (key: string): IconName => badgeIcons[key.toLowerCase()] ?? 'badge_paw',",
              "} as const;",
              "export function preservesArtwork(icon: IconName): boolean {",
              "  return ['cat', 'dog', 'bird', 'rabbit', 'paw'].includes(icon) || icon.startsWith('badge_') || !iconSvgs[icon].includes('currentColor');",
              "}", ""]
    return "\n".join(lines)


def png_icons(project: Path, launcher: str, maskable: str) -> None:
    from PIL import Image

    output = project / "web/public"
    output.mkdir(parents=True, exist_ok=True)
    jobs = [
        ("icon-192.png", 192, launcher),
        ("icon-512.png", 512, launcher),
        ("icon-maskable-512.png", 512, maskable),
        ("apple-touch-icon.png", 180, launcher),
        ("favicon.png", 32, launcher),
    ]
    # Rendering with Chromium preserves SVG curves, arcs, and layered fills.
    # It runs against a page with only local inline artwork, without a web server.
    code = r"""
import { chromium } from 'playwright';
import { readFile } from 'node:fs/promises';
const jobs = JSON.parse(await readFile(process.argv[1], 'utf8'));
let browser;
try { browser = await chromium.launch({ headless: true }); }
catch (error) {
  if (!String(error).includes("Executable doesn't exist")) throw error;
  browser = await chromium.launch({ headless: true, channel: 'chrome' });
}
console.log(`PNG renderer: Chromium ${browser.version()}`);
try {
  const page = await browser.newPage({ deviceScaleFactor: 1 });
  for (const job of jobs) {
    await page.setViewportSize({ width: job.size, height: job.size });
    await page.setContent(`<html><head><style>html,body{margin:0;width:100%;height:100%;overflow:hidden}svg{display:block;width:100%;height:100%}</style></head><body>${job.svg}</body></html>`);
    await page.screenshot({ path: job.path, animations: 'disabled', omitBackground: false });
  }
} finally { await browser.close(); }
"""
    with tempfile.TemporaryDirectory(prefix="catmap-icons-") as temporary:
        job_file = Path(temporary) / "jobs.json"
        job_file.write_text(json.dumps([{"path": str(output / name), "size": size, "svg": svg} for name, size, svg in jobs]), encoding="utf-8")
        subprocess.run(["node", "--input-type=module", "-e", code, str(job_file)], cwd=project / "web", check=True)
    for name, size, _ in jobs:
        with Image.open(output / name) as image:
            if image.size != (size, size):
                raise ValueError(f"Invalid dimensions: {name}")
            if image.mode == "RGBA" and image.getextrema()[3] != (255, 255):
                raise ValueError(f"Unexpected transparency: {name}")
            # These launcher icons must be opaque, especially on the iOS home screen.
            image.convert("RGB").save(output / name, optimize=True)
    with Image.open(output / "icon-512.png") as image:
        image.save(output / "favicon.ico", format="ICO", sizes=[(16, 16), (32, 32), (48, 48)])
    with Image.open(output / "icon-maskable-512.png") as image:
        background = image.getpixel((0, 0))
        radius = max(math.hypot(x + 0.5 - 256, y + 0.5 - 256)
                     for y in range(512) for x in range(512)
                     if image.getpixel((x, y)) != background)
        if radius > 512 * 0.4:
            raise ValueError(f"Maskable foreground extends outside its safe zone: {radius:.2f}px")
        print(f"maskable artwork radius {radius:.2f}px <= safe-zone radius {512 * 0.4:.2f}px")
    print("wrote opaque PWA icons at 192/512, maskable 512, apple-touch 180, favicon PNG and ICO")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("project", nargs="?", type=Path, default=Path(__file__).resolve().parents[2])
    parser.add_argument("--check", action="store_true", help="Fail if generated SVGs or registry are stale; write nothing")
    parser.add_argument("--png", action="store_true", help="Also rasterize the launcher layers into PWA PNGs and favicon")
    args = parser.parse_args()
    if args.check and args.png:
        parser.error("Use --check for SVG/registry drift or --png to regenerate PNGs, not both")
    project = args.project.resolve()
    resources = project / "app/src/main/res"
    drawable = resources / "drawable"
    generated = project / "web/src/icons/generated"
    colors = colors_from(resources)
    files = sorted(drawable.glob("ic_cm_*.xml")) + [drawable / name for name in ("ic_launcher_foreground.xml", "ic_launcher_background.xml", "logo.xml", "ic_stat_cat.xml")]
    for file in files:
        name = file.stem.removeprefix("ic_cm_")
        tint = file.stem.startswith("ic_cm_") and name not in ARTWORK and not name.startswith("badge_")
        # Theme-tinted copies mirror CatMapIcon; originals mirror MarkerFactory,
        # which draws VectorDrawables without a theme tint onto map bitmaps.
        write(generated / (file.stem + ".svg"), serialize(convert(file, colors, tint)), args.check)
        write(generated / (file.stem + ".original.svg"), serialize(convert(file, colors)), args.check)
    write(project / "web/src/icons/CatMapIcons.ts", registry(project, files), args.check)
    background = convert(drawable / "ic_launcher_background.xml", colors)
    foreground = convert(drawable / "ic_launcher_foreground.xml", colors)
    launcher = ET.Element("svg", {"xmlns": SVG, "viewBox": "0 0 108 108", "width": "512", "height": "512"})
    launcher.extend(list(background))
    launcher.extend(list(foreground))
    launcher_svg = serialize(launcher)
    # The Android foreground fits inside the central 80% circle without scaling:
    # pin extents x=[28,80], y=[22,88]; farthest artwork is <43.2 from (54,54).
    write(generated / "launcher.svg", launcher_svg, args.check)
    print(f"{'checked' if args.check else 'wrote'} {len(files)} Android icons, theme/original SVG variants, launcher and TypeScript registry")
    if args.png:
        png_icons(project, launcher_svg, launcher_svg)


if __name__ == "__main__":
    main()
