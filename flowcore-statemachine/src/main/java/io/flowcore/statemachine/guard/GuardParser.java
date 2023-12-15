package io.flowcore.statemachine.guard;

import java.util.ArrayList;
import java.util.List;

import static io.flowcore.statemachine.guard.GuardLexer.TokenType;

/**
 * Recursive descent parser for the guard expression language.
 *
 * <p>Parses a token list produced by {@link GuardLexer} into an AST
 * composed of {@link GuardAstNode} instances. The grammar is:</p>
 *
 * <pre>
 * guard        := orExpr ;
 * orExpr       := andExpr ( "||" andExpr )* ;
 * andExpr      := eqExpr ( "&&" eqExpr )* ;
 * eqExpr       := relExpr ( ("==" | "!=") relExpr )* ;
 * relExpr      := primary ((">" | ">=" | "<" | "<=") primary)* ;
 * primary      := literal | variable | functionCall | "(" guard ")" ;
 * literal      := "true" | "false" | number | string ;
 * variable     := "$." identifier ( "." identifier )* ;
 * functionCall := identifier "(" (guard ("," guard)*)? ")" ;
 * identifier   := [A-Za-z_][A-Za-z0-9_]* ;
 * </pre>
 *
 * <p>The parser is not thread-safe and is intended for single-use per expression.</p>
 */
public class GuardParser {

    private final List<GuardLexer.Token> tokens;
    private int current;

    /**
     * Creates a new parser over the given token list.
     *
     * @param tokens the token list (must include an EOF token at the end)
     * @throws NullPointerException if tokens is null
     */
    public GuardParser(List<GuardLexer.Token> tokens) {
        this.tokens = List.copyOf(tokens);
        this.current = 0;
    }

    /**
     * Parses the token list and returns the root AST node.
     *
     * @return the root AST node representing the entire expression
     * @throws GuardEvaluationException if the expression is syntactically invalid
     */
    public GuardAstNode parse() {
        GuardAstNode root = parseGuard();
        if (!isAtEnd()) {
            GuardLexer.Token unexpected = peek();
            throw GuardEvaluationException.unexpectedToken(
                    unexpected.position(),
                    "end of expression",
                    "'" + unexpected.lexeme() + "'");
        }
        return root;
    }

    // -----------------------------------------------------------------------
    // Grammar rule methods
    // -----------------------------------------------------------------------

    private GuardAstNode parseGuard() {
        return parseOrExpr();
    }

    private GuardAstNode parseOrExpr() {
        GuardAstNode left = parseAndExpr();
        while (match(TokenType.OR)) {
            GuardLexer.Token op = previous();
            GuardAstNode right = parseAndExpr();
            left = new GuardAstNode.BinaryOpNode(left, GuardAstNode.BinaryOperator.OR, right);
        }
        return left;
    }

    private GuardAstNode parseAndExpr() {
        GuardAstNode left = parseEqExpr();
        while (match(TokenType.AND)) {
            GuardAstNode right = parseEqExpr();
            left = new GuardAstNode.BinaryOpNode(left, GuardAstNode.BinaryOperator.AND, right);
        }
        return left;
    }

    private GuardAstNode parseEqExpr() {
        GuardAstNode left = parseRelExpr();
        while (match(TokenType.EQ) || match(TokenType.NEQ)) {
            GuardLexer.Token op = previous();
            GuardAstNode.BinaryOperator binOp = op.type() == TokenType.EQ
                    ? GuardAstNode.BinaryOperator.EQ
                    : GuardAstNode.BinaryOperator.NEQ;
            GuardAstNode right = parseRelExpr();
            left = new GuardAstNode.BinaryOpNode(left, binOp, right);
        }
        return left;
    }

    private GuardAstNode parseRelExpr() {
        GuardAstNode left = parsePrimary();
        while (match(TokenType.GT) || match(TokenType.GTE)
                || match(TokenType.LT) || match(TokenType.LTE)) {
            GuardLexer.Token op = previous();
            GuardAstNode.BinaryOperator binOp = switch (op.type()) {
                case GT -> GuardAstNode.BinaryOperator.GT;
                case GTE -> GuardAstNode.BinaryOperator.GTE;
                case LT -> GuardAstNode.BinaryOperator.LT;
                case LTE -> GuardAstNode.BinaryOperator.LTE;
                default -> throw new IllegalStateException("unexpected relational operator: " + op.type());
            };
            GuardAstNode right = parsePrimary();
            left = new GuardAstNode.BinaryOpNode(left, binOp, right);
        }
        return left;
    }

