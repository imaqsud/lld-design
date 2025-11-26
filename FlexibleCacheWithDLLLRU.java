import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

interface DataStorage<K, V> {
    V get(K key);
    void put(K key, V value);
    void remove(K key);
    boolean containsKey(K key);
    int size();
}

class SimpleDataStorage<K, V> implements DataStorage<K, V> {
    private final Map<K, V> map = new HashMap<>();

    @Override
    public V get(K key) { return map.get(key); }

    @Override
    public void put(K key, V value) { map.put(key, value); }

    @Override
    public void remove(K key) { map.remove(key); }

    @Override
    public boolean containsKey(K key) { return map.containsKey(key); }

    @Override
    public int size() { return map.size(); }
}

interface DataEvictionPolicy<K> {
    void keyAccessed(K key);
    void keyAdded(K key);
    K evictKey(); // Returns the key that should be removed
}

class CacheNode<K> {
    K key;
    CacheNode<K> prev;
    CacheNode<K> next;

    CacheNode(K key) {
        this.key = key;
    }
}

class CacheDoublyLinkedList<K> {
    private CacheNode<K> head; // MRU end
    private CacheNode<K> tail; // LRU end

    public void addFirst(CacheNode<K> node) {
        node.prev = null;
        node.next = head;
        if (head != null) {
            head.prev = node;
        }
        head = node;
        if (tail == null) {
            tail = head;
        }
    }

    public void remove(CacheNode<K> node) {
        if (node.prev != null) {
            node.prev.next = node.next;
        } else {
            head = node.next; // Removing the head
        }
        if (node.next != null) {
            node.next.prev = node.prev;
        } else {
            tail = node.prev; // Removing the tail
        }
    }

    public void moveToFront(CacheNode<K> node) {
        remove(node);
        addFirst(node);
    }

    public CacheNode<K> removeLast() {
        if (tail == null) return null;
        CacheNode<K> nodeToRemove = tail;
        remove(nodeToRemove);
        return nodeToRemove;
    }
}

class LRUDataEvictionPolicy<K> implements DataEvictionPolicy<K> {
    private final Map<K, CacheNode<K>> nodeMap = new HashMap<>();
    private final CacheDoublyLinkedList<K> dll = new CacheDoublyLinkedList<>();

    @Override
    public void keyAccessed(K key) {
        CacheNode<K> node = nodeMap.get(key);
        if (node != null) {
            dll.moveToFront(node); // Move to MRU position
        }
    }

    @Override
    public void keyAdded(K key) {
        CacheNode<K> newNode = new CacheNode<>(key);
        dll.addFirst(newNode); // Add as MRU
        nodeMap.put(key, newNode);
    }

    @Override
    public K evictKey() {
        CacheNode<K> lruNode = dll.removeLast(); // Remove LRU from list
        if (lruNode != null) {
            nodeMap.remove(lruNode.key); // Remove from map
            return lruNode.key;
        }
        return null;
    }
}

enum DataEvictionAlgorithm {
    LRU,
    LFU,
    RANK_BASED,
    RANK_BASED_LRU
}

public class FlexibleCacheWithDLLLRU<K, V> {
    private final DataStorage<K, V> storage;
    private final EvictionPolicy<K> evictionPolicy;
    private final int capacity;

    public FlexibleCacheWithDLLLRU(int capacity, DataEvictionAlgorithm evictionAlgorithm) {
        this.capacity = capacity;
        if (Objects.requireNonNull(evictionAlgorithm) == DataEvictionAlgorithm.LRU) {
            this.evictionPolicy = new LRUEvictionPolicy<>();
        } else {
            throw new RuntimeException("Eviction algorithm not supported.");
        }
        this.storage = new SimpleDataStorage<>();
    }

    public V get(K key) {
        V value = storage.get(key);
        if (value != null) {
            evictionPolicy.keyAccessed(key);
        }
        return value;
    }

    public void put(K key, V value) {
        if (storage.containsKey(key)) {
            storage.put(key, value);
            evictionPolicy.keyAccessed(key);
            return;
        }

        if (storage.size() >= capacity) {
            K keyToEvict = evictionPolicy.evictKey();
            if (keyToEvict != null) {
                storage.remove(keyToEvict);
            }
        }
        storage.put(key, value);
        evictionPolicy.keyAdded(key);
    }
}
