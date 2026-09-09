import { exportToCsv } from '../utils/csv';

const DEFAULT_COLUMNS = [
  { key: 'complaintNumber', label: 'Reference' },
  { key: 'title', label: 'Title' },
  { key: 'category', label: 'Category' },
  { key: 'status', label: 'Status' },
  { key: 'priority', label: 'Priority' },
  { key: 'departmentName', label: 'Department' },
  { key: 'assignedOfficialName', label: 'Assigned Official' },
  { key: 'citizenName', label: 'Citizen' },
  { key: 'location', label: 'Location' },
  { key: 'createdAt', label: 'Submitted At' },
];

/**
 * Exports the complaints currently visible on screen (the loaded page) as a CSV file.
 * Disabled when there's nothing to export.
 */
export default function ExportCsvButton({ rows, columns = DEFAULT_COLUMNS, filenamePrefix = 'complaints' }) {
  const handleExport = () => {
    const stamp = new Date().toISOString().slice(0, 10);
    exportToCsv(rows, columns, `${filenamePrefix}-${stamp}.csv`);
  };

  return (
    <button
      type="button"
      className="btn btn--outline btn--sm"
      onClick={handleExport}
      disabled={!rows || rows.length === 0}
      title={rows && rows.length ? 'Export the current list to CSV' : 'No rows to export'}
    >
      ⬇ Export CSV
    </button>
  );
}
