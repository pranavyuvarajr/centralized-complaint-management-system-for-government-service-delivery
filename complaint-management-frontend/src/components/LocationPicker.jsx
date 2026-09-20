import { useState, useEffect, useRef, useId } from 'react';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import { geocodeAPI } from '../services/api';
import useDebounce from '../hooks/useDebounce';
import { locateUser, describeLocateError } from '../utils/geolocation';

// The map pin is drawn as an inline SVG teardrop instead of Leaflet's default marker.
// The default marker needs separate image files, which bundlers often break (it then shows
// up as an empty box); an inline SVG always renders, follows the theme colours, and stays
// sharp at any screen density. The tip of the pin (bottom centre) sits exactly on the point.
const PIN_ICON = L.divIcon({
  className: 'lp-pin', // replaces Leaflet's default white-box div icon styling
  html: '<svg xmlns="http://www.w3.org/2000/svg" viewBox="-2 -2 36 46" width="36" height="46" aria-hidden="true">'
    + '<path class="lp-pin__body" d="M16 0C7.163 0 0 7.163 0 16c0 11.2 16 26 16 26s16-14.8 16-26C32 7.163 24.837 0 16 0z"/>'
    + '<circle class="lp-pin__dot" cx="16" cy="16" r="6"/></svg>',
  iconSize: [36, 46],
  iconAnchor: [18, 44],
});

const DEFAULT_CENTER = [20.5937, 78.9629]; // India, used only until there's a real pin
const MIN_QUERY_LENGTH = 3;

const SIZES = { s: { label: 'Small', px: 220 }, m: { label: 'Medium', px: 320 }, l: { label: 'Large', px: 480 } };
const TYPE_ICONS = { street: '🛣️', place: '🏘️', poi: '🏢', address: '🏠' };
const SOURCE_TEXT = { gps: 'your current location', search: 'search', pin: 'the map pin' };

/**
 * Location picker for the complaint form: address/street search, "use my current
 * location", and a Leaflet map with a draggable pin that can be resized or shown
 * full screen. The parent owns the chosen location so it can validate and submit it.
 *
 * props:
 *  - coords: { lat, lon } | null — the current location
 *  - source: 'gps' | 'search' | 'pin' | '' — how coords were set (a restored draft may pass any)
 *  - onChange: ({ lat, lon }, source) => void
 *  - onClear: optional; when given, a "Clear location" button is shown (for optional locations)
 *  - initialQuery / onQueryChange: keep the search text in the parent's draft
 */
