import { Config } from '../models';
import { ExportField } from './table-export';

/** Columns of a broker / topic Configurations export. Sensitive values are masked, as in the table. */
export const CONFIG_EXPORT_FIELDS: ExportField<Config>[] = [
  { key: 'name', label: 'Name', value: c => c.name },
  { key: 'value', label: 'Value', value: c => c.sensitive ? '********' : c.value },
  { key: 'source', label: 'Source', value: c => c.source },
  { key: 'type', label: 'Type', value: c => c.type },
  { key: 'sensitive', label: 'Sensitive', value: c => c.sensitive },
  { key: 'readOnly', label: 'Read only', value: c => c.readOnly }
];
