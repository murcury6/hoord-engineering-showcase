package Enemy;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;

import Obstacle.Obstacle;

/**
 * Lightweight detour helper.
 *
 * Keeps the original simple detour idea, but treats tight blockers like one cluster.
 * Important: cluster growth only uses obstacles that also block the current player line,
 * so enemies do not start steering toward random nearby obstacles.
 */
public class PathFinder {

    private static final double EPS = 0.00001;

    public PathFinder() {}

    public static Point2D pickDetour(Point2D from, Point2D to, ArrayList<Obstacle> obstacles, double stepDist) {
        if (from == null || to == null) return to;
        return pickDetour(from.getX(), from.getY(), to.getX(), to.getY(), obstacles, stepDist);
    }

    public static Point2D pickDetour(double fromX, double fromY, double toX, double toY,
                                     ArrayList<Obstacle> obstacles, double stepDist) {
        Point2D from = new Point2D.Double(fromX, fromY);
        Point2D to = new Point2D.Double(toX, toY);
        if (!lineBlocked(fromX, fromY, toX, toY, obstacles)) return to;

        double dx = to.getX() - from.getX();
        double dy = to.getY() - from.getY();
        double len = Math.sqrt(dx * dx + dy * dy);
        if (len <= EPS) return to;

        double ux = dx / len;
        double uy = dy / len;
        double nx = -uy;
        double ny = ux;

        double base = Math.max(40.0, stepDist);
        double joinGap = Math.max(18.0, base * 0.60);
        double margin = Math.max(16.0, base * 0.45);

        ArrayList<Obstacle> blocking = getBlockingObstacles(from, to, obstacles);
        Obstacle seed = firstBlockingObstacle(from, to, blocking);
        if (seed == null) {
            return simpleFallback(from, to, obstacles, base, ux, uy, nx, ny);
        }

        ArrayList<Obstacle> cluster = buildCluster(seed, blocking, joinGap);
        Rectangle2D merged = mergeClusterBounds(cluster);
        if (merged == null) {
            return simpleFallback(from, to, obstacles, base, ux, uy, nx, ny);
        }

        Rectangle2D expanded = new Rectangle2D.Double(
                merged.getX() - margin,
                merged.getY() - margin,
                merged.getWidth() + margin * 2.0,
                merged.getHeight() + margin * 2.0
        );

        double[][] corners = {
                { expanded.getMinX(), expanded.getMinY() },
                { expanded.getMinX(), expanded.getMaxY() },
                { expanded.getMaxX(), expanded.getMinY() },
                { expanded.getMaxX(), expanded.getMaxY() }
        };

        double maxAhead = Double.NEGATIVE_INFINITY;
        double minAhead = Double.POSITIVE_INFINITY;
        double maxSide = Double.NEGATIVE_INFINITY;
        double minSide = Double.POSITIVE_INFINITY;

        for (int i = 0; i < corners.length; i++) {
            double rx = corners[i][0] - from.getX();
            double ry = corners[i][1] - from.getY();

            double ahead = rx * ux + ry * uy;
            double side = rx * nx + ry * ny;

            if (ahead > maxAhead) maxAhead = ahead;
            if (ahead < minAhead) minAhead = ahead;
            if (side > maxSide) maxSide = side;
            if (side < minSide) minSide = side;
        }

        double aheadFar = Math.max(base, maxAhead + margin);
        double aheadNear = Math.max(base * 0.75, minAhead - margin);

        Point2D[] candidates = new Point2D[] {
                pointAlong(from, ux, uy, nx, ny, aheadFar,  maxSide + margin),
                pointAlong(from, ux, uy, nx, ny, aheadFar,  minSide - margin),
                pointAlong(from, ux, uy, nx, ny, aheadNear, maxSide + margin),
                pointAlong(from, ux, uy, nx, ny, aheadNear, minSide - margin),
                pointAlong(from, ux, uy, nx, ny, base,      maxSide + margin),
                pointAlong(from, ux, uy, nx, ny, base,      minSide - margin)
        };

        Point2D best = null;
        double bestScore = Double.POSITIVE_INFINITY;

        for (int i = 0; i < candidates.length; i++) {
            Point2D c = candidates[i];
            if (c == null) continue;
            if (lineBlocked(from, c, obstacles)) continue;
            if (lineBlocked(c, to, obstacles)) continue;

            double forward = (c.getX() - from.getX()) * ux + (c.getY() - from.getY()) * uy;
            if (forward <= 0.0) continue;

            double score = from.distance(c) + c.distance(to);
            if (score < bestScore) {
                bestScore = score;
                best = c;
            }
        }

        if (best != null) return best;

        for (int i = 0; i < candidates.length; i++) {
            Point2D c = candidates[i];
            if (c == null) continue;
            if (lineBlocked(from, c, obstacles)) continue;

            double forward = (c.getX() - from.getX()) * ux + (c.getY() - from.getY()) * uy;
            if (forward > 0.0) return c;
        }

        return simpleFallback(from, to, obstacles, Math.max(base, maxSide - minSide + margin), ux, uy, nx, ny);
    }

