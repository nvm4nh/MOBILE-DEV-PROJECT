// Kiểm thử Security Rules cho các lỗ hổng đã vá ở phần nhu cầu:
//   · events chỉ do đơn vị ghi nhận / tổ chức phụ trách ghi, khớp trạng thái nhu cầu
//   · counters/needs chỉ tăng khi đầu mối tạo nhu cầu thật (F06)
//   · kết thúc lần tiếp nhận phải đi cùng thay đổi nhu cầu, có lý do
//   · trả lại bắt buộc lý do (F14)
//   · người được giao phải là thành viên đang hoạt động (F10)
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

const { FieldValue } = firebase.firestore;

const REC_ORG = 'org-hoi-ctd-q3';   // đơn vị ghi nhận
const ORG = 'org-doan-tn-p7';       // tổ chức phụ trách
const OTHER_ORG = 'org-khac';

const RECORDER = 'u-recorder';
const REP = 'u-rep';
const ASSIGNEE = 'u-assignee';
const INACTIVE = 'u-inactive';      // thành viên tổ chức phụ trách đã bị tắt quyền
const OUTSIDER = 'u-outsider';      // đại diện tổ chức khác

const NEED = 'need-1';
const ASSIGN = 'assign-1';

let env;

function db(uid) {
  return env.authenticatedContext(uid).firestore();
}

async function seed({ needStatus = 'ACCEPTED', counter = 5 } = {}) {
  await env.withSecurityRulesDisabled(async (ctx) => {
    const d = ctx.firestore();
    const member = (uid, orgId, roles, active = true) =>
      d.doc(`memberships/${uid}/organizations/${orgId}`).set({ roles, active, orgId });
    await member(RECORDER, REC_ORG, ['RECORDER']);
    await member(REP, ORG, ['REPRESENTATIVE']);
    await member(ASSIGNEE, ORG, []);
    await member(INACTIVE, ORG, [], false);
    await member(OUTSIDER, OTHER_ORG, ['REPRESENTATIVE']);

    const open = needStatus === 'OPEN';
    await d.doc(`needs_summary/${NEED}`).set({
      caseCode: 'NC-0142',
      title: 'Trò chuyện cuối tuần với cụ bà sống một mình',
      status: needStatus,
      recorderOrgId: REC_ORG,
      recorderUid: RECORDER,
      assignedOrgId: open ? null : ORG,
      assigneeUid: open ? null : ASSIGNEE,
      currentAssignmentId: open ? null : ASSIGN,
      returnCount: 0,
    });
    if (!open) {
      await d.doc(`needs_assignments/${ASSIGN}`).set({
        needId: NEED, recorderOrgId: REC_ORG, orgId: ORG,
        assigneeUid: ASSIGNEE, acceptedByUid: REP, status: 'ACTIVE',
      });
    }
    if (counter) await d.doc('counters/needs').set({ next: counter });
  });
}

function event(uid, orgId, toStatus, overrides = {}) {
  return {
    type: 'NOTE', fromStatus: toStatus, toStatus,
    byUid: uid, byName: 'Người ghi', byOrgId: orgId, note: null,
    at: FieldValue.serverTimestamp(),
    ...overrides,
  };
}

function newNeed(d, id) {
  return {
    caseCode: 'NC-0005',
    title: 'Hỗ trợ đi khám định kỳ',
    topic: 'Sức khỏe',
    content: 'Cụ ông cần người đưa đi khám tại trạm y tế phường.',
    area: 'Phường 7, Quận 3',
    desiredTime: 'Sáng thứ 7',
    source: 'Tổ dân phố 4',
    consentRecorded: true,
    consentMethod: 'Gọi điện cho con trai',
    status: 'REVIEW',
    needsRevision: false,
    reviewNote: null,
    recorderOrgId: REC_ORG,
    recorderOrgName: 'Hội CTĐ Quận 3',
    recorderUid: RECORDER,
    recorderName: 'Trần Thị Mai',
    assignedOrgId: null,
    assignedOrgName: null,
    assigneeUid: null,
    assigneeName: null,
    currentAssignmentId: null,
    returnCount: 0,
    createdAt: FieldValue.serverTimestamp(),
    updatedAt: FieldValue.serverTimestamp(),
    submittedAt: FieldValue.serverTimestamp(),
  };
}

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

