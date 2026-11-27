const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_shubham_123";
const BOB_UID = "bob_other_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validPreferences(uid, ts) {
  return {
    userId: uid,
    defaultLanguage: "HINGLISH",
    defaultPackageFormat: "DUAL",
    memoryNotes: ["Prefers concise documentary hooks in Hinglish"],
    backendProxyUrl: "",
    automationEnabled: true,
    automationHour: 8,
    automationMinute: 30,
    automationBeat: "History & Science",
    createdAt: ts,
    updatedAt: ts,
  };
}

function validStudioProject(uid, ts) {
  return {
    userId: uid,
    topicTitle: "Chandrayaan-3 South Pole Water Ice Analysis",
    categoryBeat: "Science & Tech",
    language: "HINGLISH",
    packageFormat: "DUAL",
    angle: "How thermal measurements reshaped lunar polar exploration",
    hook: "Kya Chand ke South Pole par barf sach mein hai? Chaliye facts dekhte hain.",
    whyCoverNow: "New ISRO telemetry datasets published this quarter.",
    stage: "DISCOVERED",
    topicApproved: false,
    finalApproved: false,
    verifiedFacts: [
      {
        claim: "ChaSTE payload recorded steep thermal gradient near surface",
        sourceTitle: "ISRO Science Update",
        sourceUrl: "https://www.isro.gov.in",
        publishedDate: "2024-08-23",
      },
    ],
    disputedClaims: ["Exact mineable water volume remains unverified"],
    assumptions: ["Assumes future rover drills reach 1m depth"],
    shortScript: "0-5s Hook: Kya Chand par paani sach mein hai?",
    longScript: "Chapter 1: Introduction to Lunar South Pole...",
    visualDirections: "Scene 1: Archival lunar map zoom in.",
    onScreenText: ["CHANDRAYAAN-3 FACTS", "SOURCE: ISRO"],
    footageAssets: [
      {
        title: "Moon South Pole Relief",
        provider: "Wikimedia Commons",
        assetUrl: "https://commons.wikimedia.org/wiki/File:Moon.jpg",
        thumbnailUrl: "",
        creator: "NASA/GSFC",
        license: "Public Domain",
        attributionRequired: false,
        commercialCaution: "Verify public domain tag before commercial reuse",
      },
    ],
    voiceoverVoice: "Kore",
    voiceoverStatus: "NOT_GENERATED",
    voiceoverLocalPath: "",
    subtitlesSrt: "1\n00:00:00,000 --> 00:00:04,000\nKya Chand par paani hai?\n",
    capcutPlan: "00:00-00:05: Hook clip + subtle riser SFX",
    capcutChecklist: [{ label: "Verify all attribution credits", completed: false }],
    createdAt: ts,
    updatedAt: ts,
  };
}

function validChatMessage(uid, ts) {
  return {
    userId: uid,
    threadId: "default_thread",
    role: "assistant",
    content: "Confirmed Fact: ISRO launched Chandrayaan-3 on 14 July 2023.",
    language: "HINGLISH",
    citations: [{ title: "ISRO", url: "https://www.isro.gov.in" }],
    certaintyTag: "VERIFIED",
    createdAt: ts,
    updatedAt: ts,
  };
}

function validAutomationRun(uid, ts) {
  return {
    userId: uid,
    triggerType: "SCHEDULED",
    status: "COMPLETED",
    attemptCount: 1,
    summary: "Discovered 3 documentary topics for Science & Tech.",
    projectId: "proj_001",
    errorMessage: "",
    createdAt: ts,
    updatedAt: ts,
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated users cannot read or write any Shubham AI collections", async () => {
  const unauth = testEnv.unauthenticatedContext().firestore();
  const past = new Date(Date.now() - 5000);
  await assertFails(unauth.collection(`users/${ALICE_UID}/studioProjects`).get());
  await assertFails(
    unauth.doc(`users/${ALICE_UID}/preferences/profile`).set(validPreferences(ALICE_UID, past))
  );
  await assertFails(
    unauth.doc(`users/${ALICE_UID}/studioProjects/proj_1`).set(validStudioProject(ALICE_UID, past))
  );
  await assertFails(
    unauth.doc(`users/${ALICE_UID}/chatMessages/msg_1`).set(validChatMessage(ALICE_UID, past))
  );
  await assertFails(
    unauth.doc(`users/${ALICE_UID}/automationRuns/run_1`).set(validAutomationRun(ALICE_UID, past))
  );
});

