package com.oracle.truffle.brainfck;

import static com.oracle.truffle.api.HostCompilerDirectives.BytecodeInterpreterSwitch;
//import static com.oracle.truffle.brainfck.Bytecodes.DEC_DATA;
//import static com.oracle.truffle.brainfck.Bytecodes.DEC_PTR;
import static com.oracle.truffle.brainfck.Bytecodes.END;
import static com.oracle.truffle.brainfck.Bytecodes.IF_END;
//import static com.oracle.truffle.brainfck.Bytecodes.INC_DATA;
//import static com.oracle.truffle.brainfck.Bytecodes.INC_PTR;
import static com.oracle.truffle.brainfck.Bytecodes.LOOP_BEGIN;
import static com.oracle.truffle.brainfck.Bytecodes.LOOP_END;
//import static com.oracle.truffle.brainfck.Bytecodes.LOOP_MOVE_DATA;
//import static com.oracle.truffle.brainfck.Bytecodes.LOOP_MOVE_PTR;
import static com.oracle.truffle.brainfck.Bytecodes.MULT_ADD_AT_OFFSET;
import static com.oracle.truffle.brainfck.Bytecodes.MULT_ASSIGN_AT_OFFSET;
//import static com.oracle.truffle.brainfck.Bytecodes.NOP;
import static com.oracle.truffle.brainfck.Bytecodes.READ_IN_AT_OFFSET;
//import static com.oracle.truffle.brainfck.Bytecodes.READ_IN;
import static com.oracle.truffle.brainfck.Bytecodes.SET_DATA_AT_OFFSET;
//import static com.oracle.truffle.brainfck.Bytecodes.UPDATE_DATA;
import static com.oracle.truffle.brainfck.Bytecodes.UPDATE_DATA_AT_OFFSET;
import static com.oracle.truffle.brainfck.Bytecodes.UPDATE_PTR;
import static com.oracle.truffle.brainfck.Bytecodes.WRITE_OUT_AT_OFFSET;
//import static com.oracle.truffle.brainfck.Bytecodes.WRITE_OUT;
//import static com.oracle.truffle.brainfck.Bytecodes.ZERO_DATA;
import static com.oracle.truffle.brainfck.Bytecodes.lengthOf;
import static com.oracle.truffle.brainfck.Bytecodes.nameOf;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Stack;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.ExplodeLoop.LoopExplosionKind;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.PrimitiveValueProfile;
import com.oracle.truffle.brainfck.BrainfckLanguage.Options;
import com.oracle.truffle.object.DebugCounter;

public final class BytecodeNode extends RootNode {

    private final static Object NOTHING = new TruffleObject() {
    };
    private static final FrameDescriptor EMPTY_FRAME_DESCRIPTOR = new FrameDescriptor();
    // private static final DebugCounter[] HISTOGRAM;

    static {
//        HISTOGRAM = new DebugCounter[END + 1];
//        for (int i = 0; i < HISTOGRAM.length; ++i) {
//            HISTOGRAM[i] = DebugCounter.create(nameOf(i));
//        }
    }

    private final TruffleLanguage.Env env;
    @CompilationFinal(dimensions = 1) private final byte[] code;
    @CompilationFinal(dimensions = 1) private final int[] jumpTable;
    //@CompilationFinal(dimensions = 1) private final boolean[] stableLoop;
    @CompilationFinal(dimensions = 1) private final PrimitiveValueProfile[] ptrProfiles;

    private final BranchProfile eofProfile;
    private final int numberOfCells;
    private final Options.EOFMode eofMode;
    private final boolean bytecodeHistogram;

