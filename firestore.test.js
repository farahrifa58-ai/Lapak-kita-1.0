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

  // Scoped list query matches Kotlin repository
  await assertSucceeds(adminADb.collection("lapak_vendors").where("ownerUid", "==", ADMIN_A).get());

  // Partial update preserving createdAt & ownerUid
  await assertSucceeds(
    adminADb.collection("lapak_vendors").doc(VENDOR_CODE).update({
      adminFeeType: "PERCENTAGE",
      adminFeeValue: 15,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject ghost field (shadow update)
  await assertFails(
    adminADb.collection("lapak_vendors").doc(VENDOR_CODE).update({
      isVerified: true,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject tampered future timestamp
  await assertFails(
    adminADb.collection("lapak_vendors").doc(VENDOR_CODE).update({
      shopName: "Future Shop",
      updatedAt: new Date(Date.now() + 120000),
    })
  );

  // Reject immutable createdAt modification
  await assertFails(
    adminADb.collection("lapak_vendors").doc(VENDOR_CODE).update({
      createdAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("Cross-user isolation: Admin B cannot read, list, update, or delete Admin A's data", async () => {
  const adminADb = testEnv.authenticatedContext(ADMIN_A).firestore();
  const adminBDb = testEnv.authenticatedContext(ADMIN_B).firestore();

  await assertSucceeds(
    adminADb.collection("lapak_vendors").doc(VENDOR_CODE).set(validVendorPayload(ADMIN_A))
  );
  await assertSucceeds(
    adminADb.collection("lapak_vendors").doc(VENDOR_CODE).collection("private").doc("auth").set(validAuthSecretPayload(ADMIN_A))
  );

  // Admin B cannot read private auth secret
  await assertFails(
    adminBDb.collection("lapak_vendors").doc(VENDOR_CODE).collection("private").doc("auth").get()
  );
  // Admin B cannot update or delete Admin A's vendor
  await assertFails(
    adminBDb.collection("lapak_vendors").doc(VENDOR_CODE).update({
      shopName: "Hijacked",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(adminBDb.collection("lapak_vendors").doc(VENDOR_CODE).delete());
  // Admin B cannot run unfiltered list query
  await assertFails(adminBDb.collection("lapak_vendors").get());
  await assertFails(adminBDb.collection("lapak_vendors").where("ownerUid", "==", ADMIN_A).get());
});

test("Public Web Penyetor: can get own vendor by exact code, cannot list or read private secret or tamper with sales", async () => {
  const adminADb = testEnv.authenticatedContext(ADMIN_A).firestore();
  await assertSucceeds(
    adminADb.collection("lapak_vendors").doc(VENDOR_CODE).set(validVendorPayload(ADMIN_A))
  );
  await assertSucceeds(
    adminADb.collection("lapak_vendors").doc(VENDOR_CODE).collection("private").doc("auth").set(validAuthSecretPayload(ADMIN_A))
  );

  const publicDb = testEnv.unauthenticatedContext().firestore();

  // Can GET exact vendor portal document
  await assertSucceeds(publicDb.collection("lapak_vendors").doc(VENDOR_CODE).get());

  // Cannot LIST all vendors (prevents scraping other vendors)
  await assertFails(publicDb.collection("lapak_vendors").get());

  // Cannot READ private auth secret
  await assertFails(
    publicDb.collection("lapak_vendors").doc(VENDOR_CODE).collection("private").doc("auth").get()
  );

  // Cannot UPDATE vendor document directly (cannot tamper with unpaid balance, sold count, or admin fee)
  await assertFails(
    publicDb.collection("lapak_vendors").doc(VENDOR_CODE).update({
      summaryUnpaidBalance: 9999999,
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(
    publicDb.collection("lapak_vendors").doc(VENDOR_CODE).update({
      adminFeeValue: 0,
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("Web Penyetor submissions: valid token succeeds, wrong token fails, Admin processes & locks terminal state", async () => {
  const adminADb = testEnv.authenticatedContext(ADMIN_A).firestore();
  const adminBDb = testEnv.authenticatedContext(ADMIN_B).firestore();
  const publicDb = testEnv.unauthenticatedContext().firestore();

  await assertSucceeds(
    adminADb.collection("lapak_vendors").doc(VENDOR_CODE).set(validVendorPayload(ADMIN_A))
  );
  await assertSucceeds(
    adminADb.collection("lapak_vendors").doc(VENDOR_CODE).collection("private").doc("auth").set(validAuthSecretPayload(ADMIN_A))
  );

  // Wrong token rejected
  await assertFails(
    publicDb
      .collection("lapak_vendor_submissions")
      .doc("sub_bad_token")
      .set(validSubmissionPayload(ADMIN_A, VENDOR_CODE, "wrong_token_123456"))
  );

  // Wrong ownerUid spoofing rejected
  await assertFails(
    publicDb
      .collection("lapak_vendor_submissions")
      .doc("sub_bad_owner")
      .set(validSubmissionPayload(ADMIN_B, VENDOR_CODE, WRITE_TOKEN))
  );

  // Valid token submission succeeds
  await assertSucceeds(
    publicDb
      .collection("lapak_vendor_submissions")
      .doc("sub_valid_1")
      .set(validSubmissionPayload(ADMIN_A, VENDOR_CODE, WRITE_TOKEN))
  );

  // Public cannot read or list submissions (protects writeToken)
  await assertFails(publicDb.collection("lapak_vendor_submissions").doc("sub_valid_1").get());
  await assertFails(publicDb.collection("lapak_vendor_submissions").get());

  // Admin B cannot read or list Admin A's submissions
  await assertFails(adminBDb.collection("lapak_vendor_submissions").doc("sub_valid_1").get());
  await assertFails(adminBDb.collection("lapak_vendor_submissions").where("ownerUid", "==", ADMIN_A).get());

  // Admin A queries own submissions (matches Kotlin repository) and marks PROCESSED
  await assertSucceeds(
    adminADb.collection("lapak_vendor_submissions").where("ownerUid", "==", ADMIN_A).get()
  );
  await assertSucceeds(
    adminADb.collection("lapak_vendor_submissions").doc("sub_valid_1").update({
      status: "PROCESSED",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Terminal state lock: once PROCESSED, cannot be updated again
  await assertFails(
    adminADb.collection("lapak_vendor_submissions").doc("sub_valid_1").update({
      status: "REJECTED",
      updatedAt: new Date(Date.now() - 100),
    })
  );
});

test("Financial collections (transactions, expenses, settlements): owner CRUD succeeds, cross-user & public denied", async () => {
  const adminADb = testEnv.authenticatedContext(ADMIN_A).firestore();
  const adminBDb = testEnv.authenticatedContext(ADMIN_B).firestore();
  const publicDb = testEnv.unauthenticatedContext().firestore();
  const now = new Date(Date.now() - 2000);

  const validTrx = {
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
        productName: "Nasi Kuning Komplit",
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
  };

  const validExpense = {
    ownerUid: ADMIN_A,
    localExpenseId: 1,
    title: "Plastik Kresek",
    amount: 15000,
    expenseDateMillis: Date.now() - 4000,
    notes: "2 pak besar",
    createdAt: now,
    updatedAt: now,
  };

  const validSettlement = {
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
  };

  // Owner Admin A create + scoped list + update + delete
  await assertSucceeds(adminADb.collection("lapak_transactions").doc("trx_20261126_001").set(validTrx));
  await assertSucceeds(adminADb.collection("lapak_expenses").doc("exp_1").set(validExpense));
  await assertSucceeds(adminADb.collection("lapak_settlements").doc("pay_1").set(validSettlement));

  await assertSucceeds(adminADb.collection("lapak_transactions").where("ownerUid", "==", ADMIN_A).get());
  await assertSucceeds(adminADb.collection("lapak_expenses").where("ownerUid", "==", ADMIN_A).get());
  await assertSucceeds(adminADb.collection("lapak_settlements").where("ownerUid", "==", ADMIN_A).get());

  await assertSucceeds(
    adminADb.collection("lapak_transactions").doc("trx_20261126_001").update({
      cancelled: true,
      cancellationReason: "Salah input",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Unauthenticated public rejected on all financial collections
  await assertFails(publicDb.collection("lapak_transactions").doc("trx_20261126_001").get());
  await assertFails(publicDb.collection("lapak_expenses").doc("exp_1").get());
  await assertFails(publicDb.collection("lapak_settlements").doc("pay_1").get());

  // Cross-user Admin B rejected on read, list, update, delete
  await assertFails(adminBDb.collection("lapak_transactions").doc("trx_20261126_001").get());
  await assertFails(adminBDb.collection("lapak_transactions").where("ownerUid", "==", ADMIN_A).get());
  await assertFails(adminBDb.collection("lapak_expenses").doc("exp_1").delete());
  await assertFails(adminBDb.collection("lapak_settlements").doc("pay_1").update({
    amount: 999999,
    updatedAt: new Date(Date.now() - 500),
  }));

  // Owner can delete own expense
  await assertSucceeds(adminADb.collection("lapak_expenses").doc("exp_1").delete());
});

test("License codes: developer generates & lists, buyer activates once, second device blocked, non-dev denied list/create", async () => {
  const devDb = testEnv
    .authenticatedContext("dev_uid_1", {
      email: "rendykurniawan0311@gmail.com",
      email_verified: true,
    })
    .firestore();
  const buyerDb = testEnv
    .authenticatedContext("buyer_uid_1", {
      email: "pembeli@gmail.com",
      email_verified: true,
    })
    .firestore();
  const publicDb = testEnv.unauthenticatedContext().firestore();

  const code = "LPK-A7K9-M2P4";
  const now = new Date(Date.now() - 2000);
  const validLicense = {
    code,
    appName: "Lapak Kita",
    productKey: "lapak_kita",
    buyerNote: "Bu Sari - Bandung",
    isUsed: false,
    usedByDeviceId: "",
    createdByUid: "dev_uid_1",
    createdAt: now,
    updatedAt: now,
  };

  // Buyer and unauthenticated users CANNOT create or list license codes
  await assertFails(buyerDb.collection("license_codes").doc(code).set({ ...validLicense, createdByUid: "buyer_uid_1" }));
  await assertFails(publicDb.collection("license_codes").doc(code).set(validLicense));
  await assertFails(buyerDb.collection("license_codes").get());
  await assertFails(publicDb.collection("license_codes").get());

  // Developer can create and list license codes
  await assertSucceeds(devDb.collection("license_codes").doc(code).set(validLicense));
  await assertSucceeds(devDb.collection("license_codes").get());

  // Buyer (before Google Sign-In) can GET exact code and atomically claim it once
  await assertSucceeds(publicDb.collection("license_codes").doc(code).get());
  await assertSucceeds(
    publicDb.collection("license_codes").doc(code).update({
      isUsed: true,
      usedByDeviceId: "AND-DEVICE-11112222",
      usedAt: new Date(Date.now() - 500),
      appName: "Lapak Kita",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Second device CANNOT overwrite an already-used license code (terminal state lock)
  await assertFails(
    publicDb.collection("license_codes").doc(code).update({
      isUsed: true,
      usedByDeviceId: "AND-DEVICE-99998888",
      usedAt: new Date(Date.now() - 100),
      appName: "Lapak Kita",
      updatedAt: new Date(Date.now() - 100),
    })
  );

  // Buyer CANNOT delete license code; Developer CAN delete
  await assertFails(buyerDb.collection("license_codes").doc(code).delete());
  await assertSucceeds(devDb.collection("license_codes").doc(code).delete());
});


