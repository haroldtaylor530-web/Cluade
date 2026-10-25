package com.harold.corpsedecay.client.config;

import static com.harold.corpsedecay.client.config.DecayPreview.alpha;
import static com.harold.corpsedecay.client.config.DecayPreview.lerp;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.DoubleFunction;
import java.util.function.DoubleUnaryOperator;

import com.harold.corpsedecay.Config;

import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModList;

/** Corpse Decay's settings screen, opened from the Mods list. */
public class CorpseConfigScreen extends Screen {
    static final int BONE = 0xFFEDE6D3, DIM = 0xFF9C948A, ACCENT = 0xFFB8D47A, AMBER = 0xFFE0A84A,
            PANEL = 0xC8110E16, EDGE = 0xFF3A3240, EDGE_LIT = 0xFF5A4E60;

    private final Screen parent;
    private final Scene scene = new Scene();
    private final Map<ForgeConfigSpec.ConfigValue<?>, Object> pending = new LinkedHashMap<>();
    private final List<Tab> tabs = new ArrayList<>();
    private final List<String> excluded = new ArrayList<>();
    private int tab;
    private long opened = -1, tabSwitched, last, resetArmed;
    private float scroll, scrollTarget, tabBar = -1;
    private int contentHeight;
    private Slider dragging;
    private EditBox input;
    private int left, top, right, bottom, tabsRight, cx;
    private boolean compact;
    private final List<int[]> chipHits = new ArrayList<>();
    private final List<String> suggestions = new ArrayList<>();

    private record Tab(String name, ItemStack icon, List<Row> rows) {
    }

    public CorpseConfigScreen(Screen parent) {
        super(Component.literal("Corpse Decay"));
        this.parent = parent;
        load();
        tabs.add(new Tab("Corpses", new ItemStack(Items.ROTTEN_FLESH), List.of(
                new Toggle("Leave corpses", "Mobs fall as ragdolls and leave a body when they die.", Config.ENABLED),
                new Toggle("Only player kills", "Only mobs killed by a player leave a body.", Config.ONLY_PLAYER_KILLS),
                new Slider("Ragdoll force", "How hard killing blows throw the body.", Config.RAGDOLL_FORCE, false,
                        v -> v / 5.0, p -> Math.round(p * 50) / 10.0, v -> String.format(Locale.ROOT, "%.1f×", v)),
                new Toggle("Loot everything", "Take every piece of armour and held item off a corpse, not just its normal drops.",
                        Config.LOOT_ALL_EQUIPMENT),
                new Slider("Most corpses", "Bodies allowed at once in a dimension; the oldest crumble first, so mob farms don't lag.",
                        Config.MAX_CORPSES, true, logPos(5, 2000), p -> niceCount(logVal(5, 2000, p)),
                        v -> String.valueOf((int) v)))));
        tabs.add(new Tab("Decay", new ItemStack(Items.BONE), List.of(
                new Preview(),
                new Slider("Time to skeleton", "Real time for a fresh body to rot down to bare bones.", Config.MINUTES_TO_SKELETON,
                        false, logPos(0.5, 2400), p -> niceMinutes(logVal(0.5, 2400, p)), CorpseConfigScreen::minutes),
                new Slider("Skeleton lasts", "How long the bones lie there before crumbling away. All the way right: forever.",
                        Config.SKELETON_MINUTES, false, v -> v <= 0 ? 1 : logPos(1, 10000).applyAsDouble(v) * 0.94,
                        p -> p > 0.965 ? 0 : niceMinutes(logVal(1, 10000, Math.min(1, p / 0.94))),
                        v -> v <= 0 ? "forever" : minutes(v)),
                new Toggle("Flies", "Flies buzz around rotting bodies.", Config.FLIES),
                new Toggle("Bog bodies & mummies", "Bodies left in swamps turn to leathery bog bodies; in frozen places they freeze-dry.",
                        Config.MUMMIFY))));
        tabs.add(new Tab("World", new ItemStack(Items.BRUSH), List.of(
                new Note("These only change chunks generated after you save, so explore somewhere new to see them."),
                new Toggle("Ancient remains", "Old adventurers, animals, bog bodies, frozen mummies, whale falls, pirate crews and ghost ships.",
                        Config.REMAINS),
                new Toggle("Fossils", "Whole skeletons in the rock, ammonites, trilobites, fish and ferns; fossil whales in the desert.",
                        Config.FOSSILS),
                new Toggle("Stone Age sites", "Painted caves, stone circles, dolmens, burial mounds and mammoth-bone huts.",
                        Config.ANCIENT_SITES))));
        tabs.add(new Tab("Exclusions", new ItemStack(Items.BARRIER), List.of()));
    }

