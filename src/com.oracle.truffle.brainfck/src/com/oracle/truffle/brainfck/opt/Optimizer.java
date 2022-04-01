package com.oracle.truffle.brainfck.opt;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.brainfck.BrainfckError;
import com.oracle.truffle.brainfck.BrainfckLanguage;
import com.oracle.truffle.brainfck.BrainfckParseError;
import com.oracle.truffle.brainfck.BytecodeStream;
import com.oracle.truffle.brainfck.Bytecodes;

import static com.oracle.truffle.brainfck.Bytecodes.END;
import static com.oracle.truffle.brainfck.Bytecodes.IF_END;
import static com.oracle.truffle.brainfck.Bytecodes.LOOP_BEGIN;
import static com.oracle.truffle.brainfck.Bytecodes.LOOP_END;
import static com.oracle.truffle.brainfck.Bytecodes.MULT_ADD_AT_OFFSET;
import static com.oracle.truffle.brainfck.Bytecodes.MULT_ASSIGN_AT_OFFSET;
import static com.oracle.truffle.brainfck.Bytecodes.READ_IN_AT_OFFSET;
import static com.oracle.truffle.brainfck.Bytecodes.SET_DATA_AT_OFFSET;
import static com.oracle.truffle.brainfck.Bytecodes.UPDATE_DATA_AT_OFFSET;
import static com.oracle.truffle.brainfck.Bytecodes.UPDATE_PTR;
import static com.oracle.truffle.brainfck.Bytecodes.WRITE_OUT_AT_OFFSET;

public class Optimizer {

    public static byte[] parse(Source source) {
        CharSequence chars = source.getCharacters();

        Stack<List<Command>> blocks = new Stack<>();
        List<Command> topBlock = blocks.push(new ArrayList<>());

        int nestingLevel = 0;
        int maxNesting = 0;
        for (int i = 0; i < chars.length(); ++i) {
            char c = chars.charAt(i);
            // @formatter:off
            switch (c) {
                case '<':
                    topBlock.add(new UpdatePtr(-1));
                    break;
                case '>':
                    topBlock.add(new UpdatePtr(+1));
                    break;
                case '-':
                    topBlock.add(new UpdateData(0, -1));
                    break;
                case '+':
                    topBlock.add(new UpdateData(0, +1));
                    break;
                case ',':
                    topBlock.add(new Input(0));
                    break;
                case '.':
                    topBlock.add(new Output(0));
                    break;
                case '[':
                    topBlock = blocks.push(new ArrayList<>());
                    ++nestingLevel;
                    break;
                case ']':
                    --nestingLevel;
                    if (nestingLevel < 0) {
                        throw new BrainfckParseError(source, i, "Unbalanced brackets");
                    }
                    List<Command> body = blocks.pop();
                    topBlock = blocks.peek();
                    topBlock.add(new Loop(body));
                    break;
                default: // ignore
            }
            // @formatter:on
            if (nestingLevel > maxNesting) {
                maxNesting = nestingLevel;
            }
        }

        if (nestingLevel != 0) {
            throw new BrainfckParseError(source, chars.length() - 1, "Unbalanced brackets");
        }

        TruffleLogger.getLogger(BrainfckLanguage.ID, "parser").finest("Maximum [brackets] nesting depth: " + maxNesting);

        assert blocks.size() == 1;
        List<Command> commands = optimize(topBlock);
        commands = optimize(commands);
        commands = optimize(commands);
        return toBytecodes(commands);
    }

