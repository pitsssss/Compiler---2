package model;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Aggregated product-record schema and per-field type consistency. */
public final class ProductSchema {
    private final Set<String> allFields = new LinkedHashSet<>();
    private final Map<String, Set<ValueType>> typesByField = new LinkedHashMap<>();
    private final List<Set<String>> rowFieldSets = new java.util.ArrayList<>();

    public void recordRow(Map<String, ValueType> row) {
        rowFieldSets.add(new LinkedHashSet<>(row.keySet()));
        allFields.addAll(row.keySet());
        for (Map.Entry<String, ValueType> e : row.entrySet()) {
            typesByField.computeIfAbsent(e.getKey(), k -> new LinkedHashSet<>()).add(e.getValue());
        }
    }

    public Set<String> getAllFields() { return allFields; }
    public List<Set<String>> getRowFieldSets() { return List.copyOf(rowFieldSets); }
    public Map<String, Set<ValueType>> getTypesByField() { return typesByField; }

    public boolean hasInconsistentSchemas() {
        if (rowFieldSets.size() <= 1) return false;
        Set<String> first = rowFieldSets.get(0);
        for (Set<String> row : rowFieldSets) {
            if (!row.equals(first)) return true;
        }
        return false;
    }

    public boolean hasInconsistentTypes() {
        for (Set<ValueType> types : typesByField.values()) {
            if (types.size() > 1) return true;
        }
        return false;
    }
}
