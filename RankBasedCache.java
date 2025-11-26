import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;

class CacheEntry<K, V> {
    K key;
    V value;
    Integer rank;

    public CacheEntry(K key, V value, Integer rank) {
        this.key = key;
        this.value = value;
        this.rank = rank;
    }

    public K getKey() {
        return key;
    }

    public V getValue() {
        return value;
    }

    public Integer getRank() {
        return rank;
    }

    public void setValue(V value) {
        this.value = value;
    }

    public void setRank(Integer rank) {
        this.rank = rank;
    }
}

public class RankBasedCache<K, V> {
    private Integer capacity;
    private Map<K, CacheEntry<K, V>> cacheMap;
    private PriorityQueue<CacheEntry<K, V>> rankQueue;

    public RankBasedCache(Integer capacity) {
        this.capacity = capacity;
        this.cacheMap = new HashMap<>();
        this.rankQueue = new PriorityQueue<>(Comparator.comparing(CacheEntry::getRank));
    }

    public V get(K key) {
        CacheEntry<K, V> cacheEntry = cacheMap.get(key);
        if (cacheEntry != null) {
            return cacheEntry.getValue();
        }
        return null;
    }

    public void put(K key, V value, int rank) {
        if (cacheMap.containsKey(key)) {
            CacheEntry<K, V> cacheEntry = cacheMap.get(key);
            rankQueue.remove(cacheEntry);
            cacheEntry.setValue(value);
            cacheEntry.setRank(rank);
            rankQueue.add(cacheEntry);
        } else {
            if (cacheMap.size() >= capacity) {
                CacheEntry<K, V> cacheEntry = rankQueue.poll();
                if (cacheEntry != null) {
                    cacheMap.remove(cacheEntry.getKey());
                }
            }
            cacheMap.put(key, new CacheEntry<>(key, value, rank));
            rankQueue.add(new CacheEntry<>(key, value, rank));
        }
    }
}