    // Optimizes the given list of Commands, returning a new list of Commands.
    static List<Command> optimize(List<Command> commands) {
        List<Command> result = new ArrayList<>();
        int offset = 0;  // How much the memory pointer has moved without being updated
        for (Command cmd : commands) {
            if (cmd instanceof Assign) {
                // Try to fuse into previous command
                int off = ((Assign) cmd).offset + offset;

                Command prev = (result.size() > 0) ? result.get(result.size() - 1) : null;

                if ((prev instanceof UpdateData && ((UpdateData) prev).offset == off) ||
                                (prev instanceof Assign && ((Assign) prev).offset == off) ||
                                (prev instanceof MultAdd && ((MultAdd) prev).destOff == off) ||
                                (prev instanceof MultAssign && ((MultAssign) prev).destOff == off)) {
                    result.remove(result.size() - 1);
                }

                result.add(new Assign(off, ((Assign) cmd).value));
            } else if (cmd instanceof MultAssign) {
                result.add(((MultAssign) cmd).updateOffset(offset));
            } else if (cmd instanceof UpdateData) {
                // Try to fuse into previous command
                int off = ((UpdateData) cmd).offset + offset;
                Command prev = (result.size() > 0) ? result.get(result.size() - 1) : null;

                if (prev instanceof UpdateData && ((UpdateData) prev).offset == off) {
                    result.set(result.size() - 1, ((UpdateData) prev).updateValue(((UpdateData) cmd).value));
                } else if (prev instanceof Assign && ((Assign) prev).offset == off) {
                    result.set(result.size() - 1, ((Assign) prev).updateValue(((UpdateData) cmd).value));
                } else {
                    result.add(new UpdateData(off, ((UpdateData) cmd).value));
                }
            } else if (cmd instanceof MultAdd) {
                // Try to fuse into previous command.
                int off = ((MultAdd) cmd).destOff + offset;
                Command prev = (result.size() > 0) ? result.get(result.size() - 1) : null;
                if (prev instanceof Assign && ((Assign) prev).offset == off && ((Assign) prev).value == 0) {
                    result.set(result.size() - 1, new MultAssign(((MultAdd) cmd).srcOff + offset, off, ((MultAdd) cmd).value));
                } else {
                    result.add(new MultAdd(((MultAdd) cmd).srcOff + offset, off, ((MultAdd) cmd).value));
                }
            } else if (cmd instanceof UpdatePtr) {
                offset += ((UpdatePtr) cmd).offset;
            } else if (cmd instanceof Input) {
                result.add(((Input) cmd).updateOffset(offset));
            } else if (cmd instanceof Output) {
                result.add(((Output) cmd).updateOffset(offset));
            } else {
                // Commit the pointer movement before starting a loop/if
                if (offset != 0) {
                    result.add(new UpdatePtr(offset));
                    offset = 0;
                }

                if (cmd instanceof Loop) {
                    List<Command> tempSimple = optimizeSimpleLoop(((Loop) cmd).commands);
                    if (tempSimple != null) {
                        result.addAll(tempSimple);
                    } else {
                        Command tempComplex = optimizeComplexLoop(((Loop) cmd).commands);
                        if (tempComplex != null) {
                            result.add(tempComplex);
                        } else {
                            result.add(new Loop(optimize(((Loop) cmd).commands)));
                        }
                    }
                } else if (cmd instanceof If) {
                    result.add(new If(optimize(((If) cmd).commands)));
                } else {
                    throw BrainfckError.shouldNotReachHere("Unknown command");
                }
            }
        }

        // Commit the pointer movement before exiting this block
        if (offset != 0) {
            result.add(new UpdatePtr(offset));
        }
        return result;
    }

    // Tries to optimize the given list of looped commands into a list that would be executed
    // without looping. Returns None if not possible.
    static List<Command> optimizeSimpleLoop(List<Command> commands) {
        Map<Integer, Integer> deltas = new HashMap<>();  // delta[i] = v means that in each loop
                                                         // iteration, mem[p + i] is added by the
                                                         // amount v
        int offset = 0;
        for (Command cmd : commands) {
            // This implementation can only optimize loops that consist of only UpdateData and UpdatePtr
            if (cmd instanceof UpdateData) {
                int off = ((UpdateData) cmd).offset + offset;
                deltas.put(off, deltas.getOrDefault(off, 0) + ((UpdateData) cmd).value);
            } else if (cmd instanceof UpdatePtr) {
                offset += ((UpdatePtr) cmd).offset;
            } else {
                return null;
            }
        }

        // Can't optimize if a loop iteration has a net pointer movement, or if the cell being
        // tested isn't decremented by 1
        if (offset != 0 || deltas.getOrDefault(0, 0) != -1) {
            return null;
        }

        // Convert the loop into a list of multiply-add commands that source from the cell being
        // tested
        deltas.remove(0);
        List<Command> result = new ArrayList<>();
        deltas.keySet().stream().sorted().forEachOrdered(off -> {
            result.add(new MultAdd(0, off, deltas.get(off)));
        });
        result.add(new Assign(0, 0));
        return result;
    }

