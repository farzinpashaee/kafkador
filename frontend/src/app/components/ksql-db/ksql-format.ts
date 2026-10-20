/**
 * Lays out ksqlDB statements for reading: each clause (SELECT, FROM, WHERE, WITH, ...) on its own line, and the column
 * list of a CREATE and the properties after WITH one per line. Only whitespace changes, apart from single spaces around
 * operators: words, strings, quoted names and comments are kept as written.
 */

type TokenKind = 'word' | 'string' | 'ident' | 'comment' | 'lineComment' | 'punct' | 'op';

interface Token {
  kind: TokenKind;
  text: string;
}

/** Clauses that start a new line when they appear outside parentheses. Longest first, so LEFT OUTER JOIN wins over JOIN. */
const CLAUSES: string[][] = [
  ['LEFT', 'OUTER', 'JOIN'], ['RIGHT', 'OUTER', 'JOIN'], ['FULL', 'OUTER', 'JOIN'],
  ['LEFT', 'JOIN'], ['RIGHT', 'JOIN'], ['FULL', 'JOIN'], ['INNER', 'JOIN'],
  ['GROUP', 'BY'], ['PARTITION', 'BY'],
  ['SELECT'], ['FROM'], ['JOIN'], ['WHERE'], ['HAVING'], ['WINDOW'], ['EMIT'], ['LIMIT'], ['WITH']
];

/** Words a parenthesis after them is spaced from, unlike a function call such as COUNT(*). */
const SPACED_BEFORE_PAREN = new Set([
  'WITH', 'AS', 'IN', 'AND', 'OR', 'NOT', 'ON', 'WHERE', 'SELECT', 'BY', 'WHEN', 'THEN', 'ELSE', 'VALUES', 'WITHIN',
  'TUMBLING', 'HOPPING', 'SESSION', 'STREAM', 'TABLE', 'EXISTS', 'HAVING'
]);

/** Types whose parameters are written in angle brackets, e.g. STRUCT<ID INT, NAME STRING>. */
const ANGLE_TYPES = new Set(['STRUCT', 'MAP', 'ARRAY']);

const INDENT = '  ';

export function formatKsql(sql: string): string {
  const tokens = tokenize(sql);
  const lines: string[] = [];
  let line = '';
  let depth = 0;
  let angleDepth = 0;
  /** For each open parenthesis, whether its contents are laid out one item per line. */
  const parens: boolean[] = [];
  let inSelectList = false;
  let selectListBroken = false;
  let createHeader = false;
  let noSpaceNext = false;
  let prev: Token | undefined;

  const indentFor = (level: number) => INDENT.repeat(level);
  const newLine = (level: number) => {
    if (line.trim()) lines.push(line.trimEnd());
    line = indentFor(level);
    noSpaceNext = false;
  };
  const write = (text: string, space: boolean) => {
    if (space && line.trim() && !noSpaceNext) line += ' ';
    line += text;
    noSpaceNext = false;
  };
  const expandedLevel = () => parens.filter(open => open).length;

  for (let k = 0; k < tokens.length; k++) {
    const token = tokens[k];
    const upper = token.kind === 'word' ? token.text.toUpperCase() : '';

    if (token.kind === 'lineComment') {
      write(token.text, true);
      newLine(expandedLevel());
      prev = token;
      continue;
    }

    if (token.kind === 'word' && depth === 0 && angleDepth === 0) {
      const clause = matchClause(tokens, k);
      // AS starting the query of a CREATE ... AS SELECT gets its own line, with SELECT after it.
      const asSelect = upper === 'AS' && tokens[k + 1]?.text.toUpperCase() === 'SELECT';
      if (clause || asSelect) {
        const words = asSelect ? 2 : clause!.length;
        const text = tokens.slice(k, k + words).map(t => t.text).join(' ');
        newLine(0);
        write(text, false);
        k += words - 1;
        createHeader = false;
        const last = tokens[k].text.toUpperCase();
        inSelectList = last === 'SELECT';
        if (inSelectList) {
          selectListBroken = selectListHasComma(tokens, k + 1);
          if (selectListBroken) newLine(1);
        } else {
          selectListBroken = false;
        }
        prev = tokens[k];
        continue;
      }
      if (upper === 'CREATE' && !line.trim()) createHeader = true;
    }

    if (token.kind === 'punct' && token.text === '(') {
      const expand = depth === 0 && angleDepth === 0 && hasTopLevelComma(tokens, k)
        && (createHeader || prev?.text.toUpperCase() === 'WITH');
      const space = expand || prev === undefined || prev.kind !== 'word' || SPACED_BEFORE_PAREN.has(prev.text.toUpperCase());
      write('(', space);
      parens.push(expand);
      depth++;
      if (expand) newLine(expandedLevel());
      else noSpaceNext = true;
      if (expand) createHeader = false;
      prev = token;
      continue;
    }

    if (token.kind === 'punct' && token.text === ')') {
      const expanded = parens.pop() ?? false;
      depth = Math.max(0, depth - 1);
      if (expanded) newLine(expandedLevel());
      write(')', false);
      prev = token;
      continue;
    }

    if (token.kind === 'punct' && token.text === ',') {
      write(',', false);
      if (angleDepth === 0 && depth > 0 && parens[parens.length - 1]) newLine(expandedLevel());
      else if (angleDepth === 0 && depth === 0 && inSelectList && selectListBroken) newLine(1);
      prev = token;
      continue;
    }

    if (token.kind === 'punct' && token.text === ';') {
      write(';', false);
      depth = 0;
      angleDepth = 0;
      parens.length = 0;
      inSelectList = false;
      createHeader = false;
      if (tokens.slice(k + 1).some(t => t.kind !== 'comment')) {
        newLine(0);
        lines.push('');
      }
      prev = token;
      continue;
    }

    if (token.kind === 'op' && token.text === '<' && prev && ANGLE_TYPES.has(prev.text.toUpperCase())) {
      write('<', false);
      angleDepth++;
      noSpaceNext = true;
      prev = token;
      continue;
    }

    // Struct field access reads as one name: ADDRESS->CITY.
    if (token.kind === 'op' && token.text === '->') {
      write('->', false);
      noSpaceNext = true;
      prev = token;
      continue;
    }

    if (token.kind === 'op' && token.text === '>' && angleDepth > 0) {
      write('>', false);
      angleDepth--;
      prev = token;
      continue;
    }

    write(token.text, true);
    prev = token;
  }

  if (line.trim()) lines.push(line.trimEnd());
  return lines.join('\n');
}

