// Kiểm thử Security Rules cho phần phối hợp sau tiếp nhận:
//   F11 · chat theo lần tiếp nhận (needs_assignments/{id}/messages)
//   F12 · đề xuất / thống nhất lịch (needs_assignments/{id}/schedules + needs_summary)
//   F13 · báo hỗ trợ và đối soát (needs_summary)
//   F14 · trả lại nhu cầu phải xóa hết lịch đã chốt (needs_summary)
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

const REC_ORG = 'org-hoi-ctd-q3';   // đơn vị ghi nhận
const ORG = 'org-doan-tn-p7';       // tổ chức phụ trách
const OTHER_ORG = 'org-khac';

const RECORDER = 'u-recorder';      // đầu mối ghi nhận (RECORDER) — người tạo nhu cầu
const RECORDER2 = 'u-recorder-2';   // đầu mối khác cùng đơn vị (RECORDER) — không trong chat
const REP = 'u-rep';                // đại diện tổ chức (REPRESENTATIVE) — người bấm tiếp nhận
const ASSIGNEE = 'u-assignee';      // thành viên được giao phụ trách
const OUTSIDER = 'u-outsider';      // đại diện tổ chức khác

const NEED = 'need-1';
const ASSIGN = 'assign-1';

let env;

const hours = (h) => h * 60 * 60 * 1000;
const at = (ms) => Timestamp.fromMillis(Date.now() + ms);

function db(uid) {
  return env.authenticatedContext(uid).firestore();
}

async function seed({ needStatus = 'ACCEPTED', assignmentStatus = 'ACTIVE', extraSchedules = {} } = {}) {
  await env.withSecurityRulesDisabled(async (ctx) => {
    const d = ctx.firestore();
    const member = (uid, orgId, roles, active = true) =>
      d.doc(`memberships/${uid}/organizations/${orgId}`).set({ roles, active, orgId });
    await member(RECORDER, REC_ORG, ['RECORDER']);
    await member(RECORDER2, REC_ORG, ['RECORDER']);
    await member(REP, ORG, ['REPRESENTATIVE']);
    await member(ASSIGNEE, ORG, []);
    await member(OUTSIDER, OTHER_ORG, ['REPRESENTATIVE']);

    await d.doc(`needs_summary/${NEED}`).set({
      caseCode: 'NC-0142',
      title: 'Trò chuyện cuối tuần với cụ bà sống một mình',
      area: 'Phường 7, Quận 3',
      status: needStatus,
      recorderOrgId: REC_ORG,
      recorderOrgName: 'Hội CTĐ Quận 3',
      recorderUid: RECORDER,
      recorderName: 'Trần Thị Mai',
      assignedOrgId: ORG,
      assignedOrgName: 'Đoàn TN Phường 7',
      assigneeUid: ASSIGNEE,
      assigneeName: 'Nguyễn Minh Khoa',
      currentAssignmentId: ASSIGN,
      updatedAt: Timestamp.now(),
    });
    await d.doc(`needs_assignments/${ASSIGN}`).set({
      needId: NEED,
      caseCode: 'NC-0142',
      recorderOrgId: REC_ORG,
      orgId: ORG,
      orgName: 'Đoàn TN Phường 7',
      assigneeUid: ASSIGNEE,
      assigneeName: 'Nguyễn Minh Khoa',
      acceptedByUid: REP,
      status: assignmentStatus,
    });
    for (const [id, data] of Object.entries(extraSchedules)) {
      await d.doc(`needs_assignments/${ASSIGN}/schedules/${id}`).set(data);
    }
  });
}

function proposal(overrides = {}) {
  return {
    startAt: at(hours(48)),
    endAt: at(hours(50)),
    format: 'Đến nhà · 2 đoàn viên',
    place: 'Phường 7, Quận 3',
    status: 'PROPOSED',
    proposedByUid: REP,
    proposedByName: 'Đại diện Đoàn TN',
    createdAt: FieldValue.serverTimestamp(),
    ...overrides,
  };
}

/** Đề xuất đang chờ đã có sẵn trong dữ liệu (tạo bằng quyền quản trị). */
const START = Timestamp.fromMillis(Date.now() + hours(72));
const END = Timestamp.fromMillis(Date.now() + hours(74));
const PENDING = {
  p1: {
    startAt: START, endAt: END, format: 'Đến nhà · 2 đoàn viên', place: 'Phường 7, Quận 3',
    status: 'PROPOSED', proposedByUid: REP, proposedByName: 'Đại diện', createdAt: Timestamp.now(),
  },
};

