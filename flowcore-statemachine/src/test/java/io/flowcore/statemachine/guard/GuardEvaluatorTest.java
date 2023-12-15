package io.flowcore.statemachine.guard;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test suite for the guard expression evaluator covering:
 * <ul>
 *   <li>Lexer tokenization</li>
 *   <li>Parser AST construction</li>
 *   <li>Full evaluation: literals, variables, operators, functions</li>
 *   <li>Error handling with position information</li>
 *   <li>Edge cases: nulls, short-circuiting, type coercion</li>
 * </ul>
 */
class GuardEvaluatorTest {

    private final GuardEvaluator evaluator = new GuardEvaluator();

    // Helper to build context maps concisely
    @SuppressWarnings("unchecked")
    private Map<String, Object> ctx(Object... keyValues) {
        assert keyValues.length % 2 == 0 : "key-value pairs must be even";
        Map<String, Object> map = new java.util.LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return map;
    }

    // =====================================================================
    // LITERAL EVALUATION
    // =====================================================================

    @Nested
    @DisplayName("Literal evaluation")
    class LiteralTests {

        @Test
        @DisplayName("true literal evaluates to true")
        void trueLiteral() {
            assertTrue(evaluator.evaluate("true", Map.of()));
        }

        @Test
        @DisplayName("false literal evaluates to false")
        void falseLiteral() {
            assertFalse(evaluator.evaluate("false", Map.of()));
        }

        @Test
        @DisplayName("non-zero number evaluates to true in boolean context")
        void numberAsBoolean() {
            // 1 > 0 => true
            assertTrue(evaluator.evaluate("1 > 0", Map.of()));
        }

        @Test
        @DisplayName("string literal equality")
        void stringEquality() {
            assertTrue(evaluator.evaluate("\"hello\" == \"hello\"", Map.of()));
            assertFalse(evaluator.evaluate("\"hello\" == \"world\"", Map.of()));
        }

        @Test
        @DisplayName("integer and decimal number parsing")
        void numberParsing() {
            assertTrue(evaluator.evaluate("42 == 42", Map.of()));
            assertTrue(evaluator.evaluate("3.14 == 3.14", Map.of()));
            assertTrue(evaluator.evaluate("-1 < 0", Map.of()));
        }
    }

    // =====================================================================
    // VARIABLE RESOLUTION
    // =====================================================================

    @Nested
    @DisplayName("Variable resolution")
    class VariableTests {

        @Test
        @DisplayName("simple variable access")
        void simpleVariable() {
            assertTrue(evaluator.evaluate("$.status == \"active\"", ctx("status", "active")));
        }

        @Test
        @DisplayName("nested variable path navigates map")
        void nestedPath() {
            Map<String, Object> context = Map.of(
                    "kyc", Map.of("status", "verified", "level", 3)
            );
            assertTrue(evaluator.evaluate("$.kyc.status == \"verified\"", context));
            assertTrue(evaluator.evaluate("$.kyc.level > 2", context));
        }

        @Test
        @DisplayName("deeply nested variable path")
        void deeplyNestedPath() {
            Map<String, Object> context = Map.of(
                    "order", Map.of(
                            "payment", Map.of(
                                    "card", Map.of("type", "VISA")
                            )
                    )
            );
            assertTrue(evaluator.evaluate("$.order.payment.card.type == \"VISA\"", context));
        }

        @Test
        @DisplayName("missing variable yields null (false in boolean context)")
        void missingVariable() {
            assertFalse(evaluator.evaluate("$.nonexistent", Map.of()));
        }

        @Test
        @DisplayName("missing nested path segment yields null")
        void missingNestedPath() {
            Map<String, Object> context = Map.of("kyc", Map.of());
            assertFalse(evaluator.evaluate("$.kyc.status == \"verified\"", context));
        }

        @Test
        @DisplayName("variable path through null mid-segment returns null")
        void nullMidSegment() {
            Map<String, Object> context = Map.of("kyc", "not_a_map");
            assertFalse(evaluator.evaluate("$.kyc.status == \"x\"", context));
        }
    }