    // Attempts to convert the body of a while-loop into an if-statement. This is possible if
    // roughly all these conditions are met:
    // - There are no commands other than Add/Assign/MultAdd/MultAssign (in particular, no net
    // movement, I/O, or embedded loops)
    // - The value at offset 0 is decremented by 1
    // - All MultAdd and MultAssign commands read from {an offset other than 0 whose value is
    // cleared before the end in the loop}
    static Command optimizeComplexLoop(List<Command> commands) {
        List<Command> result = new ArrayList<>();
        int origindelta = 0;
        Set<Integer> clears = new HashSet<>();
        clears.add(0);

        for (Command cmd : commands) {
            if (cmd instanceof UpdateData) {
                if (((UpdateData) cmd).offset == 0) {
                    origindelta += ((UpdateData) cmd).value;
                } else {
                    clears.remove(((UpdateData) cmd).offset);
                    result.add(new MultAdd(0, ((UpdateData) cmd).offset, ((UpdateData) cmd).value));
                }
            } else if (cmd instanceof MultAdd) {
                if (((MultAdd) cmd).destOff == 0) {
                    return null;
                }
                clears.remove(((MultAdd) cmd).destOff);
                result.add(cmd);
            } else if (cmd instanceof MultAssign) {
                if (((MultAssign) cmd).destOff == 0) {
                    return null;
                }
                clears.remove(((MultAssign) cmd).destOff);
                result.add(cmd);
            } else if (cmd instanceof Assign) {
                if (((Assign) cmd).offset == 0) {
                    return null;
                } else {
                    if (((Assign) cmd).value == 0) {
                        clears.add(((Assign) cmd).offset);
                    } else {
                        clears.remove(((Assign) cmd).offset);
                    }
                    result.add(cmd);
                }
            } else {
                return null;
            }
        }

        if (origindelta != -1) {
            return null;
        }
        for (Command cmd : result) {
            if ((cmd instanceof MultAdd && !clears.contains(((MultAdd) cmd).srcOff)) ||
                            (cmd instanceof MultAssign && !clears.contains(((MultAssign) cmd).srcOff))) {
                return null;
            }
        }

        result.add(new Assign(0, 0));
        return new If(result);
    }

    static int checkByte(int b) {
        if (!(Byte.MIN_VALUE <= b && b <= Byte.MAX_VALUE)) {
            throw BrainfckError.shouldNotReachHere();
        }
        return b;
    }