function message(uid, text = 'Chào chị, bên em đề xuất chiều thứ 7 ạ.') {
  return { senderUid: uid, senderName: 'Người gửi', text, at: FieldValue.serverTimestamp() };
}

/** Transaction chốt lịch như CoordinationRepository.confirmSchedule (dùng batch cho gọn). */
function confirmBatch(d, { elderConsent = true, needOverrides = {}, scheduleOverrides = {} } = {}) {
  const b = d.batch();
  b.update(d.doc(`needs_assignments/${ASSIGN}/schedules/p1`), {
    status: 'CONFIRMED', elderConsent, consentMethod: 'Gọi qua con gái · 15/10',
    decidedByUid: RECORDER, decidedByName: 'Trần Thị Mai', decidedAt: FieldValue.serverTimestamp(),
    ...scheduleOverrides,
  });
  b.update(d.doc(`needs_summary/${NEED}`), {
    status: 'SCHEDULED', scheduleId: 'p1', scheduledAt: START, scheduledEndAt: END,
    scheduledFormat: 'Đến nhà · 2 đoàn viên', scheduledPlace: 'Phường 7, Quận 3',
    scheduledByUid: RECORDER, updatedAt: FieldValue.serverTimestamp(),
    ...needOverrides,
  });
  return b.commit();
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

// =====================================================================
// F11 · Chat
// =====================================================================

describe('F11 · Chat theo lần tiếp nhận', () => {
  const messages = (uid) => db(uid).collection(`needs_assignments/${ASSIGN}/messages`);

  test('đầu mối ghi nhận gửi được tin nhắn', async () => {
    await seed();
    await assertSucceeds(messages(RECORDER).add(message(RECORDER)));
  });

  test('người được giao phụ trách gửi và đọc được tin nhắn', async () => {
    await seed();
    await assertSucceeds(messages(ASSIGNEE).add(message(ASSIGNEE)));
    await assertSucceeds(messages(ASSIGNEE).get());
  });

  test('đại diện tổ chức khác không đọc / gửi được', async () => {
    await seed();
    await assertFails(messages(OUTSIDER).get());
    await assertFails(messages(OUTSIDER).add(message(OUTSIDER)));
  });

  test('thành viên cùng tổ chức nhưng không phải người được giao không vào được chat (R08)', async () => {
    await seed();
    await assertFails(messages(REP).get());
    await assertFails(messages(REP).add(message(REP)));
  });

  test('đầu mối khác cùng đơn vị (không phải người ghi nhận) không vào được chat', async () => {
    await seed();
    await assertFails(messages(RECORDER2).get());
  });

  test('không giả danh người gửi khác', async () => {
    await seed();
    await assertFails(messages(RECORDER).add(message(ASSIGNEE)));
  });

  test('không gửi tin quá 1000 ký tự hoặc tin rỗng', async () => {
    await seed();
    await assertFails(messages(RECORDER).add(message(RECORDER, 'a'.repeat(1001))));
    await assertFails(messages(RECORDER).add(message(RECORDER, '')));
  });

  test('không tự đặt thời gian gửi (phải là serverTimestamp)', async () => {
    await seed();
    await assertFails(messages(RECORDER).add({ ...message(RECORDER), at: Timestamp.fromMillis(0) }));
  });

  test('không chèn thêm field (ví dụ giả tin hệ thống)', async () => {
    await seed();
    await assertFails(messages(RECORDER).add({ ...message(RECORDER), type: 'SYSTEM' }));
  });

  test('lần tiếp nhận đã trả lại: vẫn đọc được tin cũ nhưng không gửi được nữa', async () => {
    await seed({ needStatus: 'OPEN', assignmentStatus: 'RETURNED' });
    await assertSucceeds(messages(RECORDER).get());
    await assertFails(messages(RECORDER).add(message(RECORDER)));
    await assertFails(messages(ASSIGNEE).add(message(ASSIGNEE)));
  });

  test('nhu cầu đã hoàn thành: chat chỉ còn đọc', async () => {
    await seed({ needStatus: 'COMPLETED', assignmentStatus: 'CLOSED' });
    await assertSucceeds(messages(ASSIGNEE).get());
    await assertFails(messages(ASSIGNEE).add(message(ASSIGNEE)));
  });

  test('không sửa, không xóa tin nhắn', async () => {
    await seed();
    const ref = await messages(RECORDER).add(message(RECORDER));
    await assertFails(db(RECORDER).doc(ref.path).update({ text: 'đã sửa' }));
    await assertFails(db(RECORDER).doc(ref.path).delete());
  });
});

// =====================================================================
// F12 · Thống nhất lịch
// =====================================================================

describe('F12 · Đề xuất lịch (ngày, khung giờ, hình thức)', () => {
  const schedules = (uid) => db(uid).collection(`needs_assignments/${ASSIGN}/schedules`);

  test('đại diện tổ chức phụ trách đề xuất được lịch hợp lệ', async () => {
    await seed();
    await assertSucceeds(schedules(REP).add(proposal()));
  });

  test('người được giao phụ trách cũng đề xuất được', async () => {
    await seed();
    await assertSucceeds(schedules(ASSIGNEE).add(proposal({ proposedByUid: ASSIGNEE })));
  });

  test('thiếu khung giờ kết thúc hoặc hình thức thì bị từ chối', async () => {
    await seed();
    const noEnd = proposal();
    delete noEnd.endAt;
    const noFormat = proposal();
    delete noFormat.format;
    await assertFails(schedules(REP).add(noEnd));
    await assertFails(schedules(REP).add(noFormat));
  });

  test('giờ kết thúc phải sau giờ bắt đầu và khung giờ tối đa 12 tiếng', async () => {
    await seed();
    await assertFails(schedules(REP).add(proposal({ endAt: at(hours(47)) })));
    await assertFails(schedules(REP).add(proposal({ endAt: at(hours(48 + 13)) })));
  });

  test('không đề xuất lịch trong quá khứ', async () => {
    await seed();
    await assertFails(schedules(REP).add(proposal({ startAt: at(-hours(2)), endAt: at(-hours(1)) })));
  });

  test('hình thức quá ngắn bị từ chối', async () => {
    await seed();
    await assertFails(schedules(REP).add(proposal({ format: 'x' })));
  });

  test('đầu mối ghi nhận và tổ chức khác không đề xuất được', async () => {
    await seed();
    await assertFails(schedules(RECORDER).add(proposal({ proposedByUid: RECORDER })));
    await assertFails(schedules(OUTSIDER).add(proposal({ proposedByUid: OUTSIDER })));
  });

  test('không đề xuất khi nhu cầu đã chốt lịch', async () => {
    await seed({ needStatus: 'SCHEDULED' });
    await assertFails(schedules(REP).add(proposal()));
  });

  test('tổ chức rút đề xuất cũ khi gửi đề xuất mới', async () => {
    await seed({ extraSchedules: PENDING });
    await assertSucceeds(db(REP).doc(`needs_assignments/${ASSIGN}/schedules/p1`).update({
      status: 'WITHDRAWN', decidedByUid: REP, decidedByName: 'Đại diện', decidedAt: FieldValue.serverTimestamp(),
    }));
  });
});

describe('F12 · Chốt lịch có ghi nhận đồng ý của cụ', () => {
  test('đầu mối tick "Cụ đồng ý" và chốt: nhu cầu sang SCHEDULED', async () => {
    await seed({ extraSchedules: PENDING });
    await assertSucceeds(confirmBatch(db(RECORDER)));
  });

  test('chưa tick đồng ý (elderConsent) thì không chốt được', async () => {
    await seed({ extraSchedules: PENDING });
    await assertFails(confirmBatch(db(RECORDER), { elderConsent: false }));
  });

  test('thiếu cách hỏi ý kiến thì không chốt được', async () => {
    await seed({ extraSchedules: PENDING });
    await assertFails(confirmBatch(db(RECORDER), { scheduleOverrides: { consentMethod: '' } }));
  });

  test('khung giờ / hình thức trên nhu cầu phải khớp lịch đã chốt', async () => {
    await seed({ extraSchedules: PENDING });
    await assertFails(confirmBatch(db(RECORDER), { needOverrides: { scheduledFormat: 'Gọi video' } }));
  });

  test('giờ kết thúc trên nhu cầu phải khớp lịch đã chốt', async () => {
    await seed({ extraSchedules: PENDING });
    await assertFails(confirmBatch(db(RECORDER),
      { needOverrides: { scheduledEndAt: Timestamp.fromMillis(END.toMillis() + hours(1)) } }));
  });

  test('chỉ xác nhận lịch mà không đổi trạng thái nhu cầu thì bị từ chối', async () => {
    await seed({ extraSchedules: PENDING });
    await assertFails(db(RECORDER).doc(`needs_assignments/${ASSIGN}/schedules/p1`).update({
      status: 'CONFIRMED', elderConsent: true, consentMethod: 'Gọi điện',
      decidedByUid: RECORDER, decidedByName: 'Mai', decidedAt: FieldValue.serverTimestamp(),
    }));
  });

  test('tổ chức phụ trách không tự chốt lịch', async () => {
    await seed({ extraSchedules: PENDING });
    await assertFails(confirmBatch(db(REP), {
      scheduleOverrides: { decidedByUid: REP }, needOverrides: { scheduledByUid: REP },
    }));
  });

  test('đầu mối đề nghị đổi giờ kèm lý do', async () => {
    await seed({ extraSchedules: PENDING });
    await assertSucceeds(db(RECORDER).doc(`needs_assignments/${ASSIGN}/schedules/p1`).update({
      status: 'DECLINED', declineReason: 'Cụ bận buổi chiều',
      decidedByUid: RECORDER, decidedByName: 'Mai', decidedAt: FieldValue.serverTimestamp(),
    }));
  });

  test('đổi giờ khác mà không có lý do thì bị từ chối', async () => {
    await seed({ extraSchedules: PENDING });
    await assertFails(db(RECORDER).doc(`needs_assignments/${ASSIGN}/schedules/p1`).update({
      status: 'DECLINED', declineReason: '',
      decidedByUid: RECORDER, decidedByName: 'Mai', decidedAt: FieldValue.serverTimestamp(),
    }));
  });

  test('từ chối nhưng gắn cờ "cụ đồng ý" là mâu thuẫn, bị từ chối', async () => {
    await seed({ extraSchedules: PENDING });
    await assertFails(db(RECORDER).doc(`needs_assignments/${ASSIGN}/schedules/p1`).update({
      status: 'DECLINED', declineReason: 'Đổi giờ', elderConsent: true,
      decidedByUid: RECORDER, decidedByName: 'Mai', decidedAt: FieldValue.serverTimestamp(),
    }));
  });

  test('sau khi chốt, đại diện được giao mới đọc được thông tin riêng (R03)', async () => {
    await seed({ extraSchedules: PENDING });
    await env.withSecurityRulesDisabled((ctx) => ctx.firestore().doc(`needs_private/${NEED}`).set({
      elderlyName: 'Cụ Lan', contact: '0900000000', address: 'Hẻm 12', recorderOrgId: REC_ORG,
    }));
    await assertFails(db(ASSIGNEE).doc(`needs_private/${NEED}`).get());
    await assertSucceeds(confirmBatch(db(RECORDER)));
    await assertSucceeds(db(ASSIGNEE).doc(`needs_private/${NEED}`).get());
  });
});

// =====================================================================
// F13 · Báo hỗ trợ và đối soát
// =====================================================================

describe('F13 · Báo hỗ trợ và đối soát', () => {
  const need = (uid) => db(uid).doc(`needs_summary/${NEED}`);

  test('thành viên tổ chức phụ trách báo đã hỗ trợ: SCHEDULED → DELIVERED', async () => {
    await seed({ needStatus: 'SCHEDULED' });
    await assertSucceeds(need(ASSIGNEE).update({
      status: 'DELIVERED', deliveryReport: 'Hai đoàn viên đến trò chuyện 2 tiếng.',
      deliveredByUid: ASSIGNEE, deliveredByName: 'Khoa', deliveredAt: FieldValue.serverTimestamp(),
      updatedAt: FieldValue.serverTimestamp(),
    }));
  });

  test('đầu mối không tự báo đã hỗ trợ thay tổ chức', async () => {
    await seed({ needStatus: 'SCHEDULED' });
    await assertFails(need(RECORDER).update({
      status: 'DELIVERED', deliveryReport: 'Đã hỗ trợ xong.',
      deliveredByUid: RECORDER, deliveredByName: 'Mai', deliveredAt: FieldValue.serverTimestamp(),
      updatedAt: FieldValue.serverTimestamp(),
    }));
  });

  test('đầu mối ghi "chưa khớp": giữ DELIVERED', async () => {
    await seed({ needStatus: 'DELIVERED' });
    await assertSucceeds(need(RECORDER).update({
      reconcileNote: 'Cụ nói buổi hẹn kết thúc sớm.', reconcileAt: FieldValue.serverTimestamp(),
      updatedAt: FieldValue.serverTimestamp(),
    }));
  });

  test('tổ chức không tự đối soát / hoàn thành', async () => {
    await seed({ needStatus: 'DELIVERED' });
    await assertFails(need(REP).update({
      status: 'COMPLETED', completedByUid: REP, completedAt: FieldValue.serverTimestamp(),
      updatedAt: FieldValue.serverTimestamp(),
    }));
  });

  test('đầu mối hoàn thành và đóng lần tiếp nhận cùng lúc', async () => {
    await seed({ needStatus: 'DELIVERED' });
    const d = db(RECORDER);
    const b = d.batch();
    b.update(d.doc(`needs_summary/${NEED}`), {
      status: 'COMPLETED', completionNote: null, completedByUid: RECORDER,
      completedAt: FieldValue.serverTimestamp(), updatedAt: FieldValue.serverTimestamp(),
    });
    b.update(d.doc(`needs_assignments/${ASSIGN}`), {
      status: 'CLOSED', endReason: 'Hoàn thành', endNote: null, endedByUid: RECORDER,
      endedAt: FieldValue.serverTimestamp(),
    });
    await assertSucceeds(b.commit());
  });
});

// =====================================================================
// F14 · Trả lại nhu cầu sau khi đã hẹn lịch
// =====================================================================

describe('F14 · Trả lại xóa hết lịch đã chốt', () => {
  const need = (uid) => db(uid).doc(`needs_summary/${NEED}`);

  /** Nhu cầu đã chốt lịch như sau CoordinationRepository.confirmSchedule. */
  async function seedScheduled() {
    await seed({ needStatus: 'SCHEDULED' });
    await env.withSecurityRulesDisabled((ctx) => ctx.firestore().doc(`needs_summary/${NEED}`).update({
      scheduleId: 'p1', scheduledAt: START, scheduledEndAt: END,
      scheduledFormat: 'Đến nhà · 2 đoàn viên', scheduledPlace: 'Phường 7, Quận 3',
      scheduledByUid: RECORDER,
    }));
  }

  /** Đúng các field NeedRepository.returnNeed ghi vào needs_summary. */
  function returnUpdate(overrides = {}) {
    return {
      status: 'OPEN', assignedOrgId: null, assignedOrgName: null, assigneeUid: null,
      assigneeName: null, acceptedByUid: null, currentAssignmentId: null, acceptedAt: null,
      returnCount: FieldValue.increment(1), lastReturnReason: 'Không đủ người tham gia',
      scheduleId: null, scheduledAt: null, scheduledEndAt: null, scheduledFormat: null,
      scheduledPlace: null, scheduledByUid: null, updatedAt: FieldValue.serverTimestamp(),
      ...overrides,
    };
  }

  test('đại diện trả lại ca đã hẹn lịch: về OPEN, xóa hết lịch cũ', async () => {
    await seedScheduled();
    await assertSucceeds(need(REP).update(returnUpdate()));
  });

  test('trả lại ca chưa hẹn lịch (ACCEPTED) vẫn được', async () => {
    await seed({ needStatus: 'ACCEPTED' });
    await assertSucceeds(need(REP).update(returnUpdate()));
  });

  test('trả lại mà còn giữ giờ kết thúc hoặc hình thức của lịch cũ thì bị chặn', async () => {
    await seedScheduled();
    const { scheduledEndAt, ...keepEnd } = returnUpdate();
    const { scheduledFormat, ...keepFormat } = returnUpdate();
    await assertFails(need(REP).update(keepEnd));
    await assertFails(need(REP).update(keepFormat));
  });

  test('thành viên không phải đại diện không trả lại được', async () => {
    await seedScheduled();
    await assertFails(need(ASSIGNEE).update(returnUpdate()));
  });
});
