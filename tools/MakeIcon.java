import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Random;

/**
 * Generates icon.png: a round "map" split into three slices, Overworld grass, Nether netherrack and End stone,
 * each slice reaching a different distance from the eye in the middle (a separate render distance per
 * dimension). The dashed arc inside each slice is its simulation distance. Drawn at 64x64 and scaled to 128x128
 * so it stays crisp in Mod Menu.
 * Run from the project folder: java tools/MakeIcon.java
 */
public class MakeIcon {
    static final int N = 64;
    static final int[] PX = new int[N * N];
    static final Random RND = new Random(2615);

    static final double CX = 32, CY = 32;

    /** One slice of the map: angles in degrees (0 = right, clockwise, as on screen), render and simulation radius. */
    record Slice(double from, double to, double render, double simulation, int[] palette, int[] specks, int edge) {
        boolean contains(double angle) {
            double a = ((angle - from) % 360 + 360) % 360;
            return a < ((to - from) % 360 + 360) % 360;
        }
    }

    static final Slice OVERWORLD = new Slice(210, 330, 27, 17,
        new int[]{0xFF5E9E3F, 0xFF6BAC46, 0xFF548F37, 0xFF76B84E, 0xFF5A9A3B},
        new int[]{0xFF3F7A2A, 0xFFE8E04A, 0xFFD94848}, 0xFF9BD873);
    static final Slice NETHER = new Slice(330, 90, 16, 10,
        new int[]{0xFF6F2A2A, 0xFF803131, 0xFF5E2424, 0xFF8C3A33, 0xFF742C2C},
        new int[]{0xFFFF8A1F, 0xFFFFC23D, 0xFF4A1717}, 0xFFD8614E);
    static final Slice END = new Slice(90, 210, 21, 13,
        new int[]{0xFFDCDCA0, 0xFFE6E6B0, 0xFFCFCF92, 0xFFD9D89E, 0xFFE1E1A8},
        new int[]{0xFFB9B97C, 0xFF9C6BB5, 0xFFC4C487}, 0xFFFFFFD6);
    static final Slice[] SLICES = {OVERWORLD, NETHER, END};

    public static void main(String[] args) throws Exception {
        badgeBackground(0xFF242A3E, 0xFF12151F);
        stars();
        radarRings();
        slices();
        eye(CX, CY);
        badgeFrame();
        write(scale(2), "src/main/resources/assets/dimrender/icon.png");
    }

    // ---------------------------------------------------------------- picture

    static void stars() {
        for (int i = 0; i < 18; i++) {
            int x = 4 + RND.nextInt(N - 8), y = 4 + RND.nextInt(N - 8);
            if (Math.hypot(x + 0.5 - CX, y + 0.5 - CY) > 29) set(x, y, RND.nextBoolean() ? 0x90FFFFFF : 0x50C9B7FF);
        }
    }

    /** A faint dashed circle: the longest slice reaches it, the others fall short of it. */
    static void radarRings() {
        for (double r : new double[]{27.5}) {
            for (int y = 0; y < N; y++) {
                for (int x = 0; x < N; x++) {
                    double dx = x + 0.5 - CX, dy = y + 0.5 - CY;
                    if (Math.abs(Math.hypot(dx, dy) - r) > 0.55) continue;
                    double along = (Math.atan2(dy, dx) + Math.PI) * r;
                    if (((int) Math.floor(along / 2)) % 2 == 0) set(x, y, 0x46FFFFFF);
                }
            }
        }
    }

    static Slice sliceAt(double angle) {
        for (Slice s : SLICES) {
            if (s.contains(angle)) return s;
        }
        return OVERWORLD;
    }

