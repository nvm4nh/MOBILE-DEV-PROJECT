// Kiểm thử Security Rules cho phần phối hợp sau tiếp nhận:
//   F04, F05 · bảng tin: đọc bài, đăng / sửa / ẩn bài của tổ chức (posts)
//   F11 · chat theo lần tiếp nhận (needs_assignments/{id}/messages)
//   F12 · đề xuất / thống nhất lịch (needs_assignments/{id}/schedules + needs_summary)
//   F13 · báo hỗ trợ và đối soát (needs_summary)
//   F14 · trả lại nhu cầu phải xóa hết lịch đã chốt (needs_summary)
//   E03 · đánh giá sau hỗ trợ (org_ratings) và xử lý phản ánh (feedback_reports)
//   E07 · sự kiện cộng đồng và đăng ký tham gia (community_events)
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

// =====================================================================
// F04, F05 · Bảng tin
// =====================================================================

describe('F04, F05 · Bảng tin (posts)', () => {
  const posts = (uid) => db(uid).collection('posts');

  /** Bài đăng đúng như PostRepository.createPost ghi. */
  function post(overrides = {}) {
    return {
      title: 'Ngày hội thăm hỏi người cao tuổi',
      content: 'Đoàn viên phường 7 đến thăm và trò chuyện cùng các cụ trong khu phố.',
      orgId: ORG, orgName: 'Đoàn TN Phường 7', orgVerified: true,
      authorUid: REP, authorName: 'Đại diện Đoàn TN',
      eventTime: null, eventPlace: null, hidden: false,
      createdAt: FieldValue.serverTimestamp(), updatedAt: FieldValue.serverTimestamp(),
      ...overrides,
    };
  }

  /** Thành viên, tổ chức (ORG đã xác minh, OTHER_ORG chưa) và 2 bài có sẵn: một bài hiện, một bài đã ẩn. */
  async function seedPosts() {
    await seed();
    await env.withSecurityRulesDisabled(async (ctx) => {
      const d = ctx.firestore();
      await d.doc(`organizations/${ORG}`).set({ name: 'Đoàn TN Phường 7', verified: true });
      await d.doc(`organizations/${OTHER_ORG}`).set({ name: 'Tổ chức khác', verified: false });
      const saved = { ...post(), createdAt: Timestamp.now(), updatedAt: Timestamp.now() };
      await d.doc('posts/p-visible').set(saved);
      await d.doc('posts/p-hidden').set({ ...saved, hidden: true });
    });
  }

  test('đại diện đăng bài cho tổ chức của mình', async () => {
    await seedPosts();
    await assertSucceeds(posts(REP).add(post()));
  });

  test('không tự gắn tích xác minh khi tổ chức chưa được xác minh', async () => {
    await seedPosts();
    const other = { orgId: OTHER_ORG, orgName: 'Tổ chức khác', authorUid: OUTSIDER };
    await assertFails(posts(OUTSIDER).add(post({ ...other, orgVerified: true })));
    await assertSucceeds(posts(OUTSIDER).add(post({ ...other, orgVerified: false })));
  });

  test('thành viên không phải đại diện không đăng được; không đăng thay tổ chức khác', async () => {
    await seedPosts();
    await assertFails(posts(ASSIGNEE).add(post({ authorUid: ASSIGNEE })));
    await assertFails(posts(REP).add(post({ orgId: OTHER_ORG, orgVerified: false })));
  });

  test('không giả danh người đăng, không đăng bài đã ẩn, nội dung phải đủ dài', async () => {
    await seedPosts();
    await assertFails(posts(REP).add(post({ authorUid: ASSIGNEE })));
    await assertFails(posts(REP).add(post({ hidden: true })));
    await assertFails(posts(REP).add(post({ title: 'Hi' })));
    await assertFails(posts(REP).add(post({ createdAt: Timestamp.fromMillis(0) })));
    await assertFails(posts(REP).add(post({ phone: '0909000000' })));
  });

  test('tài khoản đăng nhập đọc được bài đang hiện; bài đã ẩn chỉ thành viên tổ chức xem', async () => {
    await seedPosts();
    await assertSucceeds(posts(RECORDER).doc('p-visible').get());
    await assertFails(posts(RECORDER).doc('p-hidden').get());
    await assertSucceeds(posts(ASSIGNEE).doc('p-hidden').get());
    await assertFails(env.unauthenticatedContext().firestore().doc('posts/p-visible').get());
  });

  test('danh sách bảng tin phải lọc hidden == false', async () => {
    await seedPosts();
    await assertSucceeds(posts(RECORDER).where('hidden', '==', false).get());
    await assertFails(posts(RECORDER).get());
  });

  test('đại diện sửa và ẩn bài của tổ chức mình', async () => {
    await seedPosts();
    await assertSucceeds(posts(REP).doc('p-visible').update({ title: 'Ngày hội thăm hỏi tháng 11', updatedAt: FieldValue.serverTimestamp() }));
    await assertSucceeds(posts(REP).doc('p-visible').update({ hidden: true, updatedAt: FieldValue.serverTimestamp() }));
  });

  test('không sửa bài của tổ chức khác, không đổi tổ chức / người đăng, không xóa bài', async () => {
    await seedPosts();
    await assertFails(posts(OUTSIDER).doc('p-visible').update({ hidden: true, updatedAt: FieldValue.serverTimestamp() }));
    await assertFails(posts(ASSIGNEE).doc('p-visible').update({ hidden: true, updatedAt: FieldValue.serverTimestamp() }));
    await assertFails(posts(REP).doc('p-visible').update({ orgId: OTHER_ORG, updatedAt: FieldValue.serverTimestamp() }));
    await assertFails(posts(REP).doc('p-visible').update({ authorUid: ASSIGNEE, updatedAt: FieldValue.serverTimestamp() }));
    await assertFails(posts(REP).doc('p-visible').delete());
  });
});

