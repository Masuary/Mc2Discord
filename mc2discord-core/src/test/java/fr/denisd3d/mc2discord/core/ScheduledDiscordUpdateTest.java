package fr.denisd3d.mc2discord.core;

import org.junit.Test;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class ScheduledDiscordUpdateTest {
    @Test
    public void allowsUpdatesTakingLongerThanThreeSeconds() throws InterruptedException {
        CountDownLatch completed = new CountDownLatch(1);
        List<Throwable> failures = new CopyOnWriteArrayList<>();
        ScheduledDiscordUpdate task = new ScheduledDiscordUpdate(
                () -> Mono.delay(Duration.ofMillis(3200)).then().doOnSuccess(unused -> completed.countDown()),
                failures::add);
        try {
            task.run();
            assertTrue("The slow update should complete", completed.await(8, TimeUnit.SECONDS));
            assertTrue(failures.isEmpty());
        } finally {
            task.cancel();
        }
    }

    @Test
    public void skipsOverlappingUpdatesAndRunsAgainAfterCompletion() {
        Sinks.Empty<Void> pending = Sinks.empty();
        AtomicInteger calls = new AtomicInteger();
        List<Throwable> failures = new ArrayList<>();
        ScheduledDiscordUpdate task = new ScheduledDiscordUpdate(() -> {
            calls.incrementAndGet();
            return pending.asMono();
        }, failures::add);
        try {
            task.run();
            task.run();
            task.run();
            assertEquals(1, calls.get());
            assertEquals(Sinks.EmitResult.OK, pending.tryEmitEmpty());
            task.run();
            assertEquals(2, calls.get());
            assertTrue(failures.isEmpty());
        } finally {
            task.cancel();
        }
    }

    @Test
    public void handlesAsynchronousErrorsAndAllowsNextScheduledUpdate() {
        Sinks.Empty<Void> pending = Sinks.empty();
        AtomicInteger calls = new AtomicInteger();
        List<Throwable> failures = new ArrayList<>();
        ScheduledDiscordUpdate task = new ScheduledDiscordUpdate(
                () -> calls.incrementAndGet() == 1 ? pending.asMono() : Mono.empty(), failures::add);
        TimeoutException failure = new TimeoutException("Simulated transport timeout");
        try {
            task.run();
            assertEquals(Sinks.EmitResult.OK, pending.tryEmitError(failure));
            assertEquals(List.of(failure), failures);
            task.run();
            assertEquals(2, calls.get());
            assertEquals(1, failures.size());
        } finally {
            task.cancel();
        }
    }

    @Test
    public void handlesErrorsWhilePreparingAnUpdate() {
        IllegalStateException failure = new IllegalStateException("Invalid update fixture");
        List<Throwable> failures = new ArrayList<>();
        ScheduledDiscordUpdate task = new ScheduledDiscordUpdate(() -> {
            throw failure;
        }, failures::add);
        try {
            task.run();
            assertEquals(List.of(failure), failures);
        } finally {
            task.cancel();
        }
    }

    @Test
    public void cancellationDisposesPendingUpdateAndPreventsFurtherRequests() {
        AtomicInteger calls = new AtomicInteger();
        AtomicInteger cancellations = new AtomicInteger();
        List<Throwable> failures = new ArrayList<>();
        ScheduledDiscordUpdate task = new ScheduledDiscordUpdate(() -> {
            calls.incrementAndGet();
            return Mono.<Void>never().doOnCancel(cancellations::incrementAndGet);
        }, failures::add);
        task.run();
        task.cancel();
        task.run();
        task.cancel();
        assertEquals(1, calls.get());
        assertEquals(1, cancellations.get());
        assertTrue(failures.isEmpty());

        ScheduledDiscordUpdate replacement = new ScheduledDiscordUpdate(() -> {
            calls.incrementAndGet();
            return Mono.empty();
        }, failures::add);
        try {
            replacement.run();
            assertEquals(2, calls.get());
        } finally {
            replacement.cancel();
        }
    }
}
