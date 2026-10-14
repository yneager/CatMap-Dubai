import { expect, it } from 'vitest';
import { installPlatform } from '../src/platform/install';
it('recognizes iPad desktop user agents and iOS in-app browsers', () => {
  expect(installPlatform('Mozilla/5.0 (Macintosh) Version/26.0 Safari/605.1.15', 5)).toMatchObject({
    ios: true,
    ipad: true,
    ios26: true,
    inApp: false,
  });
  expect(installPlatform('iPhone OS 26_0 Instagram', 5)).toMatchObject({ ios: true, inApp: true });
  expect(installPlatform('iPhone OS 18_6 Version/26.0 Safari', 5).ios26).toBe(true);
  expect(installPlatform('Android Chrome', 5).android).toBe(true);
  expect(installPlatform('iPhone OS 26_0 AppleWebKit/605 Mobile/15E148', 5).inApp).toBe(true);
  expect(installPlatform('iPhone OS 26_0 CriOS/140 Mobile/15E148 Safari/604.1', 5).inApp).toBe(
    false,
  );
  expect(installPlatform('iPhone Custom Browser', 5).inApp).toBe(false);
});