// =====================================================================
// E03 · Đánh giá sau hỗ trợ và xử lý phản ánh
// =====================================================================

const REVIEWER = 'u-reviewer';      // điều phối viên của đơn vị ghi nhận (REVIEWER)

async function seedFeedback(opts = { needStatus: 'COMPLETED', assignmentStatus: 'CLOSED' }) {
  await seed(opts);
  await env.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().doc(`memberships/${REVIEWER}/organizations/${REC_ORG}`)
      .set({ roles: ['REVIEWER'], active: true, orgId: REC_ORG });
  });
}

function rating(overrides = {}) {
  return {
    needId: NEED, caseCode: 'NC-0142', recorderOrgId: REC_ORG, orgId: ORG,
    orgName: 'Đoàn TN Phường 7', assigneeName: 'Nguyễn Minh Khoa',
    stars: 5, tags: ['Đúng giờ', 'Nhiệt tình'], comment: 'Cụ vui lắm', elderAsked: true,
    byUid: RECORDER, byName: 'Trần Thị Mai', createdAt: FieldValue.serverTimestamp(),
    ...overrides,
  };
}

function report(overrides = {}) {
  return {
    needId: NEED, caseCode: 'NC-0142', assignmentId: ASSIGN, recorderOrgId: REC_ORG,
    reporterOrgId: REC_ORG, reportedOrgId: ORG, reportedOrgName: 'Đoàn TN Phường 7',
    type: 'LATE', description: 'Hẹn 15:00 nhưng 16:10 mới đến, không báo trước.',
    status: 'OPEN', createdByUid: RECORDER, createdByName: 'Trần Thị Mai',
    createdAt: FieldValue.serverTimestamp(), updatedAt: FieldValue.serverTimestamp(),
    ...overrides,
  };
}

function note(kind, uid, byRole, text = 'Nội dung') {
  return { kind, text, byUid: uid, byName: 'Người ghi', byRole, at: FieldValue.serverTimestamp() };
}

/** Phản ánh đã có sẵn (tạo bằng quyền quản trị). */
async function seedReport(status = 'OPEN', extra = {}) {
  await env.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().doc('feedback_reports/r1').set({
      ...report(), status, createdAt: Timestamp.now(), updatedAt: Timestamp.now(), ...extra,
    });
  });
}

