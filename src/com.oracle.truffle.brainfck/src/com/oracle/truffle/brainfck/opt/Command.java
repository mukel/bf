package com.oracle.truffle.brainfck.opt;

import java.util.List;

abstract class Command {

}

class Assign extends Command {
    final int offset;
    final int value;
    Assign(int offset, int value) {
        this.offset = offset;
        this.value = value;
    }
    Assign updateValue(int delta) {
        if (delta == 0) {
            return this;
        }
        return new Assign(offset, value + delta);
    }
}

class UpdateData extends Command {
    final int offset;
    final int value;
    UpdateData(int offset, int value) {
        this.offset = offset;
        this.value = value;
    }
    UpdateData updateValue(int delta) {
        if (delta == 0) {
            return this;
        }
        return new UpdateData(offset, value + delta);
    }
}

class MultAssign extends Command {
    final int srcOff;
    final int destOff;
    final int value;
    MultAssign(int srcOff, int destOff, int value) {
        this.srcOff = srcOff;
        this.destOff = destOff;
        this.value = value;
    }

    MultAssign updateOffset(int delta) {
        if (delta == 0) {
            return this;
        }
        return new MultAssign(srcOff + delta, destOff + delta, value);
    }
}

class MultAdd extends Command {
    final int srcOff;
    final int destOff;
    final int value;
    MultAdd(int srcOff, int destOff, int value) {
        this.srcOff = srcOff;
        this.destOff = destOff;
        this.value = value;
    }
}

class UpdatePtr extends Command {
    final int offset;
    UpdatePtr(int offset) {
        this.offset = offset;
    }

    UpdatePtr updateOffset(int delta) {
        if (delta == 0) {
            return this;
        }
        return new UpdatePtr(offset + delta);
    }
}

class Input extends Command {
    final int offset;
    Input(int offset) {
        this.offset = offset;
    }

    Input updateOffset(int delta) {
        if (delta == 0) {
            return this;
        }
        return new Input(offset + delta);
    }
}

class Output extends Command {
    final int offset;
    Output(int offset) {
        this.offset = offset;
    }

    Output updateOffset(int delta) {
        if (delta == 0) {
            return this;
        }
        return new Output(offset + delta);
    }
}

class If extends Command {
    final List<Command> commands;

    If(List<Command> commands) {
        this.commands = commands;
    }
}

class Loop extends Command {
    final List<Command> commands;

    Loop(List<Command> commands) {
        this.commands = commands;
    }
}
