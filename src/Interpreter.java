package lox;

import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
//import jdk.nashorn.internal.parser.TokenType;

/**
 * Interpreter
 */
public class Interpreter implements Expr.Visitor<Object>, Stmt.Visitor<Void> {
    private Environment globals = new Environment();
    private Environment environment = globals;
    private final Map<Expr, Integer> locals = new HashMap();

    private Object evaluate(Expr expr) {
        return expr.accept(this);
    }

    void resolve(Expr expr, int depth) {
        locals.put(expr, depth);
    }

    Interpreter() {

        globals.define("clock", new LoxCallable() {
            @Override
            public int arity() { return 0; }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                return (double)System.currentTimeMillis() / 1000.0;
            }

            @Override
            public String toString() { return "<native fn>"; }
        });
    }

    void interpreter(List<Stmt> statements) {
        try {
            for (Stmt statement : statements) {
                execute(statement);
            }
        } catch (RuntimeError error) {
            Lox.runtimeError(error);
        }
    }

    @Override
    public Void visitExpressionStmt(Stmt.Expression stmt) {
        evaluate(stmt.expression);
        return null;
    }

    @Override
    public Void visitPrintStmt(Stmt.Print stmt) {
        Object value = evaluate(stmt.expression);
        System.out.println(stringify(value));
        return null;
    }

    @Override
    public Void visitVarStmt(Stmt.Var stmt) {
        Object value = null;
        if (stmt.initializer != null) {
            value = evaluate(stmt.initializer);
        }

        environment.define(stmt.name.lexeme, value);
        return null;
    }

    @Override
    public Void visitWhileStmt(Stmt.While stmt) {
        while (isTruthy(evaluate(stmt.condition))) {
            execute(stmt.body);
        }
        
        return null;
    }

    @Override
    public Void visitClassStmt(Stmt.Class stmt) {
        Object superclass = null;
        if (stmt.superclass != null) {
            superclass = evaluate(stmt.superclass);
            if (!(superclass instanceof LoxClass)) {
                throw new RuntimeError(stmt.superclass.name, "Superclass must be a class.");
            }
        }

        environment.define(stmt.name.lexeme, null);

        if (stmt.superclass != null) {
            environment = new Environment(environment);
            environment.define("super", superclass);
        }

        Map<String, LoxFunction> methods = new HashMap<>();
        for (Stmt.Function method : stmt.methods) {
            LoxFunction function = new LoxFunction(method, environment, method.name.lexeme.equals("init"));
            methods.put(method.name.lexeme, function);
        }

        LoxClass klass = new LoxClass(stmt.name.lexeme, (LoxClass)superclass, methods);

        if (superclass != null) {
            environment = environment.enclosing();
        }

        environment.assign(stmt.name, klass);
        return null;
    }

    @Override
    public Void visitBlockStmt(Stmt.Block stmt) {
        executeBlock(stmt.statements, new Environment(environment));
        return null;
    }

    @Override
    public Void visitIfStmt(Stmt.If stmt) {
        if (isTruthy(evaluate(stmt.condition))) {
            execute(stmt.thenBranch);
        } else if (stmt.elseBranch != null) {
            execute(stmt.elseBranch);
        }
        
        return null;
    }

    @Override
    public Void visitFunctionStmt(Stmt.Function stmt) {
        LoxFunction function = new LoxFunction(stmt, environment, false);
        environment.define(stmt.name.lexeme, function);
        return null;
    }

    @Override
    public Void visitReturnStmt(Stmt.Return stmt) {
        Object value = null;
        if (stmt.value != null) value = evaluate(stmt.value);
        
        throw new Return(value);
    }

    @Override
    public Object visitAssignExpr(Expr.Assign expr) {
        Object value = evaluate(expr.value);

        // environment.assign(expr.name, value);
        Integer distance = locals.get(expr);
        if (distance != null) {
            environment.assignAt(distance, expr.name, value);
        } else {
            globals.assign(expr.name, value);

        }

        return value;
    }

    @Override
    public Object visitLiteralExpr(Expr.Literal expr) {
        return expr.value;
    }

    @Override
    public Object visitLogicalExpr(Expr.Logical expr) {
        Object left = evaluate(expr.left);

        if (expr.operator.type == TokenType.OR) {
            if (isTruthy(left)) return left;
        } else {
            if (!isTruthy(left)) return left;
        }
        
        return evaluate(expr.right);
    }

    @Override
    public Object visitSetExpr(Expr.Set expr) {
        Object object = evaluate(expr.object);

        if (!(object instanceof LoxInstance)) {
            throw new RuntimeError(expr.name, "Only instances hava fields.");
        }

        Object value = evaluate(expr.value);
        ((LoxInstance)object).set(expr.name, value);
        return value;
    }

    @Override
    public Object visitSuperExpr(Expr.Super expr) {
        int distance = locals.get(expr);
        LoxClass superclass = (LoxClass)environment.getAt(distance, "super");
        LoxInstance object = (LoxInstance)environment.getAt(distance - 1, "this");
        LoxFunction method = superclass.findMethod(object, expr.method.lexeme);

        if (method == null) {
            throw new RuntimeError(expr.method, "Undefined property '" + expr.method.lexeme + "'.");
        }

        return method;
    }

    @Override
    public Object visitThisExpr(Expr.This expr) {
        return lookUpVariable(expr.keyword, expr);
    }

    @Override
    public Object visitGroupingExpr(Expr.Grouping expr) {
        return evaluate(expr.expression);
    }

    @Override
    public Object visitTernaryExpr(Expr.Ternary expr) {
       Object conditionResult = evaluate(expr.condition); 
       if (isTruthy(conditionResult)) {
           return evaluate(expr.leftExpr);
       } else {
           return evaluate(expr.rightExpr);
       }
    }


    @Override
    public Object visitUnaryExpr(Expr.Unary expr) {
        Object rightValue = evaluate(expr.right);

        switch (expr.operator.type) {
            case MINUS:
                checkNumberOperand(expr.operator, rightValue);
                return -(double)rightValue;
            case BANG:
                return !isTruthy(rightValue);
        }

        return null;
    }

    @Override
    public Object visitVariableExpr(Expr.Variable expr) {
        return lookUpVariable(expr.name, expr);
    }

    @Override
    public Object visitCallExpr(Expr.Call expr) {
        Object callee = evaluate(expr.callee);
        
        List<Object> arguments = new ArrayList<>();
        for (Expr argument : expr.arguments) {
            arguments.add(evaluate(argument));
        }

        if (!(callee instanceof LoxCallable)) {
            throw new RuntimeError(expr.paren, "Can only call function and classes.");
        }

        LoxCallable function = (LoxCallable)callee;
        if (arguments.size() != function.arity()) {
            throw new RuntimeError(expr.paren, "Expect " + function.arity() + " arguments but got " + arguments.size() + ".");
        }

        return function.call(this, arguments);
    }

    @Override
    public Object visitGetExpr(Expr.Get expr) {
        Object object = evaluate(expr.object);
        if (object instanceof LoxInstance) {
            return ((LoxInstance) object).get(expr.name);
        }

        throw new RuntimeError(expr.name, "Only instances have properties.");
    }

    @Override
    public Object visitBinaryExpr(Expr.Binary expr) {
        Object leftValue = evaluate(expr.left);
        Object rightValue = evaluate(expr.right);

        switch (expr.operator.type) {
            case MINUS:
                checkTwoNumberOperand(expr.operator, leftValue, rightValue);
                return (double)leftValue - (double)rightValue;
            case PLUS:  
                if (leftValue instanceof Double && rightValue instanceof Double) {
                    return (double)leftValue + (double)rightValue;
                }

                if (leftValue instanceof String && rightValue instanceof String) {
                    return (String)leftValue + (String)rightValue;
                }

                if (checkAddNumberAndString(expr.operator, leftValue, rightValue)) {
                    return stringify(leftValue) + stringify(rightValue);
                }

                throw new RuntimeError(expr.operator, "Operands must be two numbers or two strings.");
            case SLASH:
                checkTwoNumberOperand(expr.operator, leftValue, rightValue);
                checkSlashZero(expr.operator, rightValue);
                return (double)leftValue / (double)rightValue;
            case STAR:
                checkTwoNumberOperand(expr.operator, leftValue, rightValue);
                return (double)leftValue * (double)rightValue;
            case GREATER:
                checkTwoNumberOperand(expr.operator, leftValue, rightValue);
                return (double)leftValue > (double)rightValue;
            case LESS:
                checkTwoNumberOperand(expr.operator, leftValue, rightValue);
                return (double)leftValue < (double)rightValue;
            case GREATER_EQUAL:
                checkTwoNumberOperand(expr.operator, leftValue, rightValue);
                return (double)leftValue >= (double)rightValue;
            case LESS_EQUAL:
                checkTwoNumberOperand(expr.operator, leftValue, rightValue);
                return (double)leftValue <= (double)rightValue;
            case BANG_EQUAL:
                return !isEqual(leftValue, rightValue);
            case EQUAL_EQUAL:
                return isEqual(leftValue, rightValue);
            case COMMA:
                return rightValue;
        }

        return null;
    }

    private boolean isTruthy(Object object) {
        if (object == null) return false;
        if (object instanceof Boolean) return (boolean)object;
        return true;
    }

    private boolean isEqual(Object a, Object b) {
        if (a == null && b == null) return true;
        if (a == null) return false;

        return a.equals(b);
    }

    private String stringify(Object object) {
        if (object == null) return "nil";

        if (object instanceof Double) {
            String text = object.toString();
            if (text.endsWith(".0")) {
                text = text.substring(0, text.length() - 2);
            }
            return text;
        }
        
        return object.toString();
    }

    private void checkNumberOperand(Token operator, Object operand) {
        if (operand instanceof Double) return;
        throwOperandRuntimeError(operator);
    }

    private void checkTwoNumberOperand(Token operator, Object leftNumber, Object rightNumber) {
        if (leftNumber instanceof Double && rightNumber instanceof Double) return;
        throwOperandRuntimeError(operator);
    }

    private boolean checkAddNumberAndString(Token operator, Object leftValue, Object rightValue) {
        if ((leftValue instanceof Double && rightValue instanceof String) || (leftValue instanceof String && rightValue instanceof Double)) {
            return true;
        }

        return false;
    }

    private void checkSlashZero(Token operator, Object rightNumber) {
        if (!rightNumber.equals(0)) return;

        throw new RuntimeError(operator, "Can't divide a number by zero");
    }

    private void throwOperandRuntimeError(Token operator) {
        throw new RuntimeError(operator, "Operand must be a number.");
    }

    private void execute(Stmt stmt) {
        stmt.accept(this);
    }

    void executeBlock(List<Stmt> statements, Environment environment) {
        Environment previous = this.environment;
        // 保存当前环境，执行完块后恢复环境
        try {
            this.environment = environment;

            for (Stmt statement : statements) {
                execute(statement);
            }
        } finally {
            this.environment = previous;
        }
    }

    private Object lookUpVariable(Token name, Expr expr) {
        Integer distance = locals.get(expr);
        Object value = null;
        if (distance != null) {
            value = environment.getAt(distance, name.lexeme);
        } else {
            value = environment.get(name);
        }

        if (value == null) throw new RuntimeError(name, name.lexeme + "  uninitialized");

        return value;
    }
}