    // ---------------------------------------------------------------- values

    private void load() {
        for (ForgeConfigSpec.ConfigValue<?> v : List.of(Config.ENABLED, Config.ONLY_PLAYER_KILLS, Config.RAGDOLL_FORCE,
                Config.LOOT_ALL_EQUIPMENT, Config.MAX_CORPSES, Config.MINUTES_TO_SKELETON, Config.SKELETON_MINUTES,
                Config.FLIES, Config.MUMMIFY, Config.REMAINS, Config.FOSSILS, Config.ANCIENT_SITES)) {
            pending.put(v, saved(v));
        }
        excluded.clear();
        for (Object o : (List<?>) saved(Config.EXCLUDED)) excluded.add(String.valueOf(o));
    }

    private static Object saved(ForgeConfigSpec.ConfigValue<?> v) {
        try {
            return v.get();
        } catch (IllegalStateException e) {
            return v.getDefault();
        }
    }

    private boolean changed(ForgeConfigSpec.ConfigValue<?> v) {
        return !Objects.equals(saved(v), pending.get(v));
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private void save() {
        if (!Config.SPEC.isLoaded()) return;
        for (Map.Entry<ForgeConfigSpec.ConfigValue<?>, Object> e : pending.entrySet()) {
            if (changed(e.getKey())) ((ForgeConfigSpec.ConfigValue) e.getKey()).set(e.getValue());
        }
        if (!excluded.equals(saved(Config.EXCLUDED))) ((ForgeConfigSpec.ConfigValue) Config.EXCLUDED).set(new ArrayList<>(excluded));
        Config.SPEC.save();
    }

    private void resetAll() {
        for (ForgeConfigSpec.ConfigValue<?> v : pending.keySet()) pending.put(v, v.getDefault());
        excluded.clear();
        for (Object o : Config.EXCLUDED.getDefault()) excluded.add(String.valueOf(o));
    }

    static DoubleUnaryOperator logPos(double min, double max) {
        return v -> (Math.log(Mth.clamp(v, min, max)) - Math.log(min)) / (Math.log(max) - Math.log(min));
    }

    static double logVal(double min, double max, double p) {
        return Math.exp(Math.log(min) + (Math.log(max) - Math.log(min)) * p);
    }

    static double niceMinutes(double m) {
        double step = m < 2 ? 0.1 : m < 10 ? 0.5 : m < 60 ? 1 : m < 600 ? 5 : m < 2400 ? 20 : 100;
        return Math.max(0.1, Math.round(m / step) * step);
    }

    static double niceCount(double c) {
        double step = c < 50 ? 1 : c < 200 ? 5 : 25;
        return Math.round(c / step) * step;
    }

    static String minutes(double m) {
        String t;
        if (m < 1) t = String.format(Locale.ROOT, "%.0f s", m * 60);
        else if (m < 60) t = (m == Math.rint(m) ? String.valueOf((long) m) : String.format(Locale.ROOT, "%.1f", m)) + " min";
        else if (m < 1440) {
            long h = (long) (m / 60), mm = Math.round(m - h * 60);
            t = h + " h" + (mm > 0 ? " " + mm + " min" : "");
        } else t = String.format(Locale.ROOT, "%.1f real days", m / 1440);
        return t + "  ·  " + DecayPreview.days(m);
    }

    // ---------------------------------------------------------------- layout

    @Override
    protected void init() {
        if (opened < 0) opened = Util.getMillis();
        int pw = Math.min(width - 16, 520);
        compact = pw < 400;
        left = (width - pw) / 2;
        right = left + pw;
        top = 46;
        bottom = height - 32;
        tabsRight = left + (compact ? 26 : 112);
        cx = tabsRight + 5;
        String text = input == null ? "" : input.getValue();
        input = new EditBox(font, cx + 13, top + 13, right - cx - 26 - 52, 10, Component.literal("Mob id"));
        input.setBordered(false);
        input.setMaxLength(120);
        input.setTextColor(BONE);
        input.setHint(Component.literal("minecraft:bat").withStyle(s -> s.withColor(0x6A6460)));
        input.setValue(text);
        input.setResponder(s -> updateSuggestions());
        input.visible = tab == 3;
        addWidget(input);
        updateSuggestions();
    }

    public void selectTab(int i) {
        if (i == tab || i < 0 || i >= tabs.size()) return;
        tab = i;
        tabSwitched = Util.getMillis();
        scroll = scrollTarget = 0;
        dragging = null;
        input.visible = tab == 3;
        if (tab != 3) input.setFocused(false);
    }

    private static float ease(float t) {
        t = Mth.clamp(t, 0, 1);
        return 1 - (1 - t) * (1 - t) * (1 - t);
    }

    private void click() {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    private void frame(GuiGraphics g, int x1, int y1, int x2, int y2, int fill, int edge, float a) {
        g.fill(x1 + 1, y1 + 1, x2 - 1, y2 - 1, alpha(fill, a));
        g.fill(x1 + 1, y1, x2 - 1, y1 + 1, alpha(lerp(edge, 0xFFFFFFFF, 0.12F), a));
        g.fill(x1 + 1, y2 - 1, x2 - 1, y2, alpha(edge, a));
        g.fill(x1, y1 + 1, x1 + 1, y2 - 1, alpha(edge, a));
        g.fill(x2 - 1, y1 + 1, x2, y2 - 1, alpha(edge, a));
    }

    // ---------------------------------------------------------------- drawing

    @Override
    public void renderBackground(GuiGraphics g) {
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        long now = Util.getMillis();
        float dt = last == 0 ? 0 : Math.min(0.1F, (now - last) / 1000F);
        last = now;
        float open = ease((now - opened) / 700F);
        scene.render(g, width, height, dt);

        g.pose().pushPose();
        g.pose().translate(0, (1 - open) * 14, 0);
        header(g, open);
        tabs(g, mx, my, open, dt);
        content(g, mx, my, open, dt, pt);
        buttons(g, mx, my, open);
        g.pose().popPose();

        if (open < 1) g.fill(0, 0, width, height, alpha(0xFF000000, 1 - open));
    }

    private void header(GuiGraphics g, float a) {
        String title = "CORPSE DECAY";
        float t = scene.time();
        int scale = width < 300 ? 1 : 2;
        int tw = font.width(title) * scale;
        int x = (width - tw) / 2;
        g.pose().pushPose();
        g.pose().translate(x, 10, 0);
        g.pose().scale(scale, scale, 1);
        int cx0 = 0;
        for (int i = 0; i < title.length(); i++) {
            String ch = String.valueOf(title.charAt(i));
            float bob = (float) Math.sin(t * 1.6F + i * 0.55F) * 0.8F;
            float glow = 0.5F + 0.5F * (float) Math.sin(t * 0.9F - i * 0.35F);
            int col = lerp(0xFFD9D0B8, 0xFFFFFAEA, glow * 0.6F);
            g.pose().pushPose();
            g.pose().translate(0, bob, 0);
            g.drawString(font, ch, cx0 + 1, 1, alpha(0xFF2A1E26, a), false);
            g.drawString(font, ch, cx0, 0, alpha(col, a), false);
            g.pose().popPose();
            cx0 += font.width(ch);
        }
        g.pose().popPose();
        // a line of little bones under the title
        int ly = 10 + 9 * scale + 3;
        int lw = Math.min(tw + 40, width - 20);
        for (int i = 0; i < lw; i++) {
            float k = 1 - Math.abs(i - lw / 2F) / (lw / 2F);
            g.fill(width / 2 - lw / 2 + i, ly, width / 2 - lw / 2 + i + 1, ly + 1, alpha(0xFFEDE6D3, a * k * 0.6F));
        }
        String version = ModList.get().getModContainerById("corpsedecay")
                .map(c -> c.getModInfo().getVersion().toString()).orElse("");
        boolean remote = minecraft.level != null && !minecraft.hasSingleplayerServer();
        String sub = remote ? "this server uses its own settings; these are for your own worlds"
                : "settings  ·  v" + version;
        if (a > 0.1F) g.drawCenteredString(font, sub, width / 2, ly + 4, alpha(remote ? AMBER : DIM, a));
    }

    private void tabs(GuiGraphics g, int mx, int my, float a, float dt) {
        frame(g, left, top, tabsRight, bottom, PANEL, EDGE, a);
        int ty = top + 5 + tab * 26;
        if (tabBar < 0) tabBar = ty;
        tabBar += (ty - tabBar) * Math.min(1, dt * 16);
        int by = Math.round(tabBar);
        g.fillGradient(left + 2, by, tabsRight - 2, by + 22, alpha(0x30B8D47A, a), alpha(0x10B8D47A, a));
        g.fill(left + 1, by + 2, left + 3, by + 20, alpha(ACCENT, a));
        for (int i = 0; i < tabs.size(); i++) {
            Tab tb = tabs.get(i);
            int y = top + 5 + i * 26;
            boolean hover = mx >= left && mx < tabsRight && my >= y && my < y + 22;
            if (hover && i != tab) g.fill(left + 2, y, tabsRight - 2, y + 22, alpha(0x14FFFFFF, a));
            float bounce = i == tab ? (float) Math.sin(scene.time() * 2.4F) * 0.8F : 0;
            g.pose().pushPose();
            g.pose().translate(0, bounce, 0);
            g.renderItem(tb.icon(), left + 5, y + 3);
            g.pose().popPose();
            if (!compact && a > 0.1F) g.drawString(font, tb.name(), left + 25, y + 7, alpha(i == tab ? BONE : DIM, a), false);
        }
        // a buried skull at the bottom of the tab column
        if (!compact && bottom - top > 26 * tabs.size() + 50) {
            int sx = left + (tabsRight - left) / 2 - 8, sy = bottom - 26;
            skull(g, sx, sy, 2, alpha(0xFF6E665A, a * 0.8F), alpha(0xFF141016, a));
        }
    }

    private void skull(GuiGraphics g, int x, int y, int p, int col, int hole) {
        String[] art = { " ##### ", "#######", "#o##o##", "#######", " #.#.# ", " ##### " };
        for (int r = 0; r < art.length; r++) {
            for (int c = 0; c < art[r].length(); c++) {
                char ch = art[r].charAt(c);
                if (ch == ' ') continue;
                int x0 = x + c * p, y0 = y + r * p;
                g.fill(x0, y0, x0 + p, y0 + p, ch == '#' ? col : hole);
            }
        }
    }

    private void content(GuiGraphics g, int mx, int my, float a, float dt, float pt) {
        frame(g, cx, top, right, bottom, PANEL, EDGE, a);
        float ca = ease((Util.getMillis() - tabSwitched) / 280F) * a;
        int off = (int) ((1 - ca) * 12);
        int w = right - cx - 8;
        if (tab == 3) {
            exclusions(g, mx, my, ca, off, pt);
            return;
        }
        scroll += (scrollTarget - scroll) * Math.min(1, dt * 14);
        g.enableScissor(cx + 1, top + 1, right - 1, bottom - 1);
        int y = top + 4 - Math.round(scroll);
        boolean inside = my >= top && my < bottom;
        List<Row> rows = tabs.get(tab).rows();
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            int h = r.height(w);
            r.place(cx + 4 + off, y, w, h);
            r.render(g, inside ? mx : -1, inside ? my : -1, ca, dt);
            y += h;
            if (i < rows.size() - 1) g.fill(cx + 10, y, right - 10, y + 1, alpha(0x14FFFFFF, ca));
            y += 2;
        }
        contentHeight = y + Math.round(scroll) - top + 2;
        g.disableScissor();
        int view = bottom - top;
        if (contentHeight > view) {
            int bar = Math.max(16, view * view / contentHeight);
            int by = top + (int) ((view - bar) * (scroll / (contentHeight - view)));
            g.fill(right - 4, top + 2, right - 2, bottom - 2, alpha(0x20FFFFFF, a));
            g.fill(right - 4, by + 2, right - 2, by + bar - 2, alpha(0x80EDE6D3, a));
        } else {
            scrollTarget = 0;
        }
    }

    private void updateSuggestions() {
        suggestions.clear();
        if (input == null) return;
        String q = input.getValue().trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) return;
        for (ResourceLocation id : BuiltInRegistries.ENTITY_TYPE.keySet()) {
            String s = id.toString();
            if ((s.contains(q)) && !excluded.contains(s)) suggestions.add(s);
        }
        suggestions.sort((x, y) -> {
            boolean xs = x.startsWith(q) || x.contains(":" + q), ys = y.startsWith(q) || y.contains(":" + q);
            return xs != ys ? (xs ? -1 : 1) : x.compareTo(y);
        });
        if (suggestions.size() > 5) suggestions.subList(5, suggestions.size()).clear();
    }

