package io.flowcore.statemachine.guard;

import java.util.List;
import java.util.Objects;

/**
 * Sealed interface hierarchy representing the Abstract Syntax Tree
 * for the guard expression language.
 *
 * <p>AST node types:</p>
 * <ul>
 *   <li>{@link LiteralNode} -- boolean, number, or string literal values</li>
 *   <li>{@link VariableNode} -- context variable path, e.g. {@code $.kyc.status}</li>
 *   <li>{@link FunctionCallNode} -- built-in function invocations like {@code exists($.x)}</li>
 *   <li>{@link BinaryOpNode} -- binary operations: {@code ||}, {@code &&}, {@code ==}, {@code !=},
 *       {@code >}, {@code >=}, {@code <}, {@code <=}</li>
 * </ul>
 */
public sealed interface GuardAstNode {

    /**
     * Returns a human-readable representation of this node for error reporting.
     *
     * @return string representation of the AST node
     */
    @Override
    String toString();

    // -----------------------------------------------------------------------
    // Literal
    // -----------------------------------------------------------------------

    /**
     * Represents a literal value: boolean ({@code true}/{@code false}),
     * number (integer or decimal), or string (double-quoted).
     *
     * @param value the literal value; may be {@code Boolean}, {@code Number}, or {@code String}
     */
    record LiteralNode(Object value) implements GuardAstNode {

        /**
         * Creates a new literal node.
         *
         * @param value the literal value, must not be null
         * @throws NullPointerException if value is null
         */
        public LiteralNode {
            Objects.requireNonNull(value, "literal value must not be null");
        }

        @Override
        public String toString() {
            if (value instanceof String s) {
                return "\"" + s + "\"";
            }
            return String.valueOf(value);
        }
    }

    // -----------------------------------------------------------------------
    // Variable
    // -----------------------------------------------------------------------

    /**
     * Represents a context variable path such as {@code $.kyc.status}.
     *
     * @param pathSegments the path segments after the dollar sign; never empty
     */
    record VariableNode(List<String> pathSegments) implements GuardAstNode {

        /**
         * Creates a new variable node.
         *
         * @param pathSegments the segments of the variable path; must not be null or empty
         * @throws NullPointerException     if pathSegments is null
         * @throws IllegalArgumentException if pathSegments is empty
         */
        public VariableNode {
            Objects.requireNonNull(pathSegments, "pathSegments must not be null");
            if (pathSegments.isEmpty()) {
                throw new IllegalArgumentException("pathSegments must not be empty");
            }
        }

        /**
         * Returns the dot-separated path representation, e.g. {@code kyc.status}.
         *
         * @return dot-joined path
         */
        public String path() {
            return String.join(".", pathSegments);
        }

        @Override
        public String toString() {
            return "$." + path();
        }
    }

    // -----------------------------------------------------------------------
    // Function call
    // -----------------------------------------------------------------------

    /**
     * Represents a built-in function call such as {@code exists($.x)} or {@code contains(a, b)}.
     *
     * @param name      the function name
     * @param arguments the argument expressions; may be empty but never null
     */
    record FunctionCallNode(String name, List<GuardAstNode> arguments) implements GuardAstNode {

        /**
         * Creates a new function call node.
         *
         * @param name      the function name, must not be null or blank
         * @param arguments the argument list, must not be null
         * @throws NullPointerException     if name or arguments is null
         * @throws IllegalArgumentException if name is blank
         */
        public FunctionCallNode {
            Objects.requireNonNull(name, "function name must not be null");
            Objects.requireNonNull(arguments, "arguments must not be null");
            if (name.isBlank()) {
                throw new IllegalArgumentException("function name must not be blank");
            }
        }

        @Override
        public String toString() {
            return name + "(" + arguments.stream().map(Object::toString).reduce((a, b) -> a + ", " + b).orElse("") + ")";
        }
    }

    // -----------------------------------------------------------------------
    // Binary operation
    // -----------------------------------------------------------------------

    /**
     * Supported binary operators in guard expressions.
     */
    enum BinaryOperator {
        /** Logical OR */ OR("||"),
        /** Logical AND */ AND("&&"),
        /** Equality */ EQ("=="),
        /** Inequality */ NEQ("!="),
        /** Greater than */ GT(">"),
        /** Greater than or equal */ GTE(">="),
        /** Less than */ LT("<"),
        /** Less than or equal */ LTE("<=");

        private final String symbol;

        BinaryOperator(String symbol) {
            this.symbol = symbol;
        }

        /**
         * Returns the operator symbol as used in expressions.
         *
         * @return the operator symbol
         */
        public String symbol() {
            return symbol;
        }
    }

    /**
     * Represents a binary operation with a left operand, operator, and right operand.
     *
     * @param left     the left-hand side expression
     * @param operator the binary operator
     * @param right    the right-hand side expression
     */
    record BinaryOpNode(GuardAstNode left, BinaryOperator operator, GuardAstNode right) implements GuardAstNode {

        /**
         * Creates a new binary operation node.
         *
         * @param left     the left operand, must not be null
         * @param operator the operator, must not be null
         * @param right    the right operand, must not be null
         */
        public BinaryOpNode {
            Objects.requireNonNull(left, "left operand must not be null");
            Objects.requireNonNull(operator, "operator must not be null");
            Objects.requireNonNull(right, "right operand must not be null");
        }

        @Override
        public String toString() {
            return "(" + left + " " + operator.symbol() + " " + right + ")";
        }
    }
}
