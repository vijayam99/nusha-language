import AST.*;
import java.util.Optional;
import java.util.LinkedList;
public class NushaFall2025Parser {
    //this will manage the tokens
    private TokenManager tmr;
    public NushaFall2025Parser() {
    }
    /**
     * this is the main phaser, we pass it all tokens, it fills the root ast and returns it
     * flow is: skip blanks -> try definition -> try variable -> try rule -> repeat
     * @param tokens the list from lexer
     * @return the nusha root
     * @throws SyntaxErrorException if the syntax is bad anywhere
     */
    public Optional<Nusha> Nusha(LinkedList<Token> tokens) throws SyntaxErrorException {
        tmr = new TokenManager(tokens);
        // root node and the three top level bucket
        Nusha root = new Nusha();
        Variables vars = new Variables();
        Definitions defs = new Definitions();
        Rules rules = new Rules();
        //remove blank lines at the very start so parsing starts clean
        SkipBlankLines();
        // this keeps going until we run out of token
        while(!tmr.Done()){
            // try a defination first if we get it, we add it to defs and keep going
            Optional<Definition> d = ParseDefinition();
            if (d.isPresent()) {
                defs.definition.add(d.get());
                SkipBlankLines();
                continue;
            }
            //if not a defination, try a variable declaration: var name : type [num]
            Optional<Variable> v = ParseDeclaration();
            if (v.isPresent()) {
                vars.variable.add(v.get());
                RequireNewLine();
                continue;
            }
            //if not var, try a rune line <expr>[=> intedented block of exprs]
            Optional<Rule> r = ParseRule();
            if (r.isPresent()) {
                rules.rule.add(r.get());
                SkipBlankLines();
                continue;
            }
            //if nothing is matched try to clear blank lines once more
            SkipBlankLines();
            //is token left then its smt unexpected
            if (!tmr.Done()) {
                Optional<Token> peek = tmr.Peek(0);
                String found = peek.map(t -> t.Type.toString()).orElse("UNKNOWN");
                throw new SyntaxErrorException("Unexpected token after declarations: " + found, tmr.getLine(), tmr.getColumn());
            }
            break;
        }
        //clean up
        SkipBlankLines();

        // this fills the root object only
        root.variables = vars;
        root.definitions= defs;
        root.rules = rules;
        return Optional.of(root);
    }
    /**
     * this parses one variable declaration. like var name: type[num]
     * @return in variable node if found
     * @throws SyntaxErrorException if syntax is wrong
     */
    private Optional<Variable> ParseDeclaration() throws SyntaxErrorException {
        // this says it must start with variable
        Optional<Token> varToken = tmr.MatchAndRemove(Token.TokenTypes.VAR);
        if(varToken.isEmpty()) {
            return Optional.empty();
        }
        //This identifies the variable name
        Optional<Token> nameToken = tmr.MatchAndRemove(Token.TokenTypes.IDENTIFIER);
        if(nameToken.isEmpty()) {
            throw new SyntaxErrorException("Expected variable name", tmr.getLine(), tmr.getColumn());
        }
        // for colon
        Optional<Token> colonToken = tmr.MatchAndRemove(Token.TokenTypes.COLON);
        if(colonToken.isEmpty()) {
            throw new SyntaxErrorException("Expected ':' after the var name", tmr.getLine(), tmr.getColumn());
        }
        // for type identifier
        Optional<Token> typeToken = tmr.MatchAndRemove(Token.TokenTypes.IDENTIFIER);
        if(typeToken.isEmpty()) {
            throw new SyntaxErrorException("Expected type name", tmr.getLine(), tmr.getColumn());
        }
        // for left bracket
        Optional<Token> leftToken = tmr.MatchAndRemove(Token.TokenTypes.LEFTBRACE);
        if(leftToken.isEmpty()) {
            throw new SyntaxErrorException("Expected '['", tmr.getLine(), tmr.getColumn());
        }
        // for num
        Optional<Token> sizeToken = tmr.MatchAndRemove(Token.TokenTypes.NUMBER);
        if(sizeToken.isEmpty()) {
            throw new SyntaxErrorException("Expected number inside bracket", tmr.getLine(), tmr.getColumn());
        }
        //for  right bracket
        Optional<Token> rightToken = tmr.MatchAndRemove(Token.TokenTypes.RIGHTBRACE);
        if(rightToken.isEmpty()) {
            throw new SyntaxErrorException("Expected ']'", tmr.getLine(), tmr.getColumn());
        }
        // for variable object
        Variable v = new Variable();
        v.variableName = nameToken.get().Value.orElse("");
        v.type = typeToken.get().Value.orElse("");
        v.size = Optional.of(sizeToken.get().Value.orElse(""));
        return  Optional.of(v);
    }
    /**
     * this tries one definition
     * @return definition node if found, otherwise empty
     * @throws SyntaxErrorException if the line starts like a definition but then is malformed
     */
    private Optional<Definition> ParseDefinition() throws SyntaxErrorException {
        Optional<Token> t0 = tmr.Peek(0); // for identifier
        Optional<Token> t1 = tmr.Peek(1); // =
        Optional<Token> t2 = tmr.Peek(2);// { or [
        //if first token are not name '=' brace, we say not a defination
        if (t0.isEmpty() || t0.get().Type != Token.TokenTypes.IDENTIFIER) return Optional.empty();
        if (t1.isEmpty() || t1.get().Type != Token.TokenTypes.EQUAL) return Optional.empty();
        if (t2.isEmpty()) return Optional.empty();
        Token.TokenTypes t2type = t2.get().Type;
        boolean braceOK = (t2type == Token.TokenTypes.LEFTCURLY) || (t2type == Token.TokenTypes.LEFTBRACE);
        // if = is not followed by a brace, its not defination line
        if (!braceOK)
            return Optional.empty();
        // consume name and '='
        Token nameTok = tmr.MatchAndRemove(Token.TokenTypes.IDENTIFIER).orElseThrow(() -> new SyntaxErrorException("Expected identifier name", tmr.getLine(), tmr.getColumn()));
        tmr.MatchAndRemove(Token.TokenTypes.EQUAL).orElseThrow(() -> new SyntaxErrorException("Expected '='", tmr.getLine(), tmr.getColumn()));
        // make a Defination node
        Definition def = new Definition();
        def.definitionName = nameTok.Value.orElse("");
        // now decide between choices or struct
        Optional<Token> next = tmr.Peek(0);
        if (next.isPresent()&& next.get().Type == Token.TokenTypes.LEFTCURLY) {
            def.choices = Optional.of(ParseChoices());
            def.nstruct = Optional.empty();
        }
        else if (next.isPresent()&& next.get().Type == Token.TokenTypes.LEFTBRACE) {
            def.nstruct = Optional.of(ParseStruct());
            def.choices = Optional.empty();
        }
        else{
            throw new SyntaxErrorException("Expected '{' or '[' after '=' in definition", tmr.getLine(), tmr.getColumn());
        }
        return Optional.of(def);
        }
    /**
     * this parses a choice list, we read the first choice, then loop on commas to collect more
     * @return the Choice node is filled with all options
     * @throws SyntaxErrorException if braces or identidiers are missing
     */
    private Choices ParseChoices() throws SyntaxErrorException {
        // open choice block
        tmr.MatchAndRemove(Token.TokenTypes.LEFTCURLY).orElseThrow(() -> new SyntaxErrorException("Expected '{' to start choices", tmr.getLine(), tmr.getColumn()));
        Choices ch = new Choices();
        //first choice must exit
        Token first = tmr.MatchAndRemove(Token.TokenTypes.IDENTIFIER).orElseThrow(() -> new SyntaxErrorException("Expected at least one choice", tmr.getLine(), tmr.getColumn()));ch.choice.add(first.Value.orElse(""));
        Optional<Token> comma = tmr.MatchAndRemove(Token.TokenTypes.COMMA);
        while (comma.isPresent()) {
            Token w = tmr.MatchAndRemove(Token.TokenTypes.IDENTIFIER).orElseThrow(() -> new SyntaxErrorException("Expected identifier after ',' in choices", tmr.getLine(), tmr.getColumn()));ch.choice.add(w.Value.orElse(""));
            comma = tmr.MatchAndRemove(Token.TokenTypes.COMMA);
        }
        // close choices block
        tmr.MatchAndRemove(Token.TokenTypes.RIGHTCURLY).orElseThrow(() -> new SyntaxErrorException("Expected '}' to end choices", tmr.getLine(), tmr.getColumn()));
        return ch;
    }
    /**
     * this parses a struct list
     * @return the NStruct node with one or more entries
     * @throws SyntaxErrorException if brackets or entries are missing
     */
    private NStruct ParseStruct() throws SyntaxErrorException {
        // open struct block
        tmr.MatchAndRemove(Token.TokenTypes.LEFTBRACE).orElseThrow(() -> new SyntaxErrorException("Expected '[' to start struct", tmr.getLine(), tmr.getColumn()));
        NStruct st = new NStruct();
        // we must at least one entry inside the struct
        Optional<Entry> e = ParseEntry();
        if (e.isEmpty()) throw new SyntaxErrorException("Expected entry inside struct", tmr.getLine(), tmr.getColumn());st.entry.add(e.get());
        // read more entries seprated by comma
        Optional<Token> comma = tmr.MatchAndRemove(Token.TokenTypes.COMMA);
        while (comma.isPresent()) {
            Optional<Entry> e2 = ParseEntry();
            if (e2.isEmpty()) throw new SyntaxErrorException("Expected entry after ',' in struct", tmr.getLine(), tmr.getColumn());st.entry.add(e2.get());comma = tmr.MatchAndRemove(Token.TokenTypes.COMMA);
        }
        tmr.MatchAndRemove(Token.TokenTypes.RIGHTBRACE).orElseThrow(() -> new SyntaxErrorException("Expected ']' to end struct", tmr.getLine(), tmr.getColumn()));
        return st;
    }