    private void addExcluded(String id) {
        id = id.trim().toLowerCase(Locale.ROOT);
        if (id.isEmpty()) return;
        if (!id.contains(":")) id = "minecraft:" + id;
        if (ResourceLocation.tryParse(id) == null || excluded.contains(id)) return;
        excluded.add(id);
        input.setValue("");
        click();
    }

    private void exclusions(GuiGraphics g, int mx, int my, float a, int off, float pt) {
        int x0 = cx + 8, x1 = right - 8;
        // the input box and its Add button
        int bx1 = x1 - 52;
        boolean focused = input.isFocused();
        frame(g, x0, top + 8, bx1, top + 26, 0xFF0A080E, focused ? lerp(EDGE_LIT, ACCENT, 0.4F) : EDGE, a);
        input.setX(x0 + 5);
        input.setY(top + 13);
        input.setWidth(bx1 - x0 - 10);
        if (a > 0.1F) input.render(g, mx, my, pt);
        boolean addHover = mx >= bx1 + 4 && mx < x1 && my >= top + 8 && my < top + 26;
        frame(g, bx1 + 4, top + 8, x1, top + 26, addHover ? 0xFF2A3420 : 0xFF1C1A20, addHover ? ACCENT : EDGE, a);
        if (a > 0.1F) g.drawCenteredString(font, "Add", (bx1 + 4 + x1) / 2, top + 13, alpha(addHover ? ACCENT : BONE, a));

        // the chips
        chipHits.clear();
        int y = top + 34;
        if (a > 0.1F) g.drawString(font, "Mobs that never leave a body:", x0 + off, y, alpha(DIM, a), false);
        y += 13;
        int x = x0;
        if (excluded.isEmpty() && a > 0.1F) {
            g.drawString(font, "None. Type a mob's id above, like minecraft:bat.", x0 + off, y + 4, alpha(0xFF6A6460, a), false);
            y += 16;
        }
        for (int i = 0; i < excluded.size(); i++) {
            String id = excluded.get(i);
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(ResourceLocation.tryParse(id)).orElse(null);
            SpawnEggItem egg = type == null ? null : SpawnEggItem.byId(type);
            int cw = font.width(id) + 30;
            if (x + cw > x1 && x > x0) {
                x = x0;
                y += 20;
            }
            boolean hover = mx >= x + off && mx < x + off + cw && my >= y && my < y + 17;
            boolean xHover = hover && mx >= x + off + cw - 12;
            frame(g, x + off, y, x + off + cw, y + 17, hover ? 0xFF2A2430 : 0xFF1C1820,
                    type == null ? 0xFF6A3A30 : hover ? EDGE_LIT : EDGE, a);
            g.pose().pushPose();
            g.pose().translate(x + off + 3, y + 2.5F, 0);
            g.pose().scale(0.75F, 0.75F, 1);
            g.renderItem(egg != null ? new ItemStack(egg) : new ItemStack(Items.SKELETON_SKULL), 0, 0);
            g.pose().popPose();
            if (a > 0.1F) {
                g.drawString(font, id, x + off + 17, y + 5, alpha(type == null ? 0xFFC08070 : BONE, a), false);
                g.drawString(font, "×", x + off + cw - 9, y + 5, alpha(xHover ? 0xFFFF7060 : DIM, a), false);
            }
            chipHits.add(new int[] { x + off + cw - 12, y, x + off + cw, y + 17, i });
            x += cw + 4;
        }
        y += 26;
        if (a > 0.1F && y < bottom - 12) {
            for (FormattedCharSequence line : font.split(Component.literal(
                    "Red ones aren't in this game (maybe from a mod that isn't installed). The entity tag #corpsedecay:no_corpse works too."),
                    x1 - x0)) {
                if (y > bottom - 12) break;
                g.drawString(font, line, x0 + off, y, alpha(0xFF6A6460, a), false);
                y += 10;
            }
        }

        // suggestions drop down over everything
        if (focused && !suggestions.isEmpty()) {
            int sy = top + 27;
            frame(g, x0, sy, bx1, sy + suggestions.size() * 12 + 4, 0xF0141018, EDGE_LIT, a);
            for (int i = 0; i < suggestions.size(); i++) {
                int ly = sy + 2 + i * 12;
                boolean h = mx >= x0 && mx < bx1 && my >= ly && my < ly + 12;
                if (h) g.fill(x0 + 1, ly, bx1 - 1, ly + 12, alpha(0x30B8D47A, a));
                g.drawString(font, suggestions.get(i), x0 + 5, ly + 2, alpha(h ? ACCENT : BONE, a), false);
            }
        }
    }

