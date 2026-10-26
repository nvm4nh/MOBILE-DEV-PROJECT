package com.seniortribe.app.data;

import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.seniortribe.app.models.CommunityEvent;
import com.seniortribe.app.models.EventForm;
import com.seniortribe.app.models.EventRegistration;
import com.seniortribe.app.models.Membership;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * E07 · Nhóm và sự kiện: hoạt động nhiều người, đăng ký / hủy tham gia, quản lý sức chứa.
 *
 * Firestore:
 *   community_events/{eventId}                         sự kiện của một tổ chức
 *   community_events/{eventId}/registrations/{orgId}   phiếu đăng ký của mỗi tổ chức (số người)
 * Đăng ký, đổi số người và hủy đều chạy trong transaction, cập nhật registeredCount cùng lúc
 * nên không vượt sức chứa khi nhiều tổ chức đăng ký cùng lúc. Security Rules kiểm tra lại.
 */
public class EventRepository {

    public static final String COL_EVENTS = "community_events";
    public static final String SUB_REGISTRATIONS = "registrations";

    public static final int MAX_CAPACITY = 500;
    public static final int MAX_PEOPLE_PER_ORG = 20;

    private final FirebaseFirestore db;

    public EventRepository() {
        this(FirebaseFirestore.getInstance());
    }

    public EventRepository(FirebaseFirestore db) {
        this.db = db;
    }

    // =====================================================================
    // Đọc
    // =====================================================================

    /** Sự kiện chưa hủy và chưa kết thúc, sắp xếp theo giờ bắt đầu (sắp xếp ở máy, không cần index). */
    public ListenerRegistration listenUpcoming(NeedRepository.Callback<List<CommunityEvent>> callback) {
        return db.collection(COL_EVENTS).whereEqualTo("cancelled", false)
                .addSnapshotListener((snap, error) -> {
                    if (error != null) {
                        callback.onError(error);
                        return;
                    }
                    if (snap == null) return;
                    long now = System.currentTimeMillis();
                    List<CommunityEvent> list = new ArrayList<>();
                    for (DocumentSnapshot doc : snap.getDocuments()) {
                        CommunityEvent e = doc.toObject(CommunityEvent.class);
                        if (e != null && !e.hasEnded(now)) list.add(e);
                    }
                    Collections.sort(list, (a, b) -> Long.compare(a.getStartMillis(), b.getStartMillis()));
                    callback.onData(list, snap.getMetadata().isFromCache());
                });
    }

    public ListenerRegistration listenEvent(String eventId, NeedRepository.Callback<CommunityEvent> callback) {
        return eventRef(eventId).addSnapshotListener((snap, error) -> {
            if (error != null) {
                callback.onError(error);
                return;
            }
            if (snap == null || !snap.exists()) {
                callback.onError(new NeedActionException("Không tìm thấy sự kiện."));
                return;
            }
            callback.onData(snap.toObject(CommunityEvent.class), snap.getMetadata().isFromCache());
        });
    }

    public ListenerRegistration listenRegistrations(String eventId,
                                                    NeedRepository.Callback<List<EventRegistration>> callback) {
        return eventRef(eventId).collection(SUB_REGISTRATIONS).addSnapshotListener((snap, error) -> {
            if (error != null) {
                callback.onError(error);
                return;
            }
            if (snap == null) return;
            List<EventRegistration> list = new ArrayList<>();
            for (DocumentSnapshot doc : snap.getDocuments()) {
                EventRegistration r = doc.toObject(EventRegistration.class);
                if (r != null) list.add(r);
            }
            Collections.sort(list, (a, b) -> Long.compare(time(a.getCreatedAt()), time(b.getCreatedAt())));
            callback.onData(list, snap.getMetadata().isFromCache());
        });
    }

    // =====================================================================
    // Tạo / hủy sự kiện (đại diện tổ chức)
    // =====================================================================

    /** @return id sự kiện vừa tạo */
    public Task<String> createEvent(Membership me, EventForm form) {
        if (me == null || !me.isRepresentative()) {
            return Tasks.forException(new NeedActionException("Chỉ đại diện tổ chức mới tạo được sự kiện."));
        }
        String error = validate(form, System.currentTimeMillis());
        if (error != null) return Tasks.forException(new NeedActionException(error));

        Map<String, Object> data = new HashMap<>();
        data.put("title", form.title.trim());
        data.put("content", form.content.trim());
        data.put("place", form.place.trim());
        data.put("area", form.area != null ? form.area.trim() : "");
        data.put("orgId", me.getOrgId());
        data.put("orgName", me.getOrgName());
        data.put("createdByUid", me.getUid());
        data.put("createdByName", me.getDisplayName());
        data.put("startAt", new Date(form.startAt));
        data.put("endAt", new Date(form.endAt));
        data.put("capacity", form.capacity);
        data.put("registeredCount", 0);
        data.put("allowCancel", form.allowCancel);
        data.put("openToOtherOrgs", form.openToOtherOrgs);
        data.put("cancelled", false);
        data.put("createdAt", FieldValue.serverTimestamp());
        data.put("updatedAt", FieldValue.serverTimestamp());

        final DocumentReference ref = db.collection(COL_EVENTS).document();
        return ref.set(data).continueWith(task -> {
            if (!task.isSuccessful()) throw errorOf(task.getException());
            return ref.getId();
        });
    }

