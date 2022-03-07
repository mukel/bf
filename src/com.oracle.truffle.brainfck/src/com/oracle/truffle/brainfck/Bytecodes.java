package com.oracle.truffle.brainfck;

import static com.oracle.truffle.brainfck.Bytecodes.Flags.FALL_THROUGH;
import static com.oracle.truffle.brainfck.Bytecodes.Flags.STOP;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;

/**
 * Definitions of the Brainf*ck bytecodes.
 */
public final class Bytecodes {

    // @formatter:off
    // Standard operations
    public static final int NOP         = 0;
    public static final int INC_PTR     = 1;
    public static final int DEC_PTR     = 2;
    public static final int INC_DATA    = 3;
    public static final int DEC_DATA    = 4;
    public static final int WRITE_OUT   = 5;
    public static final int READ_IN     = 6;
    public static final int LOOP_BEGIN  = 7;
    public static final int LOOP_END    = 8;

    // Additional operations
    public static final int UPDATE_PTR  = 9;
    public static final int UPDATE_DATA = 10;
    public static final int ZERO_DATA   = 11;

    public static final int LOOP_MOVE_PTR  = 12;
    public static final int LOOP_MOVE_DATA = 13;

    public static final int END         = 14;
    // @formatter:on

    /**
     * An array that maps from a bytecode value to a {@link String} for the corresponding
     * instruction mnemonic.
     */
    @CompilationFinal(dimensions = 1) private static final String[] nameArray = new String[0xff];

    /**
     * An array that maps from a bytecode value to the set of {@link Flags} for the corresponding
     * instruction.
     */
    @CompilationFinal(dimensions = 1) private static final int[] flagsArray = new int[0xff];

    /**
     * An array that maps from a bytecode value to the length in bytes for the corresponding
     * instruction.
     */
    @CompilationFinal(dimensions = 1) private static final int[] lengthArray = new int[0xff];

    static class Flags {
        /**
         * Denotes an instruction that ends a basic block and does not let control flow fall through
         * to its lexical successor.
         */
        static final int STOP = 0x00000001;

        /**
         * Denotes an instruction that ends a basic block and may let control flow fall through to
         * its lexical successor. In practice this means it is a conditional branch.
         */
        static final int FALL_THROUGH = 0x00000002;
    }

    // @formatter:off
    static {
        def(NOP, "nop", "b", 0);
        def(INC_PTR, ">", "b", 0);
        def(DEC_PTR, "<", "b", 0);
        def(INC_DATA, "+", "b", 0);
        def(DEC_DATA, "-", "b", 0);
        def(WRITE_OUT, ".", "b", 0);
        def(READ_IN, ",", "b", 0);
        def(LOOP_BEGIN, "[", "b", FALL_THROUGH);
        def(LOOP_END, "]", "b", STOP);

        // Extra bytecodes produced by the optimization pass (--bf.Optimize).
        def(UPDATE_PTR, "p", "bj", 0);
        def(UPDATE_DATA, "d", "bj", 0);
        def(ZERO_DATA, "0", "b", 0);
        def(LOOP_MOVE_PTR, "P", "bj", 0);
        def(LOOP_MOVE_DATA, "D", "bji", 0);

        def(END, "end", "b", 0);
    }
    // @formatter:on

    /**
     * Defines a bytecode by entering it into the arrays that record its name, length and flags.
     *
     * @param name instruction name (lower case)
     * @param flags the set of {@link Flags} associated with the instruction
     */
    private static void def(int opcode, String name, String format, int flags) {
        assert nameArray[opcode] == null : "opcode " + opcode + " is already bound to name " + nameArray[opcode];
        nameArray[opcode] = name;
        Bytecodes.flagsArray[opcode] = flags;
        int instructionLength = format.length();
        lengthArray[opcode] = instructionLength;
    }

    /**
     * Gets the lower-case mnemonic for a given opcode.
     *
     * @param opcode an opcode
     * @return the mnemonic for {@code opcode} or {@code "<illegal opcode: " + opcode + ">"} if
     *         {@code opcode} is not a legal opcode
     */
    public static String nameOf(int opcode) throws IllegalArgumentException {
        String name = nameArray[opcode & 0xff];
        if (name == null) {
            return "<illegal opcode: " + opcode + ">";
        }
        return name;
    }

    /**
     * Gets the length of an instruction denoted by a given opcode.
     *
     * @param opcode an instruction opcode
     * @return the length of the instruction denoted by {@code opcode}. If {@code opcode} is an
     *         illegal instruction or denotes then 0 is returned.
     */
    public static int lengthOf(int opcode) {
        return lengthArray[opcode & 0xff];
    }

}
