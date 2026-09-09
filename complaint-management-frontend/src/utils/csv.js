// Lightweight client-side CSV export — no extra dependency needed.

function escapeCsvValue(value) {
  if (value === null || value === undefined) return '';
  const str = String(value);
  if (/[",\n]/.test(str)) {
    return `"${str.replace(/"/g, '""')}"`;
  }
  return str;
}

/**
 * Downloads an array of flat objects as a CSV file.
 * @param {Array<Object>} rows - data rows
 * @param {Array<{key:string,label:string}>} columns - which fields to include, in order
 * @param {string} filename
 */
export function exportToCsv(rows, columns, filename = 'export.csv') {
  if (!rows || rows.length === 0) return;

  const header = columns.map((c) => escapeCsvValue(c.label)).join(',');
  const lines = rows.map((row) =>
    columns.map((c) => escapeCsvValue(row[c.key])).join(',')
  );
  const csvContent = [header, ...lines].join('\r\n');

  const blob = new Blob(['\ufeff' + csvContent], { type: 'text/csv;charset=utf-8;' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
}