    /** Kiểm tra form; trả về câu báo lỗi hoặc null nếu hợp lệ. Dùng chung cho màn hình và repository. */
    @Nullable
    public static String validate(EventForm f, long now) {
        if (f == null) return "Thiếu thông tin sự kiện.";
        if (f.title == null || f.title.trim().length() < 5) return "Tên sự kiện cần ít nhất 5 ký tự.";
        if (f.title.trim().length() > 120) return "Tên sự kiện dài quá 120 ký tự.";
        if (f.startAt <= now) return "Giờ bắt đầu phải ở sau thời điểm hiện tại.";
        if (f.endAt <= f.startAt) return "Giờ kết thúc phải sau giờ bắt đầu.";
        if (f.place == null || f.place.trim().length() < 2) return "Hãy ghi địa điểm.";
        if (f.place.trim().length() > 160) return "Địa điểm dài quá 160 ký tự.";
        if (f.area != null && f.area.trim().length() > 120) return "Khu vực dài quá 120 ký tự.";
        if (f.capacity < 1 || f.capacity > MAX_CAPACITY) return "Số chỗ phải từ 1 đến " + MAX_CAPACITY + ".";
        if (f.content == null || f.content.trim().length() < 10) return "Nội dung cần ít nhất 10 ký tự.";
        if (f.content.trim().length() > 2000) return "Nội dung dài quá 2000 ký tự.";
        return null;
    }

    public static boolean isOrganizer(@Nullable Membership me, @Nullable CommunityEvent e) {
        return me != null && e != null && me.isRepresentative() && me.getOrgId().equals(e.getOrgId());
    }

    public Task<Void> cancelEvent(CommunityEvent e, Membership me) {
        if (!isOrganizer(me, e)) {
            return Tasks.forException(new NeedActionException("Chỉ đại diện của tổ chức tạo sự kiện mới hủy được."));
        }
        Map<String, Object> update = new HashMap<>();
        update.put("cancelled", true);
        update.put("updatedAt", FieldValue.serverTimestamp());
        return eventRef(e.getEventId()).update(update);
    }

    // =====================================================================
    // Đăng ký tham gia (đại diện tổ chức)
    // =====================================================================

    /** Tổ chức của tài khoản này có được đăng ký sự kiện không (chưa xét số chỗ). */
    public static boolean canRegister(@Nullable Membership me, @Nullable CommunityEvent e, long now) {
        return me != null && e != null && me.isRepresentative() && !e.isCancelled() && !e.hasStarted(now)
                && (e.isOpenToOtherOrgs() || me.getOrgId().equals(e.getOrgId()));
    }