    private static final String[] BUTTONS = { "Reset to defaults", "Cancel", "Save & close" };

    private int[] button(int i) {
        int bw = Math.min(120, (right - left - 12) / 3);
        int total = bw * 3 + 12;
        int x = (width - total) / 2 + i * (bw + 6);
        int y = height - 26;
        return new int[] { x, y, x + bw, y + 20 };
    }

    private void buttons(GuiGraphics g, int mx, int my, float a) {
        for (int i = 0; i < 3; i++) {
            int[] b = button(i);
            boolean hover = mx >= b[0] && mx < b[2] && my >= b[1] && my < b[3];
            boolean armed = i == 0 && Util.getMillis() - resetArmed < 3000;
            int edge = armed ? AMBER : hover ? (i == 2 ? ACCENT : EDGE_LIT) : EDGE;
            int fill = hover ? (i == 2 ? 0xF0263020 : 0xF0221E28) : 0xE0141118;
            if (i == 2 && !hover) fill = 0xE01A2016;
            frame(g, b[0], b[1], b[2], b[3], fill, edge, a);
            if (hover) {
                float s = 0.5F + 0.5F * (float) Math.sin(scene.time() * 5F);
                g.fill(b[0] + 2, b[3] - 2, b[2] - 2, b[3] - 1, alpha(edge, a * (0.4F + 0.4F * s)));
            }
            String text = armed ? "Click again to reset" : BUTTONS[i];
            if (font.width(text) > b[2] - b[0] - 6) text = armed ? "Sure?" : i == 0 ? "Reset" : i == 2 ? "Save" : text;
            if (a > 0.1F) g.drawCenteredString(font, text, (b[0] + b[2]) / 2, b[1] + 6,
                    alpha(armed ? AMBER : i == 2 ? (hover ? ACCENT : 0xFFD8E8B0) : BONE, a));
        }
    }