function startBatch(d, uid = REVIEWER) {
  const b = d.batch();
  b.update(d.doc('feedback_reports/r1'), {
    status: 'IN_PROGRESS', handlerUid: uid, handlerName: 'Lê Hoàng', updatedAt: FieldValue.serverTimestamp(),
  });
  b.set(d.collection('feedback_reports/r1/notes').doc(), note('START', uid, 'REVIEWER', 'Tiếp nhận phản ánh'));
  return b.commit();
}

function closeBatch(d, resolution = 'Đã nhắc tổ chức báo trước khi đến trễ.') {
  const b = d.batch();
  b.update(d.doc('feedback_reports/r1'), {
    status: 'CLOSED', resolution, closedAt: FieldValue.serverTimestamp(), updatedAt: FieldValue.serverTimestamp(),
  });
  b.set(d.collection('feedback_reports/r1/notes').doc(), note('CLOSE', REVIEWER, 'REVIEWER', resolution || 'x'));
  return b.commit();
}

describe('E03 · Đánh giá tổ chức sau khi hoàn thành', () => {
  const ratingDoc = (uid) => db(uid).doc(`org_ratings/${ASSIGN}`);

  test('đầu mối ghi nhận đánh giá được khi nhu cầu đã hoàn thành', async () => {
    await seedFeedback();
    await assertSucceeds(ratingDoc(RECORDER).get());   // kiểm tra "đã đánh giá chưa"
    await assertSucceeds(ratingDoc(RECORDER).set(rating()));
  });

  test('mỗi lần hỗ trợ chỉ đánh giá một lần; không sửa, không xóa', async () => {
    await seedFeedback();
    await assertSucceeds(ratingDoc(RECORDER).set(rating()));
    await assertFails(ratingDoc(RECORDER).set(rating({ stars: 1 })));
    await assertFails(ratingDoc(RECORDER).update({ stars: 1 }));
    await assertFails(ratingDoc(RECORDER).delete());
  });

  test('chưa hoàn thành thì chưa đánh giá được', async () => {
    await seedFeedback({ needStatus: 'DELIVERED', assignmentStatus: 'ACTIVE' });
    await assertFails(ratingDoc(RECORDER).set(rating()));
  });

  test('tổ chức được đánh giá và tổ chức khác không tự đánh giá', async () => {
    await seedFeedback();
    await assertFails(ratingDoc(REP).set(rating({ byUid: REP })));
    await assertFails(ratingDoc(OUTSIDER).set(rating({ byUid: OUTSIDER })));
  });

  test('phải hỏi ý kiến cụ, số sao 1–5, đúng tổ chức của lần tiếp nhận', async () => {
    await seedFeedback();
    await assertFails(ratingDoc(RECORDER).set(rating({ elderAsked: false })));
    await assertFails(ratingDoc(RECORDER).set(rating({ stars: 0 })));
    await assertFails(ratingDoc(RECORDER).set(rating({ stars: 6 })));
    await assertFails(ratingDoc(RECORDER).set(rating({ orgId: OTHER_ORG })));
    await assertFails(ratingDoc(RECORDER).set(rating({ byUid: RECORDER2 })));
    await assertFails(ratingDoc(RECORDER).set(rating({ createdAt: Timestamp.fromMillis(0) })));
    await assertFails(ratingDoc(RECORDER).set(rating({ comment: 'a'.repeat(501) })));
  });

  test('nhận xét không bắt buộc', async () => {
    await seedFeedback();
    await assertSucceeds(ratingDoc(RECORDER).set(rating({ comment: null, tags: [] })));
  });

  test('chỉ hai bên của lần tiếp nhận đọc được đánh giá', async () => {
    await seedFeedback();
    await env.withSecurityRulesDisabled(async (ctx) => {
      await ctx.firestore().doc(`org_ratings/${ASSIGN}`).set({ ...rating(), createdAt: Timestamp.now() });
    });
    await assertSucceeds(ratingDoc(RECORDER2).get());
    await assertSucceeds(ratingDoc(ASSIGNEE).get());
    await assertFails(ratingDoc(OUTSIDER).get());
  });
});

