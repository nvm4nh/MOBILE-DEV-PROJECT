// Kiểm thử Security Rules cho E09 · SOS (needs_assignments/{id}/sos/{alertId}):
//   - chỉ hai người trong luồng chat gửi / đọc báo khẩn, khi lần tiếp nhận còn hiệu lực
//   - người còn lại xác nhận đã nhận; một trong hai người ghi đã xử lý xong
//   - không sửa nội dung, không xóa; vị trí phải đúng kiểu tọa độ
// Chạy: npm test (Firebase Emulator, không đụng tới project thật).

import { readFileSync } from 'node:fs';
import { after, before, beforeEach, describe, test } from 'node:test';
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from '@firebase/rules-unit-testing';
import firebase from 'firebase/compat/app';
import 'firebase/compat/firestore';

const { Timestamp, FieldValue, GeoPoint } = firebase.firestore;

const REC_ORG = 'org-hoi-ctd-q3';
const ORG = 'org-doan-tn-p7';
const OTHER_ORG = 'org-khac';

const RECORDER = 'u-recorder';
const RECORDER2 = 'u-recorder-2';
const REP = 'u-rep';
const ASSIGNEE = 'u-assignee';
const OUTSIDER = 'u-outsider';

const NEED = 'need-1';
const ASSIGN = 'assign-1';
const base = `needs_assignments/${ASSIGN}/sos`;

let env;

function db(uid) {
  return env.authenticatedContext(uid).firestore();
}

async function seed({ needStatus = 'SCHEDULED', assignmentStatus = 'ACTIVE', alerts = {} } = {}) {
  await env.withSecurityRulesDisabled(async (ctx) => {
    const d = ctx.firestore();
    const member = (uid, orgId, roles) =>
      d.doc(`memberships/${uid}/organizations/${orgId}`).set({ roles, active: true, orgId });
    await member(RECORDER, REC_ORG, ['RECORDER']);
    await member(RECORDER2, REC_ORG, ['RECORDER']);
    await member(REP, ORG, ['REPRESENTATIVE']);
    await member(ASSIGNEE, ORG, []);
    await member(OUTSIDER, OTHER_ORG, ['REPRESENTATIVE']);
    await d.doc(`needs_summary/${NEED}`).set({
      caseCode: 'NC-0142', status: needStatus, recorderOrgId: REC_ORG, recorderUid: RECORDER,
      assignedOrgId: ORG, assigneeUid: ASSIGNEE, currentAssignmentId: ASSIGN, updatedAt: Timestamp.now(),
    });
    await d.doc(`needs_assignments/${ASSIGN}`).set({
      needId: NEED, recorderOrgId: REC_ORG, orgId: ORG, assigneeUid: ASSIGNEE, acceptedByUid: REP,
      status: assignmentStatus,
    });
    for (const [id, data] of Object.entries(alerts)) await d.doc(`${base}/${id}`).set(data);
  });
}

function alert(uid, overrides = {}) {
  return {
    byUid: uid, byName: 'Người báo', kind: 'MEDICAL', note: 'Cụ khó thở, đã gọi 115',
    status: 'OPEN', createdAt: FieldValue.serverTimestamp(), ...overrides,
  };
}

const openAlert = { a1: { ...alert(ASSIGNEE), createdAt: Timestamp.now() } };
const ack = (uid) => ({
  status: 'ACK', ackByUid: uid, ackByName: 'X', ackAt: FieldValue.serverTimestamp(),
});
const resolve = (uid, note = 'Cụ đã được đưa đi viện, con gái đi cùng') => ({
  status: 'RESOLVED', resolvedByUid: uid, resolvedByName: 'X', resolvedAt: FieldValue.serverTimestamp(),
  resolveNote: note,
});

before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-seniortribe',
    firestore: { rules: readFileSync(new URL('../firestore.rules', import.meta.url), 'utf8') },
  });
});

after(async () => {
  await env.cleanup();
});

beforeEach(async () => {
  await env.clearFirestore();
});

