package com.oracle.truffle.brainfck;

import java.util.ArrayList;
import java.util.Locale;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.HostCompilerDirectives.BytecodeInterpreterSwitchBoundary;

/**
 * Indicates a condition in Brainfck related code that should never occur during normal operation.
 */
public final class BrainfckError extends Error {

    @BytecodeInterpreterSwitchBoundary
    public static RuntimeException unimplemented() {
        CompilerAsserts.neverPartOfCompilation();
        throw new BrainfckError("unimplemented");
    }

    @BytecodeInterpreterSwitchBoundary
    public static RuntimeException shouldNotReachHere() {
        CompilerAsserts.neverPartOfCompilation();
        throw new BrainfckError("should not reach here");
    }

    @BytecodeInterpreterSwitchBoundary
    public static RuntimeException shouldNotReachHere(String message) {
        CompilerAsserts.neverPartOfCompilation();
        throw new BrainfckError("should not reach here: %s", message);
    }

    @BytecodeInterpreterSwitchBoundary
    public static RuntimeException shouldNotReachHere(Throwable cause) {
        CompilerAsserts.neverPartOfCompilation();
        throw new BrainfckError(cause);
    }

    /**
     * Checks a given condition and throws a {@link BrainfckError} if it is false. Guarantees are
     * stronger than assertions in that they are always checked. Error messages for guarantee
     * violations should clearly indicate the nature of the problem as well as a suggested solution
     * if possible.
     *
     * @param condition the condition to check
     * @param message the message that will be associated with the error, in
     *            {@link String#format(String, Object...)} syntax
     * @param args arguments to the format string
     */
    @BytecodeInterpreterSwitchBoundary
    public static void guarantee(boolean condition, String message, Object... args) {
        if (!condition) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new BrainfckError("failed guarantee: " + message, args);
        }
    }

    /**
     * This constructor creates a {@link BrainfckError} with a given message.
     *
     * @param msg the message that will be associated with the error
     */
    private BrainfckError(String msg) {
        super(msg);
    }

    /**
     * This constructor creates a {@link BrainfckError} with a message assembled via
     * {@link String#format(String, Object...)}. It always uses the ENGLISH locale in order to
     * always generate the same output.
     *
     * @param msg the message that will be associated with the error, in String.format syntax
     * @param args parameters to String.format - parameters that implement {@link Iterable} will be
     *            expanded into a [x, x, ...] representation.
     */
    private BrainfckError(String msg, Object... args) {
        super(format(msg, args));
    }

    /**
     * This constructor creates a {@link BrainfckError} for a given causing Throwable instance.
     *
     * @param cause the original exception that contains additional information on this error
     */
    public BrainfckError(Throwable cause) {
        super(cause);
    }

    private static String format(String msg, Object... args) {
        if (args != null) {
            // expand Iterable parameters into a list representation
            for (int i = 0; i < args.length; i++) {
                if (args[i] instanceof Iterable<?>) {
                    ArrayList<Object> list = new ArrayList<>();
                    for (Object o : (Iterable<?>) args[i]) {
                        list.add(o);
                    }
                    args[i] = list.toString();
                }
            }
        }
        return String.format(Locale.ENGLISH, msg, args);
    }
}