    /**
     * this parses one struct entry
     * @return an entry node if both identifiers are present, else empty
     */
    private Optional<Entry> ParseEntry() {
        // try to grab optional unique
        boolean isUnique = tmr.MatchAndRemove(Token.TokenTypes.UNIQUE).isPresent();
        // then we need type and name
        Optional<Token> typeTok = tmr.MatchAndRemove(Token.TokenTypes.IDENTIFIER);
        Optional<Token> nameTok = tmr.MatchAndRemove(Token.TokenTypes.IDENTIFIER);
        if (typeTok.isEmpty() || nameTok.isEmpty()) return Optional.empty();
        // build the  entry node
        Entry e = new Entry();
        e.unique = isUnique;
        e.type = typeTok.get().Value.orElse("");
        e.name = nameTok.get().Value.orElse("");
        return Optional.of(e);
    }
    /**
     * this parses a rule, we allow blank lines inside the block, but we need at least one expression there
     * @return a rule node if an Expression was found, else empty
     * @throws SyntaxErrorException if '=>'block structure is broken
     */
    private Optional<Rule> ParseRule() throws SyntaxErrorException {
        // we need a head expression first or else its not a rule
        Optional<Expression> expr = ParseExpression();
        if (expr.isEmpty()) return Optional.empty();
        Rule r = new Rule();
        r.expression = expr.get();
        Optional<Token> yields = tmr.MatchAndRemove(Token.TokenTypes.YIELDS);
        if (yields.isPresent()) {
            SkipBlankLines();
            // block must start with INDENT
            tmr.MatchAndRemove(Token.TokenTypes.INDENT).orElseThrow(() -> new SyntaxErrorException("Expected indent after '=>'", tmr.getLine(), tmr.getColumn()));
            boolean sawAny = false;
            while (true) {
                SkipBlankLines();
                // maybe the block has ended
                Optional<Token> dd = tmr.MatchAndRemove(Token.TokenTypes.DEDENT);
                if (dd.isPresent()) {
                    if (!sawAny) {
                        throw new SyntaxErrorException("Expected expression inside '=> block'", tmr.getLine(), tmr.getColumn());
                    }
                    break;
                }
                // we need an expression line inside the block
                Optional<Expression> thenExpr = ParseExpression();
                if (thenExpr.isEmpty()) {
                    throw new SyntaxErrorException("Expected expression inside '=> block'", tmr.getLine(), tmr.getColumn());
                }
                r.thens.add(thenExpr.get());
                sawAny = true;
                // line end can be NEWLINE or directly dedent
                Optional<Token> after = tmr.MatchAndRemove(Token.TokenTypes.NEWLINE);
                if (after.isEmpty()) {
                    Optional<Token> dd2 = tmr.MatchAndRemove(Token.TokenTypes.DEDENT);
                    if (dd2.isPresent())
                        break;
                }
            }
        }
        return Optional.of(r);
    }
    /**
     * this parses a simple comparison
     * both sides must exist, otherwise we throw where it fails
     * @return an Expression node if successful, empty only if left side is not a var ref
     * @throws SyntaxErrorException if operator or right are missing
     */
    private Optional<Expression> ParseExpression() throws SyntaxErrorException {
        // get left side var ref, caller can try other constructs
        Optional<VariableReference> left = ParseVariableReference();
        if (left.isEmpty()) return Optional.empty();
        // get operator and map it to AST op
        Op op = new Op();
        if (tmr.MatchAndRemove(Token.TokenTypes.EQUAL).isPresent()) {
            op.type = Op.OpTypes.Equal;
        } else if (tmr.MatchAndRemove(Token.TokenTypes.NOTEQUAL).isPresent()) {
            op.type = Op.OpTypes.NotEqual;
        } else {
            throw new SyntaxErrorException("Expected '==' or '!=' in expression", tmr.getLine(), tmr.getColumn());
        }
        // get right side var ref
        Optional<VariableReference> right = ParseVariableReference();
        if (right.isEmpty()) {
            throw new SyntaxErrorException("Expected right side of expression", tmr.getLine(), tmr.getColumn());
        }
        // build and return the expression node
        Expression e = new Expression();
        e.left = left.get();
        e.op = op;
        e.right = right.get();
        return Optional.of(e);
    }
    /**
     * this parses a variable reference with optional chained modifiers
     * @return a VariableReference if an IDENTIFIER is present, empty otherwise
     * @throws SyntaxErrorException if a modifier starts but is malformed
     */
    private Optional<VariableReference> ParseVariableReference() throws SyntaxErrorException {
        // base var name
        Optional<Token> base = tmr.MatchAndRemove(Token.TokenTypes.IDENTIFIER);
        if (base.isEmpty()) return Optional.empty();
        // build the var refrence and attach optional modifier chain
        VariableReference vr = new VariableReference();
        vr.variableName = base.get().Value.orElse("");
        vr.vrmodifier = ParseVRModifier();
        return Optional.of(vr);
    }
    /**
     * this pares a variable reference modifier chain, we return empty if there is no modifier at this point
     * @return the first VRModifier in the chain, or empty if no modifier available
     * @throws SyntaxErrorException if a modifier opens but is not completed correctly
     */
    private Optional<VRModifier> ParseVRModifier() throws SyntaxErrorException {
        // dot style .name
        Optional<Token> dot = tmr.MatchAndRemove(Token.TokenTypes.DOT);
        if (dot.isPresent()) {
            VRModifier m = new VRModifier();
            m.dot = true;
            Token part = tmr.MatchAndRemove(Token.TokenTypes.IDENTIFIER).orElseThrow(() -> new SyntaxErrorException("Expected identifier after '.'", tmr.getLine(), tmr.getColumn()));
            m.part = Optional.of(part.Value.orElse(""));
            // chain more if present
            m.vrmodifier = ParseVRModifier();
            // dot style has no size
            m.size = null;
            return Optional.of(m);
        }
        // index style
        Optional<Token> lb = tmr.MatchAndRemove(Token.TokenTypes.LEFTBRACE);
        if (lb.isPresent()) {
            VRModifier m = new VRModifier();
            m.dot = false;
            Token num = tmr.MatchAndRemove(Token.TokenTypes.NUMBER).orElseThrow(() -> new SyntaxErrorException("Expected number inside '[' ']'", tmr.getLine(), tmr.getColumn()));
            m.size = num.Value.orElse("");
            tmr.MatchAndRemove(Token.TokenTypes.RIGHTBRACE).orElseThrow(() -> new SyntaxErrorException("Expected ']'", tmr.getLine(), tmr.getColumn()));
            // chain more if present
            m.vrmodifier = ParseVRModifier();
            // index style has no part name
            m.part = Optional.empty();
            return Optional.of(m);
        }
        return Optional.empty();
    }
    /**
     * helper: skip any number of NEWLINE tokens, this keeps parsing stable around blank lines
     */
    // skip any number of NEWLINE tokens (does nothing if none)
    private void SkipBlankLines() {
        Optional<Token> nl = tmr.MatchAndRemove(Token.TokenTypes.NEWLINE);
        while (nl.isPresent()) {
            nl = tmr.MatchAndRemove(Token.TokenTypes.NEWLINE);
        }
    }
    /**
     * helper: require at least one NEWLINE when we need it nd then also skip any extra blank lines that follow.
     * @throws SyntaxErrorException if the required newline is missing
     */
    private void RequireNewLine() throws SyntaxErrorException {
        Optional<Token> nl = tmr.MatchAndRemove(Token.TokenTypes.NEWLINE);
        if (nl.isEmpty() && !tmr.Done()) {
            throw new SyntaxErrorException("Expected new line after declaration", tmr.getLine(), tmr.getColumn());
        }
        // also skip any extra newlines after the required one
        SkipBlankLines();
    }
}