describe('E03 · Gửi phản ánh', () => {
  const reports = (uid) => db(uid).collection('feedback_reports');

  test('đầu mối ghi nhận phản ánh tổ chức phụ trách', async () => {
    await seedFeedback();
    await assertSucceeds(reports(RECORDER).add(report()));
  });

  test('thành viên tổ chức phụ trách phản ánh đơn vị ghi nhận', async () => {
    await seedFeedback();
    await assertSucceeds(reports(ASSIGNEE).add(report({
      reporterOrgId: ORG, reportedOrgId: REC_ORG, reportedOrgName: 'Hội CTĐ Quận 3',
      type: 'ATTITUDE', createdByUid: ASSIGNEE, createdByName: 'Nguyễn Minh Khoa',
    })));
  });

  test('tổ chức ngoài lần tiếp nhận không gửi được', async () => {
    await seedFeedback();
    await assertFails(reports(OUTSIDER).add(report({ reporterOrgId: OTHER_ORG, createdByUid: OUTSIDER })));
  });

  test('phản ánh phải nói về bên còn lại của lần tiếp nhận', async () => {
    await seedFeedback();
    await assertFails(reports(RECORDER).add(report({ reportedOrgId: OTHER_ORG })));
    await assertFails(reports(RECORDER).add(report({ reportedOrgId: REC_ORG })));
  });

  test('loại phản ánh hợp lệ, mô tả đủ dài, trạng thái ban đầu là OPEN', async () => {
    await seedFeedback();
    await assertFails(reports(RECORDER).add(report({ type: 'SPAM' })));
    await assertFails(reports(RECORDER).add(report({ description: 'ngắn' })));
    await assertFails(reports(RECORDER).add(report({ status: 'CLOSED' })));
    await assertFails(reports(RECORDER).add(report({ createdByUid: RECORDER2 })));
    await assertFails(reports(RECORDER).add(report({ handlerUid: REVIEWER })));
  });

  test('tổ chức bị phản ánh chỉ đọc được khi điều phối viên đã tiếp nhận', async () => {
    await seedFeedback();
    await seedReport('OPEN');
    await assertSucceeds(reports(RECORDER).doc('r1').get());
    await assertSucceeds(reports(REVIEWER).doc('r1').get());
    await assertFails(reports(REP).doc('r1').get());
    await assertFails(reports(OUTSIDER).doc('r1').get());
    await assertSucceeds(startBatch(db(REVIEWER)));
    await assertSucceeds(reports(REP).doc('r1').get());
  });

  test('danh sách: điều phối viên lọc theo đơn vị; người gửi lọc theo chính mình', async () => {
    await seedFeedback();
    await seedReport('OPEN');
    await assertSucceeds(reports(REVIEWER).where('recorderOrgId', '==', REC_ORG).get());
    await assertSucceeds(reports(RECORDER).where('createdByUid', '==', RECORDER).get());
    await assertFails(reports(REP).where('recorderOrgId', '==', REC_ORG).get());
    await assertFails(reports(RECORDER2).where('recorderOrgId', '==', REC_ORG).get());
  });
});

