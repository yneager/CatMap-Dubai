const BASE32 = '0123456789bcdefghjkmnpqrstuvwxyz';
export const Geohash = {
  encode(lat: number, lng: number, precision: number): string {
    let latLo = -90,
      latHi = 90,
      lngLo = -180,
      lngHi = 180;
    let out = '',
      even = true,
      bit = 0,
      ch = 0;
    while (out.length < precision) {
      const mid = even ? (lngLo + lngHi) / 2 : (latLo + latHi) / 2;
      const on = (even ? lng : lat) >= mid;
      ch = ch * 2 + Number(on);
      if (even) {
        if (on) lngLo = mid;
        else lngHi = mid;
      } else {
        if (on) latLo = mid;
        else latHi = mid;
      }
      even = !even;
      if (++bit === 5) {
        out += BASE32[ch];
        bit = 0;
        ch = 0;
      }
    }
    return out;
  },
  cells(lat: number, lng: number): string[] {
    if (lat === 0 && lng === 0) return [];
    const full = Geohash.encode(lat, lng, 6);
    return [4, 5, 6].map((n) => full.slice(0, n));
  },
  cellSize(precision: number): [number, number] {
    return [180 / 2 ** Math.floor((precision * 5) / 2), 360 / 2 ** Math.ceil((precision * 5) / 2)];
  },
  origin(hash: string): [number, number] {
    let latLo = -90,
      latHi = 90,
      lngLo = -180,
      lngHi = 180,
      even = true;
    for (const c of hash) {
      const v = BASE32.indexOf(c);
      if (v < 0) throw new Error('Invalid geohash');
      for (let i = 4; i >= 0; i--) {
        const on = ((v >> i) & 1) === 1;
        if (even) {
          const mid = (lngLo + lngHi) / 2;
          if (on) lngLo = mid;
          else lngHi = mid;
        } else {
          const mid = (latLo + latHi) / 2;
          if (on) latLo = mid;
          else latHi = mid;
        }
        even = !even;
      }
    }
    return [latLo, lngLo];
  },
  covering(
    south: number,
    west: number,
    north: number,
    east: number,
    precision: number,
    max = 64,
  ): string[] {
    const [h, w] = Geohash.cellSize(precision);
    const s = Math.max(Math.min(south, north), -89.999999),
      n = Math.min(Math.max(south, north), 89.999999);
    const out = new Set<string>();
    for (let lat = s; ; lat = Math.min(lat + h, n)) {
      for (let lng = west; ; lng = Math.min(lng + w, east)) {
        out.add(Geohash.encode(lat, lng, precision));
        if (out.size > max) return [...out];
        if (lng >= east) break;
      }
      if (lat >= n) break;
    }
    return [...out];
  },
  around(lat: number, lng: number, radiusKm: number, max = 6): string[] {
    const dy = radiusKm / 111,
      dx = radiusKm / (111 * Math.max(0.1, Math.cos((lat * Math.PI) / 180)));
    for (const p of [6, 5, 4]) {
      const cells = Geohash.covering(lat - dy, lng - dx, lat + dy, lng + dx, p, max);
      if (cells.length <= max) return cells;
    }
    return Geohash.covering(lat - dy, lng - dx, lat + dy, lng + dx, 4);
  },
};