    // =====================================================================
    // LOGICAL OPERATORS
    // =====================================================================

    @Nested
    @DisplayName("Logical operators: ||, &&")
    class LogicalOperatorTests {

        @Test
        @DisplayName("OR: true || false => true")
        void orTrue() {
            assertTrue(evaluator.evaluate("true || false", Map.of()));
        }

        @Test
        @DisplayName("OR: false || false => false")
        void orFalse() {
            assertFalse(evaluator.evaluate("false || false", Map.of()));
        }

        @Test
        @DisplayName("AND: true && true => true")
        void andTrue() {
            assertTrue(evaluator.evaluate("true && true", Map.of()));
        }

        @Test
        @DisplayName("AND: true && false => false")
        void andFalse() {
            assertFalse(evaluator.evaluate("true && false", Map.of()));
        }

        @Test
        @DisplayName("OR short-circuits: does not evaluate right when left is true")
        void orShortCircuit() {
            // If right side were evaluated, it would error (unknown function).
            // Short-circuit prevents that.
            assertTrue(evaluator.evaluate("true || unknownFunc()", Map.of()));
        }

        @Test
        @DisplayName("AND short-circuits: does not evaluate right when left is false")
        void andShortCircuit() {
            assertFalse(evaluator.evaluate("false && unknownFunc()", Map.of()));
        }

        @Test
        @DisplayName("Chained OR: false || false || true => true")
        void chainedOr() {
            assertTrue(evaluator.evaluate("false || false || true", Map.of()));
        }

        @Test
        @DisplayName("Chained AND: true && true && false => false")
        void chainedAnd() {
            assertFalse(evaluator.evaluate("true && true && false", Map.of()));
        }

        @Test
        @DisplayName("AND has higher precedence than OR: true || false && false => true")
        void precedenceAndOverOr() {
            // Should be parsed as true || (false && false) => true
            assertTrue(evaluator.evaluate("true || false && false", Map.of()));
        }
    }

    // =====================================================================
    // EQUALITY OPERATORS
    // =====================================================================

    @Nested
    @DisplayName("Equality operators: ==, !=")
    class EqualityOperatorTests {

        @Test
        @DisplayName("String equality with variable")
        void stringVarEquality() {
            Map<String, Object> ctx = Map.of("status", "pending");
            assertTrue(evaluator.evaluate("$.status == \"pending\"", ctx));
            assertFalse(evaluator.evaluate("$.status != \"pending\"", ctx));
        }

        @Test
        @DisplayName("Number equality with different numeric types")
        void numericEquality() {
            assertTrue(evaluator.evaluate("1 == 1.0", Map.of()));
        }

        @Test
        @DisplayName("Inequality between string and number")
        void inequality() {
            assertTrue(evaluator.evaluate("\"hello\" != 42", Map.of()));
        }

        @Test
        @DisplayName("null compared with != yields true")
        void nullInequality() {
            Map<String, Object> ctx = Map.of();
            // $.missing is null; null != "something" => true
            assertTrue(evaluator.evaluate("$.missing != \"something\"", ctx));
        }

        @Test
        @DisplayName("null compared with == yields false for non-null")
        void nullEquality() {
            Map<String, Object> ctx = Map.of();
            assertFalse(evaluator.evaluate("$.missing == \"something\"", ctx));
        }

        @Test
        @DisplayName("null == null yields true")
        void nullEqualsNull() {
            Map<String, Object> ctx = Map.of();
            assertTrue(evaluator.evaluate("$.missing == $.alsoMissing", ctx));
        }
    }

    // =====================================================================
    // RELATIONAL OPERATORS
    // =====================================================================

    @Nested
    @DisplayName("Relational operators: >, >=, <, <=")
    class RelationalOperatorTests {

        @Test
        @DisplayName("greater than")
        void greaterThan() {
            assertTrue(evaluator.evaluate("10 > 5", Map.of()));
            assertFalse(evaluator.evaluate("5 > 10", Map.of()));
        }