describe('Xem nhu cầu OPEN trong mạng lưới (R02)', () => {
  const NO_ORG = 'u-no-org';
  const LEFT = 'u-left';            // từng có quyền nhưng đã bị tắt cờ mạng lưới

  async function seedNetwork() {
    await seed({ needStatus: 'OPEN' });
    await env.withSecurityRulesDisabled(async (ctx) => {
      const d = ctx.firestore();
      await d.doc(`memberships/${OUTSIDER}`).set({ active: true });
      await d.doc(`memberships/${LEFT}`).set({ active: false });
    });
  }

  test('thành viên mạng lưới (tổ chức khác) xem được nhu cầu OPEN', async () => {
    await seedNetwork();
    await assertSucceeds(db(OUTSIDER).doc(`needs_summary/${NEED}`).get());
  });

  test('tài khoản chưa có quyền tổ chức không xem được nhu cầu OPEN', async () => {
    await seedNetwork();
    await assertFails(db(NO_ORG).doc(`needs_summary/${NEED}`).get());
  });

  test('tài khoản đã bị tắt cờ mạng lưới không xem được', async () => {
    await seedNetwork();
    await assertFails(db(LEFT).doc(`needs_summary/${NEED}`).get());
  });

  test('đơn vị ghi nhận vẫn xem được nhu cầu của mình mà không cần cờ', async () => {
    await seedNetwork();
    await assertSucceeds(db(RECORDER).doc(`needs_summary/${NEED}`).get());
  });

  test('chỉ chủ tài khoản đọc được cờ, không ai tự ghi', async () => {
    await seedNetwork();
    await assertSucceeds(db(OUTSIDER).doc(`memberships/${OUTSIDER}`).get());
    await assertFails(db(NO_ORG).doc(`memberships/${OUTSIDER}`).get());
    await assertFails(db(NO_ORG).doc(`memberships/${NO_ORG}`).set({ active: true }));
  });
});

describe('Lịch sử nhu cầu (events)', () => {
  const events = (uid) => db(uid).collection(`needs_summary/${NEED}/events`);

  test('đơn vị ghi nhận và tổ chức phụ trách ghi được sự kiện', async () => {
    await seed();
    await assertSucceeds(events(RECORDER).add(event(RECORDER, REC_ORG, 'ACCEPTED')));
    await assertSucceeds(events(REP).add(event(REP, ORG, 'ACCEPTED')));
  });

  test('tổ chức không liên quan không ghi được sự kiện giả', async () => {
    await seed();
    await assertFails(events(OUTSIDER).add(event(OUTSIDER, OTHER_ORG, 'ACCEPTED')));
  });

  test('toStatus phải khớp trạng thái hiện tại của nhu cầu', async () => {
    await seed();
    await assertFails(events(RECORDER).add(event(RECORDER, REC_ORG, 'COMPLETED')));
  });

  test('không chèn thêm field', async () => {
    await seed();
    await assertFails(events(RECORDER).add(event(RECORDER, REC_ORG, 'ACCEPTED', { official: true })));
  });

  test('tổ chức vừa trả lại vẫn ghi được sự kiện RETURNED trong cùng transaction', async () => {
    await seed();
    const d = db(REP);
    const b = d.batch();
    b.update(d.doc(`needs_summary/${NEED}`), {
      status: 'OPEN', assignedOrgId: null, assigneeUid: null, currentAssignmentId: null,
      returnCount: FieldValue.increment(1), lastReturnReason: 'Không đủ người tham gia',
      updatedAt: FieldValue.serverTimestamp(),
    });
    b.update(d.doc(`needs_assignments/${ASSIGN}`), {
      status: 'RETURNED', endReason: 'Không đủ người tham gia', endNote: null,
      endedByUid: REP, endedAt: FieldValue.serverTimestamp(),
    });
    b.set(d.collection(`needs_summary/${NEED}/events`).doc(),
      event(REP, ORG, 'OPEN', { type: 'RETURNED', fromStatus: 'ACCEPTED' }));
    await assertSucceeds(b.commit());
  });
});

describe('Bộ đếm mã nhu cầu (counters/needs)', () => {
  test('đầu mối tạo nhu cầu kèm tăng bộ đếm trong cùng transaction', async () => {
    await seed();
    const d = db(RECORDER);
    const ref = d.collection('needs_summary').doc();
    const b = d.batch();
    b.set(d.doc('counters/needs'), { next: 6, lastNeedId: ref.id });
    b.set(ref, newNeed(d, ref.id));
    await assertSucceeds(b.commit());
  });

  test('tài khoản bất kỳ không tự tăng bộ đếm', async () => {
    await seed();
    await assertFails(db(OUTSIDER).doc('counters/needs').set({ next: 6 }));
    await assertFails(db(OUTSIDER).doc('counters/needs').set({ next: 6, lastNeedId: NEED }));
  });

  test('không tạo nhu cầu mà bỏ qua bộ đếm', async () => {
    await seed();
    const d = db(RECORDER);
    const ref = d.collection('needs_summary').doc();
    await assertFails(ref.set(newNeed(d, ref.id)));
  });
});

