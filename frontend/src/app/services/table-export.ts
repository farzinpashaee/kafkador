/** Shared CSV / JSON / XML export of table rows, used by the list pages' Export menus. */

export type ExportFormat = 'csv' | 'json' | 'xml';

export interface ExportField<T> {
  /** JSON key and XML element name */
  key: string;
  /** CSV header */
  label: string;
  value: (row: T) => string | number | boolean | null | undefined;
}

export const EXPORT_FORMATS: { format: ExportFormat; label: string }[] = [
  { format: 'csv', label: 'CSV' },
  { format: 'json', label: 'JSON' },
  { format: 'xml', label: 'XML' }
];

const MIME_TYPES: Record<ExportFormat, string> = {
  csv: 'text/csv;charset=utf-8',
  json: 'application/json;charset=utf-8',
  xml: 'application/xml;charset=utf-8'
};

const BYTE_UNITS = ['B', 'KB', 'MB', 'GB', 'TB', 'PB'];

/** @param rootElement / itemElement XML element names, e.g. "brokers" / "broker" */
export function buildExport<T>(format: ExportFormat, fields: ExportField<T>[], rows: T[],
                               rootElement: string, itemElement: string): string {
  if (format === 'json') {
    return JSON.stringify(rows.map(row => Object.fromEntries(fields.map(f => [f.key, f.value(row) ?? null]))), null, 2);
  }
  if (format === 'xml') {
    const items = rows.map(row => `  <${itemElement}>\n`
      + fields.map(f => `    <${f.key}>${xmlText(f.value(row))}</${f.key}>`).join('\n')
      + `\n  </${itemElement}>`);
    return ['<?xml version="1.0" encoding="UTF-8"?>', `<${rootElement}>`, ...items, `</${rootElement}>`].join('\n');
  }
  const lines = [fields.map(f => f.label), ...rows.map(row => fields.map(f => f.value(row)))];
  return lines.map(line => line.map(csvCell).join(',')).join('\r\n');
}

/** Saves {@code content} as "<baseName>.<format>", e.g. brokers-local.csv. */
export function downloadExport(content: string, format: ExportFormat, baseName: string): void {
  const url = URL.createObjectURL(new Blob([content], { type: MIME_TYPES[format] }));
  const link = document.createElement('a');
  link.href = url;
  link.download = `${baseName.replace(/[^\w.-]+/g, '_')}.${format}`;
  link.click();
  URL.revokeObjectURL(url);
}

export function formatBytes(bytes: number): string {
  let value = bytes;
  let unit = 0;
  while (value >= 1024 && unit < BYTE_UNITS.length - 1) {
    value /= 1024;
    unit++;
  }
  return unit === 0 ? `${value} B` : `${value.toFixed(2)} ${BYTE_UNITS[unit]}`;
}

function csvCell(value: unknown): string {
  if (value == null) return '';
  const text = String(value);
  return /[",\r\n]/.test(text) ? `"${text.replace(/"/g, '""')}"` : text;
}

function xmlText(value: unknown): string {
  if (value == null) return '';
  return String(value).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;').replace(/'/g, '&apos;');
}
