package io.flowcore.statemachine.guard;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Evaluates guard expressions against a workflow context.
 *
 * <p>The evaluator operates in three phases:</p>
 * <ol>
 *   <li><strong>Tokenization</strong> -- the expression string is broken into tokens by {@link GuardLexer}</li>
 *   <li><strong>Parsing</strong> -- the token stream is parsed into an AST by {@link GuardParser}</li>
 *   <li><strong>Evaluation</strong> -- the AST is walked and evaluated against a context map</li>
 * </ol>
 *
 * <h3>Context resolution</h3>
 * <p>Variable paths like {@code $.kyc.status} navigate the nested context map.
 * A missing path segment yields {@code null}, which is treated as {@code false}
 * in boolean context unless {@code exists()} is used.</p>
 *
 * <h3>Built-in functions</h3>
 * <ul>
 *   <li>{@code exists(x)} -- returns true if the variable path exists in the context</li>
 *   <li>{@code len(x)} -- returns the length of a collection, map, or string</li>
 *   <li>{@code contains(a, b)} -- returns true if a contains b</li>
 *   <li>{@code regex(s, pattern)} -- returns true if string s matches the regex pattern</li>
 * </ul>
 *
 * <p>This class is thread-safe: it holds no mutable state between evaluations.
 * The AST is produced per-call and is not shared.</p>
 */
public class GuardEvaluator {

    // -- Public API --------------------------------------------------------

    /**
     * Evaluates a guard expression against the given context.
     *
     * @param expression the guard expression string
     * @param context    the workflow context data map; must not be null
     * @return the boolean result of the evaluation
     * @throws GuardEvaluationException if the expression cannot be tokenized,
     *                                  parsed, or evaluated
     * @throws NullPointerException     if expression or context is null
     */
    public boolean evaluate(String expression, Map<String, Object> context) {
        Objects.requireNonNull(expression, "expression must not be null");
        Objects.requireNonNull(context, "context must not be null");

        GuardLexer lexer = new GuardLexer(expression);
        List<GuardLexer.Token> tokens = lexer.tokenize();

        GuardParser parser = new GuardParser(tokens);
        GuardAstNode ast = parser.parse();

        Object result = evalNode(ast, context);

        return toBoolean(result);
    }

    // -- AST evaluation ----------------------------------------------------

    private Object evalNode(GuardAstNode node, Map<String, Object> context) {
        return switch (node) {
            case GuardAstNode.LiteralNode lit -> lit.value();
            case GuardAstNode.VariableNode var -> resolveVariable(var, context);
            case GuardAstNode.FunctionCallNode fn -> evalFunction(fn, context);
            case GuardAstNode.BinaryOpNode bin -> evalBinaryOp(bin, context);
        };
    }

    // -- Variable resolution -----------------------------------------------

    /**
     * Resolves a variable path against the context map.
     * For example, {@code $.kyc.status} navigates:
     * {@code context.get("kyc")} -> then {@code .get("status")}.
     *
     * <p>If any segment along the path is null or not a map, returns null.</p>
     */
    @SuppressWarnings("unchecked")
    private Object resolveVariable(GuardAstNode.VariableNode var, Map<String, Object> context) {
        Object current = context;
        for (String segment : var.pathSegments()) {
            if (current instanceof Map<?, ?> map) {
                current = ((Map<String, Object>) map).get(segment);
            } else {
                return null;
            }
        }
        return current;
    }

    // -- Binary operators --------------------------------------------------

    private Object evalBinaryOp(GuardAstNode.BinaryOpNode bin, Map<String, Object> context) {
        return switch (bin.operator()) {
            case OR -> evalOr(bin, context);
            case AND -> evalAnd(bin, context);
            case EQ -> evalEquality(bin, context);
            case NEQ -> evalInequality(bin, context);
            case GT, GTE, LT, LTE -> evalRelational(bin, context);
        };
    }

    private Object evalOr(GuardAstNode.BinaryOpNode bin, Map<String, Object> context) {
        // Short-circuit: if left is true, skip right
        Object left = evalNode(bin.left(), context);
        if (toBoolean(left)) {
            return true;
        }
        return toBoolean(evalNode(bin.right(), context));
    }

    private Object evalAnd(GuardAstNode.BinaryOpNode bin, Map<String, Object> context) {
        // Short-circuit: if left is false, skip right
        Object left = evalNode(bin.left(), context);
        if (!toBoolean(left)) {
            return false;
        }
        return toBoolean(evalNode(bin.right(), context));
    }

    private Object evalEquality(GuardAstNode.BinaryOpNode bin, Map<String, Object> context) {
        Object left = evalNode(bin.left(), context);
        Object right = evalNode(bin.right(), context);
        return objectsEqual(left, right);
    }

    private Object evalInequality(GuardAstNode.BinaryOpNode bin, Map<String, Object> context) {
        Object left = evalNode(bin.left(), context);
        Object right = evalNode(bin.right(), context);
        return !objectsEqual(left, right);
    }

    private Object evalRelational(GuardAstNode.BinaryOpNode bin, Map<String, Object> context) {
        Object left = evalNode(bin.left(), context);
        Object right = evalNode(bin.right(), context);

        if (left == null || right == null) {
            return false;
        }

        double leftNum = toDouble(left);
        double rightNum = toDouble(right);

        return switch (bin.operator()) {
            case GT -> leftNum > rightNum;
            case GTE -> leftNum >= rightNum;
            case LT -> leftNum < rightNum;
            case LTE -> leftNum <= rightNum;
            default -> throw new IllegalStateException("unexpected operator in relational: " + bin.operator());
        };
    }

    // -- Built-in functions ------------------------------------------------

