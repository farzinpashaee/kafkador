import { KsqlSourceKind } from '../../models';

/** Where a new stream or table gets its rows: an existing (or new) topic, or a query over another source. */
export type KsqlCreateMode = 'topic' | 'query';

export interface KsqlCreateForm {
  name: string;
  mode: KsqlCreateMode;
  topic: string;
  keyFormat: string;
  valueFormat: string;
  /** Only when ksqlDB should create the topic. */
  partitions?: number | null;
  /** The stream or table a query reads from. */
  source: string;
}

export const KSQL_KEY_FORMATS = ['KAFKA', 'JSON', 'AVRO', 'PROTOBUF', 'JSON_SR', 'DELIMITED', 'NONE'];
export const KSQL_VALUE_FORMATS = ['JSON', 'AVRO', 'PROTOBUF', 'JSON_SR', 'DELIMITED', 'KAFKA'];

/** Formats whose columns ksqlDB reads from Schema Registry, so the statement needn't list them. */
const SCHEMA_REGISTRY_FORMATS = new Set(['AVRO', 'PROTOBUF', 'JSON_SR']);

/** A starting CREATE statement for the dialog's fields; example columns are meant to be edited. */
export function buildCreateStatement(kind: KsqlSourceKind, form: KsqlCreateForm): string {
  const type = kind === 'tables' ? 'TABLE' : 'STREAM';
  const name = form.name.trim() || (kind === 'tables' ? 'MY_TABLE' : 'MY_STREAM');
  const topic = form.topic.trim();

  if (form.mode === 'query') {
    const props = [topic ? `KAFKA_TOPIC = ${literal(topic)}` : null, `VALUE_FORMAT = ${literal(form.valueFormat)}`]
      .filter(Boolean).join(', ');
    const source = form.source.trim() || 'SOURCE_STREAM';
    const select = kind === 'tables'
      ? `  SELECT id, COUNT(*) AS total\n  FROM ${source}\n  GROUP BY id\n  EMIT CHANGES;`
      : `  SELECT *\n  FROM ${source}\n  EMIT CHANGES;`;
    return `CREATE ${type} ${name} WITH (${props}) AS\n${select}`;
  }

  const keyColumn = kind === 'tables' ? 'id VARCHAR PRIMARY KEY' : 'id VARCHAR KEY';
  let columns: string[] = [];
  if (!SCHEMA_REGISTRY_FORMATS.has(form.valueFormat)) {
    columns = [keyColumn, 'name VARCHAR', 'amount DOUBLE'];
  } else if (kind === 'tables' && !SCHEMA_REGISTRY_FORMATS.has(form.keyFormat)) {
    // A table needs a primary key, and only a Schema Registry key format supplies it.
    columns = [keyColumn];
  }
  const props = [
    `KAFKA_TOPIC = ${literal(topic || name.toLowerCase())}`,
    `KEY_FORMAT = ${literal(form.keyFormat)}`,
    `VALUE_FORMAT = ${literal(form.valueFormat)}`
  ];
  if (form.partitions) props.push(`PARTITIONS = ${form.partitions}`);
  const columnList = columns.length ? ` (\n${columns.map(c => `  ${c}`).join(',\n')}\n)` : '';
  return `CREATE ${type} ${name}${columnList} WITH (\n${props.map(p => `  ${p}`).join(',\n')}\n);`;
}

/** The statement that created a source, turned into one that replaces it: CREATE ... becomes CREATE OR REPLACE ... */
export function toReplaceStatement(statement: string): string {
  const trimmed = statement.trim();
  if (/^CREATE\s+OR\s+REPLACE\b/i.test(trimmed)) return trimmed;
  return trimmed.replace(/^CREATE\s+/i, 'CREATE OR REPLACE ');
}

/** Whether the statement starts a persistent query, which is when processing existing records is a choice. */
export function isAsSelect(statement: string): boolean {
  return /\bAS\s+SELECT\b/i.test(statement);
}

function literal(text: string): string {
  return `'${text.replace(/'/g, "''")}'`;
}
