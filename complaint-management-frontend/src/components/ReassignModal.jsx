import { useState } from 'react';

/**
 * Shown when an admin tries to delete something (a department, category, or
 * official) that still has records pointing to it. Lets them pick where
 * those records should move before the delete is retried.
 *
 * props:
 *  - title: string
 *  - message: string (explains what's blocking the delete)
 *  - options: [{ id, name }]
 *  - onConfirm: (targetId) => void
 *  - onCancel: () => void
 */
export default function ReassignModal({ title, message, options, onConfirm, onCancel }) {
  const [selected, setSelected] = useState(options[0]?.id ?? '');

  return (
    <div className="modal-overlay" onClick={onCancel}>
      <div className="modal" onClick={(e) => e.stopPropagation()}>
        <h3 className="modal__title">{title}</h3>
        <p className="modal__message">{message}</p>
        <div className="form-group">
          <label>Move to</label>
          <select className="form-control" value={selected} onChange={(e) => setSelected(e.target.value)}>
            {options.map((o) => (
              <option key={o.id} value={o.id}>{o.name}</option>
            ))}
          </select>
        </div>
        <div className="modal__actions">
          <button type="button" className="btn btn--ghost" onClick={onCancel}>Cancel</button>
          <button
            type="button"
            className="btn btn--primary"
            disabled={!selected}
            onClick={() => onConfirm(Number(selected))}
          >
            Reassign &amp; Delete
          </button>
        </div>
      </div>
    </div>
  );
}
