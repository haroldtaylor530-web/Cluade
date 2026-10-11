package com.harold.realisticsurvival.client;

import com.harold.realisticsurvival.RealisticSurvival;
import com.harold.realisticsurvival.survival.OreMining;
import com.mojang.blaze3d.platform.InputConstants;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * The ore up close: its own 16x16 texture, one cell per pixel. Your pickaxe starts outside the
 * block and can walk around its edge and along any tunnel you dig.
 * <ul>
 * <li>WASD / arrow keys: move (or turn, if the way is blocked)</li>
 * <li>Space: swing hard. Breaks rock (a better pickaxe breaks more), but smashes ore to dust.</li>
 * <li>Shift: tap gently. Prises a whole piece of ore out cleanly; only chips rock.</li>
 * <li>Enter: stop here and keep what you've got.</li>
 * </ul>
 */
public class MiningScreen extends Screen {
    static final int N = 16;
    private static final int CELL = 9;
    private static final int EMPTY = 0, ROCK = 1, ORE = 2;
    private static final int INTACT = 0, COLLECTED = 1, SMASHED = 2;
    private static final Identifier ROCK_TEX = RealisticSurvival.id("textures/block/rock.png");

    private final BlockPos pos;
    private final int power;
    private final int rockHp;
    private final long timeLimitMs;
    private final Component oreName;

    private final int[][] type = new int[N][N];
    private final int[][] hp = new int[N][N];
    private final int[][] colour = new int[N][N];
    private final int[][] piece = new int[N][N];
    private final List<Integer> pieceSize = new ArrayList<>();
    private final List<Integer> pieceState = new ArrayList<>();
    private final List<Integer> pieceColour = new ArrayList<>();
    int totalOre, collected, smashed, actions;

    // the pickaxe: x, y in -1..16 (outside the block is the ring around it)
    int px = N / 2, py = -1;
    int fx = 0, fy = 1;
    private long readyAt, swingUntil, startMs = -1, endAt = -1;
    private boolean resolved;
    private String flash = "";
    private long flashUntil;
    private int flashColour;
    private final List<float[]> bits = new ArrayList<>(); // x, y, vx, vy, life, colour
    private final Random random = new Random();
    private long last;

