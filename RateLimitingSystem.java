import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.time.Clock;
import java.time.Instant;

class APIRequest {
    String apiKey;
    String apiUrl;
    Instant timestamp;

    public APIRequest(String apiKey, String apiUrl, Instant timestamp) {
        this.apiKey = apiKey;
        this.apiUrl = apiUrl;
        this.timestamp = timestamp;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getApiUrl() {
        return apiUrl;
    }

    public void setApiUrl(String apiUrl) {
        this.apiUrl = apiUrl;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }
}

interface RateLimiter {
    RequestStatus allowRequest(String apiKey);
}

enum RequestStatus {
    ALLOWED,
    THROTTLED
}

class TokenBucketRateLimiter implements RateLimiter {
    private final Map<String, Bucket> userBuckets = new ConcurrentHashMap<>();
    private final int capacity;
    private final long refillRate; // Tokens refilled per second
    private final Clock clock;

    public TokenBucketRateLimiter(int capacity, long refillRate, Clock clock) {
        this.capacity = capacity;
        this.refillRate = refillRate;
        this.clock = clock;
    }

    private static class Bucket {
        final AtomicLong currentTokens;
        long lastRefillTimeMillis;

        Bucket(int initialTokens, long startTimeMillis) {
            this.currentTokens = new AtomicLong(initialTokens);
            this.lastRefillTimeMillis = startTimeMillis;
        }
    }

    @Override
    public RequestStatus allowRequest(String apiKey) {
        long nowMillis = Instant.now(clock).toEpochMilli();
        Bucket bucket = userBuckets.computeIfAbsent(apiKey,
                k -> new Bucket(capacity, nowMillis));

        synchronized (bucket) {
            refillTokens(bucket, nowMillis);

            if (bucket.currentTokens.get() > 0) {
                bucket.currentTokens.decrementAndGet();
                return RequestStatus.ALLOWED;
            }
            return RequestStatus.THROTTLED;
        }
    }

    private void refillTokens(Bucket bucket, long nowMillis) {
        long elapsedTimeMs = nowMillis - bucket.lastRefillTimeMillis;
        if (refillRate <= 0) return;

        long tokensToAdd = (long) (elapsedTimeMs * (refillRate / 1000.0));

        if (tokensToAdd > 0) {
            long newTokens = Math.min(bucket.currentTokens.get() + tokensToAdd, capacity);
            bucket.currentTokens.set(newTokens);

            long timeAdvanced = (long) (tokensToAdd * (1000.0 / refillRate));
            bucket.lastRefillTimeMillis += timeAdvanced;
        }
    }
}

class LeakyBucketRateLimiter implements RateLimiter {
    private final Map<String, Bucket> userBuckets = new ConcurrentHashMap<>();
    private final int capacity;
    private final int leakRate; // Requests leaked (processed) per second
    private final long windowUnitMs = 1000L;
    private final Clock clock;

    public LeakyBucketRateLimiter(int capacity, int leakRate, Clock clock) {
        this.capacity = capacity;
        this.leakRate = leakRate;
        this.clock = clock;
    }

    private static class Bucket {
        final AtomicInteger waterLevel = new AtomicInteger(0);
        volatile long lastLeakTimestampMillis;

        Bucket(long initialTimeMillis) {
            this.lastLeakTimestampMillis = initialTimeMillis;
        }
    }

    @Override
    public RequestStatus allowRequest(String apiKey) {
        long nowMillis = Instant.now(clock).toEpochMilli();
        Bucket bucket = userBuckets.computeIfAbsent(apiKey,
                k -> new Bucket(nowMillis));

        synchronized (bucket) {
            leakRequests(bucket, nowMillis);

            if (bucket.waterLevel.get() < capacity) {
                bucket.waterLevel.incrementAndGet();
                return RequestStatus.ALLOWED;
            }
            return RequestStatus.THROTTLED;
        }
    }

    private void leakRequests(Bucket bucket, long nowMillis) {
        long elapsedTime = nowMillis - bucket.lastLeakTimestampMillis;
        int leakedAmount = (int) ((elapsedTime / (double)windowUnitMs) * leakRate);

        if (leakedAmount > 0) {
            bucket.waterLevel.updateAndGet(current -> Math.max(0, current - leakedAmount));
            bucket.lastLeakTimestampMillis = nowMillis;
        }
    }
}

class FixedWindowRateLimiter implements RateLimiter {
    private final Map<String, Window> userWindows = new ConcurrentHashMap<>();
    private final int limit;
    private final long windowSizeMs;
    private final Clock clock;

    public FixedWindowRateLimiter(int limit, long windowSizeMs, Clock clock) {
        this.limit = limit;
        this.windowSizeMs = windowSizeMs;
        this.clock = clock;
    }

