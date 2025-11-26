import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

class LFUCacheEntry<K, V> {
    K key;
    V value;
    Integer frequency;

    public LFUCacheEntry(K key, V value, Integer frequency) {
        this.key = key;
        this.value = value;
        this.frequency = frequency;
    }

    public K getKey() {
        return key;
    }

    public V getValue() {
        return value;
    }

    public Integer getFrequency() {
        return frequency;
    }

    public void setValue(V value) {
        this.value = value;
    }

    public void setFrequency(Integer frequency) {
        this.frequency = frequency;
    }
}

public class LFUCacheSystem<K, V> {
    private Integer capacity;
    private Map<K, LFUCacheEntry<K, V>> cacheMap;
    private PriorityQueue<LFUCacheEntry<K, V>> cacheHeap;
    private Lock lock;

    public LFUCacheSystem(int capacity) {
        this.capacity = capacity;
        this.cacheMap = new HashMap<>();
        this.cacheHeap = new PriorityQueue<>(Comparator.comparing(LFUCacheEntry::getFrequency));
        this.lock = new ReentrantLock();
    }

    public V get(K key) {
        lock.lock();
        if (cacheMap.containsKey(key)) {
            LFUCacheEntry<K, V> cacheEntry = cacheMap.get(key);
            int frequency = cacheEntry.getFrequency();
            cacheHeap.remove(cacheEntry);
            cacheEntry.setFrequency(frequency + 1);
            cacheHeap.add(cacheEntry);
            return cacheEntry.getValue();
        }
        lock.unlock();
        return null;
    }

    public void put(K key, V value) {
        lock.lock();
        if (cacheMap.containsKey(key)) {
            LFUCacheEntry<K, V> cacheEntry = cacheMap.get(key);
            cacheHeap.remove(cacheEntry);
            cacheEntry.setValue(value);
            int freq = cacheEntry.getFrequency();
            cacheEntry.setFrequency(freq + 1);
            cacheHeap.add(cacheEntry);
        } else {
            if (cacheMap.size() >= capacity) {
                LFUCacheEntry<K, V> cacheEntry = cacheHeap.poll();
                if (cacheEntry != null) {
                    cacheMap.remove(key);
                }
            }
            LFUCacheEntry<K, V> cacheEntry = new LFUCacheEntry<>(key, value, 1);
            cacheMap.put(key, cacheEntry);
            cacheHeap.add(cacheEntry);
        }
        lock.unlock();
    }
}
