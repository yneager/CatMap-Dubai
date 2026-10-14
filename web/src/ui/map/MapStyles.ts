import type { Map, StyleSpecification } from 'maplibre-gl';
import { config } from '../../config';
const DAY = {
  background: '#F4F1FF',
  water: '#5EC8F8',
  park: '#93E07A',
  sand: '#FFE7A6',
  residential: '#EEE8FB',
  building: '#E2DAF6',
  building3d: '#D9CCF5',
  motorway: '#FF8A4C',
  major: '#FFC94D',
  secondary: '#FFFFFF',
  minor: '#FFFFFF',
  casing: '#D6CCEB',
  label: '#2B2350',
  halo: '#FFFFFF',
  rail: '#B9AEDB',
};
const NIGHT = {
  background: '#121A2E',
  water: '#1C4F8F',
  park: '#1D5A3A',
  sand: '#3B3420',
  residential: '#18213A',
  building: '#25304F',
  building3d: '#2E3B63',
  motorway: '#FF7A3D',
  major: '#FFC247',
  secondary: '#7D8CFF',
  minor: '#33406A',
  casing: '#0B1124',
  label: '#E9EDFF',
  halo: '#0B1020',
  rail: '#46527A',
};
export function mapStyle(
  mode: string,
  dark: boolean,
  satellite: boolean,
): string | StyleSpecification {
  return satellite && config.maptiler
    ? {
        version: 8,
        glyphs: 'https://tiles.openfreemap.org/fonts/{fontstack}/{range}.pbf',
        sources: {
          satellite: {
            type: 'raster',
            tiles: [
              `https://api.maptiler.com/tiles/satellite-v2/{z}/{x}/{y}.jpg?key=${config.maptiler}`,
            ],
            tileSize: 512,
            maxzoom: 20,
            attribution: '© MapTiler © OpenStreetMap contributors',
          },
        },
        layers: [{ id: 'satellite', type: 'raster', source: 'satellite' }],
      }
    : mode === 'classic' && dark
      ? 'https://tiles.openfreemap.org/styles/dark'
      : 'https://tiles.openfreemap.org/styles/liberty';
}
export function customize(
  map: Map,
  mode: string,
  dark: boolean,
  satellite: boolean,
  language: string,
) {
  const p = dark ? NIGHT : DAY,
    modern = mode === 'modern' && !(satellite && config.maptiler);
  for (const l of map.getStyle().layers ?? []) {
    try {
      const id = l.id;
      if (l.type === 'symbol') {
        const field = l.layout?.['text-field'];
        if (JSON.stringify(field)?.includes('name') && !JSON.stringify(field).includes('ref'))
          map.setLayoutProperty(
            id,
            'text-field',
            language === 'ar'
              ? ['coalesce', ['get', 'name:ar'], ['get', 'name:nonlatin'], ['get', 'name']]
              : [
                  'coalesce',
                  ['get', 'name:en'],
                  ['get', 'name_en'],
                  ['get', 'name:latin'],
                  ['get', 'name'],
                ],
          );
        if (modern) {
          if (/^poi_r7|^poi_r1|^poi_transit/.test(id))
            map.setLayoutProperty(id, 'visibility', 'none');
          else {
            map.setPaintProperty(id, 'text-color', p.label);
            map.setPaintProperty(id, 'text-halo-color', p.halo);
            map.setPaintProperty(id, 'text-halo-width', 1.4);
          }
        }
      }
      if (!modern) continue;
      if (l.type === 'background') map.setPaintProperty(id, 'background-color', p.background);
      if (l.type === 'fill-extrusion') {
        map.setPaintProperty(id, 'fill-extrusion-color', p.building3d);
        map.setPaintProperty(id, 'fill-extrusion-opacity', 0.9);
      }
      if (l.type === 'fill') {
        const color = id.startsWith('water')
          ? p.water
          : /park|grass|wood|pitch|cemetery/.test(id)
            ? p.park
            : id.includes('sand')
              ? p.sand
              : id.includes('residential')
                ? p.residential
                : id === 'building'
                  ? p.building
                  : null;
        if (color) map.setPaintProperty(id, 'fill-color', color);
        if (/park|grass|wood|pitch|cemetery/.test(id))
          map.setPaintProperty(id, 'fill-opacity', 0.85);
      }
      if (l.type === 'line') {
        const color = id.includes('waterway')
          ? p.water
          : id.includes('casing')
            ? p.casing
            : id.includes('rail')
              ? p.rail
              : id.includes('motorway')
                ? p.motorway
                : /trunk|primary/.test(id)
                  ? p.major
                  : /secondary|tertiary/.test(id)
                    ? p.secondary
                    : /minor|street|link|service/.test(id)
                      ? p.minor
                      : id.includes('park_outline')
                        ? p.park
                        : null;
        if (color) map.setPaintProperty(id, 'line-color', color);
      }
    } catch {
      /* Optional properties differ among tile style versions. */
    }
  }
  if (modern && map.getLayer('building-3d'))
    map.setPaintProperty('building-3d', 'fill-extrusion-height', [
      'interpolate',
      ['linear'],
      ['zoom'],
      14,
      0,
      15.5,
      ['get', 'render_height'],
    ]);
}
