import AST.Token;
import java.util.LinkedList;
import java.util.Optional;
public class TokenManager {
    private final LinkedList<Token> tokens;
    /**
     * @param tokens the full list of tokens from the lexer
     */
    public TokenManager(LinkedList<Token> tokens) {
        this.tokens = tokens;
    }
    public int getLine() {
        if (tokens.isEmpty()) {
            return -1;
        }
        return tokens.getFirst().LineNumber;
    }
    public int getColumn() {
        if (tokens.isEmpty()) {
            return -1;
        }
        return tokens.getFirst().ColumnNumber;
    }
    public int getCurrentLine() {
        return getLine();
    }
    public int getCurrentColumnNumber() {
        return getColumn();
    }
    public boolean Done() {
        return tokens.isEmpty();
    }
    /**
     * if the next token is of the expected type, remove and return it else, return Optional.empty()
     * @param t the expected token type
     * @return the token if it matches, else empty
     */
    public Optional<Token> MatchAndRemove(Token.TokenTypes t) {
        if (tokens.isEmpty()) {
            return Optional.empty();
        }
        Token first = tokens.getFirst();
        if (first.Type == t) {
            tokens.removeFirst();
            return Optional.of(first);
        }
        return Optional.empty();
    }
    /**
     * looks ahead at the next token without consuming it
     * @param i index from the front (0= current token)
     * @return the token at the position, or empty if out of range
     */
    public Optional<Token> Peek(int i) {
        if (i < 0 || i >= tokens.size()) {
            return Optional.empty();
        }
        return Optional.of(tokens.get(i));
    }
}