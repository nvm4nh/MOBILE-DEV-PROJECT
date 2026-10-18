package com.seniortribe.app.auth;

import org.junit.Test;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeoutException;
import static org.junit.Assert.*;

public class LiveAccessMonitorTest {
    private static final class Handle implements LiveAccessMonitor.Handle {
        boolean removed;
        Runnable onRemove;

        @Override public void remove() {
            removed = true;
            if (onRemove != null) onRemove.run();
        }
    }

    private static final class Source implements LiveAccessMonitor.Source {
        String uid = "a";
        RuntimeException failure;
        List<LiveAccessMonitor.Grant> immediate;
        final List<LiveAccessMonitor.Observer> observers = new ArrayList<>();
        final List<Handle> handles = new ArrayList<>();

        @Override public String currentUid() {
            return uid;
        }

        @Override public LiveAccessMonitor.Handle listen(
                String uid,
                LiveAccessMonitor.Observer observer
        ) {
            if (failure != null) throw failure;
            observers.add(observer);
            Handle handle = new Handle();
            handles.add(handle);

            if (immediate != null) {
                observer.snapshot(false, false, immediate);
            }

            return handle;
        }
    }

    private static final class Clock implements LiveAccessMonitor.Scheduler {
        final List<Runnable> jobs = new ArrayList<>();
        final List<Handle> handles = new ArrayList<>();

        @Override public LiveAccessMonitor.Handle after(long delay, Runnable action) {
            assertEquals(LiveAccessMonitor.CHECK_TIMEOUT_MS, delay);
            jobs.add(action);
            Handle handle = new Handle();
            handles.add(handle);
            return handle;
        }

        void queued(int index) {
            jobs.get(index).run();
        }
    }

    private static final class Fixture {
        final Source source = new Source();
        final Clock clock = new Clock();
        final List<LiveAccessMonitor.State> changes = new ArrayList<>();
        final LiveAccessMonitor monitor =
                new LiveAccessMonitor(source, clock, changes::add);

        Fixture() {
            monitor.start("a");
        }

        void server(LiveAccessMonitor.Grant... values) {
            source.observers.get(source.observers.size() - 1)
                    .snapshot(false, false, Arrays.asList(values));
        }

        void cache() {
            source.observers.get(source.observers.size() - 1)
                    .snapshot(true, false, Collections.emptyList());
        }

        void stage(LiveAccessMonitor.Stage stage) {
            assertEquals(stage, monitor.state().stage);
        }
    }

    private static LiveAccessMonitor.Grant grant(
            String id,
            boolean active,
            String... roles
    ) {
        return new LiveAccessMonitor.Grant(id, active, Arrays.asList(roles));
    }

    private static LiveAccessMonitor.Grant recorder() {
        return grant("org-a", true, "RECORDER");
    }

    @Test public void freshServerGrantOpensAccess() {
        Fixture f = new Fixture();
        f.server(recorder());
        f.stage(LiveAccessMonitor.Stage.GRANTED);
        assertTrue(f.clock.handles.get(0).removed);
    }

    @Test public void cachedEmptyIsCheckingNotPending() {
        Fixture f = new Fixture();
        f.cache();
        f.stage(LiveAccessMonitor.Stage.CHECKING);
    }

    @Test public void cachedGrantCannotOpenAccess() {
        Fixture f = new Fixture();
        f.source.observers.get(0)
                .snapshot(true, false, Arrays.asList(recorder()));
        f.stage(LiveAccessMonitor.Stage.CHECKING);
    }

    @Test public void pendingLocalWritesCannotOpenAccess() {
        Fixture f = new Fixture();
        f.source.observers.get(0)
                .snapshot(false, true, Arrays.asList(recorder()));
        f.stage(LiveAccessMonitor.Stage.CHECKING);
    }

    @Test public void serverEmptyRevokesAndDetaches() {
        Fixture f = new Fixture();
        f.server();
        f.stage(LiveAccessMonitor.Stage.PENDING);
        assertTrue(f.source.handles.get(0).removed);
        assertTrue(f.clock.handles.get(0).removed);
    }

    @Test public void inactiveMembershipDoesNotGrantAccess() {
        Fixture f = new Fixture();
        f.server(grant("org-a", false, "RECORDER"));
        f.stage(LiveAccessMonitor.Stage.PENDING);
    }

    @Test public void malformedGrantIsErrorNotPending() {
        Fixture f = new Fixture();
        f.server(new LiveAccessMonitor.Grant(
                "org-a", "true", Arrays.asList("RECORDER")
        ));
        f.stage(LiveAccessMonitor.Stage.ERROR);
    }

