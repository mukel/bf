package com.oracle.truffle.brainfck;

import java.io.ByteArrayOutputStream;

public final class BrainfckParser {

    public static byte[] parse(CharSequence chars) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(chars.length());
        for (int i = 0; i < chars.length(); ++i) {
            char c = chars.charAt(i);
            switch (c) {
                case '<': baos.write(Bytecodes.DEC_PTR); break;
                case '>': baos.write(Bytecodes.INC_PTR); break;
                case '-': baos.write(Bytecodes.DEC_DATA); break;
                case '+': baos.write(Bytecodes.INC_DATA); break;
                case ',': baos.write(Bytecodes.READ_IN); break;
                case '.': baos.write(Bytecodes.WRITE_OUT); break;
                case '[': baos.write(Bytecodes.LOOP_BEGIN); break;
                case ']': baos.write(Bytecodes.LOOP_END); break;
                default : // ignore
            }
        }
        return baos.toByteArray();
    }

}
