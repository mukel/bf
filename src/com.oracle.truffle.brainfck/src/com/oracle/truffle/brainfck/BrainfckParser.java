package com.oracle.truffle.brainfck;

import java.io.ByteArrayOutputStream;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.interop.ExceptionType;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

public final class BrainfckParser {

    @ExportLibrary(InteropLibrary.class)
    public static class BrainfckParseError extends AbstractTruffleException {

        private final Source source;
        private final int offset;

        public BrainfckParseError(Source source, int offset, String message) {
            super(niceMessage(source, offset, message));
            this.source = source;
            this.offset = offset;
        }

        private static String niceMessage(Source source, int offset, String message) {
            if (source == null) {
                return message;
            }
            return String.format("%s (at line %d, column %d): %s", source.getName(), source.getLineNumber(offset), source.getColumnNumber(offset), message);
        }

        @ExportMessage
        ExceptionType getExceptionType() {
            return ExceptionType.PARSE_ERROR;
        }

        @ExportMessage
        boolean hasSourceLocation() {
            return source != null;
        }

        @ExportMessage(name = "getSourceLocation")
        @TruffleBoundary
        SourceSection getSourceSection() throws UnsupportedMessageException {
            if (source == null) {
                throw UnsupportedMessageException.create();
            }
            return source.createSection(source.getLineNumber(offset), source.getColumnNumber(offset), 1);
        }
    }

    public static byte[] parse(Source source) {
        assert source.hasCharacters();

        CharSequence chars = source.getCharacters();
        ByteArrayOutputStream baos = new ByteArrayOutputStream(chars.length());

        int nestingLevel = 0;
        for (int i = 0; i < chars.length(); ++i) {
            char c = chars.charAt(i);
            // @formatter:off
            switch (c) {
                case '<': baos.write(Bytecodes.DEC_PTR); break;
                case '>': baos.write(Bytecodes.INC_PTR); break;
                case '-': baos.write(Bytecodes.DEC_DATA); break;
                case '+': baos.write(Bytecodes.INC_DATA); break;
                case ',': baos.write(Bytecodes.READ_IN); break;
                case '.': baos.write(Bytecodes.WRITE_OUT); break;
                case '[': baos.write(Bytecodes.LOOP_BEGIN); ++nestingLevel; break;
                case ']': baos.write(Bytecodes.LOOP_END);   --nestingLevel; break;
                default : // ignore
            }
            // @formatter:on
            if (nestingLevel < 0) {
                throw new BrainfckParseError(source, i, "Unbalanced brackets");
            }
        }

        if (nestingLevel != 0) {
            throw new BrainfckParseError(source, chars.length() - 1, "Unbalanced brackets");
        }

        return baos.toByteArray();
    }
}