    @Test public void roleChangeRequiresNewGateValidation() {
        Fixture f = new Fixture();
        f.server(recorder());
        f.server(grant("org-a", true, "REVIEWER"));
        f.stage(LiveAccessMonitor.Stage.RECHECK);
    }

    @Test public void removingOneOfSeveralOrganizationsRequiresRecheck() {
        Fixture f = new Fixture();
        f.server(recorder(), grant("org-b", true, "REPRESENTATIVE"));
        f.server(recorder());
        f.stage(LiveAccessMonitor.Stage.RECHECK);
    }

    @Test public void orderingAndDuplicateRolesDoNotCauseFalseRecheck() {
        Fixture f = new Fixture();
        f.server(
                grant("org-b", true, "REPRESENTATIVE"),
                grant("org-a", true, "REVIEWER", "RECORDER")
        );
        f.server(
                grant("org-a", true, "RECORDER", "REVIEWER", "RECORDER"),
                grant("org-b", true, "REPRESENTATIVE")
        );
        f.stage(LiveAccessMonitor.Stage.GRANTED);
    }

    @Test public void inactiveGrantChangesDoNotAffectCurrentAccess() {
        Fixture f = new Fixture();
        f.server(recorder());
        f.server(recorder(), new LiveAccessMonitor.Grant("old", false, "legacy"));
        f.stage(LiveAccessMonitor.Stage.GRANTED);
    }

    @Test public void streamErrorPreservesFailureAndDetaches() {
        Fixture f = new Fixture();
        RuntimeException error = new RuntimeException("test");
        f.source.observers.get(0).error(error);
        f.stage(LiveAccessMonitor.Stage.ERROR);
        assertSame(error, f.monitor.state().error);
        assertTrue(f.source.handles.get(0).removed);
    }

    @Test public void missingServerResponseBecomesErrorNotRevocation() {
        Fixture f = new Fixture();
        f.clock.queued(0);
        f.stage(LiveAccessMonitor.Stage.ERROR);
        assertTrue(f.monitor.state().error instanceof TimeoutException);
    }

    @Test public void lostServerConfirmationBlocksThenRecovers() {
        Fixture f = new Fixture();
        f.server(recorder());
        f.cache();
        f.stage(LiveAccessMonitor.Stage.CHECKING);
        f.server(recorder());
        f.stage(LiveAccessMonitor.Stage.GRANTED);
    }

    @Test public void repeatedCacheEventsDoNotExtendDeadline() {
        Fixture f = new Fixture();
        f.cache();
        f.cache();
        assertEquals(1, f.clock.jobs.size());
        f.clock.queued(0);
        f.stage(LiveAccessMonitor.Stage.ERROR);
    }

    @Test public void canceledTimeoutCannotRevokeFreshServerGrant() {
        Fixture f = new Fixture();
        f.server(recorder());
        f.clock.queued(0);
        f.stage(LiveAccessMonitor.Stage.GRANTED);
    }

    @Test public void oldTimeoutCannotEndNewWaitingCycle() {
        Fixture f = new Fixture();
        f.server(recorder());
        f.cache();
        f.clock.queued(0);
        f.stage(LiveAccessMonitor.Stage.CHECKING);
        f.clock.queued(1);
        f.stage(LiveAccessMonitor.Stage.ERROR);
    }

    @Test public void snapshotAfterStopCannotRestoreAccess() {
        Fixture f = new Fixture();
        f.monitor.stop();
        f.server(recorder());
        f.stage(LiveAccessMonitor.Stage.STOPPED);
    }

    @Test public void snapshotFromOldSubscriptionIsIgnored() {
        Fixture f = new Fixture();
        f.monitor.start("a");
        f.source.observers.get(0)
                .snapshot(false, false, Collections.emptyList());
        f.stage(LiveAccessMonitor.Stage.CHECKING);
        f.server(recorder());
        f.stage(LiveAccessMonitor.Stage.GRANTED);
    }

    @Test public void timeoutFromOldSubscriptionIsIgnored() {
        Fixture f = new Fixture();
        f.monitor.start("a");
        f.clock.queued(0);
        f.stage(LiveAccessMonitor.Stage.CHECKING);
    }

    @Test public void changedAccountRejectsOldSnapshot() {
        Fixture f = new Fixture();
        f.source.uid = "b";
        f.server(recorder());
        f.stage(LiveAccessMonitor.Stage.SIGNED_OUT);
    }

    @Test public void mismatchedStartDoesNotAttachListener() {
        Source source = new Source();
        Clock clock = new Clock();
        LiveAccessMonitor monitor =
                new LiveAccessMonitor(source, clock, state -> {});

        monitor.start("b");
        assertEquals(LiveAccessMonitor.Stage.SIGNED_OUT, monitor.state().stage);
        assertTrue(source.observers.isEmpty());
        assertTrue(clock.jobs.isEmpty());
    }

