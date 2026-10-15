import { KsqlCreateForm, buildCreateStatement, isAsSelect, toReplaceStatement } from './ksql-statement-template';

describe('ksql statement template', () => {

  const form = (changes: Partial<KsqlCreateForm> = {}): KsqlCreateForm => ({
    name: 'ORDERS_STREAM', mode: 'topic', topic: 'orders', keyFormat: 'KAFKA', valueFormat: 'JSON', partitions: null, source: 'ORDERS',
    ...changes
  });

  it('declares example columns for a stream over a JSON topic', () => {
    expect(buildCreateStatement('streams', form())).toBe(
      'CREATE STREAM ORDERS_STREAM (\n  id VARCHAR KEY,\n  name VARCHAR,\n  amount DOUBLE\n) WITH (\n'
      + "  KAFKA_TOPIC = 'orders',\n  KEY_FORMAT = 'KAFKA',\n  VALUE_FORMAT = 'JSON'\n);");
  });

  it('leaves the columns to Schema Registry for Avro, except a table\'s primary key', () => {
    expect(buildCreateStatement('streams', form({ valueFormat: 'AVRO' }))).not.toContain('(\n  id');
    expect(buildCreateStatement('tables', form({ valueFormat: 'AVRO' }))).toContain('(\n  id VARCHAR PRIMARY KEY\n)');
    expect(buildCreateStatement('tables', form({ keyFormat: 'AVRO', valueFormat: 'AVRO' }))).toMatch(/^CREATE TABLE ORDERS_STREAM WITH/);
  });

  it('asks ksqlDB to create the topic only when partitions are set', () => {
    expect(buildCreateStatement('streams', form())).not.toContain('PARTITIONS');
    expect(buildCreateStatement('streams', form({ partitions: 3 }))).toContain('PARTITIONS = 3');
  });

  it('defaults the topic to the name and escapes quotes', () => {
    expect(buildCreateStatement('streams', form({ topic: '' }))).toContain("KAFKA_TOPIC = 'orders_stream'");
    expect(buildCreateStatement('streams', form({ topic: "it's" }))).toContain("KAFKA_TOPIC = 'it''s'");
  });

  it('writes a query over the chosen source', () => {
    expect(buildCreateStatement('streams', form({ mode: 'query', topic: '' }))).toBe(
      "CREATE STREAM ORDERS_STREAM WITH (VALUE_FORMAT = 'JSON') AS\n  SELECT *\n  FROM ORDERS\n  EMIT CHANGES;");
    expect(buildCreateStatement('tables', form({ mode: 'query' }))).toContain('GROUP BY id');
  });

  it('turns a CREATE into a CREATE OR REPLACE once', () => {
    expect(toReplaceStatement('CREATE STREAM S (ID INT KEY) WITH (KAFKA_TOPIC=\'s\');'))
      .toBe('CREATE OR REPLACE STREAM S (ID INT KEY) WITH (KAFKA_TOPIC=\'s\');');
    expect(toReplaceStatement('  create or replace table t as select * from s;')).toBe('create or replace table t as select * from s;');
    expect(toReplaceStatement('CREATE SOURCE STREAM s WITH (KAFKA_TOPIC=\'s\');')).toMatch(/^CREATE OR REPLACE SOURCE STREAM/);
  });

  it('recognises statements that start a query', () => {
    expect(isAsSelect('CREATE STREAM s AS\n  SELECT * FROM o EMIT CHANGES;')).toBeTrue();
    expect(isAsSelect("CREATE STREAM s (id INT KEY) WITH (KAFKA_TOPIC='s');")).toBeFalse();
  });
});