describe('Kết thúc lần tiếp nhận', () => {
  const end = (uid, status, overrides = {}) => db(uid).doc(`needs_assignments/${ASSIGN}`).update({
    status, endReason: 'Không đủ người tham gia', endNote: null,
    endedByUid: uid, endedAt: FieldValue.serverTimestamp(), ...overrides,
  });

  test('đại diện không đánh dấu RETURNED khi nhu cầu vẫn đang trỏ tới lần tiếp nhận', async () => {
    await seed();
    await assertFails(end(REP, 'RETURNED'));
  });

  test('đầu mối không đóng lần tiếp nhận khi nhu cầu chưa hủy / hoàn thành', async () => {
    await seed();
    await assertFails(end(RECORDER, 'CLOSED'));
  });

  test('đầu mối hủy nhu cầu và đóng lần tiếp nhận trong cùng transaction', async () => {
    await seed();
    const d = db(RECORDER);
    const b = d.batch();
    b.update(d.doc(`needs_summary/${NEED}`), {
      status: 'CANCELLED', cancelReason: 'Gia đình đã tự lo', cancelNote: null,
      cancelledByUid: RECORDER, cancelledAt: FieldValue.serverTimestamp(),
      updatedAt: FieldValue.serverTimestamp(),
    });
    b.update(d.doc(`needs_assignments/${ASSIGN}`), {
      status: 'CLOSED', endReason: 'Đầu mối hủy nhu cầu: Gia đình đã tự lo', endNote: null,
      endedByUid: RECORDER, endedAt: FieldValue.serverTimestamp(),
    });
    await assertSucceeds(b.commit());
  });

  test('lý do kết thúc không được rỗng', async () => {
    await seed();
    const d = db(RECORDER);
    const b = d.batch();
    b.update(d.doc(`needs_summary/${NEED}`), {
      status: 'CANCELLED', cancelReason: 'Gia đình đã tự lo', cancelNote: null,
      cancelledByUid: RECORDER, cancelledAt: FieldValue.serverTimestamp(),
      updatedAt: FieldValue.serverTimestamp(),
    });
    b.update(d.doc(`needs_assignments/${ASSIGN}`), {
      status: 'CLOSED', endReason: '', endNote: null,
      endedByUid: RECORDER, endedAt: FieldValue.serverTimestamp(),
    });
    await assertFails(b.commit());
  });
});

describe('Trả lại nhu cầu (F14)', () => {
  const returnUpdate = (overrides = {}) => ({
    status: 'OPEN', assignedOrgId: null, assignedOrgName: null, assigneeUid: null,
    assigneeName: null, acceptedByUid: null, currentAssignmentId: null, acceptedAt: null,
    returnCount: FieldValue.increment(1), lastReturnReason: 'Không đủ người tham gia',
    updatedAt: FieldValue.serverTimestamp(),
    ...overrides,
  });

  test('trả lại có lý do thì được', async () => {
    await seed();
    await assertSucceeds(db(REP).doc(`needs_summary/${NEED}`).update(returnUpdate()));
  });

  test('trả lại thiếu lý do hoặc lý do rỗng thì bị chặn', async () => {
    await seed();
    const { lastReturnReason, ...noReason } = returnUpdate();
    await assertFails(db(REP).doc(`needs_summary/${NEED}`).update(noReason));
    await assertFails(db(REP).doc(`needs_summary/${NEED}`).update(returnUpdate({ lastReturnReason: '' })));
  });
});

describe('Tiếp nhận nhu cầu (F10)', () => {
  function accept(assigneeUid) {
    const d = db(REP);
    const b = d.batch();
    b.update(d.doc(`needs_summary/${NEED}`), {
      status: 'ACCEPTED', assignedOrgId: ORG, assignedOrgName: 'Đoàn TN Phường 7',
      assigneeUid, assigneeName: 'Người phụ trách', acceptedByUid: REP,
      currentAssignmentId: ASSIGN, acceptedAt: FieldValue.serverTimestamp(),
      updatedAt: FieldValue.serverTimestamp(),
    });
    b.set(d.doc(`needs_assignments/${ASSIGN}`), {
      needId: NEED, recorderOrgId: REC_ORG, orgId: ORG, assigneeUid,
      acceptedByUid: REP, status: 'ACTIVE',
    });
    return b.commit();
  }

  test('giao cho thành viên đang hoạt động thì được', async () => {
    await seed({ needStatus: 'OPEN' });
    await assertSucceeds(accept(ASSIGNEE));
  });

  test('không giao cho thành viên đã bị tắt quyền', async () => {
    await seed({ needStatus: 'OPEN' });
    await assertFails(accept(INACTIVE));
  });

  test('không giao cho người ngoài tổ chức', async () => {
    await seed({ needStatus: 'OPEN' });
    await assertFails(accept(OUTSIDER));
  });
});