    public BytecodeNode(byte[] code, BrainfckLanguage language, TruffleLanguage.Env env) {
        super(language, EMPTY_FRAME_DESCRIPTOR);
        CompilerAsserts.neverPartOfCompilation();
        this.code = code;
        this.env = env;
        this.eofProfile = BranchProfile.create();
        this.numberOfCells = env.getOptions().get(Options.NumberOfCells);
        this.eofMode = env.getOptions().get(Options.EOF);
        this.bytecodeHistogram = env.getOptions().get(Options.Histogram);
        this.jumpTable = computeJumpTable(code);
        //this.stableLoop = computeStableLoops(code);
        this.ptrProfiles = new PrimitiveValueProfile[code.length];
        for (int i = 0; i < ptrProfiles.length; i++) {
            ptrProfiles[i] = PrimitiveValueProfile.create();
        }

    }

//    private static boolean[] computeStableLoops(byte[] code) {
//        boolean[] stableLoop = new boolean[code.length];
//
//        Stack<Integer> openBrackets = new Stack<>();
//        Stack<Integer> deltas = new Stack<>();
//        int delta = 0;
//        for (int bci = 0; bci < code.length; /* nop */) {
//            int opcode = code[bci];
//            if (opcode == LOOP_BEGIN) {
//                openBrackets.push(bci);
//                deltas.push(delta);
//            } else if (opcode == LOOP_END) {
//                int begin = openBrackets.pop();
//                int beginDelta = deltas.pop();
//                stableLoop[begin] = (delta == beginDelta);
//
//                // Found a non-stable loop => all enclosing loops are also non-stable.
//                if (!stableLoop[begin]) {
//                    for (int i = 0; i < deltas.size(); ++i) {
//                        deltas.set(i, Integer.MIN_VALUE);
//                    }
//                }
//            } else if (opcode == UPDATE_PTR) {
//                delta += code[bci + 1];
//            } else if (opcode == INC_PTR) {
//                ++delta;
//            } else if (opcode == DEC_PTR) {
//                --delta;
//            } else if (opcode == LOOP_MOVE_PTR) {
//                // Found a non-stable loop => all enclosing loops are also non-stable.
//                for (int i = 0; i < deltas.size(); ++i) {
//                    deltas.set(i, Integer.MIN_VALUE);
//                }
//            }
//            bci += lengthOf(opcode);
//        }
//        assert openBrackets.empty();
//        return stableLoop;
//    }

    private static int[] computeJumpTable(byte[] code) {
        int[] jumpTable = new int[code.length];

        Stack<Integer> openBrackets = new Stack<>();
        for (int bci = 0; bci < code.length; /* nop */) {
            int opcode = code[bci];
            if (opcode == LOOP_BEGIN) {
                openBrackets.push(bci);
            } else if (opcode == LOOP_END || opcode == IF_END) {
                int begin = openBrackets.pop();
                jumpTable[bci] = begin + 1;
                jumpTable[begin] = bci + 1;
            }
            bci += lengthOf(opcode);
        }

        assert openBrackets.empty();
        return jumpTable;
    }

    @TruffleBoundary
    private static void logElapsedTime(long fromNanos) {
        long elapsedNanos = System.nanoTime() - fromNanos;
        TruffleLogger.getLogger(BrainfckLanguage.ID).finest("Elapsed time: " + elapsedNanos / 1000_000 + "ms");
    }

    @Override
    public Object execute(VirtualFrame frame) {
        long startNanos = System.nanoTime();
        executeBody(0, 1000, new byte[numberOfCells]);
        logElapsedTime(startNanos);
        return NOTHING;
    }

    private static final class Pointer {
        int value;
        Pointer(int value) {
            this.value = value;
        }
    }

