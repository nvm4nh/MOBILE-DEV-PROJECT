// Kiểm thử Security Rules cho E02 · Đổi lịch sau khi đã chốt:
//   - bên nào được gửi yêu cầu đổi lịch, đồng ý của người cao tuổi đi kèm ở đâu
//   - chỉ bên còn lại xác nhận / từ chối; bên gửi rút lại được yêu cầu của mình
//   - xác nhận phải đổi đồng thời: yêu cầu → CONFIRMED, lịch cũ → RESCHEDULED, nhu cầu trỏ lịch mới
//   - lịch cũ không bị xóa hay sửa tùy ý (giữ lịch sử)
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

const { Timestamp, FieldValue } = firebase.firestore;

const REC_ORG = 'org-hoi-ctd-q3';
const ORG = 'org-doan-tn-p7';
const OTHER_ORG = 'org-khac';

const RECORDER = 'u-recorder';
const REP = 'u-rep';
const ASSIGNEE = 'u-assignee';
const MEMBER = 'u-member';          // thành viên tổ chức phụ trách, không phải đại diện / người được giao
const OUTSIDER = 'u-outsider';

const NEED = 'need-1';
const ASSIGN = 'assign-1';
const CURRENT = 'c1';                // lịch đang hẹn

const hours = (h) => h * 60 * 60 * 1000;
const at = (ms) => Timestamp.fromMillis(Date.now() + ms);
const C_START = Timestamp.fromMillis(Date.now() + hours(24));
const C_END = Timestamp.fromMillis(Date.now() + hours(26));
const N_START = Timestamp.fromMillis(Date.now() + hours(48));
const N_END = Timestamp.fromMillis(Date.now() + hours(50));

let env;

function db(uid) {
  return env.authenticatedContext(uid).firestore();
}

const schedulePath = (id) => `needs_assignments/${ASSIGN}/schedules/${id}`;

async function seed({ needStatus = 'SCHEDULED', extraSchedules = {}, rescheduleCount } = {}) {
  await env.withSecurityRulesDisabled(async (ctx) => {
    const d = ctx.firestore();
    const member = (uid, orgId, roles) =>
      d.doc(`memberships/${uid}/organizations/${orgId}`).set({ roles, active: true, orgId });
    await member(RECORDER, REC_ORG, ['RECORDER']);
    await member(REP, ORG, ['REPRESENTATIVE']);
    await member(ASSIGNEE, ORG, []);
    await member(MEMBER, ORG, []);
    await member(OUTSIDER, OTHER_ORG, ['REPRESENTATIVE']);

    const need = {
      caseCode: 'NC-0142',
      title: 'Trò chuyện cuối tuần với cụ bà sống một mình',
      area: 'Phường 7, Quận 3',
      status: needStatus,
      recorderOrgId: REC_ORG,
      recorderUid: RECORDER,
      assignedOrgId: ORG,
      assigneeUid: ASSIGNEE,
      currentAssignmentId: ASSIGN,
      scheduleId: CURRENT,
      scheduledAt: C_START,
      scheduledEndAt: C_END,
      scheduledFormat: 'Đến nhà · 2 đoàn viên',
      scheduledPlace: 'Phường 7, Quận 3',
      scheduledByUid: RECORDER,
      updatedAt: Timestamp.now(),
    };
    if (rescheduleCount !== undefined) need.rescheduleCount = rescheduleCount;
    await d.doc(`needs_summary/${NEED}`).set(need);
    await d.doc(`needs_assignments/${ASSIGN}`).set({
      needId: NEED, caseCode: 'NC-0142', recorderOrgId: REC_ORG, orgId: ORG,
      assigneeUid: ASSIGNEE, acceptedByUid: REP, status: 'ACTIVE',
    });
    await d.doc(schedulePath(CURRENT)).set({
      startAt: C_START, endAt: C_END, format: 'Đến nhà · 2 đoàn viên', place: 'Phường 7, Quận 3',
      status: 'CONFIRMED', proposedByUid: REP, proposedByName: 'Đại diện', createdAt: Timestamp.now(),
      elderConsent: true, consentMethod: 'Gọi qua con gái', decidedByUid: RECORDER, decidedAt: Timestamp.now(),
    });
    for (const [id, data] of Object.entries(extraSchedules)) {
      await d.doc(schedulePath(id)).set(data);
    }
  });
}

