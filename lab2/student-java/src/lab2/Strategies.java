package lab2;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/* Стратегии выбора следующего потока.
 * FirstAlive — этап 1, Replay — этап 2, RandomPick — этап 3, Dfs — этап 4 (ограничение bound — этап 5).
 * enabled — номера потоков, которые могут сделать шаг, по возрастанию; last — прошлый выбранный (-1 в начале). */
public final class Strategies {

    /** Всегда первый поток из enabled. */
    public static final class FirstAlive implements Strategy {
        public int pick(List<Integer> enabled, int last) {
            return enabled.getFirst();
        }
    }

    /** Проигрывает трассу: на i-м решении выбирает поток trace[i] ('A' -> 0, 'B' -> 1). */
    public static final class Replay implements Strategy {
        private final String trace;
        private int position;

        public Replay(String trace) {
            this.trace = Objects.requireNonNull(trace);
        }

        /** Сбросить позицию в трассе перед прогоном. */
        public void beginRun() {
            position = 0;
        }

        public int pick(List<Integer> enabled, int last) {
            if (position >= trace.length()) {
                throw new IllegalArgumentException("Replay trace exhausted at decision " + (position + 1));
            }

            int next = trace.charAt(position++) - 'A';
            if (!enabled.contains(next)) {
                throw new IllegalArgumentException("Replay thread " + trace.charAt(position - 1) + " disabled at decision " + position);
            }

            return next;
        }
    }

    /** Случайный поток: enabled.get(rnd.nextInt(enabled.size())), rnd = new java.util.Random(seed). */
    public static final class RandomPick implements Strategy {
        private final Random rnd;

        public RandomPick(long seed) {
            rnd = new Random(seed);
        }

        public int pick(List<Integer> enabled, int last) {
            return enabled.get(rnd.nextInt(enabled.size()));
        }
    }

    /** Stateless DFS по дереву выборов: каждый прогон с нуля, повторяет префикс прошлого и берёт следующий вариант.
     *  bound — максимум вытеснений (этап 5); для dfs runner передаёт Integer.MAX_VALUE. */
    public static final class Dfs implements Strategy {
        private static final class Choice {
            private final List<Integer> options;
            private int index;

            private Choice(List<Integer> options) {
                this.options = options;
            }

            private int selected() {
                return options.get(index);
            }
        }
        private final int bound;
        private final List<Choice> path = new ArrayList<>();
        private int depth;
        private int preemptions;

        public Dfs(int bound) {
            if (bound < 0) {
                throw new IllegalArgumentException("Negative preemption bound");
            }
            this.bound = bound;
        }

        /** Подготовиться к новому прогону (позиция в пути, счётчик вытеснений). */
        public void beginRun() {
            depth = 0;
            preemptions = 0;
        }

        public int pick(List<Integer> enabled, int last) {
            boolean lastThreadEnabled = enabled.contains(last);
            if (depth == path.size()) {
                List<Integer> options = new ArrayList<>();
                for (int id : enabled) {
                    if (!lastThreadEnabled || id == last || preemptions < bound) {
                        options.add(id);
                    }
                }
                path.add(new Choice(List.copyOf(options)));
            }
            int next = path.get(depth++).selected();
            if (lastThreadEnabled && next != last) {
                preemptions++;
            }
            return next;
        }

        /** Выбрать следующий путь; false — дерево обойдено. */
        public boolean endRun() {
            while (path.size() > depth) {
                path.removeLast();
            }
            for (int i = path.size() - 1; i >= 0; i--) {
                Choice choice = path.get(i);
                if (++choice.index < choice.options.size()) {
                    return true;
                }
                path.remove(i);
            }
            return false;
        }
    }
}
