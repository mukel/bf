package com.oracle.truffle.brainfck.launcher;

import org.graalvm.launcher.AbstractLanguageLauncher;
import org.graalvm.options.OptionCategory;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class BrainfckLauncher extends AbstractLanguageLauncher {

    private VersionAction versionAction = VersionAction.None;
    String sourceFile = null;
    String sourceString = null;

    public static void main(String[] args) {
        new BrainfckLauncher().launch(args);
    }

    @Override
    protected List<String> preprocessArguments(List<String> arguments, Map<String, String> polyglotOptions) {
        ArrayList<String> unrecognized = new ArrayList<>();

        int i = 0;
        while (i < arguments.size()) {
            String arg = arguments.get(i);
            switch (arg) {
                case "-e":
                case "--eval":
                    i += 1;
                    if (i < arguments.size()) {
                        sourceString = arguments.get(i);
                    } else {
                        throw abort("Error: " + arg + " requires program");
                    }
                    break;
                case "-version":
                    versionAction = VersionAction.PrintAndExit;
                    break;
                case "-?":
                case "-help":
                    unrecognized.add("--help");
                    break;
                default:
                    if (!arg.startsWith("-")) {
                        sourceFile = arg;
                    } else {
                        unrecognized.add(arg);
                    }
                    break;
            }
            if (sourceFile != null || sourceString != null) {
                i += 1;
                if (i < arguments.size()) {
                    throw abort("Error: Invalid arguments ..." + Arrays.toString(arguments.subList(i, arguments.size()).toArray(new String[0])));
                }
                break;
            }
            i++;
        }

        return unrecognized;
    }

    @Override
    protected void launch(Context.Builder contextBuilder) {

        try (Context context = contextBuilder.build()) {
            if (versionAction != VersionAction.None) {
                runVersionAction(versionAction, context.getEngine());
            }
            if (sourceFile == null && sourceString == null) {
                throw abort(usage());
            }

            Source source = null;
            try {
                if (sourceFile != null) {
                    source = Source.newBuilder("bf", new File(sourceFile)).build();
                } else {
                    assert sourceString != null;
                    source = Source.newBuilder("bf", sourceString, "program.bf").build();
                }
            } catch (IOException e) {
                throw abort(e);
            }
            context.eval(source);
        }
    }

    @Override
    protected String getLanguageId() {
        return "bf";
    }

    private static String usage() {
        String nl = System.lineSeparator();
        // @formatter:off
        return "Usage: bf [options] -e <program>" + nl +
                "   or  bf [options] <sourcefile>" + nl +
                "           (to execute a single source-file program)" + nl + nl +
                " where options include:" + nl +
                "    -version      print product version and exit" + nl +
                "    -? -help      print this help message";
        // @formatter:on
    }

    @Override
    protected void printHelp(OptionCategory maxCategory) {
        getOutput().println(usage());
    }
}