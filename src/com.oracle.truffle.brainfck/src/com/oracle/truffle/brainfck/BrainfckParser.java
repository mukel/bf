package com.oracle.truffle.brainfck;

import static com.oracle.truffle.brainfck.Bytecodes.DEC_DATA;
import static com.oracle.truffle.brainfck.Bytecodes.DEC_PTR;
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

import java.io.ByteArrayOutputStream;

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

    public static byte[] parse(Source source, boolean optimize) {
        assert source.hasCharacters();

        CharSequence chars = source.getCharacters();
        ByteArrayOutputStream baos = new ByteArrayOutputStream(chars.length());

        int nestingLevel = 0;
        int maxNesting = 0;
        for (int i = 0; i < chars.length(); ++i) {
            char c = chars.charAt(i);
            // @formatter:off
            switch (c) {
                case '<': baos.write(DEC_PTR); break;
                case '>': baos.write(INC_PTR); break;
                case '-': baos.write(DEC_DATA); break;
                case '+': baos.write(INC_DATA); break;
                case ',': baos.write(READ_IN); break;
                case '.': baos.write(WRITE_OUT); break;
                case '[': baos.write(LOOP_BEGIN); ++nestingLevel; break;
                case ']': baos.write(LOOP_END);   --nestingLevel; break;
                default : // ignore
            }
            // @formatter:on
            if (nestingLevel > maxNesting) {
                maxNesting = nestingLevel;
            }
            if (nestingLevel < 0) {
                throw new BrainfckParseError(source, i, "Unbalanced brackets");
            }
        }

        TruffleLogger.getLogger(BrainfckLanguage.ID, "parser").finest("Maximum [brackets] nesting depth: " + maxNesting);

        if (nestingLevel != 0) {
            throw new BrainfckParseError(source, chars.length() - 1, "Unbalanced brackets");
        }

        return optimize
                ? rleOptimize(baos.toByteArray())
                : baos.toByteArray();
    }

    private static boolean fitsInShort(int value) {
        return Short.MIN_VALUE <= value && value <= Short.MAX_VALUE;
    }

    static byte[] rleOptimize(byte[] code) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(code.length);
        for (int i = 0; i < code.length; ++i) {
            byte c = code[i];
            // @formatter:off
            switch (c) {
                case NOP: /* ignore  */
                    break;
                case DEC_PTR: // fall-through
                case INC_PTR: {
                    int delta = 0;
                    while (i < code.length) {
                        if (code[i] == INC_PTR) {
                            ++delta;
                        } else if (code[i] == DEC_PTR) {
                            --delta;
                        } else {
                            break;
                        }
                        ++i;
                    }
                    if (delta != 0) {
                        baos.write(UPDATE_PTR);
                        BrainfckError.guarantee(fitsInShort(delta), "(Pointer) update offset must fit in a short: " + delta);
                        baos.write(delta & 0xff);
                        baos.write(delta >> 8);
                    }
                    --i;
                    break;
                }
                case DEC_DATA: // fall-through
                case INC_DATA: {
                    int delta = 0;
                    while (i < code.length) {
                        if (code[i] == INC_DATA) {
                            ++delta;
                        } else if (code[i] == DEC_DATA) {
                            --delta;
                        } else {
                            break;
                        }
                        ++i;
                    }
                    if (delta != 0) {
                        baos.write(UPDATE_DATA);
                        BrainfckError.guarantee(fitsInShort(delta), "(Data) update offset must fit in a short: " + delta);
                        baos.write(delta & 0xFF);
                        baos.write(delta >> 8);
                    }
                    --i;
                    break;
                }
                case LOOP_BEGIN : {
                    // [-] and [+]
                    if (c == LOOP_BEGIN && i + 2 < code.length && (code[i + 1] == DEC_DATA || code[i + 1] == INC_DATA) && code[i + 2] == LOOP_END) {
                        i += 2;
                        baos.write(ZERO_DATA);
                        break;
                    } else {
                        int delta = 0;
                        int j = i + 1;
                        while (j < code.length && (code[j] == DEC_PTR || code[j] == INC_PTR)) {
                            if (code[j] == DEC_PTR) {
                                --delta;
                            } else {
                                ++delta;
                            }
                            ++j;
                        }
                        if (j < code.length && code[j] == LOOP_END && delta != 0) {
                            baos.write(LOOP_MOVE_PTR);
                            BrainfckError.guarantee(fitsInShort(delta), "(Pointer) update offset must fit in a short: " + delta);
                            baos.write(delta & 0xFF);
                            baos.write(delta >> 8);
                            i = j;
                            break;
                        }

                        int deltaDataL = 0;
                        j = i + 1;
                        while (j < code.length && (code[j] == DEC_DATA || code[j] == INC_DATA)) {
                            if (code[j] == DEC_DATA) {
                                --deltaDataL;
                            } else {
                                ++deltaDataL;
                            }
                            ++j;
                        }

                        int deltaPtrL = 0;
                        while (j < code.length && (code[j] == DEC_PTR || code[j] == INC_PTR)) {
                            if (code[j] == DEC_PTR) {
                                --deltaPtrL;
                            } else {
                                ++deltaPtrL;
                            }
                            ++j;
                        }

                        int deltaDataR = 0;
                        while (j < code.length && (code[j] == DEC_DATA || code[j] == INC_DATA)) {
                            if (code[j] == DEC_DATA) {
                                --deltaDataR;
                            } else {
                                ++deltaDataR;
                            }
                            ++j;
                        }


                        int deltaPtrR = 0;
                        while (j < code.length && (code[j] == DEC_PTR || code[j] == INC_PTR)) {
                            if (code[j] == DEC_PTR) {
                                --deltaPtrR;
                            } else {
                                ++deltaPtrR;
                            }
                            ++j;
                        }

                        if (j < code.length && code[j] == LOOP_END) {
                            if (deltaDataL == -1 && deltaPtrL != 0 && deltaPtrL == -deltaPtrR) {
                                baos.write(LOOP_MOVE_DATA);
                                BrainfckError.guarantee(fitsInShort(delta), "(Pointer) update offset must fit in a short: " + delta);
                                baos.write(deltaPtrL & 0xFF);
                                baos.write(deltaPtrL >> 8);
                                baos.write(deltaDataR & 0xFF);
                                baos.write(deltaDataR >> 8);
                                i = j;
                                break;
                            }
                        }

                        baos.write(c);
                    }
                    break;
                }
                case READ_IN    : // fall-through
                case WRITE_OUT  : // fall-through
                case LOOP_END   : // fall-through
                    baos.write(c);
                    break;
                default:
                    throw BrainfckError.shouldNotReachHere("Unexpected bytecode: " + c);
            }
            // @formatter:on
        }

        return baos.toByteArray();
    }
}
