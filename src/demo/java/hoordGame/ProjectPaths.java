package hoordGame;

import java.nio.file.Path;

/** Portfolio-only path contract. Never discovers or opens a Hoord installation. */
public final class ProjectPaths {
    private ProjectPaths() {}
    public static Path userDataRoot() {
        return Path.of(".build", "demo-output").toAbsolutePath().normalize();
    }
}