describe('E03 · Điều phối viên xử lý phản ánh', () => {
  const notes = (uid) => db(uid).collection('feedback_reports/r1/notes');

  test('tiếp nhận → hỏi thêm → người gửi bổ sung → đóng có kết quả', async () => {
    await seedFeedback();
    await seedReport('OPEN');
    await assertSucceeds(startBatch(db(REVIEWER)));
    await assertSucceeds(notes(REVIEWER).add(note('ASK', REVIEWER, 'REVIEWER', 'Tổ chức có báo trễ qua chat không?')));
    await assertSucceeds(notes(RECORDER).add(note('INFO', RECORDER, 'REPORTER', 'Không báo gì cả.')));
    await assertSucceeds(notes(REP).get());
    await assertSucceeds(closeBatch(db(REVIEWER)));
  });

  test('chỉ điều phối viên đơn vị ghi nhận tiếp nhận; không tự xử lý phản ánh của mình', async () => {
    await seedFeedback();
    await seedReport('OPEN');
    await assertFails(startBatch(db(RECORDER2), RECORDER2));
    await assertFails(startBatch(db(REP), REP));
    await seedReport('OPEN', { createdByUid: REVIEWER });
    await assertFails(startBatch(db(REVIEWER)));
  });

  test('đóng phải có kết quả xử lý và đi đúng thứ tự trạng thái', async () => {
    await seedFeedback();
    await seedReport('OPEN');
    await assertFails(closeBatch(db(REVIEWER)));               // chưa tiếp nhận
    await assertSucceeds(startBatch(db(REVIEWER)));
    await assertFails(closeBatch(db(REVIEWER), 'ok'));         // kết quả quá ngắn
    await assertSucceeds(closeBatch(db(REVIEWER)));
    await assertFails(db(REVIEWER).doc('feedback_reports/r1').update({
      status: 'OPEN', updatedAt: FieldValue.serverTimestamp(),
    }));
  });

  test('ghi chú: hỏi thêm chỉ khi đang xử lý; người khác không bổ sung; đã đóng thì không bổ sung', async () => {
    await seedFeedback();
    await seedReport('OPEN');
    await assertFails(notes(REVIEWER).add(note('ASK', REVIEWER, 'REVIEWER')));
    await assertFails(notes(OUTSIDER).add(note('INFO', OUTSIDER, 'REPORTER')));
    await assertFails(notes(RECORDER).add(note('START', RECORDER, 'REVIEWER')));
    await seedReport('CLOSED', { resolution: 'Đã xử lý xong', closedAt: Timestamp.now() });
    await assertFails(notes(RECORDER).add(note('INFO', RECORDER, 'REPORTER')));
  });

  test('không sửa nội dung phản ánh, không xóa phản ánh / ghi chú', async () => {
    await seedFeedback();
    await seedReport('IN_PROGRESS', { handlerUid: REVIEWER });
    await assertFails(db(REVIEWER).doc('feedback_reports/r1').update({
      description: 'Đã sửa lại nội dung', updatedAt: FieldValue.serverTimestamp(),
    }));
    await assertFails(db(RECORDER).doc('feedback_reports/r1').delete());
    const ref = await notes(REVIEWER).add(note('ASK', REVIEWER, 'REVIEWER'));
    await assertFails(db(REVIEWER).doc(ref.path).update({ text: 'sửa' }));
    await assertFails(db(REVIEWER).doc(ref.path).delete());
  });
});

// =====================================================================
// E07 · Nhóm và sự kiện cộng đồng
// =====================================================================

const EVENT = 'ev-1';
const REP2 = 'u-rep-2';             // đại diện của đơn vị ghi nhận (REC_ORG)

async function seedEvent(overrides = {}) {
  await seed();
  await env.withSecurityRulesDisabled(async (ctx) => {
    const d = ctx.firestore();
    await d.doc(`memberships/${REP2}/organizations/${REC_ORG}`)
      .set({ roles: ['REPRESENTATIVE'], active: true, orgId: REC_ORG });
    await d.doc(`community_events/${EVENT}`).set({
      ...eventData(), startAt: at(hours(72)), endAt: at(hours(75)),
      createdAt: Timestamp.now(), updatedAt: Timestamp.now(), ...overrides,
    });
  });
}

function eventData(overrides = {}) {
  return {
    title: 'Ngày hội sức khỏe người cao tuổi', content: 'Đo huyết áp, tư vấn dinh dưỡng, văn nghệ.',
    place: 'Nhà văn hóa Phường 7', area: 'Quận 3', orgId: ORG, orgName: 'Đoàn TN Phường 7',
    createdByUid: REP, createdByName: 'Đại diện Đoàn TN',
    startAt: at(hours(72)), endAt: at(hours(75)), capacity: 10, registeredCount: 0,
    allowCancel: true, openToOtherOrgs: true, cancelled: false,
    createdAt: FieldValue.serverTimestamp(), updatedAt: FieldValue.serverTimestamp(),
    ...overrides,
  };
}

/** Đăng ký / đổi số người như EventRepository.register: phiếu + bộ đếm trong cùng một lần ghi. */
function registerBatch(d, orgId, people, { count, exists = false, before = 0, uid = OUTSIDER } = {}) {
  const b = d.batch();
  const reg = d.doc(`community_events/${EVENT}/registrations/${orgId}`);
  if (exists) {
    b.update(reg, { people, byUid: uid, byName: 'Đại diện', updatedAt: FieldValue.serverTimestamp() });
  } else {
    b.set(reg, {
      orgName: 'Tổ chức', people, byUid: uid, byName: 'Đại diện',
      createdAt: FieldValue.serverTimestamp(), updatedAt: FieldValue.serverTimestamp(),
    });
  }
  b.update(d.doc(`community_events/${EVENT}`), { registeredCount: count, lastRegOrgId: orgId });
  return b.commit();
}

