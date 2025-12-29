# Testing Guide

This guide covers how to run and write tests for MALLET.

## Prerequisites

- Java 17 or higher
- Maven 3.6 or higher

## Running Tests

### Run All Tests

```bash
mvn test
```

### Run a Specific Test Class

```bash
mvn test -Dtest=TestAlphabet
```

### Run Multiple Test Classes

```bash
mvn test -Dtest=TestAlphabet,TestLabelAlphabet
```

### Run Tests in a Specific Package

```bash
mvn test -Dtest="cc.mallet.types.*"
```

### Run Tests Matching a Pattern

```bash
mvn test -Dtest="*CRF*"
```

### Skip Tests During Build

```bash
mvn package -DskipTests
```

## Test Coverage

MALLET uses JaCoCo for code coverage reporting.

### Generate Coverage Report

Coverage reports are automatically generated when running tests:

```bash
mvn test
```

View the HTML report at: `target/site/jacoco/index.html`

### Generate Coverage Report Separately

```bash
mvn jacoco:report
```

## Writing Tests

### Test Location

All tests are located in `src/test/java/` following the standard Maven directory structure. Tests are in the same package as the classes they test.

```
src/
├── main/java/cc/mallet/types/Alphabet.java
└── test/java/cc/mallet/types/TestAlphabet.java
```

### Test Framework

MALLET uses JUnit 4. Test classes should:

1. Import JUnit annotations and assertions:
   ```java
   import org.junit.Test;
   import org.junit.Before;
   import org.junit.After;
   import static org.junit.Assert.*;
   ```

2. Annotate test methods with `@Test`:
   ```java
   @Test
   public void testSomething() {
       // test code
   }
   ```

3. Use `@Before` for setup and `@After` for teardown:
   ```java
   @Before
   public void setUp() {
       // initialization code
   }
   ```

### Test Naming Convention

- Test classes: `Test` prefix (e.g., `TestAlphabet.java`)
- Test methods: `test` prefix (e.g., `testNotFound()`)

### Example Test

```java
package cc.mallet.types;

import org.junit.Test;
import static org.junit.Assert.*;

public class TestAlphabet {

    @Test
    public void testLookupIndex() {
        Alphabet dict = new Alphabet();
        dict.lookupIndex("TEST1");
        dict.lookupIndex("TEST2");

        assertEquals(2, dict.size());
        assertEquals(-1, dict.lookupIndex("TEST3", false));
    }
}
```

### Testing Serialization

Use the `TestSerializable.cloneViaSerialization()` utility to test serialization:

```java
import cc.mallet.types.TestSerializable;

@Test
public void testSerialization() throws Exception {
    MyClass original = new MyClass();
    MyClass clone = (MyClass) TestSerializable.cloneViaSerialization(original);
    assertEquals(original, clone);
}
```

## Test Resources

Test resources (data files, configuration, etc.) are located in `src/test/resources/`.

To access test resources in your tests:

```java
// For files
File file = new File("src/test/resources/mydata.txt");

// Or using classpath
InputStream is = getClass().getResourceAsStream("/mydata.txt");
```

## Continuous Integration

Tests are automatically run on pull requests via GitHub Actions. See `.github/workflows/maven-publish.yml` for the CI configuration.

## Troubleshooting

### Tests Fail with OutOfMemoryError

Increase Maven's heap size:

```bash
export MAVEN_OPTS="-Xmx2g"
mvn test
```

### Tests Fail Due to Logging

Logging configuration for tests is in `src/test/resources/logging.properties`.

### Floating Point Comparison Failures

When comparing floating-point numbers, always use a delta:

```java
assertEquals(expected, actual, 0.0001);
```