    public MiningScreen(OreMining.OpenMining open) {
        super(Component.translatable("gui.realisticsurvival.mining.title"));
        this.pos = open.pos();
        this.power = open.power();
        this.rockHp = open.rock() == 1 ? 5 : open.rock() == 2 ? 2 : 3;
        this.timeLimitMs = open.timeTicks() * 50L;
        Identifier blockId = Identifier.parse(open.block());
        this.oreName = BuiltInRegistries.BLOCK.getValue(blockId).getName();
        build(blockId, open.rock());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---- reading the ore's texture -------------------------------------------------------------

    private static BufferedImage read(Identifier id) {
        var res = Minecraft.getInstance().getResourceManager().getResource(id);
        if (res.isEmpty()) return null;
        try (InputStream in = res.get().open()) {
            return ImageIO.read(in);
        } catch (Exception e) {
            return null;
        }
    }

    /** One 16x16 frame of a texture, any resolution, as RGB ints (0 for transparent). */
    private static int[][] frame(BufferedImage img) {
        int[][] out = new int[N][N];
        int w = img.getWidth();
        for (int y = 0; y < N; y++) for (int x = 0; x < N; x++) {
            int argb = img.getRGB(x * w / N, y * w / N);
            out[x][y] = (argb >>> 24) < 128 ? 0 : (argb & 0xFFFFFF);
        }
        return out;
    }

    private static int dist(int a, int b) {
        return Math.abs((a >> 16 & 255) - (b >> 16 & 255)) + Math.abs((a >> 8 & 255) - (b >> 8 & 255)) + Math.abs((a & 255) - (b & 255));
    }

    private void build(Identifier blockId, int rock) {
        BufferedImage img = read(blockId.withPath(p -> "textures/block/" + p + ".png"));
        if (img == null) img = read(blockId.withPath(p -> "textures/block/" + p + "_side.png"));
        int[][] px;
        if (img != null) {
            px = frame(img);
        } else {
            px = generated(blockId.hashCode());
        }
        for (int y = 0; y < N; y++) for (int x = 0; x < N; x++) colour[x][y] = px[x][y];

        // the rock's own colours: from the plain stone texture the ore was painted on
        String base = rock == 1 ? "deepslate" : rock == 2 ? "netherrack" : "stone";
        BufferedImage baseImg = read(Identifier.withDefaultNamespace("textures/block/" + base + ".png"));
        Set<Integer> palette = new HashSet<>();
        if (baseImg != null) {
            int[][] b = frame(baseImg);
            for (int[] row : b) for (int c : row) palette.add(c);
        }
        boolean[][] ore = classify(palette);
        int count = 0;
        for (boolean[] row : ore) for (boolean o : row) if (o) count++;
        if (palette.isEmpty() || count > 200 || count < 4) {
            // not painted on plain rock: call its most common colours the rock instead
            Map<Integer, Integer> freq = new HashMap<>();
            for (int[] row : px) for (int c : row) freq.merge(c, 1, Integer::sum);
            List<Map.Entry<Integer, Integer>> sorted = new ArrayList<>(freq.entrySet());
            sorted.sort((a, b) -> b.getValue() - a.getValue());
            palette.clear();
            int covered = 0;
            for (var e : sorted) {
                if (covered >= N * N / 2) break;
                palette.add(e.getKey());
                covered += e.getValue();
            }
            ore = classify(palette);
        }

        // pieces of ore: connected groups; lone specks count as rock
        for (int[] row : piece) java.util.Arrays.fill(row, -1);
        for (int y = 0; y < N; y++) for (int x = 0; x < N; x++) {
            if (!ore[x][y] || piece[x][y] >= 0) continue;
            int id = pieceSize.size();
            List<int[]> cells = new ArrayList<>();
            ArrayDeque<int[]> todo = new ArrayDeque<>();
            todo.add(new int[] { x, y });
            piece[x][y] = id;
            long r = 0, g = 0, b = 0;
            while (!todo.isEmpty()) {
                int[] c = todo.poll();
                cells.add(c);
                int col = colour[c[0]][c[1]];
                r += col >> 16 & 255;
                g += col >> 8 & 255;
                b += col & 255;
                for (int[] d : new int[][] { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } }) {
                    int nx = c[0] + d[0], ny = c[1] + d[1];
                    if (nx >= 0 && ny >= 0 && nx < N && ny < N && ore[nx][ny] && piece[nx][ny] < 0) {
                        piece[nx][ny] = id;
                        todo.add(new int[] { nx, ny });
                    }
                }
            }
            if (cells.size() < 2) {
                for (int[] c : cells) piece[c[0]][c[1]] = -2;
                pieceSize.add(0);
                pieceState.add(SMASHED);
                pieceColour.add(0);
                continue;
            }
            pieceSize.add(cells.size());
            pieceState.add(INTACT);
            int n = cells.size();
            pieceColour.add((int) (r / n) << 16 | (int) (g / n) << 8 | (int) (b / n));
            totalOre += n;
        }
        for (int y = 0; y < N; y++) for (int x = 0; x < N; x++) {
            boolean isOre = piece[x][y] >= 0;
            type[x][y] = isOre ? ORE : ROCK;
            hp[x][y] = isOre ? 1 : rockHp;
        }
    }

    private boolean[][] classify(Set<Integer> palette) {
        boolean[][] ore = new boolean[N][N];
        for (int y = 0; y < N; y++) for (int x = 0; x < N; x++) {
            int c = colour[x][y];
            int best = Integer.MAX_VALUE;
            for (int p : palette) best = Math.min(best, dist(c, p));
            ore[x][y] = best > 24;
        }
        return ore;
    }

    /** No texture to read: grey rock with a few orange lumps. */
    private static int[][] generated(int seed) {
        Random r = new Random(seed);
        int[][] out = new int[N][N];
        for (int y = 0; y < N; y++) for (int x = 0; x < N; x++) {
            int v = 110 + r.nextInt(30);
            out[x][y] = v << 16 | v << 8 | v;
        }
        for (int i = 0; i < 6; i++) {
            int cx = 2 + r.nextInt(12), cy = 2 + r.nextInt(12);
            for (int y = -1; y <= 1; y++) for (int x = -1; x <= 1; x++) {
                if (r.nextInt(4) > 0) out[cx + x][cy + y] = 0xD8892E;
            }
        }
        return out;
    }

    // ---- playing --------------------------------------------------------------------------------

    private boolean inside(int x, int y) {
        return x >= 0 && y >= 0 && x < N && y < N;
    }

    private boolean walkable(int x, int y) {
        if (x < -1 || y < -1 || x > N || y > N) return false;
        return !inside(x, y) || type[x][y] == EMPTY;
    }

