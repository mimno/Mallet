package cc.mallet.util;
import junit.framework.TestCase;
import jdk.jshell.JShell;
import jdk.jshell.Snippet;
import jdk.jshell.SnippetEvent;

import java.util.List;

public class TestJShellCLI extends TestCase {

    public void testJShellAvailability() {
        // Verify JShell is available in JDK
        try {
            Class.forName("jdk.jshell.JShell");
        } catch (ClassNotFoundException e) {
            fail("JShell not available in current JDK");
        }
    }

    public void testBasicJShellExecution() {
        // Test that JShell can execute basic commands
        try {
            JShell shell = JShell.create();
            var events = shell.eval("1 + 1");
            Snippet.Status status = events.get(0).status();
            assertSame("Expected VALID status, got: " + status,
                    Snippet.Status.VALID, status);
            shell.close();
        } catch (Exception e) {
            fail("JShell execution failed: " + e.getMessage());
        }
    }

    public void testMalletImports() {
        // Verify Mallet classes can be imported in JShell
        try {
            JShell shell = JShell.create();
            var events = shell.eval("import cc.mallet.types.*;");
            Snippet.Status status = events.get(0).status();
            assertSame("Expected VALID status for import, got: " + status,
                    Snippet.Status.VALID, status);
            shell.close();
        } catch (Exception e) {
            fail("Mallet import in JShell failed: " + e.getMessage());
        }
    }

    public void testMultipleImports() {
        // Verify multiple Mallet package imports work
        try {
            JShell shell = JShell.create();
            List<SnippetEvent> events1 = shell.eval("import cc.mallet.types.*;");
            List<SnippetEvent> events2 = shell.eval("import cc.mallet.fst.*;");
            List<SnippetEvent> events3 = shell.eval("import cc.mallet.pipe.*;");

            assertSame(Snippet.Status.VALID, events1.get(0).status());
            assertSame(Snippet.Status.VALID, events2.get(0).status());
            assertSame(Snippet.Status.VALID, events3.get(0).status());
            shell.close();
        } catch (Exception e) {
            fail("Multiple imports failed: " + e.getMessage());
        }
    }

    public void testVariableDeclaration() {
        // Test variable declaration and usage (common BeanShell pattern)
        try {
            JShell shell = JShell.create();
            List<SnippetEvent> events1 = shell.eval("int x = 42;");
            List<SnippetEvent> events2 = shell.eval("x + 1");

            assertSame(Snippet.Status.VALID, events1.get(0).status());
            assertSame(Snippet.Status.VALID, events2.get(0).status());
            assertEquals("43", events2.get(0).value());
            shell.close();
        } catch (Exception e) {
            fail("Variable declaration/usage failed: " + e.getMessage());
        }
    }

    public void testMalletObjectCreation() {
        // Verify Mallet objects can be instantiated
        try {
            JShell shell = JShell.create();
            shell.eval("import cc.mallet.types.*;");
            List<SnippetEvent> events = shell.eval("Alphabet alpha = new Alphabet();");

            assertSame(Snippet.Status.VALID, events.get(0).status());
            shell.close();
        } catch (Exception e) {
            fail("Mallet object creation failed: " + e.getMessage());
        }
    }

    public void testErrorHandling() {
        // Verify JShell handles syntax errors gracefully
        try {
            JShell shell = JShell.create();
            List<SnippetEvent> events = shell.eval("int x = ;"); // Invalid syntax

            Snippet.Status status = events.get(0).status();
            assertTrue("Expected error status for invalid syntax",
                    status == Snippet.Status.REJECTED || status == Snippet.Status.RECOVERABLE_NOT_DEFINED);
            shell.close();
        } catch (Exception e) {
            fail("Error handling test failed: " + e.getMessage());
        }
    }

    public void testJShellStatePersistence() {
        // Verify state persists across evaluations (like BeanShell)
        try {
            JShell shell = JShell.create();
            shell.eval("int counter = 0;");
            shell.eval("counter++;");
            List<SnippetEvent> events = shell.eval("counter");

            assertEquals("1", events.get(0).value());
            shell.close();
        } catch (Exception e) {
            fail("State persistence test failed: " + e.getMessage());
        }
    }

    public void testJavaStandardLibrary() {
        // Verify standard Java library access (BeanShell compatibility)
        try {
            JShell shell = JShell.create();
            shell.eval("import java.util.*;");
            List<SnippetEvent> events = shell.eval("List<String> list = new ArrayList<>();");

            assertSame(Snippet.Status.VALID, events.get(0).status());
            shell.close();
        } catch (Exception e) {
            fail("Java standard library test failed: " + e.getMessage());
        }
    }

    public void testMethodDefinition() {
        // Test method definition (common scripting pattern)
        try {
            JShell shell = JShell.create();
            List<SnippetEvent> events1 = shell.eval("int doubleValue(int x) { return x * 2; }");
            List<SnippetEvent> events2 = shell.eval("doubleValue(21)");

            assertSame(Snippet.Status.VALID, events1.get(0).status());
            assertEquals("42", events2.get(0).value());
            shell.close();
        } catch (Exception e) {
            fail("Method definition test failed: " + e.getMessage());
        }
    }

    public void testClasspathAccess() {
        // Verify JShell can access compiled Mallet classes from classpath
        try {
            JShell shell = JShell.create();

            List<SnippetEvent> importEvents = shell.eval("import cc.mallet.types.Alphabet;");
            assertSame(Snippet.Status.VALID, importEvents.get(0).status());

            List<SnippetEvent> instanceEvents = shell.eval("Alphabet alpha = new Alphabet();");
            assertSame(Snippet.Status.VALID, instanceEvents.get(0).status());

            shell.eval("int size = alpha.size();");
            List<SnippetEvent> sizeEvents = shell.eval("size");

            assertSame(Snippet.Status.VALID, sizeEvents.get(0).status());
            assertEquals("0", sizeEvents.get(0).value());

            shell.close();
        } catch (Exception e) {
            fail("Classpath access test failed: " + e.getMessage());
        }
    }

}
