package tdtsp.routing;

import java.util.HashMap;
import java.util.Map;

/**
 * Caches legs by {@code (from, to, departure time)} during tour construction (plan §5.1).
 * Local search re-evaluates whole tours, so unchanged prefixes become cache hits.
 */
public final class MemoRouter implements Router {

    private final Router inner;
    private final Map<Long, Leg> cache = new HashMap<>();

    public MemoRouter(Router inner) {
        this.inner = inner;
    }

    @Override
    public Leg leg(int from, int to, int time) {
        long key = ((long) from << 48) | ((long) to << 32) | (time & 0xffffffffL);
        return cache.computeIfAbsent(key, k -> inner.leg(from, to, time));
    }

    public int size() {
        return cache.size();
    }
}
