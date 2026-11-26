const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ADMIN_A = "admin_a_uid";
const ADMIN_B = "admin_b_uid";
const VENDOR_CODE = "LPK-AB12CD";
const WRITE_TOKEN = "tok_secret_9876543210abc";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validVendorPayload(ownerUid = ADMIN_A, vendorCode = VENDOR_CODE, now = new Date(Date.now() - 2000)) {
  return {
    ownerUid,
    vendorCode,
    localSellerId: 1,
    shopName: "Lapak Kita",
    sellerName: "Dapur Mama Farida",
    sellerStatus: "AKTIF",
    adminFeeType: "FIXED",
    adminFeeValue: 2000,
    summaryTotalStock: 15,
    summaryTotalSold: 4,
    summaryUnpaidBalance: 48000,
    products: [
      {
        productKey: "prod_1",
        localProductId: 1,
        name: "Burger Sapi Keju",
        category: "Makanan",
        unit: "pcs",
        sellingPrice: 15000,
        sellerShare: 13000,
        adminProfitPerUnit: 2000,
        currentStock: 15,
      },
    ],
    lastProcessedSubmissionId: "",
    createdAt: now,
    updatedAt: now,
  };
}

function validAuthSecretPayload(ownerUid = ADMIN_A, token = WRITE_TOKEN, now = new Date(Date.now() - 2000)) {
  return {
    ownerUid,
    writeToken: token,
    createdAt: now,
    updatedAt: now,
  };
}

