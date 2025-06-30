package com.oracle.truffle.bf;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.interop.ExceptionType;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

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
    @CompilerDirectives.TruffleBoundary
    SourceSection getSourceSection() {
        return source.createSection(source.getLineNumber(offset), source.getColumnNumber(offset), 1);
    }
}
