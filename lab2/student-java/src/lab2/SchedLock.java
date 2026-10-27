package lab2;

/* Этап 2: лок, о котором знает планировщик. Поток, ждущий занятый лок, не попадает в enabled;
 * обычный synchronized / ReentrantLock здесь нельзя — поток уснёт с ходом в руках и всё зависнет.
 * Лог: "lock <имя>", "unlock <имя>". Планировщик должен знать все локи, чтобы печатать held={a:A, b:B}. */
public final class SchedLock {
    private final String name;
    private int owner = -1;

    /** Создаётся в setup() тестовой программы; зарегистрируйте лок в планировщике s. */
    public SchedLock(Scheduler s, String name) {
        this.name = name;
        s.registerLock(this);
    }

    public String name() {
        return name;
    }

    public int owner() {
        return owner;
    }

    /** Пауза; если лок занят — ждать, не участвуя в выборе; затем стать владельцем. */
    public void lock() {
        Scheduler.beforeOperation(() -> this.owner == -1);
        owner = Scheduler.currentId();
        Scheduler.record("lock " + name);
    }

    /** Пауза; освободить лок, ждавшие потоки снова могут быть выбраны. */
    public void unlock() {
        Scheduler.beforeOperation(null);
        if (owner != Scheduler.currentId()) {
            throw new IllegalMonitorStateException("Not owner of " + name);
        }
        owner = -1;
        Scheduler.record("unlock " + name);
    }
}
