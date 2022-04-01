package com.oracle.truffle.brainfck;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.interop.ExceptionType;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

@ExportLibrary(InteropLibrary.class)
public final class BrainfckParseError extends AbstractTruffleException {

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
