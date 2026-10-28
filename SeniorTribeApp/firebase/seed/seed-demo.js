/**
 * Tạo dữ liệu demo cho SeniorTribe (chạy trên máy, KHÔNG đưa vào app).
 *
 *   1. Đăng ký trước các tài khoản trong app (email/mật khẩu) để có uid.
 *   2. Firebase Console → Project settings → Service accounts → Generate new private key
 *      → lưu thành serviceAccount.json (KHÔNG commit file này lên GitHub).
 *   3. cp demo-accounts.example.json demo-accounts.json rồi sửa email cho đúng.
 *   4. npm install && node seed-demo.js ./serviceAccount.json ./demo-accounts.json
 *
 * Script dùng Admin SDK nên bỏ qua Security Rules; chạy lại nhiều lần vẫn an toàn
 * (quyền được ghi đè, nhu cầu mẫu chỉ tạo khi chưa có).
 */
const path = require('path');
const admin = require('firebase-admin');

const [serviceAccountPath, configPath] = process.argv.slice(2);
if (!serviceAccountPath || !configPath) {
  console.error('Cách dùng: node seed-demo.js <serviceAccount.json> <demo-accounts.json>');
  process.exit(1);
}

admin.initializeApp({
  credential: admin.credential.cert(require(path.resolve(serviceAccountPath))),
});
const db = admin.firestore();
const { FieldValue } = admin.firestore;
const config = require(path.resolve(configPath));

const SAMPLE_NEEDS = [
  {
    status: 'OPEN',
    title: 'Trò chuyện cuối tuần với cụ bà sống một mình',
    topic: 'Trò chuyện, thăm hỏi',
    content: 'Cụ bà sống một mình, thích nghe cải lương và hay kể chuyện ngày xưa. Mong có người đến trò chuyện vào chiều cuối tuần.',
    area: 'Phường 7, Quận 3',
    desiredTime: 'Chiều thứ 7 · 14:00–16:00',
    source: 'Thăm hộ gia đình ngày 09/10',
  },
  {
    status: 'OPEN',
    title: 'Đi chợ cùng cụ bà mỗi sáng thứ 3',
    topic: 'Nhu yếu phẩm',
    content: 'Cụ bà đi lại chậm, cần người đi cùng ra chợ gần nhà và xách đồ về mỗi tuần một lần.',
    area: 'Phường 9, Quận 3',
    desiredTime: 'Sáng thứ 3 · 7:00–8:30',
    source: 'Tổ dân phố báo ngày 08/10',
  },
  {
    status: 'OPEN',
    title: 'Hướng dẫn dùng điện thoại gọi video cho con',
    topic: 'Hướng dẫn dùng điện thoại',
    content: 'Cụ ông có điện thoại thông minh nhưng chưa biết gọi video cho con đang ở xa. Cần hướng dẫn chậm, ghi lại các bước bằng chữ to.',
    area: 'Phường 7, Quận 3',
    desiredTime: 'Buổi chiều trong tuần',
    source: 'Cụ ông nhờ trực tiếp tại nhà văn hóa',
  },
  {
    status: 'REVIEW',
    title: 'Trò chuyện cuối tuần với cụ ông thích nhạc xưa',
    topic: 'Trò chuyện, thăm hỏi',
    content: 'Cụ ông sống một mình, thích nghe nhạc xưa, mong có người đến trò chuyện vào chiều cuối tuần.',
    area: 'Phường 9, Quận 3',
    desiredTime: 'Chiều thứ 7 hoặc Chủ nhật',
    source: 'Thăm hộ gia đình ngày 10/10',
  },
];

