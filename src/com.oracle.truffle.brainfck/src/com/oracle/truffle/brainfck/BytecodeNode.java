package com.oracle.truffle.brainfck;

import static com.oracle.truffle.brainfck.Bytecodes.DEC_DATA;
import static com.oracle.truffle.brainfck.Bytecodes.DEC_PTR;
import static com.oracle.truffle.brainfck.Bytecodes.INC_DATA;
import static com.oracle.truffle.brainfck.Bytecodes.INC_PTR;
import static com.oracle.truffle.brainfck.Bytecodes.LOOP_BEGIN;
import static com.oracle.truffle.brainfck.Bytecodes.LOOP_END;
import static com.oracle.truffle.brainfck.Bytecodes.READ_IN;
import static com.oracle.truffle.brainfck.Bytecodes.WRITE_OUT;

import java.io.IOException;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.ExplodeLoop.LoopExplosionKind;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.object.DebugCounter;

public class BytecodeNode extends RootNode {

    private final TruffleLanguage.Env env;

    private static final DebugCounter[] HISTOGRAM;
    static {
        HISTOGRAM = new DebugCounter[Bytecodes.END];
        for (int i = 0; i < HISTOGRAM.length; ++i) {
            HISTOGRAM[i] = DebugCounter.create(Bytecodes.nameOf(i));
        }
    }

    @CompilationFinal(dimensions = 1) private final byte[] code;
    private final BranchProfile eofProfile;

    private final int numberOfCells;

    public BytecodeNode(byte[] code, BrainfckLanguage language, TruffleLanguage.Env env) {
        super(language, new FrameDescriptor());
        this.code = code;
        this.env = env;
        this.eofProfile = BranchProfile.create();
        this.numberOfCells = env.getOptions().get(BrainfckLanguage.Options.NumberOfCells);
    }

    @ExplodeLoop(kind = LoopExplosionKind.MERGE_EXPLODE)
    @Override
    public Object execute(VirtualFrame frame) {
        long ticks = System.currentTimeMillis();

        int bci = 0;
        final BytecodeStream stream = new BytecodeStream(code);

        int ptr = 0;
        final byte[] data = new byte[numberOfCells];

        loop: while (bci < stream.endBCI()) {
            int opcode = stream.currentBC(bci);

            // HISTOGRAM[opcode].inc();

            // CompilerAsserts.partialEvaluationConstant(bci);
            // CompilerAsserts.partialEvaluationConstant(opcode);

            // @formatter:off
            switch (opcode) {
                case INC_PTR: ++ptr; break;
                case DEC_PTR: --ptr; break;
                case INC_DATA: ++data[ptr]; break;
                case DEC_DATA: --data[ptr]; break;
                case READ_IN:
                    int value;
                    try {
                       value = env.in().read();
                    } catch (IOException ioe) {
                        CompilerDirectives.transferToInterpreter();
                        throw BrainfckError.shouldNotReachHere(ioe);
                    }
                    if (value != -1) {
                        data[ptr] = (byte) value;
                    } else {
                        eofProfile.enter();
                        switch (env.getOptions().get(BrainfckLanguage.Options.EOF)) {
                            case ZERO: data[ptr] = 0; break;
                            case MINUS_ONE: data[ptr] = -1; break;
                            case UNCHANGED: /* ignore */ break;
                        }
                    }
                    break;
                case WRITE_OUT:
                    try {
                        env.out().write(data[ptr]);
                    } catch (IOException ioe) {
                        CompilerDirectives.transferToInterpreter();
                        throw BrainfckError.shouldNotReachHere(ioe);
                    }
                    break;
                case LOOP_BEGIN:
                    if (data[ptr] == 0) { // GOTO (AFTER) LOOP_END
                        int level = 0;
                        int target = bci;
                        while (true) {
                            int oc = stream.currentBC(target);
                            if (oc == LOOP_BEGIN) {
                                ++level;
                            } else if (oc == LOOP_END) {
                                --level;
                            }
                            target++; // = stream.nextBCI(target);
                            if (level == 0) {
                                break ;
                            }
                        }
                        bci = target;
                        continue loop;
                    }
                    break;
                case LOOP_END: // GOTO LOOP_BEGIN
                    int level = 0;
                    int target = bci;
                    while (true) {
                        int oc = stream.currentBC(target);
                        if (oc == LOOP_BEGIN) {
                            --level;
                        } else if (oc == LOOP_END) {
                            ++level;
                        }
                        if (level == 0) {
                            break ;
                        }
                        --target; // assumes all bytecodes are 1-byte
                    }
                    bci = target;
                    continue loop;
                default:
                    CompilerDirectives.transferToInterpreter();
                    throw BrainfckError.shouldNotReachHere("unknown bytecode: " + opcode);
            }
            // @formatter:on
            bci++; // = stream.nextBCI(bci);
        }

        TruffleLogger.getLogger(BrainfckLanguage.ID).finest(() -> "Elapsed time: " + (System.currentTimeMillis() - ticks) + "ms");

        return Nothing.INSTANCE; // cannot return null
    }

    @ExportLibrary(InteropLibrary.class)
    static final class Nothing implements TruffleObject {
        public final static Nothing INSTANCE = new Nothing();

        private Nothing() {
            // empty
        }
    }
}
