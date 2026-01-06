import AST.*;
import java.util.*;

public class Interpreter {
    //This stores all choice definitions like Girl example :Alice, Joanne
    private final HashMap<String, String[]> choiceDefs = new HashMap<>();
    //This stores struct definitions like Couple example :Girl g, Boy b
    private final HashMap<String, List<Entry>> structDefs = new HashMap<>();
    //This stores the top-level variables like Couples example :Couple[4]
    private final HashMap<String, StructInstance[]> structVars = new HashMap<>();
    // this class is one field inside a struct, like g or b
    /**
     * this is one field inside a struct like g or b, stores which def it uses and which option we picked it starts at 0, unique + peers are for later use
     */
    private static class VariableInstance {
        String fieldName;
        String defName;
        int valueIndex;
        boolean unique;
        VariableInstance[] peers = new VariableInstance[0];
        VariableInstance(String fieldName, String defName, boolean unique, int startIndex) {
            this.fieldName = fieldName;
            this.defName = defName;
            this.unique = unique;
            this.valueIndex = startIndex;
        }
        //this gets the real value using defName and index
        String getValue(HashMap<String, String[]> defs) {
            String[] arr = defs.get(defName);
            if (arr == null) return "<undef:" + defName + ">";
            if (valueIndex < 0 || valueIndex >= arr.length) return "<bad index>";
            return arr[valueIndex];
        }
    }
    //this is one struct instance, like one "Couple"
    private static class StructInstance {
        String typeName;
        LinkedHashMap<String, VariableInstance> fields = new LinkedHashMap<>();
        StructInstance(String typeName) {
            this.typeName = typeName;
        }
        //this adds a field to the struct
        void addField(VariableInstance v) {
            fields.put(v.fieldName, v);
        }
        //this gets a field by name
        VariableInstance get(String name) {
            return fields.get(name);
        }
    }
    /**
     * this runs interpreter 2: loads defs, makes vars, sets up unique, then tries permutations + rules to find a solution
     * @param tree the main that holds definitions, variables, rules
     * @throws Exception if a struct, field, or rule is missing or invalid
     */
    public void Interpret(Nusha tree) throws Exception {
        //build everything like Interpreter1
        loadDefs(tree.definitions);
        makeVars(tree.variables);
        setupUnique();
        //collect all variable instances (for permutations)
        List<VariableInstance> allVars = collectAllVariables();
        // compute how many total permutations there are
        long totalPerms = computeTotalPermutations(allVars);
          /*
        // limit so we don't die on huge puzzles (Battleship, etc.)
        long MAX_PERMUTATIONS = 4_294_00_000L;
        if (totalPerms > MAX_PERMUTATIONS) {
            System.out.println("Total permutations: " + totalPerms);
            System.out.println("Puzzle too large to brute-force. Skipping this puzzle.");
            return;
        }
    */
        System.out.println("Total permutations: " + totalPerms);
        boolean found = false;
        // main permutation loop (like counting with mixed bases)
        for (long i = 0; i < totalPerms; i++) {
            //check uniqueness
            if (!checkAllUnique(allVars)) {
                if (incrementVariables(allVars)) {
                    continue;
                } else {
                    break;
                }
            }
            //check all rules
            if (!checkAllRules(tree)) {
                if (incrementVariables(allVars)) {
                    continue;
                } else {
                    break;
                }
            }
            //if we reach here, everything passed!
            System.out.println("SUCCESS:");
            printAll();
            found = true;
            break;
        }
        if (!found) {
            System.out.println("No solution found.");
        }
    }
    //helpers for checking and unwrapping
    private static boolean has(Object o) {
        if (o instanceof Optional<?> opt) return opt.isPresent();
        return o != null;
    }
    //this safely gets value from Optional
    @SuppressWarnings("unchecked")
    private static <T> T get(Object o) {
        return (o instanceof Optional<?> opt) ? ((T) opt.orElse(null)) : (T) o;
    }
    /**
     * read all definitions from the AST
     * @param defs all definitions found in the AST
     */
    private void loadDefs(Definitions defs) {
        if (defs == null || defs.definition == null) return;
        for (Definition d : defs.definition) {
            if (d == null) continue;
            String name = d.definitionName;
            //this handles choice definitions
            if (has(d.choices)) {
                Choices ch = get(d.choices);
                List<String> opts = (ch != null && ch.choice != null) ? ch.choice : new LinkedList<>();
                choiceDefs.put(name, opts.toArray(new String[0]));
            }
            //this handles struct definitions
            if (has(d.nstruct)) {
                NStruct ns = get(d.nstruct);
                List<Entry> fields = (ns != null && ns.entry != null) ? ns.entry : new LinkedList<>();
                structDefs.put(name, List.copyOf(fields));
            }
        }
    }
    /**
     * this creates all top-level vars from structs
     * @param vars the variables section from the AST
     * @throws Exception if the struct type doesn’t exist
     */
    private void makeVars(Variables vars) throws Exception {
        if (vars == null || vars.variable == null) return;
        for (Variable v : vars.variable) {
            if (v == null) continue;
            String varName = v.variableName;
            String type = v.type;
            int size = parseSize(get(v.size));
            List<Entry> shape = structDefs.get(type);
            if (shape == null) throw new Exception("Unknown struct: " + type);
            //make an array of struct instances
            StructInstance[] arr = new StructInstance[size];
            for (int i = 0; i < size; i++) {
                arr[i] = makeStruct(type, shape);
            }
            structVars.put(varName, arr);
        }
    }
    /**
     * this makes one struct instance with all fields
     * @param type the struct type name
     * @param shape the list of fields that belong to that struct
     * @return a StructInstance with all fields filled
     * @throws Exception if any field uses a bad type
     */
    private StructInstance makeStruct(String type, List<Entry> shape) throws Exception {
        StructInstance s = new StructInstance(type);
        for (Entry e : shape) {
            s.addField(makeVar(e, type));
        }
        return s;
    }
    /**
     * this makes one field inside a struct, starts index at 0
     * @param e the field entry from the struct definition
     * @param parentType the name of the struct that owns this field
     * @return a VariableInstance that holds the field data
     * @throws Exception if the field’s type is not a valid choice def
     */
    private VariableInstance makeVar(Entry e, String parentType) throws Exception {
        String name = e.name;
        String type = e.type;
        boolean unique = Boolean.TRUE.equals(e.unique);
        if (!choiceDefs.containsKey(type)) {
            throw new Exception("Field '" + name + "' in '" + parentType + "' uses bad def: " + type);
        }
        return new VariableInstance(name, type, unique, 0);
    }
    /**
     * this changes size string to int, returns 1 if not found
     * @param sizeStr the size string from the AST
     * @return the parsed integer value
     * @throws Exception if the string isn’t a valid number
     */
    private int parseSize(String sizeStr) throws Exception {
        if (sizeStr == null) return 1;
        try {
            return Integer.parseInt(sizeStr);
        } catch (NumberFormatException e) {
            throw new Exception("Bad array size: " + sizeStr);
        }
    }
    //this sets up all unique field connections
    private void setupUnique() {
        for (StructInstance[] arr : structVars.values()) {
            if (arr.length == 0) continue;
            String structType = arr[0].typeName;
            List<Entry> shape = structDefs.get(structType);
            List<String> fields = new ArrayList<>();
            if (shape != null) {
                for (Entry e : shape) fields.add(e.name);
            } else {
                fields.addAll(arr[0].fields.keySet());
            }
            for (String f : fields) {
                List<VariableInstance> column = new ArrayList<>();
                for (StructInstance s : arr) {
                    VariableInstance v = s.get(f);
                    if (v != null) column.add(v);
                }
                boolean uniqueField = column.stream().anyMatch(v -> v.unique);
                if (!uniqueField) continue;

                for (VariableInstance cur : column) {
                    cur.peers = column.stream()
                            .filter(v -> v != cur)
                            .toArray(VariableInstance[]::new);
                }
            }
        }
    }
    /**
     * this prints all data in clean order to match prof's output:
     * if a field named "p" exists, print "p" first, then the rest alphabetically
     * otherwise, print all fields alphabetically
     */
    public void printAll() {
        List<String> topVars = new ArrayList<>(structVars.keySet());
        Collections.sort(topVars);
        for (String varName : topVars) {
            StructInstance[] arr = structVars.get(varName);
            if (arr == null) continue;

            for (int i = 0; i < arr.length; i++) {
                StructInstance s = arr[i];
                if (s == null) continue;
                //collect field names from structDefs (if present) or from the instance
                List<String> fieldNames = new ArrayList<>();
                List<Entry> shape = structDefs.get(s.typeName);
                if (shape != null) {
                    for (Entry e : shape) {
                        fieldNames.add(e.name);
                    }
                } else {
                    fieldNames.addAll(s.fields.keySet());
                }
                //sort alphabetically
                Collections.sort(fieldNames);
                // if there's a "p" field, move it to the front
                if (fieldNames.contains("p")) {
                    fieldNames.remove("p");
                    fieldNames.add(0, "p");
                }

                //now print in this order
                for (String f : fieldNames) {
                    VariableInstance v = s.get(f);
                    if (v == null) continue;
                    System.out.println(varName + "[" + i + "]." + f + " = " + v.getValue(choiceDefs));
                }
                System.out.println();
            }
        }
    }
    //  EXTRA METHODS FOR INTERPRETER 2 (permutations + rules)
    /**
     * this collects every VariableInstance into one list (for permutations)
     * order is similar to printAll so it is stable
     */
    private List<VariableInstance> collectAllVariables() {
        List<VariableInstance> result = new ArrayList<>();
        List<String> topVars = new ArrayList<>(structVars.keySet());
        Collections.sort(topVars);
        for (String varName : topVars) {
            StructInstance[] arr = structVars.get(varName);
            if (arr == null) continue;
            for (StructInstance s : arr) {
                if (s == null) continue;
                List<Entry> shape = structDefs.get(s.typeName);
                if (shape != null) {
                    for (Entry e : shape) {
                        VariableInstance v = s.get(e.name);
                        if (v != null) result.add(v);
                    }
                } else {
                    for (VariableInstance v : s.fields.values()) {
                        if (v != null) result.add(v);
                    }
                }
            }
        }
        return result;
    }
    /**
     *this computes total permutations: product of number of options for each variable
     */
    private long computeTotalPermutations(List<VariableInstance> vars) {
        long total = 1L;
        for (VariableInstance v : vars) {
            String[] options = choiceDefs.get(v.defName);
            if (options == null || options.length == 0) return 0L;
            total *= options.length;
        }
        return total;
    }
    /**
     *this checks uniqueness for all variables (using their peers lists)
     */
    private boolean checkAllUnique(List<VariableInstance> vars) {
        for (VariableInstance v : vars) {
            if (!v.unique) continue;
            for (VariableInstance peer : v.peers) {
                if (peer.valueIndex == v.valueIndex) {
                    return false;
                }
            }
        }
        return true;
    }
    /**
     * this increments the "mixed base number" stored in allVars
     * @return true if next permutation exists, false if we overflow
     */
    private boolean incrementVariables(List<VariableInstance> vars) {
        for (int i = 0; i < vars.size(); i++) {
            VariableInstance v = vars.get(i);
            String[] options = choiceDefs.get(v.defName);
            int base = (options == null) ? 0 : options.length;
            if (base <= 0) return false;
            v.valueIndex++;
            if (v.valueIndex < base) {
                return true;
            } else {
                v.valueIndex = 0;
            }
        }
        return false;
    }
    // RULE EVALUATION LOGIC
    /**
     * this checks all rules in the program
     */
    private boolean checkAllRules(Nusha tree) throws Exception {
        if (tree.rules == null || tree.rules.rule == null) return true;
        for (Rule r : tree.rules.rule) {
            if (r == null) continue;
            if (!runRule(r)) return false;
        }
        return true;
    }
    /**
     * this runs a single rule
     * simple rule: just expression (no thens)
     * complex rule: IF expr THEN each expr in thens, applied to each element of the struct array
     */
    private boolean runRule(Rule r) throws Exception {
        if (r.expression == null) {
            throw new Exception("Rule missing expression.");
        }
        // simple rule: no THENs
        if (r.thens == null || r.thens.isEmpty()) {
            return evaluateExpression(r.expression, null, null);
        }
        // complex rule: IF expr THEN thens
        Expression ifExpr = r.expression;
        VariableReference ifLeft = ifExpr.left;
        if (ifLeft == null) {
            throw new Exception("IF rule has no left side.");
        }
        String baseArrayName = ifLeft.variableName;
        StructInstance[] arr = structVars.get(baseArrayName);
        if (arr == null) {
            throw new Exception("Rule IF refers to unknown variable array: " + baseArrayName);
        }
        // For each element of this struct array, if IF matches, THEN must also match.
        for (StructInstance inst : arr) {
            if (inst == null) continue;
            if (evaluateExpression(ifExpr, baseArrayName, inst)) {
                for (Expression thenExpr : r.thens) {
                    if (!evaluateExpression(thenExpr, baseArrayName, inst)) {
                        //a THEN failed for this entry – rule fails
                        return false;
                    }
                }
            }
        }
        // if IF never matches, rule is "vacuously" true (no violation)
        return true;
    }
    /**
     * this evaluates one expression (left op right) and returns true/false
     * baseArrayName/baseInstance is used for IF/THEN rules to bind "OurFriends"
     */
    private boolean evaluateExpression(Expression expr, String baseArrayName, StructInstance baseInstance) throws Exception {
        if (expr == null || expr.left == null || expr.right == null || expr.op == null) {
            throw new Exception("Malformed expression: " + expr);
        }
        VariableReference leftRef = expr.left;
        VariableReference rightRef = expr.right;
        //left must be a variable
        VariableInstance leftVar = evaluateVariableReference(leftRef, baseArrayName, baseInstance);
        if (leftVar == null) {
            throw new Exception("Left side did not resolve to a variable: " + leftRef);
        }
        int leftIndex = leftVar.valueIndex;
        //try to make right a variable
        VariableInstance rightVar = evaluateVariableReference(rightRef, baseArrayName, baseInstance);
        int rightIndex;
        if (rightVar != null) {
            //VARIABLE op VARIABLE
            rightIndex = rightVar.valueIndex;
        } else {
            //VARIABLE op option (constant)
            String[] options = choiceDefs.get(leftVar.defName);
            if (options == null) {
                throw new Exception("No choice definition for: " + leftVar.defName);
            }
            String optionName = rightRef.variableName;
            int idx = -1;
            for (int i = 0; i < options.length; i++) {
                if (options[i].equals(optionName)) {
                    idx = i;
                    break;
                }
            }
            if (idx == -1) {
                throw new Exception("Unknown option '" + optionName + "' for definition " + leftVar.defName);
            }
            rightIndex = idx;
        }
        boolean equals = (leftIndex == rightIndex);
        if (expr.op.type == Op.OpTypes.Equal) {
            return equals;
        } else if (expr.op.type == Op.OpTypes.NotEqual) {
            return !equals;
        } else {
            throw new Exception("Unknown operator type: " + expr.op.type);
        }
    }
    /**
     * this resolves a VariableReference to a VariableInstance (one field), or returns null if it is not a real variable (like an option literal)
     * baseArrayName/baseInstance is used for IF/THEN rules when the variable name and matches the array we are looping over.
     */
    private VariableInstance evaluateVariableReference(VariableReference vr, String baseArrayName, StructInstance baseInstance) throws Exception {
        if (vr == null) return null;
        //case 1: IF/THEN context – we are inside some array element like OurFriends[i]
        if (baseArrayName != null &&
                baseInstance != null &&
                baseArrayName.equals(vr.variableName)) {
            VRModifier mod = has(vr.vrmodifier) ? get(vr.vrmodifier) : null;
            //in IF/THEN rules we expect ".field", not [index]
            if (mod == null) {
                return null; //just "OurFriends" alone is not a single variable
            }
            if (!mod.dot) {
                throw new Exception("Indexing not supported in IF/THEN rules for: " + vr);
            }
            String fieldName = mod.part.orElseThrow(() -> new Exception("Missing field name in VRModifier for: " + vr));

            if (has(mod.vrmodifier)) {throw new Exception("Nested field modifiers not supported for: " + vr);
            }

            VariableInstance v = baseInstance.get(fieldName);
            if (v == null) {
                throw new Exception("Field '" + fieldName + "' not found in struct type '" + baseInstance.typeName + "'");
            }
            return v;
        }
        //case 2: global – must start with a top-level variable array
        StructInstance[] arr = structVars.get(vr.variableName);
        if (arr == null) {
            //not a known variable – probably an option literal
            return null;
        }
        VRModifier mod = has(vr.vrmodifier) ? get(vr.vrmodifier) : null;
        int index = 0;
        // optional [index]
        if (mod != null && !mod.dot) {
            String idxStr = mod.size;
            try {
                index = Integer.parseInt(idxStr);
            } catch (NumberFormatException e) {
                throw new Exception("Non-integer index '" + idxStr + "' in variable reference: " + vr);
            }
            if (index < 0 || index >= arr.length) {
                throw new Exception("Index " + index + " out of bounds for variable " + vr.variableName);
            }
            mod = has(mod.vrmodifier) ? get(mod.vrmodifier) : null;
        }
        if (mod == null) {
            //"Couples" or "Couples[0]" alone – not a specific field
            return null;
        }
        if (!mod.dot) {
            throw new Exception("Unexpected array modifier inside struct for: " + vr);
        }
        String fieldName = mod.part.orElseThrow(() -> new Exception("Missing field name in VRModifier for: " + vr));
        if (has(mod.vrmodifier)) {throw new Exception("Nested field modifiers not supported for: " + vr);
        }
        StructInstance inst = arr[index];
        if (inst == null) {
            throw new Exception("Null struct instance at index " + index + " for variable " + vr.variableName);
        }
        VariableInstance v = inst.get(fieldName);
        if (v == null) {
            throw new Exception("Field '" + fieldName + "' not found in struct type '" + inst.typeName + "'");
        }
        return v;
    }
}