async function main() {
  const orgNames = {};
  for (const org of config.organizations) {
    orgNames[org.id] = org.name;
    await db.doc(`organizations/${org.id}`).set({
      name: org.name,
      area: org.area || '',
      verified: true,
      updatedAt: FieldValue.serverTimestamp(),
    }, { merge: true });
    console.log('✓ Tổ chức', org.id, '-', org.name);
  }

  const members = [];
  for (const m of config.members) {
    let user;
    try {
      user = await admin.auth().getUserByEmail(m.email);
    } catch (e) {
      console.warn('! Bỏ qua', m.email, '- chưa đăng ký tài khoản trong app');
      continue;
    }
    const displayName = user.displayName || m.email.split('@')[0];
    await db.doc(`memberships/${user.uid}/organizations/${m.orgId}`).set({
      orgName: orgNames[m.orgId] || m.orgId,
      roles: m.roles,
      active: true,
      updatedAt: FieldValue.serverTimestamp(),
    });
    await db.doc(`organizations/${m.orgId}/members/${user.uid}`).set({
      displayName,
      roles: m.roles,
      active: true,
      updatedAt: FieldValue.serverTimestamp(),
    });
    members.push({ ...m, uid: user.uid, displayName });
    console.log('✓ Quyền', m.email, '→', m.orgId, m.roles.join(', '));
  }

  // Cờ mạng lưới: Security Rules chỉ cho xem nhu cầu OPEN khi memberships/{uid}.active == true.
  // Khi tắt hết quyền tổ chức của một tài khoản thì đặt lại cờ này thành false.
  for (const uid of new Set(members.map((m) => m.uid))) {
    await db.doc(`memberships/${uid}`).set({
      active: true,
      updatedAt: FieldValue.serverTimestamp(),
    });
  }

  if (!config.createSampleNeeds) return;
  const recorder = members.find((m) => m.roles.includes('RECORDER'));
  if (!recorder) {
    console.warn('! Không có tài khoản RECORDER nên không tạo nhu cầu mẫu');
    return;
  }
  const existing = await db.collection('needs_summary').where('recorderOrgId', '==', recorder.orgId).limit(1).get();
  if (!existing.empty) {
    console.log('• Đơn vị', recorder.orgId, 'đã có nhu cầu, không tạo thêm mẫu');
    return;
  }

  for (const sample of SAMPLE_NEEDS) {
    await db.runTransaction(async (tx) => {
      const counterRef = db.doc('counters/needs');
      const counter = await tx.get(counterRef);
      const number = counter.exists ? counter.get('next') : 1;
      const caseCode = `NC-${String(number).padStart(4, '0')}`;
      const needRef = db.collection('needs_summary').doc();
      tx.set(counterRef, { next: number + 1 });
      tx.set(needRef, {
        caseCode,
        title: sample.title,
        topic: sample.topic,
        content: sample.content,
        area: sample.area,
        desiredTime: sample.desiredTime,
        source: sample.source,
        consentRecorded: true,
        consentMethod: 'Hỏi trực tiếp tại nhà',
        status: sample.status,
        needsRevision: false,
        reviewNote: null,
        recorderOrgId: recorder.orgId,
        recorderOrgName: orgNames[recorder.orgId],
        recorderUid: recorder.uid,
        recorderName: recorder.displayName,
        assignedOrgId: null,
        assignedOrgName: null,
        assigneeUid: null,
        assigneeName: null,
        currentAssignmentId: null,
        returnCount: 0,
        createdAt: FieldValue.serverTimestamp(),
        updatedAt: FieldValue.serverTimestamp(),
        submittedAt: FieldValue.serverTimestamp(),
      });
      // Thông tin riêng là dữ liệu giả để demo
      tx.set(db.doc(`needs_private/${needRef.id}`), {
        elderlyName: 'Người cao tuổi (dữ liệu mẫu)',
        contact: 'Liên hệ qua đầu mối',
        address: '',
        recorderOrgId: recorder.orgId,
        updatedAt: FieldValue.serverTimestamp(),
      });
      console.log('✓ Nhu cầu mẫu', caseCode, sample.status, '-', sample.title);
    });
  }
}

main()
  .then(() => { console.log('Xong.'); process.exit(0); })
  .catch((e) => { console.error(e); process.exit(1); });
