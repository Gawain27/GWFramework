/* SPDX-License-Identifier: Apache-2.0 */
package com.gwngames.core.util;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.stream.Stream;

/**
 * Ultra-small helper for <strong>efficient UTF-8 file I/O</strong> that
 * transparently handles<br>
 * &nbsp;• absolute paths<br>
 * &nbsp;• <em>relative paths resolved against the current working directory</em>
 *
 * <p>Every public method has a {@code String} overload that delegates to the
 * {@code Path} version after normalising the path.</p>
 */
public final class FileUtils {

    /* ═══════════════ path resolver (central place) ════════════════ */
    private static Path resolve(Path p) {// FIXME bean for path, also file logger
        return p.isAbsolute()
            ? p
            : Paths.get(System.getProperty("user.dir")).resolve(p).normalize();
    }
    private static Path resolve(String p) { return resolve(Path.of(p)); }

    /* ═══════════════ read helpers ════════════════ */

    /** Read whole file into a UTF-8 string. */
    public static String readUtf8(Path path) throws IOException {
        path = resolve(path);
        try (var ch = Files.newByteChannel(path, StandardOpenOption.READ)) {
            ByteBuffer buf = ByteBuffer.allocate((int) ch.size());

            int n;
            do { n = ch.read(buf); } while (n > 0);

            buf.flip();
            return StandardCharsets.UTF_8.decode(buf).toString();
        }
    }
    public static String readUtf8(String path) throws IOException { return readUtf8(resolve(path)); }

    /** Memory-map a file and return a UTF-8 string. */
    public static String mmapUtf8(Path path) throws IOException {
        path = resolve(path);
        try (var ch = FileChannel.open(path, StandardOpenOption.READ)) {
            ByteBuffer buf = ch.map(FileChannel.MapMode.READ_ONLY, 0, ch.size());
            return StandardCharsets.UTF_8.decode(buf).toString();
        }
    }
    public static String mmapUtf8(String path) throws IOException { return mmapUtf8(resolve(path)); }

    /** Stream lines lazily. */
    public static Stream<String> lines(Path path) throws IOException {
        return Files.lines(resolve(path), StandardCharsets.UTF_8);
    }
    public static Stream<String> lines(String path) throws IOException { return lines(resolve(path)); }

    /* ═══════════════ write helpers ════════════════ */

    public static void writeUtf8(Path path, String txt) throws IOException {
        path = resolve(path);
        Files.createDirectories(path.getParent());
        Files.writeString(path, txt, StandardCharsets.UTF_8,
            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }
    public static void writeUtf8(String path, String txt) throws IOException { writeUtf8(resolve(path), txt); }

    public static void appendUtf8(Path path, String txt) throws IOException {
        path = resolve(path);
        Files.writeString(path, txt, StandardCharsets.UTF_8,
            StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }
    public static void appendUtf8(String path, String txt) throws IOException { appendUtf8(resolve(path), txt); }

    /* ═══════════════ misc ════════════════ */

    public static void copy(Path src, Path dst, CopyOption... opts) throws IOException {
        dst = resolve(dst);
        Files.createDirectories(dst.getParent());
        Files.copy(resolve(src), dst, opts);
    }
    public static void copy(String src, String dst, CopyOption... opts) throws IOException {
        copy(resolve(src), resolve(dst), opts);
    }

    /** Read a class-path resource fully into a String. */
    public static String readResource(Class<?> anchor, String res) throws IOException {
        try (var in = anchor.getResourceAsStream(res)) {
            if (in == null) throw new IOException("No resource " + res);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private FileUtils() { /* utility – no instances */ }
}