function matchClause(tokens: Token[], k: number): string[] | undefined {
  return CLAUSES.find(words => words.every((word, i) => {
    const token = tokens[k + i];
    return token?.kind === 'word' && token.text.toUpperCase() === word;
  }));
}

/** Whether the parenthesis at k holds more than one item, so it's worth a line per item. */
function hasTopLevelComma(tokens: Token[], k: number): boolean {
  let depth = 0;
  let angle = 0;
  for (let i = k; i < tokens.length; i++) {
    const text = tokens[i].text;
    if (tokens[i].kind === 'punct' && text === '(') depth++;
    else if (tokens[i].kind === 'punct' && text === ')') {
      if (--depth === 0) return false;
    }
    else if (tokens[i].kind === 'op' && text === '<' && ANGLE_TYPES.has(tokens[i - 1]?.text.toUpperCase())) angle++;
    else if (tokens[i].kind === 'op' && text === '>' && angle > 0) angle--;
    else if (tokens[i].kind === 'punct' && text === ',' && depth === 1 && angle === 0) return true;
  }
  return false;
}

/** Whether the select list starting at k has more than one column, so each gets its own line. */
function selectListHasComma(tokens: Token[], k: number): boolean {
  let depth = 0;
  for (let i = k; i < tokens.length; i++) {
    const token = tokens[i];
    if (token.kind === 'punct' && token.text === '(') depth++;
    else if (token.kind === 'punct' && token.text === ')') depth--;
    else if (token.kind === 'punct' && token.text === ';') return false;
    else if (depth === 0 && token.kind === 'word' && token.text.toUpperCase() === 'FROM') return false;
    else if (depth === 0 && token.kind === 'punct' && token.text === ',') return true;
  }
  return false;
}

function tokenize(sql: string): Token[] {
  const tokens: Token[] = [];
  let i = 0;
  while (i < sql.length) {
    const c = sql[i];
    if (/\s/.test(c)) {
      i++;
    } else if (c === '-' && sql[i + 1] === '-') {
      const end = sql.indexOf('\n', i);
      const stop = end === -1 ? sql.length : end;
      tokens.push({ kind: 'lineComment', text: sql.slice(i, stop).trimEnd() });
      i = stop;
    } else if (c === '/' && sql[i + 1] === '*') {
      const end = sql.indexOf('*/', i + 2);
      const stop = end === -1 ? sql.length : end + 2;
      tokens.push({ kind: 'comment', text: sql.slice(i, stop) });
      i = stop;
    } else if (c === "'" || c === '`' || c === '"') {
      // Quotes are escaped by doubling them, as in 'it''s'.
      let j = i + 1;
      while (j < sql.length) {
        if (sql[j] === c && sql[j + 1] === c) j += 2;
        else if (sql[j] === c) { j++; break; }
        else j++;
      }
      tokens.push({ kind: c === "'" ? 'string' : 'ident', text: sql.slice(i, j) });
      i = j;
    } else if (/[A-Za-z0-9_.$@]/.test(c)) {
      let j = i + 1;
      while (j < sql.length && /[A-Za-z0-9_.$@]/.test(sql[j])) j++;
      tokens.push({ kind: 'word', text: sql.slice(i, j) });
      i = j;
    } else if ('(),;'.includes(c)) {
      tokens.push({ kind: 'punct', text: c });
      i++;
    } else {
      // Two-character operators; any other symbol stands alone, so the > closing nested types stay separate.
      const pair = sql.slice(i, i + 2);
      const text = ['<=', '>=', '<>', '!=', '->', '||', ':='].includes(pair) ? pair : c;
      tokens.push({ kind: 'op', text });
      i += text.length;
    }
  }
  return tokens;
}
