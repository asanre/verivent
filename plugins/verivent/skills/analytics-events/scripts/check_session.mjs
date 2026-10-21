// Checks a session rebuilt by read_lines.py against a tracking plan file, with Verivent's own
// validator (the npm package, 0.9.0 or later), so the verdicts and messages are the device's.
//
//   node check_session.mjs --validator <dir> plan.json session.json
//
// <dir> is the package (holding verivent.mjs), or a prefix it was installed into with
// `npm install --prefix <dir> <tgz>`.
import { existsSync } from 'node:fs';
import { readFile } from 'node:fs/promises';
import path from 'node:path';
import { pathToFileURL } from 'node:url';

const args = process.argv.slice(2);
const at = args.indexOf('--validator');
if (at < 0 || args.length !== 4) {
  console.error('usage: node check_session.mjs --validator <dir> plan.json session.json');
  process.exit(2);
}
const validator = path.resolve(args[at + 1]);
const [planPath, sessionPath] = args.filter((_, i) => i !== at && i !== at + 1);

const entry = [validator, path.join(validator, 'verivent.mjs'), path.join(validator, 'node_modules/verivent/verivent.mjs')]
  .find((candidate) => candidate.endsWith('.mjs') && existsSync(candidate));
if (!entry) {
  console.error(`no verivent.mjs in ${validator}`);
  process.exit(2);
}
const { Plan } = await import(pathToFileURL(entry).href);

let plan;
try {
  plan = new Plan(await readFile(planPath, 'utf8'));
} catch (error) {
  // The plan file is broken: the message is the one the device gives when loading it.
  console.error(`invalid plan: ${error.message}`);
  process.exit(1);
}

const events = plan.checkSession(await readFile(sessionPath, 'utf8')).map((event) => ({
  id: event.id,
  name: event.name,
  channel: event.channel,
  verdict: event.verdict,
  issues: Array.from(event.issues, (issue) => issue.line),
  untyped: event.untyped,
}));
console.log(JSON.stringify({ planVersion: plan.version, events }, null, 2));
