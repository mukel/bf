package com.oracle.truffle.brainfck;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Option;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.Registration;
import com.oracle.truffle.api.nodes.RootNode;
import org.graalvm.options.OptionCategory;
import org.graalvm.options.OptionKey;
import org.graalvm.options.OptionStability;
import org.graalvm.options.OptionType;

import java.util.function.Function;

@Registration( //
                id = BrainfckLanguage.ID, //
                name = BrainfckLanguage.NAME, //
                version = BrainfckLanguage.VERSION, //
                characterMimeTypes = BrainfckLanguage.MIME_TYPE, //
                fileTypeDetectors = BrainfckFileDetector.class, //
                contextPolicy = TruffleLanguage.ContextPolicy.SHARED) //
public final class BrainfckLanguage extends TruffleLanguage<TruffleLanguage.Env> {

    static final String ID = "bf";
    static final String NAME = "Brainf*ck";
    static final String VERSION = "0.1";
    public static final String MIME_TYPE = "application/x-bf";

    @Override
    protected Env createContext(final TruffleLanguage.Env env) {
        return env;
    }

    @Override
    protected CallTarget parse(final ParsingRequest request) throws Exception {
        byte[] code = BrainfckParser.parse(request.getSource().getCharacters());
        RootNode rootNode = new BytecodeNode(code, this, getCurrentContext(BrainfckLanguage.class));
        return Truffle.getRuntime().createCallTarget(rootNode);
    }

    static final class Options {
        @Option(help = "Specify the behavior when encountering EOF while reading.", //
                category = OptionCategory.USER, stability = OptionStability.STABLE) //
        public static final OptionKey<EOFMode> EOF = new OptionKey<>(EOFMode.UNCHANGED, EOFMode.OPTION_TYPE);

        @Option(help = "Specify the number of cells available to every program.", //
                category = OptionCategory.USER, stability = OptionStability.STABLE) //
        public static final OptionKey<Integer> NumberOfCells = new OptionKey<>(30000);

        enum EOFMode {
            ZERO,
            MINUS_ONE,
            UNCHANGED;

            static final OptionType<EOFMode> OPTION_TYPE = new OptionType<>("EOFMode", new Function<String, EOFMode>() {
                @Override
                public EOFMode apply(String s) {
                    switch (s.toLowerCase()) {
                        case "0":
                            return EOFMode.ZERO;
                        case "-1":
                            return EOFMode.MINUS_ONE;
                        case "unchanged":
                            return EOFMode.UNCHANGED;
                        default:
                            throw new IllegalArgumentException("--eof= can be '0', '-1' or 'unchanged'.");
                    }
                }
            });
        }
    }
}
