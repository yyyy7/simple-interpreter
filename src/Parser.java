package lox;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;

//import com.sun.javafx.fxml.expression.Expression.Parser.Token;

import static lox.TokenType.*;

/*
 * Syntax Grammar

 * program        → declaration* EOF ;

 * declaration    → classDecl
 *                | funDecl
 *                | varDecl
 *                | statement ;

 * classDecl      → "class" IDENTIFIER ( "<" IDENTIFIER )?
 *                  "{" function* "}" ;
 * funDecl        → "fun" function ;
 * varDecl        → "var" IDENTIFIER ( "=" expression )? ";" ;

 * statement      → exprStmt
 *                | forStmt
 *                | ifStmt
 *                | printStmt
 *                | returnStmt
 *                | whileStmt
 *                | block ;
 * 
 * exprStmt       → expression ";" ;
 * forStmt        → "for" "(" ( varDecl | exprStmt | ";" )
 *                            expression? ";"
 *                            expression? ")" statement ;
 * ifStmt         → "if" "(" expression ")" statement ( "else" statement )? ;
 * printStmt      → "print" expression ";" ;
 * returnStmt     → "return" expression? ";" ;
 * whileStmt      → "while" "(" expression ")" statement ;
 * block          → "{" declaration* "}" ;

 * expression     → assignment ( "," assignment)* ;
 * 
 * assignment     → ( call "." )? IDENTIFIER "=" assignment;
 *                | logic_or ( "?" logic_or ":" logic_or)? ; 
 * 
 * logic_or       → logic_and ( "or" logic_and )* ;
 * logic_and      → equality ( "and" equality )* ;
 * equality       → comparison ( ( "!=" | "==" ) comparison )* ;
 * comparison     → addition ( ( ">" | ">=" | "<" | "<=" ) addition )* ;
 * addition       → multiplication ( ( "-" | "+" ) multiplication )* ;
 * multiplication → unary ( ( "/" | "*" ) unary )* ;
 * 
 * unary          → ( "!" | "-" ) unary | call ;
 * call           → primary ( "(" arguments? ")" | "." IDENTIFIER )* ;
 * primary        → "true" | "false" | "nil" | "this"
 *                | NUMBER | STRING | IDENTIFIER | "(" expression ")"
 *                | "super" "." IDENTIFIER ;

 * function       → IDENTIFIER "(" parameters? ")" block ;
 * parameters     → IDENTIFIER ( "," IDENTIFIER )* ;
 * arguments      → expression ( "," expression )* ;

 * NUMBER         → DIGIT+ ( "." DIGIT+ )? ;
 * STRING         → '"' <any char except '"'>* '"' ;
 * IDENTIFIER     → ALPHA ( ALPHA | DIGIT )* ;
 * ALPHA          → 'a' ... 'z' | 'A' ... 'Z' | '_' ;
 * DIGIT          → '0' ... '9' ;
*/

/**
 * Parser
 */
public class Parser {
    private static class ParserError extends RuntimeException {}

    private final List<Token> tokens;
    private int current = 0;

    Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    /*Expr parse() {
        try {
            return expression();
        } catch (ParserError error) {
            return null;
        }
    }*/

    List<Stmt> parse() {
        List<Stmt> statements = new ArrayList<>();
        while (!isAtEnd()) {
            statements.add(declaration());
        }

        return statements;
    }

    private Stmt declaration() {
        try {
            if (match(VAR)) return varDeclaration();

            return statement();
        } catch (ParserError error) {
            synchronize();
            return null;
        }
    }

    private Stmt varDeclaration() {
        Token name = consume(IDENTIFIER, "Expect variable name.");

        Expr initializer = null;
        if (match(EQUAL)) {
            initializer = expression();
        }

        consume(SEMICOLON, "Expect ';' after variable declaration.");
        return new Stmt.Var(name, initializer);
    }

    private Stmt statement() {
        if (match(CLASS)) return classDeclaration();

        if (match(IF)) return ifStatement();

        if (match(FUN)) return function("function");

        if (match(PRINT)) return printStatement();

        if (match(LEFT_BRACE)) return new Stmt.Block(block());

        if (match(FOR)) return forStatement();

        if (match(WHILE)) return whileStatement();

        if (match(RETURN)) return returnStatement();

        return expressionStatement();
    }

    private Stmt classDeclaration() {
        Token name = consume(IDENTIFIER, "Expect class name.");

        Expr.Variable superclass = null;
        if (match(LESS)) {
            consume(IDENTIFIER, "Expect superclass name.");
            superclass = new Expr.Variable(previous());
        }
        consume(LEFT_BRACE, "Expect '{' before class body.");

        List<Stmt.Function> methods = new ArrayList<>();
        while (!check(RIGHT_BRACE) && !isAtEnd()) {
            methods.add(function("method"));
        } 

        consume(RIGHT_BRACE, "Expect '}' after class body.");
        return new Stmt.Class(name, superclass, methods);
    }

