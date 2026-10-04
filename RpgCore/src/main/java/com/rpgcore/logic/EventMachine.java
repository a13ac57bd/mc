package com.rpgcore.logic;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * World event state machine and scheduler (TECH_SPEC §10).
 * Each event definition runs at most once per world. At most {@code maxConcurrent} run at once; the rest wait in a queue.
 */
public final class EventMachine {

    /**
     * One state. {@code days} &lt;= 0 means "until the goal is met". A state with neither {@code next}
     * nor {@code onSuccess} is terminal and carries the event's {@code outcome}.
     */
    public record State(String id, int days, String next, String onSuccess, String outcome) {
        public boolean terminal() {
            return next == null && onSuccess == null;
        }
    }

    public record Def(String id, String initial, Map<String, State> states) {
        public State state(String id) {
            return states.get(id);
        }
    }

    public static final class Instance {
        public final String defId;
        public String state;
        public int daysInState;

        public Instance(String defId, String state, int daysInState) {
            this.defId = defId;
            this.state = state;
            this.daysInState = daysInState;
        }
    }

    /** A state change; {@code outcome} is non-null when the event finished. */
    public record Transition(String defId, String from, String to, String outcome) {}

    public enum StartResult { STARTED, QUEUED, ALREADY_KNOWN, UNKNOWN }

    private final Map<String, Def> defs;
    private final int maxConcurrent;
    private final List<Instance> active = new ArrayList<>();
    private final Deque<String> queue = new ArrayDeque<>();
    /** defId -> outcome for finished events. */
    private final Map<String, String> finished = new LinkedHashMap<>();

    public EventMachine(Map<String, Def> defs, int maxConcurrent) {
        this.defs = defs;
        this.maxConcurrent = Math.max(1, maxConcurrent);
    }

    public List<Instance> active() {
        return Collections.unmodifiableList(active);
    }

    public Deque<String> queue() {
        return queue;
    }

    public Map<String, String> finished() {
        return finished;
    }

    public Instance get(String defId) {
        for (Instance i : active) if (i.defId.equals(defId)) return i;
        return null;
    }

    /** Restore saved state (no transitions are emitted). */
    public void restore(List<Instance> savedActive, List<String> savedQueue, Map<String, String> savedFinished) {
        active.clear();
        queue.clear();
        finished.clear();
        for (Instance i : savedActive) if (defs.containsKey(i.defId) && defs.get(i.defId).state(i.state) != null) active.add(i);
        for (String q : savedQueue) if (defs.containsKey(q)) queue.add(q);
        finished.putAll(savedFinished);
    }

    public StartResult trigger(String defId, List<Transition> out) {
        Def def = defs.get(defId);
        if (def == null) return StartResult.UNKNOWN;
        if (finished.containsKey(defId) || get(defId) != null || queue.contains(defId)) return StartResult.ALREADY_KNOWN;
        if (active.size() >= maxConcurrent) {
            queue.add(defId);
            return StartResult.QUEUED;
        }
        start(def, out);
        return StartResult.STARTED;
    }

    private void start(Def def, List<Transition> out) {
        Instance inst = new Instance(def.id(), def.initial(), 0);
        active.add(inst);
        out.add(new Transition(def.id(), null, def.initial(), null));
        State s = def.state(def.initial());
        if (s != null && s.terminal()) finish(inst, s, out);
    }

    /** Advance every active event by one in-game day. */
    public List<Transition> dailyTick() {
        List<Transition> out = new ArrayList<>();
        for (Instance inst : new ArrayList<>(active)) {
            Def def = defs.get(inst.defId);
            State s = def.state(inst.state);
            inst.daysInState++;
            if (s.days() > 0 && inst.daysInState >= s.days() && s.next() != null) {
                move(inst, def, s.next(), out);
            }
        }
        startQueued(out);
        return out;
    }

    /** The player-intervention goal of the current state was met. */
    public List<Transition> goalMet(String defId) {
        List<Transition> out = new ArrayList<>();
        Instance inst = get(defId);
        if (inst == null) return out;
        Def def = defs.get(defId);
        State s = def.state(inst.state);
        if (s.onSuccess() != null) move(inst, def, s.onSuccess(), out);
        startQueued(out);
        return out;
    }

    /** Force a state (debug command). */
    public List<Transition> force(String defId, String stateId) {
        List<Transition> out = new ArrayList<>();
        Instance inst = get(defId);
        Def def = defs.get(defId);
        if (inst == null || def == null || def.state(stateId) == null) return out;
        move(inst, def, stateId, out);
        startQueued(out);
        return out;
    }

    private void move(Instance inst, Def def, String to, List<Transition> out) {
        State target = def.state(to);
        if (target == null) return;
        String from = inst.state;
        inst.state = to;
        inst.daysInState = 0;
        out.add(new Transition(def.id(), from, to, null));
        if (target.terminal()) finish(inst, target, out);
    }

    private void finish(Instance inst, State terminal, List<Transition> out) {
        active.remove(inst);
        String outcome = terminal.outcome() == null ? terminal.id() : terminal.outcome();
        finished.put(inst.defId, outcome);
        out.add(new Transition(inst.defId, terminal.id(), null, outcome));
    }

    private void startQueued(List<Transition> out) {
        while (active.size() < maxConcurrent && !queue.isEmpty()) {
            Def def = defs.get(queue.poll());
            if (def != null) start(def, out);
        }
    }

    /** Validation helper for loaders. */
    public static List<String> validate(Def def) {
        List<String> errors = new ArrayList<>();
        if (def.state(def.initial()) == null) errors.add("initial state " + def.initial() + " missing");
        Set<String> seen = new LinkedHashSet<>();
        for (State s : def.states().values()) {
            seen.add(s.id());
            if (s.next() != null && def.state(s.next()) == null) errors.add(s.id() + ": next " + s.next() + " missing");
            if (s.onSuccess() != null && def.state(s.onSuccess()) == null) errors.add(s.id() + ": on_success " + s.onSuccess() + " missing");
            if (s.next() != null && s.days() <= 0) errors.add(s.id() + ": next requires days > 0");
        }
        boolean anyTerminal = def.states().values().stream().anyMatch(State::terminal);
        if (!anyTerminal) errors.add("no terminal state");
        return errors;
    }
}
