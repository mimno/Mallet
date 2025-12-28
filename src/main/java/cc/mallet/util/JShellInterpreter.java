/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */

package cc.mallet.util;

import jdk.jshell.JShell;
import jdk.jshell.Snippet;
import jdk.jshell.SnippetEvent;

import java.util.List;

/**
 * JShell-based interpreter replacing the deprecated BeanShell implementation.
 *
 * <p><b>Important Limitations:</b></p>
 * <ul>
 *   <li>JShell evaluates code snippets and returns String representations, not actual objects</li>
 *   <li>Complex object construction should be done programmatically</li>
 *   <li>The {@code set()} method only supports primitives, String, and String arrays</li>
 * </ul>
 *
 * <p>This class implements {@link AutoCloseable} and should be used with try-with-resources.</p>
 */
public class JShellInterpreter implements AutoCloseable {
    private final JShell jshell;

    public JShellInterpreter(String prefixCommands) {
        this.jshell = JShell.builder().build();

        try {
            // Import standard Java packages
            eval("import java.util.*;");
            eval("import java.util.regex.*;");
            eval("import java.io.*;");

            // Import MALLET packages
            eval("import cc.mallet.types.*;");
            eval("import cc.mallet.pipe.*;");
            eval("import cc.mallet.pipe.iterator.*;");
            eval("import cc.mallet.pipe.tsf.*;");
            eval("import cc.mallet.classify.*;");
            eval("import cc.mallet.extract.*;");
            eval("import cc.mallet.fst.*;");
            eval("import cc.mallet.optimize.*;");

            if (prefixCommands != null) {
                eval(prefixCommands);
            }
        } catch (RuntimeException e) {
            throw new IllegalStateException("JShell initialization failed", e);
        } catch (Exception e) {
            throw new IllegalStateException("JShell initialization failed: " + e.getMessage(), e);
        }
    }

    public JShellInterpreter() {
        this(null);
    }

    /**
     * Evaluates a code snippet and returns the result.
     *
     * @param code The code to evaluate
     * @return The result of the evaluation, or null if no result
     * @throws Exception if evaluation fails
     */
    public Object eval(String code) throws Exception {
        List<SnippetEvent> events = jshell.eval(code);

        if (events.isEmpty()) {
            return null;
        }

        SnippetEvent lastEvent = events.get(events.size() - 1);

        if (lastEvent.exception() != null) {
            throw lastEvent.exception();
        }

        if (lastEvent.status() == Snippet.Status.VALID && lastEvent.value() != null) {
            return lastEvent.value();
        }

        return null;
    }

    /**
     * Sets a variable in the JShell context.
     *
     * <p><b>Supported types:</b> String, Integer, Long, Double, Boolean, String[], null</p>
     *
     * @param name The variable name
     * @param value The value to set
     * @throws Exception if setting fails
     * @throws UnsupportedOperationException if value type is not supported
     */
    public void set(String name, Object value) throws Exception {
        if (value == null) {
            eval(String.format("Object %s = null;", name));
        } else if (value instanceof String) {
            eval(String.format("String %s = \"%s\";", name, escapeString((String) value)));
        } else if (value instanceof Integer) {
            eval(String.format("int %s = %d;", name, value));
        } else if (value instanceof Long) {
            eval(String.format("long %s = %dL;", name, value));
        } else if (value instanceof Double) {
            eval(String.format("double %s = %f;", name, value));
        } else if (value instanceof Boolean) {
            eval(String.format("boolean %s = %s;", name, value));
        } else if (value instanceof String[]) {
            String[] arr = (String[]) value;
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("String[] %s = new String[]{", name));
            for (int i = 0; i < arr.length; i++) {
                if (i > 0) sb.append(", ");
                sb.append("\"").append(escapeString(arr[i])).append("\"");
            }
            sb.append("};");
            eval(sb.toString());
        } else {
            throw new UnsupportedOperationException(
                    "JShell does not support direct injection of complex objects. " +
                            "Object type: " + value.getClass().getName()
            );
        }
    }

    /**
     * Escapes special characters in strings for Java string literals.
     */
    private String escapeString(String str) {
        return str.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * Closes the JShell instance and releases resources.
     * Should be called when interpreter is no longer needed, or use try-with-resources.
     */
    @Override
    public void close() {
        if (jshell != null) {
            jshell.close();
        }
    }
}
