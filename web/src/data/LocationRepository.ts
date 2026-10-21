export interface DeviceFix {
  lat: number;
  lng: number;
  accuracy?: number;
}
let fix: DeviceFix | null = null;
const listeners = new Set<() => void>();
// Like AppContainer.location.last: a session-only GPS fix shared by nearby screens.
export const LocationRepository = {
  get: () => fix,
  subscribe: (listener: () => void) => {
    listeners.add(listener);
    return () => {
      listeners.delete(listener);
    };
  },
  set: (position: DeviceFix | null) => {
    fix = position;
    listeners.forEach((listener) => listener());
  },
};