    @Test public void restartSameUserDetectsChangedGrants() {
        Fixture f = new Fixture();
        f.server(recorder());
        f.monitor.start("a");
        f.server(grant("org-a", true, "REVIEWER"));
        f.stage(LiveAccessMonitor.Stage.RECHECK);
    }

    @Test public void newAccountDoesNotInheritPriorFingerprint() {
        Fixture f = new Fixture();
        f.server(recorder());
        f.source.uid = "b";
        f.monitor.start("b");
        f.server(grant("org-b", true, "REPRESENTATIVE"));
        f.stage(LiveAccessMonitor.Stage.GRANTED);
        assertEquals("b", f.monitor.state().uid);
    }

    @Test public void listenerCreationFailureCancelsDeadline() {
        Source source = new Source();
        source.failure = new RuntimeException("test");
        Clock clock = new Clock();
        LiveAccessMonitor monitor =
                new LiveAccessMonitor(source, clock, state -> {});

        monitor.start("a");
        assertEquals(LiveAccessMonitor.Stage.ERROR, monitor.state().stage);
        assertTrue(clock.handles.get(0).removed);
    }

    @Test public void synchronousDenialDoesNotLeakReturnedRegistration() {
        Source source = new Source();
        source.immediate = Collections.emptyList();
        LiveAccessMonitor monitor =
                new LiveAccessMonitor(source, new Clock(), state -> {});

        monitor.start("a");
        assertEquals(LiveAccessMonitor.Stage.PENDING, monitor.state().stage);
        assertTrue(source.handles.get(0).removed);
    }

    @Test public void synchronousGrantKeepsOneLiveRegistration() {
        Source source = new Source();
        source.immediate = Arrays.asList(recorder());
        LiveAccessMonitor monitor =
                new LiveAccessMonitor(source, new Clock(), state -> {});

        monitor.start("a");
        assertEquals(LiveAccessMonitor.Stage.GRANTED, monitor.state().stage);
        assertFalse(source.handles.get(0).removed);
        monitor.stop();
        assertTrue(source.handles.get(0).removed);
    }

    @Test public void callbackDuringOldListenerRemovalIsIgnored() {
        Fixture f = new Fixture();
        f.source.handles.get(0).onRemove = () ->
                f.source.observers.get(0)
                        .snapshot(false, false, Collections.emptyList());

        f.monitor.start("a");
        f.stage(LiveAccessMonitor.Stage.CHECKING);
        f.server(recorder());
        f.stage(LiveAccessMonitor.Stage.GRANTED);
    }

    @Test public void ownerStoppingDuringCheckingPreventsSubscription() {
        Source source = new Source();
        Clock clock = new Clock();
        LiveAccessMonitor[] ref = new LiveAccessMonitor[1];

        ref[0] = new LiveAccessMonitor(source, clock, state -> {
            if (state.stage == LiveAccessMonitor.Stage.CHECKING) ref[0].stop();
        });

        ref[0].start("a");
        assertTrue(source.observers.isEmpty());
        assertTrue(clock.jobs.isEmpty());
    }

    @Test public void ownerStoppingDuringSynchronousGrantDoesNotLeakRegistration() {
        Source source = new Source();
        source.immediate = Arrays.asList(recorder());
        LiveAccessMonitor[] ref = new LiveAccessMonitor[1];

        ref[0] = new LiveAccessMonitor(source, new Clock(), state -> {
            if (state.stage == LiveAccessMonitor.Stage.GRANTED) ref[0].stop();
        });

        ref[0].start("a");
        assertEquals(LiveAccessMonitor.Stage.STOPPED, ref[0].state().stage);
        assertTrue(source.handles.get(0).removed);
    }

    @Test public void nullServerDataIsAnError() {
        Fixture f = new Fixture();
        f.source.observers.get(0).snapshot(false, false, null);
        f.stage(LiveAccessMonitor.Stage.ERROR);
    }

    @Test public void duplicateOrganizationIdIsRejected() {
        Fixture f = new Fixture();
        f.server(recorder(), recorder());
        f.stage(LiveAccessMonitor.Stage.ERROR);
    }

    @Test public void callbacksAfterTerminalFailureCannotReopenAccess() {
        Fixture f = new Fixture();
        f.clock.queued(0);
        f.server(recorder());
        f.cache();
        f.stage(LiveAccessMonitor.Stage.ERROR);
    }

    @Test public void accountChangedBeforeTimeoutEndsOldSession() {
        Fixture f = new Fixture();
        f.source.uid = "b";
        f.clock.queued(0);
        f.stage(LiveAccessMonitor.Stage.SIGNED_OUT);
    }
}