describe('E09 · Gửi báo khẩn', () => {
  test('người được giao và đầu mối gửi được, kèm số đã gọi và vị trí', async () => {
    await seed();
    await assertSucceeds(db(ASSIGNEE).collection(base).add(alert(ASSIGNEE, {
      calledNumber: '115', location: new GeoPoint(10.78, 106.69), accuracyM: 12.5,
    })));
    await assertSucceeds(db(RECORDER).collection(base).add(alert(RECORDER, { kind: 'SAFETY', note: '' })));
  });

  test('người ngoài luồng chat không gửi, không đọc được', async () => {
    await seed({ alerts: openAlert });
    await assertFails(db(REP).collection(base).add(alert(REP)));
    await assertFails(db(RECORDER2).collection(base).add(alert(RECORDER2)));
    await assertFails(db(OUTSIDER).collection(base).add(alert(OUTSIDER)));
    await assertFails(db(REP).doc(`${base}/a1`).get());
    await assertFails(db(OUTSIDER).collection(base).get());
    await assertSucceeds(db(RECORDER).collection(base).get());
  });

  test('không giả danh người báo, không tự đặt trạng thái / thời gian', async () => {
    await seed();
    await assertFails(db(ASSIGNEE).collection(base).add(alert(RECORDER)));
    await assertFails(db(ASSIGNEE).collection(base).add(alert(ASSIGNEE, { status: 'RESOLVED' })));
    await assertFails(db(ASSIGNEE).collection(base).add(alert(ASSIGNEE, { createdAt: Timestamp.fromMillis(0) })));
  });

  test('loại báo khẩn, số gọi và vị trí phải hợp lệ', async () => {
    await seed();
    await assertFails(db(ASSIGNEE).collection(base).add(alert(ASSIGNEE, { kind: 'RESCUE_GUARANTEED' })));
    await assertFails(db(ASSIGNEE).collection(base).add(alert(ASSIGNEE, { calledNumber: '0909123456' })));
    await assertFails(db(ASSIGNEE).collection(base).add(alert(ASSIGNEE, { location: '10.78,106.69' })));
    await assertFails(db(ASSIGNEE).collection(base).add(alert(ASSIGNEE, { note: 'x'.repeat(301) })));
    await assertFails(db(ASSIGNEE).collection(base).add(alert(ASSIGNEE, { address: '12 Lê Lợi' })));
  });

  test('lần tiếp nhận đã kết thúc thì không gửi được nữa', async () => {
    await seed({ needStatus: 'COMPLETED', assignmentStatus: 'CLOSED' });
    await assertFails(db(ASSIGNEE).collection(base).add(alert(ASSIGNEE)));
  });
});

describe('E09 · Xác nhận và xử lý xong', () => {
  test('người còn lại xác nhận đã nhận; người báo không tự xác nhận', async () => {
    await seed({ alerts: openAlert });
    await assertFails(db(ASSIGNEE).doc(`${base}/a1`).update(ack(ASSIGNEE)));
    await assertFails(db(REP).doc(`${base}/a1`).update(ack(REP)));
    await assertSucceeds(db(RECORDER).doc(`${base}/a1`).update(ack(RECORDER)));
  });

  test('một trong hai người ghi đã xử lý xong', async () => {
    await seed({ alerts: openAlert });
    await assertSucceeds(db(ASSIGNEE).doc(`${base}/a1`).update(resolve(ASSIGNEE)));
  });

  test('đã xử lý xong thì không mở lại / xác nhận lại', async () => {
    await seed({ alerts: { a1: { ...openAlert.a1, status: 'RESOLVED' } } });
    await assertFails(db(RECORDER).doc(`${base}/a1`).update(ack(RECORDER)));
    await assertFails(db(RECORDER).doc(`${base}/a1`).update({ status: 'OPEN' }));
  });

  test('không sửa nội dung báo khẩn, không xóa', async () => {
    await seed({ alerts: openAlert });
    await assertFails(db(ASSIGNEE).doc(`${base}/a1`).update({ note: 'Đã sửa' }));
    await assertFails(db(RECORDER).doc(`${base}/a1`).update({ ...ack(RECORDER), note: 'Đã sửa' }));
    await assertFails(db(ASSIGNEE).doc(`${base}/a1`).delete());
  });

  test('ghi chú xử lý tối đa 300 ký tự', async () => {
    await seed({ alerts: openAlert });
    await assertFails(db(RECORDER).doc(`${base}/a1`).update(resolve(RECORDER, 'x'.repeat(301))));
  });
});
