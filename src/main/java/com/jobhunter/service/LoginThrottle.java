package com.jobhunter.service;

import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Counts failed sign-ins per key (email or address) over 15 minutes. In memory, so it resets on restart and covers one instance. */
@Component
public class LoginThrottle {
    private static final long WINDOW_MS = 15 * 60 * 1000L;
    private final Map<String, Deque<Long>> fails = new ConcurrentHashMap<>();

    public boolean blocked(String key, int max) {
        Deque<Long> d = fails.get(key);
        if (d == null) return false;
        synchronized (d) {
            prune(d);
            return d.size() >= max;
        }
    }

    public void fail(String key) {
        if (fails.size() > 10_000) fails.clear(); // bound memory under attack
        Deque<Long> d = fails.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (d) {
            prune(d);
            d.addLast(System.currentTimeMillis());
        }
    }

    public void clear(String key) {
        fails.remove(key);
    }

    private static void prune(Deque<Long> d) {
        long cutoff = System.currentTimeMillis() - WINDOW_MS;
        while (!d.isEmpty() && d.peekFirst() < cutoff) d.removeFirst();
    }
}
