public class TextManager {
    private final String text;
    private int position;

    public TextManager(String input) {
        this.position = 0;
        this.text = (input); // We assume the input is not null for Lexer1
    }
    // This checks if we have reached the end of the text
    public boolean isAtEnd() {
        return position >= text.length();
    }
    // Looks at the current character without moving forward
    public char PeekCharacter() {
        if (position >= text.length()) {
            return '\0';
        }
        else return text.charAt(position);
    }
    /**
     * Returns the char at a distance from the current position without advancing the position
     * @param dist how many char ahead to look
     * @return the char at position + dist, or '/0' if out of range
     */
    // This looks ahead by a distance without moving the position, and it is useful later while checking multi char token
    public char PeekCharacter(int dist) {
        int index = position + dist;
        if (index < 0 || index >= text.length()) {
            return '\0';
        }
        else return text.charAt(index);
    }
    // This gets the current char and moves the position forward by 1
    public char GetCharacter() {
        if(isAtEnd()) {
            return '\0';
        }
        else return text.charAt(position++);
    }
}