/** Yêu cầu đổi lịch như CoordinationRepository.proposeReschedule ghi. */
function request(side, uid, overrides = {}) {
  const base = {
    startAt: at(hours(48)),
    endAt: at(hours(50)),
    format: 'Đến nhà · 2 đoàn viên',
    place: 'Phường 7, Quận 3',
    status: 'PROPOSED',
    proposedByUid: uid,
    proposedByName: 'Người gửi',
    createdAt: FieldValue.serverTimestamp(),
    kind: 'RESCHEDULE',
    replacesScheduleId: CURRENT,
    proposedBySide: side,
    reason: 'Thứ 7 đoàn có lịch trực',
  };
  if (side === 'RECORDER') {
    base.elderConsent = true;
    base.consentMethod = 'Gọi điện cho cụ · 18/10';
  }
  return { ...base, ...overrides };
}

/** Yêu cầu đổi lịch đang chờ đã có sẵn trong dữ liệu. */
function pending(side, uid) {
  const r = request(side, uid, { startAt: N_START, endAt: N_END, createdAt: Timestamp.now() });
  return { r1: r };
}

/** Xác nhận như CoordinationRepository.confirmReschedule (batch thay cho transaction). */
function confirmBatch(d, uid, { decided = {}, superseded = {}, need = {}, skipSuperseded = false, skipNeed = false } = {}) {
  const b = d.batch();
  b.update(d.doc(schedulePath('r1')), {
    status: 'CONFIRMED', decidedByUid: uid, decidedByName: 'Người xác nhận',
    decidedAt: FieldValue.serverTimestamp(), ...decided,
  });
  if (!skipSuperseded) {
    b.update(d.doc(schedulePath(CURRENT)), {
      status: 'RESCHEDULED', replacedById: 'r1', rescheduledAt: FieldValue.serverTimestamp(), ...superseded,
    });
  }
  if (!skipNeed) {
    b.update(d.doc(`needs_summary/${NEED}`), {
      scheduleId: 'r1', scheduledAt: N_START, scheduledEndAt: N_END,
      scheduledFormat: 'Đến nhà · 2 đoàn viên', scheduledPlace: 'Phường 7, Quận 3',
      scheduledByUid: uid, rescheduleCount: FieldValue.increment(1),
      rescheduledAt: FieldValue.serverTimestamp(), updatedAt: FieldValue.serverTimestamp(), ...need,
    });
  }
  return b.commit();
}

const recorderConsent = { elderConsent: true, consentMethod: 'Gọi qua con gái · 18/10' };

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

describe('E02 · Gửi yêu cầu đổi lịch', () => {
  const schedules = (uid) => db(uid).collection(`needs_assignments/${ASSIGN}/schedules`);

  test('đại diện và người được giao của tổ chức phụ trách gửi được', async () => {
    await seed();
    await assertSucceeds(schedules(REP).add(request('ORG', REP)));
    await assertSucceeds(schedules(ASSIGNEE).add(request('ORG', ASSIGNEE)));
  });

  test('tổ chức không được tự ghi "cụ đã đồng ý" khi gửi', async () => {
    await seed();
    await assertFails(schedules(REP).add(request('ORG', REP, recorderConsent)));
  });

  test('đầu mối gửi được khi đã ghi đồng ý của cụ và cách hỏi', async () => {
    await seed();
    await assertSucceeds(schedules(RECORDER).add(request('RECORDER', RECORDER)));
  });

  test('đầu mối gửi mà thiếu đồng ý của cụ thì bị từ chối', async () => {
    await seed();
    const noConsent = request('RECORDER', RECORDER);
    delete noConsent.elderConsent;
    await assertFails(schedules(RECORDER).add(noConsent));
    await assertFails(schedules(RECORDER).add(request('RECORDER', RECORDER, { consentMethod: '' })));
  });

  test('không gửi nhầm bên (đầu mối khai là tổ chức và ngược lại)', async () => {
    await seed();
    await assertFails(schedules(RECORDER).add(request('ORG', RECORDER)));
    await assertFails(schedules(REP).add(request('RECORDER', REP)));
  });

  test('thành viên thường của tổ chức và tổ chức khác không gửi được', async () => {
    await seed();
    await assertFails(schedules(MEMBER).add(request('ORG', MEMBER)));
    await assertFails(schedules(OUTSIDER).add(request('ORG', OUTSIDER)));
  });

  test('bắt buộc có lý do', async () => {
    await seed();
    await assertFails(schedules(REP).add(request('ORG', REP, { reason: '' })));
    const noReason = request('ORG', REP);
    delete noReason.reason;
    await assertFails(schedules(REP).add(noReason));
  });

  test('phải thay đúng lịch đang hẹn', async () => {
    await seed();
    await assertFails(schedules(REP).add(request('ORG', REP, { replacesScheduleId: 'khac' })));
  });

  test('chỉ đổi lịch khi nhu cầu đang SCHEDULED', async () => {
    await seed({ needStatus: 'ACCEPTED' });
    await assertFails(schedules(REP).add(request('ORG', REP)));
    await seed({ needStatus: 'DELIVERED' });
    await assertFails(schedules(REP).add(request('ORG', REP)));
  });

  test('giờ mới vẫn phải hợp lệ (tương lai, tối đa 12 tiếng)', async () => {
    await seed();
    await assertFails(schedules(REP).add(request('ORG', REP, { startAt: at(-hours(2)), endAt: at(-hours(1)) })));
    await assertFails(schedules(REP).add(request('ORG', REP, { endAt: at(hours(48 + 13)) })));
  });
});

