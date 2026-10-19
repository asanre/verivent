// Loads the built npm package the way the web app will and checks one event, expecting the exact
// messages the device gives (TrackingPlanTest asserts the same strings on Android and iOS).
//   ./gradlew :verivent:jsNodeProductionLibraryDistribution && node verivent/npm/smoke.mjs
import assert from 'node:assert/strict';
import { fileURLToPath, pathToFileURL } from 'node:url';
import path from 'node:path';

const dist = process.argv[2] ?? path.join(path.dirname(fileURLToPath(import.meta.url)), '../build/dist/js/productionLibrary');
const { Plan } = await import(pathToFileURL(path.join(path.resolve(dist), 'verivent.mjs')).href);

const plan = new Plan(JSON.stringify({
  version: '1.4.0',
  events: [{ name: 'play', channel: 'firebase', schema: {
    required: ['tracks'],
    properties: { tracks: { type: 'array', items: { properties: { duration_ms: { type: 'integer' } } } } },
  } }],
}));

const tracks = Array.from({ length: 60 }, (_, i) => ({ duration_ms: String(i) }));
const issues = plan.check('play', 'firebase', JSON.stringify({ tracks }));
assert.deepEqual(issues.map((i) => i.line), ['tracks[*].duration_ms · Expected integer, got string · 60 of 60']);
assert.deepEqual(plan.check('play', 'firebase', '{}').map((i) => i.line), ['tracks · Required, but not sent']);
assert.equal(plan.check('search', 'firebase', '{}'), null);
assert.throws(() => new Plan('{"events":[]}'), { message: 'version must be a string' });

console.log(`verivent ${plan.version}: npm package checks events with the device's messages`);