    private Stmt ifStatement() {
        consume(LEFT_PAREN, "Expect '(' after if");
        Expr condition = expression();
        consume(RIGHT_PAREN, "Expect ')' after if condition");

        Stmt thenBranch = statement();
        Stmt elseBranch = null;
        if (match(ELSE)) {
            elseBranch = statement();
        }

        return new Stmt.If(condition, thenBranch, elseBranch);
    }

    private Stmt printStatement() {
        Expr value = expression();
        consume(SEMICOLON, "Expect ';' after value.");
        return new Stmt.Print(value);
    }

    private Stmt expressionStatement() {
        Expr expr = expression();
        consume(SEMICOLON, "Expect ';' after expression.");
        return new Stmt.Expression(expr);
        
    }

    // 转换成while语句实现for循环
    private Stmt forStatement() {
        consume(LEFT_PAREN, "Expect '(' after 'for'.");

        Stmt initializer;
        if (match(SEMICOLON)) {
            initializer = null;
        } else if (match(VAR)) {
            initializer = varDeclaration();
        } else {
            initializer = expressionStatement();
        }

        Expr condition = null;
        if (!check(SEMICOLON)) {
            condition = expression();
        }
        consume(SEMICOLON, "Expect ';' after loop condition.");

        Expr increment = null;
        if (!check(SEMICOLON)) {
            increment = expression();
        }
        consume(RIGHT_PAREN, "Expect ')' after for clauses.");

        Stmt body = statement();

        if (increment != null) {
            body = new Stmt.Block(Arrays.asList(body, new Stmt.Expression(increment)));
        }

        if (condition == null) condition = new Expr.Literal(true);
        body = new Stmt.While(condition, body);

        if (initializer != null) {
            body = new Stmt.Block(Arrays.asList(initializer, body));
        }

        return body;
    }

    private Stmt whileStatement() {
        consume(LEFT_PAREN, "Expect '(' after 'while'.");
        Expr condition = expression();
        consume(RIGHT_PAREN, "Expect ')' after condition.");

        Stmt body = statement();

        return new Stmt.While(condition, body);
    }

    private Stmt returnStatement() {
        Token keyword = previous();
        Expr value = null;
        if (!check(SEMICOLON)) {
            value = expression();
        }

        consume(SEMICOLON, "Expect ';' after return value.");
        return new Stmt.Return(keyword, value);
    }

    private List<Stmt> block() {
        List<Stmt> statements = new ArrayList<>();

        while (!check(RIGHT_BRACE) && !isAtEnd()) {
            statements.add(declaration());
        }

        consume(RIGHT_BRACE, "Expect '}' after block.");
        return statements;
    }

    private Expr expression() {
        return expression("");
    }
    
    private Expr expression(String kind) {
        Expr expr = assignment();

        // * 如果在解析函数调用参数时，不构造 comma 表达式
        if (kind != "function") {
            while (match(COMMA)) { 
                Expr rightExpr = assignment();
                expr = new Expr.Binary(expr, previous(), rightExpr);
            }
        }
        return expr;
    }

    private Expr assignment() {
        Expr expr = or();

        if (match(EQUAL)) {
            Token equals = previous();
            Expr value = assignment();

            if (expr instanceof Expr.Variable) {
                Token name = ((Expr.Variable)expr).name;
                return new Expr.Assign(name, value);
            } else if (expr instanceof Expr.Get) {
                Expr.Get get = (Expr.Get)expr;
                return new Expr.Set(get.object, get.name, value);
            }

            error(equals, "Invalid assignment target.");
        } else if (match(QUESTION_MARK)){
            Expr leftExpr = or();
            Expr rightExpr;
            if (match(COLON)){
                rightExpr = or();
                return new Expr.Ternary(expr, leftExpr, rightExpr);
            }
            error(previous(), "expect ':' after ? ");
        }

        return expr;
    }

    private Expr or() {
        Expr expr = and();

        while (match(OR)) {
            Token operator = previous();
            Expr right = and();
            expr = new Expr.Logical(expr, operator, right);
        }

        return expr;
    }

    private Expr and() {
        Expr expr = equality();

        while (match(AND)) {
            Token operator = previous();
            Expr right = equality();
            expr = new Expr.Logical(expr, operator, right);
        }
        
        return expr;
    }