describe('E02 · Xác nhận đổi lịch', () => {
  test('đầu mối hỏi ý kiến cụ rồi xác nhận yêu cầu của tổ chức', async () => {
    await seed({ extraSchedules: pending('ORG', REP) });
    await assertSucceeds(confirmBatch(db(RECORDER), RECORDER, { decided: recorderConsent }));
  });

  test('đầu mối xác nhận mà chưa ghi đồng ý của cụ thì bị từ chối', async () => {
    await seed({ extraSchedules: pending('ORG', REP) });
    await assertFails(confirmBatch(db(RECORDER), RECORDER));
    await assertFails(confirmBatch(db(RECORDER), RECORDER,
      { decided: { elderConsent: true, consentMethod: '' } }));
  });

  test('tổ chức xác nhận yêu cầu do đầu mối gửi (đồng ý của cụ đã có sẵn)', async () => {
    await seed({ extraSchedules: pending('RECORDER', RECORDER) });
    await assertSucceeds(confirmBatch(db(ASSIGNEE), ASSIGNEE));
  });

  test('bên gửi không tự xác nhận yêu cầu của mình', async () => {
    await seed({ extraSchedules: pending('ORG', REP) });
    await assertFails(confirmBatch(db(REP), REP));
    await seed({ extraSchedules: pending('RECORDER', RECORDER) });
    await assertFails(confirmBatch(db(RECORDER), RECORDER, { decided: recorderConsent }));
  });

  test('tổ chức khác không xác nhận được', async () => {
    await seed({ extraSchedules: pending('RECORDER', RECORDER) });
    await assertFails(confirmBatch(db(OUTSIDER), OUTSIDER));
  });

  test('phải chuyển lịch cũ sang RESCHEDULED trong cùng lần ghi', async () => {
    await seed({ extraSchedules: pending('RECORDER', RECORDER) });
    await assertFails(confirmBatch(db(ASSIGNEE), ASSIGNEE, { skipSuperseded: true }));
  });

  test('phải cập nhật nhu cầu sang lịch mới trong cùng lần ghi', async () => {
    await seed({ extraSchedules: pending('RECORDER', RECORDER) });
    await assertFails(confirmBatch(db(ASSIGNEE), ASSIGNEE, { skipNeed: true }));
  });

  test('giờ trên nhu cầu phải khớp yêu cầu đã xác nhận', async () => {
    await seed({ extraSchedules: pending('RECORDER', RECORDER) });
    await assertFails(confirmBatch(db(ASSIGNEE), ASSIGNEE,
      { need: { scheduledEndAt: Timestamp.fromMillis(N_END.toMillis() + hours(1)) } }));
    await assertFails(confirmBatch(db(ASSIGNEE), ASSIGNEE, { need: { scheduledFormat: 'Gọi video' } }));
  });

  test('số lần đổi lịch phải tăng đúng 1', async () => {
    await seed({ extraSchedules: pending('RECORDER', RECORDER), rescheduleCount: 2 });
    await assertFails(confirmBatch(db(ASSIGNEE), ASSIGNEE, { need: { rescheduleCount: 7 } }));
    await assertSucceeds(confirmBatch(db(ASSIGNEE), ASSIGNEE));
  });

  test('lịch cũ phải trỏ đúng sang lịch mới', async () => {
    await seed({ extraSchedules: pending('RECORDER', RECORDER) });
    await assertFails(confirmBatch(db(ASSIGNEE), ASSIGNEE, { superseded: { replacedById: 'khac' } }));
  });

  test('không đổi trạng thái nhu cầu khi đổi lịch (vẫn SCHEDULED)', async () => {
    await seed({ extraSchedules: pending('RECORDER', RECORDER) });
    await assertFails(confirmBatch(db(ASSIGNEE), ASSIGNEE, { need: { status: 'DELIVERED' } }));
  });

  test('không tự đánh dấu lịch đang hẹn là RESCHEDULED khi không có lịch mới', async () => {
    await seed();
    await assertFails(db(REP).doc(schedulePath(CURRENT)).update({
      status: 'RESCHEDULED', replacedById: 'r1', rescheduledAt: FieldValue.serverTimestamp(),
    }));
  });

  test('lịch cũ không bị xóa', async () => {
    await seed();
    await assertFails(db(RECORDER).doc(schedulePath(CURRENT)).delete());
  });
});

