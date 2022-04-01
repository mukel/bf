package com.oracle.truffle.brainfck;

//import static com.oracle.truffle.brainfck.Bytecodes.DEC_DATA;
//import static com.oracle.truffle.brainfck.Bytecodes.DEC_PTR;
//import static com.oracle.truffle.brainfck.Bytecodes.INC_DATA;
//import static com.oracle.truffle.brainfck.Bytecodes.INC_PTR;
//import static com.oracle.truffle.brainfck.Bytecodes.LOOP_BEGIN;
//import static com.oracle.truffle.brainfck.Bytecodes.LOOP_END;
//import static com.oracle.truffle.brainfck.Bytecodes.LOOP_MOVE_DATA;
//import static com.oracle.truffle.brainfck.Bytecodes.LOOP_MOVE_PTR;
//import static com.oracle.truffle.brainfck.Bytecodes.NOP;
//import static com.oracle.truffle.brainfck.Bytecodes.READ_IN;
//import static com.oracle.truffle.brainfck.Bytecodes.UPDATE_DATA;
//import static com.oracle.truffle.brainfck.Bytecodes.UPDATE_PTR;
//import static com.oracle.truffle.brainfck.Bytecodes.WRITE_OUT;
//import static com.oracle.truffle.brainfck.Bytecodes.ZERO_DATA;

import java.io.ByteArrayOutputStream;

import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.source.Source;

