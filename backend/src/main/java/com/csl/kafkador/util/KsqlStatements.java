package com.csl.kafkador.util;

import com.csl.kafkador.exception.KsqlStatementException;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Checks the statements the ksqlDB page sends before they reach the server: exactly one statement, and of the kind the
 * request is for, so creating a stream can't drop a table or terminate a query.
 */
public final class KsqlStatements {

    public enum SourceType { STREAM, TABLE }

    /** An identifier: `back-ticked` (kept as written) or bare (ksqlDB upper-cases it). */
    private static final String IDENTIFIER = "(`[^`]+`|[A-Za-z_][A-Za-z0-9_@]*)";
    private static final Pattern CREATE = Pattern.compile(
            "^\\s*CREATE\\s+(OR\\s+REPLACE\\s+)?(SOURCE\\s+)?(STREAM|TABLE)\\s+(IF\\s+NOT\\s+EXISTS\\s+)?" + IDENTIFIER,
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ALTER = Pattern.compile(
            "^\\s*ALTER\\s+(STREAM|TABLE)\\s+" + IDENTIFIER, Pattern.CASE_INSENSITIVE);

    private KsqlStatements() {}

    /** A statement creating a stream or table of {@code type}, ending with a semicolon. */
    public static String requireCreate(SourceType type, String statement) throws KsqlStatementException {
        String single = requireSingle(statement);
        Matcher create = CREATE.matcher(withoutComments(single));
        if (!create.find() || !create.group(3).equalsIgnoreCase(type.name())) {
            throw new KsqlStatementException("Enter a CREATE " + type + " statement");
        }
        return single;
    }

    /** A statement changing the stream or table {@code name}: CREATE OR REPLACE, or ALTER. */
    public static String requireUpdate(SourceType type, String name, String statement) throws KsqlStatementException {
        String single = requireSingle(statement);
        String code = withoutComments(single);
        Matcher create = CREATE.matcher(code);
        Matcher alter = ALTER.matcher(code);
        String kind;
        String identifier;
        if (create.find() && create.group(1) != null) {
            kind = create.group(3);
            identifier = create.group(5);
        } else if (alter.find()) {
            kind = alter.group(1);
            identifier = alter.group(2);
        } else {
            throw new KsqlStatementException("Enter a CREATE OR REPLACE " + type + " or ALTER " + type + " statement");
        }
        if (!kind.equalsIgnoreCase(type.name()) || !sameIdentifier(identifier, name)) {
            throw new KsqlStatementException("The statement must change " + type.name().toLowerCase(Locale.ROOT) + " " + name);
        }
        return single;
    }

    /** {@code name} back-ticked, so names ksqlDB keeps case-sensitive are matched exactly. */
    public static String quoteIdentifier(String name) throws KsqlStatementException {
        if (name == null || name.isBlank() || name.indexOf('`') >= 0) {
            throw new KsqlStatementException("Invalid stream or table name");
        }
        return "`" + name + "`";
    }

    /** The statement trimmed and ending with one semicolon; rejects empty input and more than one statement. */
    static String requireSingle(String statement) throws KsqlStatementException {
        String trimmed = statement == null ? "" : statement.strip();
        while (trimmed.endsWith(";")) trimmed = trimmed.substring(0, trimmed.length() - 1).stripTrailing();
        String code = withoutComments(trimmed);
        if (code.isBlank()) throw new KsqlStatementException("Enter a statement");
        if (code.indexOf(';') >= 0) throw new KsqlStatementException("Enter one statement at a time");
        return trimmed + ";";
    }

    private static boolean sameIdentifier(String written, String name) {
        return written.startsWith("`") ? written.substring(1, written.length() - 1).equals(name)
                : written.toUpperCase(Locale.ROOT).equals(name);
    }

    /**
     * The statement with comments blanked out and the contents of 'strings' and `identifiers` replaced by spaces,
     * keeping their quotes, so semicolons and keywords inside them don't count.
     */
    static String withoutComments(String sql) {
        StringBuilder out = new StringBuilder(sql.length());
        int i = 0;
        while (i < sql.length()) {
            char c = sql.charAt(i);
            if (c == '-' && i + 1 < sql.length() && sql.charAt(i + 1) == '-') {
                while (i < sql.length() && sql.charAt(i) != '\n') { out.append(' '); i++; }
            } else if (c == '/' && i + 1 < sql.length() && sql.charAt(i + 1) == '*') {
                int end = sql.indexOf("*/", i + 2);
                int stop = end < 0 ? sql.length() : end + 2;
                while (i < stop) { out.append(' '); i++; }
            } else if (c == '\'') {
                // '' inside a string is an escaped quote.
                out.append(c);
                i++;
                while (i < sql.length()) {
                    if (sql.charAt(i) == '\'') {
                        if (i + 1 < sql.length() && sql.charAt(i + 1) == '\'') { out.append("  "); i += 2; continue; }
                        break;
                    }
                    out.append(' ');
                    i++;
                }
                if (i < sql.length()) { out.append('\''); i++; }
            } else if (c == '`') {
                // Identifiers are compared by name, so keep their text.
                int end = sql.indexOf('`', i + 1);
                int stop = end < 0 ? sql.length() : end + 1;
                out.append(sql, i, stop);
                i = stop;
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

}
