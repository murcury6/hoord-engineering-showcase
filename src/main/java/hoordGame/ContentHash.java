package hoordGame;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

/** SHA-256 content identities for generated assets, training inputs and packages. */
public final class ContentHash {
    private ContentHash() {}

    /** Streams files so hashing a large asset does not load it all into memory. */
    public static String sha256(Path file) throws IOException {
        MessageDigest digest = newDigest();
        try (var input = Files.newInputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            for (int read; (read = input.read(buffer)) >= 0;) {
                if (read > 0) digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    public static String sha256(byte[] bytes) {
        return HexFormat.of().formatHex(newDigest().digest(bytes));
    }

    public static String sha256(String text) {
        return sha256(text.getBytes(StandardCharsets.UTF_8));
    }

    /** Composer source records retain their existing uppercase identity format. */
    public static String sha256Upper(Path file) throws IOException {
        return sha256(file).toUpperCase(Locale.ROOT);
    }

    public static String sha256Upper(byte[] bytes) {
        return sha256(bytes).toUpperCase(Locale.ROOT);
    }

    private static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
