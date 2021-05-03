package com.oracle.truffle.brainfck;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;

/**
 * A utility class that makes iterating over bytecodes and reading operands simpler and less error
 * prone.
 */
public final class BytecodeStream {

    @CompilationFinal(dimensions = 1) //
    private final byte[] code;

    /**
     * Creates a new {@code BytecodeStream} for the specified bytecode.
     *
     * @param code the array of bytes that contains the bytecode
     */
    public BytecodeStream(final byte[] code) {
        assert code != null;
        this.code = code;
    }

    /**
     * Gets the next bytecode index (no side-effects).
     *
     * @return the next bytecode index
     */
    public int nextBCI(int curBCI) {
        if (curBCI < code.length) {
            return curBCI + lengthOf(curBCI);
        } else {
            return curBCI;
        }
    }

    /**
     * Gets the bytecode index of the end of the code.
     *
     * @return the index of the end of the code
     */
    public int endBCI() {
        return code.length;
    }

    public int opcode(int curBCI) {
        if (curBCI < code.length) {
            // opcode validity is performed at verification time.
            return Bytes.beU1(code, curBCI);
        } else {
            return Bytecodes.END;
        }
    }

    /**
     * Gets the current opcode.
     *
     * @return the current opcode; {@link Bytecodes#END} if at or beyond the end of the code
     */
    public int currentBC(int curBCI) {
        return opcode(curBCI);
    }

    /**
     * Reads the offset argument for one of the update (pointer or data) instructions.
     *
     * @return the offset argument of the update (pointer or data) bytecode
     */
    public int readArgument(int curBCI) {
        return Bytes.beS2(code, curBCI + 1);
    }

    /**
     * Gets the length of the current bytecode.
     */
    private int lengthOf(int curBCI) {
        int length = Bytecodes.lengthOf(opcode(curBCI));
        return length;
    }
}