    /**
     * Đăng ký mới hoặc đổi số người của tổ chức mình. Đọc sự kiện và phiếu cũ trong transaction,
     * tính lại registeredCount và chặn khi vượt sức chứa.
     */
    public Task<Void> register(String eventId, Membership me, int people) {
        if (me == null || !me.isRepresentative()) {
            return Tasks.forException(new NeedActionException("Chỉ đại diện tổ chức mới đăng ký được."));
        }
        if (people < 1 || people > MAX_PEOPLE_PER_ORG) {
            return Tasks.forException(new NeedActionException(
                    "Số thành viên phải từ 1 đến " + MAX_PEOPLE_PER_ORG + "."));
        }
        final DocumentReference evRef = eventRef(eventId);
        final DocumentReference regRef = evRef.collection(SUB_REGISTRATIONS).document(me.getOrgId());
        return db.runTransaction(tx -> {
            DocumentSnapshot evSnap = tx.get(evRef);
            DocumentSnapshot regSnap = tx.get(regRef);
            if (!evSnap.exists()) throw new NeedActionException("Không tìm thấy sự kiện.");
            CommunityEvent ev = evSnap.toObject(CommunityEvent.class);
            long now = System.currentTimeMillis();
            if (!canRegister(me, ev, now)) {
                throw new NeedActionException(ev != null && ev.isCancelled()
                        ? "Sự kiện đã bị hủy."
                        : ev != null && ev.hasStarted(now)
                        ? "Sự kiện đã bắt đầu, không đăng ký thêm được."
                        : "Sự kiện này chỉ dành cho thành viên của tổ chức tạo sự kiện.");
            }
            long before = 0;
            if (regSnap.exists()) {
                Long p = regSnap.getLong("people");
                before = p != null ? p : 0;
                if (people < before && !ev.canCancelRegistration(now)) {
                    throw new NeedActionException("Đã quá hạn giảm số người (trước 24 giờ).");
                }
            }
            long count = ev.getRegisteredCount() - before + people;
            if (people > before && count > ev.getCapacity()) {
                long left = Math.max(0, ev.getCapacity() - (ev.getRegisteredCount() - before));
                throw new NeedActionException(left == 0
                        ? "Sự kiện đã đủ chỗ."
                        : "Chỉ còn " + left + " chỗ cho tổ chức của bạn.");
            }
            if (regSnap.exists()) {
                Map<String, Object> update = new HashMap<>();
                update.put("people", people);
                update.put("byUid", me.getUid());
                update.put("byName", me.getDisplayName());
                update.put("updatedAt", FieldValue.serverTimestamp());
                tx.update(regRef, update);
            } else {
                Map<String, Object> reg = new HashMap<>();
                reg.put("orgName", me.getOrgName());
                reg.put("people", people);
                reg.put("byUid", me.getUid());
                reg.put("byName", me.getDisplayName());
                reg.put("createdAt", FieldValue.serverTimestamp());
                reg.put("updatedAt", FieldValue.serverTimestamp());
                tx.set(regRef, reg);
            }
            Map<String, Object> evUpdate = new HashMap<>();
            evUpdate.put("registeredCount", count);
            evUpdate.put("lastRegOrgId", me.getOrgId());
            tx.update(evRef, evUpdate);
            return null;
        });
    }

    /** Hủy tham gia: xóa phiếu của tổ chức mình và trả lại số chỗ (chỉ khi còn hơn 24 giờ). */
    public Task<Void> cancelRegistration(String eventId, Membership me) {
        if (me == null || !me.isRepresentative()) {
            return Tasks.forException(new NeedActionException("Chỉ đại diện tổ chức mới hủy đăng ký được."));
        }
        final DocumentReference evRef = eventRef(eventId);
        final DocumentReference regRef = evRef.collection(SUB_REGISTRATIONS).document(me.getOrgId());
        return db.runTransaction(tx -> {
            DocumentSnapshot evSnap = tx.get(evRef);
            DocumentSnapshot regSnap = tx.get(regRef);
            if (!evSnap.exists()) throw new NeedActionException("Không tìm thấy sự kiện.");
            if (!regSnap.exists()) throw new NeedActionException("Tổ chức của bạn chưa đăng ký sự kiện này.");
            CommunityEvent ev = evSnap.toObject(CommunityEvent.class);
            if (ev == null || !ev.canCancelRegistration(System.currentTimeMillis())) {
                throw new NeedActionException(ev != null && !ev.isAllowCancel()
                        ? "Sự kiện này không cho hủy trong ứng dụng. Hãy liên hệ tổ chức tạo sự kiện."
                        : "Chỉ hủy được trước giờ bắt đầu ít nhất 24 giờ.");
            }
            Long p = regSnap.getLong("people");
            long people = p != null ? p : 0;
            tx.delete(regRef);
            Map<String, Object> evUpdate = new HashMap<>();
            evUpdate.put("registeredCount", Math.max(0, ev.getRegisteredCount() - people));
            evUpdate.put("lastRegOrgId", me.getOrgId());
            tx.update(evRef, evUpdate);
            return null;
        });
    }

    // =====================================================================
    // Hàm hỗ trợ
    // =====================================================================

    private DocumentReference eventRef(String eventId) {
        return db.collection(COL_EVENTS).document(eventId);
    }

    private static long time(@Nullable Date d) {
        return d != null ? d.getTime() : Long.MAX_VALUE;
    }

    private static Exception errorOf(@Nullable Exception e) {
        return e != null ? e : new NeedActionException("Không kết nối được máy chủ. Vui lòng thử lại.");
    }

    /** Dòng "Hội CTĐ Quận 3 · 2 người" cho danh sách tổ chức đã đăng ký. */
    public static String describe(EventRegistration r) {
        String name = TextUtils.isEmpty(r.getOrgName()) ? r.getOrgId() : r.getOrgName();
        return name + " · " + r.getPeople() + " người";
    }
}
