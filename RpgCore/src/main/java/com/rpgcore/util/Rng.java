package com.rpgcore.util;

import net.minecraft.util.RandomSource;

import java.util.List;
import java.util.random.RandomGenerator;

/** Bridges Minecraft's RandomSource to java.util.random for the pure logic classes. */
public final class Rng {
    private Rng() {}

    public static RandomGenerator of(RandomSource source) {
        return source::nextLong;
    }

    public static <T> T pick(List<T> list, RandomSource random) {
        return list.isEmpty() ? null : list.get(random.nextInt(list.size()));
    }
}