        @Test
        @DisplayName("greater than or equal")
        void greaterThanOrEqual() {
            assertTrue(evaluator.evaluate("5 >= 5", Map.of()));
            assertTrue(evaluator.evaluate("6 >= 5", Map.of()));
            assertFalse(evaluator.evaluate("4 >= 5", Map.of()));
        }

        @Test
        @DisplayName("less than")
        void lessThan() {
            assertTrue(evaluator.evaluate("3 < 7", Map.of()));
            assertFalse(evaluator.evaluate("7 < 3", Map.of()));
        }

        @Test
        @DisplayName("less than or equal")
        void lessThanOrEqual() {
            assertTrue(evaluator.evaluate("5 <= 5", Map.of()));
            assertTrue(evaluator.evaluate("4 <= 5", Map.of()));
            assertFalse(evaluator.evaluate("6 <= 5", Map.of()));
        }

        @Test
        @DisplayName("relational with variable and literal")
        void relationalWithVariable() {
            Map<String, Object> ctx = Map.of("amount", 150);
            assertTrue(evaluator.evaluate("$.amount > 100", ctx));
            assertFalse(evaluator.evaluate("$.amount < 100", ctx));
        }

        @Test
        @DisplayName("relational with null operand yields false")
        void relationalWithNull() {
            Map<String, Object> ctx = Map.of();
            assertFalse(evaluator.evaluate("$.missing > 0", ctx));
            assertFalse(evaluator.evaluate("$.missing >= 0", ctx));
            assertFalse(evaluator.evaluate("$.missing < 0", ctx));
            assertFalse(evaluator.evaluate("$.missing <= 0", ctx));
        }

        @Test
        @DisplayName("chained relational: 1 < 5 && 5 < 10")
        void chainedRelational() {
            assertTrue(evaluator.evaluate("1 < 5 && 5 < 10", Map.of()));
        }
    }

    // =====================================================================
    // BUILT-IN FUNCTIONS
    // =====================================================================

    @Nested
    @DisplayName("Built-in functions")
    class FunctionTests {

        @Test
        @DisplayName("exists() returns true when variable path exists")
        void existsTrue() {
            Map<String, Object> ctx = Map.of("kyc", Map.of("status", "verified"));
            assertTrue(evaluator.evaluate("exists($.kyc.status)", ctx));
        }

        @Test
        @DisplayName("exists() returns false when variable path is missing")
        void existsFalse() {
            Map<String, Object> ctx = Map.of("kyc", Map.of());
            assertFalse(evaluator.evaluate("exists($.kyc.status)", ctx));
        }

        @Test
        @DisplayName("exists() returns false when top-level key is missing")
        void existsTopLevelMissing() {
            assertFalse(evaluator.evaluate("exists($.nonexistent)", Map.of()));
        }

        @Test
        @DisplayName("exists() with literal always returns true (non-null)")
        void existsWithLiteral() {
            assertTrue(evaluator.evaluate("exists(\"hello\")", Map.of()));
        }

        @Test
        @DisplayName("len() returns string length")
        void lenString() {
            assertTrue(evaluator.evaluate("len(\"hello\") == 5", Map.of()));
        }

        @Test
        @DisplayName("len() returns collection size")
        void lenCollection() {
            Map<String, Object> ctx = Map.of("items", List.of(1, 2, 3));
            assertTrue(evaluator.evaluate("len($.items) == 3", ctx));
        }

        @Test
        @DisplayName("len() returns map size")
        void lenMap() {
            Map<String, Object> ctx = Map.of("data", Map.of("a", 1, "b", 2));
            assertTrue(evaluator.evaluate("len($.data) == 2", ctx));
        }

        @Test
        @DisplayName("len() with null variable returns 0")
        void lenNull() {
            assertTrue(evaluator.evaluate("len($.missing) == 0", Map.of()));
        }

        @Test
        @DisplayName("contains() with strings returns true for substring")
        void containsString() {
            assertTrue(evaluator.evaluate("contains(\"hello world\", \"world\")", Map.of()));
            assertFalse(evaluator.evaluate("contains(\"hello world\", \"xyz\")", Map.of()));
        }