    private static class Window {
        final AtomicInteger requestCount = new AtomicInteger(0);
        volatile long windowStartTime;

        Window(long startTime) {
            this.windowStartTime = startTime;
        }
    }

    @Override
    public RequestStatus allowRequest(String apiKey) {
        long currentTimeMillis = Instant.now(clock).toEpochMilli();
        Window window = userWindows.computeIfAbsent(apiKey,
                k -> new Window(currentTimeMillis));

        synchronized (window) {
            if (currentTimeMillis - window.windowStartTime >= windowSizeMs) {
                window.windowStartTime = currentTimeMillis;
                window.requestCount.set(0);
            }

            if (window.requestCount.get() < limit) {
                window.requestCount.incrementAndGet();
                return RequestStatus.ALLOWED;
            }
            return RequestStatus.THROTTLED;
        }
    }
}

class SlidingWindowLogRateLimiter implements RateLimiter {
    private final Map<String, Deque<Long>> userLogs = new ConcurrentHashMap<>();
    private final int limit;
    private final long windowSizeMs;
    private final Clock clock;

    public SlidingWindowLogRateLimiter(int limit, long windowSizeMs, Clock clock) {
        this.limit = limit;
        this.windowSizeMs = windowSizeMs;
        this.clock = clock;
    }

    @Override
    public RequestStatus allowRequest(String apiKey) {
        long currentTimeMillis = Instant.now(clock).toEpochMilli();
        Deque<Long> timestamps = userLogs.computeIfAbsent(apiKey,
                k -> new ConcurrentLinkedDeque<>());

        synchronized (timestamps) {
            long windowStartBoundary = currentTimeMillis - windowSizeMs;

            // 1. Clean up: Remove expired timestamps
            while (!timestamps.isEmpty() && timestamps.peekFirst() < windowStartBoundary) {
                timestamps.removeFirst();
            }

            if (timestamps.size() < limit) {
                timestamps.addLast(currentTimeMillis);
                return RequestStatus.ALLOWED;
            }
            return RequestStatus.THROTTLED;
        }
    }
}

class SlidingWindowCounterRateLimiter implements RateLimiter {
    private final Map<String, WindowPair> userWindows = new ConcurrentHashMap<>();
    private final int limit;
    private final long windowSizeMs;
    private final Clock clock;

    public SlidingWindowCounterRateLimiter(int limit, long windowSizeMs, Clock clock) {
        this.limit = limit;
        this.windowSizeMs = windowSizeMs;
        this.clock = clock;
    }

    private static class WindowPair {
        long currentWindowStartTime;
        int currentWindowCount;
        int prevWindowCount;

        WindowPair(long startTime) {
            this.currentWindowStartTime = startTime;
            this.currentWindowCount = 0;
            this.prevWindowCount = 0;
        }
    }

    @Override
    public RequestStatus allowRequest(String apiKey) {
        long currentTimeMillis = Instant.now(clock).toEpochMilli();
        long currentWindowStartBoundary = (currentTimeMillis / windowSizeMs) * windowSizeMs;

        WindowPair pair = userWindows.computeIfAbsent(apiKey,
                k -> new WindowPair(currentWindowStartBoundary));

        synchronized (pair) {
            // 1. Window Transition Logic
            if (currentWindowStartBoundary > pair.currentWindowStartTime) {
                pair.prevWindowCount = pair.currentWindowCount;
                pair.currentWindowCount = 0;
                pair.currentWindowStartTime = currentWindowStartBoundary;
            }

            // 2. Calculate Weighted Count
            double currentWindowFraction = (currentTimeMillis - pair.currentWindowStartTime) / (double) windowSizeMs;
            long weightedCount = (long) (pair.prevWindowCount * (1.0 - currentWindowFraction) + pair.currentWindowCount);

            // 3. Check and Increment
            if (weightedCount < limit) {
                pair.currentWindowCount++;
                return RequestStatus.ALLOWED;
            }
            return RequestStatus.THROTTLED;
        }
    }
}

public class RateLimitingSystem {

    private void runRequests(RateLimiter limiter, String apiKey, int count, String limiterName) {
        System.out.printf("\nTesting %s for %s\n", limiterName, apiKey);
        for (int i = 1; i <= count; i++) {
            RequestStatus status = limiter.allowRequest(apiKey);
            System.out.printf("Request %d: %s\n", i, status);
        }
    }

    private void pause(long milliseconds) throws InterruptedException {
        TimeUnit.MILLISECONDS.sleep(milliseconds);
    }