    private GuardAstNode parsePrimary() {
        // Boolean literals
        if (match(TokenType.TRUE)) {
            return new GuardAstNode.LiteralNode(Boolean.TRUE);
        }
        if (match(TokenType.FALSE)) {
            return new GuardAstNode.LiteralNode(Boolean.FALSE);
        }

        // Number literal
        if (match(TokenType.NUMBER)) {
            String lexeme = previous().lexeme();
            Number value = parseNumber(lexeme);
            return new GuardAstNode.LiteralNode(value);
        }

        // String literal
        if (match(TokenType.STRING)) {
            return new GuardAstNode.LiteralNode(previous().lexeme());
        }

        // Variable: $.path.to.value
        if (match(TokenType.DOLLAR)) {
            return parseVariable();
        }

        // Function call or grouped expression
        if (check(TokenType.IDENTIFIER)) {
            return parseFunctionCallOrIdentifier();
        }

        // Parenthesized expression
        if (match(TokenType.LPAREN)) {
            GuardAstNode inner = parseGuard();
            consume(TokenType.RPAREN, "expected ')' after expression");
            return inner;
        }

        GuardLexer.Token tok = peek();
        throw GuardEvaluationException.unexpectedToken(
                tok.position(),
                "a literal, variable, function call, or '('",
                tokenDescription(tok));
    }

    private GuardAstNode parseVariable() {
        // We have consumed '$'; expect '.' then identifier
        consume(TokenType.DOT, "expected '.' after '$' in variable reference");
        consume(TokenType.IDENTIFIER, "expected identifier after '$.' in variable reference");
        List<String> segments = new ArrayList<>();
        segments.add(previous().lexeme());

        while (match(TokenType.DOT)) {
            consume(TokenType.IDENTIFIER, "expected identifier after '.' in variable path");
            segments.add(previous().lexeme());
        }

        return new GuardAstNode.VariableNode(segments);
    }

    private GuardAstNode parseFunctionCallOrIdentifier() {
        GuardLexer.Token identToken = advance();
        String name = identToken.lexeme();

        // If next token is '(' this is a function call
        if (match(TokenType.LPAREN)) {
            List<GuardAstNode> args = new ArrayList<>();
            if (!check(TokenType.RPAREN)) {
                args.add(parseGuard());
                while (match(TokenType.COMMA)) {
                    args.add(parseGuard());
                }
            }
            consume(TokenType.RPAREN, "expected ')' after function arguments");
            return new GuardAstNode.FunctionCallNode(name, args);
        }

        // Standalone identifier not followed by '(' -- not valid in this grammar
        throw GuardEvaluationException.atPosition(
                identToken.position(),
                "unexpected identifier '" + name + "'; did you mean a function call '" + name + "(...)' or a variable '$." + name + "'?");
    }

    // -----------------------------------------------------------------------
    // Token navigation helpers
    // -----------------------------------------------------------------------

    private GuardLexer.Token peek() {
        return tokens.get(current);
    }

    private GuardLexer.Token previous() {
        return tokens.get(current - 1);
    }

    private GuardLexer.Token advance() {
        if (!isAtEnd()) {
            current++;
        }
        return tokens.get(current - 1);
    }

    private boolean isAtEnd() {
        return peek().type() == TokenType.EOF;
    }

    private boolean check(TokenType type) {
        return !isAtEnd() && peek().type() == type;
    }

    private boolean match(TokenType type) {
        if (check(type)) {
            advance();
            return true;
        }
        return false;
    }

    private GuardLexer.Token consume(TokenType type, String errorMessage) {
        if (check(type)) {
            return advance();
        }
        GuardLexer.Token tok = peek();
        throw GuardEvaluationException.unexpectedToken(
                tok.position(),
                errorMessage,
                tokenDescription(tok));
    }

    private static String tokenDescription(GuardLexer.Token token) {
        if (token.type() == TokenType.EOF) {
            return "end of expression";
        }
        return "'" + token.lexeme() + "'";
    }

    private static Number parseNumber(String lexeme) {
        if (lexeme.contains(".")) {
            return Double.parseDouble(lexeme);
        }
        long longValue = Long.parseLong(lexeme);
        if (longValue >= Integer.MIN_VALUE && longValue <= Integer.MAX_VALUE) {
            return (int) longValue;
        }
        return longValue;
    }
}