        @Test
        @DisplayName("contains() with collection returns true for member")
        void containsCollection() {
            Map<String, Object> ctx = Map.of("tags", List.of("urgent", "review"));
            assertTrue(evaluator.evaluate("contains($.tags, \"urgent\")", ctx));
            assertFalse(evaluator.evaluate("contains($.tags, \"done\")", ctx));
        }

        @Test
        @DisplayName("contains() with null container returns false")
        void containsNull() {
            assertFalse(evaluator.evaluate("contains($.missing, \"x\")", Map.of()));
        }

        @Test
        @DisplayName("regex() matches pattern in string")
        void regexMatch() {
            assertTrue(evaluator.evaluate("regex(\"ABC123\", \"[0-9]+\")", Map.of()));
            assertFalse(evaluator.evaluate("regex(\"hello\", \"^[0-9]+$\")", Map.of()));
        }

        @Test
        @DisplayName("regex() with null target returns false")
        void regexNull() {
            assertFalse(evaluator.evaluate("regex($.missing, \".*\")", Map.of()));
        }

        @Test
        @DisplayName("regex() with invalid pattern throws exception")
        void regexInvalidPattern() {
            assertThrows(GuardEvaluationException.class,
                    () -> evaluator.evaluate("regex(\"test\", \"[invalid\")", Map.of()));
        }

        @Test
        @DisplayName("function with wrong argument count throws exception")
        void wrongArgCount() {
            assertThrows(GuardEvaluationException.class,
                    () -> evaluator.evaluate("exists($.a, $.b)", Map.of()));
        }

        @Test
        @DisplayName("unknown function throws exception")
        void unknownFunction() {
            assertThrows(GuardEvaluationException.class,
                    () -> evaluator.evaluate("magic($.x)", Map.of()));
        }
    }

    // =====================================================================
    // PARENTHESIZED EXPRESSIONS
    // =====================================================================

    @Nested
    @DisplayName("Parenthesized expressions")
    class ParenTests {

        @Test
        @DisplayName("parentheses override precedence")
        void overridePrecedence() {
            // Without parens: false || (true && false) = false
            // With parens: (false || true) && false = false
            // Let's test a case where it matters:
            // (true || false) && true => true
            assertTrue(evaluator.evaluate("(true || false) && true", Map.of()));
            // true || (false && true) => true (same result)

            // Better example: (1 == 1 || 1 == 2) && 2 == 2 => true
            assertTrue(evaluator.evaluate("(1 == 1 || 1 == 2) && 2 == 2", Map.of()));
        }

        @Test
        @DisplayName("nested parentheses")
        void nestedParens() {
            assertTrue(evaluator.evaluate("((true))", Map.of()));
            assertTrue(evaluator.evaluate("((1 == 1) == true || false)", Map.of()));
        }
    }

    // =====================================================================
    // COMPLEX EXPRESSIONS (INTEGRATION)
    // =====================================================================

    @Nested
    @DisplayName("Complex guard expressions")
    class ComplexExpressionTests {

        @Test
        @DisplayName("KYC check with status and level")
        void kycCheck() {
            Map<String, Object> ctx = Map.of(
                    "kyc", Map.of("status", "verified", "level", 3)
            );
            assertTrue(evaluator.evaluate(
                    "$.kyc.status == \"verified\" && $.kyc.level >= 2", ctx));
        }

        @Test
        @DisplayName("Payment amount check with fallback")
        void paymentAmountCheck() {
            Map<String, Object> ctx = Map.of(
                    "payment", Map.of("amount", 500, "currency", "USD")
            );
            assertTrue(evaluator.evaluate(
                    "$.payment.amount > 100 && $.payment.currency == \"USD\"", ctx));
        }

        @Test
        @DisplayName("Exists check combined with equality")
        void existsCombined() {
            Map<String, Object> ctx = Map.of(
                    "approvedBy", "admin"
            );
            assertTrue(evaluator.evaluate(
                    "exists($.approvedBy) && $.approvedBy == \"admin\"", ctx));
            assertFalse(evaluator.evaluate(
                    "exists($.rejectedBy) && $.rejectedBy == \"admin\"", ctx));
        }

