// Reliable "where am I?" for the complaint form.
//
// Why not a single getCurrentPosition call: with enableHighAccuracy and a short
// timeout it fails often indoors and on laptops (no GPS chip). Strategy here:
//   1. Ask for a high-accuracy fix and keep listening for a few seconds, taking the
//      best reading; stop early once it is accurate enough.
//   2. If that produced nothing (timeout / position unavailable), fall back to a
//      low-accuracy request (Wi-Fi / cell / IP based), which almost always answers.
//   3. Permission denied is final - retrying can't help - so it is reported at once.
// Errors carry a machine-readable `code` and describeLocateError() turns it into a
// message telling the user what to actually do.

export class LocateError extends Error {
  constructor(code, message) {
    super(message || code);
    this.name = 'LocateError';
    this.code = code; // 'insecure' | 'unsupported' | 'denied' | 'unavailable' | 'timeout'
  }
}

function fromPositionError(err) {
  if (err && err.code === 1) return new LocateError('denied');
  if (err && err.code === 3) return new LocateError('timeout');
  return new LocateError('unavailable');
}

function toResult(pos) {
  return { lat: pos.coords.latitude, lon: pos.coords.longitude, accuracy: pos.coords.accuracy };
}

/**
 * Resolves { lat, lon, accuracy } (accuracy in metres) or rejects with a LocateError.
 * options.fastMs - how long to listen for a high-accuracy fix (default 10 s)
 * options.goodAccuracy - stop early when accuracy is at least this good, metres (default 25)
 * options.fallbackTimeoutMs - timeout of the low-accuracy fallback (default 15 s)
 * options.signal - an AbortSignal (used by the Cancel button). Aborting stops the listening;
 *                  the promise then simply never settles, so the caller must ignore it.
 */
export function locateUser({ fastMs = 10000, goodAccuracy = 25, fallbackTimeoutMs = 15000, signal } = {}) {
  return new Promise((resolve, reject) => {
    if (typeof window !== 'undefined' && window.isSecureContext === false) {
      reject(new LocateError('insecure'));
      return;
    }
    if (typeof navigator === 'undefined' || !navigator.geolocation) {
      reject(new LocateError('unsupported'));
      return;
    }

    let best = null;
    let watchId = null;
    let timer = null;
    let finished = false;

    const stopWatching = () => {
      if (timer) clearTimeout(timer);
      if (watchId != null) navigator.geolocation.clearWatch(watchId);
      timer = null;
      watchId = null;
    };

    // Settle exactly once from the high-accuracy phase.
    const finish = (action) => {
      if (finished) return;
      finished = true;
      stopWatching();
      action();
    };

    const lowAccuracyFallback = () => {
      navigator.geolocation.getCurrentPosition(
        (pos) => resolve(toResult(pos)),
        (err) => reject(fromPositionError(err)),
        { enableHighAccuracy: false, timeout: fallbackTimeoutMs, maximumAge: 120000 }
      );
    };

    if (signal) {
      signal.addEventListener('abort', () => {
        finished = true;
        stopWatching();
      }, { once: true });
    }

    watchId = navigator.geolocation.watchPosition(
      (pos) => {
        if (!best || pos.coords.accuracy < best.coords.accuracy) best = pos;
        if (pos.coords.accuracy <= goodAccuracy) finish(() => resolve(toResult(best)));
      },
      (err) => {
        if (err.code === 1) {
          finish(() => reject(fromPositionError(err)));
        } else if (!best) {
          // Unavailable / timed out with no reading at all: go straight to the fallback.
          finish(lowAccuracyFallback);
        }
        // else: we already have a usable reading; let the timer settle with it.
      },
      { enableHighAccuracy: true, timeout: fastMs, maximumAge: 0 }
    );

    timer = setTimeout(() => {
      finish(() => (best ? resolve(toResult(best)) : lowAccuracyFallback()));
    }, fastMs);
  });
}

export function describeLocateError(err) {
  const tail = ' You can also search for an address or drop a pin on the map.';
  switch (err && err.code) {
    case 'insecure':
      return 'Your browser only shares location on secure (https) pages, and this page is not secure.' + tail;
    case 'unsupported':
      return "This browser can't detect your location." + tail;
    case 'denied':
      return 'Location access is blocked for this site. Allow location in your browser/phone settings '
        + '(usually the lock or site-settings icon next to the address bar), then try again.' + tail;
    case 'timeout':
      return 'Finding your location took too long. Move to an open area or make sure Location/GPS is on, '
        + 'then try again.' + tail;
    default:
      return "Your device couldn't work out its position. Turn on Location/GPS and try again." + tail;
  }
}
