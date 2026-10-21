import { useEffect, useSyncExternalStore } from 'react';
import { MapRepository } from './MapRepository';
export function useMapArea(uid: string, enabled = true) {
  const area = useSyncExternalStore(MapRepository.subscribe, MapRepository.get);
  useEffect(() => {
    if (!uid || !enabled) return;
    void MapRepository.start(uid);
    return MapRepository.stop;
  }, [uid, enabled]);
  return area;
}
