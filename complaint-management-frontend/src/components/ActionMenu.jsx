import { useState, useRef, useEffect } from 'react';

/**
 * A single "Actions" button per row that opens a dropdown of options,
 * instead of cramming several buttons into a table cell.
 *
 * items: [{ label, onClick, danger }]
 */
export default function ActionMenu({ items }) {
  const [open, setOpen] = useState(false);
  const ref = useRef(null);

  useEffect(() => {
    function onDocClick(e) {
      if (ref.current && !ref.current.contains(e.target)) setOpen(false);
    }
    function onKey(e) {
      if (e.key === 'Escape') setOpen(false);
    }
    document.addEventListener('mousedown', onDocClick);
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('mousedown', onDocClick);
      document.removeEventListener('keydown', onKey);
    };
  }, []);

  return (
    <div className="action-menu" ref={ref}>
      <button
        type="button"
        className="btn btn--sm btn--ghost action-menu__trigger"
        onClick={() => setOpen((v) => !v)}
        aria-haspopup="true"
        aria-expanded={open}
      >
        Actions <span className="action-menu__caret">▾</span>
      </button>
      {open && (
        <div className="action-menu__dropdown" role="menu">
          {items.map((item, idx) => (
            <button
              key={idx}
              type="button"
              role="menuitem"
              className={`action-menu__item${item.danger ? ' action-menu__item--danger' : ''}`}
              onClick={() => { setOpen(false); item.onClick(); }}
            >
              {item.label}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
