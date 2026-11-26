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

test("Activation codes (License Admin): available -> used once, second buyer blocked, non-dev cannot list/create", async () => {
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
  const now = new Date(Date.now() - 2000);

  // License Admin creates code with status: "available" + custom metadata fields
  await assertSucceeds(
    devDb.collection("activation_codes").doc(code).set({
      code,
      status: "available",
      app: "Lapak Kita",
      buyerNote: "Bu Sari",
      createdAt: now,
    })
  );

  // Non-dev cannot list, create, or delete activation codes
  await assertFails(buyer1Db.collection("activation_codes").get());
  await assertFails(publicDb.collection("activation_codes").get());
  await assertFails(buyer1Db.collection("activation_codes").doc("RAFDEV-FAKE-0001").set({ status: "available" }));

  // Exact-ID lookup allowed before/during registration
  await assertSucceeds(publicDb.collection("activation_codes").doc(code).get());
  await assertSucceeds(buyer1Db.collection("activation_codes").doc(code).get());

  // Signed-in buyer 1 atomically claims available -> used
  await assertSucceeds(
    buyer1Db.collection("activation_codes").doc(code).update({
      status: "used",
      usedByUid: ADMIN_A,
      usedByEmail: "toko.bu.sari@gmail.com",
      usedByBusinessName: "Lapak Bu Sari",
      registeredDeviceId: "AND-DEVICE11112222",
      usedAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Buyer 2 CANNOT claim an already-used activation code
  await assertFails(
    buyer2Db.collection("activation_codes").doc(code).update({
      status: "used",
      usedByUid: ADMIN_B,
      usedByEmail: "toko.lain@gmail.com",
      usedByBusinessName: "Lapak Lain",
      registeredDeviceId: "AND-DEVICE99998888",
      usedAt: new Date(Date.now() - 100),
      updatedAt: new Date(Date.now() - 100),
    })
  );
});

test("Users profile & Single Device Lock: 1 account = 1 HP, admin reset allows rebind, cross-user denied", async () => {
  const devDb = testEnv
    .authenticatedContext("dev_uid_1", {
      email: "rendykurniawan0311@gmail.com",
      email_verified: true,
    })
    .firestore();
  const userADb = testEnv.authenticatedContext(ADMIN_A).firestore();
  const userBDb = testEnv.authenticatedContext(ADMIN_B).firestore();
  const publicDb = testEnv.unauthenticatedContext().firestore();
  const now = new Date(Date.now() - 2000);

  const profileA = {
    userId: ADMIN_A,
    email: "toko.bu.sari@gmail.com",
    businessName: "Lapak Bu Sari",
    licenseCode: "RAFDEV-8K4M-2P9Q",
    registeredDeviceId: "AND-HP11111111",
    active: true,
    createdAt: now,
    updatedAt: now,
  };

  await assertSucceeds(userADb.collection("users").doc(ADMIN_A).set(profileA));
  await assertSucceeds(userADb.collection("users").doc(ADMIN_A).get());

  // Public and User B cannot read or list users
  await assertFails(publicDb.collection("users").doc(ADMIN_A).get());
  await assertFails(publicDb.collection("users").get());
  await assertFails(userBDb.collection("users").doc(ADMIN_A).get());
  await assertFails(userBDb.collection("users").get());

  // User A CANNOT hijack/change registeredDeviceId to a different HP while already bound
  await assertFails(
    userADb.collection("users").doc(ADMIN_A).update({
      registeredDeviceId: "AND-HP22222222",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // License Admin resets registeredDeviceId to ""
  await assertSucceeds(
    devDb.collection("users").doc(ADMIN_A).update({
      registeredDeviceId: "",
      updatedAt: new Date(Date.now() - 400),
    })
  );

  // Now User A CAN bind the new HP!
  await assertSucceeds(
    userADb.collection("users").doc(ADMIN_A).update({
      registeredDeviceId: "AND-HP22222222",
      updatedAt: new Date(Date.now() - 200),
    })
  );
});

test("User-scoped financial subcollections (users/{uid}/transactions, expenses, settlements) & device_registry", async () => {
  const userADb = testEnv.authenticatedContext(ADMIN_A).firestore();
  const userBDb = testEnv.authenticatedContext(ADMIN_B).firestore();
  const publicDb = testEnv.unauthenticatedContext().firestore();
  const now = new Date(Date.now() - 2000);

  const trxRef = userADb.collection("users").doc(ADMIN_A).collection("transactions").doc("trx_20261126_001");
  const expRef = userADb.collection("users").doc(ADMIN_A).collection("expenses").doc("exp_1");
  const setRef = userADb.collection("users").doc(ADMIN_A).collection("settlements").doc("pay_1");

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

  // Owner can list own subcollections
  await assertSucceeds(userADb.collection("users").doc(ADMIN_A).collection("transactions").get());
  await assertSucceeds(userADb.collection("users").doc(ADMIN_A).collection("expenses").get());
  await assertSucceeds(userADb.collection("users").doc(ADMIN_A).collection("settlements").get());

  // Cross-user and public denied
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
