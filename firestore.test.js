const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_student_1";
const BOB_UID = "bob_student_2";
const CHARLIE_UID = "charlie_student_3";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

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

test("Unauthenticated requests are denied across user and group collections", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const past = new Date(Date.now() - 2000);
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("homework").get()
  );
  await assertFails(
    unauthDb.collection("study_groups").where("memberUids", "array-contains", ALICE_UID).get()
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("homework").doc("hw_1").set({
      userId: ALICE_UID,
      subjectId: "subj_1",
      title: "Unauthorized",
      dueAtMillis: Date.now() + 3600000,
      priority: "HIGH",
      completed: false,
      updatedAtMillis: Date.now(),
      createdAt: past,
      updatedAt: past,
    })
  );
});

test("User profile and scoped subcollections enforce ownership, schema, and partial updates", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const nowMillis = Date.now();
  const created = new Date(nowMillis - 5000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      email: "alice@school.edu",
      gradeLevel: "Grade 12",
      themeMode: "SYSTEM",
      accentPalette: "ROYAL_BLUE",
      dailyGoalMinutes: 120,
      soundEnabled: true,
      vibrationEnabled: true,
      privacyPolicyAccepted: true,
      currentStreak: 3,
      longestStreak: 7,
      totalFocusMinutes: 240,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Cross-user profile read/write fails
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({
      displayName: "Hijacked",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Homework create & partial update matching Kotlin repository payload
  const hwRef = aliceDb.collection("users").doc(ALICE_UID).collection("homework").doc("hw_1");
  await assertSucceeds(
    hwRef.set({
      userId: ALICE_UID,
      subjectId: "math_101",
      subjectName: "Mathematics",
      title: "Calculus Worksheet 4",
      description: "Problems 1-12",
      dueAtMillis: nowMillis + 86400000,
      priority: "HIGH",
      completed: false,
      reminderEnabled: true,
      updatedAtMillis: nowMillis,
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    hwRef.update({
      completed: true,
      completedAtMillis: nowMillis,
      updatedAtMillis: nowMillis,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject ghost field / @DocumentId 'id' leakage
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("homework").doc("hw_2").set({
      id: "hw_2",
      userId: ALICE_UID,
      subjectId: "math_101",
      title: "Bad ID payload",
      dueAtMillis: nowMillis + 86400000,
      priority: "MEDIUM",
      completed: false,
      updatedAtMillis: nowMillis,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Reject future tampered timestamp
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("notes").doc("n_1").set({
      userId: ALICE_UID,
      subjectId: "math_101",
      title: "Future Note",
      content: "Test",
      important: false,
      bookmarked: false,
      updatedAtMillis: nowMillis,
      createdAt: new Date(Date.now() + 60000),
      updatedAt: new Date(Date.now() + 60000),
    })
  );

  // Reject immutable createdAt modification on update
  await assertFails(
    hwRef.update({
      createdAt: new Date(Date.now() - 100),
      updatedAt: new Date(Date.now() - 100),
    })
  );

  // Empty & populated subcollection list queries succeed for owner, fail for others
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("homework").get());
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("exams").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("homework").get());
});

test("Study groups, invite join, and group items enforce membership and query alignment", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const created = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb.collection("study_groups").doc("grp_1").set({
      ownerId: ALICE_UID,
      name: "AP Physics Crew",
      subjectTag: "Physics",
      description: "Daily problem solving",
      inviteCode: "PHY402",
      privateGroup: true,
      memberUids: [ALICE_UID],
      memberNames: ["Alice"],
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    aliceDb.collection("group_invites").doc("PHY402").set({
      inviteCode: "PHY402",
      groupId: "grp_1",
      ownerId: ALICE_UID,
      groupName: "AP Physics Crew",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Unfiltered study_groups list fails; filtered memberUids list succeeds
  await assertFails(aliceDb.collection("study_groups").get());
  await assertSucceeds(
    aliceDb.collection("study_groups").where("memberUids", "array-contains", ALICE_UID).get()
  );

  // Invite list is denied, single exact lookup succeeds
  await assertFails(bobDb.collection("group_invites").get());
  await assertSucceeds(bobDb.collection("group_invites").doc("PHY402").get());

  // Non-member cannot read group directly before joining
  await assertFails(bobDb.collection("study_groups").doc("grp_1").get());

  // Bob joins with valid invite
  await assertSucceeds(
    bobDb.collection("study_groups").doc("grp_1").update({
      memberUids: [ALICE_UID, BOB_UID],
      memberNames: ["Alice", "Bob"],
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Charlie cannot read Bob/Alice's group
  await assertFails(charlieDb.collection("study_groups").doc("grp_1").get());

  // Group item create by Bob (current member) succeeds, query aligned to memberUids succeeds
  await assertSucceeds(
    bobDb
      .collection("study_groups")
      .doc("grp_1")
      .collection("group_items")
      .doc("item_1")
      .set({
        groupId: "grp_1",
        authorId: BOB_UID,
        authorName: "Bob",
        itemType: "GOAL",
        title: "Finish 25 kinematics problems",
        body: "Focus on projectile motion.",
        completed: false,
        score: 0,
        memberUids: [ALICE_UID, BOB_UID],
        createdAt: created,
        updatedAt: created,
      })
  );

  await assertSucceeds(
    aliceDb
      .collection("study_groups")
      .doc("grp_1")
      .collection("group_items")
      .where("memberUids", "array-contains", ALICE_UID)
      .get()
  );

  // Unfiltered group_items fails
  await assertFails(
    aliceDb.collection("study_groups").doc("grp_1").collection("group_items").get()
  );
  // Charlie cannot read Bob/Alice's group items directly, query Alice's items, or create items
  await assertFails(
    charlieDb
      .collection("study_groups")
      .doc("grp_1")
      .collection("group_items")
      .doc("item_1")
      .get()
  );
  await assertFails(
    charlieDb
      .collection("study_groups")
      .doc("grp_1")
      .collection("group_items")
      .where("memberUids", "array-contains", ALICE_UID)
      .get()
  );
  await assertFails(
    charlieDb
      .collection("study_groups")
      .doc("grp_1")
      .collection("group_items")
      .doc("item_2")
      .set({
        groupId: "grp_1",
        authorId: CHARLIE_UID,
        authorName: "Charlie",
        itemType: "TASK",
        title: "Intrusion",
        completed: false,
        score: 0,
        memberUids: [CHARLIE_UID],
        createdAt: created,
        updatedAt: created,
      })
  );
});
