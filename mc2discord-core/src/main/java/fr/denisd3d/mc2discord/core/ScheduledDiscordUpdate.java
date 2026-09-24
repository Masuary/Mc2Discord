package fr.denisd3d.mc2discord.core;

import reactor.core.Disposable;
import reactor.core.publisher.Mono;

import java.util.TimerTask;
import java.util.function.Consumer;
import java.util.function.Supplier;

final class ScheduledDiscordUpdate extends TimerTask {
    private final Supplier<Mono<Void>> update;
    private final Consumer<Throwable> errorHandler;
    private Disposable pendingUpdate;
    private boolean cancelled;

    ScheduledDiscordUpdate(Supplier<Mono<Void>> update, Consumer<Throwable> errorHandler) {
        this.update = update;
        this.errorHandler = errorHandler;
    }

    @Override
    public synchronized void run() {
        if (cancelled || (pendingUpdate != null && !pendingUpdate.isDisposed())) {
            return;
        }

        // Discord4J may wait for a rate-limit reset. Keep one update pending instead of timing it out.
        pendingUpdate = Mono.defer(update).subscribe(unused -> {}, errorHandler);
    }

    @Override
    public synchronized boolean cancel() {
        cancelled = true;
        if (pendingUpdate != null) {
            pendingUpdate.dispose();
        }
        return super.cancel();
    }
}
