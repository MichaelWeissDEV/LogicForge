package dev.logicforge.format;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFileAttributeView;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * Replaces a file so that a crash, a full disk or a failing write never leaves it half
 * written.
 *
 * <p>The new content goes into a temporary file next to the target, is flushed to disk and
 * only then renamed over the target. On every file system that supports it the rename is
 * atomic, so the target always holds either the complete old or the complete new content.
 * Where an atomic rename is not available the writer falls back to an ordinary replacing
 * move; the optional {@code .bak} copy of the previous version covers that remaining window.
 */
public final class AtomicFileWriter {

    /** Whether the previous version of the target is kept as {@code <name>.bak}. */
    public enum Backup {
        NONE,
        KEEP_PREVIOUS
    }

    /** Suffix of the backup copy that {@link Backup#KEEP_PREVIOUS} maintains. */
    public static final String BACKUP_SUFFIX = ".bak";

    private static final SecureRandom RANDOM = new SecureRandom();

    /** The rename step, replaceable so tests can simulate file systems without atomic moves. */
    @FunctionalInterface
    interface Mover {
        void move(Path source, Path target, CopyOption... options) throws IOException;
    }

    private AtomicFileWriter() {
    }

    /** Writes {@code content} to {@code target}, replacing it atomically where possible. */
    public static void write(Path target, byte[] content, Backup backup) throws IOException {
        write(target, content, backup, Files::move);
    }

    /** The backup file {@link Backup#KEEP_PREVIOUS} writes for {@code target}. */
    public static Path backupPathFor(Path target) {
        return target.resolveSibling(target.getFileName() + BACKUP_SUFFIX);
    }

    static void write(Path target, byte[] content, Backup backup, Mover mover) throws IOException {
        Path destination = resolveDestination(target);
        Path directory = destination.toAbsolutePath().getParent();
        if (directory == null || !Files.isDirectory(directory)) {
            throw new IOException("The folder " + directory + " does not exist");
        }
        Path temporary = directory.resolve(temporaryName(destination));
        try {
            writeFully(temporary, content);
            copyPermissions(destination, temporary);
            if (backup == Backup.KEEP_PREVIOUS && Files.isRegularFile(destination)) {
                Files.copy(destination, backupPathFor(destination), StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.COPY_ATTRIBUTES);
            }
            replace(temporary, destination, mover);
        } finally {
            Files.deleteIfExists(temporary);
        }
        syncDirectory(directory);
    }

    /** A symbolic link is written through, so the link itself survives the save. */
    private static Path resolveDestination(Path target) throws IOException {
        if (Files.isSymbolicLink(target) && Files.exists(target)) {
            return target.toRealPath();
        }
        return target;
    }

    /** Hidden, unique and in the same directory, so the final rename never crosses devices. */
    private static String temporaryName(Path destination) {
        byte[] suffix = new byte[6];
        RANDOM.nextBytes(suffix);
        return "." + destination.getFileName() + "." + HexFormat.of().formatHex(suffix) + ".tmp";
    }

    private static void writeFully(Path file, byte[] content) throws IOException {
        try (FileChannel channel = FileChannel.open(file,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            ByteBuffer buffer = ByteBuffer.wrap(content);
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
            channel.force(true);
        }
    }

    /** Keeps the permissions of the file being replaced, e.g. a deliberately private project. */
    private static void copyPermissions(Path from, Path to) {
        if (!Files.isRegularFile(from)) {
            return;
        }
        PosixFileAttributeView source = Files.getFileAttributeView(from, PosixFileAttributeView.class,
                LinkOption.NOFOLLOW_LINKS);
        PosixFileAttributeView target = Files.getFileAttributeView(to, PosixFileAttributeView.class,
                LinkOption.NOFOLLOW_LINKS);
        if (source == null || target == null) {
            return;
        }
        try {
            target.setPermissions(source.readAttributes().permissions());
        } catch (IOException | UnsupportedOperationException | SecurityException ignored) {
            // Best effort: the file keeps the permissions the umask gave the new file.
        }
    }

    private static void replace(Path temporary, Path destination, Mover mover) throws IOException {
        try {
            mover.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException notAtomic) {
            mover.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** Makes the rename itself durable. Not every platform can open a directory; that's fine. */
    private static void syncDirectory(Path directory) {
        try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) {
            channel.force(true);
        } catch (IOException | UnsupportedOperationException | SecurityException ignored) {
            // The data itself is already on disk; only the directory entry may lag behind.
        }
    }
}
