import { useEffect, useRef, useState } from 'react';
import * as maplibregl from 'maplibre-gl';
import type { GeoJSONSource, Map as LibreMap } from 'maplibre-gl';
import 'maplibre-gl/dist/maplibre-gl.css';
import mapWorkerUrl from 'maplibre-gl/dist/maplibre-gl-worker.mjs?worker&url';
maplibregl.setWorkerUrl(mapWorkerUrl);
// The package export is its source module; MapLibre needs the complete worker bundle.
import rtlPluginUrl from '../../../node_modules/@mapbox/mapbox-gl-rtl-text/dist/mapbox-gl-rtl-text.js?url';
if (maplibregl.getRTLTextPluginStatus() === 'unavailable')
  void maplibregl.setRTLTextPlugin(rtlPluginUrl, true).catch(() => {});
import { useSettings } from '../../state';
import { mapStyle, customize } from './MapStyles';
import { CatMapIcons, catMapSvg } from '../../icons/CatMapIcons';
import { enums } from '../../data/model/Enums';
import { t } from '../../i18n';
import { config } from '../../config';
export interface MapPoint {
  id: string;
  kind: 'post' | 'cat' | 'station' | 'sitter';
  lat: number;
  lng: number;
  category?: string;
  animalType?: string;
  coat?: string;
  pulse?: boolean;
}
export function circlePolygon(lat: number, lng: number, radius: number) {
  const ring = Array.from({ length: 65 }, (_, i) => {
    const angle = (i / 64) * Math.PI * 2;
    return [
      lng + (Math.cos(angle) * radius) / (111000 * Math.cos((lat * Math.PI) / 180)),
      lat + (Math.sin(angle) * radius) / 111000,
    ];
  });
  return {
    type: 'Feature' as const,
    properties: {},
    geometry: { type: 'Polygon' as const, coordinates: [ring] },
  };
}
let memory = { center: [55.2708, 25.2048] as [number, number], zoom: 11.3, bearing: 0 };
async function marker(point: MapPoint) {
  const canvas = document.createElement('canvas');
  canvas.width = 92;
  canvas.height = 108;
  const ctx = canvas.getContext('2d')!;
  ctx.scale(2, 2);
  if (point.kind === 'cat') {
    canvas.width = 76;
    canvas.height = 96;
    ctx.scale(2, 2);
    const cx = 19,
      cy = 21,
      hr = 9.5;
    const pin = new Path2D();
    pin.arc(19, 19, 17, 0, Math.PI * 2);
    pin.moveTo(9.65, 32.6);
    pin.lineTo(19, 46.5);
    pin.lineTo(28.35, 32.6);
    pin.closePath();
    ctx.save();
    ctx.translate(0, 1.5);
    ctx.fillStyle = '#00000046';
    ctx.fill(pin);
    ctx.restore();
    ctx.fillStyle = '#fff';
    ctx.fill(pin);
    ctx.strokeStyle = point.pulse ? '#E53935' : '#8D6E63';
    ctx.lineWidth = 2.5;
    ctx.stroke(pin);
    const head = new Path2D();
    head.arc(cx, cy, hr, 0, Math.PI * 2);
    for (const sign of [-1, 1]) {
      head.moveTo(cx + sign * hr * 0.95, cy - hr * 0.2);
      head.lineTo(cx + sign * hr * 0.85, cy - hr * 1.55);
      head.lineTo(cx + sign * hr * 0.15, cy - hr * 0.85);
      head.closePath();
    }
    const coat = enums.CoatColor.find((c) => c.key === point.coat) ?? enums.CoatColor.at(-1)!;
    const colors = coat.colors;
    const gradient = ctx.createLinearGradient(cx - hr, cy - hr, cx + hr, cy + hr);
    colors.forEach((color, i) => {
      gradient.addColorStop(i / colors.length, color);
      gradient.addColorStop((i + 1) / colors.length, color);
    });
    ctx.fillStyle = new Set(colors).size === 1 ? colors[0] : gradient;
    ctx.fill(head);
    ctx.strokeStyle = '#3C281E';
    ctx.lineWidth = 1.2;
    ctx.stroke(head);
    const rgb = colors[0].match(/[a-f\d]{2}/gi)!.map((x) => parseInt(x, 16) / 255);
    const linear = rgb.map((x) => (x <= 0.04045 ? x / 12.92 : ((x + 0.055) / 1.055) ** 2.4));
    ctx.fillStyle =
      0.2126 * linear[0] + 0.7152 * linear[1] + 0.0722 * linear[2] < 0.35 ? '#F0C83C' : '#281E14';
    for (const sign of [-1, 1]) {
      ctx.beginPath();
      ctx.arc(cx + sign * hr * 0.38, cy - hr * 0.05, hr * 0.14, 0, Math.PI * 2);
      ctx.fill();
    }
    ctx.fillStyle = '#E68282';
    ctx.beginPath();
    ctx.arc(cx, cy + hr * 0.3, hr * 0.09, 0, Math.PI * 2);
    ctx.fill();
    return ctx.getImageData(0, 0, 76, 96);
  }
  const ring =
    point.kind === 'post'
      ? (enums.PostCategory.find((c) => c.key === point.category)?.color ?? '#546E7A')
      : point.kind === 'station'
        ? '#00897B'
        : point.kind === 'sitter'
          ? '#5E35B1'
          : point.pulse
            ? '#E53935'
            : '#8D6E63';
  ctx.fillStyle = ring;
  ctx.beginPath();
  ctx.moveTo(16, 39);
  ctx.lineTo(23, 53);
  ctx.lineTo(30, 39);
  ctx.fill();
  ctx.beginPath();
  ctx.arc(23, 23, 20, 0, Math.PI * 2);
  ctx.fill();
  ctx.fillStyle = '#fff';
  ctx.beginPath();
  ctx.arc(23, 23, 16.5, 0, Math.PI * 2);
  ctx.fill();
  const name =
    point.kind === 'post'
      ? CatMapIcons.animal(point.animalType ?? 'cat')
      : point.kind === 'station'
        ? CatMapIcons.STATION
        : CatMapIcons.SITTER;
  const img = new Image();
  img.src = 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(catMapSvg(name, true));
  await img.decode();
  ctx.drawImage(img, 11, 11, 24, 24);
  return ctx.getImageData(0, 0, 92, 108);
}
export default function CommunityMap({
  points = [],
  selected,
  onSelect,
  center,
  onMove,
  radius = 0,
  interactive = true,
  compact = false,
  location,
  focus,
}: {
  points?: MapPoint[];
  selected?: string;
  onSelect?: (p: MapPoint) => void;
  center?: [number, number];
  onMove?: (lat: number, lng: number) => void;
  radius?: number;
  interactive?: boolean;
  compact?: boolean;
  location?: { lat: number; lng: number } | null;
  focus?: { lat: number; lng: number; zoom: number } | null;
}) {
  const container = useRef<HTMLDivElement>(null),
    mapRef = useRef<LibreMap | null>(null),
    pointsRef = useRef(points),
    moveRef = useRef(onMove),
    selectRef = useRef(onSelect);
  pointsRef.current = points;
  moveRef.current = onMove;
  selectRef.current = onSelect;
  const settings = useSettings(),
    [ready, setReady] = useState(0),
    [error, setError] = useState('');
  const dark = document.documentElement.dataset.theme === 'dark';
  const styleKey = settings.mapMode + dark + settings.satellite + settings.language;
  useEffect(() => {
    if (!container.current) return;
    let map: LibreMap;
    try {
      map = new maplibregl.Map({
        container: container.current,
        style: mapStyle(settings.mapMode, dark, settings.satellite),
        center: center ?? memory.center,
        zoom: center ? 14 : memory.zoom,
        bearing: memory.bearing,
        pitch: settings.mapMode === 'modern' && !compact ? 45 : 0,
        interactive,
        attributionControl: { compact: true },
      });
      mapRef.current = map;
      if (config.emulators) {
        const testHost = container.current as HTMLDivElement & { catmapMap?: LibreMap };
        testHost.catmapMap = map;
      }
    } catch {
      setError(t('web_map_unavailable'));
      return;
    }
    map.on('error', () => setError(t('web_map_unavailable')));
    map.on('style.load', () => {
      setError('');
      customize(map, settings.mapMode, dark, settings.satellite, settings.language);
      map.addSource('points', {
        type: 'geojson',
        data: { type: 'FeatureCollection', features: [] },
        cluster: true,
        clusterRadius: 50,
        clusterMaxZoom: 14,
      });
      map.addLayer({
        id: 'clusters',
        type: 'circle',
        source: 'points',
        filter: ['has', 'point_count'],
        paint: {
          'circle-color': '#A9531A',
          'circle-radius': ['step', ['get', 'point_count'], 20, 10, 25, 50, 30],
          'circle-stroke-width': 3,
          'circle-stroke-color': '#fff',
        },
      });
      map.addLayer({
        id: 'cluster-count',
        type: 'symbol',
        source: 'points',
        filter: ['has', 'point_count'],
        layout: {
          'text-field': ['get', 'point_count_abbreviated'],
          'text-size': 14,
          'text-font': ['Noto Sans Bold'],
        },
        paint: { 'text-color': '#fff' },
      });
      map.addLayer({
        id: 'urgent',
        type: 'circle',
        source: 'points',
        filter: ['all', ['!', ['has', 'point_count']], ['==', ['get', 'pulse'], true]],
        paint: { 'circle-color': '#E53935', 'circle-radius': 30, 'circle-opacity': 0.18 },
      });
      map.addLayer({
        id: 'markers',
        type: 'symbol',
        source: 'points',
        filter: ['!', ['has', 'point_count']],
        layout: {
          'icon-image': ['get', 'icon'],
          'icon-size': 0.5,
          'icon-anchor': 'bottom',
          'icon-allow-overlap': true,
        },
      });
      map.addSource('heat', { type: 'geojson', data: { type: 'FeatureCollection', features: [] } });
      map.addLayer(
        {
          id: 'heat',
          type: 'heatmap',
          source: 'heat',
          layout: { visibility: settings.heatmap ? 'visible' : 'none' },
          paint: { 'heatmap-radius': 35, 'heatmap-opacity': 0.5 },
        },
        'urgent',
      );
      map.addSource('area', { type: 'geojson', data: { type: 'FeatureCollection', features: [] } });
      map.addLayer(
        {
          id: 'area-fill',
          type: 'fill',
          source: 'area',
          paint: { 'fill-color': '#A9531A', 'fill-opacity': 0.12 },
        },
        'urgent',
      );
      map.addLayer(
        {
          id: 'area-line',
          type: 'line',
          source: 'area',
          paint: { 'line-color': '#A9531A', 'line-width': 2, 'line-dasharray': [3, 2] },
        },
        'urgent',
      );
      map.addSource('user', { type: 'geojson', data: { type: 'FeatureCollection', features: [] } });
      map.addLayer({
        id: 'user',
        type: 'circle',
        source: 'user',
        paint: {
          'circle-color': '#2979FF',
          'circle-radius': 7,
          'circle-stroke-color': '#fff',
          'circle-stroke-width': 3,
        },
      });
      setReady((n) => n + 1);
    });
    map.on('click', 'clusters', async (e) => {
      const feature = e.features?.[0];
      if (!feature || feature.geometry.type !== 'Point') return;
      const zoom = await (map.getSource('points') as GeoJSONSource).getClusterExpansionZoom(
        Number(feature.properties?.cluster_id),
      );
      map.easeTo({ center: feature.geometry.coordinates as [number, number], zoom });
    });
    map.on('click', 'markers', (e) => {
      const id = String(e.features?.[0]?.properties?.id ?? '');
      const p = pointsRef.current.find((p) => p.id === id);
      if (p) selectRef.current?.(p);
    });
    map.on('moveend', () => {
      const pos = map.getCenter();
      moveRef.current?.(pos.lat, pos.lng);
      if (!compact)
        memory = { center: [pos.lng, pos.lat], zoom: map.getZoom(), bearing: map.getBearing() };
    });
    return () => {
      mapRef.current = null;
      map.remove();
    };
    // A style change replaces MapLibre's full style and attached application layers.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [styleKey, compact, interactive]);
  useEffect(() => {
    const map = mapRef.current;
    if (!map || !ready || !map.getSource('points')) return;
    let alive = true;
    void (async () => {
      const features = [];
      for (const p of points) {
        const icon = `${p.kind}:${p.category ?? p.coat ?? ''}:${p.animalType ?? ''}:${p.pulse ?? false}`;
        if (!map.hasImage(icon)) {
          const bitmap = await marker(p);
          if (!alive || !map.getSource('points')) return;
          map.addImage(icon, bitmap, { pixelRatio: 1 });
        }
        features.push({
          type: 'Feature' as const,
          properties: { id: p.id, icon, pulse: p.pulse ?? false },
          geometry: { type: 'Point' as const, coordinates: [p.lng, p.lat] },
        });
      }
      if (alive) {
        (map.getSource('points') as GeoJSONSource).setData({ type: 'FeatureCollection', features });
        (map.getSource('heat') as GeoJSONSource).setData({
          type: 'FeatureCollection',
          features: features.filter((f) => f.properties.pulse),
        });
      }
    })();
    return () => {
      alive = false;
    };
  }, [points, ready]);
  useEffect(() => {
    const map = mapRef.current;
    if (!map || !ready) return;
    if (map.getLayer('markers'))
      map.setLayoutProperty('markers', 'icon-size', [
        'case',
        ['==', ['get', 'id'], selected ?? ''],
        0.675,
        0.5,
      ]);
  }, [selected, ready]);
  useEffect(() => {
    const map = mapRef.current;
    if (!map || !ready || !map.getSource('area')) return;
    const pos = center ?? [map.getCenter().lng, map.getCenter().lat];
    (map.getSource('area') as GeoJSONSource).setData(
      radius > 0
        ? circlePolygon(pos[1], pos[0], radius)
        : { type: 'FeatureCollection', features: [] },
    );
  }, [radius, center, ready]);
  useEffect(() => {
    const map = mapRef.current;
    if (!map || !ready || !map.getSource('user')) return;
    (map.getSource('user') as GeoJSONSource).setData(
      location
        ? {
            type: 'Feature',
            properties: {},
            geometry: { type: 'Point', coordinates: [location.lng, location.lat] },
          }
        : { type: 'FeatureCollection', features: [] },
    );
    if (location && !compact) map.flyTo({ center: [location.lng, location.lat], zoom: 14 });
  }, [location, ready, compact]);
  useEffect(() => {
    const map = mapRef.current;
    if (
      !map ||
      !ready ||
      !map.getLayer('urgent') ||
      matchMedia('(prefers-reduced-motion: reduce)').matches
    )
      return;
    let frame = 0;
    const pulse = (at: number) => {
      if (!map.getLayer('urgent')) return;
      map.setPaintProperty('urgent', 'circle-radius', 25 + 6 * Math.sin(at / 450));
      frame = requestAnimationFrame(pulse);
    };
    frame = requestAnimationFrame(pulse);
    return () => cancelAnimationFrame(frame);
  }, [ready]);

  useEffect(() => {
    const map = mapRef.current;
    if (map && focus && ready)
      map.flyTo({
        center: [focus.lng, focus.lat],
        zoom: focus.zoom,
        duration: matchMedia('(prefers-reduced-motion: reduce)').matches ? 0 : 900,
      });
  }, [focus, ready]);
  return (
    <div className={'community-map ' + (compact ? 'compact' : '')} dir="ltr">
      <div className="map-canvas" ref={container} />
      {error && (
        <div className="map-error" role="status">
          {error}
        </div>
      )}
    </div>
  );
}