    @BytecodeInterpreterSwitch
    @ExplodeLoop(kind = LoopExplosionKind.MERGE_EXPLODE)
    private void executeBody(int bci0, int ptr0, byte[] data) {
        int bci = bci0;
        final Pointer ptr = new Pointer(ptr0);

        loop: while (Integer.compareUnsigned(bci, code.length) < 0) {
            int opcode = code[bci]; // stream.currentBC(bci);

            CompilerAsserts.partialEvaluationConstant(bci);
            CompilerAsserts.partialEvaluationConstant(opcode);
            CompilerDirectives.ensureVirtualized(ptr);

//            if (bytecodeHistogram) {
//                HISTOGRAM[opcode].inc();
//            }

            // @formatter:off
            switch (opcode) {

                // region Standard bytecodes

//                case NOP: /* ignore */ break;
//                case INC_PTR: ++ptr.value; break;
//                case DEC_PTR: --ptr.value; break;
//                case INC_DATA: ++data[ptr.value]; break;
//                case DEC_DATA: --data[ptr.value]; break;
//
//                case READ_IN: {
//                    int value;
//                    try {
//                        value = read();
//                    } catch (IOException ioe) {
//                        CompilerDirectives.transferToInterpreterAndInvalidate();
//                        throw BrainfckError.shouldNotReachHere(ioe);
//                    }
//                    if (value != -1) {
//                        data[ptr.value] = (byte) value;
//                    } else {
//                        eofProfile.enter();
//                        switch (eofMode) {
//                            case ZERO: data[ptr.value] = 0; break;
//                            case MINUS_ONE: data[ptr.value] = -1; break;
//                            case UNCHANGED: /* ignore */ break;
//                        }
//                    }
//                    break;
//                }
//
//                case WRITE_OUT:
//                    try {
//                        write(data[ptr.value]);
//                    } catch (IOException ioe) {
//                        CompilerDirectives.transferToInterpreterAndInvalidate();
//                        throw BrainfckError.shouldNotReachHere(ioe);
//                    }
//                    break;

                case LOOP_BEGIN:
                    if (data[ptr.value] == 0) { // GOTO (AFTER) LOOP_END
                        bci = jumpTable[bci];
                        continue loop;
                    }
                    break;

                case LOOP_END:
                    if (data[ptr.value] != 0) { // GOTO (AFTER) LOOP_BEGIN
                        bci = jumpTable[bci];
                        continue loop;
                    }
                    break;

                // endregion Standard bytecodes

                // region Extra bytecodes

                case UPDATE_PTR:
                    ptr.value += code[bci + 1];
                    bci += 2; // = stream.nextBCI(bci);
                    continue loop;

//                case UPDATE_DATA:
//                    data[ptr.value] += code[bci + 1];
//                    bci += 2; // = stream.nextBCI(bci);
//                    continue loop;
//
//                case ZERO_DATA:  data[ptr.value] = 0; break;
//
//                case LOOP_MOVE_PTR:
//                    if (data[ptr.value] != 0) {
//                        int ptrOffset = code[bci + 1];
//                        ptr.value += ptrOffset;
//                        while (data[ptr.value] != 0) {
//                            ptr.value += ptrOffset;
//                        }
//                    }
//                    bci += 2; // = stream.nextBCI(bci);
//                    continue loop;
//
//                case LOOP_MOVE_DATA: {
//                    if (data[ptr.value] != 0) {
//                        int ptrOffset = code[bci + 1];
//                        int dataMultiplier = code[bci + 2];
//                        data[ptr.value + ptrOffset] += dataMultiplier * data[ptr.value];
//                        data[ptr.value] = 0;
//                    }
//                    bci += 3; // = stream.nextBCI(bci);
//                    continue loop;
//                }

                case END: return ;

                // endregion Extra bytecodes

                // region Bytecodes with offset


                case UPDATE_DATA_AT_OFFSET: {
                    int offset = code[bci + 2];
                    byte delta = code[bci + 1];
                    data[ptr.value + offset] += delta;
                    bci += 3; // = stream.nextBCI(bci);
                    continue loop;
                }

                case SET_DATA_AT_OFFSET: {
                    int offset = code[bci + 2];
                    byte value = code[bci + 1];
                    data[ptr.value + offset] = value;
                    bci += 3; // = stream.nextBCI(bci);
                    continue loop;
                }

                case READ_IN_AT_OFFSET: {
                    int value;
                    try {
                        value = read();
                    } catch (IOException ioe) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        throw BrainfckError.shouldNotReachHere(ioe);
                    }
                    int offset = code[bci + 1];
                    if (value != -1) {
                        data[ptr.value + offset] = (byte) value;
                    } else {
                        eofProfile.enter();
                        switch (eofMode) {
                            case ZERO: data[ptr.value + offset] = 0; break;
                            case MINUS_ONE: data[ptr.value + offset] = -1; break;
                            case UNCHANGED: /* ignore */ break;
                        }
                    }
                    bci += 2;
                    continue loop;
                }

                case WRITE_OUT_AT_OFFSET:
                    try {
                        int offset = code[bci + 1];
                        write(data[ptr.value + offset]);
                    } catch (IOException ioe) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        throw BrainfckError.shouldNotReachHere(ioe);
                    }
                    bci += 2;
                    continue loop;

                case MULT_ADD_AT_OFFSET: {
                    int destOff = code[bci + 3];
                    int srcOff = code[bci + 2];
                    int multiplier = code[bci + 1];
                    data[ptr.value + destOff] += (byte) (data[ptr.value + srcOff] * multiplier);
                    bci += 4;
                    continue loop;
                }

                case MULT_ASSIGN_AT_OFFSET: {
                    int destOff = code[bci + 3];
                    int srcOff = code[bci + 2];
                    int multiplier = code[bci + 1];
                    data[ptr.value + destOff] = (byte) (data[ptr.value + srcOff] * multiplier);
                    bci += 4;
                    continue loop;
                }

                case IF_END:
                    ++bci;
                    continue loop;

                // endregion Bytecodes with offset

                default:
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw BrainfckError.shouldNotReachHere("unknown bytecode: " + opcode);
            }
            // @formatter:on
            ++bci; // = stream.nextBCI(bci);
        }
    }


    @TruffleBoundary
    private int read() throws IOException {
        return env.in().read();
    }

    @TruffleBoundary
    private void write(int b) throws IOException {
        env.out().write(b);
    }
}