    public void demoTokenBucket(String apiKey, int limit, long windowMs, Clock clock) throws InterruptedException {
        long refillRatePerSecond = (long) (limit * 1000.0 / windowMs);
        TokenBucketRateLimiter tokenLimiter = new TokenBucketRateLimiter(limit, refillRatePerSecond, clock);

        System.out.println("Token Bucket Rate Limiter");

        runRequests(tokenLimiter, apiKey, limit + 1, "Token Bucket (Burst 1)");

        long waitTime = windowMs / 2;
        System.out.printf("\nPausing for %dms to allow token refill\n", waitTime);
        pause(waitTime);

        runRequests(tokenLimiter, apiKey, 3, "Token Bucket (Burst 2)");
    }

    public void demoLeakyBucket(String apiKey, int limit, Clock clock) throws InterruptedException {
        int capacity = 5;
        int leakRate = 2;
        LeakyBucketRateLimiter leakyLimiter = new LeakyBucketRateLimiter(capacity, leakRate, clock);

        System.out.println("Leaky Bucket Rate Limiter");
        System.out.printf("Capacity: %d, Leak Rate: %d req/s.\n", capacity, leakRate);

        runRequests(leakyLimiter, apiKey, 6, "Leaky Bucket (Initial Flood)");

        long waitTime = 1000;
        System.out.printf("\nPausing for %dms (2 requests should leak)\n", waitTime);
        pause(waitTime);

        runRequests(leakyLimiter, apiKey, 3, "Leaky Bucket (Post Leak Check)");
    }

    public void demoFixedWindow(String apiKey, int limit, long windowMs, Clock clock) throws InterruptedException {
        FixedWindowRateLimiter fixedLimiter = new FixedWindowRateLimiter(limit, windowMs, clock);

        System.out.println("Fixed Window Counter");

        runRequests(fixedLimiter, apiKey, limit, "Fixed Window (Window 1, Full Limit)");

        long waitBeforeReset = windowMs - 50;
        System.out.printf("\nPausing for %dms (just before reset)\n", waitBeforeReset);
        pause(waitBeforeReset);

        runRequests(fixedLimiter, apiKey, 1, "Fixed Window (Window 1, Throttled)");

        long waitAfterReset = 100;
        System.out.printf("\nPausing for %dms (to cross reset boundary). Window 2 starts now\n", waitAfterReset);
        pause(waitAfterReset);

        runRequests(fixedLimiter, apiKey, limit, "Fixed Window (Window 2, Full Burst)");
    }

    public void demoSlidingWindowLog(String apiKey, int limit, long windowMs, Clock clock) throws InterruptedException {
        SlidingWindowLogRateLimiter logLimiter = new SlidingWindowLogRateLimiter(limit, windowMs, clock);

        System.out.println("Sliding Window Log");

        runRequests(logLimiter, apiKey, 4, "Sliding Log (Initial Requests)");

        long waitTime = (long) (windowMs * 0.75);
        System.out.printf("\nPausing for %dms (75%% through window)\n", waitTime);
        pause(waitTime);

        runRequests(logLimiter, apiKey, 3, "Sliding Log");
    }

    public void demoSlidingWindowCounter(String apiKey, int limit, long windowMs, Clock clock) throws InterruptedException {
        SlidingWindowCounterRateLimiter slidingLimiter = new SlidingWindowCounterRateLimiter(limit, windowMs, clock);

        System.out.println("\nSliding Window Counter\n");

        runRequests(slidingLimiter, apiKey, 4, "Sliding Counter (Window 1, Part 1)");

        long waitTime = (long) (windowMs * 0.75);
        System.out.printf("\nPausing for %dms (75%% through window)\n", waitTime);
        pause(waitTime);

        runRequests(slidingLimiter, apiKey, 3, "Sliding Counter (Window 1/2 Transition)");
    }

    public static void main(String[] args) throws InterruptedException {
        final Clock clock = Clock.systemUTC();
        APIRequest request = new APIRequest("apiKey1", "/api/v1/resources", Instant.now(clock));
        final int LIMIT = 5;
        final long WINDOW_MS = 2000; // 2 seconds

        System.out.println(LIMIT + " requests per " + (WINDOW_MS / 1000.0) + " seconds\n");

        RateLimitingSystem o = new RateLimitingSystem();

        o.demoTokenBucket(request.getApiKey(), LIMIT, WINDOW_MS, clock);
        System.out.println("\n");

        o.demoLeakyBucket(request.getApiKey(), LIMIT, clock);
        System.out.println("\n");

        o.demoFixedWindow(request.getApiKey(), LIMIT, WINDOW_MS, clock);
        System.out.println("\n");

        o.demoSlidingWindowLog(request.getApiKey(), LIMIT, WINDOW_MS, clock);
        System.out.println("\n");

        o.demoSlidingWindowCounter(request.getApiKey(), LIMIT, WINDOW_MS, clock);
    }
}