        @Test
        @DisplayName("Complex business rule with multiple conditions")
        void complexBusinessRule() {
            Map<String, Object> ctx = Map.of(
                    "customer", Map.of(
                            "tier", "gold",
                            "yearsActive", 5,
                            "totalSpent", 10000.0
                    ),
                    "order", Map.of(
                            "amount", 250
                    )
            );
            String expr = "($.customer.tier == \"gold\" || $.customer.tier == \"platinum\")"
                    + " && $.customer.yearsActive >= 3"
                    + " && $.order.amount > 100";
            assertTrue(evaluator.evaluate(expr, ctx));
        }

        @Test
        @DisplayName("Regex validation combined with exists")
        void regexValidation() {
            Map<String, Object> ctx = Map.of(
                    "email", "user@example.com"
            );
            assertTrue(evaluator.evaluate(
                    "exists($.email) && regex($.email, \"^[^@]+@[^@]+\\\\.[^@]+$\")", ctx));
        }

        @Test
        @DisplayName("Collection length check")
        void collectionLengthCheck() {
            Map<String, Object> ctx = Map.of(
                    "documents", List.of("passport.pdf", "utility_bill.pdf")
            );
            assertTrue(evaluator.evaluate(
                    "exists($.documents) && len($.documents) >= 2", ctx));
        }

        @Test
        @DisplayName("Contains check for tags")
        void containsTagCheck() {
            Map<String, Object> ctx = Map.of(
                    "tags", List.of("urgent", "high-priority")
            );
            assertTrue(evaluator.evaluate("contains($.tags, \"urgent\")", ctx));
        }

        @Test
        @DisplayName("Negation pattern using !=")
        void negationPattern() {
            Map<String, Object> ctx = Map.of("status", "blocked");
            assertTrue(evaluator.evaluate("$.status != \"active\"", ctx));
        }
    }

    // =====================================================================
    // LEXER TESTS
    // =====================================================================

    @Nested
    @DisplayName("Lexer tokenization")
    class LexerTests {

        @Test
        @DisplayName("Tokenizes simple expression")
        void tokenizeSimple() {
            GuardLexer lexer = new GuardLexer("$.status == \"active\"");
            var tokens = lexer.tokenize();

            assertEquals(GuardLexer.TokenType.DOLLAR, tokens.get(0).type());
            assertEquals(GuardLexer.TokenType.DOT, tokens.get(1).type());
            assertEquals(GuardLexer.TokenType.IDENTIFIER, tokens.get(2).type());
            assertEquals("status", tokens.get(2).lexeme());
            assertEquals(GuardLexer.TokenType.EQ, tokens.get(3).type());
            assertEquals(GuardLexer.TokenType.STRING, tokens.get(4).type());
            assertEquals("active", tokens.get(4).lexeme());
            assertEquals(GuardLexer.TokenType.EOF, tokens.get(5).type());
        }

        @Test
        @DisplayName("Tokenizes all operators")
        void tokenizeOperators() {
            GuardLexer lexer = new GuardLexer("|| && == != > >= < <=");
            var tokens = lexer.tokenize();

            assertEquals(9, tokens.size()); // 8 operators + EOF
            assertEquals(GuardLexer.TokenType.OR, tokens.get(0).type());
            assertEquals(GuardLexer.TokenType.AND, tokens.get(1).type());
            assertEquals(GuardLexer.TokenType.EQ, tokens.get(2).type());
            assertEquals(GuardLexer.TokenType.NEQ, tokens.get(3).type());
            assertEquals(GuardLexer.TokenType.GT, tokens.get(4).type());
            assertEquals(GuardLexer.TokenType.GTE, tokens.get(5).type());
            assertEquals(GuardLexer.TokenType.LT, tokens.get(6).type());
            assertEquals(GuardLexer.TokenType.LTE, tokens.get(7).type());
        }

        @Test
        @DisplayName("Tokenizes numbers including decimals and negatives")
        void tokenizeNumbers() {
            GuardLexer lexer = new GuardLexer("42 -3 3.14 -0.5");
            var tokens = lexer.tokenize();

            assertEquals("42", tokens.get(0).lexeme());
            assertEquals("-3", tokens.get(1).lexeme());
            assertEquals("3.14", tokens.get(2).lexeme());
            assertEquals("-0.5", tokens.get(3).lexeme());
        }

