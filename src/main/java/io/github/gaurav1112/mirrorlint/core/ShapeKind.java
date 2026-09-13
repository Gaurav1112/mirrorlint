package io.github.gaurav1112.mirrorlint.core;

/**
 * How a shape's member names came to be written down. The distinction matters for pairing:
 * only a bare enumeration of names is a hand-maintained copy of someone else's member list.
 *
 * <ul>
 *   <li>{@link #TRUTH} — a declaration that names its members: an interface, a type alias'
 *       object type, a string-literal union, an enum.
 *   <li>{@link #LIST} — a bare enumeration of names and nothing else: a string array, a
 *       {@code List.of(...)}, a destructuring pattern. It exists only to restate a member set,
 *       so a name missing from it is drift.
 *   <li>{@link #LITERAL} — the key set of an object literal. The keys name members, but the
 *       literal is a <em>value</em>: defaults tables, serializer outputs and partial configs all
 *       legitimately carry a subset of their type's members, so its gaps are not drift.
 * </ul>
 */
public enum ShapeKind { LIST, LITERAL, TRUTH }
