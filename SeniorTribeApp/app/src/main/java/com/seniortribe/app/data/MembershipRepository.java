package com.seniortribe.app.data;

import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.Source;
import com.seniortribe.app.models.Membership;
import com.seniortribe.app.models.OrgMember;
import com.seniortribe.app.utils.Constants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Đọc quyền của tài khoản hiện tại theo tổ chức.
 *
 * Schema (do quản trị tạo sẵn, ứng dụng chỉ đọc):
 *   memberships/{uid}/organizations/{orgId} { orgName, roles: [..], active: true }
 *   organizations/{orgId}/members/{uid} { displayName, roles: [..], active: true }
 *
 * MVP: nếu một tài khoản thuộc nhiều tổ chức thì dùng tổ chức đầu tiên (theo orgId).
 */
public final class MembershipRepository {
    private static MembershipRepository instance;

    private final FirebaseAuth auth;
    private final FirebaseFirestore db;

    private Membership cached;
    private long generation;

    private MembershipRepository() {
        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
    }

    public static synchronized MembershipRepository get() {
        if (instance == null) instance = new MembershipRepository();
        return instance;
    }

    /** Gọi khi đăng xuất để không dùng lại quyền của tài khoản cũ. */
    public void clear() {
        ++generation;
        cached = null;
    }

    /**
     * Lấy quyền hiện tại.
     * Kết quả null nghĩa là tài khoản chưa được cấp quyền tổ chức nào.
     */
    public Task<Membership> loadCurrent(boolean forceRefresh) {
        FirebaseUser user = auth.getCurrentUser();

        if (user == null) {
            return Tasks.forException(new NeedActionException(
                    "Phiên đăng nhập đã hết. Vui lòng đăng nhập lại."
            ));
        }

        final String uid = user.getUid();
        final long request = generation;

        if (!forceRefresh && cached != null && uid.equals(cached.getUid())) {
            return Tasks.forResult(cached);
        }

        final String displayName = displayNameOf(user);

        return db.collection(Constants.COL_MEMBERSHIPS)
                .document(uid)
                .collection(Constants.SUB_ORGANIZATIONS)
                .get(Source.SERVER)
                .continueWith(task -> {
                    requireCurrent(uid, request);

                    if (!task.isSuccessful()) {
                        Exception e = task.getException();
                        throw e != null
                                ? e
                                : new NeedActionException("Không đọc được quyền tài khoản.");
                    }

                    Membership picked = pickActive(uid, displayName, task.getResult());
                    cached = picked;
                    return picked;
                });
    }

    @Nullable
    private static Membership pickActive(
            String uid,
            String displayName,
            QuerySnapshot snapshot
    ) {
        List<DocumentSnapshot> docs = new ArrayList<>(snapshot.getDocuments());
        Collections.sort(docs, (a, b) -> a.getId().compareTo(b.getId()));

        for (DocumentSnapshot doc : docs) {
            if (!Boolean.TRUE.equals(doc.getBoolean("active"))) continue;

            List<String> roles = toStringList(doc.get("roles"));
            if (roles.isEmpty()) continue;

            String orgName = doc.getString("orgName");
            if (TextUtils.isEmpty(orgName)) orgName = doc.getId();

            return new Membership(uid, displayName, doc.getId(), orgName, roles);
        }

        return null;
    }

    /** Danh sách đại diện của một tổ chức, để chọn người phụ trách khi tiếp nhận. */
    public Task<List<OrgMember>> loadRepresentatives(String orgId) {
        FirebaseUser user = auth.getCurrentUser();

        if (user == null) {
            return Tasks.forException(new NeedActionException(
                    "Phiên đăng nhập đã hết. Vui lòng đăng nhập lại."
            ));
        }

        final String uid = user.getUid();
        final long request = generation;

        return db.collection(Constants.COL_ORGANIZATIONS)
                .document(orgId)
                .collection(Constants.SUB_MEMBERS)
                .whereArrayContains("roles", Membership.ROLE_REPRESENTATIVE)
                .get(Source.SERVER)
                .continueWith(task -> {
                    requireCurrent(uid, request);

                    if (!task.isSuccessful()) {
                        Exception e = task.getException();
                        throw e != null
                                ? e
                                : new NeedActionException("Không tải được danh sách đại diện.");
                    }

                    List<OrgMember> result = new ArrayList<>();

                    for (DocumentSnapshot doc : task.getResult().getDocuments()) {
                        if (Boolean.FALSE.equals(doc.getBoolean("active"))) continue;

                        String name = doc.getString("displayName");
                        result.add(new OrgMember(
                                doc.getId(),
                                TextUtils.isEmpty(name) ? doc.getId() : name
                        ));
                    }

                    return result;
                });
    }

    private void requireCurrent(String uid, long request) throws NeedActionException {
        FirebaseUser user = auth.getCurrentUser();

        if (request != generation || user == null || !uid.equals(user.getUid())) {
            throw new NeedActionException(
                    "Quyền tài khoản đã thay đổi. Vui lòng thử lại."
            );
        }
    }

    private static List<String> toStringList(Object value) {
        List<String> out = new ArrayList<>();

        if (value instanceof List) {
            for (Object o : (List<?>) value) {
                if (o instanceof String) out.add((String) o);
            }
        }

        return out;
    }

    private static String displayNameOf(FirebaseUser user) {
        if (!TextUtils.isEmpty(user.getDisplayName())) return user.getDisplayName();

        String email = user.getEmail();
        if (!TextUtils.isEmpty(email) && email.contains("@")) {
            return email.substring(0, email.indexOf('@'));
        }

        return "Thành viên";
    }
}