export default function LocationPicker({ coords, source, onChange, onClear, initialQuery = '', onQueryChange }) {
  const listId = useId();

  const [query, setQuery] = useState(initialQuery);
  const debouncedQuery = useDebounce(query, 300);
  const [results, setResults] = useState([]);
  const [status, setStatus] = useState('idle'); // idle | loading | ok | empty | error
  const [statusMsg, setStatusMsg] = useState('');
  const [open, setOpen] = useState(false);
  const [activeIndex, setActiveIndex] = useState(-1);

  const [locating, setLocating] = useState(false);
  const [locateMsg, setLocateMsg] = useState(null); // { kind: 'error' | 'info', text }
  const [accuracy, setAccuracy] = useState(null); // metres, from the last "use my location"

  const [addressLabel, setAddressLabel] = useState('');
  const [labelLoading, setLabelLoading] = useState(false);

  const [size, setSize] = useState('m');
  const [expanded, setExpanded] = useState(false);

  const wrapperRef = useRef(null);
  const inputRef = useRef(null);
  const mapContainerRef = useRef(null);
  const mapRef = useRef(null);
  const markerRef = useRef(null);
  const circleRef = useRef(null);
  const typedRef = useRef(false); // true only when the user typed (not for programmatic query changes)
  const searchSeqRef = useRef(0);
  const reverseSeqRef = useRef(0);
  const labelForRef = useRef(null); // { lat, lon, label } — a label we already know for these coords
  const locateAbortRef = useRef(null);
  const onChangeRef = useRef(onChange);
  onChangeRef.current = onChange;

  // ---------------------------------------------------------------- map
  useEffect(() => {
    if (!mapContainerRef.current || mapRef.current) return undefined;

    const map = L.map(mapContainerRef.current).setView(DEFAULT_CENTER, 5);
    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      attribution: '&copy; OpenStreetMap contributors',
      maxZoom: 19,
    }).addTo(map);
    map.on('click', (e) => onChangeRef.current({ lat: e.latlng.lat, lon: e.latlng.lng }, 'pin'));
    mapRef.current = map;

    // Any size change (size chips, full screen, window resize) needs a Leaflet re-measure.
    const observer = typeof ResizeObserver !== 'undefined' ? new ResizeObserver(() => map.invalidateSize()) : null;
    if (observer) observer.observe(mapContainerRef.current);

    return () => {
      if (observer) observer.disconnect();
      map.remove();
      mapRef.current = null;
      markerRef.current = null;
      circleRef.current = null;
    };
  }, []);

  // Keep the marker in sync with coords, whichever way they were set.
  useEffect(() => {
    const map = mapRef.current;
    if (!map) return;
    if (!coords) {
      if (markerRef.current) { markerRef.current.remove(); markerRef.current = null; }
      return;
    }
    const created = !markerRef.current;
    if (created) {
      markerRef.current = L.marker([coords.lat, coords.lon], { draggable: true, icon: PIN_ICON, title: 'Drag to adjust the location' }).addTo(map);
      markerRef.current.on('dragend', () => {
        const pos = markerRef.current.getLatLng();
        onChangeRef.current({ lat: pos.lat, lon: pos.lng }, 'pin');
      });
    } else {
      markerRef.current.setLatLng([coords.lat, coords.lon]);
    }
    // Don't yank the view around while the user is placing/dragging the pin themselves.
    if (source !== 'pin' || created) {
      map.setView([coords.lat, coords.lon], Math.max(map.getZoom(), 16));
    }
  }, [coords, source]);

  // Accuracy circle for "use my location".
  useEffect(() => {
    const map = mapRef.current;
    if (!map) return;
    if (circleRef.current) { circleRef.current.remove(); circleRef.current = null; }
    if (coords && source === 'gps' && accuracy) {
      circleRef.current = L.circle([coords.lat, coords.lon], { radius: accuracy, weight: 1, opacity: 0.6, fillOpacity: 0.12 }).addTo(map);
    }
  }, [coords, source, accuracy]);

  // Re-measure the map after a size change / full-screen toggle.
  useEffect(() => {
    const t = setTimeout(() => mapRef.current && mapRef.current.invalidateSize(), 60);
    return () => clearTimeout(t);
  }, [size, expanded]);

  // Full screen: lock page scroll, Esc to leave.
  useEffect(() => {
    if (!expanded) return undefined;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    const onKey = (e) => { if (e.key === 'Escape') setExpanded(false); };
    document.addEventListener('keydown', onKey);
    return () => {
      document.body.style.overflow = previousOverflow;
      document.removeEventListener('keydown', onKey);
    };
  }, [expanded]);

  // ---------------------------------------------------------------- address label
  useEffect(() => {
    if (!coords) {
      setAddressLabel('');
      setLabelLoading(false);
      return undefined;
    }
    const known = labelForRef.current;
    if (known && known.lat === coords.lat && known.lon === coords.lon) {
      setAddressLabel(known.label);
      setLabelLoading(false);
      return undefined;
    }
    const seq = ++reverseSeqRef.current;
    setLabelLoading(true);
    setAddressLabel('');
    const timer = setTimeout(() => {
      geocodeAPI.reverse(coords.lat, coords.lon)
        .then((res) => { if (seq === reverseSeqRef.current) setAddressLabel(res.data?.address || ''); })
        .catch(() => { if (seq === reverseSeqRef.current) setAddressLabel(''); })
        .finally(() => { if (seq === reverseSeqRef.current) setLabelLoading(false); });
    }, 250);
    return () => clearTimeout(timer);
  }, [coords]);

  // ---------------------------------------------------------------- search
  const searchBias = () => {
    if (coords) return { lat: coords.lat, lon: coords.lon };
    const map = mapRef.current;
    if (map && map.getZoom() >= 7) {
      const c = map.getCenter();
      return { lat: c.lat, lon: c.lng };
    }
    return {};
  };

  useEffect(() => {
    if (!typedRef.current) return;
    const q = debouncedQuery.trim();
    if (q.length < MIN_QUERY_LENGTH) return;
    const seq = ++searchSeqRef.current;
    setStatus('loading');
    setOpen(true);
    geocodeAPI.search(q, searchBias())
      .then((res) => {
        if (seq !== searchSeqRef.current) return; // a newer search superseded this one
        const list = Array.isArray(res.data) ? res.data : [];
        setResults(list);
        setActiveIndex(-1);
        setStatus(list.length ? 'ok' : 'empty');
      })
      .catch((err) => {
        if (seq !== searchSeqRef.current) return;
        setResults([]);
        setStatus('error');
        setStatusMsg(err.response?.data?.error
          || "Couldn't reach location search. Check your connection, or tap the map to drop a pin instead.");
      });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [debouncedQuery]);

  // Close the result list when clicking elsewhere.
  useEffect(() => {
    const onDown = (e) => {
      if (wrapperRef.current && !wrapperRef.current.contains(e.target)) setOpen(false);
    };
    document.addEventListener('mousedown', onDown);
    return () => document.removeEventListener('mousedown', onDown);
  }, []);

  // Cancel a pending "use my location" if the component goes away.
  useEffect(() => () => { if (locateAbortRef.current) locateAbortRef.current.abort(); }, []);

  const handleInput = (e) => {
    const value = e.target.value;
    typedRef.current = true;
    setQuery(value);
    if (onQueryChange) onQueryChange(value);
    if (value.trim().length < MIN_QUERY_LENGTH) {
      searchSeqRef.current += 1; // drop any in-flight search
      setResults([]);
      setStatus('idle');
      setOpen(false);
    } else {
      setStatus('loading');
      setOpen(true);
    }
  };

  const clearSearch = () => {
    typedRef.current = false;
    searchSeqRef.current += 1;
    setQuery('');
    if (onQueryChange) onQueryChange('');
    setResults([]);
    setStatus('idle');
    setOpen(false);
    if (inputRef.current) inputRef.current.focus();
  };

  const pickResult = (r) => {
    typedRef.current = false;
    searchSeqRef.current += 1;
    labelForRef.current = { lat: r.latitude, lon: r.longitude, label: r.displayName };
    setQuery(r.displayName);
    if (onQueryChange) onQueryChange(r.displayName);
    setResults([]);
    setStatus('idle');
    setOpen(false);
    setLocateMsg(null);
    onChange({ lat: r.latitude, lon: r.longitude }, 'search');
  };

  const handleKeyDown = (e) => {
    if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
      if (!results.length) return;
      e.preventDefault();
      setOpen(true);
      const step = e.key === 'ArrowDown' ? 1 : -1;
      setActiveIndex((i) => (i + step + results.length) % results.length);
    } else if (e.key === 'Enter') {
      e.preventDefault(); // never submit the complaint form from the search box
      if (open && status === 'ok' && results.length) pickResult(results[activeIndex >= 0 ? activeIndex : 0]);
    } else if (e.key === 'Escape' && open) {
      e.stopPropagation(); // close the list first; a second Esc leaves full screen
      setOpen(false);
    }
  };

  // ---------------------------------------------------------------- use my location
  const locateMe = async () => {
    setLocateMsg(null);
    setLocating(true);
    const controller = new AbortController();
    locateAbortRef.current = controller;
    try {
      const fix = await locateUser({ signal: controller.signal });
      if (controller.signal.aborted) return;
      setAccuracy(fix.accuracy);
      labelForRef.current = null;
      typedRef.current = false;
      setQuery('');
      if (onQueryChange) onQueryChange('');
      setResults([]);
      setOpen(false);
      onChange({ lat: fix.lat, lon: fix.lon }, 'gps');
      if (fix.accuracy > 1000) {
        setLocateMsg({ kind: 'info', text: `This is only an approximate location (within about ${Math.round(fix.accuracy / 1000)} km). Drag the pin to the exact spot.` });
      } else if (fix.accuracy > 100) {
        setLocateMsg({ kind: 'info', text: `Location found (accurate to about ${Math.round(fix.accuracy)} m). Drag the pin to fine-tune it.` });
      }
    } catch (err) {
      if (controller.signal.aborted) return;
      setLocateMsg({ kind: 'error', text: describeLocateError(err) });
    } finally {
      if (!controller.signal.aborted) setLocating(false);
    }
  };

  const cancelLocate = () => {
    if (locateAbortRef.current) locateAbortRef.current.abort();
    setLocating(false);
  };

  const handleClear = () => {
    labelForRef.current = null;
    setLocateMsg(null);
    setAccuracy(null);
    clearSearch();
    if (onClear) onClear();
  };

  // ---------------------------------------------------------------- render
  const showList = open && query.trim().length >= MIN_QUERY_LENGTH;

  return (
    <div ref={wrapperRef} className={`lp${expanded ? ' lp--fullscreen' : ''}`}>
      <div className="lp__toolbar">
        <div className="lp__search">
          <input
            ref={inputRef}
            type="text"
            className="form-control"
            placeholder="Search a street, area, landmark or address…"
            value={query}
            role="combobox"
            aria-expanded={showList}
            aria-controls={listId}
            aria-activedescendant={activeIndex >= 0 ? `${listId}-${activeIndex}` : undefined}
            aria-autocomplete="list"
            autoComplete="off"
            onChange={handleInput}
            onKeyDown={handleKeyDown}
            onFocus={() => { if (results.length > 0 || status === 'error') setOpen(true); }}
          />
          {query && (
            <button type="button" className="lp__clear-search" aria-label="Clear search" onClick={clearSearch}>×</button>
          )}
          {showList && (
            <ul id={listId} role="listbox" className="lp__results">
              {status === 'loading' && <li className="lp__note">Searching…</li>}
              {status === 'error' && <li className="lp__note lp__note--error">{statusMsg}</li>}
              {status === 'empty' && (
                <li className="lp__note">
                  No places found for “{query.trim()}”. Try fewer words or another spelling, or drop a pin on the map.
                </li>
              )}
              {status === 'ok' && results.map((r, i) => (
                <li
                  key={`${r.latitude},${r.longitude},${i}`}
                  id={`${listId}-${i}`}
                  role="option"
                  aria-selected={i === activeIndex}
                  className={`lp__result${i === activeIndex ? ' lp__result--active' : ''}`}
                  onMouseDown={(e) => e.preventDefault()}
                  onMouseEnter={() => setActiveIndex(i)}
                  onClick={() => pickResult(r)}
                >
                  <span className="lp__result-icon" aria-hidden="true">{TYPE_ICONS[r.type] || '📍'}</span>
                  <span className="lp__result-text">
                    <span className="lp__result-name">{r.name || r.displayName}</span>
                    {r.secondary && <span className="lp__result-sub">{r.secondary}</span>}
                  </span>
                </li>
              ))}
            </ul>
          )}
        </div>

        {locating ? (
          <button type="button" className="btn btn--ghost" onClick={cancelLocate}>Locating… Cancel</button>
        ) : (
          <button type="button" className="btn btn--ghost" onClick={locateMe}>📍 Use my location</button>
        )}
        <button type="button" className="btn btn--ghost" onClick={() => setExpanded((v) => !v)}
          aria-pressed={expanded} title={expanded ? 'Exit full screen (Esc)' : 'Enlarge the map to full screen'}>
          {expanded ? '✕ Close map' : '⤢ Expand'}
        </button>
      </div>

      {locating && <div className="lp__msg lp__msg--info">Finding your location… this can take up to about 25 seconds indoors.</div>}
      {locateMsg && <div className={`lp__msg lp__msg--${locateMsg.kind}`}>{locateMsg.text}</div>}

      {coords ? (
        <div className="lp__status lp__status--set">
          <div className="lp__status-main">
            <strong>📍 {addressLabel || (labelLoading ? 'Finding address…' : 'Pinned location')}</strong>
            <span className="lp__status-sub">
              {coords.lat.toFixed(5)}, {coords.lon.toFixed(5)}
              {SOURCE_TEXT[source] ? ` · set from ${SOURCE_TEXT[source]}` : ''} · drag the pin to fine-tune
            </span>
          </div>
          {onClear && <button type="button" className="btn btn--ghost btn--sm" onClick={handleClear}>Clear location</button>}
        </div>
      ) : (
        <div className="lp__status">No location set yet — search above, use your current location, or tap the map.</div>
      )}

      {!expanded && (
        <div className="lp__sizes" role="group" aria-label="Map size">
          <span className="lp__sizes-label">Map size</span>
          {Object.entries(SIZES).map(([key, s]) => (
            <button key={key} type="button" className={`chip${size === key ? ' active' : ''}`}
              aria-pressed={size === key} onClick={() => setSize(key)}>{s.label}</button>
          ))}
        </div>
      )}

      <div
        ref={mapContainerRef}
        className="lp__map"
        style={expanded ? undefined : { height: SIZES[size].px }}
      />
    </div>
  );
}
