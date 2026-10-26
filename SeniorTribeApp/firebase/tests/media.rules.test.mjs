// Kiểm thử Security Rules cho E06 · Chat nâng cao:
//   - tin ảnh / tệp: tệp chia mảnh trong needs_assignments/{id}/attachments/{attId}/chunks
//   - gọi thoại / video: tín hiệu WebRTC trong needs_assignments/{id}/calls/{callId}
// Chỉ hai người trong luồng chat (R08), chỉ khi lần tiếp nhận còn hiệu lực; không sửa / xóa.
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

const { Timestamp, FieldValue, Blob } = firebase.firestore;

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
const CHUNK = 700000;

let env;

function db(uid) {
  return env.authenticatedContext(uid).firestore();
}

const base = `needs_assignments/${ASSIGN}`;

async function seed({ needStatus = 'ACCEPTED', assignmentStatus = 'ACTIVE', calls = {}, attachments = {} } = {}) {
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
    await d.doc(base).set({
      needId: NEED, recorderOrgId: REC_ORG, orgId: ORG, assigneeUid: ASSIGNEE,
      acceptedByUid: REP, status: assignmentStatus,
    });
    for (const [id, data] of Object.entries(calls)) await d.doc(`${base}/calls/${id}`).set(data);
    for (const [id, data] of Object.entries(attachments)) await d.doc(`${base}/attachments/${id}`).set(data);
  });
}

const bytes = (n) => Blob.fromUint8Array(new Uint8Array(n).fill(7));

/**
 * Gửi tệp như ChatMediaRepository: metadata + mảnh đầu trong một batch, các mảnh sau mỗi mảnh
 * một request, cuối cùng là tin nhắn (tệp một mảnh thì gộp cả tin nhắn vào batch đầu).
 */
async function sendBatch(d, uid, {
  size = 1200, kind = 'IMAGE', mimeType = 'image/jpeg', id = 'att1',
  meta = {}, message = {}, chunks = null, skipMessage = false,
} = {}) {
  const count = Math.ceil(size / CHUNK);
  const list = chunks ?? Array.from({ length: count }, (_, i) => ({
    i, n: i === count - 1 ? size - CHUNK * (count - 1) : CHUNK,
  }));
  const chunkRef = (c) => d.doc(`${base}/attachments/${id}/chunks/${c.id ?? String(c.i)}`);
  const msg = {
    senderUid: uid, senderName: 'Người gửi', text: '', at: FieldValue.serverTimestamp(),
    type: kind, attachmentId: id, fileName: kind === 'IMAGE' ? 'anh.jpg' : 'giay-hen.pdf',
    mimeType, size, thumb: bytes(2000), ...message,
  };

  const first = d.batch();
  first.set(d.doc(`${base}/attachments/${id}`), {
    ownerUid: uid, fileName: kind === 'IMAGE' ? 'anh.jpg' : 'giay-hen.pdf', mimeType, size,
    chunkCount: count, kind, createdAt: FieldValue.serverTimestamp(), ...meta,
  });
  first.set(chunkRef(list[0]), { index: list[0].i, data: bytes(list[0].n) });
  const single = list.length === 1;
  if (single && !skipMessage) first.set(d.collection(`${base}/messages`).doc(), msg);
  await first.commit();
  for (const c of list.slice(1)) {
    await chunkRef(c).set({ index: c.i, data: bytes(c.n) });
  }
  if (!single && !skipMessage) await d.collection(`${base}/messages`).add(msg);
}

const OFFER = { type: 'offer', sdp: 'v=0\r\no=- 1 2 IN IP4 127.0.0.1\r\n' };
const ANSWER = { type: 'answer', sdp: 'v=0\r\no=- 3 4 IN IP4 127.0.0.1\r\n' };

function call(from, to, overrides = {}) {
  return {
    callerUid: from, callerName: 'Người gọi', calleeUid: to, video: true, status: 'RINGING',
    offer: OFFER, createdAt: FieldValue.serverTimestamp(), ...overrides,
  };
}