    private Object evalFunction(GuardAstNode.FunctionCallNode fn, Map<String, Object> context) {
        return switch (fn.name()) {
            case "exists" -> evalExists(fn, context);
            case "len" -> evalLen(fn, context);
            case "contains" -> evalContains(fn, context);
            case "regex" -> evalRegex(fn, context);
            default -> throw new GuardEvaluationException(
                    "unknown function '" + fn.name() + "'; supported functions: exists, len, contains, regex");
        };
    }

    /**
     * {@code exists(x)} -- returns true if the argument is a variable whose path
     * exists in the context (the value is not null).
     */
    private Object evalExists(GuardAstNode.FunctionCallNode fn, Map<String, Object> context) {
        validateArgCount(fn, 1);
        GuardAstNode arg = fn.arguments().getFirst();

        if (arg instanceof GuardAstNode.VariableNode var) {
            return resolveVariable(var, context) != null;
        }

        // For non-variable arguments, evaluate and check non-null
        Object value = evalNode(arg, context);
        return value != null;
    }

    /**
     * {@code len(x)} -- returns the length of a string, collection, or map.
     */
    private Object evalLen(GuardAstNode.FunctionCallNode fn, Map<String, Object> context) {
        validateArgCount(fn, 1);
        Object value = evalNode(fn.arguments().getFirst(), context);
        if (value == null) {
            return 0;
        }
        if (value instanceof CharSequence cs) {
            return cs.length();
        }
        if (value instanceof Collection<?> col) {
            return col.size();
        }
        if (value instanceof Map<?, ?> map) {
            return map.size();
        }
        throw new GuardEvaluationException(
                "len() requires a string, collection, or map argument, got: " + value.getClass().getSimpleName());
    }

    /**
     * {@code contains(a, b)} -- returns true if a contains b.
     * Works for strings (substring check) and collections (membership check).
     */
    private Object evalContains(GuardAstNode.FunctionCallNode fn, Map<String, Object> context) {
        validateArgCount(fn, 2);
        Object container = evalNode(fn.arguments().getFirst(), context);
        Object contained = evalNode(fn.arguments().get(1), context);

        if (container == null) {
            return false;
        }

        if (container instanceof CharSequence cs && contained instanceof CharSequence sub) {
            return cs.toString().contains(sub.toString());
        }

        if (container instanceof Collection<?> col) {
            return col.contains(contained);
        }

        throw new GuardEvaluationException(
                "contains() requires a string or collection as first argument, got: "
                        + container.getClass().getSimpleName());
    }

    /**
     * {@code regex(s, pattern)} -- returns true if string s matches the regex pattern.
     */
    private Object evalRegex(GuardAstNode.FunctionCallNode fn, Map<String, Object> context) {
        validateArgCount(fn, 2);
        Object target = evalNode(fn.arguments().getFirst(), context);
        Object patternObj = evalNode(fn.arguments().get(1), context);

        if (target == null) {
            return false;
        }
        if (!(target instanceof String s)) {
            throw new GuardEvaluationException(
                    "regex() requires a string as the first argument, got: " + target.getClass().getSimpleName());
        }
        if (!(patternObj instanceof String pattern)) {
            throw new GuardEvaluationException(
                    "regex() requires a string pattern as the second argument");
        }

        try {
            return Pattern.compile(pattern).matcher(s).find();
        } catch (PatternSyntaxException e) {
            throw new GuardEvaluationException("invalid regex pattern '" + pattern + "': " + e.getMessage(), e);
        }
    }

    // -- Type conversion helpers -------------------------------------------

    /**
     * Converts an arbitrary value to boolean for logical operations.
     * <ul>
     *   <li>{@code null} -> false</li>
     *   <li>{@code Boolean} -> as-is</li>
     *   <li>{@code Number} -> true if non-zero</li>
     *   <li>{@code String} -> true if non-empty</li>
     *   <li>{@code Collection} -> true if non-empty</li>
     *   <li>{@code Map} -> true if non-empty</li>
     * </ul>
     */
    private boolean toBoolean(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof Number n) {
            return n.doubleValue() != 0.0;
        }
        if (value instanceof String s) {
            return !s.isEmpty();
        }
        if (value instanceof Collection<?> col) {
            return !col.isEmpty();
        }
        if (value instanceof Map<?, ?> map) {
            return !map.isEmpty();
        }
        return true;
    }

    /**
     * Converts a value to double for relational comparisons.
     */
    private double toDouble(Object value) {
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        if (value instanceof String s) {
            try {
                return Double.parseDouble(s);
            } catch (NumberFormatException e) {
                throw new GuardEvaluationException(
                        "cannot convert string '" + s + "' to number for comparison");
            }
        }
        throw new GuardEvaluationException(
                "cannot convert " + value.getClass().getSimpleName() + " to number for comparison");
    }

    /**
     * Compares two objects for equality, handling type coercion
     * between different numeric types.
     */
    private boolean objectsEqual(Object a, Object b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }

        // Numeric type coercion
        if (a instanceof Number na && b instanceof Number nb) {
            return Double.compare(na.doubleValue(), nb.doubleValue()) == 0;
        }

        // Boolean comparison with string
        if (a instanceof Boolean ba && b instanceof String bs) {
            return ba.toString().equals(bs);
        }
        if (b instanceof Boolean bb && a instanceof String as) {
            return bb.toString().equals(as);
        }

        return a.equals(b);
    }

    private static void validateArgCount(GuardAstNode.FunctionCallNode fn, int expected) {
        int actual = fn.arguments().size();
        if (actual != expected) {
            throw new GuardEvaluationException(
                    "function '" + fn.name() + "' expects " + expected
                            + " argument(s) but got " + actual);
        }
    }
}
