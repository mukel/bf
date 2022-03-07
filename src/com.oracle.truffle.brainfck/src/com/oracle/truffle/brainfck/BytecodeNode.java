package com.oracle.truffle.brainfck;

import static com.oracle.truffle.api.HostCompilerDirectives.BytecodeInterpreterSwitch;
import static com.oracle.truffle.brainfck.Bytecodes.DEC_DATA;
import static com.oracle.truffle.brainfck.Bytecodes.DEC_PTR;
import static com.oracle.truffle.brainfck.Bytecodes.END;
import static com.oracle.truffle.brainfck.Bytecodes.INC_DATA;
import static com.oracle.truffle.brainfck.Bytecodes.INC_PTR;
import static com.oracle.truffle.brainfck.Bytecodes.LOOP_BEGIN;
import static com.oracle.truffle.brainfck.Bytecodes.LOOP_END;
import static com.oracle.truffle.brainfck.Bytecodes.LOOP_MOVE_DATA;
import static com.oracle.truffle.brainfck.Bytecodes.LOOP_MOVE_PTR;
import static com.oracle.truffle.brainfck.Bytecodes.NOP;
import static com.oracle.truffle.brainfck.Bytecodes.READ_IN;
import static com.oracle.truffle.brainfck.Bytecodes.UPDATE_DATA;
import static com.oracle.truffle.brainfck.Bytecodes.UPDATE_PTR;
import static com.oracle.truffle.brainfck.Bytecodes.WRITE_OUT;
import static com.oracle.truffle.brainfck.Bytecodes.ZERO_DATA;
import static com.oracle.truffle.brainfck.Bytecodes.lengthOf;
import static com.oracle.truffle.brainfck.Bytecodes.nameOf;

import java.io.IOException;
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
import com.oracle.truffle.brainfck.BrainfckLanguage.Options;
import com.oracle.truffle.object.DebugCounter;

public final class BytecodeNode extends RootNode {

    private final static Object NOTHING = new TruffleObject() {
    };
    private static final FrameDescriptor EMPTY_FRAME_DESCRIPTOR = new FrameDescriptor();
    private static final DebugCounter[] HISTOGRAM;

    static {
        HISTOGRAM = new DebugCounter[END + 1];
        for (int i = 0; i < HISTOGRAM.length; ++i) {
            HISTOGRAM[i] = DebugCounter.create(nameOf(i));
        }
    }

    private final TruffleLanguage.Env env;
    @CompilationFinal(dimensions = 1) private final byte[] code;
    @CompilationFinal(dimensions = 1) private final int[] jumpTable;

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
    }

    private static int[] computeJumpTable(byte[] code) {
        int[] jumpTable = new int[code.length];

        Stack<Integer> openBrackets = new Stack<>();
        for (int bci = 0; bci < code.length; /* nop */) {
            int opcode = code[bci];
            if (opcode == LOOP_BEGIN) {
                openBrackets.push(bci);
            } else if (opcode == LOOP_END) {
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
        long elapsedNanos=System.nanoTime()-fromNanos;TruffleLogger.getLogger(BrainfckLanguage.ID).finest("Elapsed time: "+elapsedNanos/1000_000+"ms");
    }

    @Override
    public Object execute(VirtualFrame frame) {
        long startNanos = System.nanoTime();
        executeBody(0, 0, new byte[numberOfCells]);
        logElapsedTime(startNanos);
        return NOTHING;
    }

    @BytecodeInterpreterSwitch
    @ExplodeLoop(kind = LoopExplosionKind.MERGE_EXPLODE)
    private void executeBody(int bci0, int ptr0, byte[] data) {
        int bci = bci0;
        final int[] ptr = new int[]{ptr0};

        loop: while (Integer.compareUnsigned(bci, code.length) < 0) {
            int opcode = code[bci]; // stream.currentBC(bci);

            CompilerAsserts.partialEvaluationConstant(bci);
            CompilerAsserts.partialEvaluationConstant(opcode);
            CompilerDirectives.ensureVirtualized(ptr);

            if (bytecodeHistogram) {
                HISTOGRAM[opcode].inc();
            }

            // @formatter:off
            switch (opcode) {

                // region Standard bytecodes

                case NOP: /* ignore */ break;
                case INC_PTR: ++ptr[0]; break;
                case DEC_PTR: --ptr[0]; break;
                case INC_DATA: ++data[ptr[0]]; break;
                case DEC_DATA: --data[ptr[0]]; break;

                case READ_IN:
                    int value;
                    try {
                        value = read();
                    } catch (IOException ioe) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        throw BrainfckError.shouldNotReachHere(ioe);
                    }
                    if (value != -1) {
                        data[ptr[0]] = (byte) value;
                    } else {
                        eofProfile.enter();
                        switch (eofMode) {
                            case ZERO: data[ptr[0]] = 0; break;
                            case MINUS_ONE: data[ptr[0]] = -1; break;
                            case UNCHANGED: /* ignore */ break;
                        }
                    }
                    break;

                case WRITE_OUT:
                    try {
                        write(data[ptr[0]]);
                    } catch (IOException ioe) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        throw BrainfckError.shouldNotReachHere(ioe);
                    }
                    break;

                case LOOP_BEGIN:
                    if (data[ptr[0]] == 0) { // GOTO (AFTER) LOOP_END
                        bci = jumpTable[bci];
                        continue loop;
                    }
                    break;

                case LOOP_END:
                    if (data[ptr[0]] != 0) { // GOTO (AFTER) LOOP_BEGIN
                        bci = jumpTable[bci];
                        continue loop;
                    }
                    break;

                // endregion Standard bytecodes

                // region Extra bytecodes

                case UPDATE_PTR:
                    ptr[0] += code[bci + 1];
                    bci += 2; // = stream.nextBCI(bci);
                    continue loop;

                case UPDATE_DATA:
                    data[ptr[0]] += code[bci + 1];
                    bci += 2; // = stream.nextBCI(bci);
                    continue loop;

                case ZERO_DATA:  data[ptr[0]] = 0; break;

                case LOOP_MOVE_PTR:
                    if (data[ptr[0]] != 0) {
                        int ptrOffset = code[bci + 1];
                        ptr[0] += ptrOffset;
                        while (data[ptr[0]] != 0) {
                            ptr[0] += ptrOffset;
                        }
                    }
                    bci += 2; // = stream.nextBCI(bci);
                    continue loop;

                case LOOP_MOVE_DATA: {
                    if (data[ptr[0]] != 0) {
                        int ptrOffset = code[bci + 1];
                        int dataMultiplier = code[bci + 2];
                        data[ptr[0] + ptrOffset] += dataMultiplier * data[ptr[0]];
                        data[ptr[0]] = 0;
                    }
                    bci += 3; // = stream.nextBCI(bci);
                    continue loop;
                }

                case END: return ;

                // endregion Extra bytecodes

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
