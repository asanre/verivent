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

const generic = new Plan(JSON.stringify({
  version: '2.0.0',
  events: [{ name: 'ui_interaction', channel: 'firebase',
    schema: { required: ['element_id'], properties: { element_id: { type: 'string' }, item_id: {} } },
    variants: { by: ['element_id'], list: [{ id: 'share_playlist', when: { element_id: 'playlist_share' }, properties: { item_id: { pattern: '^pl_' } } }] } }],
}));
const sent = (element, item) => ({ type: 'analytics', id: element, ts: 0, name: 'ui_interaction', channel: 'firebase', properties: [
  { name: 'element_id', value: element, redacted: false, type: 'string' }, { name: 'item_id', value: item, redacted: false, type: 'string' }] });
const [share, unplanned] = generic.checkSession(JSON.stringify([sent('playlist_share', 'trk_1'), sent('cart_icon', 'x')]));
assert.equal(share.variant, 'share_playlist');
assert.deepEqual(Array.from(share.issues, (i) => i.line), ["item_id · For share_playlist: Doesn't match the pattern ^pl_"]);
assert.equal(unplanned.variant, null);
assert.deepEqual(Array.from(unplanned.issues, (i) => i.line), ['element_id · Not a planned variant · expects playlist_share']);

console.log(`verivent ${plan.version}: npm package checks events with the device's messages`);