test("Owner CRUD succeeds across preferences, chatMessages, studioProjects, and automationRuns", async () => {
  const alice = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);
  const updated = new Date(Date.now() - 1000);

  // Preferences create + partial update (including economy mode) + read
  const prefRef = alice.doc(`users/${ALICE_UID}/preferences/profile`);
  await assertSucceeds(prefRef.set(validPreferences(ALICE_UID, created)));
  await assertSucceeds(
    prefRef.update({
      defaultLanguage: "HINDI",
      memoryNotes: ["Updated preference"],
      economyModeEnabled: true,
      chatWebSearchDefault: false,
      updatedAt: updated,
    })
  );
  await assertSucceeds(prefRef.get());

  // Studio project create + partial update (topic approval + PEHLU blueprint & audit) + list + delete
  const projRef = alice.doc(`users/${ALICE_UID}/studioProjects/proj_1`);
  await assertSucceeds(projRef.set(validStudioProject(ALICE_UID, created)));
  await assertSucceeds(
    projRef.update({
      topicApproved: true,
      stage: "TOPIC_APPROVED",
      titleOptions: ["The Untold Lunar South Pole Story"],
      thumbnailConcepts: ["Lunar crater silhouette with bold yellow text: REAL ICE?"],
      projectOverview: { documentaryTitle: "South Pole Truth", estimatedDuration: "8 min" },
      blueprintScenes: [{ sceneNumber: 1, startTime: "00:00", endTime: "00:15", purpose: "Hook" }],
      auditChecklist: [{ label: "Claims verified against primary sources", completed: true }],
      updatedAt: updated,
    })
  );
  await assertSucceeds(alice.collection(`users/${ALICE_UID}/studioProjects`).get());

  // Chat messages create + list + delete
  const msgRef = alice.doc(`users/${ALICE_UID}/chatMessages/msg_1`);
  await assertSucceeds(msgRef.set(validChatMessage(ALICE_UID, created)));
  await assertSucceeds(alice.collection(`users/${ALICE_UID}/chatMessages`).get());

  // Automation runs create + update + list
  const runRef = alice.doc(`users/${ALICE_UID}/automationRuns/run_1`);
  await assertSucceeds(runRef.set(validAutomationRun(ALICE_UID, created)));
  await assertSucceeds(
    runRef.update({
      status: "FAILED",
      attemptCount: 3,
      errorMessage: "Offline timeout after 3 retries",
      updatedAt: updated,
    })
  );
  await assertSucceeds(alice.collection(`users/${ALICE_UID}/automationRuns`).get());
  await assertSucceeds(projRef.delete());
});

test("Cross-user isolation blocks Bob from reading or modifying Alice's data", async () => {
  const created = new Date(Date.now() - 10000);
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.doc(`users/${ALICE_UID}/studioProjects/proj_1`).set(validStudioProject(ALICE_UID, created));
    await db.doc(`users/${ALICE_UID}/preferences/profile`).set(validPreferences(ALICE_UID, created));
  });

  const bob = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bob.doc(`users/${ALICE_UID}/studioProjects/proj_1`).get());
  await assertFails(bob.collection(`users/${ALICE_UID}/studioProjects`).get());
  await assertFails(
    bob.doc(`users/${ALICE_UID}/studioProjects/proj_1`).update({
      topicTitle: "Hijacked",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(bob.doc(`users/${ALICE_UID}/preferences/profile`).get());
});

test("Security bounds reject ghost fields, immutable createdAt mutation, future timestamps, and @DocumentId key", async () => {
  const alice = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);
  const future = new Date(Date.now() + 60000);

  // Reject @DocumentId 'id' inside payload
  const withId = { ...validStudioProject(ALICE_UID, created), id: "proj_bad" };
  await assertFails(alice.doc(`users/${ALICE_UID}/studioProjects/proj_bad`).set(withId));

  // Reject ghost field
  const withGhost = { ...validPreferences(ALICE_UID, created), isAdmin: true };
  await assertFails(alice.doc(`users/${ALICE_UID}/preferences/profile`).set(withGhost));

  // Reject future timestamp
  await assertFails(
    alice.doc(`users/${ALICE_UID}/studioProjects/proj_future`).set(validStudioProject(ALICE_UID, future))
  );

  // Reject immutable createdAt mutation on update
  const projRef = alice.doc(`users/${ALICE_UID}/studioProjects/proj_valid`);
  await assertSucceeds(projRef.set(validStudioProject(ALICE_UID, created)));
  await assertFails(
    projRef.update({
      createdAt: new Date(Date.now() - 2000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});
