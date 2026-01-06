# Nusha Skeleton (Course Project)

Java implementation of the Nusha language skeleton and interpreter components (lexer, parser, AST, interpreter tests).

> **Note:** This repository contains coursework/project code. Please follow your course’s academic integrity policy.

## Project Structure

- `src/` — Java source code
  - `AST/` — AST node definitions
  - `Lexer.java` — lexer
  - `NushaFall2025Parser.java` — parser
  - `Interpreter.java` — interpreter
  - `*Tests.java` — test suite(s)

## How to Run

### Using IntelliJ IDEA
1. Open the project folder in IntelliJ.
2. Ensure a JDK is configured (Java 17+ recommended unless your course specifies otherwise).
3. Run the test files (e.g., `InterpreterTests.java`) from the IDE.

### Command Line (basic compile)
From the project root:
```bash
javac -d out $(find src -name "*.java")
