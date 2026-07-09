package model;

import java.util.LinkedHashMap;
import java.util.Map;

/** Maps template context variable names to inferred {@link ValueType} for a render call. */
public final class TemplateContextModel {
    private final Map<String, ValueType> types = new LinkedHashMap<>();

    public void put(String name, ValueType type) {
        types.put(name, type);
    }

    public ValueType get(String name) {
        return types.getOrDefault(name, ValueType.UNKNOWN);
    }

    public Map<String, ValueType> all() {
        return Map.copyOf(types);
    }
}