function cancelBatch(d, orgId, count) {
  const b = d.batch();
  b.delete(d.doc(`community_events/${EVENT}/registrations/${orgId}`));
  b.update(d.doc(`community_events/${EVENT}`), { registeredCount: count, lastRegOrgId: orgId });
  return b.commit();
}

async function seedRegistration(orgId, people, eventOverrides = {}) {
  await seedEvent({ registeredCount: people, ...eventOverrides });
  await env.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().doc(`community_events/${EVENT}/registrations/${orgId}`).set({
      orgName: 'Tổ chức', people, byUid: OUTSIDER, byName: 'Đại diện',
      createdAt: Timestamp.now(), updatedAt: Timestamp.now(),
    });
  });
}

describe('E07 · Tạo và hủy sự kiện', () => {
  const events = (uid) => db(uid).collection('community_events');

  test('đại diện tổ chức tạo sự kiện cho tổ chức của mình', async () => {
    await seed();
    await assertSucceeds(events(REP).add(eventData()));
  });

  test('thành viên không phải đại diện, hoặc tạo thay tổ chức khác, đều bị từ chối', async () => {
    await seed();
    await assertFails(events(ASSIGNEE).add(eventData({ createdByUid: ASSIGNEE })));
    await assertFails(events(REP).add(eventData({ orgId: OTHER_ORG })));
    await assertFails(events(REP).add(eventData({ createdByUid: ASSIGNEE })));
  });

  test('giờ phải ở tương lai, kết thúc sau bắt đầu; số chỗ 1–500; chưa ai đăng ký', async () => {
    await seed();
    await assertFails(events(REP).add(eventData({ startAt: at(-hours(1)), endAt: at(hours(2)) })));
    await assertFails(events(REP).add(eventData({ endAt: at(hours(71)) })));
    await assertFails(events(REP).add(eventData({ capacity: 0 })));
    await assertFails(events(REP).add(eventData({ capacity: 501 })));
    await assertFails(events(REP).add(eventData({ registeredCount: 3 })));
    await assertFails(events(REP).add(eventData({ cancelled: true })));
    await assertFails(events(REP).add(eventData({ title: 'Hi' })));
  });

  test('tài khoản đăng nhập xem được sự kiện và danh sách đăng ký', async () => {
    await seedRegistration(OTHER_ORG, 2);
    await assertSucceeds(events(ASSIGNEE).doc(EVENT).get());
    await assertSucceeds(events(RECORDER).where('cancelled', '==', false).get());
    await assertSucceeds(db(RECORDER).collection(`community_events/${EVENT}/registrations`).get());
    await assertFails(env.unauthenticatedContext().firestore().doc(`community_events/${EVENT}`).get());
  });

  test('tổ chức tạo sự kiện hủy được; tổ chức khác không hủy được; đã hủy không mở lại', async () => {
    await seedEvent();
    const cancel = { cancelled: true, updatedAt: FieldValue.serverTimestamp() };
    await assertFails(events(OUTSIDER).doc(EVENT).update(cancel));
    await assertFails(events(ASSIGNEE).doc(EVENT).update(cancel));
    await assertSucceeds(events(REP).doc(EVENT).update(cancel));
    await assertFails(events(REP).doc(EVENT).update({ cancelled: false, updatedAt: FieldValue.serverTimestamp() }));
  });

  test('không sửa thẳng bộ đếm, không xóa sự kiện', async () => {
    await seedEvent();
    await assertFails(events(REP).doc(EVENT).update({ registeredCount: 5, lastRegOrgId: ORG }));
    await assertFails(events(OUTSIDER).doc(EVENT).update({ registeredCount: 5, lastRegOrgId: OTHER_ORG }));
    await assertFails(events(REP).doc(EVENT).delete());
  });
});