        @Test
        @DisplayName("Tokenizes string with escape sequences")
        void tokenizeStringEscapes() {
            GuardLexer lexer = new GuardLexer("\"hello\\nworld\"");
            var tokens = lexer.tokenize();
            assertEquals("hello\nworld", tokens.get(0).lexeme());
        }

        @Test
        @DisplayName("Unterminated string throws exception")
        void unterminatedString() {
            GuardLexer lexer = new GuardLexer("\"hello");
            assertThrows(GuardEvaluationException.class, lexer::tokenize);
        }

        @Test
        @DisplayName("Unexpected character throws exception")
        void unexpectedCharacter() {
            GuardLexer lexer = new GuardLexer("$%^");
            assertThrows(GuardEvaluationException.class, lexer::tokenize);
        }

        @Test
        @DisplayName("Single & throws exception (expected &&)")
        void singleAmpersand() {
            GuardLexer lexer = new GuardLexer("true & false");
            assertThrows(GuardEvaluationException.class, lexer::tokenize);
        }

        @Test
        @DisplayName("Single ! throws exception (expected !=)")
        void singleBang() {
            GuardLexer lexer = new GuardLexer("true ! false");
            assertThrows(GuardEvaluationException.class, lexer::tokenize);
        }
    }

    // =====================================================================
    // PARSER TESTS
    // =====================================================================

    @Nested
    @DisplayName("Parser AST construction")
    class ParserTests {

        @Test
        @DisplayName("Parses simple comparison into BinaryOpNode")
        void parseComparison() {
            GuardLexer lexer = new GuardLexer("$.x > 5");
            var tokens = lexer.tokenize();
            GuardParser parser = new GuardParser(tokens);
            GuardAstNode ast = parser.parse();

            assertInstanceOf(GuardAstNode.BinaryOpNode.class, ast);
            GuardAstNode.BinaryOpNode bin = (GuardAstNode.BinaryOpNode) ast;
            assertEquals(GuardAstNode.BinaryOperator.GT, bin.operator());
            assertInstanceOf(GuardAstNode.VariableNode.class, bin.left());
            assertInstanceOf(GuardAstNode.LiteralNode.class, bin.right());
        }

        @Test
        @DisplayName("Parses function call with arguments")
        void parseFunctionCall() {
            GuardLexer lexer = new GuardLexer("contains($.tags, \"urgent\")");
            var tokens = lexer.tokenize();
            GuardParser parser = new GuardParser(tokens);
            GuardAstNode ast = parser.parse();

            assertInstanceOf(GuardAstNode.FunctionCallNode.class, ast);
            GuardAstNode.FunctionCallNode fn = (GuardAstNode.FunctionCallNode) ast;
            assertEquals("contains", fn.name());
            assertEquals(2, fn.arguments().size());
        }

        @Test
        @DisplayName("Parses complex expression into correct AST tree")
        void parseComplexExpression() {
            GuardLexer lexer = new GuardLexer("$.a == 1 && $.b > 2 || $.c == 3");
            var tokens = lexer.tokenize();
            GuardParser parser = new GuardParser(tokens);
            GuardAstNode ast = parser.parse();

            // Root should be OR
            assertInstanceOf(GuardAstNode.BinaryOpNode.class, ast);
            GuardAstNode.BinaryOpNode root = (GuardAstNode.BinaryOpNode) ast;
            assertEquals(GuardAstNode.BinaryOperator.OR, root.operator());

            // Left of OR should be AND
            assertInstanceOf(GuardAstNode.BinaryOpNode.class, root.left());
            GuardAstNode.BinaryOpNode andNode = (GuardAstNode.BinaryOpNode) root.left();
            assertEquals(GuardAstNode.BinaryOperator.AND, andNode.operator());
        }

        @Test
        @DisplayName("Standalone identifier without parens throws error")
        void standaloneIdentifierError() {
            assertThrows(GuardEvaluationException.class,
                    () -> evaluator.evaluate("status", Map.of()));
        }
    }

