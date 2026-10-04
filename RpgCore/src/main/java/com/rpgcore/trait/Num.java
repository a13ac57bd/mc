package com.rpgcore.trait;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** A numeric parameter that is either a constant or "roll" / "roll*k" (the item's effective roll). */
public record Num(double value, boolean useRoll) {

    public static Num of(double v) {
        return new Num(v, false);
    }

    public double get(ActiveTrait trait) {
        return useRoll ? trait.roll() * value : value;
    }

    public static Num parse(JsonObject o, String key, double def) {
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonPrimitive()) return of(def);
        if (e.getAsJsonPrimitive().isNumber()) return of(e.getAsDouble());
        String s = e.getAsString().trim();
        if (s.equals("roll")) return new Num(1.0, true);
        if (s.startsWith("roll*")) return new Num(Double.parseDouble(s.substring(5)), true);
        throw new IllegalArgumentException("bad number for " + key + ": " + s);
    }
}
