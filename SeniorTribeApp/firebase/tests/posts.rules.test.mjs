// Kiểm thử Security Rules cho bảng tin tổ chức:
//   F04 · xem bài đăng (posts)
//   F05 · đại diện đăng bài nhân danh tổ chức mình
// Chạy: npm test (Firebase Emulator, không đụng tới project thật).

import { readFileSync } from 'node:fs';
import { after, before, beforeEach, describe, test } from 'node:test';
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from '@firebase/rules-unit-testing';
import 'firebase/compat/firestore';

const ORG = 'org-doan-tn-p7';
const ORG_NAME = 'Đoàn TN Phường 7';
const OTHER_ORG = 'org-khac';

const REP = 'u-rep';                // đại diện tổ chức (REPRESENTATIVE)
const RECORDER = 'u-recorder';      // cùng tổ chức nhưng chỉ là đầu mối ghi nhận
const INACTIVE = 'u-inactive';      // đại diện đã bị tắt quyền
const OUTSIDER = 'u-outsider';      // đại diện tổ chức khác
const NO_ORG = 'u-no-org';          // tài khoản chưa được cấp quyền

let env;

function db(uid) {
  return env.authenticatedContext(uid).firestore();
}

function post(overrides = {}) {
  return {
    orgId: ORG,
    authorOrg: ORG_NAME,
    authorUid: REP,
    title: 'Ngày hội sức khỏe cho người cao tuổi',
    content: 'Đoàn TN Phường 7 tổ chức đo huyết áp miễn phí sáng chủ nhật.',
    createdAt: Date.now(),
    ...overrides,
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
  await env.withSecurityRulesDisabled(async (ctx) => {
    const d = ctx.firestore();
    const member = (uid, orgId, orgName, roles, active = true) =>
      d.doc(`memberships/${uid}/organizations/${orgId}`).set({ roles, active, orgId, orgName });
    await member(REP, ORG, ORG_NAME, ['REPRESENTATIVE']);
    await member(RECORDER, ORG, ORG_NAME, ['RECORDER']);
    await member(INACTIVE, ORG, ORG_NAME, ['REPRESENTATIVE'], false);
    await member(OUTSIDER, OTHER_ORG, 'Tổ chức khác', ['REPRESENTATIVE']);
    await d.doc('posts/p1').set(post());
  });
});

describe('F04 · Xem bảng tin', () => {
  test('tài khoản đã đăng nhập đọc được danh sách và chi tiết bài', async () => {
    await assertSucceeds(db(NO_ORG).collection('posts').orderBy('createdAt', 'desc').get());
    await assertSucceeds(db(OUTSIDER).doc('posts/p1').get());
  });

  test('chưa đăng nhập thì không đọc được', async () => {
    await assertFails(env.unauthenticatedContext().firestore().collection('posts').get());
  });
});

describe('F05 · Đăng bài nhân danh tổ chức', () => {
  test('đại diện đăng được bài của tổ chức mình', async () => {
    await assertSucceeds(db(REP).collection('posts').add(post()));
  });

  test('thành viên không phải đại diện không đăng được', async () => {
    await assertFails(db(RECORDER).collection('posts').add(post({ authorUid: RECORDER })));
  });

  test('đại diện đã bị tắt quyền không đăng được', async () => {
    await assertFails(db(INACTIVE).collection('posts').add(post({ authorUid: INACTIVE })));
  });

  test('đại diện tổ chức khác không đăng nhân danh tổ chức này', async () => {
    await assertFails(db(OUTSIDER).collection('posts').add(post({ authorUid: OUTSIDER })));
  });

  test('tài khoản chưa có quyền tổ chức không đăng được', async () => {
    await assertFails(db(NO_ORG).collection('posts').add(post({ authorUid: NO_ORG })));
  });

  test('không giả danh người đăng hoặc tên tổ chức', async () => {
    await assertFails(db(REP).collection('posts').add(post({ authorUid: RECORDER })));
    await assertFails(db(REP).collection('posts').add(post({ authorOrg: 'Hội Chữ thập đỏ TP.HCM' })));
  });

  test('tiêu đề hoặc nội dung rỗng thì bị từ chối', async () => {
    await assertFails(db(REP).collection('posts').add(post({ title: '' })));
    await assertFails(db(REP).collection('posts').add(post({ content: '' })));
  });

  test('không chèn thêm field (ví dụ likesCount)', async () => {
    await assertFails(db(REP).collection('posts').add(post({ likesCount: 999 })));
  });

  test('chưa cho sửa hoặc xóa bài', async () => {
    await assertFails(db(REP).doc('posts/p1').update({ title: 'Tiêu đề mới' }));
    await assertFails(db(REP).doc('posts/p1').delete());
  });
});