    // ---------------------------------------------------------------- input

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (tab == 3) {
            if (input.isFocused() && !suggestions.isEmpty()) {
                int sy = top + 27, x0 = cx + 8, bx1 = right - 8 - 52;
                for (int i = 0; i < suggestions.size(); i++) {
                    int ly = sy + 2 + i * 12;
                    if (mx >= x0 && mx < bx1 && my >= ly && my < ly + 12) {
                        addExcluded(suggestions.get(i));
                        return true;
                    }
                }
            }
            if (input.mouseClicked(mx, my, button)) {
                setFocused(input);
                input.setFocused(true);
                return true;
            }
            input.setFocused(false);
            if (mx >= right - 8 - 48 && mx < right - 8 && my >= top + 8 && my < top + 26) {
                addExcluded(input.getValue());
                return true;
            }
            for (int[] c : chipHits) {
                if (mx >= c[0] && mx < c[2] && my >= c[1] && my < c[3]) {
                    excluded.remove(c[4]);
                    updateSuggestions();
                    click();
                    return true;
                }
            }
        }
        for (int i = 0; i < 3; i++) {
            int[] b = button(i);
            if (mx >= b[0] && mx < b[2] && my >= b[1] && my < b[3]) {
                click();
                if (i == 0) {
                    if (Util.getMillis() - resetArmed < 3000) {
                        resetAll();
                        resetArmed = 0;
                    } else resetArmed = Util.getMillis();
                } else if (i == 1) {
                    minecraft.setScreen(parent);
                } else {
                    onClose();
                }
                return true;
            }
        }
        for (int i = 0; i < tabs.size(); i++) {
            int y = top + 5 + i * 26;
            if (mx >= left && mx < tabsRight && my >= y && my < y + 22) {
                if (i != tab) click();
                selectTab(i);
                return true;
            }
        }
        if (tab != 3 && my >= top && my < bottom) {
            for (Row r : tabs.get(tab).rows()) {
                if (r.inside(mx, my) && r.click(mx, my)) return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging != null) {
            dragging.drag(mx);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (dragging != null) {
            dragging = null;
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BONE_BLOCK_HIT, 1.4F, 0.5F));
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int max = Math.max(0, contentHeight - (bottom - top));
        scrollTarget = Mth.clamp(scrollTarget - (float) delta * 24F, 0, max);
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (tab == 3 && input.isFocused() && (key == 257 || key == 335)) {
            addExcluded(suggestions.isEmpty() || input.getValue().contains(":") ? input.getValue() : suggestions.get(0));
            return true;
        }
        if (key == 258 && !input.isFocused()) { // tab key: next tab
            selectTab((tab + ((mods & 1) != 0 ? tabs.size() - 1 : 1)) % tabs.size());
            click();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void onClose() {
        save();
        minecraft.setScreen(parent);
    }

    // ---------------------------------------------------------------- rows

    private abstract class Row {
        int x, y, w, h;

        abstract int height(int w);

        abstract void render(GuiGraphics g, int mx, int my, float a, float dt);

        boolean click(double mx, double my) {
            return false;
        }

        void place(int x, int y, int w, int h) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }

        boolean inside(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }

        List<FormattedCharSequence> lines(String s, int width) {
            return font.split(Component.literal(s), Math.max(40, width));
        }

        void label(GuiGraphics g, String text, boolean changed, float a) {
            if (a < 0.1F) return;
            g.drawString(font, text, x + 8, y + 7, alpha(BONE, a), true);
            if (changed) {
                int dx = x + 8 + font.width(text) + 4;
                float s = 0.6F + 0.4F * (float) Math.sin(scene.time() * 4);
                g.fill(dx, y + 10, dx + 3, y + 13, alpha(AMBER, a * s));
            }
        }

        int desc(GuiGraphics g, String text, int width, int yy, float a) {
            for (FormattedCharSequence l : lines(text, width)) {
                if (a > 0.1F) g.drawString(font, l, x + 8, yy, alpha(DIM, a), false);
                yy += 10;
            }
            return yy;
        }

        void hover(GuiGraphics g, int mx, int my, float a) {
            if (inside(mx, my)) g.fill(x + 1, y, x + w - 1, y + h, alpha(0x10FFFFFF, a));
        }
    }

    private final class Toggle extends Row {
        final String label, desc;
        final ForgeConfigSpec.BooleanValue value;
        float anim = -1;

        Toggle(String label, String desc, ForgeConfigSpec.BooleanValue value) {
            this.label = label;
            this.desc = desc;
            this.value = value;
        }

        boolean on() {
            return Boolean.TRUE.equals(pending.get(value));
        }

        @Override
        int height(int w) {
            return 22 + lines(desc, w - 56).size() * 10 + 6;
        }

        @Override
        void render(GuiGraphics g, int mx, int my, float a, float dt) {
            hover(g, mx, my, a);
            boolean on = on();
            if (anim < 0) anim = on ? 1 : 0;
            anim += ((on ? 1 : 0) - anim) * Math.min(1, dt * 14);
            label(g, label, changed(value), a);
            desc(g, desc, w - 56, y + 20, a);
            int sx = x + w - 40, sy = y + 6;
            int track = lerp(0xFF2A2628, 0xFF4A6A2A, anim);
            frame(g, sx, sy, sx + 30, sy + 12, track, lerp(0xFF4A4448, 0xFF7AA040, anim), a);
            if (anim > 0.5F) g.fill(sx - 2, sy - 2, sx + 32, sy + 14, alpha(0xFFB8D47A, (anim - 0.5F) * 0.12F * a));
            int kx = sx + 1 + Math.round(anim * 18);
            g.fill(kx, sy + 1, kx + 10, sy + 11, alpha(0xFFEDE6D3, a));
            g.fill(kx, sy + 10, kx + 10, sy + 11, alpha(0xFFB8AD94, a));
            g.fill(kx + 3, sy + 4, kx + 4, sy + 6, alpha(0xFF3A3238, a * (1 - anim)));
            g.fill(kx + 6, sy + 4, kx + 7, sy + 6, alpha(0xFF3A3238, a * (1 - anim)));
            if (a > 0.1F) {
                String s = on ? "ON" : "OFF";
                g.drawString(font, s, sx - 4 - font.width(s), sy + 2, alpha(on ? ACCENT : 0xFF6A6460, a), false);
            }
        }

        @Override
        boolean click(double mx, double my) {
            pending.put(value, !on());
            CorpseConfigScreen.this.click();
            return true;
        }
    }

    private final class Slider extends Row {
        final String label, desc;
        final ForgeConfigSpec.ConfigValue<?> value;
        final boolean integer;
        final DoubleUnaryOperator toPos, fromPos;
        final DoubleFunction<String> fmt;
        float shown = -1;
        int tx0, tx1, ty;

        Slider(String label, String desc, ForgeConfigSpec.ConfigValue<?> value, boolean integer, DoubleUnaryOperator toPos,
                DoubleUnaryOperator fromPos, DoubleFunction<String> fmt) {
            this.label = label;
            this.desc = desc;
            this.value = value;
            this.integer = integer;
            this.toPos = toPos;
            this.fromPos = fromPos;
            this.fmt = fmt;
        }

        double get() {
            return ((Number) pending.get(value)).doubleValue();
        }

        @Override
        int height(int w) {
            return 20 + lines(desc, w - 16).size() * 10 + 22;
        }

        @Override
        void render(GuiGraphics g, int mx, int my, float a, float dt) {
            hover(g, mx, my, a);
            label(g, label, changed(value), a);
            String v = fmt.apply(get());
            if (a > 0.1F) g.drawString(font, v, x + w - 8 - font.width(v), y + 7, alpha(ACCENT, a), false);
            int yy = desc(g, desc, w - 16, y + 20, a);
            tx0 = x + 10;
            tx1 = x + w - 10;
            ty = yy + 8;
            float p = (float) Mth.clamp(toPos.applyAsDouble(get()), 0, 1);
            if (shown < 0) shown = p;
            shown += (p - shown) * Math.min(1, dt * (dragging == this ? 30 : 12));
            int kx = tx0 + Math.round((tx1 - tx0) * shown);
            g.fill(tx0, ty - 1, tx1, ty + 3, alpha(0xFF221E24, a));
            g.fill(tx0, ty - 2, tx1, ty - 1, alpha(0xFF141016, a));
            for (int i = tx0; i < kx; i++) {
                float t = (i - tx0) / (float) Math.max(1, tx1 - tx0);
                g.fill(i, ty - 1, i + 1, ty + 3, alpha(lerp(0xFF3E5A22, ACCENT, t), a));
            }
            for (int i = 1; i < 10; i++) {
                int nx = tx0 + (tx1 - tx0) * i / 10;
                g.fill(nx, ty + 4, nx + 1, ty + 6, alpha(0x30FFFFFF, a));
            }
            boolean hot = dragging == this || (mx >= kx - 6 && mx < kx + 6 && my >= ty - 8 && my < ty + 10);
            int bc = hot ? 0xFFFFFAEA : 0xFFE0D8C4;
            // a little bone for the knob
            g.fill(kx - 3, ty - 7, kx + 4, ty - 4, alpha(bc, a));
            g.fill(kx - 1, ty - 4, kx + 2, ty + 5, alpha(bc, a));
            g.fill(kx - 3, ty + 5, kx + 4, ty + 8, alpha(bc, a));
            g.fill(kx - 3, ty - 7, kx - 2, ty - 6, alpha(0xFF141016, a));
            g.fill(kx + 3, ty - 7, kx + 4, ty - 6, alpha(0xFF141016, a));
            g.fill(kx - 3, ty + 7, kx - 2, ty + 8, alpha(0xFF141016, a));
            g.fill(kx + 3, ty + 7, kx + 4, ty + 8, alpha(0xFF141016, a));
            if (hot) g.fill(kx - 5, ty - 9, kx + 6, ty + 10, alpha(0xFFB8D47A, 0.10F * a));
        }

        @Override
        boolean click(double mx, double my) {
            if (my < ty - 10 || my > ty + 12) return false;
            dragging = this;
            drag(mx);
            return true;
        }

        void drag(double mx) {
            double p = Mth.clamp((mx - tx0) / (double) Math.max(1, tx1 - tx0), 0, 1);
            double v = fromPos.applyAsDouble(p);
            pending.put(value, integer ? (Object) Integer.valueOf((int) Math.round(v)) : (Object) Double.valueOf(v));
        }
    }

    private final class Preview extends Row {
        @Override
        int height(int w) {
            return 106;
        }

        @Override
        void render(GuiGraphics g, int mx, int my, float a, float dt) {
            int px = x + 6, py = y + 6, pw = w - 12, ph = h - 10;
            frame(g, px - 1, py - 1, px + pw + 1, py + ph + 1, 0xFF0A080E, EDGE, a);
            DecayPreview.render(g, font, px, py, pw, ph,
                    ((Number) pending.get(Config.MINUTES_TO_SKELETON)).doubleValue(),
                    ((Number) pending.get(Config.SKELETON_MINUTES)).doubleValue(),
                    Boolean.TRUE.equals(pending.get(Config.FLIES)), a, scene.time());
        }
    }

    private final class Note extends Row {
        final String text;

        Note(String text) {
            this.text = text;
        }

        @Override
        int height(int w) {
            return 8 + lines(text, w - 30).size() * 10 + 6;
        }

        @Override
        void render(GuiGraphics g, int mx, int my, float a, float dt) {
            g.fill(x + 6, y + 5, x + 8, y + h - 4, alpha(AMBER, a * 0.7F));
            int yy = y + 7;
            for (FormattedCharSequence l : lines(text, w - 30)) {
                if (a > 0.1F) g.drawString(font, l, x + 14, yy, alpha(0xFFCFC0A0, a), false);
                yy += 10;
            }
        }
    }
}
