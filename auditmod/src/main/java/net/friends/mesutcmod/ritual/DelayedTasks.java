package net.friends.mesutcmod.ritual;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Küçük "N tick sonra şunu çalıştır" yardımcısı. Entity kendini silip gittikten sonra bile
 * (ör. Midas portalda yok olduktan sonra Mesut'u çıkarmak için) bir şeyin gecikmeli olmasını sağlar.
 * Tek bir sunucu tick dinleyicisi kullanır; ilk kullanımda kendini kaydeder.
 */
public final class DelayedTasks {
    private static final class Task {
        int ticksLeft;
        final Runnable action;
        Task(int ticksLeft, Runnable action) { this.ticksLeft = ticksLeft; this.action = action; }
    }

    private static final List<Task> TASKS = new ArrayList<>();
    private static boolean registered = false;

    private DelayedTasks() {}

    public static synchronized void schedule(int ticks, Runnable action) {
        if (!registered) {
            registered = true;
            ServerTickEvents.END_SERVER_TICK.register(server -> run());
        }
        TASKS.add(new Task(Math.max(1, ticks), action));
    }

    private static synchronized void run() {
        if (TASKS.isEmpty()) return;
        List<Runnable> due = new ArrayList<>();
        Iterator<Task> it = TASKS.iterator();
        while (it.hasNext()) {
            Task t = it.next();
            if (--t.ticksLeft <= 0) {
                due.add(t.action);
                it.remove();
            }
        }
        for (Runnable r : due) {
            try {
                r.run();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
