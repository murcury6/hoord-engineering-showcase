import Cannon.CannonExpression;
import Enemy.PathFinder;
import Obstacle.Obstacle;
import Sounds.SoundsDevPanel;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import javax.swing.*;

/** Portfolio UI. Only the Sounds tab is the production panel. */
public final class Showcase {
    private static final Color BG = new Color(12, 17, 28);
    private static final Color FG = new Color(226, 235, 245);
    private static final Color MUTED = new Color(153, 173, 195);
    private static final Color ACCENT = new Color(89, 221, 201);

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Hoord | Engineering showcase");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setContentPane(createContent());
            frame.setSize(1200, 800);
            frame.setMinimumSize(new Dimension(900, 620));
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }

    public static JPanel createContent() {
        JPanel root = new JPanel(new BorderLayout(0, 18));
        root.setBorder(BorderFactory.createEmptyBorder(24, 24, 24, 24));
        root.setBackground(BG);
        JPanel heading = new JPanel(new GridLayout(2, 1, 0, 8));
        heading.setOpaque(false);
        heading.add(label("HOORD / Engineering showcase", 28, FG));
        heading.add(label("Jordyn Wood  ·  Java systems, desktop tools & procedural algorithms", 15, MUTED));
        root.add(heading, BorderLayout.NORTH);
        JTabbedPane tabs = new JTabbedPane(JTabbedPane.LEFT);
        tabs.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        tabs.addTab("1  AI Approval", new ApprovalDemo());
        tabs.addTab("2  Asset + Rounded 3D", privateStage("Asset workshop",
            "The development workflow covers ships, backgrounds, asteroids, effects and exhaust.\n\n"
            + "Asset inspection, editing and 3D preparation belong here. "
            + "The model implementations, learned geometry and production asset collection remain private."));
        tabs.addTab("3  Pre-Weapon", navigation());
        tabs.addTab("4  Finishing", privateStage("Finishing and integration",
            "Flight simulation, animation review and upgrade presentation help check content in context.\n\n"
            + "This stage is described here; the full simulator and private content are not included."));
        tabs.addTab("5  Weapons", expressions());
        tabs.addTab("6  Music", privateStage("Composition and review",
            "Music is composed, auditioned, reviewed and prepared for explicit promotion.\n\n"
            + "The composition systems, inference bridges, recordings and training material remain private."));
        SoundsDevPanel sounds = new SoundsDevPanel();
        tabs.addTab("7  Sounds", sounds);
        tabs.addTab("8  Weapon AI", privateStage("Private AI systems",
            "Hoord's weapon-generation and training implementation is intentionally withheld.\n\n"
            + "No model code, tensors, weights, checkpoints, adapters, datasets or private prompts are shipped. "
            + "The Weapons tab demonstrates a separate deterministic expression language."));
        tabs.addChangeListener(event -> sounds.setActive(tabs.getSelectedComponent() == sounds));
        tabs.setSelectedIndex(2);
        root.add(tabs, BorderLayout.CENTER);
        root.add(label("Portfolio demo  /  Synthetic inputs  /  Private AI stages are descriptive only", 12, MUTED), BorderLayout.SOUTH);
        return root;
    }

    private static JPanel privateStage(String title, String body) {
        JPanel panel = page(title, "PIPELINE WALKTHROUGH · IMPLEMENTATION PRIVATE");
        JTextArea description = new JTextArea(body);
        description.setLineWrap(true);
        description.setWrapStyleWord(true);
        description.setEditable(false);
        description.setOpaque(false);
        description.setForeground(FG);
        description.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 18));
        description.setBorder(BorderFactory.createEmptyBorder(20, 0, 0, 0));
        panel.add(description, BorderLayout.CENTER);
        return panel;
    }

    private static JPanel navigation() {
        JPanel panel = page("Enemy navigation", "ACTUAL HOORD PATHFINDER · SYNTHETIC SCENE");
        panel.add(new NavigationCanvas(), BorderLayout.CENTER);
        panel.add(label("Click to move target. Teal = clear detour; amber = obstructed fallback.", 12, MUTED), BorderLayout.SOUTH);
        return panel;
    }

    private static JPanel expressions() {
        JPanel panel = page("Weapon expression language", "ACTUAL HOORD PARSER · NO MODEL OR NETWORK");
        JPanel form = new JPanel(new BorderLayout(0, 16));
        form.setOpaque(false);
        form.setBorder(BorderFactory.createEmptyBorder(24, 0, 0, 0));
        JTextField formula = new JTextField("sin(pi / 2) * 10 + 2^3");
        formula.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 18));
        JPanel row = new JPanel(new BorderLayout(12, 0));
        row.setOpaque(false);
        row.add(formula, BorderLayout.CENTER);
        JButton evaluate = new JButton("Evaluate");
        row.add(evaluate, BorderLayout.EAST);
        JTextArea result = new JTextArea("Try arithmetic, conditionals and functions.\n\n"
            + "Examples:\n  clamp(12, 0, 10)\n  1 < 2 ? 42 : 0\n  length(vec2(3, 4))\n\n"
            + "Variables u, v and t are zero in this demo. rand() returns 0.5.");
        result.setEditable(false);
        result.setForeground(FG);
        result.setOpaque(false);
        result.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 16));
        ActionListener action = event -> {
            try {
                if (formula.getText().length() > 512) throw new IllegalArgumentException("Use at most 512 characters in this demo.");
                double value = CannonExpression.parse(formula.getText()).eval(new CannonExpression.Values() {
                    public double value(String name) {
                        if (name.equals("u") || name.equals("v") || name.equals("t")) return 0;
                        throw new IllegalArgumentException("Unknown demo variable: " + name);
                    }
                    public double random() { return 0.5; }
                });
                result.setText("Result: " + value);
            } catch (IllegalArgumentException error) { result.setText("Expression error: " + error.getMessage()); }
        };
        evaluate.addActionListener(action);
        formula.addActionListener(action);
        form.add(row, BorderLayout.NORTH);
        form.add(result, BorderLayout.CENTER);
        panel.add(form, BorderLayout.CENTER);
        return panel;
    }

    private static JPanel page(String title, String caption) {
        JPanel panel = new JPanel(new BorderLayout(0, 16));
        panel.setBackground(BG);
        panel.setBorder(BorderFactory.createEmptyBorder(28, 28, 28, 28));
        JPanel heading = new JPanel(new GridLayout(2, 1, 0, 8));
        heading.setOpaque(false);
        heading.add(label(caption, 11, ACCENT));
        heading.add(label(title, 26, FG));
        panel.add(heading, BorderLayout.NORTH);
        return panel;
    }

    private static JLabel label(String text, int size, Color color) {
        JLabel label = new JLabel(text);
        label.setForeground(color);
        label.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, size));
        return label;
    }

    private static final class NavigationCanvas extends JPanel {
        private final ArrayList<Obstacle> obstacles = new ArrayList<>();
        private final Point2D start = new Point2D.Double(90, 220);
        private Point2D target = new Point2D.Double(670, 220);
        NavigationCanvas() {
            setBackground(new Color(17, 25, 40));
            setPreferredSize(new Dimension(760, 450));
            obstacles.add(new Obstacle(275, 170, 100, 100));
            obstacles.add(new Obstacle(390, 175, 65, 90));
            addMouseListener(new MouseAdapter() {
                @Override public void mousePressed(MouseEvent event) {
                    target = new Point2D.Double(event.getX() * 760.0 / getWidth(), event.getY() * 450.0 / getHeight());
                    repaint();
                }
            });
        }
        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            g.scale(getWidth() / 760.0, getHeight() / 450.0);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(29, 40, 57));
            for (int x = 0; x < 760; x += 40) g.drawLine(x, 0, x, 450);
            for (int y = 0; y < 450; y += 40) g.drawLine(0, y, 760, y);
            g.setColor(new Color(103, 118, 143));
            for (Obstacle obstacle : obstacles) g.fill(obstacle.getBounds2D());
            g.setStroke(new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 10, new float[]{5, 5}, 0));
            g.setColor(MUTED);
            line(g, start, target);
            Point2D detour = PathFinder.pickDetour(start, target, obstacles, 60);
            boolean clear = !PathFinder.lineBlocked(start, detour, obstacles) && !PathFinder.lineBlocked(detour, target, obstacles);
            g.setColor(clear ? ACCENT : new Color(245, 181, 84));
            g.setStroke(new BasicStroke(3));
            line(g, start, detour);
            line(g, detour, target);
            dot(g, detour, 5);
            g.setColor(FG);
            dot(g, start, 8);
            g.drawString("ENEMY", (int)start.getX() - 20, (int)start.getY() + 28);
            g.setColor(new Color(239, 118, 137));
            dot(g, target, 8);
            g.drawString("TARGET", (int)target.getX() - 22, (int)target.getY() + 28);
            g.dispose();
        }
        private static void line(Graphics2D g, Point2D a, Point2D b) {
            g.draw(new java.awt.geom.Line2D.Double(a, b));
        }
        private static void dot(Graphics2D g, Point2D point, int radius) {
            g.fill(new java.awt.geom.Ellipse2D.Double(point.getX() - radius, point.getY() - radius, radius * 2, radius * 2));
        }
    }
}
