package dev.quay.slotting.cli;

import dev.quay.slotting.FaceRejection;
import dev.quay.slotting.app.FixtureLibrary;
import dev.quay.slotting.app.Reading;
import dev.quay.slotting.app.SlotFace;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** `quay place <face.json>` prints the proposal a planner can check. */
public final class QuayCommand {

    private QuayCommand() {}

    public static void main(String[] args) {
        System.exit(run(args, System.out, System.err));
    }

    public static int run(String[] args, PrintStream out, PrintStream err) {
        if (args.length == 1 && ("--help".equals(args[0]) || "-h".equals(args[0]) || "help".equals(args[0]))) {
            usage(out);
            return 0;
        }
        if (args.length != 2 || !"place".equals(args[0])) {
            usage(err);
            return 2;
        }
        Path path = Path.of(args[1]);
        if (!Files.isRegularFile(path)) {
            err.println("Could not read " + path);
            return 2;
        }
        try {
            out.print(Reading.text(new SlotFace().place(FixtureLibrary.read(path))));
            return 0;
        } catch (FaceRejection rejection) {
            err.println(rejection.getMessage());
            return 2;
        } catch (UncheckedIOException exception) {
            err.println(exception.getMessage());
            return 2;
        }
    }

    private static void usage(PrintStream stream) {
        stream.println("usage: quay place <face.json>");
    }
}
