package com.oracle.truffle.bf;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;

import org.graalvm.options.OptionCategory;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionKey;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Option;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ExceptionType;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

@TruffleLanguage.Registration(id = "bf", name = "Brainf*ck", version = "0.1", characterMimeTypes = "application/x-bf", contextPolicy = TruffleLanguage.ContextPolicy.SHARED)
public final class BFLanguage extends TruffleLanguage<TruffleLanguage.Env> {

    @Option(help = "Number of memory cells available (default: 30000).", usageSyntax = "[0..inf)", category = OptionCategory.USER) //
    public static final OptionKey<Integer> NumberOfCells = new OptionKey<>(30000);

    @Override
    protected OptionDescriptors getOptionDescriptors() {
        return new BFLanguageOptionDescriptors();
    }

    private static final ContextReference<TruffleLanguage.Env> CONTEXT_REFERENCE = ContextReference.create(BFLanguage.class);

    @Override
    protected Env createContext(TruffleLanguage.Env env) {
        return env;
    }

    private static final FrameDescriptor EMPTY_FRAME_DESCRIPTOR = new FrameDescriptor();

    static Object[] parse(Source source) {
        CharSequence chars = source.getCharacters();
        char[] bc = new char[chars.length()];
        int[] sideTable = new int[chars.length()];
        int[] stack = new int[chars.length()];
        int top = 0;
        int bci = 0;
        int prevIndex = -1;
        for (int i = 0; i < chars.length(); ++i) {
            char c = chars.charAt(i);
            if ("<>-+,.[]".indexOf(c) < 0) {
                continue; // skip
            }
            bc[bci++] = c;
            if (prevIndex < 0 || c != bc[prevIndex]) {
                prevIndex = bci - 1;
            }
            if (c == '[') {
                stack[top++] = bci;
            } else if (c == ']') {
                if (top == 0) {
                    throw new ParseError(source, i, "Unbalanced brackets");
                }
                int begin = stack[--top];
                sideTable[bci - 1] = begin;
                sideTable[begin - 1] = bci;
            } else {
                // <>+-
                sideTable[prevIndex]++;
            }
        }
        if (top != 0) {
            throw new ParseError(source, chars.length() - 1, "Unbalanced brackets");
        }
        return new Object[]{Arrays.copyOf(bc, bci), Arrays.copyOf(sideTable, bci)};
    }

    @Override
    protected CallTarget parse(final ParsingRequest request) throws Exception {
        Object[] parseResult = parse(request.getSource());
        RootNode rootNode = new RootNode(BFLanguage.this, EMPTY_FRAME_DESCRIPTOR) {
            @CompilationFinal(dimensions = 1) private final char[] code = (char[]) parseResult[0];
            @CompilationFinal(dimensions = 1) private final int[] sideTable = (int[]) parseResult[1];

            @Override
            public Object execute(VirtualFrame frame) {
                long startNanos = System.nanoTime();
                try {
                    executeBody(new byte[getNumberOfCells(getEnv())]);
                } catch (IOException ioe) {
                    throw throwUncheckedIOException(ioe);
                } finally {
                    logElapsed(startNanos);
                }
                return 0; // not null
            }

            @TruffleBoundary
            private RuntimeException throwUncheckedIOException(IOException ioe) {
                throw new UncheckedIOException(ioe);
            }

            @TruffleBoundary
            private void logElapsed(long startNanos) {
                long elapsedNanos = System.nanoTime() - startNanos;
                TruffleLogger.getLogger("bf").fine("Elapsed time: " + elapsedNanos / 1000_000 + "ms");
            }

            @TruffleBoundary
            private int getNumberOfCells(TruffleLanguage.Env env) {
                return env.getOptions().get(BFLanguage.NumberOfCells);
            }

            TruffleLanguage.Env getEnv() {
                return CONTEXT_REFERENCE.get(this);
            }

            @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.MERGE_EXPLODE)
            private void executeBody(byte[] data) throws IOException {
                int bci = 0;
                final int[] ptr = new int[]{0};
                while (Integer.compareUnsigned(bci, code.length) < 0) {
                    CompilerAsserts.partialEvaluationConstant(bci);
                    CompilerDirectives.ensureVirtualized(ptr);
                    // @formatter:off
                    int arg = sideTable[bci];
                    switch (code[bci]) {
                        case '>': ptr[0] += arg; bci += arg; continue; // ++ptr[0]; break;
                        case '<': ptr[0] -= arg; bci += arg; continue; // --ptr[0]; break;
                        case '+': data[ptr[0]] += arg; bci += arg; continue; // ++data[ptr[0]]; break;
                        case '-': data[ptr[0]] -= arg; bci += arg; continue; // --data[ptr[0]]; break;
                        case ',': data[ptr[0]] = (byte) read(getEnv()); break;
                        case '.': write(getEnv(), data[ptr[0]]); break;
                        case '[': bci = (data[ptr[0]] == 0) ? arg : bci + 1; continue;
                        case ']': bci = (data[ptr[0]] != 0) ? arg : bci + 1; continue ;
                    }
                    // @formatter:on
                    ++bci;
                }
            }

            @TruffleBoundary
            private int read(TruffleLanguage.Env env) throws IOException {
                return env.in().read();
            }

            @TruffleBoundary
            private void write(TruffleLanguage.Env env, int b) throws IOException {
                env.out().write(b);
            }
        };
        return rootNode.getCallTarget();
    }
}

@ExportLibrary(InteropLibrary.class)
final class ParseError extends AbstractTruffleException {

    private final Source source;
    private final int offset;

    public ParseError(Source source, int offset, String message) {
        super(String.format("%s (at line %d, column %d): %s", source.getName(), source.getLineNumber(offset), source.getColumnNumber(offset), message));
        this.source = source;
        this.offset = offset;
    }

    @ExportMessage
    ExceptionType getExceptionType() {
        return ExceptionType.PARSE_ERROR;
    }

    @ExportMessage
    boolean hasSourceLocation() {
        return true;
    }

    @ExportMessage(name = "getSourceLocation")
    @TruffleBoundary
    SourceSection getSourceSection() {
        return source.createSection(source.getLineNumber(offset), source.getColumnNumber(offset), 1);
    }
}
