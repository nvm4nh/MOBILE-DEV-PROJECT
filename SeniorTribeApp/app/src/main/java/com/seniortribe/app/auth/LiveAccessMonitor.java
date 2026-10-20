package com.seniortribe.app.auth;

import java.util.List;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.TimeoutException;

/** Called on the main thread. No Android/Firebase dependency. */
public final class LiveAccessMonitor {
    public static final long CHECK_TIMEOUT_MS = 10_000L;

    public enum Stage {
        STOPPED, CHECKING, GRANTED, RECHECK, PENDING, ERROR, SIGNED_OUT
    }

    public static final class State {
        public final Stage stage;
        public final String uid;
        public final Throwable error;

        private State(Stage stage, String uid, Throwable error) {
            this.stage = stage;
            this.uid = uid;
            this.error = error;
        }
    }

    public static final class Grant {
        public final String id;
        public final Object active, roles;

        public Grant(String id, Object active, Object roles) {
            this.id = id;
            this.active = active;
            this.roles = roles;
        }
    }

    public interface Handle {
        void remove();
    }

    public interface Observer {
        void snapshot(boolean fromCache, boolean pendingWrites, List<Grant> grants);
        void error(Throwable error);
    }

    public interface Source {
        String currentUid();
        Handle listen(String uid, Observer observer);
    }

    public interface Scheduler {
        Handle after(long milliseconds, Runnable action);
    }

    public interface Listener {
        void changed(State state);
    }

    private final Source source;
    private final Scheduler scheduler;
    private final Listener listener;

    private State state = new State(Stage.STOPPED, "", null);
    private String uid = "", confirmed;
    private long generation, waitGeneration;
    private boolean running;
    private Handle subscription, deadline;

    public LiveAccessMonitor(Source source, Scheduler scheduler, Listener listener) {
        this.source = source;
        this.scheduler = scheduler;
        this.listener = listener;
    }

    public State state() {
        return state;
    }

    public void start(String nextUid) {
        String previous = running && uid.equals(nextUid) ? confirmed : null;
        running = false;
        ++generation;
        detach();
        running = true;
        uid = nextUid == null ? "" : nextUid;
        confirmed = previous;
        final long token = generation;

        if (uid.isEmpty() || !uid.equals(source.currentUid())) {
            finish(Stage.SIGNED_OUT, null);
            return;
        }

        try {
            waiting(token);
            if (!accepts(token)) return;

            Handle next = source.listen(uid, new Observer() {
                @Override
                public void snapshot(boolean cache, boolean pending, List<Grant> grants) {
                    if (!accepts(token)) return;

                    if (cache || pending) {
                        waiting(token);
                        return;
                    }

                    final String current;
                    try {
                        current = fingerprint(grants);
                    } catch (RuntimeException invalid) {
                        finish(Stage.ERROR, invalid);
                        return;
                    }

                    if (current.isEmpty()) {
                        finish(Stage.PENDING, null);
                        return;
                    }

                    if (confirmed != null && !confirmed.equals(current)) {
                        finish(Stage.RECHECK, null);
                        return;
                    }

                    confirmed = current;
                    cancelDeadline();
                    publish(Stage.GRANTED, null);
                }

                @Override
                public void error(Throwable error) {
                    if (accepts(token)) finish(Stage.ERROR, error);
                }
            });

            if (accepts(token)) subscription = next;
            else if (next != null) next.remove();
        } catch (RuntimeException error) {
            if (accepts(token)) finish(Stage.ERROR, error);
        }
    }

    public void stop() {
        running = false;
        ++generation;
        detach();
        uid = "";
        confirmed = null;
        publish(Stage.STOPPED, null);
    }

    private boolean accepts(long token) {
        if (!running || generation != token) return false;

        if (!uid.equals(source.currentUid())) {
            finish(Stage.SIGNED_OUT, null);
            return false;
        }

        return true;
    }

    private void waiting(long token) {
        publish(Stage.CHECKING, null);
        if (!accepts(token) || deadline != null) return;

        final long waitToken = ++waitGeneration;
        Handle next = scheduler.after(CHECK_TIMEOUT_MS, () -> {
            if (accepts(token)
                    && waitGeneration == waitToken
                    && state.stage == Stage.CHECKING) {
                finish(Stage.ERROR, new TimeoutException("MEMBERSHIP_SERVER"));
            }
        });

        if (accepts(token) && state.stage == Stage.CHECKING) deadline = next;
        else if (next != null) next.remove();
    }

    private void finish(Stage stage, Throwable error) {
        running = false;
        ++generation;
        detach();
        confirmed = null;
        publish(stage, error);
    }

    private void publish(Stage stage, Throwable error) {
        if (state.stage == stage && state.uid.equals(uid) && state.error == error) return;

        state = new State(stage, uid, error);
        listener.changed(state);
    }

    private void cancelDeadline() {
        ++waitGeneration;
        Handle previous = deadline;
        deadline = null;
        if (previous != null) previous.remove();
    }

    private void detach() {
        Handle previous = subscription;
        subscription = null;
        if (previous != null) previous.remove();
        cancelDeadline();
    }

    /** Only active known grants affect access; order, duplicates and labels do not. */
    public static String fingerprint(List<Grant> grants) {
        if (grants == null) throw new IllegalStateException("MEMBERSHIP");

        TreeMap<String, String> active = new TreeMap<>();

        for (Grant grant : grants) {
            if (grant == null || grant.id == null || grant.id.isEmpty()) {
                throw new IllegalStateException("MEMBERSHIP");
            }

            if (!MembershipPolicy.hasActiveRoles(grant.active, grant.roles)) continue;

            TreeSet<String> roles = new TreeSet<>();
            for (Object role : (List<?>) grant.roles) roles.add((String) role);

            StringBuilder names = new StringBuilder();
            for (String role : roles) {
                if (names.length() > 0) names.append(',');
                names.append(role);
            }

            if (active.put(grant.id, names.toString()) != null) {
                throw new IllegalStateException("MEMBERSHIP");
            }
        }

        StringBuilder value = new StringBuilder();
        for (String id : active.keySet()) {
            value.append(id.length()).append(':').append(id).append(':')
                    .append(active.get(id)).append(';');
        }
        return value.toString();
    }
}