describe('E02 · Từ chối và rút lại yêu cầu', () => {
  const decline = (uid, reason = 'Cụ có hẹn tái khám') => ({
    status: 'DECLINED', declineReason: reason, decidedByUid: uid, decidedByName: 'X',
    decidedAt: FieldValue.serverTimestamp(),
  });
  const withdraw = (uid) => ({
    status: 'WITHDRAWN', decidedByUid: uid, decidedByName: 'X', decidedAt: FieldValue.serverTimestamp(),
  });

  test('đầu mối từ chối yêu cầu của tổ chức kèm lý do', async () => {
    await seed({ extraSchedules: pending('ORG', REP) });
    await assertSucceeds(db(RECORDER).doc(schedulePath('r1')).update(decline(RECORDER)));
  });

  test('tổ chức từ chối yêu cầu của đầu mối kèm lý do', async () => {
    await seed({ extraSchedules: pending('RECORDER', RECORDER) });
    await assertSucceeds(db(ASSIGNEE).doc(schedulePath('r1')).update(decline(ASSIGNEE)));
  });

  test('từ chối không có lý do thì bị từ chối', async () => {
    await seed({ extraSchedules: pending('RECORDER', RECORDER) });
    await assertFails(db(ASSIGNEE).doc(schedulePath('r1')).update(decline(ASSIGNEE, '')));
  });

  test('bên gửi không "từ chối" yêu cầu của chính mình', async () => {
    await seed({ extraSchedules: pending('RECORDER', RECORDER) });
    await assertFails(db(RECORDER).doc(schedulePath('r1')).update(decline(RECORDER)));
  });

  test('bên gửi rút lại được, bên kia không rút hộ', async () => {
    await seed({ extraSchedules: pending('RECORDER', RECORDER) });
    await assertFails(db(REP).doc(schedulePath('r1')).update(withdraw(REP)));
    await assertSucceeds(db(RECORDER).doc(schedulePath('r1')).update(withdraw(RECORDER)));
  });

  test('tổ chức rút lại yêu cầu của tổ chức', async () => {
    await seed({ extraSchedules: pending('ORG', REP) });
    await assertFails(db(RECORDER).doc(schedulePath('r1')).update(withdraw(RECORDER)));
    await assertSucceeds(db(ASSIGNEE).doc(schedulePath('r1')).update(withdraw(ASSIGNEE)));
  });
});

describe('E02 · Hồi quy F12', () => {
  test('chốt lịch F12 phải đúng là lịch nhu cầu trỏ tới', async () => {
    // Nhu cầu đang ACCEPTED, có đề xuất p1 chờ; chốt nhưng ghi scheduleId khác thì bị từ chối
    await seed({
      needStatus: 'ACCEPTED',
      extraSchedules: {
        p1: {
          startAt: N_START, endAt: N_END, format: 'Đến nhà', place: 'Phường 7, Quận 3',
          status: 'PROPOSED', proposedByUid: REP, proposedByName: 'Đại diện', createdAt: Timestamp.now(),
        },
      },
    });
    const d = db(RECORDER);
    const b = d.batch();
    b.update(d.doc(schedulePath('p1')), {
      status: 'CONFIRMED', elderConsent: true, consentMethod: 'Gọi điện',
      decidedByUid: RECORDER, decidedByName: 'Mai', decidedAt: FieldValue.serverTimestamp(),
    });
    b.update(d.doc(`needs_summary/${NEED}`), { status: 'SCHEDULED', updatedAt: FieldValue.serverTimestamp() });
    await assertFails(b.commit());
  });
});
