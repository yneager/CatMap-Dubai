import { AppException } from '../data/Firestore';
export interface ProcessedPhoto {
  full: Uint8Array;
  thumb: Uint8Array;
  analysis: HTMLCanvasElement;
}
export function scaleToMax(
  source: CanvasImageSource & { width: number; height: number },
  max: number,
) {
  const ratio = Math.min(1, max / Math.max(source.width, source.height));
  const canvas = document.createElement('canvas');
  canvas.width = Math.max(1, Math.round(source.width * ratio));
  canvas.height = Math.max(1, Math.round(source.height * ratio));
  const ctx = canvas.getContext('2d');
  if (!ctx) throw new AppException('err_photo');
  ctx.fillStyle = '#fff';
  ctx.fillRect(0, 0, canvas.width, canvas.height);
  ctx.drawImage(source, 0, 0, canvas.width, canvas.height);
  return canvas;
}
export async function decodeUpright(file: Blob) {
  try {
    if (typeof createImageBitmap === 'function')
      return await createImageBitmap(file, { imageOrientation: 'from-image' });
  } catch {
    /* Safari can use the image element decoder for HEIC. */
  }
  const url = URL.createObjectURL(file);
  try {
    const img = new Image();
    img.src = url;
    await img.decode();
    return img;
  } catch {
    throw new AppException('err_photo');
  } finally {
    URL.revokeObjectURL(url);
  }
}
export const compress = (canvas: HTMLCanvasElement, quality: number) =>
  new Promise<Uint8Array>((resolve, reject) =>
    canvas.toBlob(
      async (blob) => {
        if (blob) resolve(new Uint8Array(await blob.arrayBuffer()));
        else reject(new AppException('err_photo'));
      },
      'image/jpeg',
      quality / 100,
    ),
  );
export async function compressUnder<T>(
  source: T,
  encode: (source: T, quality: number) => Promise<Uint8Array>,
  shrink: (source: T, ratio: number) => T,
  maxBytes = 650000,
) {
  let current = source,
    quality = 85;
  for (let n = 0; n < 100; n++) {
    const bytes = await encode(current, quality);
    if (bytes.byteLength <= maxBytes) return bytes;
    if (quality > 55) quality -= 10;
    else {
      current = shrink(current, 0.8);
      quality = 80;
    }
  }
  throw new AppException('err_photo');
}
export async function processPhoto(file: Blob): Promise<ProcessedPhoto> {
  const source = await decodeUpright(file);
  try {
    return {
      full: await compressUnder(scaleToMax(source, 1280), compress, (canvas, ratio) =>
        scaleToMax(canvas, Math.round(Math.max(canvas.width, canvas.height) * ratio)),
      ),
      thumb: await compress(scaleToMax(source, 360), 72),
      analysis: scaleToMax(source, 512),
    };
  } finally {
    if ('close' in source) source.close();
  }
}
export function centreCrop(source: HTMLCanvasElement) {
  const canvas = document.createElement('canvas');
  const side = Math.max(1, Math.trunc(Math.min(source.width, source.height) * Math.fround(0.85)));
  canvas.width = side;
  canvas.height = side;
  canvas
    .getContext('2d')!
    .drawImage(
      source,
      Math.trunc((source.width - side) / 2),
      Math.trunc((source.height - side) / 2),
      side,
      side,
      0,
      0,
      side,
      side,
    );
  return scaleToMax(canvas, 320);
}
