package com.seniortribe.app.activities;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.snackbar.Snackbar;
import com.seniortribe.app.R;
import com.seniortribe.app.data.EventRepository;
import com.seniortribe.app.models.CommunityEvent;
import com.seniortribe.app.models.EventRegistration;
import com.seniortribe.app.models.Membership;
import com.seniortribe.app.utils.DateTimeUtils;
import com.seniortribe.app.utils.OrgAvatar;
import com.seniortribe.app.utils.StateView;
import com.seniortribe.app.viewmodels.EventDetailViewModel;

/**
 * E07 · Chi tiết sự kiện.
 *  - Ai cũng xem được thông tin, số chỗ và tên các tổ chức đã đăng ký (không có tên các cụ).
 *  - Đại diện tổ chức đăng ký số thành viên tham gia; đổi số người hoặc hủy trước 24 giờ nếu sự kiện cho phép.
 *  - Đại diện của tổ chức tạo sự kiện hủy sự kiện ở menu.
 * Số chỗ được giữ đúng bằng transaction và Security Rules nên không vượt sức chứa khi nhiều tổ chức cùng bấm.
 */
public class EventDetailActivity extends AppCompatActivity {

    public static final String EXTRA_EVENT_ID = "extra_event_id";

    private EventDetailViewModel viewModel;
    private View root, scroll;
    private MaterialToolbar toolbar;
    private MenuItem itemCancelEvent;
    private LinearProgressIndicator progressBusy, progressCapacity;
    private TextView tvTitle, tvAvatar, tvOrg, tvNote, tvWhen, tvWhere, tvWho;
    private TextView tvCapacity, tvRemaining, tvContent, tvRegisterFor, tvPeople, tvRule;
    private LinearLayout layoutRegistrations;
    private MaterialCardView cardRegister;
    private MaterialButton btnMinus, btnPlus, btnPrimary, btnSecondary;
    private View layoutActions, spaceActions;
    private StateView stateView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_event_detail);
        String eventId = getIntent().getStringExtra(EXTRA_EVENT_ID);
        if (eventId == null) {
            finish();
            return;
        }
        bindViews();
        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.inflateMenu(R.menu.menu_event_detail);
        itemCancelEvent = toolbar.getMenu().findItem(R.id.action_cancel_event);
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_cancel_event) {
                confirm(R.string.event_cancel_event_title, R.string.event_cancel_event_message,
                        R.string.event_cancel_event, () -> viewModel.cancelEvent());
                return true;
            }
            return false;
        });
        btnMinus.setOnClickListener(v -> viewModel.changePeople(-1));
        btnPlus.setOnClickListener(v -> viewModel.changePeople(1));

        viewModel = new ViewModelProvider(this).get(EventDetailViewModel.class);
        viewModel.getUi().observe(this, this::render);
        viewModel.getMessages().observe(this, e -> {
            String msg = e.getIfNotHandled();
            if (msg != null) Snackbar.make(root, msg, Snackbar.LENGTH_LONG).show();
        });
        viewModel.start(eventId);
    }

    private void bindViews() {
        root = findViewById(R.id.rootEventDetail);
        toolbar = findViewById(R.id.toolbar);
        progressBusy = findViewById(R.id.progressBusy);
        scroll = findViewById(R.id.scrollEventDetail);
        tvTitle = findViewById(R.id.tvEventTitle);
        tvAvatar = findViewById(R.id.tvOrgAvatar);
        tvOrg = findViewById(R.id.tvEventOrg);
        tvNote = findViewById(R.id.tvEventNote);
        tvWhen = findViewById(R.id.tvEventWhen);
        tvWhere = findViewById(R.id.tvEventWhere);
        tvWho = findViewById(R.id.tvEventWho);
        tvCapacity = findViewById(R.id.tvEventCapacity);
        progressCapacity = findViewById(R.id.progressCapacity);
        tvRemaining = findViewById(R.id.tvEventRemaining);
        layoutRegistrations = findViewById(R.id.layoutRegistrations);
        tvContent = findViewById(R.id.tvEventContent);
        cardRegister = findViewById(R.id.cardRegister);
        tvRegisterFor = findViewById(R.id.tvRegisterFor);
        btnMinus = findViewById(R.id.btnPeopleMinus);
        tvPeople = findViewById(R.id.tvPeople);
        btnPlus = findViewById(R.id.btnPeoplePlus);
        tvRule = findViewById(R.id.tvRegisterRule);
        layoutActions = findViewById(R.id.layoutActions);
        spaceActions = findViewById(R.id.spaceActions);
        btnPrimary = findViewById(R.id.btnPrimary);
        btnSecondary = findViewById(R.id.btnSecondary);
        stateView = new StateView(findViewById(R.id.frameEventDetail));
    }

    // =====================================================================
    // Hiển thị
    // =====================================================================

    private void render(EventDetailViewModel.Ui s) {
        progressBusy.setVisibility(s.busy ? View.VISIBLE : View.GONE);
        CommunityEvent e = s.event;
        View content = scroll;
        if (e == null) {
            content.setVisibility(View.INVISIBLE);
            layoutActions.setVisibility(View.GONE);
            itemCancelEvent.setVisible(false);
            if (s.loading) {
                stateView.showLoading();
            } else {
                stateView.showError(getString(R.string.state_error_title),
                        s.error != null ? s.error : getString(R.string.state_error_title),
                        getString(R.string.action_retry), v -> viewModel.retry());
            }
            return;
        }
        stateView.hide();
        content.setVisibility(View.VISIBLE);
        long now = System.currentTimeMillis();
        Membership me = s.membership;

        tvTitle.setText(e.getTitle());
        tvAvatar.setText(OrgAvatar.initials(e.getOrgName()));
        tvOrg.setText(e.getOrgName());
        tvWhen.setText(DateTimeUtils.formatWeekdayFullDate(e.getStartMillis()) + " · "
                + DateTimeUtils.formatTimeRange(e.getStartMillis(), e.getEndMillis()));
        String place = e.getPlace();
        if (!TextUtils.isEmpty(e.getArea())) place = place + ", " + e.getArea();
        tvWhere.setText(place);
        tvWho.setText(e.isOpenToOtherOrgs()
                ? getString(R.string.event_who_open) : getString(R.string.event_who_own, e.getOrgName()));
        tvContent.setText(e.getContent());

        int registered = (int) e.getRegisteredCount();
        int capacity = (int) e.getCapacity();
        tvCapacity.setText(e.isFull()
                ? getString(R.string.event_capacity_full, registered, capacity)
                : getString(R.string.event_capacity, registered, capacity));
        progressCapacity.setProgress(e.getFillPercent());
        tvRemaining.setText(e.isFull() ? getString(R.string.event_full_note)
                : getString(R.string.event_remaining, (int) e.getRemaining()));
        renderRegistrations(s);

        boolean organizer = EventRepository.isOrganizer(me, e);
        itemCancelEvent.setVisible(organizer && !e.isCancelled() && !e.hasStarted(now));

        // Ghi chú trạng thái
        Integer note = null;
        if (e.isCancelled()) note = R.string.event_cancelled_note;
        else if (e.hasStarted(now)) note = R.string.event_started_note;
        else if (organizer) note = R.string.event_organizer_note;
        tvNote.setVisibility(note != null ? View.VISIBLE : View.GONE);
        if (note != null) tvNote.setText(note);

        renderRegister(s, e, now);
    }

    private void renderRegistrations(EventDetailViewModel.Ui s) {
        layoutRegistrations.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        if (s.registrations.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(R.string.event_no_registration);
            empty.setTextColor(getColor(R.color.text_secondary));
            empty.setTextSize(14);
            layoutRegistrations.addView(empty);
            return;
        }
        for (EventRegistration r : s.registrations) {
            View row = inflater.inflate(R.layout.item_event_registration, layoutRegistrations, false);
            String name = TextUtils.isEmpty(r.getOrgName()) ? r.getOrgId() : r.getOrgName();
            ((TextView) row.findViewById(R.id.tvRegAvatar)).setText(OrgAvatar.initials(name));
            ((TextView) row.findViewById(R.id.tvRegOrg)).setText(name);
            ((TextView) row.findViewById(R.id.tvRegPeople)).setText(r.getPeople() + " người");
            layoutRegistrations.addView(row);
        }
    }

    /** Thẻ đăng ký và nút cuối màn hình cho đại diện tổ chức. */
    private void renderRegister(EventDetailViewModel.Ui s, CommunityEvent e, long now) {
        Membership me = s.membership;
        EventRegistration mine = s.mine();
        boolean can = EventRepository.canRegister(me, e, now);
        if (!can) {
            cardRegister.setVisibility(View.GONE);
            layoutActions.setVisibility(View.GONE);
            // Giải thích vì sao không đăng ký được (nếu không phải do đã hủy / đã bắt đầu)
            if (!e.isCancelled() && !e.hasStarted(now) && me != null && !EventRepository.isOrganizer(me, e)) {
                tvNote.setVisibility(View.VISIBLE);
                tvNote.setText(!me.isRepresentative() ? R.string.event_cannot_register_role : R.string.event_cannot_register_own);
            }
            return;
        }
        cardRegister.setVisibility(View.VISIBLE);
        tvRegisterFor.setText(getString(R.string.event_register_for, me.getOrgName()));
        tvPeople.setText(String.valueOf(s.people));

        boolean canDecrease = e.canCancelRegistration(now);
        int before = mine != null ? (int) mine.getPeople() : 0;
        // Đã quá hạn 24 giờ thì chỉ được tăng, không được giảm
        int min = (mine != null && !canDecrease) ? before : 1;
        int room = (int) e.getRemaining() + before;
        int max = Math.min(EventRepository.MAX_PEOPLE_PER_ORG, Math.max(min, room));
        btnMinus.setEnabled(!s.busy && s.people > min);
        btnPlus.setEnabled(!s.busy && s.people < max);
        tvRule.setText(e.isAllowCancel() ? R.string.event_rule_cancel : R.string.event_rule_no_cancel);

        layoutActions.setVisibility(View.VISIBLE);
        btnPrimary.setOnClickListener(v -> viewModel.register());
        if (mine == null) {
            btnPrimary.setText(R.string.event_action_register);
            btnPrimary.setEnabled(!s.busy && !e.isFull() && s.people <= room);
            btnSecondary.setVisibility(View.GONE);
            spaceActions.setVisibility(View.GONE);
        } else {
            btnPrimary.setText(R.string.event_action_update);
            btnPrimary.setEnabled(!s.busy && s.people != before && s.people <= room && s.people >= min);
            boolean cancellable = canDecrease;
            btnSecondary.setVisibility(cancellable ? View.VISIBLE : View.GONE);
            spaceActions.setVisibility(cancellable ? View.VISIBLE : View.GONE);
            btnSecondary.setText(R.string.event_action_cancel_registration);
            btnSecondary.setEnabled(!s.busy);
            btnSecondary.setOnClickListener(v -> confirm(R.string.event_cancel_registration_title,
                    R.string.event_cancel_registration_message, R.string.event_action_confirm,
                    () -> viewModel.cancelRegistration()));
            tvNote.setVisibility(View.VISIBLE);
            tvNote.setText(getString(R.string.event_registered_note, before));
        }
    }

    private void confirm(int title, int message, int positive, Runnable action) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setMessage(message)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(positive, (d, w) -> action.run())
                .show();
    }
}
