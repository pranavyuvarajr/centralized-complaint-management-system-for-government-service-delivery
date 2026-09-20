import { useEffect, useState } from 'react';
import { attachmentAPI } from '../services/api';

// An attachment's kind says what it is for. Attachments that predate the field come
// back without one and are treated as the citizen's original evidence.
const GROUPS = [
  { kind: 'EVIDENCE', title: 'Reported by citizen' },
  { kind: 'COMPLETION', title: 'Completion photos' },
  { kind: 'REOPEN', title: 'Reopen photos' },
];

function Thumb({ attachment, onView }) {
  const [url, setUrl] = useState(null);
  const isImage = attachment.contentType?.startsWith('image/');

  useEffect(() => {
    if (!isImage) return undefined;
    let cancelled = false;
    let created = null;
    attachmentAPI.download(attachment.id)
      .then((res) => {
        if (cancelled) return;
        created = window.URL.createObjectURL(new Blob([res.data], { type: attachment.contentType }));
        setUrl(created);
      })
      .catch(() => {});
    return () => {
      cancelled = true;
      if (created) window.URL.revokeObjectURL(created);
    };
  }, [attachment.id, attachment.contentType, isImage]);

  if (!isImage) {
    const ext = (attachment.originalFileName?.split('.').pop() || 'file').slice(0, 4).toUpperCase();
    return <div className="att-thumb att-thumb--placeholder"><span className="att-thumb__ext">{ext}</span></div>;
  }
  if (!url) return <div className="att-thumb att-thumb--placeholder">Loading…</div>;
  return (
    <img className="att-thumb" src={url} alt={attachment.originalFileName}
      onClick={() => onView(attachment)} />
  );
}

/**
 * Attachments grouped by what they are, as a tidy grid of fixed-size cards (photo on top,
 * details and actions below). The citizen's reported photos and the official's completion
 * photos sit side by side (stacked on narrow screens) so before and after are easy to compare.
 *
 * props: attachments, onView(attachment), onDownload(attachment)
 */
export default function AttachmentSections({ attachments, onView, onDownload }) {
  const byKind = (kind) => attachments.filter((a) => (a.kind || 'EVIDENCE') === kind);

  return (
    <div className="att-groups">
      {GROUPS.map(({ kind, title }) => {
        const items = byKind(kind);
        if (items.length === 0) return null;
        return (
          <section key={kind} className={`att-group att-group--${kind.toLowerCase()}`} aria-label={title}>
            <h4 className="att-group__title">{title}</h4>
            <div className="att-grid">
              {items.map((a) => (
                <div className="att-card" key={a.id}>
                  <Thumb attachment={a} onView={onView} />
                  <div className="att-info">
                    <div className="att-name" title={a.originalFileName}>{a.originalFileName}</div>
                    <div className="att-meta">
                      {Math.round(a.fileSize / 1024)} KB{a.uploadedByName ? ` · ${a.uploadedByName}` : ''}
                    </div>
                    {a.uploadedAt && <div className="att-meta">{new Date(a.uploadedAt).toLocaleString()}</div>}
                  </div>
                  <div className="att-actions">
                    <button type="button" className="btn btn--ghost btn--sm" onClick={() => onView(a)}>View</button>
                    <button type="button" className="btn btn--outline btn--sm" onClick={() => onDownload(a)}>Download</button>
                  </div>
                </div>
              ))}
            </div>
          </section>
        );
      })}
    </div>
  );
}