    static byte[] toBytecodes(List<Command> commands) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        for (Command cmd : commands) {
            if (cmd instanceof UpdateData) {
                baos.write(UPDATE_DATA_AT_OFFSET);
                baos.write(checkByte(((UpdateData) cmd).value));
                baos.write(checkByte(((UpdateData) cmd).offset));
            } else if (cmd instanceof Assign) {
                baos.write(SET_DATA_AT_OFFSET);
                baos.write(checkByte(((Assign) cmd).value));
                baos.write(checkByte(((Assign) cmd).offset));
            } else if (cmd instanceof If) {
                baos.write(LOOP_BEGIN);
                baos.writeBytes(toBytecodes(((If) cmd).commands));
                baos.write(IF_END);
            } else if (cmd instanceof Input) {
                baos.write(READ_IN_AT_OFFSET);
                baos.write(checkByte(((Input) cmd).offset));
            } else if (cmd instanceof Loop) {
                baos.write(LOOP_BEGIN);
                baos.writeBytes(toBytecodes(((Loop) cmd).commands));
                baos.write(LOOP_END);
            } else if (cmd instanceof MultAdd) {
                baos.write(MULT_ADD_AT_OFFSET);
                baos.write(checkByte(((MultAdd) cmd).value));
                baos.write(checkByte(((MultAdd) cmd).srcOff));
                baos.write(checkByte(((MultAdd) cmd).destOff));
            } else if (cmd instanceof MultAssign) {
                baos.write(MULT_ASSIGN_AT_OFFSET);
                baos.write(checkByte(((MultAssign) cmd).value));
                baos.write(checkByte(((MultAssign) cmd).srcOff));
                baos.write(checkByte(((MultAssign) cmd).destOff));
            } else if (cmd instanceof Output) {
                baos.write(WRITE_OUT_AT_OFFSET);
                baos.write(checkByte(((Output) cmd).offset));
            } else if (cmd instanceof UpdatePtr) {
                baos.write(UPDATE_PTR);
                baos.write(checkByte(((UpdatePtr) cmd).offset));
            } else {
                throw BrainfckError.shouldNotReachHere("Unknown command: " + cmd);
            }
        }
        return baos.toByteArray();
    }

    static void dumpBytecodes(byte[] code) {
        BytecodeStream bs = new BytecodeStream(code);
        int bci = 0;
        while (bci < bs.endBCI()) {
            System.out.println(Bytecodes.nameOf(bs.currentBC(bci)));
            bci = bs.nextBCI(bci);
        }
    }

    static String indent(String line, int level) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < level; i++) {
            sb.append("\t");
        }
        sb.append(line);
        sb.append("\n");
        return sb.toString();
    }


    static String formatMemory(int off) {
        if (off == 0) {
            return "mem[i]";
        } else {
            return String.format("mem[i %s %s]", plusminus(off), Math.abs(off));
        }
    }


    static String plusminus(int val) {
        if (val >= 0) {
            return "+";
        } else {
            return "-";
        }
    }

    static String commandsToJava(List<Command> commands, String name, boolean maincall, int indentlevel) {
        String result = "";
        if (maincall) {
            result += indent("import java.io.IOException;", 0);
            result += indent("", 0);
            result += indent("public class " + name + " {", 0);
            result += indent("public static void main(String[] args) throws IOException {", 1);
            result += indent("byte[] mem = new byte[1000000];", indentlevel);
            result += indent("int i = 1000;", indentlevel);
            result += indent("", indentlevel);
        }


        for (Command cmd : commands) {
            if (cmd instanceof Assign) {
                result += indent(String.format("%s = %s;", formatMemory(((Assign) cmd).offset), (((Assign) cmd).value & 0xFF) - ((((Assign) cmd).value & 0x80) << 1)), indentlevel);
            } else if (cmd instanceof UpdateData) {
                if (((UpdateData) cmd).value == 1) {
                    result += indent(String.format("%s++;", formatMemory(((UpdateData) cmd).offset)), indentlevel);
                } else if (((UpdateData) cmd).value == -1) {
                    result += indent(String.format("%s--;", formatMemory(((UpdateData) cmd).offset)), indentlevel);
                } else {
                    result += indent(String.format("%s %s= %s;", formatMemory(((UpdateData) cmd).offset), plusminus(((UpdateData) cmd).value), Math.abs(((UpdateData) cmd).value)), indentlevel);
                }
            } else if (cmd instanceof MultAssign) {
                if (((MultAssign) cmd).value == 1) {
                    result += indent(String.format("%s = %s;", formatMemory(((MultAssign) cmd).destOff), formatMemory(((MultAssign) cmd).srcOff)), indentlevel);
                } else {
                    result += indent(String.format("%s = (byte)(%s * %s);", formatMemory(((MultAssign) cmd).destOff), formatMemory(((MultAssign) cmd).srcOff), ((MultAssign) cmd).value), indentlevel);
                }
            } else if (cmd instanceof MultAdd) {
                if (Math.abs(((MultAdd) cmd).value) == 1) {
                    result += indent(String.format("%s %s= %s;", formatMemory(((MultAdd) cmd).destOff), plusminus(((MultAdd) cmd).value), formatMemory(((MultAdd) cmd).srcOff)), indentlevel);
                } else {
                    result += indent(String.format("%s %s= %s * %s;", formatMemory(((MultAdd) cmd).destOff), plusminus(((MultAdd) cmd).value), formatMemory(((MultAdd) cmd).srcOff), Math.abs(((MultAdd) cmd).value)), indentlevel);
                }
            } else if (cmd instanceof UpdatePtr) {
                if (((UpdatePtr) cmd).offset == 1) {
                    result += indent("i++;", indentlevel);
                } else if (((UpdatePtr) cmd).offset == -1) {
                    result += indent("i--;", indentlevel);
                } else {
                    result += indent(String.format("i %s= %s;", plusminus(((UpdatePtr) cmd).offset), Math.abs(((UpdatePtr) cmd).offset)), indentlevel);
                }
            } else if (cmd instanceof Input) {
                result += indent(String.format("%s = (byte)Math.max(System.in.read(), 0);", formatMemory(((Input) cmd).offset)), indentlevel);
            } else if (cmd instanceof Output) {
                result += indent(String.format("System.out.write(%s);", formatMemory(((Output) cmd).offset)), indentlevel) + indent("System.out.flush();", indentlevel);
            } else if (cmd instanceof If) {
                result += indent("if (mem[i] != 0) {", indentlevel);
                result += commandsToJava(((If) cmd).commands, name, false, indentlevel + 1);
                result += indent("}", indentlevel);
            } else if (cmd instanceof Loop) {
                result += indent("while (mem[i] != 0) {", indentlevel);
                result += commandsToJava(((Loop) cmd).commands, name, false, indentlevel + 1);
                result += indent("}", indentlevel);
            } else {
                throw BrainfckError.shouldNotReachHere("Unknown command");
            }
        }

        if (maincall) {
            result += indent("}", 1);
            result += indent("}", 0);
        }
        return result;
    }

}
