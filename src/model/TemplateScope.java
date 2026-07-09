package model;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

/** Stack of in-scope Jinja variable names while walking a template AST. */
public final class TemplateScope {
    private final Deque<Set<String>> stack = new ArrayDeque<>();

    public TemplateScope() {
        stack.push(new HashSet<>());
    }

    public void push(Set<String> names) {
        Set<String> frame = new HashSet<>(current());
        frame.addAll(names);
        stack.push(frame);
    }

    public void pop() {
        if (stack.size() > 1) stack.pop();
    }

    public boolean contains(String name) {
        return current().contains(name);
    }

    public Set<String> current() {
        return stack.peek();
    }
}
