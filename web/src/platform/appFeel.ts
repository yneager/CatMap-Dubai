const editable = (target: EventTarget | null) =>
  target instanceof Element &&
  !!target.closest('input,textarea,select,[contenteditable]:not([contenteditable="false"])');
const zoomSurface = (target: EventTarget | null) =>
  target instanceof Element && !!target.closest('.community-map,.zoom-viewer');
export function startAppFeel() {
  let zoomTouch = false;
  let scrollTouch = { x: 0, y: 0 };
  document.addEventListener(
    'touchstart',
    (event) => {
      zoomTouch = zoomSurface(event.target);
      const point = event.touches[0];
      if (point) scrollTouch = { x: point.clientX, y: point.clientY };
    },
    { passive: true, capture: true },
  );
  // Fixed app scrolling also prevents bounce on WebKit builds without overscroll CSS.
  document.addEventListener(
    'touchmove',
    (event) => {
      if (zoomTouch || event.touches.length !== 1 || editable(event.target)) return;
      const point = event.touches[0],
        dx = point.clientX - scrollTouch.x,
        dy = point.clientY - scrollTouch.y;
      scrollTouch = { x: point.clientX, y: point.clientY };
      if (Math.abs(dx) > Math.abs(dy)) return;
      let scroller = event.target instanceof Element ? event.target : null;
      while (
        scroller &&
        !(
          scroller instanceof HTMLElement &&
          /(auto|scroll)/.test(getComputedStyle(scroller).overflowY) &&
          scroller.scrollHeight > scroller.clientHeight
        )
      )
        scroller = scroller.parentElement;
      if (
        !scroller ||
        (dy > 0 && scroller.scrollTop <= 0) ||
        (dy < 0 && scroller.scrollTop + scroller.clientHeight >= scroller.scrollHeight - 1)
      )
        event.preventDefault();
    },
    { passive: false },
  );
  document.addEventListener(
    'pointerdown',
    () => {
      document.documentElement.dataset.inputMode = 'pointer';
    },
    true,
  );
  for (const name of ['gesturestart', 'gesturechange', 'gestureend'])
    document.addEventListener(
      name,
      (event) => {
        if (!zoomTouch && !zoomSurface(event.target)) event.preventDefault();
      },
      { passive: false },
    );
  document.addEventListener(
    'wheel',
    (event) => {
      if (event.ctrlKey || event.metaKey) event.preventDefault();
    },
    { passive: false, capture: true },
  );
  document.addEventListener('keydown', (event) => {
    if (event.key === 'Tab') document.documentElement.dataset.inputMode = 'keyboard';
    if ((event.ctrlKey || event.metaKey) && ['+', '=', '-', '0'].includes(event.key))
      event.preventDefault();
  });
  document.addEventListener('contextmenu', (event) => {
    if (!editable(event.target)) event.preventDefault();
  });
  document.addEventListener('dragstart', (event) => {
    if (event.target instanceof HTMLImageElement) event.preventDefault();
  });
  document.addEventListener(
    'click',
    (event) => {
      const link = event.target instanceof Element ? event.target.closest('a') : null;
      if (!link?.href) return;
      const url = new URL(link.href, location.href);
      if (url.origin === location.origin) link.removeAttribute('target');
      else if (['https:', 'http:'].includes(url.protocol)) {
        link.target = '_blank';
        link.rel = 'noopener noreferrer';
      }
    },
    true,
  );
  const images = () =>
    document
      .querySelectorAll('img:not([draggable="false"])')
      .forEach((image) => image.setAttribute('draggable', 'false'));
  new MutationObserver(images).observe(document.documentElement, {
    childList: true,
    subtree: true,
  });
  images();
}
export function edgeBack(element: HTMLElement, goBack: () => void) {
  let start: { x: number; y: number; rtl: boolean } | null = null,
    delta = 0,
    dragging = false;
  const reset = () => {
    element.style.removeProperty('--edge-back-x');
    element.classList.remove('edge-dragging');
    start = null;
    delta = 0;
    dragging = false;
  };
  const onStart = (event: TouchEvent) => {
    reset();
    if (
      event.touches.length !== 1 ||
      zoomSurface(event.target) ||
      editable(event.target) ||
      document.querySelector('dialog[open]')
    )
      return;
    const touch = event.touches[0],
      bounds = element.getBoundingClientRect(),
      rtl = document.documentElement.dir === 'rtl';
    const edge = rtl ? bounds.right - touch.clientX : touch.clientX - bounds.left;
    if (edge >= 0 && edge <= 20) start = { x: touch.clientX, y: touch.clientY, rtl };
  };
  const onMove = (event: TouchEvent) => {
    if (!start || event.touches.length !== 1) {
      reset();
      return;
    }
    const touch = event.touches[0],
      dx = (touch.clientX - start.x) * (start.rtl ? -1 : 1),
      dy = Math.abs(touch.clientY - start.y);
    if (!dragging && dy > 12 && dy > Math.abs(dx)) {
      reset();
      return;
    }
    if (dx > 12 && dx > dy) dragging = true;
    if (!dragging) return;
    event.preventDefault();
    delta = Math.max(0, dx);
    element.classList.add('edge-dragging');
    element.style.setProperty('--edge-back-x', `${delta * (start.rtl ? -1 : 1)}px`);
  };
  const onEnd = () => {
    const back = dragging && delta > Math.min(90, element.clientWidth * 0.25);
    reset();
    if (back) goBack();
  };
  element.addEventListener('touchstart', onStart, { passive: true });
  element.addEventListener('touchmove', onMove, { passive: false });
  element.addEventListener('touchend', onEnd);
  element.addEventListener('touchcancel', reset);
  return () => {
    reset();
    element.removeEventListener('touchstart', onStart);
    element.removeEventListener('touchmove', onMove);
    element.removeEventListener('touchend', onEnd);
    element.removeEventListener('touchcancel', reset);
  };
}
