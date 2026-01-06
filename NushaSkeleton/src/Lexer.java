import AST.*;
import java.util.LinkedList;
import java.util.HashMap;

public class Lexer {
    private final TextManager textManager;
    private final HashMap<String, Token.TokenTypes> keywords = new HashMap<>();
    private final HashMap<String, Token.TokenTypes> punctuation = new HashMap<>();
    //This keep track of the current line and column while scanning
    private int line = 1;
    private int column = 0;
    //This counts how many indentation levels we are in
    private int currentIndent = 0;
    /**
     * @param input the full source code text to tokenize
     */
    public Lexer(String input) {
        //We add a newline at the end so the last line is also processed
        this.textManager = new TextManager(input + "\n");
        //This map lowercase keywords stings to their token types so we can easily check if a word is a keyword later.
        keywords.put("var", Token.TokenTypes.VAR);
        keywords.put("unique", Token.TokenTypes.UNIQUE);
        keywords.put("yields", Token.TokenTypes.YIELDS);
        //This map is for punctuation and operators
        punctuation.put("=", Token.TokenTypes.EQUAL);
        punctuation.put("{", Token.TokenTypes.LEFTCURLY);
        punctuation.put("}", Token.TokenTypes.RIGHTCURLY);
        punctuation.put("[", Token.TokenTypes.LEFTBRACE);
        punctuation.put("]", Token.TokenTypes.RIGHTBRACE);
        punctuation.put(":", Token.TokenTypes.COLON);
        punctuation.put(",", Token.TokenTypes.COMMA);
        punctuation.put(".", Token.TokenTypes.DOT);
    }
    //Looks at the next char without moving forward
    private char peek() {
        return textManager.PeekCharacter();
    }
    //Looks ahead by a distance without moving forward
    private char peek(int d) {
        return textManager.PeekCharacter(d);
    }
    //Gets the current char and moves forward, it also updates line and column
    private char get() {
        char c = textManager.GetCharacter();
        if (c == '\n') {
            line++;
            column = 0;
        } else {
            column++;
        }
        return c;
    }
    //Skips a single carriage returns "\r" if present, so the code works with Windows line endings (\r\n)
    private void ignoreCR() {
        if (peek() == '\r') {
            textManager.GetCharacter();
        }
    }
    /**
     * This scans the entire input and returns a list of tokens
     * @return a LinkedList of token objects
     * @throws SyntaxErrorException if something invalid is found
     */
    public LinkedList<Token> Lex() throws SyntaxErrorException {
        LinkedList<Token> tokens = new LinkedList<>();
        while (!textManager.isAtEnd()) {
            //We ignore stray '/r' to be safe
            ignoreCR();
            char c = peek();
            //If we see a new line, we use Newline and then measure indentaion for the next one
            if (c == '\n') {
                get();
                tokens.add(new Token(Token.TokenTypes.NEWLINE, line, column));
                //We count space or tabs to know how much the next line is indented
                int spaces = 0;
                //This ignore any CR before the space or tabs
                while (peek() == '\r') textManager.GetCharacter();
                //This counts spaces as 1 and tabs as 4
                while (!textManager.isAtEnd()) {
                    char n = peek();
                    if (n == ' ') {
                        spaces++; get();
                    } else if (n == '\t') {
                        spaces += 4; get();
                    } else if (n == '\r') {
                        textManager.GetCharacter();
                    } else {
                        break;
                    }
                }
                //This identaiton must be multiple of 4
                if (spaces % 4 != 0) {
                    throw new SyntaxErrorException("Indentation not multiple of 4", line, column);
                }
                //We use one Idenentaion per level increses, and one Dedent per level
                int newIndent = spaces / 4;
                while (newIndent > currentIndent) {
                    tokens.add(new Token(Token.TokenTypes.INDENT, line, column));
                    currentIndent++;
                }
                while (newIndent < currentIndent) {
                    tokens.add(new Token(Token.TokenTypes.DEDENT, line, column));
                    currentIndent--;
                }
                continue;
            }
            //If a word starts with a letter we read the full word
            if (isLetter(c)) {
                tokens.add(readWord());
                continue;
            }
            //If it starts with a digit, or a '.' followed by a digit, we read a number
            if (isDigit(c) || (c == '.' && isDigit(peek(1)))) {
                tokens.add(readNumber());
                continue;
            }
            //If the char matches our punctuation or 2 char operation, we read punctuation
            if (punctuation.containsKey(String.valueOf(c)) ||
                    punctuation.containsKey("" + c + (textManager.isAtEnd() ? "" : peek(1))) ||
                    (c == '!' && peek(1) == '=') || (c == '=' && peek(1) == '>')) {
                tokens.add(readPunctuation());
                continue;
            }
            //This skips any other Whitespace like tabs or spaces
            if (Character.isWhitespace(c) || c == '\r') {
                get();
                continue;
            }
            throw new SyntaxErrorException("Unexpected character: " + c, line, column);
        }
        //This will close any open indentation with Dedent after the end of the file
        while (currentIndent > 0) {
            tokens.add(new Token(Token.TokenTypes.DEDENT, line, column));
            currentIndent--;
        }
        return tokens;
    }
    /**
     * Reads a word starting with a letter this may include digits after.
     * Returns a keyword token if it matches, otherwose an identifier.
     * @return a token representing the word just read
     */
    private Token readWord() {
        int startLine = line;
        int startCol  = column;
        StringBuilder sb = new StringBuilder();
        sb.append(get());
        while (!textManager.isAtEnd()) {
            char n = peek();
            if (isLetter(n) || isDigit(n)) {
                sb.append(get());
            } else {
                break;
            }
        }
        String lexeme = sb.toString();
        if (keywords.containsKey(lexeme)) {
            return new Token(keywords.get(lexeme), startLine, startCol);
        }
        return new Token(Token.TokenTypes.IDENTIFIER, startLine, startCol, lexeme);
    }
    /**
     * Reads a number
     * @return a number token with the numeric value
     * @throws SyntaxErrorException if more then one '.' is found.
     */
    private Token readNumber() throws SyntaxErrorException {
        int startLine = line;
        int startCol  = column;
        StringBuilder sb = new StringBuilder();
        boolean seenDot = false;
        while (!textManager.isAtEnd()) {
            char n = peek();
            if (isDigit(n)) {
                sb.append(get());
            } else if (n == '.') {
                if (seenDot) {
                    // Second dot inside the same number → invalid format like 1.2.3
                    throw new SyntaxErrorException("Invalid number format", startLine, startCol);
                }
                seenDot = true;
                sb.append(get());
            } else {
                break;
            }
        }
        return new Token(Token.TokenTypes.NUMBER, startLine, startCol, sb.toString());
    }
    /**
     * Reads puntuation or operator such as =, !=, =>, etc
     * @return a punctuation token
     * @throws SyntaxErrorException if the punctuation is unknown
     */
    private Token readPunctuation() throws SyntaxErrorException {
        int startLine = line;
        int startCol  = column;
        char first = get();
        if (!textManager.isAtEnd()) {
            String two = "" + first + peek();
            if (two.equals("!=")) {
                get();
                return new Token(Token.TokenTypes.NOTEQUAL, startLine, startCol);
            }
            if (two.equals("=>")) {
                get();
                return new Token(Token.TokenTypes.YIELDS, startLine, startCol);
            }
            if (punctuation.containsKey(two)) {
                get();
                return new Token(punctuation.get(two), startLine, startCol);
            }
        }
        String one = String.valueOf(first);
        if (punctuation.containsKey(one)) {
            return new Token(punctuation.get(one), startLine, startCol);
        }
        throw new SyntaxErrorException("Unknown punctuation: " + first, startLine, startCol);
    }
    /**
     * Checks if a char is a letter (A-z or a-z)
     * Used to detect valid starting char for wards
     * @param c the char to check
     * @return true if the char is a letter
     */
    private boolean isLetter(char c) {
        //This only treats A-Z and a-z as letters to keep rules simple for now
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
    }
    /**
     * Checks if a char is a digit (0-9)
     * Used to allow digits inside identifiers
     * @param c the character to check
     * @return true if the char is a digit
     */
    private boolean isDigit(char c) {
        //Allow 0-9 digits, used only inside identifiers for lexer1
        return (c >= '0' && c <= '9');
    }
}