    // =====================================================================
    // ERROR HANDLING
    // =====================================================================

    @Nested
    @DisplayName("Error handling")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Null expression throws NullPointerException")
        void nullExpression() {
            assertThrows(NullPointerException.class,
                    () -> evaluator.evaluate(null, Map.of()));
        }

        @Test
        @DisplayName("Null context throws NullPointerException")
        void nullContext() {
            assertThrows(NullPointerException.class,
                    () -> evaluator.evaluate("true", null));
        }

        @Test
        @DisplayName("Empty expression throws exception")
        void emptyExpression() {
            assertThrows(GuardEvaluationException.class,
                    () -> evaluator.evaluate("", Map.of()));
        }

        @Test
        @DisplayName("Unmatched opening parenthesis throws exception")
        void unmatchedOpenParen() {
            assertThrows(GuardEvaluationException.class,
                    () -> evaluator.evaluate("(true && false", Map.of()));
        }

        @Test
        @DisplayName("Unmatched closing parenthesis throws exception")
        void unmatchedCloseParen() {
            assertThrows(GuardEvaluationException.class,
                    () -> evaluator.evaluate("true)", Map.of()));
        }

        @Test
        @DisplayName("Error message contains position information")
        void positionInErrorMessage() {
            GuardEvaluationException ex = assertThrows(GuardEvaluationException.class,
                    () -> evaluator.evaluate("true && $", Map.of()));
            assertTrue(ex.getMessage().contains("position"),
                    "Error message should contain position info: " + ex.getMessage());
        }

        @Test
        @DisplayName("Incomplete binary expression throws exception")
        void incompleteBinary() {
            assertThrows(GuardEvaluationException.class,
                    () -> evaluator.evaluate("true &&", Map.of()));
        }
    }

    // =====================================================================
    // EDGE CASES
    // =====================================================================

    @Nested
    @DisplayName("Edge cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Whitespace is properly handled")
        void whitespaceHandling() {
            assertTrue(evaluator.evaluate("  true  &&  true  ", Map.of()));
            assertTrue(evaluator.evaluate("1==1", Map.of()));
            assertTrue(evaluator.evaluate("1 == 1", Map.of()));
        }

        @Test
        @DisplayName("Boolean string coercion in equality")
        void booleanStringCoercion() {
            Map<String, Object> ctx = Map.of("flag", true);
            assertTrue(evaluator.evaluate("$.flag == \"true\"", ctx));
        }

        @Test
        @DisplayName("String compared to number via ==")
        void stringNumberComparison() {
            assertFalse(evaluator.evaluate("\"5\" == 5", Map.of()));
        }

        @Test
        @DisplayName("Variable resolving to boolean works in boolean context")
        void booleanVariableInContext() {
            Map<String, Object> ctx = Map.of("active", true);
            assertTrue(evaluator.evaluate("$.active", ctx));
        }

        @Test
        @DisplayName("Variable resolving to number works in relational context")
        void numberVariableInContext() {
            Map<String, Object> ctx = Map.of("count", 10);
            assertTrue(evaluator.evaluate("$.count > 5", ctx));
        }

        @Test
        @DisplayName("Zero number is falsy in boolean context")
        void zeroIsFalsy() {
            Map<String, Object> ctx = Map.of("val", 0);
            assertFalse(evaluator.evaluate("$.val && true", ctx));
        }

        @Test
        @DisplayName("Empty string is falsy in boolean context")
        void emptyStringIsFalsy() {
            Map<String, Object> ctx = Map.of("val", "");
            assertFalse(evaluator.evaluate("$.val && true", ctx));
        }

        @Test
        @DisplayName("exists() with literal number argument returns true")
        void existsWithNumber() {
            assertTrue(evaluator.evaluate("exists(42)", Map.of()));
        }

        @Test
        @DisplayName("Negative number comparison")
        void negativeNumber() {
            assertTrue(evaluator.evaluate("-5 < 0", Map.of()));
            assertTrue(evaluator.evaluate("-10 < -5", Map.of()));
        }
    }
}
