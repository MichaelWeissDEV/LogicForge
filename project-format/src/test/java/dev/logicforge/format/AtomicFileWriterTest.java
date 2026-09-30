package dev.logicforge.format;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.CopyOption;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AtomicFileWriterTest {

    @TempDir
    Path directory;

    @Test
    void writesANewFile() throws IOException {
        Path target = directory.resolve("new.logic");

        AtomicFileWriter.write(target, bytes("hello"), AtomicFileWriter.Backup.KEEP_PREVIOUS);

        assertEquals("hello", Files.readString(target));
        assertFalse(Files.exists(AtomicFileWriter.backupPathFor(target)),
                "there was no previous version to back up");
        assertOnlyFiles("new.logic");
    }

    @Test
    void replacesAnExistingFileCompletely() throws IOException {
        Path target = directory.resolve("project.logic");
        Files.writeString(target, "a much longer previous content that must not survive as a tail");

        AtomicFileWriter.write(target, bytes("short"), AtomicFileWriter.Backup.NONE);

        assertEquals("short", Files.readString(target));
        assertOnlyFiles("project.logic");
    }

    @Test
    void keepsThePreviousVersionAsBackupWhenAsked() throws IOException {
        Path target = directory.resolve("project.logic");
        Files.writeString(target, "version 1");

        AtomicFileWriter.write(target, bytes("version 2"), AtomicFileWriter.Backup.KEEP_PREVIOUS);
        assertEquals("version 1", Files.readString(directory.resolve("project.logic.bak")));

        AtomicFileWriter.write(target, bytes("version 3"), AtomicFileWriter.Backup.KEEP_PREVIOUS);
        assertEquals("version 3", Files.readString(target));
        assertEquals("version 2", Files.readString(directory.resolve("project.logic.bak")));
        assertOnlyFiles("project.logic", "project.logic.bak");
    }

    @Test
    void usesAnAtomicReplacingMove() throws IOException {
        Path target = directory.resolve("project.logic");
        List<Set<CopyOption>> moves = new ArrayList<>();

        AtomicFileWriter.write(target, bytes("content"), AtomicFileWriter.Backup.NONE,
                (source, destination, options) -> {
                    moves.add(Set.of(options));
                    Files.move(source, destination, options);
                });

        assertEquals(List.of(Set.of(StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)),
                moves);
        assertEquals("content", Files.readString(target));
    }

    @Test
    void fallsBackToAReplacingMoveWhenTheFileSystemCannotMoveAtomically() throws IOException {
        Path target = directory.resolve("project.logic");
        Files.writeString(target, "old");
        List<Set<CopyOption>> moves = new ArrayList<>();

        AtomicFileWriter.write(target, bytes("new"), AtomicFileWriter.Backup.KEEP_PREVIOUS,
                (source, destination, options) -> {
                    moves.add(Set.of(options));
                    if (Arrays.asList(options).contains(StandardCopyOption.ATOMIC_MOVE)) {
                        throw new AtomicMoveNotSupportedException(source.toString(), destination.toString(),
                                "simulated file system without atomic rename");
                    }
                    Files.move(source, destination, options);
                });

        assertEquals(2, moves.size());
        assertEquals(Set.of(StandardCopyOption.REPLACE_EXISTING), moves.get(1));
        assertEquals("new", Files.readString(target));
        assertEquals("old", Files.readString(AtomicFileWriter.backupPathFor(target)));
        assertOnlyFiles("project.logic", "project.logic.bak");
    }

    @Test
    void aFailedSaveLeavesTheOriginalUntouchedAndNoTemporaryFileBehind() throws IOException {
        Path target = directory.resolve("project.logic");
        Files.writeString(target, "precious");

        IOException failure = assertThrows(IOException.class, () ->
                AtomicFileWriter.write(target, bytes("never visible"), AtomicFileWriter.Backup.NONE,
                        (source, destination, options) -> {
                            // The complete new content was written before the move is attempted.
                            assertEquals("never visible", Files.readString(source));
                            throw new IOException("simulated crash before the rename");
                        }));

        assertEquals("simulated crash before the rename", failure.getMessage());
        assertEquals("precious", Files.readString(target));
        assertOnlyFiles("project.logic");
    }

    @Test
    void aMissingFolderIsReportedWithoutCreatingAnything() {
        Path target = directory.resolve("missing").resolve("project.logic");

        IOException failure = assertThrows(IOException.class, () ->
                AtomicFileWriter.write(target, bytes("x"), AtomicFileWriter.Backup.NONE));

        assertTrue(failure.getMessage().contains("does not exist"), failure.getMessage());
        assertFalse(Files.exists(directory.resolve("missing")));
    }

    @Test
    void writingThroughASymbolicLinkKeepsTheLink() throws IOException {
        Path real = Files.createDirectory(directory.resolve("real")).resolve("project.logic");
        Files.writeString(real, "old");
        Path link = directory.resolve("link.logic");
        try {
            Files.createSymbolicLink(link, real);
        } catch (UnsupportedOperationException | IOException noLinks) {
            assumeTrue(false, "symbolic links are not supported here");
        }

        AtomicFileWriter.write(link, bytes("new"), AtomicFileWriter.Backup.NONE);

        assertTrue(Files.isSymbolicLink(link), "the link must not be replaced by a plain file");
        assertEquals("new", Files.readString(real));
    }

    @Test
    void thePermissionsOfTheReplacedFileAreKept() throws IOException {
        assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"));
        Path target = directory.resolve("private.logic");
        Files.writeString(target, "old");
        Set<PosixFilePermission> ownerOnly = PosixFilePermissions.fromString("rw-------");
        Files.setPosixFilePermissions(target, ownerOnly);

        AtomicFileWriter.write(target, bytes("new"), AtomicFileWriter.Backup.NONE);

        assertEquals(ownerOnly, Files.getPosixFilePermissions(target));
    }

    @Test
    void largeContentIsWrittenCompletely() throws IOException {
        byte[] content = new byte[5 * 1024 * 1024 + 17];
        for (int index = 0; index < content.length; index++) {
            content[index] = (byte) (index * 31);
        }
        Path target = directory.resolve("large.logic");

        AtomicFileWriter.write(target, content, AtomicFileWriter.Backup.NONE);

        assertArrayEquals(content, Files.readAllBytes(target));
    }

    private void assertOnlyFiles(String... expected) throws IOException {
        try (Stream<Path> files = Files.list(directory)) {
            assertEquals(Set.of(expected),
                    Set.copyOf(files.map(file -> file.getFileName().toString()).toList()),
                    "unexpected files (a leftover temporary file?)");
        }
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