const ringing = { c1: { ...call(RECORDER, ASSIGNEE), createdAt: Timestamp.now() } };
const ongoing = { c1: { ...call(RECORDER, ASSIGNEE), createdAt: Timestamp.now(), status: 'ACCEPTED', answer: ANSWER } };
const end = (uid, status, extra = {}) => ({
  status, endedByUid: uid, endedAt: FieldValue.serverTimestamp(), ...extra,
});
const candidate = (uid) => ({
  fromUid: uid, candidate: 'candidate:1 1 udp 2122260223 192.168.1.2 54321 typ host',
  sdpMid: '0', sdpMLineIndex: 0, at: FieldValue.serverTimestamp(),
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

describe('E06 · Gửi ảnh / tệp trong chat', () => {
  test('đầu mối gửi ảnh, người được giao gửi tệp PDF', async () => {
    await seed();
    await assertSucceeds(sendBatch(db(RECORDER), RECORDER));
    await assertSucceeds(sendBatch(db(ASSIGNEE), ASSIGNEE,
      { id: 'att2', kind: 'FILE', mimeType: 'application/pdf', size: 90000 }));
  });

  test('tệp 5 MB (8 mảnh) gửi được trong một lần', async () => {
    await seed();
    await assertSucceeds(sendBatch(db(ASSIGNEE), ASSIGNEE,
      { kind: 'FILE', mimeType: 'application/pdf', size: 5242880 }));
  });

  test('tệp quá 5 MB bị từ chối', async () => {
    await seed();
    await assertFails(sendBatch(db(ASSIGNEE), ASSIGNEE,
      { kind: 'FILE', mimeType: 'application/pdf', size: 5242881 }));
  });

  test('chưa tải xong mảnh cuối thì chưa gửi được tin nhắn', async () => {
    await seed();
    // Tệp 2 mảnh nhưng chỉ có mảnh đầu khi gửi tin
    await assertFails(sendBatch(db(ASSIGNEE), ASSIGNEE, {
      kind: 'FILE', mimeType: 'application/pdf', size: 1000000, chunks: [{ i: 0, n: CHUNK }],
    }));
  });

  test('số mảnh phải khớp kích thước', async () => {
    await seed();
    await assertFails(sendBatch(db(RECORDER), RECORDER, { size: 1200, meta: { chunkCount: 3 } }));
  });

  test('mảnh quá 700 000 byte, sai số thứ tự hoặc sai tên mảnh bị từ chối', async () => {
    await seed();
    await assertFails(sendBatch(db(RECORDER), RECORDER, { size: 1200, chunks: [{ i: 0, n: 700001 }] }));
    await assertFails(sendBatch(db(RECORDER), RECORDER, { size: 1200, chunks: [{ i: 1, n: 1200 }] }));
    await assertFails(sendBatch(db(RECORDER), RECORDER, { size: 1200, chunks: [{ i: 0, n: 1200, id: 'x' }] }));
  });

  test('tin ảnh phải là kiểu ảnh và khớp tệp đính kèm', async () => {
    await seed();
    await assertFails(sendBatch(db(RECORDER), RECORDER, { mimeType: 'application/pdf' }));
    await assertFails(sendBatch(db(RECORDER), RECORDER, { message: { size: 999 } }));
    await assertFails(sendBatch(db(RECORDER), RECORDER, { message: { type: 'FILE' } }));
  });

  test('không gửi tin trỏ vào tệp của người khác', async () => {
    await seed({
      attachments: {
        other: {
          ownerUid: ASSIGNEE, fileName: 'anh.jpg', mimeType: 'image/jpeg', size: 1200,
          chunkCount: 1, kind: 'IMAGE', createdAt: Timestamp.now(),
        },
      },
    });
    const d = db(RECORDER);
    await assertFails(d.collection(`${base}/messages`).add({
      senderUid: RECORDER, senderName: 'Mai', text: '', at: FieldValue.serverTimestamp(),
      type: 'IMAGE', attachmentId: 'other', fileName: 'anh.jpg', mimeType: 'image/jpeg', size: 1200,
    }));
  });

  test('không thêm mảnh vào tệp của người khác', async () => {
    await seed({
      attachments: {
        other: {
          ownerUid: ASSIGNEE, fileName: 'anh.jpg', mimeType: 'image/jpeg', size: 1400000,
          chunkCount: 2, kind: 'IMAGE', createdAt: Timestamp.now(),
        },
      },
    });
    await assertFails(db(RECORDER).doc(`${base}/attachments/other/chunks/1`).set({ index: 1, data: bytes(10) }));
  });

  test('ảnh xem trước (thumb) tối đa 64 KB', async () => {
    await seed();
    await assertFails(sendBatch(db(RECORDER), RECORDER, { message: { thumb: bytes(65537) } }));
  });

  test('thành viên khác của tổ chức và tổ chức ngoài không gửi, không đọc được (R08)', async () => {
    await seed();
    await assertFails(sendBatch(db(REP), REP));
    await assertFails(sendBatch(db(OUTSIDER), OUTSIDER));
    await sendBatch(db(RECORDER), RECORDER);
    await assertFails(db(REP).doc(`${base}/attachments/att1`).get());
    await assertFails(db(REP).collection(`${base}/attachments/att1/chunks`).get());
    await assertFails(db(RECORDER2).collection(`${base}/attachments/att1/chunks`).get());
    await assertSucceeds(db(ASSIGNEE).collection(`${base}/attachments/att1/chunks`).get());
  });

  test('lần tiếp nhận đã kết thúc: vẫn xem được tệp cũ nhưng không gửi được', async () => {
    await seed();
    await sendBatch(db(RECORDER), RECORDER);
    await seed({ needStatus: 'OPEN', assignmentStatus: 'RETURNED' });
    await assertSucceeds(db(RECORDER).doc(`${base}/attachments/att1/chunks/0`).get());
    await assertFails(sendBatch(db(RECORDER), RECORDER, { id: 'att2' }));
  });

  test('không sửa, không xóa tệp đã gửi', async () => {
    await seed();
    await sendBatch(db(RECORDER), RECORDER);
    await assertFails(db(RECORDER).doc(`${base}/attachments/att1/chunks/0`).set({ index: 0, data: bytes(5) }));
    await assertFails(db(RECORDER).doc(`${base}/attachments/att1/chunks/0`).delete());
    await assertFails(db(RECORDER).doc(`${base}/attachments/att1`).update({ fileName: 'khac.jpg' }));
  });

  test('tin văn bản vẫn gửi được, có hoặc không có type TEXT', async () => {
    await seed();
    const m = { senderUid: RECORDER, senderName: 'Mai', text: 'Chào em', at: FieldValue.serverTimestamp() };
    await assertSucceeds(db(RECORDER).collection(`${base}/messages`).add(m));
    await assertSucceeds(db(RECORDER).collection(`${base}/messages`).add({ ...m, type: 'TEXT' }));
    await assertFails(db(RECORDER).collection(`${base}/messages`).add({ ...m, type: 'SYSTEM' }));
  });
});

describe('E06 · Gọi thoại / video', () => {
  const calls = (uid) => db(uid).collection(`${base}/calls`);
  const c1 = (uid) => db(uid).doc(`${base}/calls/c1`);

  test('hai người trong chat gọi cho nhau', async () => {
    await seed();
    await assertSucceeds(calls(RECORDER).add(call(RECORDER, ASSIGNEE)));
    await assertSucceeds(calls(ASSIGNEE).add(call(ASSIGNEE, RECORDER, { video: false })));
  });

  test('không gọi cho người ngoài luồng chat hoặc tự gọi mình', async () => {
    await seed();
    await assertFails(calls(RECORDER).add(call(RECORDER, REP)));
    await assertFails(calls(RECORDER).add(call(RECORDER, RECORDER)));
    await assertFails(calls(REP).add(call(REP, RECORDER)));
    await assertFails(calls(OUTSIDER).add(call(OUTSIDER, RECORDER)));
  });

  test('không giả danh người gọi, phải có offer hợp lệ', async () => {
    await seed();
    await assertFails(calls(RECORDER).add(call(ASSIGNEE, RECORDER)));
    await assertFails(calls(RECORDER).add(call(RECORDER, ASSIGNEE, { offer: { type: 'answer', sdp: 'x' } })));
    await assertFails(calls(RECORDER).add(call(RECORDER, ASSIGNEE, { status: 'ACCEPTED' })));
  });

  test('lần tiếp nhận đã kết thúc thì không gọi được', async () => {
    await seed({ needStatus: 'COMPLETED', assignmentStatus: 'CLOSED' });
    await assertFails(calls(RECORDER).add(call(RECORDER, ASSIGNEE)));
  });

  test('chỉ người được gọi nghe máy', async () => {
    await seed({ calls: ringing });
    const answer = { status: 'ACCEPTED', answer: ANSWER, answeredAt: FieldValue.serverTimestamp() };
    await assertFails(c1(RECORDER).update(answer));
    await assertFails(c1(REP).update(answer));
    await assertSucceeds(c1(ASSIGNEE).update(answer));
  });

  test('người được gọi từ chối; người gọi hủy hoặc báo không ai nghe', async () => {
    await seed({ calls: ringing });
    await assertFails(c1(RECORDER).update(end(RECORDER, 'DECLINED')));
    await assertSucceeds(c1(ASSIGNEE).update(end(ASSIGNEE, 'DECLINED')));
    await seed({ calls: ringing });
    await assertFails(c1(ASSIGNEE).update(end(ASSIGNEE, 'MISSED')));
    await assertSucceeds(c1(RECORDER).update(end(RECORDER, 'MISSED')));
  });

  test('một trong hai bên kết thúc cuộc gọi đang diễn ra', async () => {
    await seed({ calls: ongoing });
    await assertFails(c1(REP).update(end(REP, 'ENDED')));
    await assertFails(c1(ASSIGNEE).update(end(ASSIGNEE, 'ENDED', { durationSec: -1 })));
    await assertSucceeds(c1(ASSIGNEE).update(end(ASSIGNEE, 'ENDED', { durationSec: 192 })));
  });

  test('không đổi người gọi / người nhận, không xóa cuộc gọi', async () => {
    await seed({ calls: ongoing });
    await assertFails(c1(RECORDER).update({ calleeUid: REP }));
    await assertFails(c1(RECORDER).delete());
  });

  test('ICE candidate: chỉ hai bên của cuộc gọi, khi cuộc gọi còn diễn ra', async () => {
    await seed({ calls: ongoing });
    const cand = (uid) => db(uid).collection(`${base}/calls/c1/candidates`);
    await assertSucceeds(cand(RECORDER).add(candidate(RECORDER)));
    await assertSucceeds(cand(ASSIGNEE).add(candidate(ASSIGNEE)));
    await assertFails(cand(RECORDER).add(candidate(ASSIGNEE)));
    await assertFails(cand(REP).add(candidate(REP)));
    await assertFails(cand(REP).get());
    await assertSucceeds(cand(ASSIGNEE).get());
    await seed({ calls: { c1: { ...ongoing.c1, status: 'ENDED' } } });
    await assertFails(cand(RECORDER).add(candidate(RECORDER)));
  });
});