public final class BrainfckParser {
//
//    public static byte[] parse(Source source, boolean optimize) {
//        CharSequence chars = source.getCharacters();
//        ByteArrayOutputStream baos = new ByteArrayOutputStream(chars.length());
//
//        int nestingLevel = 0;
//        int maxNesting = 0;
//        for (int i = 0; i < chars.length(); ++i) {
//            char c = chars.charAt(i);
//            // @formatter:off
//            switch (c) {
//                case '<': baos.write(DEC_PTR); break;
//                case '>': baos.write(INC_PTR); break;
//                case '-': baos.write(DEC_DATA); break;
//                case '+': baos.write(INC_DATA); break;
//                case ',': baos.write(READ_IN); break;
//                case '.': baos.write(WRITE_OUT); break;
//                case '[': baos.write(LOOP_BEGIN); ++nestingLevel; break;
//                case ']': baos.write(LOOP_END);   --nestingLevel; break;
//                default : // ignore
//            }
//            // @formatter:on
//            if (nestingLevel > maxNesting) {
//                maxNesting = nestingLevel;
//            }
//            if (nestingLevel < 0) {
//                throw new BrainfckParseError(source, i, "Unbalanced brackets");
//            }
//        }
//
//        TruffleLogger.getLogger(BrainfckLanguage.ID, "parser").finest("Maximum [brackets] nesting depth: " + maxNesting);
//
//        if (nestingLevel != 0) {
//            throw new BrainfckParseError(source, chars.length() - 1, "Unbalanced brackets");
//        }
//
//        return optimize
//                        ? rleOptimize(baos.toByteArray())
//                        : baos.toByteArray();
//    }
//
//    private static boolean fitsInByte(int value) {
//        return Byte.MIN_VALUE <= value && value <= Byte.MAX_VALUE;
//    }
//
//    static byte[] rleOptimize(byte[] code) {
//        ByteArrayOutputStream baos = new ByteArrayOutputStream(code.length);
//        for (int i = 0; i < code.length; ++i) {
//            byte c = code[i];
//            // @formatter:off
//            switch (c) {
//                case NOP: /* ignore  */
//                    break;
//                case DEC_PTR: // fall-through
//                case INC_PTR: {
//                    int delta = 0;
//                    while (i < code.length) {
//                        if (code[i] == INC_PTR) {
//                            ++delta;
//                        } else if (code[i] == DEC_PTR) {
//                            --delta;
//                        } else {
//                            break;
//                        }
//                        ++i;
//                    }
//                    while (delta != 0) {
//                        int rest = delta > 0 ? Math.min(delta, Byte.MAX_VALUE) : Math.max(delta, Byte.MIN_VALUE);
//                        baos.write(UPDATE_PTR);
//                        baos.write(rest);
//                        delta -= rest;
//                    }
//                    --i;
//                    break;
//                }
//                case DEC_DATA: // fall-through
//                case INC_DATA: {
//                    int delta = 0;
//                    while (i < code.length) {
//                        if (code[i] == INC_DATA) {
//                            ++delta;
//                        } else if (code[i] == DEC_DATA) {
//                            --delta;
//                        } else {
//                            break;
//                        }
//                        ++i;
//                    }
//                    while (delta != 0) {
//                        int rest = delta > 0 ? Math.min(delta, Byte.MAX_VALUE) : Math.max(delta, Byte.MIN_VALUE);
//                        baos.write(UPDATE_DATA);
//                        baos.write(rest);
//                        delta -= rest;
//                    }
//                    --i;
//                    break;
//                }
//                case LOOP_BEGIN : {
//                    // [-] and [+]
//                    if (c == LOOP_BEGIN && i + 2 < code.length && (code[i + 1] == DEC_DATA || code[i + 1] == INC_DATA) && code[i + 2] == LOOP_END) {
//                        i += 2;
//                        baos.write(ZERO_DATA);
//                        break;
//                    } else {
//                        int delta = 0;
//                        int j = i + 1;
//                        while (j < code.length && (code[j] == DEC_PTR || code[j] == INC_PTR)) {
//                            if (code[j] == DEC_PTR) {
//                                --delta;
//                            } else {
//                                ++delta;
//                            }
//                            ++j;
//                        }
//                        if (j < code.length && code[j] == LOOP_END && delta != 0 && fitsInByte(delta)) {
//                            baos.write(LOOP_MOVE_PTR);
//                            baos.write(delta);
//                            i = j;
//                            break;
//                        }
//
//                        int deltaDataL = 0;
//                        j = i + 1;
//                        while (j < code.length && (code[j] == DEC_DATA || code[j] == INC_DATA)) {
//                            if (code[j] == DEC_DATA) {
//                                --deltaDataL;
//                            } else {
//                                ++deltaDataL;
//                            }
//                            ++j;
//                        }
//
//                        int deltaPtrL = 0;
//                        while (j < code.length && (code[j] == DEC_PTR || code[j] == INC_PTR)) {
//                            if (code[j] == DEC_PTR) {
//                                --deltaPtrL;
//                            } else {
//                                ++deltaPtrL;
//                            }
//                            ++j;
//                        }
//
//                        int deltaDataR = 0;
//                        while (j < code.length && (code[j] == DEC_DATA || code[j] == INC_DATA)) {
//                            if (code[j] == DEC_DATA) {
//                                --deltaDataR;
//                            } else {
//                                ++deltaDataR;
//                            }
//                            ++j;
//                        }
//
//
//                        int deltaPtrR = 0;
//                        while (j < code.length && (code[j] == DEC_PTR || code[j] == INC_PTR)) {
//                            if (code[j] == DEC_PTR) {
//                                --deltaPtrR;
//                            } else {
//                                ++deltaPtrR;
//                            }
//                            ++j;
//                        }
//
//                        if (j < code.length && code[j] == LOOP_END && fitsInByte(deltaPtrL) && fitsInByte(deltaDataR)) {
//                            if (deltaDataL == -1 && deltaPtrL != 0 && deltaPtrL == -deltaPtrR) {
//                                baos.write(LOOP_MOVE_DATA);
//                                baos.write(deltaPtrL);
//                                baos.write(deltaDataR);
//                                i = j;
//                                break;
//                            }
//                        }
//
//                        baos.write(c);
//                    }
//                    break;
//                }
//                case READ_IN    : // fall-through
//                case WRITE_OUT  : // fall-through
//                case LOOP_END   : // fall-through
//                    baos.write(c);
//                    break;
//                default:
//                    throw BrainfckError.shouldNotReachHere("Unexpected bytecode: " + c);
//            }
//            // @formatter:on
//        }
//
//        return baos.toByteArray();
//    }
}
