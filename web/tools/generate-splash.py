"""Generate small, local iOS portrait launch images from the existing app icon."""
from pathlib import Path
from PIL import Image

web = Path(__file__).resolve().parents[1]
out = web / 'public' / 'splash'
out.mkdir(exist_ok=True)
icon = Image.open(web / 'public' / 'icon-512.png').convert('RGBA')
sizes = [(375, 812, 3), (390, 844, 3), (393, 852, 3), (402, 874, 3), (420, 912, 3), (430, 932, 3), (440, 956, 3), (414, 896, 2)]
tags = []
for width, height, scale in sizes:
    for theme, background in [('light', '#FFFBF7'), ('dark', '#1A1410')]:
        image = Image.new('RGB', (width * scale, height * scale), background)
        mark = icon.resize((112 * scale, 112 * scale), Image.Resampling.LANCZOS)
        image.paste(mark, ((image.width - mark.width) // 2, (image.height - mark.height) // 2), mark)
        name = f'iphone-{width}x{height}-{scale}x-{theme}.png'
        image.save(out / name, optimize=True)
        media = f'(device-width: {width}px) and (device-height: {height}px) and (-webkit-device-pixel-ratio: {scale}) and (orientation: portrait) and (prefers-color-scheme: {theme})'
        tags.append(f'    <link rel="apple-touch-startup-image" href="/splash/{name}" media="{media}" />')
index = web / 'index.html'
source = index.read_text(encoding='utf-8')
start, end = '    <!-- ios-splash:start -->', '    <!-- ios-splash:end -->'
block = start + '\n' + '\n'.join(tags) + '\n' + end
if start in source:
    a = source.index(start)
    b = source.index(end, a) + len(end)
    source = source[:a] + block + source[b:]
else:
    source = source.replace('    <title>', block + '\n    <title>')
index.write_text(source, encoding='utf-8')
total = sum(p.stat().st_size for p in out.glob('*.png'))
assert total < 1_500_000, f'Splash images exceed the budget: {total}'
print(f'{len(tags)} portrait splash images, {total:,} bytes total')
