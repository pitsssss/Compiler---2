package model;

/** Inferred runtime type of a Python value or template context variable. */
public enum ValueType {
    UNKNOWN,
    NONE,
    BOOLEAN,
    NUMBER,
    STRING,
    LIST,
    DICT,
    FUNCTION,
    MODULE
}
