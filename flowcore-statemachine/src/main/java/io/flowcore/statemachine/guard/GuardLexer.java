package io.flowcore.statemachine.guard;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Tokenizer (lexer) for the guard expression language.
 *
 * <p>Produces a flat list of {@link Token} instances from a raw expression string.
 * The lexer performs no semantic validation; it only breaks the input into
 * classified chunks with position tracking for error reporting.</p>
 *
 * <h3>Token types</h3>
 * <table>
 *   <tr><th>Token Type</th><th>Lexeme Examples</th></tr>
 *   <tr><td>{@code AND}</td><td>{@code &&}</td></tr>
 *   <tr><td>{@code OR}</td><td>{@code ||}</td></tr>
 *   <tr><td>{@code EQ}</td><td>{@code ==}</td></tr>
 *   <tr><td>{@code NEQ}</td><td>{@code !=}</td></tr>
 *   <tr><td>{@code GT}</td><td>{@code >}</td></tr>
 *   <tr><td>{@code GTE}</td><td>{@code >=}</td></tr>
 *   <tr><td>{@code LT}</td><td>{@code <}</td></tr>
 *   <tr><td>{@code LTE}</td><td>{@code <=}</td></tr>
 *   <tr><td>{@code LPAREN}</td><td>{@code (}</td></tr>
 *   <tr><td>{@code RPAREN}</td><td>{@code )}</td></tr>
 *   <tr><td>{@code COMMA}</td><td>{@code ,}</td></tr>
 *   <tr><td>{@code DOT}</td><td>{@code .}</td></tr>
 *   <tr><td>{@code DOLLAR}</td><td>{@code $}</td></tr>
 *   <tr><td>{@code TRUE}</td><td>{@code true}</td></tr>
 *   <tr><td>{@code FALSE}</td><td>{@code false}</td></tr>
 *   <tr><td>{@code NUMBER}</td><td>{@code 42}, {@code 3.14}</td></tr>
 *   <tr><td>{@code STRING}</td><td>{@code "hello"}</td></tr>
 *   <tr><td>{@code IDENTIFIER}</td><td>{@code status}, {@code my_func}</td></tr>
 *   <tr><td>{@code EOF}</td><td><em>end of input</em></td></tr>
 * </table>
 */
public class GuardLexer {

    /**
     * Enumeration of all token types in the guard expression language.
     */
    public enum TokenType {
        AND, OR, EQ, NEQ, GT, GTE, LT, LTE,
        LPAREN, RPAREN, COMMA, DOT, DOLLAR,
        TRUE, FALSE, NUMBER, STRING, IDENTIFIER,
        EOF
    }

    /**
     * Represents a single lexical token with type, lexeme text, and position.
     *
     * @param type     the token type
     * @param lexeme   the exact text consumed from the input
     * @param position the zero-based character offset where this token starts
     */
    public record Token(TokenType type, String lexeme, int position) {

        /**
         * Creates a new token.
         *
         * @throws NullPointerException if type or lexeme is null
         */
        public Token {
            Objects.requireNonNull(type, "token type must not be null");
            Objects.requireNonNull(lexeme, "lexeme must not be null");
        }
    }

    // -- Instance state ----------------------------------------------------

    private final String input;
    private int pos;

    /**
     * Creates a new lexer for the given expression.
     *
     * @param input the raw guard expression string, must not be null
     */
    public GuardLexer(String input) {
        this.input = Objects.requireNonNull(input, "input expression must not be null");
        this.pos = 0;
    }

    /**
     * Tokenizes the entire input and returns the complete list of tokens,
     * always ending with an {@link TokenType#EOF EOF} token.
     *
     * @return unmodifiable list of tokens
     * @throws GuardEvaluationException if an unexpected character is encountered
     */
    public List<Token> tokenize() {
        List<Token> tokens = new ArrayList<>();
        Token token;
        do {
            token = nextToken();
            tokens.add(token);
        } while (token.type() != TokenType.EOF);
        return List.copyOf(tokens);
    }

    // -- Private helpers ---------------------------------------------------

