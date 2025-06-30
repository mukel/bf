package com.oracle.truffle.bf;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.bytecode.*;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;

import java.io.IOException;

@GenerateBytecode(languageClass = BFLanguage.class)
public abstract class BytecodeInterpreter extends RootNode implements BytecodeRootNode {

    protected BytecodeInterpreter(BFLanguage language, FrameDescriptor frameDescriptor) {
        super(language, frameDescriptor);
    }

    @Operation
    public static final class Add {
        @Specialization
        public static int doInts(int a, int b) {
            return a + b;
        }
    }

    @Operation
    public static final class UpdateData {
        @Specialization
        public static int doInts(byte[] data, int ptr, int delta) {
            return data[ptr] += delta;
        }
    }

    @Operation
    public static final class ReadData {

        @TruffleBoundary
        private static int read(TruffleLanguage.Env env) {
            try {
                return env.in().read();
            } catch (IOException ioe) {
                throw new RuntimeException(ioe);
            }
        }

        @Specialization
        public static void doInts(byte[] data, int ptr, @Bind BytecodeNode bytecodeNode) {
            TruffleLanguage.Env env = BFLanguage.CONTEXT_REFERENCE.get(bytecodeNode);
            data[ptr] = (byte) read(env);
        }
    }

    @Operation
    public static final class WriteData {
        @TruffleBoundary
        private static void write(TruffleLanguage.Env env, int b) {
            try {
                env.out().write(b);
            } catch (IOException ioe) {
                throw new RuntimeException(ioe);
            }
        }

        @Specialization
        public static void doInts(byte[] data, int ptr, @Bind BytecodeNode bytecodeNode) {
            TruffleLanguage.Env env = BFLanguage.CONTEXT_REFERENCE.get(bytecodeNode);
            write(env, data[ptr]);
        }
    }

    @Operation
    public static final class DataIsNonZero {
        @Specialization
        public static boolean doInts(byte[] data, int ptr) {
            return data[ptr] != 0;
        }
    }

    @Operation
    public static final class NewData {

        @TruffleBoundary
        private static int getNumberOfCells(TruffleLanguage.Env env) {
            return env.getOptions().get(BFLanguage.NumberOfCells);
        }

        @Specialization
        public static byte[] doInts(@Bind BytecodeNode bytecodeNode) {
            TruffleLanguage.Env env = BFLanguage.CONTEXT_REFERENCE.get(bytecodeNode);
            return new byte[getNumberOfCells(env)];
        }
    }

    static CallTarget parseBytecode(Source source, BFLanguage language) {
        var rootNodes = BytecodeInterpreterGen.create(language, BytecodeConfig.DEFAULT, b -> {

            Object[] parseResult = BFLanguage.parse(source);

            final char[] code = (char[]) parseResult[0];
            final int[] sideTable = (int[]) parseResult[1];

            b.beginRoot();

            BytecodeLocal ptr = b.createLocal("ptr", null);
            b.beginStoreLocal(ptr);
            b.emitLoadConstant(0);
            b.endStoreLocal();

            BytecodeLocal data = b.createLocal("data", null);
            b.beginStoreLocal(data);
                b.emitNewData(); // cannot use loadConstant(new byte[3000]) here
            b.endStoreLocal();

            for (int bci = 0; bci < code.length; ) {
                // @formatter:off
                int arg = sideTable[bci];
                switch (code[bci]) {
                    case '>': {
                        // ptr += arg
                        b.beginStoreLocal(ptr);
                            b.beginAdd();
                                b.emitLoadLocal(ptr);
                                b.emitLoadConstant(arg);
                            b.endAdd();
                        b.endStoreLocal();
                        bci += arg;
                        continue ;
                    }
                    case '<': {
                        // ptr -= arg
                        b.beginStoreLocal(ptr);
                            b.beginAdd();
                                b.emitLoadLocal(ptr);
                                b.emitLoadConstant(-arg);
                            b.endAdd();
                        b.endStoreLocal();
                        bci += arg;
                        continue ;
                    }

                    case '+': {
                        // data[ptr] += arg
                        b.beginUpdateData();
                            b.emitLoadLocal(data);
                            b.emitLoadLocal(ptr);
                            b.emitLoadConstant(+arg);
                        b.endUpdateData();
                        bci += arg;
                        continue ;
                    }
                    case '-': {
                        // data[ptr] -= arg
                        b.beginUpdateData();
                            b.emitLoadLocal(data);
                            b.emitLoadLocal(ptr);
                            b.emitLoadConstant(-arg);
                        b.endUpdateData();
                        bci += arg;
                        continue ;
                    }
                    case ',': {
                        // data[ptr] = read()
                        b.beginReadData();
                            b.emitLoadLocal(data);
                            b.emitLoadLocal(ptr);
                        b.endReadData();
                    }
                    break;
                    case '.': {
                        // write(data[ptr])
                        b.beginWriteData();
                            b.emitLoadLocal(data);
                            b.emitLoadLocal(ptr);
                        b.endWriteData();
                    }
                    break;
                    case '[': {
                        // while (data[ptr] != 0) {
                        b.beginWhile();
                            // condition
                            b.beginDataIsNonZero();
                                b.emitLoadLocal(data);
                                b.emitLoadLocal(ptr);
                            b.endDataIsNonZero();

                            b.beginBlock();
                    }
                    break;
                    case ']':
                        // }
                        b.endBlock();
                        b.endWhile();
                        break;
                }

                // @formatter:on
                ++bci;
            }

            // dummy return value, what else can I return here?
            b.beginReturn();
                b.emitLoadConstant(0);
            b.endReturn();

            b.endRoot();
        });

        BytecodeInterpreter rootNode = rootNodes.getNode(0);
        return rootNode.getCallTarget();
    }

}