    static void slices() {
        // texture: 2x2 "block" cells, so it reads like a top-down map
        int[][] cell = new int[N / 2][N / 2];
        for (int[] row : cell) {
            for (int i = 0; i < row.length; i++) row[i] = RND.nextInt(1000);
        }
        // a drop shadow first, then the slices
        for (int y = 0; y < N; y++) {
            for (int x = 0; x < N; x++) {
                double dx = x + 0.5 - CX - 1.5, dy = y + 0.5 - CY - 1.5;
                double d = Math.hypot(dx, dy);
                if (d <= sliceAt(Math.toDegrees(Math.atan2(dy, dx))).render + 0.5) set(x, y, 0x60000000);
            }
        }
        for (int y = 0; y < N; y++) {
            for (int x = 0; x < N; x++) {
                double dx = x + 0.5 - CX, dy = y + 0.5 - CY;
                double d = Math.hypot(dx, dy);
                double angle = Math.toDegrees(Math.atan2(dy, dx));
                Slice s = sliceAt(angle);
                if (d > s.render + 1.2) continue;
                if (d > s.render) {
                    set(x, y, 0xFF0B0D14); // outer outline
                    continue;
                }
                int r = cell[y / 2][x / 2];
                int c = r < 965 ? s.palette[r % s.palette.length] : s.specks[r % s.specks.length];
                // chunk grid every 8 pixels from the centre
                if (((x - (int) CX) & 7) == 0 || ((y - (int) CY) & 7) == 0) c = mix(c, 0xFF000000, 0.09);
                // outside the simulation distance the world is drawn but not running: a little darker
                if (d > s.simulation) c = mix(c, 0xFF000000, 0.16);
                // lit rim just inside the edge
                if (d > s.render - 1.1) c = mix(c, s.edge, 0.35);
                set(x, y, c);
            }
        }
        // dashed simulation-distance arcs
        for (Slice s : SLICES) {
            for (int y = 0; y < N; y++) {
                for (int x = 0; x < N; x++) {
                    double dx = x + 0.5 - CX, dy = y + 0.5 - CY;
                    double d = Math.hypot(dx, dy);
                    double angle = Math.toDegrees(Math.atan2(dy, dx));
                    if (!s.contains(angle) || Math.abs(d - s.simulation) > 0.62) continue;
                    double along = Math.toRadians(((angle - s.from) % 360 + 360) % 360) * s.simulation;
                    if (((int) Math.floor(along / 2)) % 2 == 0) set(x, y, 0xF0FFFFFF);
                }
            }
        }
        // borders between the slices, as long as the longer of the two
        for (Slice s : SLICES) {
            double longest = Math.max(s.render, sliceAt(s.from - 1).render);
            double a = Math.toRadians(s.from);
            for (double t = 0; t <= longest + 0.5; t += 0.25) {
                set((int) Math.floor(CX + Math.cos(a) * t), (int) Math.floor(CY + Math.sin(a) * t), 0xFF0B0D14);
            }
        }
    }

    /** An eye in the middle: the player looking out. */
    static void eye(double cx, double cy) {
        double w = 6.6, h = 4.0;
        boolean[][] inside = new boolean[N][N];
        for (int y = 0; y < N; y++) {
            for (int x = 0; x < N; x++) {
                double u = (x + 0.5 - cx) / w, v = (y + 0.5 - cy) / h;
                inside[y][x] = Math.abs(u) < 1 && Math.abs(v) <= (1 - u * u);
            }
        }
        for (int y = 1; y < N - 1; y++) {
            for (int x = 1; x < N - 1; x++) {
                if (inside[y][x]) continue;
                if (inside[y - 1][x] || inside[y + 1][x] || inside[y][x - 1] || inside[y][x + 1]) set(x, y, 0xFF0B0D14);
            }
        }
        for (int y = 0; y < N; y++) {
            for (int x = 0; x < N; x++) {
                if (!inside[y][x]) continue;
                double d = Math.hypot(x + 0.5 - cx, y + 0.5 - cy);
                int c = y + 0.5 < cy ? 0xFFFFFFFF : 0xFFE4E8F2;
                if (d < 3.3) c = d < 1.6 ? 0xFF0B0D14 : (y + 0.5 < cy ? 0xFF4FA3FF : 0xFF2F6FD0);
                set(x, y, c);
            }
        }
        set((int) cx - 2, (int) cy - 2, 0xFFFFFFFF); // glint
    }