    public static boolean lineBlocked(Point2D from, Point2D to, ArrayList<Obstacle> obstacles) {
        if (from == null || to == null) return false;
        return lineBlocked(from.getX(), from.getY(), to.getX(), to.getY(), obstacles);
    }

    public static boolean lineBlocked(double fromX, double fromY, double toX, double toY, ArrayList<Obstacle> obstacles) {
        if (obstacles == null || obstacles.isEmpty()) return false;

        double minX = Math.min(fromX, toX);
        double maxX = Math.max(fromX, toX);
        double minY = Math.min(fromY, toY);
        double maxY = Math.max(fromY, toY);
        for (int i = 0; i < obstacles.size(); i++) {
            Obstacle o = obstacles.get(i);
            if (o == null || !o.isAlive()) continue;
            try {
                Rectangle2D b = o.getBounds2D();
                if (b == null) continue;
                if (b.getMaxX() < minX || b.getMinX() > maxX || b.getMaxY() < minY || b.getMinY() > maxY) {
                    continue;
                }
                if (b.intersectsLine(fromX, fromY, toX, toY)) {
                    return true;
                }
            } catch (Throwable ignore) {}
        }
        return false;
    }

    private static ArrayList<Obstacle> getBlockingObstacles(Point2D from, Point2D to, ArrayList<Obstacle> obstacles) {
        ArrayList<Obstacle> blocking = new ArrayList<>();
        if (from == null || to == null || obstacles == null) return blocking;

        double minX = Math.min(from.getX(), to.getX());
        double maxX = Math.max(from.getX(), to.getX());
        double minY = Math.min(from.getY(), to.getY());
        double maxY = Math.max(from.getY(), to.getY());

        for (int i = 0; i < obstacles.size(); i++) {
            Obstacle o = obstacles.get(i);
            if (o == null || !o.isAlive()) continue;
            try {
                Rectangle2D b = o.getBounds2D();
                if (b == null) continue;
                if (b.getMaxX() < minX || b.getMinX() > maxX || b.getMaxY() < minY || b.getMinY() > maxY) {
                    continue;
                }
                if (b.intersectsLine(from.getX(), from.getY(), to.getX(), to.getY())) {
                    blocking.add(o);
                }
            } catch (Throwable ignore) {}
        }
        return blocking;
    }

    private static Point2D simpleFallback(Point2D from, Point2D to, ArrayList<Obstacle> obstacles,
                                          double dist, double ux, double uy, double nx, double ny) {
        Point2D left = pointAlong(from, ux, uy, nx, ny, dist * 0.50, dist);
        if (!lineBlocked(from, left, obstacles) && !lineBlocked(left, to, obstacles)) return left;

        Point2D right = pointAlong(from, ux, uy, nx, ny, dist * 0.50, -dist);
        if (!lineBlocked(from, right, obstacles) && !lineBlocked(right, to, obstacles)) return right;

        if (!lineBlocked(from, left, obstacles)) return left;
        if (!lineBlocked(from, right, obstacles)) return right;
        return to;
    }

