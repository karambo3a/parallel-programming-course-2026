package lab2;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Semaphore;
import java.util.function.BooleanSupplier;

/* Этап 1: планировщик, который ведёт настоящие потоки строго по одному (передача хода через семафоры).
 * Этап 2: обнаружение deadlock в главном цикле и корректное завершение застрявших потоков.
 * Внутреннее устройство (свои классы, семафоры, вспомогательные методы для Shared и SchedLock) придумываете сами. */
public final class Scheduler {
    /**
     * Если true, каждая операция с общими данными добавляет строку в log. Выставляет runner.
     */
    public boolean logging;
    /**
     * Лог прогона: строки "<поток>  <операция>", например "A  lock mu", "A  balance -> 100".
     */
    public final List<String> log = new ArrayList<>();
    /**
     * null, если deadlock не было; иначе "held={a:A, b:B}" (этап 2).
     */
    public String deadlock;

    private static final ThreadLocal<Worker> currentWorker = new ThreadLocal<>();
    private final List<Worker> workers = new ArrayList<>();
    private final List<SchedLock> locks = new ArrayList<>();
    private final Semaphore workerYielded = new Semaphore(0);
    private final StringBuilder trace = new StringBuilder();
    private volatile boolean aborted;
    private boolean started;

    private static final class Cancelled extends Error {
        private static final long serialVersionUID = 1L;
        private Cancelled() {
            super(null, null, false, false);
        }
    }

    private final class Worker {
        private final int id;
        private final Runnable body;
        private final Semaphore runPermit = new Semaphore(0);
        private final Thread thread;
        private boolean pauseRequired = true;
        private boolean done;
        private BooleanSupplier ready;
        private Throwable failure;

        private Worker(int id, Runnable body) {
            this.id = id;
            this.body = body;
            thread = new Thread(this::execute, "scheduler-" + (char) ('A' + id));
        }

        private void execute() {
            currentWorker.set(this);
            try {
                body.run();
            } catch (Cancelled ignored) {
            } catch (Throwable e) {
                failure = e;
            } finally {
                done = true;
                currentWorker.remove();
                workerYielded.release();
            }
        }

        private void writeLog(String operation) {
            appendLog(id, operation);
        }

        private void pause(BooleanSupplier condition) {
            if (aborted) {
                throw new Cancelled();
            }
            ready = condition;
            pauseRequired = false;
            workerYielded.release();
            runPermit.acquireUninterruptibly();
            if (aborted) {
                throw new Cancelled();
            }
            ready = null;
        }
    }

    public void registerLock(SchedLock lock) {
        if (started) {
            throw new IllegalStateException("Cannot register lock after run started");
        }
        locks.add(Objects.requireNonNull(lock));
    }

    /**
     * Зарегистрировать поток. Потоки получают буквы A, B, C… в порядке вызова spawn.
     */
    public void spawn(Runnable body) {
        if (started) {
            throw new IllegalStateException("Cannot spawn after run started");
        }

        workers.add(new Worker(workers.size(), Objects.requireNonNull(body)));
    }

    /**
     * Запустить потоки и вести их по одному: на каждой паузе спросить strategy.pick(enabled, last).
     */
    public void run(Strategy strategy) {
        if (started) {
            throw new IllegalStateException("Scheduler can run only once");
        }

        started = true;
        try {
            for (Worker w : workers) {
                w.thread.start();
                workerYielded.acquireUninterruptibly();
                rethrow(w.failure);
            }
            int last = -1;
            while (true) {
                List<Integer> enabled = new ArrayList<>();
                boolean alive = false;
                for (Worker w : workers) {
                    if (w.done) {
                        continue;
                    }
                    alive = true;
                    if (w.ready == null || w.ready.getAsBoolean()) {
                        enabled.add(w.id);
                    }
                }
                if (!alive) {
                    break;
                }
                if (enabled.isEmpty()) {
                    StringBuilder held = new StringBuilder("held={");
                    for (SchedLock lock : locks) {
                        int owner = lock.owner();
                        if (owner < 0) {
                            continue;
                        }
                        if (held.length() > 6) {
                            held.append(", ");
                        }
                        held.append(lock.name()).append(':').append((char) ('A' + owner));
                    }
                    deadlock = held.append('}').toString();
                    break;
                }
                int next = strategy.pick(List.copyOf(enabled), last);
                if (!enabled.contains(next)) {
                    throw new IllegalArgumentException("Strategy chose disabled thread " + next);
                }
                trace.append((char) ('A' + next));
                last = next;
                Worker w = workers.get(next);
                w.runPermit.release();
                workerYielded.acquireUninterruptibly();
                rethrow(w.failure);
            }
        } finally {
            aborted = true;
            for (Worker w : workers) {
                w.runPermit.release();
            }
            boolean interrupted = false;
            for (Worker w : workers) {
                while (w.thread.isAlive()) {
                    try {
                        w.thread.join();
                    } catch (InterruptedException e) {
                        interrupted = true;
                    }
                }
            }
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static void rethrow(Throwable failure) {
        if (failure instanceof Error e) {
            throw e;
        } else if (failure instanceof RuntimeException e) {
            throw e;
        } else if (failure != null) {
            throw new RuntimeException(failure);
        }
    }

    private static Worker current() {
        Worker w = currentWorker.get();
        if (w == null) {
            throw new IllegalStateException("Operation outside a scheduled worker");
        }
        return w;
    }

    public static int currentId() {
        return current().id;
    }

    public static void beforeOperation(BooleanSupplier ready) {
        Worker w = current();
        if (w.pauseRequired || (ready != null && !ready.getAsBoolean())) {
            w.pause(ready);
        }
        w.pauseRequired = true;
    }

    public static void record(String operation) {
        Worker w = current();
        w.writeLog(operation);
    }

    private void appendLog(int id, String operation) {
        if (logging && !aborted) {
            log.add((char) ('A' + id) + "  " + operation);
        }
    }

    public String traceString() {
        return trace.toString();
    }

    /**
     * window() из тестовых программ: пауза перед следующей операцией с общими данными обязательна.
     */
    public static void point() {
        Worker w = current();
        w.pauseRequired = true;
        record("window");
    }

    /**
     * Ждать, пока cond не станет true. До этого поток не попадает в enabled.
     */
    public static void await(BooleanSupplier cond) {
        beforeOperation(Objects.requireNonNull(cond));
        record("await done");
    }
}
