package com.oracle.truffle.brainfck;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Option;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.Registration;
import com.oracle.truffle.api.nodes.RootNode;
import org.graalvm.options.OptionCategory;
import org.graalvm.options.OptionDescriptors;
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
    protected OptionDescriptors getOptionDescriptors() {
        return new OptionsOptionDescriptors();
    }

    @Override
    protected CallTarget parse(final ParsingRequest request) throws Exception {
        Env context = getCurrentContext(BrainfckLanguage.class);
        boolean optimizeBytecodes = context.getOptions().get(Options.Optimize);
        byte[] code = BrainfckParser.parse(request.getSource(), optimizeBytecodes);
        RootNode rootNode = new BytecodeNode(code, this, context);
        return Truffle.getRuntime().createCallTarget(rootNode);
    }

    @Option.Group(BrainfckLanguage.ID)
    static final class Options {
        @Option(help = "Specify the behavior when encountering EOF while reading (0|-1|unchanged).", //
                        category = OptionCategory.USER, stability = OptionStability.STABLE) //
        public static final OptionKey<EOFMode> EOF = new OptionKey<>(EOFMode.UNCHANGED, EOFMode.OPTION_TYPE);

        @Option(help = "Augments/optimize Brainf*ck bytecodes: fusing chains of <> and +- and adding additional high-level bytecodes.", //
                category = OptionCategory.USER, stability = OptionStability.STABLE) //
        public static final OptionKey<Boolean> Optimize = new OptionKey<>(true);

        @Option(help = "Collects and prints the executed bytecodes.", //
                category = OptionCategory.USER, stability = OptionStability.STABLE) //
        public static final OptionKey<Boolean> Histogram = new OptionKey<>(false);

        @Option(help = "Specify the number of cells (memory slots) available.", //
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
                            throw new IllegalArgumentException("--EOF= can be '0', '-1' or 'unchanged'.");
                    }
                }
            });
        }
    }
}