    private Expr equality() {
        Expr expr = comparison();

        while (match(BANG_EQUAL, EQUAL_EQUAL)) {
            Token operator = previous();
            Expr right = comparison();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr comparison() {
        Expr expr = addition();

        while (match(GREATER, GREATER_EQUAL, LESS, LESS_EQUAL)) {
            Token operator = previous();
            Expr right = addition();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr addition() {
        Expr expr = multiplication();

        while (match(MINUS, PLUS)) {
            Token operator = previous();
            Expr right = multiplication();
            expr = new Expr.Binary(expr, operator, right);
        }
        
        return expr;
    }

    private Expr multiplication() {
        Expr expr = unary();

        while (match(SLASH, STAR)) {
            Token operator = previous();
            Expr right = unary();
            expr = new Expr.Binary(expr, operator, right);
        }
        
        return expr;
    }

    private Expr unary() {
        if (match(BANG, MINUS)) {
            Token operator = previous();
            Expr right = unary();
            return new Expr.Unary(operator, right);
        }
        // return primary();
        return call();
    }

    private Expr call() {
        Expr expr = primary();

        while (true) {
            if (match(LEFT_PAREN)) {
                expr = finishCall(expr);
            } else if (match(DOT)) {
                Token name = consume(IDENTIFIER, "Expect property name fater '.'.");
                expr = new Expr.Get(expr, name);
            } else {
                break;
            }
        }
        
        return expr;
    }

    private Stmt.Function function(String kind) {
        Token name = consume(IDENTIFIER, "Expect " + kind + " name.");
        consume(LEFT_PAREN, "Expect '(' after " + kind + " name.");
        List<Token> parameters = new ArrayList<>();
        if (!check(RIGHT_PAREN)) {
            do {
                if (parameters.size() >= 8) {
                    error(peek(), "Cannot have more than 8 parameters.");
                }

                parameters.add(consume(IDENTIFIER, "Expect parameter name."));
            } while (match(COMMA));
        }
        consume(RIGHT_PAREN, "Expect ')' after parameters.");

        consume(LEFT_BRACE, "Expect '{' before " + kind + " body.");
        List<Stmt> body = block();
        //consume(RIGHT_BRACE, "Expect '}' after " + kind + "body.");
        return new Stmt.Function(name, parameters, body);
    }

    private Expr primary() {
        if (match(FALSE)) return new Expr.Literal(false);
        if (match(TRUE)) return new Expr.Literal(true);
        if (match(NIL)) return new Expr.Literal(null);

        if (match(NUMBER, STRING)) {
            return new Expr.Literal(previous().literal);
        }

        if (match(SUPER)) {
            Token keyword = previous();
            consume(DOT, "Expect '.' after super.");
            Token method = consume(IDENTIFIER, "Expect superclass method name.");
            return new Expr.Super(keyword, method);
        }

        if (match(THIS)) return new Expr.This(previous());

        if (match(IDENTIFIER)) return new Expr.Variable(previous());

        if (match(LEFT_PAREN)) {
            Expr expr = expression();
            consume(RIGHT_PAREN, "Expect ')' after expression.");
            return new Expr.Grouping(expr);
        }

        error(peek(), "Except expression.");
        return null;
        
    }

    private Token consume(TokenType type, String message) {
        if (check(type)) return advance();

        error(peek(), message);
        return null;
    }

    private boolean match(TokenType...types) {
        for (TokenType type: types) {
            if (check(type)) {
                advance();
                return true;
            }
        }

        return false;
    }

    private boolean check(TokenType type) {
        if (isAtEnd()) return false;
        return peek().type == type;
    }

    private Token advance() {
        if (!isAtEnd()) current++;
        return previous();
    }

    private boolean isAtEnd() {
        return peek().type == EOF;
    }

    private Token peek() {
        return tokens.get(current);
    }

    private Token previous() {
        return tokens.get(current - 1);
        
    }

    static void error(Token token, String message) {
        if (token.type == TokenType.EOF) {
            Lox.report(token.line, " at end", message);
        } else {
            Lox.report(token.line, " at '" + token.lexeme + "'", message);
        }
    }

    private void synchronize() {
        advance();

        while (!isAtEnd()) {
            if (previous().type == SEMICOLON) return;

            switch (peek().type) {
                case CLASS:
                case FUN:
                case VAR:
                case FOR:
                case IF:
                case WHILE:
                case PRINT:
                case RETURN:
                     return;
            }
        }
        
    }

    private Expr finishCall(Expr callee) {
        List<Expr> arguments = new ArrayList<>();
        if (!match(RIGHT_PAREN)) {
            do {
                if (arguments.size() > 8) {
                    error(peek(), "Cannot have more than 8 arguments.");
                }
   
                arguments.add(expression("function"));
            } while (match(COMMA));
        }
        
        Token paren = consume(RIGHT_PAREN, "Expect ')' after arguments.");

        return new Expr.Call(callee, paren, arguments);
    }
}