    private static Point2D pointAlong(Point2D from, double ux, double uy, double nx, double ny,
                                      double ahead, double side) {
        return new Point2D.Double(
                from.getX() + ux * ahead + nx * side,
                from.getY() + uy * ahead + ny * side
        );
    }

    private static Obstacle firstBlockingObstacle(Point2D from, Point2D to, ArrayList<Obstacle> blocking) {
        if (from == null || to == null || blocking == null) return null;

        Obstacle best = null;
        double bestT = Double.POSITIVE_INFINITY;

        double dx = to.getX() - from.getX();
        double dy = to.getY() - from.getY();
        double lenSq = dx * dx + dy * dy;
        if (lenSq <= EPS) return null;

        double minX = Math.min(from.getX(), to.getX());
        double maxX = Math.max(from.getX(), to.getX());
        double minY = Math.min(from.getY(), to.getY());
        double maxY = Math.max(from.getY(), to.getY());

        for (int i = 0; i < blocking.size(); i++) {
            Obstacle o = blocking.get(i);
            if (o == null || !o.isAlive()) continue;
            try {
                Rectangle2D b = o.getBounds2D();
                if (b == null) continue;
                if (b.getMaxX() < minX || b.getMinX() > maxX || b.getMaxY() < minY || b.getMinY() > maxY) {
                    continue;
                }

                double cx = b.getCenterX() - from.getX();
                double cy = b.getCenterY() - from.getY();
                double t = (cx * dx + cy * dy) / lenSq;

                if (t >= 0.0 && t <= 1.0 && t < bestT) {
                    bestT = t;
                    best = o;
                }
            } catch (Throwable ignore) {}
        }

        return best;
    }

    private static ArrayList<Obstacle> buildCluster(Obstacle seed, ArrayList<Obstacle> blocking, double joinGap) {
        ArrayList<Obstacle> cluster = new ArrayList<>();
        if (seed == null) return cluster;

        cluster.add(seed);
        boolean changed = true;

        while (changed) {
            changed = false;

            for (int i = 0; i < blocking.size(); i++) {
                Obstacle test = blocking.get(i);
                if (test == null || !test.isAlive() || cluster.contains(test)) continue;

                for (int j = 0; j < cluster.size(); j++) {
                    if (boundsGap(cluster.get(j), test) <= joinGap) {
                        cluster.add(test);
                        changed = true;
                        break;
                    }
                }
            }
        }

        return cluster;
    }

    private static Rectangle2D mergeClusterBounds(ArrayList<Obstacle> cluster) {
        Rectangle2D merged = null;
        if (cluster == null) return null;

        for (int i = 0; i < cluster.size(); i++) {
            Obstacle o = cluster.get(i);
            if (o == null || !o.isAlive()) continue;
            try {
                Rectangle2D b = o.getBounds2D();
                if (b == null) continue;
                if (merged == null) {
                    merged = new Rectangle2D.Double(b.getX(), b.getY(), b.getWidth(), b.getHeight());
                } else {
                    Rectangle2D.union(merged, b, merged);
                }
            } catch (Throwable ignore) {}
        }

        return merged;
    }

    private static double boundsGap(Obstacle a, Obstacle b) {
        try {
            Rectangle2D ra = a.getBounds2D();
            Rectangle2D rb = b.getBounds2D();
            if (ra == null || rb == null) return Double.POSITIVE_INFINITY;

            double dx = 0.0;
            if (ra.getMaxX() < rb.getMinX()) dx = rb.getMinX() - ra.getMaxX();
            else if (rb.getMaxX() < ra.getMinX()) dx = ra.getMinX() - rb.getMaxX();

            double dy = 0.0;
            if (ra.getMaxY() < rb.getMinY()) dy = rb.getMinY() - ra.getMaxY();
            else if (rb.getMaxY() < ra.getMinY()) dy = ra.getMinY() - rb.getMaxY();

            return Math.sqrt(dx * dx + dy * dy);
        } catch (Throwable ignore) {
            return Double.POSITIVE_INFINITY;
        }
    }
}