describe('E07 · Đăng ký tham gia sự kiện', () => {
  test('đại diện tổ chức khác đăng ký số thành viên; bộ đếm tăng đúng', async () => {
    await seedEvent();
    await assertSucceeds(registerBatch(db(OUTSIDER), OTHER_ORG, 3, { count: 3 }));
  });

  test('đăng ký mà không cập nhật bộ đếm, hoặc bộ đếm sai, bị từ chối', async () => {
    await seedEvent();
    await assertFails(db(OUTSIDER).doc(`community_events/${EVENT}/registrations/${OTHER_ORG}`).set({
      orgName: 'Tổ chức', people: 3, byUid: OUTSIDER, byName: 'Đại diện',
      createdAt: FieldValue.serverTimestamp(), updatedAt: FieldValue.serverTimestamp(),
    }));
    await assertFails(registerBatch(db(OUTSIDER), OTHER_ORG, 3, { count: 1 }));
  });

  test('không vượt quá số chỗ', async () => {
    await seedEvent({ capacity: 4, registeredCount: 3 });
    await assertFails(registerBatch(db(OUTSIDER), OTHER_ORG, 2, { count: 5 }));
    await assertSucceeds(registerBatch(db(OUTSIDER), OTHER_ORG, 1, { count: 4 }));
  });

  test('chỉ đại diện, đăng ký cho đúng tổ chức của mình, tối đa 20 người', async () => {
    await seedEvent();
    await assertFails(registerBatch(db(ASSIGNEE), ORG, 1, { count: 1, uid: ASSIGNEE }));
    await assertFails(registerBatch(db(OUTSIDER), REC_ORG, 1, { count: 1 }));
    await assertFails(registerBatch(db(OUTSIDER), OTHER_ORG, 21, { count: 21 }));
  });

  test('sự kiện chỉ cho tổ chức tạo: tổ chức khác không đăng ký được', async () => {
    await seedEvent({ openToOtherOrgs: false });
    await assertFails(registerBatch(db(OUTSIDER), OTHER_ORG, 1, { count: 1 }));
    await assertSucceeds(registerBatch(db(REP), ORG, 2, { count: 2, uid: REP }));
  });

  test('sự kiện đã hủy hoặc đã bắt đầu thì không đăng ký được', async () => {
    await seedEvent({ cancelled: true });
    await assertFails(registerBatch(db(OUTSIDER), OTHER_ORG, 1, { count: 1 }));
    await seedEvent({ startAt: at(-hours(1)), endAt: at(hours(2)) });
    await assertFails(registerBatch(db(OUTSIDER), OTHER_ORG, 1, { count: 1 }));
  });

  test('tăng số người lúc nào cũng được (còn chỗ); giảm chỉ khi còn hơn 24 giờ', async () => {
    await seedRegistration(OTHER_ORG, 2);
    await assertSucceeds(registerBatch(db(OUTSIDER), OTHER_ORG, 4, { count: 4, exists: true }));
    await assertSucceeds(registerBatch(db(OUTSIDER), OTHER_ORG, 1, { count: 1, exists: true }));
    await seedRegistration(OTHER_ORG, 2, { startAt: at(hours(12)), endAt: at(hours(14)) });
    await assertFails(registerBatch(db(OUTSIDER), OTHER_ORG, 1, { count: 1, exists: true }));
    await assertSucceeds(registerBatch(db(OUTSIDER), OTHER_ORG, 3, { count: 3, exists: true }));
  });

  test('hủy tham gia trả lại chỗ, chỉ trước 24 giờ và khi sự kiện cho hủy', async () => {
    await seedRegistration(OTHER_ORG, 2);
    await assertFails(cancelBatch(db(OUTSIDER), OTHER_ORG, 2));   // không trả lại chỗ
    await assertSucceeds(cancelBatch(db(OUTSIDER), OTHER_ORG, 0));
    await seedRegistration(OTHER_ORG, 2, { allowCancel: false });
    await assertFails(cancelBatch(db(OUTSIDER), OTHER_ORG, 0));
    await seedRegistration(OTHER_ORG, 2, { startAt: at(hours(12)), endAt: at(hours(14)) });
    await assertFails(cancelBatch(db(OUTSIDER), OTHER_ORG, 0));
  });

  test('không hủy hộ tổ chức khác', async () => {
    await seedRegistration(OTHER_ORG, 2);
    await assertFails(cancelBatch(db(REP2), OTHER_ORG, 0));
  });
});
