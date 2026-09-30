package dev.logicforge.app;

import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * What the command line asks for: {@code logicforge [FILE]}, plus {@code --version} and
 * {@code --help}.
 *
 * <p>{@code FILE} may be a path, relative to the working directory, or a {@code file://} URI
 * as some file managers pass it. One project is opened per window; further files are
 * reported as ignored rather than failing the start. {@code --smoke-test} is an internal
 * switch for the packaging checks and deliberately not listed in the help text.
 */
public record LaunchOptions(Mode mode, Optional<Path> file, List<String> ignoredFiles) {

    public enum Mode {
        /** Start the workbench. */
        GUI,
        /** Start, open, save and reopen a project, then exit — used by the packaging checks. */
        SMOKE_TEST,
        VERSION,
        HELP
    }

    static final String USAGE = """
            Usage: logicforge [OPTION] [FILE]
            Start the LogicForge digital logic simulator, optionally opening FILE (a .logic project).

              -h, --help       show this help and exit
              -V, --version    print the version and exit
            """;

    public LaunchOptions {
        ignoredFiles = List.copyOf(ignoredFiles);
    }

    /**
     * Parses the arguments.
     *
     * @param workingDirectory what relative file names are resolved against
     * @throws IllegalArgumentException for an unknown option or a malformed file name
     */
    public static LaunchOptions parse(List<String> arguments, Path workingDirectory) {
        Mode mode = Mode.GUI;
        List<Path> files = new ArrayList<>();
        boolean optionsEnded = false;
        for (String argument : arguments) {
            if (!optionsEnded && argument.equals("--")) {
                optionsEnded = true;
            } else if (!optionsEnded && argument.startsWith("-") && argument.length() > 1) {
                mode = switch (argument) {
                    case "-h", "--help" -> Mode.HELP;
                    case "-V", "--version" -> mode == Mode.HELP ? mode : Mode.VERSION;
                    case "--smoke-test" -> mode == Mode.GUI ? Mode.SMOKE_TEST : mode;
                    default -> throw new IllegalArgumentException("unknown option '" + argument + "'");
                };
            } else {
                files.add(toPath(argument, workingDirectory));
            }
        }
        List<String> ignored = files.stream().skip(1).map(Path::toString).toList();
        return new LaunchOptions(mode, files.stream().findFirst(), ignored);
    }

    private static Path toPath(String argument, Path workingDirectory) {
        if (argument.isBlank()) {
            throw new IllegalArgumentException("an empty file name was given");
        }
        try {
            if (argument.startsWith("file:")) {
                return Path.of(URI.create(argument)).normalize();
            }
            return workingDirectory.resolve(argument).toAbsolutePath().normalize();
        } catch (IllegalArgumentException | java.nio.file.FileSystemNotFoundException invalid) {
            // InvalidPathException is an IllegalArgumentException, as is a malformed URI.
            throw new IllegalArgumentException("'" + argument + "' is not a valid file name", invalid);
        }
    }
}
