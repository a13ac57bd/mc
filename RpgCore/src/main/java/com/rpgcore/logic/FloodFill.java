package com.rpgcore.logic;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

/** Bounded breadth-first fill used by secret walls (64), oil (256) and stacked doors. */
public final class FloodFill {
    private FloodFill() {}

    /** Returns connected members reachable from {@code start} (included if it is a member), at most {@code limit}. */
    public static <T> List<T> fill(T start, Function<T, Iterable<T>> neighbors, Predicate<T> member, int limit) {
        List<T> out = new ArrayList<>();
        if (!member.test(start) || limit <= 0) return out;
        Set<T> seen = new HashSet<>();
        Deque<T> queue = new ArrayDeque<>();
        queue.add(start);
        seen.add(start);
        while (!queue.isEmpty() && out.size() < limit) {
            T cur = queue.poll();
            out.add(cur);
            for (T n : neighbors.apply(cur)) {
                if (seen.add(n) && member.test(n)) queue.add(n);
            }
        }
        return out;
    }
}
