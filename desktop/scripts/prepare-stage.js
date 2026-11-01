// Puts what the app ships into stage/: the Spring Boot jar, and a Java runtime made with jlink from the JDK building
// it. The runtime matches that JDK's OS and CPU, so each installer is built on its own kind of machine.
const { spawnSync } = require('node:child_process');
const fs = require('node:fs');
const path = require('node:path');

const desktop = path.join(__dirname, '..');
const stage = path.join(desktop, 'stage');
const jar = path.join(desktop, '..', 'backend', 'target', 'kafkador.jar');

// What the jar needs at runtime: it can't be worked out from a Spring Boot jar, and a missing module only shows up
// when that feature is used (e.g. java.naming for LDAP, jdk.security.auth for Kafka SASL).
const MODULES = [
  'java.base', 'java.compiler', 'java.desktop', 'java.instrument', 'java.logging', 'java.management',
  'java.management.rmi', 'java.naming', 'java.net.http', 'java.prefs', 'java.rmi', 'java.scripting',
  'java.security.jgss', 'java.security.sasl', 'java.sql', 'java.sql.rowset', 'java.transaction.xa', 'java.xml',
  'java.xml.crypto', 'jdk.charsets', 'jdk.crypto.cryptoki', 'jdk.crypto.ec', 'jdk.jfr', 'jdk.localedata',
  'jdk.management', 'jdk.naming.dns', 'jdk.net', 'jdk.security.auth', 'jdk.security.jgss', 'jdk.unsupported',
  'jdk.zipfs'
];

function tool(name) {
  const home = process.env.JAVA_HOME;
  return home ? path.join(home, 'bin', name) : name;
}

function run(command, args) {
  const result = spawnSync(command, args, { encoding: 'utf8', maxBuffer: 64 * 1024 * 1024 });
  if (result.error) throw new Error(`${command}: ${result.error.message}. Is a JDK on the PATH, or JAVA_HOME set?`);
  if (result.status !== 0) throw new Error(`${command} failed:\n${result.stdout}${result.stderr}`);
  return result.stdout;
}

if (!fs.existsSync(jar)) {
  throw new Error(`${jar} is missing. Build it first: cd backend && ./mvnw package -P with-frontend`);
}
if (!run(tool('jar'), ['tf', jar]).includes('BOOT-INF/classes/static/index.html')) {
  throw new Error(`${jar} has no UI in it. Build it with the with-frontend profile.`);
}

fs.rmSync(stage, { recursive: true, force: true });
fs.mkdirSync(stage, { recursive: true });
fs.copyFileSync(jar, path.join(stage, 'kafkador.jar'));
run(tool('jlink'), [
  '--add-modules', MODULES.join(','),
  '--strip-debug', '--no-header-files', '--no-man-pages', '--compress', 'zip-6',
  '--output', path.join(stage, 'runtime')
]);
console.log(`Prepared ${stage}`);