function validSubmissionPayload(
  ownerUid = ADMIN_A,
  vendorCode = VENDOR_CODE,
  token = WRITE_TOKEN,
  now = new Date(Date.now() - 1000)
) {
  return {
    ownerUid,
    vendorCode,
    writeToken: token,
    status: "PENDING",
    newProducts: [
      {
        tempId: "new_1",
        name: "Risoles Mayo",
        category: "Snack",
        unit: "pcs",
        sellingPrice: 5000,
        initialStock: 10,
      },
    ],
    editedProducts: [],
    stockAdditions: [
      {
        localProductId: 1,
        productKey: "prod_1",
        productName: "Burger Sapi Keju",
        addQty: 10,
        note: "Kiriman pagi",
      },
    ],
    note: "Kiriman via Web Penyetor",
    createdAt: now,
    updatedAt: now,
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

test("Admin A can create, update, query, and delete own vendor + private auth secret", async () => {
  const adminADb = testEnv.authenticatedContext(ADMIN_A).firestore();
  const created = new Date(Date.now() - 5000);

  await assertSucceeds(
    adminADb.collection("lapak_vendors").doc(VENDOR_CODE).set(validVendorPayload(ADMIN_A, VENDOR_CODE, created))
  );
  await assertSucceeds(
    adminADb
      .collection("lapak_vendors")
      .doc(VENDOR_CODE)
      .collection("private")
      .doc("auth")
      .set(validAuthSecretPayload(ADMIN_A, WRITE_TOKEN, created))
  );

  await assertSucceeds(adminADb.collection("lapak_vendors").where("ownerUid", "==", ADMIN_A).get());

  await assertSucceeds(
    adminADb.collection("lapak_vendors").doc(VENDOR_CODE).update({
      adminFeeType: "PERCENTAGE",
      adminFeeValue: 15,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  await assertFails(
    adminADb.collection("lapak_vendors").doc(VENDOR_CODE).update({
      isVerified: true,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Full clean set() overwrite replaces legacy root fields & refreshes per-item sellingPrice/basePrice/commission
  const adminBDb = testEnv.authenticatedContext(ADMIN_B).firestore();
  const legacyCode = "LPK-NGL67G";
  const legacyCreated = new Date(Date.now() - 10000);
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("lapak_vendors").doc(legacyCode).set({
      ...validVendorPayload(ADMIN_A, legacyCode, legacyCreated),
      legacyStaleFee: 9999,
    });
  });

  const cleanReplacement = {
    ...validVendorPayload(ADMIN_A, legacyCode, legacyCreated),
    products: [
      {
        id: 1,
        localProductId: 1,
        name: "Risol Mayo",
        category: "Gorengan",
        unit: "pcs",
        sellingPrice: 5000,
        basePrice: 3500,
        sellerShare: 3500,
        commission: 1500,
        adminProfitPerUnit: 1500,
        stock: 20,
        currentStock: 20,
        isActive: true,
      },
    ],
    updatedAt: new Date(Date.now() - 500),
  };

  await assertSucceeds(adminADb.collection("lapak_vendors").doc(legacyCode).set(cleanReplacement));
  await assertFails(adminBDb.collection("lapak_vendors").doc(legacyCode).set({
    ...cleanReplacement,
    ownerUid: ADMIN_B,
  }));
});

test("Public Web Penyetor: can get vendor by exact code and submit with valid token without login", async () => {
  const adminADb = testEnv.authenticatedContext(ADMIN_A).firestore();
  const publicDb = testEnv.unauthenticatedContext().firestore();

  await assertSucceeds(
    adminADb.collection("lapak_vendors").doc(VENDOR_CODE).set(validVendorPayload(ADMIN_A))
  );
  await assertSucceeds(
    adminADb.collection("lapak_vendors").doc(VENDOR_CODE).collection("private").doc("auth").set(validAuthSecretPayload(ADMIN_A))
  );

  await assertSucceeds(publicDb.collection("lapak_vendors").doc(VENDOR_CODE).get());
  await assertFails(publicDb.collection("lapak_vendors").get());
  await assertFails(publicDb.collection("lapak_vendors").doc(VENDOR_CODE).collection("private").doc("auth").get());

  await assertFails(
    publicDb
      .collection("lapak_vendor_submissions")
      .doc("sub_bad_token")
      .set(validSubmissionPayload(ADMIN_A, VENDOR_CODE, "wrong_token_123456"))
  );

  await assertSucceeds(
    publicDb
      .collection("lapak_vendor_submissions")
      .doc("sub_valid_1")
      .set(validSubmissionPayload(ADMIN_A, VENDOR_CODE, WRITE_TOKEN))
  );

  await assertSucceeds(
    adminADb.collection("lapak_vendor_submissions").doc("sub_valid_1").update({
      status: "PROCESSED",
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("Activation codes (Max 2 Devices): 1st claim -> 2nd HP append succeeds -> 3rd HP & second buyer blocked", async () => {
  const devDb = testEnv
    .authenticatedContext("dev_uid_1", {
      email: "rendykurniawan0311@gmail.com",
      email_verified: true,
    })
    .firestore();
  const buyer1Db = testEnv
    .authenticatedContext(ADMIN_A, {
      email: "toko.bu.sari@gmail.com",
      email_verified: true,
    })
    .firestore();
  const buyer2Db = testEnv
    .authenticatedContext(ADMIN_B, {
      email: "toko.lain@gmail.com",
      email_verified: true,
    })
    .firestore();
  const publicDb = testEnv.unauthenticatedContext().firestore();

  const code = "RAFDEV-8K4M-2P9Q";
  const now = new Date(Date.now() - 3000);

  await assertSucceeds(
    devDb.collection("activation_codes").doc(code).set({
      code,
      status: "available",
      app: "Lapak Kita",
      buyerNote: "Bu Sari",
      createdAt: now,
    })
  );

  await assertFails(buyer1Db.collection("activation_codes").get());
  await assertFails(publicDb.collection("activation_codes").get());
  await assertSucceeds(buyer1Db.collection("activation_codes").doc(code).get());

  // 1st HP claims code: available -> used
  await assertSucceeds(
    buyer1Db.collection("activation_codes").doc(code).update({
      status: "used",
      usedByUid: ADMIN_A,
      usedByEmail: "toko.bu.sari@gmail.com",
      usedByBusinessName: "Lapak Bu Sari",
      registeredDeviceId: "AND-DEVICE11111111",
      registeredDeviceIds: ["AND-DEVICE11111111"],
      usedAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Same owner's 2nd HP appends to registeredDeviceIds (2/2) -> SUCCEEDS
  await assertSucceeds(
    buyer1Db.collection("activation_codes").doc(code).update({
      registeredDeviceId: "AND-DEVICE11111111",
      registeredDeviceIds: ["AND-DEVICE11111111", "AND-DEVICE22222222"],
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // 3rd HP by same owner -> BLOCKED (max 2 devices)
  await assertFails(
    buyer1Db.collection("activation_codes").doc(code).update({
      registeredDeviceId: "AND-DEVICE11111111",
      registeredDeviceIds: ["AND-DEVICE11111111", "AND-DEVICE22222222", "AND-DEVICE33333333"],
      updatedAt: new Date(Date.now() - 200),
    })
  );

  // Different buyer CANNOT claim or modify already-used code
  await assertFails(
    buyer2Db.collection("activation_codes").doc(code).update({
      registeredDeviceIds: ["AND-DEVICE99999999"],
      updatedAt: new Date(Date.now() - 100),
    })
  );
});

test("Users profile Multi-Device Lock (Max 2 HP): 1st & 2nd HP allowed, 3rd HP & overwrite blocked, admin reset works", async () => {
  const devDb = testEnv
    .authenticatedContext("dev_uid_1", {
      email: "rendykurniawan0311@gmail.com",
      email_verified: true,
    })
    .firestore();
  const userADb = testEnv.authenticatedContext(ADMIN_A).firestore();
  const userBDb = testEnv.authenticatedContext(ADMIN_B).firestore();
  const publicDb = testEnv.unauthenticatedContext().firestore();
  const now = new Date(Date.now() - 3000);

  // Create profile on HP #1 (Kasir)
  await assertSucceeds(
    userADb.collection("users").doc(ADMIN_A).set({
      userId: ADMIN_A,
      email: "toko.bu.sari@gmail.com",
      businessName: "Lapak Bu Sari",
      licenseCode: "RAFDEV-8K4M-2P9Q",
      registeredDeviceId: "AND-HP11111111",
      registeredDeviceIds: ["AND-HP11111111"],
      active: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertFails(publicDb.collection("users").doc(ADMIN_A).get());
  await assertFails(userBDb.collection("users").doc(ADMIN_A).get());

  // HP #2 (Owner) logs in and appends itself -> SUCCEEDS (2/2)
  await assertSucceeds(
    userADb.collection("users").doc(ADMIN_A).update({
      registeredDeviceId: "AND-HP11111111",
      registeredDeviceIds: ["AND-HP11111111", "AND-HP22222222"],
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // HP #3 tries to append a 3rd device -> BLOCKED
  await assertFails(
    userADb.collection("users").doc(ADMIN_A).update({
      registeredDeviceIds: ["AND-HP11111111", "AND-HP22222222", "AND-HP33333333"],
      updatedAt: new Date(Date.now() - 800),
    })
  );

  // HP #3 tries to replace HP #2 -> BLOCKED
  await assertFails(
    userADb.collection("users").doc(ADMIN_A).update({
      registeredDeviceIds: ["AND-HP11111111", "AND-HP33333333"],
      updatedAt: new Date(Date.now() - 700),
    })
  );

  // License Admin resets registeredDeviceIds to [] and registeredDeviceId to ""
  await assertSucceeds(
    devDb.collection("users").doc(ADMIN_A).update({
      registeredDeviceId: "",
      registeredDeviceIds: [],
      updatedAt: new Date(Date.now() - 400),
    })
  );

  // Now User A can register a fresh HP #1!
  await assertSucceeds(
    userADb.collection("users").doc(ADMIN_A).update({
      registeredDeviceId: "AND-HP33333333",
      registeredDeviceIds: ["AND-HP33333333"],
      updatedAt: new Date(Date.now() - 200),
    })
  );
});

test("User-scoped real-time subcollections (vendors, transactions, expenses, settlements) & device_registry", async () => {
  const userADb = testEnv.authenticatedContext(ADMIN_A).firestore();
  const userBDb = testEnv.authenticatedContext(ADMIN_B).firestore();
  const publicDb = testEnv.unauthenticatedContext().firestore();
  const now = new Date(Date.now() - 2000);

  const vendorRef = userADb.collection("users").doc(ADMIN_A).collection("vendors").doc(VENDOR_CODE);
  const trxRef = userADb.collection("users").doc(ADMIN_A).collection("transactions").doc("trx_20261126_001");
  const expRef = userADb.collection("users").doc(ADMIN_A).collection("expenses").doc("exp_1");
  const setRef = userADb.collection("users").doc(ADMIN_A).collection("settlements").doc("pay_1");

  await assertSucceeds(
    vendorRef.set({
      ownerUid: ADMIN_A,
      vendorCode: VENDOR_CODE,
      sellerName: "Dapur Bu Sari",
      whatsappNumber: "081234567890",
      address: "Jl. Mawar No. 1",
      notes: "Kue pagi",
      sellerStatus: "AKTIF",
      writeToken: WRITE_TOKEN,
      customAdminFeeType: "",
      customAdminFeeValue: 0,
      sourceDeviceId: "AND-HP11111111",
      products: [
        {
          name: "Nasi Kuning",
          category: "Makanan",
          unit: "porsi",
          sellingPrice: 12000,
          sellerShare: 10000,
          adminProfitPerUnit: 2000,
          currentStock: 18,
          isActive: true,
        },
      ],
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    trxRef.set({
      ownerUid: ADMIN_A,
      transactionNumber: "TRX-20261126-001",
      timestampMillis: Date.now() - 5000,
      totalAmount: 27000,
      totalItemsCount: 2,
      cashReceived: 30000,
      changeGiven: 3000,
      paymentMethod: "Tunai",
      totalAdminProfit: 4000,
      totalSellerShare: 23000,
      cancelled: false,
      cancellationReason: "",
      items: [
        {
          productName: "Nasi Kuning",
          sellerName: "Dapur Bu Sari",
          unit: "porsi",
          unitPrice: 12000,
          sellerSharePerUnit: 10000,
          adminProfitPerUnit: 2000,
          quantity: 1,
          subtotal: 12000,
          totalSellerShare: 10000,
          totalAdminProfit: 2000,
        },
      ],
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    expRef.set({
      ownerUid: ADMIN_A,
      localExpenseId: 1,
      title: "Plastik Kresek",
      amount: 15000,
      expenseDateMillis: Date.now() - 4000,
      notes: "2 pak besar",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    setRef.set({
      ownerUid: ADMIN_A,
      localPaymentId: 1,
      localSellerId: 2,
      sellerName: "Dapur Bu Sari",
      vendorCode: VENDOR_CODE,
      amount: 50000,
      paymentDateMillis: Date.now() - 3000,
      paymentMethod: "Transfer",
      notes: "Setoran harian",
      status: "LUNAS",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Owner can list all 4 own real-time subcollections
  await assertSucceeds(userADb.collection("users").doc(ADMIN_A).collection("vendors").get());
  await assertSucceeds(userADb.collection("users").doc(ADMIN_A).collection("transactions").get());
  await assertSucceeds(userADb.collection("users").doc(ADMIN_A).collection("expenses").get());
  await assertSucceeds(userADb.collection("users").doc(ADMIN_A).collection("settlements").get());

  // Cross-user and public denied
  await assertFails(userBDb.collection("users").doc(ADMIN_A).collection("vendors").get());
  await assertFails(publicDb.collection("users").doc(ADMIN_A).collection("vendors").get());
  await assertFails(userBDb.collection("users").doc(ADMIN_A).collection("transactions").get());
  await assertFails(publicDb.collection("users").doc(ADMIN_A).collection("transactions").get());
  await assertFails(userBDb.collection("users").doc(ADMIN_A).collection("expenses").doc("exp_1").delete());

  // Device registry exact-ID lookup succeeds for reinstall auto-restore, list denied
  await assertSucceeds(
    userADb.collection("device_registry").doc("AND-HP11111111").set({
      ownerUid: ADMIN_A,
      emailHint: "toko.bu.sari@gmail.com",
      businessName: "Lapak Bu Sari",
      licenseCode: "RAFDEV-8K4M-2P9Q",
      active: true,
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(publicDb.collection("device_registry").doc("AND-HP11111111").get());
  await assertFails(publicDb.collection("device_registry").get());
  await assertFails(
    userBDb.collection("device_registry").doc("AND-HP11111111").update({
      emailHint: "hijack@gmail.com",
      updatedAt: new Date(Date.now() - 100),
    })
  );
});

test("Legacy user auto-migration: single registeredDeviceId migrates cleanly to registeredDeviceIds [HP1, HP2] and blocks HP3", async () => {
  const userADb = testEnv
    .authenticatedContext(ADMIN_A, {
      email: "toko.lama@gmail.com",
      email_verified: true,
    })
    .firestore();
  const code = "LPK-LEGACY-001";
  const now = new Date(Date.now() - 5000);

  // Seed legacy documents (only single registeredDeviceId, NO registeredDeviceIds array!)
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const adminFs = ctx.firestore();
    await adminFs.collection("users").doc(ADMIN_A).set({
      userId: ADMIN_A,
      email: "toko.lama@gmail.com",
      businessName: "Lapak Lama",
      licenseCode: code,
      registeredDeviceId: "AND-LEGACY-HP1",
      active: true,
      createdAt: now,
      updatedAt: now,
    });
    await adminFs.collection("activation_codes").doc(code).set({
      code,
      status: "used",
      usedByUid: ADMIN_A,
      usedByEmail: "toko.lama@gmail.com",
      registeredDeviceId: "AND-LEGACY-HP1",
      updatedAt: now,
    });
  });

  // HP #2 CANNOT overwrite/drop legacy HP #1!
  await assertFails(
    userADb.collection("users").doc(ADMIN_A).update({
      registeredDeviceId: "AND-NEW-HP2",
      registeredDeviceIds: ["AND-NEW-HP2"],
      updatedAt: new Date(Date.now() - 2000),
    })
  );

  // HP #2 migrates legacy doc directly by preserving HP #1 as device #1 and appending HP #2 -> SUCCEEDS!
  await assertSucceeds(
    userADb.collection("users").doc(ADMIN_A).update({
      registeredDeviceId: "AND-LEGACY-HP1",
      registeredDeviceIds: ["AND-LEGACY-HP1", "AND-NEW-HP2"],
      updatedAt: new Date(Date.now() - 1500),
    })
  );

  // Matching migration on activation_codes/{code} -> SUCCEEDS!
  await assertSucceeds(
    userADb.collection("activation_codes").doc(code).update({
      registeredDeviceId: "AND-LEGACY-HP1",
      registeredDeviceIds: ["AND-LEGACY-HP1", "AND-NEW-HP2"],
      updatedAt: new Date(Date.now() - 1400),
    })
  );

  // HP #3 is now BLOCKED on both users/{uid} and activation_codes/{code}!
  await assertFails(
    userADb.collection("users").doc(ADMIN_A).update({
      registeredDeviceId: "AND-LEGACY-HP1",
      registeredDeviceIds: ["AND-LEGACY-HP1", "AND-NEW-HP2", "AND-EXTRA-HP3"],
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(
    userADb.collection("activation_codes").doc(code).update({
      registeredDeviceId: "AND-LEGACY-HP1",
      registeredDeviceIds: ["AND-LEGACY-HP1", "AND-NEW-HP2", "AND-EXTRA-HP3"],
      updatedAt: new Date(Date.now() - 900),
    })
  );

  // Also test legacy doc with empty registeredDeviceIds: [] + non-empty registeredDeviceId
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const adminFs = ctx.firestore();
    await adminFs.collection("users").doc(ADMIN_A).set({
      email: "toko.lama@gmail.com",
      businessName: "Lapak Lama",
      licenseCode: code,
      registeredDeviceId: "AND-LEGACY-HP1",
      registeredDeviceIds: [],
      active: true,
      updatedAt: now,
    });
  });

  // Dropping HP #1 fails even when registeredDeviceIds started empty
  await assertFails(
    userADb.collection("users").doc(ADMIN_A).update({
      registeredDeviceId: "AND-NEW-HP2",
      registeredDeviceIds: ["AND-NEW-HP2"],
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Preserving HP #1 + appending HP #2 succeeds!
  await assertSucceeds(
    userADb.collection("users").doc(ADMIN_A).update({
      registeredDeviceId: "AND-LEGACY-HP1",
      registeredDeviceIds: ["AND-LEGACY-HP1", "AND-NEW-HP2"],
      updatedAt: new Date(Date.now() - 400),
    })
  );
});

