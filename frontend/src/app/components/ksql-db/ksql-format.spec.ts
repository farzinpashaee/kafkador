import { formatKsql } from './ksql-format';

describe('formatKsql', () => {

  it('puts a stream\'s columns and properties one per line', () => {
    expect(formatKsql(
      "CREATE OR REPLACE STREAM ORDERTYPESTREAM (EVENTID STRING, EVENTTYPE STRING) WITH (CLEANUP_POLICY='delete', KAFKA_TOPIC='orders', VALUE_FORMAT='JSON');"
    )).toBe([
      'CREATE OR REPLACE STREAM ORDERTYPESTREAM (',
      '  EVENTID STRING,',
      '  EVENTTYPE STRING',
      ')',
      'WITH (',
      "  CLEANUP_POLICY = 'delete',",
      "  KAFKA_TOPIC = 'orders',",
      "  VALUE_FORMAT = 'JSON'",
      ');'
    ].join('\n'));
  });

  it('starts each clause of a query on its own line', () => {
    expect(formatKsql(
      "CREATE TABLE TOTALS AS SELECT CUSTOMER, COUNT(*) AS N, SUM(AMOUNT) FROM ORDERS WHERE STATUS = 'PAID' GROUP BY CUSTOMER EMIT CHANGES;"
    )).toBe([
      'CREATE TABLE TOTALS',
      'AS SELECT',
      '  CUSTOMER,',
      '  COUNT(*) AS N,',
      '  SUM(AMOUNT)',
      'FROM ORDERS',
      "WHERE STATUS = 'PAID'",
      'GROUP BY CUSTOMER',
      'EMIT CHANGES;'
    ].join('\n'));
  });

  it('keeps a single selected column and joins readable', () => {
    expect(formatKsql('SELECT * FROM A LEFT JOIN B WITHIN 1 HOUR ON A.ID = B.ID EMIT CHANGES;')).toBe([
      'SELECT *',
      'FROM A',
      'LEFT JOIN B WITHIN 1 HOUR ON A.ID = B.ID',
      'EMIT CHANGES;'
    ].join('\n'));
  });

  it('leaves strings, quoted names, struct types and comments as written', () => {
    expect(formatKsql(
      "-- orders\nCREATE STREAM `My Stream` (ID INT, ADDRESS STRUCT<CITY STRING, ZIP INT>, TAGS ARRAY<STRUCT<K STRING, V STRING>>) WITH (KAFKA_TOPIC='a, (b)');"
    )).toBe([
      '-- orders',
      'CREATE STREAM `My Stream` (',
      '  ID INT,',
      '  ADDRESS STRUCT<CITY STRING, ZIP INT>,',
      '  TAGS ARRAY<STRUCT<K STRING, V STRING>>',
      ')',
      "WITH (KAFKA_TOPIC = 'a, (b)');"
    ].join('\n'));
  });

  it('separates statements with a blank line and reads struct fields as one name', () => {
    expect(formatKsql('SELECT ADDRESS -> CITY FROM S EMIT CHANGES; DROP STREAM S;')).toBe([
      'SELECT ADDRESS->CITY',
      'FROM S',
      'EMIT CHANGES;',
      '',
      'DROP STREAM S;'
    ].join('\n'));
  });

  it('gives the same result when formatted twice', () => {
    const once = formatKsql("CREATE STREAM S (A INT, B STRING) WITH (KAFKA_TOPIC='t', VALUE_FORMAT='JSON'); SELECT A, B FROM S WHERE A > 1 EMIT CHANGES;");
    expect(formatKsql(once)).toBe(once);
  });
});
