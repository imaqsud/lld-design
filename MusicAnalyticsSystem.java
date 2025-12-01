import java.util.*;

class Song {
    private final String songId;
    // Metadata can be added here (title, artist, etc.) for better OOP design
    private int playCount;

    public Song(String songId) {
        this.songId = songId;
        this.playCount = 0;
    }

    public String getSongId() {
        return songId;
    }

    public int getPlayCount() {
        return playCount;
    }

    public void incrementPlayCount() {
        this.playCount++;
    }

    @Override
    public String toString() {
        return "Song{id='" + songId + "', plays=" + playCount + '}';
    }
}

// Represents a User entity and manages their recently played list
class User {
    private final String userId;
    private final LimitedQueue recentlyPlayed;

    public User(String userId, int limit) {
        this.userId = userId;
        this.recentlyPlayed = new LimitedQueue(limit);
    }

    public String getUserId() {
        return userId;
    }

    public void addPlayedSong(String songId) {
        recentlyPlayed.add(songId);
    }

    public List<String> getRecentlyPlayed(int k) {
        // Return a sublist of the k most recent unique songs
        List<String> allPlayed = recentlyPlayed.getSongs();
        int count = Math.min(k, allPlayed.size());
        return allPlayed.subList(0, count);
    }
}

// Helper class to maintain a queue of unique songs with a fixed size limit (LIFO/recency order)
class LimitedQueue {
    // LinkedHashSet maintains insertion order and guarantees uniqueness (O(1) average time for add/remove)
    private final LinkedHashSet<String> queue;
    private final int limit;

    public LimitedQueue(int limit) {
        this.limit = limit;
        this.queue = new LinkedHashSet<>();
    }

    public void add(String songId) {
        // Remove existing to bring to front (maintain recency order)
        if (queue.contains(songId)) {
            queue.remove(songId);
        } else if (queue.size() == limit) {
            // If at limit, remove the least recently played (the first element in LinkedHashSet)
            Iterator<String> it = queue.iterator();
            it.next();
            it.remove();
        }
        queue.add(songId); // Add to the end (most recent)
    }

    public List<String> getSongs() {
        // Return a list in reverse order of insertion (most recent first)
        List<String> list = new ArrayList<>(queue);
        Collections.reverse(list);
        return list;
    }
}

public class MusicAnalyticsSystem {
    // Stores all songs by ID (O(1) lookup)
    private final Map<String, Song> songs = new HashMap<>();
    // Stores all users by ID (O(1) lookup)
    private final Map<String, User> users = new HashMap<>();
    // Default limit for recently played songs per user
    private static final int DEFAULT_RECENTS_LIMIT = 10;

    // Add a new song to the system
    public void add_song(String songId) {
        songs.putIfAbsent(songId, new Song(songId));
    }

    // Record a song play for a user
    public void play_song(String userId, String songId) {
        // Ensure song exists
        if (!songs.containsKey(songId)) {
            add_song(songId); // Or handle error if only pre-added songs allowed
        }
        songs.get(songId).incrementPlayCount();

        // Ensure user exists and update their recently played
        users.putIfAbsent(userId, new User(userId, DEFAULT_RECENTS_LIMIT));
        users.get(userId).addPlayedSong(songId);
    }

    // Print the top K most played songs across all users
    public void print_analytics() {
        System.out.println("--- Global Top 10 Most Played Songs (by unique plays in this system) ---");
        // Use a MinHeap to find the top K efficiently
        PriorityQueue<Song> minHeap = new PriorityQueue<>(Comparator.comparingInt(Song::getPlayCount));
        int k = 10;

        for (Song song : songs.values()) {
            if (song.getPlayCount() > 0) {
                minHeap.add(song);
                if (minHeap.size() > k) {
                    minHeap.poll(); // Remove the minimum element if size exceeds K
                }
            }
        }

        List<Song> topSongs = new ArrayList<>(minHeap);
        topSongs.sort(Comparator.comparingInt(Song::getPlayCount).reversed());

        for (int i = 0; i < topSongs.size(); i++) {
            System.out.println((i + 1) + ". " + topSongs.get(i));
        }
    }

    // Print all recently played unique songs for a user
    public void print_recently_played(String userId) {
        print_recently_played(userId, DEFAULT_RECENTS_LIMIT);
    }

    // Print the K most recently played unique songs for a user
    public void print_recently_played(String userId, int k) {
        User user = users.get(userId);
        if (user == null) {
            System.out.println("User " + userId + " not found or has no play history.");
            return;
        }

        List<String> recents = user.getRecentlyPlayed(k);
        System.out.println("--- Top " + recents.size() + " Recent Unique Songs for User " + userId + " ---");
        for (int i = 0; i < recents.size(); i++) {
            System.out.println((i + 1) + ". Song ID: " + recents.get(i));
        }
    }

    public static void main(String[] args) {
        MusicAnalyticsSystem analytics = new MusicAnalyticsSystem();

        analytics.add_song("s1");
        analytics.add_song("s2");
        analytics.add_song("s3");

        analytics.play_song("u1", "s1");
        analytics.play_song("u1", "s2");
        analytics.play_song("u1", "s1"); // s1 moves to the top of u1's recents
        analytics.play_song("u2", "s1");
        analytics.play_song("u2", "s2");
        analytics.play_song("u3", "s3");

        analytics.print_analytics();
        System.out.println();
        analytics.print_recently_played("u1", 2); // Get top 2 recent unique songs for u1
        System.out.println();
        analytics.print_recently_played("u2"); // Get default limit recent unique songs for u2
    }
}
