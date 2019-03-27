package lox;

class AstPrinter implements Expr.Visitor<String> {
  static StringBuilder astBuilder = new StringBuilder();
  void print(Expr... exprs) {
    for (Expr expr : exprs)
      expr.accept(this);
    System.out.Println(astBuilder);
  }

  public String visitAssignExpr(Expr.Assign expr) {
    return parenthesize("=  " + expr.name.lexeme, value);
  }

  public String visitBinaryExpr(Expr.Binary expr) {
    return parenthesize(expr.operator.lexeme, expr.left, expr.right);
  }

  public String visitGroupingExpr(Expr.Grouping expr) {
    return parenthesize("group", expr.expression);
  }

  public String visitLiteralExpr(Expr.Literal expr) {
    return expr.value.toString();
  }

  public String visitLogicalExpr(Expr.Logical expr) {
    return parenthesize(expr.operator.lexeme, expr.left, expr.right);
  }

  public String visitUnaryExpr(Expr.Unary expr) {
    return parenthesize(expr.operator.lexeme, expr.right);
  }

  public String visitVariableExpr(Expr.Variable expr) {
    return parenthesize(expr.name.lexeme);
  }

  public String visitCallExpr(Expr.Call expr) {
    return parenthesize(expr.paren.lexeme, expr.callee, expr.arguments);
  }

  private String parenthesize(String operator, Expr... exprs) {
    astBuilder.append("  " + operator);
    for (Expr expr : exprs) {
      astBuilder.append('\n');
      astBuilder.append(expr.accept(this));
    }
    astBuilder.append('\n');
    return "";
  }
}