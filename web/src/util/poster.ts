import QRCode from 'qrcode';
import { t, locale, dateTime } from '../i18n';
import { PostLinks } from './links';
import { Dubai } from '../data/Neighborhoods';
import { ImageRepository } from '../data/ImageRepository';
import { enums } from '../data/model/Enums';
import { catMapSvg, CatMapIcons } from '../icons/CatMapIcons';
import type { Post } from '../data/model';
export async function makePoster(post: Post) {
  // Safari can leave the global font-ready promise pending after a map route.
  // Wait for the poster's own faces, with a bounded system-font fallback.
  await Promise.race([
    Promise.all([
      document.fonts.load('400 34px Roboto'),
      document.fonts.load('700 64px Roboto'),
      ...(locale() === 'ar'
        ? [
            document.fonts.load('400 34px "Noto Sans Arabic"'),
            document.fonts.load('700 64px "Noto Sans Arabic"'),
          ]
        : []),
    ]),
    new Promise<void>((resolve) => setTimeout(resolve, 2000)),
  ]);
  const canvas = document.createElement('canvas');
  canvas.width = 1080;
  canvas.height = 1350;
  const ctx = canvas.getContext('2d')!;
  ctx.fillStyle = '#FFFBF7';
  ctx.fillRect(0, 0, 1080, 1350);
  const category = enums.PostCategory.find((c) => c.key === post.category) ?? enums.PostCategory[0],
    accent = category.color;
  const dark =
    '#' +
    [1, 3, 5]
      .map((i) =>
        Math.trunc(parseInt(accent.slice(i, i + 2), 16) * 0.7)
          .toString(16)
          .padStart(2, '0'),
      )
      .join('');
  const gradient = ctx.createLinearGradient(0, 0, 1080, 230);
  gradient.addColorStop(0, accent);
  gradient.addColorStop(1, dark);
  ctx.fillStyle = gradient;
  ctx.fillRect(0, 0, 1080, 230);
  const font = locale() === 'ar' ? '"Noto Sans Arabic"' : 'Roboto';
  const line = (
    text: string,
    y: number,
    size: number,
    color: string,
    bold = false,
    maxLines = 1,
    center = false,
    width = 952,
  ) => {
    ctx.font = `${bold ? 700 : 400} ${size}px ${font}`;
    ctx.fillStyle = color;
    ctx.direction = locale() === 'ar' ? 'rtl' : 'ltr';
    ctx.textAlign = center ? 'center' : locale() === 'ar' ? 'right' : 'left';
    ctx.textBaseline = 'top';
    const words = text.split(/\s+/);
    const lines: string[] = [];
    let current = '';
    for (const word of words) {
      const next = current ? current + ' ' + word : word;
      if (ctx.measureText(next).width > width && current) {
        lines.push(current);
        current = word;
      } else current = next;
    }
    if (current) lines.push(current);
    lines.slice(0, maxLines).forEach((value, i) => {
      if (i === maxLines - 1 && (lines.length > maxLines || ctx.measureText(value).width > width)) {
        while (ctx.measureText(value + '…').width > width && value.length)
          value = value.slice(0, -1);
        value += '…';
      }
      ctx.fillText(
        value,
        center ? 64 + width / 2 : locale() === 'ar' ? 64 + width : 64,
        y + i * size * 1.2,
      );
    });
    return Math.min(lines.length, maxLines) * size * 1.2;
  };
  const animal = t('animal_' + post.animalType),
    headline = t('headline_' + post.category, animal).toLocaleUpperCase(locale());
  const headerHeight = line(headline, 34, 92, '#fff', true, 1, true);
  line(t('poster_help_line'), 34 + headerHeight + 4, 34, '#ffffffe6', false, 1, true);
  ctx.save();
  ctx.beginPath();
  ctx.roundRect(64, 260, 952, 600, 36);
  ctx.clip();
  const url = post.photoIds.length ? await ImageRepository.url(post.photoIds[0], false) : null;
  if (url) {
    const img = new Image();
    img.src = url;
    await img.decode();
    const scale = Math.max(952 / img.width, 600 / img.height),
      w = 952 / scale,
      h = 600 / scale;
    ctx.drawImage(img, (img.width - w) / 2, (img.height - h) / 2, w, h, 64, 260, 952, 600);
  } else {
    ctx.fillStyle = accent + '40';
    ctx.fillRect(64, 260, 952, 600);
    const img = new Image();
    img.src =
      'data:image/svg+xml,' +
      encodeURIComponent(catMapSvg(CatMapIcons.animal(post.animalType), true));
    await img.decode();
    ctx.drawImage(img, 420, 440, 240, 240);
  }
  ctx.restore();
  if (post.reward > 0) {
    const text = t('reward_aed', post.reward).toLocaleUpperCase(locale());
    ctx.font = `700 44px ${font}`;
    const width = ctx.measureText(text).width + 64,
      x = 996 - width;
    ctx.fillStyle = '#FFC233';
    ctx.beginPath();
    ctx.roundRect(x, 800, width, 80, 40);
    ctx.fill();
    ctx.fillStyle = '#3A2A00';
    ctx.textAlign = 'left';
    ctx.direction = 'ltr';
    ctx.fillText(text, x + 32, 820);
  }
  let y = 900;
  y += line(post.name || headline, y, 64, '#221A15', true);
  const facts = [
    t('microchip') + ': ' + t(post.microchip),
    post.color,
    post.breed,
    post.sex !== 'unknown' ? t('sex_' + post.sex) : '',
  ]
    .filter(Boolean)
    .join(' · ');
  y += 6 + line(facts, y + 6, 38, '#52443C');
  y +=
    10 +
    line(
      t(
        'poster_last_seen',
        Dubai.nameOf(post.neighborhood),
        dateTime(post.lastSeenAt || post.createdAt),
      ),
      y + 10,
      38,
      accent,
      true,
      2,
    );
  line(post.features || post.description, y + 10, 34, '#3C322C', false, 3);
  ctx.fillStyle = '#221A15';
  ctx.fillRect(0, 1170, 1080, 180);
  const contact =
    post.contactMethod === 'phone' && post.contactPhone
      ? t('poster_call', post.contactPhone)
      : post.contactMethod === 'email' && post.contactEmail
        ? t('poster_email', post.contactEmail)
        : t('poster_in_app', post.ownerName);
  line(contact, 1194, 32, '#fff', true, 2, true, post.id ? 772 : 952);
  line(t('poster_footer'), 1296, 30, '#ffffffc8', false, 1, true, post.id ? 772 : 952);
  if (post.id) {
    const qr = await QRCode.toCanvas(PostLinks.create(post.id), { width: 128, margin: 2 });
    ctx.drawImage(qr, 888, 1210, 128, 128);
  }
  return canvas;
}
