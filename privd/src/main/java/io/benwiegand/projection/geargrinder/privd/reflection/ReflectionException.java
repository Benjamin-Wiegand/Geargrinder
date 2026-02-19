package io.benwiegand.projection.geargrinder.privd.reflection;

/**
 * general exception for reflection-related problems.
 */
public class ReflectionException extends Exception {

    public ReflectionException(String message, Throwable cause) {
        super(message, cause);
    }

    public ReflectionException(Throwable cause) {
        super(cause);
    }

    public ReflectionException(String message) {
        super(message);
    }
}
