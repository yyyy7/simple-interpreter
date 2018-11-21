package lox;

/**
 * RuntimeError
 */
public class RuntimeError extends RuntimeException {
    //static final long serialVersionUID;

    final Token token;

    RuntimeError(Token token, String message) {
        super(message);
        this.token = token;
    }
}