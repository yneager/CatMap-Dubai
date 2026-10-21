const allowed =
  /^(?:(?:post|cat|sighting|rescue|station|sitter|user|match|chat)\/[A-Za-z0-9][A-Za-z0-9_.-]{0,299}|adoption-requests|achievements|profile|bookings|bookings\?tab=(?:sitter|owner)|notifications)$/;
export function alertRoute(route: unknown): string | null {
  return typeof route === 'string' && allowed.test(route) ? '/' + route : null;
}
