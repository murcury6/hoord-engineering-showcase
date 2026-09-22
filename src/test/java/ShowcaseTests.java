import Cannon.CannonExpression;
import Enemy.PathFinder;
import Obstacle.Obstacle;
import Sounds.SoundEffectGenerator;
import hoordGame.BoundedCache;
import hoordGame.ContentHash;
import java.awt.geom.Point2D;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import javax.swing.SwingUtilities;

/** Standalone regression checks with synthetic inputs; no private project fixtures. */
public final class ShowcaseTests {
    private static int checks;
    private static void check(boolean condition, String description) {
        checks++;
        if (!condition) throw new AssertionError(description);
    }
    public static void main(String[] args) throws Exception {
        Point2D start = new Point2D.Double(90, 220), target = new Point2D.Double(670, 220);
        ArrayList<Obstacle> obstacles = new ArrayList<>();
        check(PathFinder.pickDetour(start, target, obstacles, 60).equals(target), "Unobstructed target");
        obstacles.add(new Obstacle(275, 170, 100, 100));
        obstacles.add(new Obstacle(390, 175, 65, 90));
        check(PathFinder.lineBlocked(start, target, obstacles), "Direct line blocked");
        Point2D detour = PathFinder.pickDetour(start, target, obstacles, 60);
        check(!detour.equals(target), "Cluster requires detour");
        check(!PathFinder.lineBlocked(start, detour, obstacles), "First detour leg clear");
        check(!PathFinder.lineBlocked(detour, target, obstacles), "Second detour leg clear");
        Point2D previous = detour;
        obstacles.add(new Obstacle(160, 380, 20, 20));
        check(PathFinder.pickDetour(start, target, obstacles, 60).equals(previous), "Unrelated obstacle excluded from cluster");
        ArrayList<Obstacle> dead = new ArrayList<>();
        dead.add(new Obstacle(100, 0, 400, 400, false));
        check(!PathFinder.lineBlocked(start, target, dead), "Dead obstacles ignored");
        check(PathFinder.pickDetour(start, start, obstacles, 60).equals(start), "Coincident endpoints");

        CannonExpression.Values values = new CannonExpression.Values() {
            public double value(String name) { return 0; }
            public double random() { throw new AssertionError("Lazy branch executed"); }
        };
        check(CannonExpression.parse("2 + 3 * 4").eval(values) == 14, "Precedence");
        check(CannonExpression.parse("2^3^2").eval(values) == 512, "Right-associative powers");
        check(CannonExpression.parse("true ? 42 : rand()").eval(values) == 42, "Lazy conditional");
        check(CannonExpression.parse("length(vec2(3,4))").eval(values) == 5, "Vector shorthand");
        check(CannonExpression.parse("10 / 0").eval(values) == 0, "Finite zero-division result");
        boolean invalid = false;
        try { CannonExpression.parse("sin("); } catch (IllegalArgumentException expected) { invalid = true; }
        check(invalid, "Malformed expression rejected");

        for (SoundEffectGenerator.Kind kind : SoundEffectGenerator.Kind.values()) {
            var parameters = new SoundEffectGenerator.Parameters(kind, 0.1, 220, 0.7, 42);
            var first = SoundEffectGenerator.generate(parameters);
            var second = SoundEffectGenerator.generate(parameters);
            check(Arrays.equals(first.samples(), second.samples()), "Repeatable " + kind);
            check(first.samples().length == 4410, "Sample count " + kind);
            double peak = 0;
            for (float sample : first.samples()) {
                if (!Float.isFinite(sample)) throw new AssertionError("Nonfinite sample");
                peak = Math.max(peak, Math.abs(sample));
            }
            check(peak <= 0.63001 && peak > 0, "Headroom " + kind);
            check(first.samples()[0] == 0 && first.samples()[4409] == 0, "Silent endpoints " + kind);
        }
        Path temp = Files.createTempDirectory("hoord-showcase-test-");
        Path wav = temp.resolve("synthetic.wav");
        try {
            var result = SoundEffectGenerator.generate(new SoundEffectGenerator.Parameters(SoundEffectGenerator.Kind.PICKUP, 0.1, 880, 0.5, 9));
            SoundEffectGenerator.writeWav(result, wav);
            byte[] data = Files.readAllBytes(wav);
            check(data.length == 44 + 4410 * 2, "PCM16 header and payload length");
            check(new String(data, 0, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("RIFF"), "WAV RIFF header");
            boolean overwrite = false;
            try { SoundEffectGenerator.writeWav(result, wav); } catch (java.nio.file.FileAlreadyExistsException expected) { overwrite = true; }
            check(overwrite, "Existing WAV protected");
        } finally { Files.deleteIfExists(wav); Files.delete(temp); }
        BoundedCache<String, Integer> cache = new BoundedCache<>(4, 0.75f, true, 2);
        cache.put("a", 1); cache.put("b", 2); cache.get("a"); cache.put("c", 3);
        check(cache.containsKey("a") && !cache.containsKey("b") && cache.size() == 2, "LRU eviction");
        check(ContentHash.sha256("abc").equals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"), "SHA-256 standard vector");
        SwingUtilities.invokeAndWait(() -> {
            var panel = Showcase.createContent();
            panel.setSize(1200, 800);
            panel.doLayout();
            check(panel.getComponentCount() == 3, "Demo panel constructs headlessly");
        });
        System.out.println("PASS: " + checks + " checks (navigation, parser, synthesis, export, utilities, UI construction)");
    }
}