    private Token nextToken() {
        skipWhitespace();

        if (pos >= input.length()) {
            return new Token(TokenType.EOF, "", pos);
        }

        char ch = input.charAt(pos);

        return switch (ch) {
            case '&' -> consumeDoubleChar('&', TokenType.AND);
            case '|' -> consumeDoubleChar('|', TokenType.OR);
            case '=' -> consumeDoubleChar('=', TokenType.EQ);
            case '!' -> consumeNeq();
            case '>' -> consumeGteOrGt();
            case '<' -> consumeLteOrLt();
            case '(' -> singleChar(TokenType.LPAREN);
            case ')' -> singleChar(TokenType.RPAREN);
            case ',' -> singleChar(TokenType.COMMA);
            case '.' -> singleChar(TokenType.DOT);
            case '$' -> singleChar(TokenType.DOLLAR);
            case '"' -> consumeString();
            default -> {
                if (Character.isDigit(ch) || (ch == '-' && pos + 1 < input.length() && Character.isDigit(input.charAt(pos + 1)))) {
                    yield consumeNumber();
                }
                if (Character.isLetter(ch) || ch == '_') {
                    yield consumeIdentifierOrKeyword();
                }
                throw GuardEvaluationException.atPosition(pos,
                        "unexpected character '" + ch + "'");
            }
        };
    }

    private Token singleChar(TokenType type) {
        char ch = input.charAt(pos);
        Token token = new Token(type, String.valueOf(ch), pos);
        pos++;
        return token;
    }

    private Token consumeDoubleChar(char expectedSecond, TokenType type) {
        int start = pos;
        if (pos + 1 < input.length() && input.charAt(pos + 1) == expectedSecond) {
            String lexeme = input.substring(start, start + 2);
            pos += 2;
            return new Token(type, lexeme, start);
        }
        throw GuardEvaluationException.atPosition(start,
                "expected '" + expectedSecond + expectedSecond + "' at position " + start);
    }

    private Token consumeNeq() {
        int start = pos;
        if (pos + 1 < input.length() && input.charAt(pos + 1) == '=') {
            pos += 2;
            return new Token(TokenType.NEQ, "!=", start);
        }
        throw GuardEvaluationException.atPosition(start,
                "unexpected character '!', expected '!='");
    }

    private Token consumeGteOrGt() {
        int start = pos;
        if (pos + 1 < input.length() && input.charAt(pos + 1) == '=') {
            pos += 2;
            return new Token(TokenType.GTE, ">=", start);
        }
        pos++;
        return new Token(TokenType.GT, ">", start);
    }

    private Token consumeLteOrLt() {
        int start = pos;
        if (pos + 1 < input.length() && input.charAt(pos + 1) == '=') {
            pos += 2;
            return new Token(TokenType.LTE, "<=", start);
        }
        pos++;
        return new Token(TokenType.LT, "<", start);
    }

    private Token consumeString() {
        int start = pos;
        pos++; // skip opening quote
        StringBuilder sb = new StringBuilder();
        while (pos < input.length()) {
            char ch = input.charAt(pos);
            if (ch == '\\') {
                pos++;
                if (pos >= input.length()) {
                    throw GuardEvaluationException.atPosition(pos,
                            "unterminated string escape at end of input");
                }
                sb.append(unescape(input.charAt(pos)));
                pos++;
            } else if (ch == '"') {
                pos++; // skip closing quote
                String lexeme = input.substring(start, pos);
                return new Token(TokenType.STRING, sb.toString(), start);
            } else {
                sb.append(ch);
                pos++;
            }
        }
        throw GuardEvaluationException.atPosition(start,
                "unterminated string literal");
    }

    private static char unescape(char ch) {
        return switch (ch) {
            case 'n' -> '\n';
            case 't' -> '\t';
            case 'r' -> '\r';
            case '\\' -> '\\';
            case '"' -> '"';
            default -> ch;
        };
    }

    private Token consumeNumber() {
        int start = pos;
        if (input.charAt(pos) == '-') {
            pos++;
        }
        while (pos < input.length() && Character.isDigit(input.charAt(pos))) {
            pos++;
        }
        if (pos < input.length() && input.charAt(pos) == '.') {
            pos++;
            if (pos >= input.length() || !Character.isDigit(input.charAt(pos))) {
                throw GuardEvaluationException.atPosition(pos,
                        "expected digit after decimal point");
            }
            while (pos < input.length() && Character.isDigit(input.charAt(pos))) {
                pos++;
            }
        }
        String lexeme = input.substring(start, pos);
        return new Token(TokenType.NUMBER, lexeme, start);
    }

    private Token consumeIdentifierOrKeyword() {
        int start = pos;
        while (pos < input.length()) {
            char ch = input.charAt(pos);
            if (Character.isLetterOrDigit(ch) || ch == '_') {
                pos++;
            } else {
                break;
            }
        }
        String lexeme = input.substring(start, pos);
        TokenType type = switch (lexeme) {
            case "true" -> TokenType.TRUE;
            case "false" -> TokenType.FALSE;
            default -> TokenType.IDENTIFIER;
        };
        return new Token(type, lexeme, start);
    }

    private void skipWhitespace() {
        while (pos < input.length() && Character.isWhitespace(input.charAt(pos))) {
            pos++;
        }
    }
}
