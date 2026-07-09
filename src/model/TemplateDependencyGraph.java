package model;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Directed graph of template {@code extends} relationships. */
public final class TemplateDependencyGraph {
    private final Map<String, String> parentOf = new LinkedHashMap<>();

    public void addEdge(String child, String parent) {
        parentOf.put(child, parent);
    }

    public String getParent(String child) {
        return parentOf.get(child);
    }

    /** Returns a cycle path (child -> ... -> child) or empty if acyclic. */
    public List<String> findCycle() {
        for (String start : parentOf.keySet()) {
            List<String> cycle = traceCycle(start);
            if (!cycle.isEmpty()) return cycle;
        }
        return List.of();
    }

    private List<String> traceCycle(String start) {
        Deque<String> path = new ArrayDeque<>();
        Set<String> onPath = new HashSet<>();
        List<String> found = new ArrayList<>();
        walk(start, path, onPath, found);
        return found;
    }

    private void walk(String node, Deque<String> path, Set<String> onPath, List<String> found) {
        if (found.size() > 0 || node == null) return;
        if (onPath.contains(node)) {
            boolean recording = false;
            for (String p : path) {
                if (p.equals(node)) recording = true;
                if (recording) found.add(p);
            }
            found.add(node);
            return;
        }
        String parent = parentOf.get(node);
        if (parent == null) return;
        onPath.add(node);
        path.addLast(node);
        walk(parent, path, onPath, found);
        path.removeLast();
        onPath.remove(node);
    }

    public Set<String> collectBlocksNeeded(String template, Map<String, Set<String>> blocksByTemplate) {
        return blocksByTemplate.getOrDefault(template, Set.of());
    }
}