    void move(int dx, int dy) {
        if (endAt >= 0) return;
        fx = dx;
        fy = dy;
        if (walkable(px + dx, py + dy)) {
            px += dx;
            py += dy;
        }
    }

    /** Swing at the cell in front. Returns false while the pickaxe is still coming back. */
    boolean swing(boolean hard) {
        long now = Util.getMillis();
        if (endAt >= 0 || now < readyAt) return false;
        readyAt = now + (hard ? 220 : 150);
        swingUntil = now + 140;
        actions++;
        int tx = px + fx, ty = py + fy;
        if (!inside(tx, ty) || type[tx][ty] == EMPTY) {
            sound(SoundEvents.PLAYER_ATTACK_NODAMAGE, 1.2F);
            return true;
        }
        float cx = cellX(tx) + CELL / 2.0F, cy = cellY(ty) + CELL / 2.0F;
        if (type[tx][ty] == ROCK) {
            hp[tx][ty] -= hard ? power : 1;
            spray(cx, cy, colour[tx][ty], 3);
            if (hp[tx][ty] <= 0) {
                type[tx][ty] = EMPTY;
                spray(cx, cy, colour[tx][ty], 6);
                sound(SoundEvents.STONE_BREAK, 0.9F + random.nextFloat() * 0.2F);
            } else {
                sound(SoundEvents.STONE_HIT, 0.8F + random.nextFloat() * 0.3F);
            }
            return true;
        }
        // ore: the whole piece comes out, cleanly or in dust
        int id = piece[tx][ty];
        int size = pieceSize.get(id);
        for (int y = 0; y < N; y++) for (int x = 0; x < N; x++) {
            if (piece[x][y] == id) {
                type[x][y] = EMPTY;
                spray(cellX(x) + CELL / 2.0F, cellY(y) + CELL / 2.0F, colour[x][y], hard ? 3 : 1);
            }
        }
        if (hard) {
            pieceState.set(id, SMASHED);
            smashed += size;
            show(Component.translatable("gui.realisticsurvival.mining.smashed").getString(), 0xFFE05A47);
            sound(SoundEvents.DECORATED_POT_SHATTER, 1.0F);
        } else {
            pieceState.set(id, COLLECTED);
            collected += size;
            show(Component.translatable("gui.realisticsurvival.mining.got").getString(), 0xFF8BE07A);
            sound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.8F + 0.6F * collected / Math.max(1, totalOre));
        }
        if (piecesLeft() == 0) endAt = now + 900;
        return true;
    }

    int piecesLeft() {
        int left = 0;
        for (int s : pieceState) if (s == INTACT) left++;
        return left;
    }

    int piecesTotal() {
        int n = 0;
        for (int s : pieceSize) if (s > 0) n++;
        return n;
    }

    /** Ends the dig now: you keep what you've prised out. */
    void finish() {
        send();
        if (minecraft != null && minecraft.gui.screen() == this) minecraft.gui.setScreen(null);
    }

    private void send() {
        if (resolved) return;
        resolved = true;
        ClientPacketDistributor.sendToServer(new OreMining.MiningResult(pos, collected, totalOre, actions));
        RealisticSurvival.LOGGER.info("Mining done: {}/{} ore, {} smashed, {} swings", collected, totalOre, smashed, actions);
    }

    @Override
    public void onClose() {
        send();
        super.onClose();
    }

    @Override
    public void removed() {
        send();
        super.removed();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int k = event.key();
        if (k == InputConstants.KEY_W || k == InputConstants.KEY_UP) move(0, -1);
        else if (k == InputConstants.KEY_S || k == InputConstants.KEY_DOWN) move(0, 1);
        else if (k == InputConstants.KEY_A || k == InputConstants.KEY_LEFT) move(-1, 0);
        else if (k == InputConstants.KEY_D || k == InputConstants.KEY_RIGHT) move(1, 0);
        else if (k == InputConstants.KEY_SPACE) swing(true);
        else if (k == InputConstants.KEY_LSHIFT || k == InputConstants.KEY_RSHIFT) swing(false);
        else if (k == InputConstants.KEY_RETURN) finish();
        else return super.keyPressed(event);
        return true;
    }

    // ---- a simple player for the automated test: heads for the nearest ore ---------------------

    /** Takes one step or one swing towards the nearest piece of ore. */
    void botStep() {
        if (endAt >= 0) return;
        int bx = -1, by = -1, best = Integer.MAX_VALUE;
        for (int y = 0; y < N; y++) for (int x = 0; x < N; x++) {
            if (type[x][y] != ORE) continue;
            int d = Math.abs(x - px) + Math.abs(y - py);
            if (d < best) {
                best = d;
                bx = x;
                by = y;
            }
        }
        if (bx < 0) return;
        int dx = Integer.signum(bx - px), dy = Integer.signum(by - py);
        // step along the longer way first
        int sx, sy;
        if (Math.abs(bx - px) >= Math.abs(by - py)) { sx = dx; sy = 0; } else { sx = 0; sy = dy; }
        int tx = px + sx, ty = py + sy;
        if (walkable(tx, ty)) {
            move(sx, sy);
            return;
        }
        fx = sx;
        fy = sy;
        if (inside(tx, ty)) swing(type[tx][ty] == ROCK);
    }

    // ---- drawing --------------------------------------------------------------------------------

    private int left() {
        return (width - 280) / 2;
    }

    private int top() {
        return (height - 200) / 2;
    }

    private int cellX(int x) {
        return left() + 12 + CELL + x * CELL;
    }

    private int cellY(int y) {
        return top() + 22 + CELL + y * CELL;
    }

    private void spray(float x, float y, int col, int n) {
        for (int i = 0; i < n; i++) {
            bits.add(new float[] { x, y, (random.nextFloat() - 0.5F) * 90, -20 - random.nextFloat() * 60, 0.4F + random.nextFloat() * 0.4F, col });
        }
    }

    private void show(String text, int col) {
        flash = text;
        flashColour = col;
        flashUntil = Util.getMillis() + 700;
    }

    private void sound(SoundEvent event, float pitch) {
        if (minecraft != null) minecraft.getSoundManager().play(SimpleSoundInstance.forUI(event, pitch));
    }

    private void update(long now) {
        if (startMs < 0) {
            startMs = now;
            last = now;
        }
        float dt = Math.min(0.1F, (now - last) / 1000.0F);
        last = now;
        if (endAt < 0 && now - startMs > timeLimitMs) {
            endAt = now + 900;
            show(Component.translatable("gui.realisticsurvival.mining.time").getString(), 0xFFE0C070);
        }
        for (int i = bits.size() - 1; i >= 0; i--) {
            float[] b = bits.get(i);
            b[0] += b[2] * dt;
            b[1] += b[3] * dt;
            b[3] += 220 * dt;
            b[4] -= dt;
            if (b[4] <= 0) bits.remove(i);
        }
        if (endAt >= 0 && now >= endAt) finish();
    }

    private static int argb(int rgb) {
        return 0xFF000000 | rgb;
    }

    private static int shade(int rgb, float f) {
        int r = Math.min(255, (int) ((rgb >> 16 & 255) * f));
        int g = Math.min(255, (int) ((rgb >> 8 & 255) * f));
        int b = Math.min(255, (int) ((rgb & 255) * f));
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        g.fill(0, 0, width, height, 0x70000000);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        long now = Util.getMillis();
        update(now);
        int x0 = left(), y0 = top(), W = 280, H = 200;
        // frame: your rock texture, darkened
        g.fill(x0 - 1, y0 - 1, x0 + W + 1, y0 + H + 1, 0xFF000000);
        for (int ty = 0; ty < H; ty += 16) for (int tx = 0; tx < W; tx += 16) {
            int w = Math.min(16, W - tx), h = Math.min(16, H - ty);
            g.blit(ROCK_TEX, x0 + tx, y0 + ty, x0 + tx + w, y0 + ty + h, 0.0F, w / 16.0F, 0.0F, h / 16.0F);
        }
        g.fill(x0, y0, x0 + W, y0 + H, 0xB0201812);
        g.centeredText(font, oreName.getString(), x0 + 12 + (N + 2) * CELL / 2, y0 + 8, 0xFFFFD9A0);

        // the tunnel round the edge and the block itself
        int gx = cellX(0), gy = cellY(0);
        g.fill(gx - CELL, gy - CELL, gx + (N + 1) * CELL, gy + (N + 1) * CELL, 0xFF140F0C);
        for (int y = 0; y < N; y++) for (int x = 0; x < N; x++) {
            int cx = cellX(x), cy = cellY(y);
            switch (type[x][y]) {
                case ROCK -> {
                    float f = 0.55F + 0.45F * hp[x][y] / (float) rockHp;
                    g.fill(cx, cy, cx + CELL, cy + CELL, shade(colour[x][y], f));
                    if (hp[x][y] < rockHp) {
                        g.fill(cx + 2, cy + 3, cx + 5, cy + 4, 0xFF2A221C);
                        g.fill(cx + 4, cy + 4, cx + 5, cy + 7, 0xFF2A221C);
                    }
                }
                case ORE -> {
                    g.fill(cx, cy, cx + CELL, cy + CELL, argb(colour[x][y]));
                    // glints so ore reads as ore
                    if (((x * 7 + y * 13 + now / 300) % 23) == 0) g.fill(cx + 3, cy + 3, cx + 6, cy + 6, 0xC0FFFFFF);
                }
                default -> g.fill(cx, cy, cx + CELL, cy + CELL, (x + y) % 2 == 0 ? 0xFF231B16 : 0xFF1F1813);
            }
        }

        // the cell the next swing will hit
        int hx = px + fx, hy0 = py + fy;
        if (inside(hx, hy0) && endAt < 0) {
            int cx = cellX(hx), cy = cellY(hy0);
            int c = type[hx][hy0] == ORE ? 0xFF8BE07A : 0xFFFFF4D6;
            g.fill(cx - 1, cy - 1, cx + CELL + 1, cy, c);
            g.fill(cx - 1, cy + CELL, cx + CELL + 1, cy + CELL + 1, c);
            g.fill(cx - 1, cy, cx, cy + CELL, c);
            g.fill(cx + CELL, cy, cx + CELL + 1, cy + CELL, c);
        }

        // the pickaxe, head towards where it faces
        ItemStack tool = minecraft != null && minecraft.player != null ? minecraft.player.getMainHandItem() : ItemStack.EMPTY;
        float pcx = cellX(px) + CELL / 2.0F, pcy = cellY(py) + CELL / 2.0F;
        float angle = (float) Math.atan2(fy, fx) + (float) Math.PI / 4.0F;
        if (now < swingUntil) angle -= 0.7F * (swingUntil - now) / 140.0F;
        g.fill((int) pcx - 1, (int) pcy - 1, (int) pcx + 1, (int) pcy + 1, 0x80FFFFFF);
        g.pose().pushMatrix();
        g.pose().translate(pcx + fx * 3, pcy + fy * 3);
        g.pose().rotate(angle);
        g.pose().scale(1.1F, 1.1F);
        g.pose().translate(-8.0F, -8.0F);
        g.item(tool, 0, 0);
        g.pose().popMatrix();

        for (float[] b : bits) {
            int c = 0xFF000000 | (int) b[5];
            g.fill((int) b[0], (int) b[1], (int) b[0] + 2, (int) b[1] + 2, c);
        }

        // the side panel
        int sx = x0 + 12 + (N + 2) * CELL + 10, sy = y0 + 24;
        g.text(font, Component.translatable("gui.realisticsurvival.mining.pieces", piecesTotal() - piecesLeft(), piecesTotal()), sx, sy, 0xFFE8DCC8, false);
        int pct = Math.round(100.0F * collected / Math.max(1, totalOre));
        g.text(font, Component.translatable("gui.realisticsurvival.mining.share", pct), sx, sy + 12, 0xFF8BE07A, false);
        if (smashed > 0) {
            int lost = Math.round(100.0F * smashed / Math.max(1, totalOre));
            g.text(font, Component.translatable("gui.realisticsurvival.mining.lost", lost), sx, sy + 24, 0xFFE05A47, false);
        }
        int barW = W - (sx - x0) - 10;
        float left = endAt >= 0 ? 0.0F : Math.max(0.0F, 1.0F - (now - Math.max(0, startMs)) / (float) timeLimitMs);
        g.fill(sx, sy + 40, sx + barW, sy + 44, 0xFF3B2A20);
        g.fill(sx, sy + 40, sx + Math.round(barW * left), sy + 44, left < 0.25F ? 0xFFE05A47 : 0xFFB8A48C);
        int hy = sy + 56;
        for (String key : new String[] { "move", "swing", "tap", "done" }) {
            for (var line : font.split(Component.translatable("gui.realisticsurvival.mining.help." + key), barW)) {
                g.text(font, line, sx, hy, 0xFFB8A48C, false);
                hy += 10;
            }
            hy += 3;
        }
        if (now < flashUntil) g.centeredText(font, flash, x0 + 12 + (N + 2) * CELL / 2, y0 + H - 12, flashColour);
    }
}
