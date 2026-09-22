package hoordGame;

import java.util.LinkedHashMap;
import java.util.Map;

/** Entry-bounded map with the caller's existing insertion or access order.
 * Synchronization remains with the owner so compound cache operations stay atomic.
 */
public final class BoundedCache<K, V> extends LinkedHashMap<K, V> {
    private static final long serialVersionUID = 1L;
    private final int maximumEntries;

    public BoundedCache(int initialCapacity, float loadFactor, boolean accessOrder,
            int maximumEntries) {
        super(initialCapacity, loadFactor, accessOrder);
        this.maximumEntries = maximumEntries;
    }

    @Override
    protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
        return size() > maximumEntries;
    }
}
