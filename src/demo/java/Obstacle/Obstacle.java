package Obstacle;

import java.awt.geom.Rectangle2D;

/** Portfolio fixture: only the bounds/alive contract consumed by PathFinder. */
public final class Obstacle {
    private final Rectangle2D bounds;
    private final boolean alive;

    public Obstacle(double x, double y, double width, double height) {
        this(x, y, width, height, true);
    }

    public Obstacle(double x, double y, double width, double height, boolean alive) {
        this.bounds = new Rectangle2D.Double(x, y, width, height);
        this.alive = alive;
    }

    public boolean isAlive() { return alive; }
    public Rectangle2D getBounds2D() { return (Rectangle2D) bounds.clone(); }
}
