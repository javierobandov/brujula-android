import assert from "node:assert/strict";

const projectId = process.env.FIREBASE_PROJECT_ID || "demo-brujula";
const authOrigin = `http://${process.env.FIREBASE_AUTH_EMULATOR_HOST || "127.0.0.1:9099"}`;
const firestoreOrigin = `http://${process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8080"}`;

async function request(url, options = {}) {
  return fetch(url, { ...options, signal: AbortSignal.timeout(10_000) });
}

const signup = await request(
  `${authOrigin}/identitytoolkit.googleapis.com/v1/accounts:signUp?key=fake-api-key`,
  {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({
      email: `synthetic-${Date.now()}@example.test`,
      password: "synthetic-password-123",
      returnSecureToken: true,
    }),
  },
);
assert.equal(signup.status, 200, `Auth emulator signup returned ${signup.status}`);
const { idToken, localId } = await signup.json();
assert.ok(idToken && localId, "Auth emulator did not return a synthetic user token");

const fingerprint = `synthetic-${Date.now()}`;
const documentUrl = (uid, collection, id) =>
  `${firestoreOrigin}/v1/projects/${projectId}/databases/(default)/documents/users/${encodeURIComponent(uid)}/${collection}/${encodeURIComponent(id)}`;
const authorized = { authorization: `Bearer ${idToken}` };
const documentBody = (fingerprintValue, amount) =>
  JSON.stringify({
    fields: {
      fingerprint: { stringValue: fingerprintValue },
      schema_version: { integerValue: "1" },
      amount: { integerValue: String(amount) },
    },
  });

const eventUrl = documentUrl(localId, "events", fingerprint);
const eventWrite = () =>
  request(eventUrl, {
    method: "PATCH",
    headers: { ...authorized, "content-type": "application/json" },
    body: documentBody(fingerprint, 12_345),
  });
assert.equal((await eventWrite()).status, 200, "Owner event write was rejected");
assert.equal((await eventWrite()).status, 200, "Idempotent write to the same fingerprint was rejected");
assert.equal((await request(eventUrl, { headers: authorized })).status, 200, "Owner event read was rejected");

const foreignEventUrl = documentUrl("synthetic-other-user", "events", fingerprint);
assert.equal(
  (await request(foreignEventUrl, { headers: authorized })).status,
  403,
  "Cross-user event read was not denied",
);
assert.equal(
  (
    await request(foreignEventUrl, {
      method: "PATCH",
      headers: { ...authorized, "content-type": "application/json" },
      body: documentBody(fingerprint, 12_345),
    })
  ).status,
  403,
  "Cross-user event write was not denied",
);

assert.equal(
  (
    await request(documentUrl(localId, "events", `${fingerprint}-wrong-id`), {
      method: "PATCH",
      headers: { ...authorized, "content-type": "application/json" },
      body: documentBody(fingerprint, 12_345),
    })
  ).status,
  403,
  "A fingerprint that differs from its document ID was accepted",
);

const planningWrite = await request(documentUrl(localId, "planning", "snapshot"), {
  method: "PATCH",
  headers: { ...authorized, "content-type": "application/json" },
  body: JSON.stringify({ fields: { schema_version: { integerValue: "1" } } }),
});
assert.equal(planningWrite.status, 200, "Owner planning snapshot write was rejected");
assert.equal(
  (await request(documentUrl(localId, "planning", "snapshot"), { headers: authorized })).status,
  200,
  "Owner planning snapshot read was rejected",
);
assert.equal(
  (
    await request(documentUrl("synthetic-other-user", "planning", "snapshot"), {
      headers: authorized,
    })
  ).status,
  403,
  "Cross-user planning read was not denied",
);

console.log(
  "FIREBASE_EMULATOR_RULES_OK owner_event_read_write=pass same_fingerprint_update=pass cross_user_access=denied fingerprint_binding=pass planning_read_write=pass",
);
