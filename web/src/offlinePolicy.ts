/** Serialized into the service worker: keep this predicate self-contained and limited to static route chunks. */
export function optionalRouteAsset({
  url,
  sameOrigin,
  request,
}: {
  url: URL;
  sameOrigin: boolean;
  request: Request;
}): boolean {
  return (
    sameOrigin &&
    request.destination === 'script' &&
    /^\/assets\/(?:AdminScreens|PosterScreen)-[A-Za-z0-9_-]+\.js$/.test(url.pathname)
  );
}