    // ---------------------------------------------------------------- shared with the other mods' icon tools

    // ---------------------------------------------------------------- badge

    static final int CORNER = 8;

    /** Is the pixel inside the rounded square, shrunk by {@code inset} pixels? */
    static boolean inBadge(int x, int y, double inset) {
        double px = x + 0.5, py = y + 0.5;
        double lo = inset, hi = N - inset, r = Math.max(1, CORNER - inset);
        if (px < lo || py < lo || px > hi || py > hi) return false;
        double cx = Math.max(lo + r, Math.min(hi - r, px));
        double cy = Math.max(lo + r, Math.min(hi - r, py));
        return (px - cx) * (px - cx) + (py - cy) * (py - cy) <= r * r;
    }

    static void badgeBackground(int top, int bottom) {
        for (int y = 0; y < N; y++) {
            int c = mix(top, bottom, y / (double) (N - 1));
            for (int x = 0; x < N; x++) {
                if (inBadge(x, y, 0)) PX[y * N + x] = c;
            }
        }
        // a few faint dots so the background is not flat
        for (int i = 0; i < 40; i++) {
            int x = RND.nextInt(N), y = RND.nextInt(N);
            if (inBadge(x, y, 3)) set(x, y, 0x18FFFFFF);
        }
    }

    /** 2px dark rim, a lit inner edge at the top left and a shaded one at the bottom right; outside is cleared. */
    static void badgeFrame() {
        for (int y = 0; y < N; y++) {
            for (int x = 0; x < N; x++) {
                int i = y * N + x;
                if (!inBadge(x, y, 0)) {
                    PX[i] = 0;
                } else if (!inBadge(x, y, 2)) {
                    PX[i] = 0xFF0A0B12;
                } else if (!inBadge(x, y, 3)) {
                    boolean lit = x + y < N - 1;
                    PX[i] = blend(PX[i], lit ? 0x40FFFFFF : 0x50000000);
                }
            }
        }
    }

    // ---------------------------------------------------------------- drawing helpers

    static void set(int x, int y, int argb) {
        if (x < 0 || y < 0 || x >= N || y >= N) return;
        PX[y * N + x] = blend(PX[y * N + x], argb);
    }

    static int blend(int dst, int src) {
        int sa = src >>> 24;
        if (sa == 255) return src;
        if (sa == 0) return dst;
        int da = dst >>> 24;
        double a = sa / 255.0, b = da / 255.0 * (1 - a), out = a + b;
        if (out <= 0) return 0;
        int r = (int) Math.round((((src >> 16) & 255) * a + ((dst >> 16) & 255) * b) / out);
        int g = (int) Math.round((((src >> 8) & 255) * a + ((dst >> 8) & 255) * b) / out);
        int bl = (int) Math.round(((src & 255) * a + (dst & 255) * b) / out);
        return ((int) Math.round(out * 255) << 24) | (r << 16) | (g << 8) | bl;
    }

    static int mix(int a, int b, double t) {
        t = Math.max(0, Math.min(1, t));
        int r = (int) Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) Math.round((a & 255) * (1 - t) + (b & 255) * t);
        return (a & 0xFF000000) | (r << 16) | (g << 8) | bl;
    }

    static BufferedImage scale(int factor) {
        BufferedImage img = new BufferedImage(N * factor, N * factor, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < N * factor; y++) {
            for (int x = 0; x < N * factor; x++) img.setRGB(x, y, PX[(y / factor) * N + x / factor]);
        }
        return img;
    }

    static void write(BufferedImage img, String path) throws Exception {
        File out = new File(path);
        out.getParentFile().mkdirs();
        ImageIO.write(img, "png", out);
        System.out.println("Wrote " + out);
    }

}
