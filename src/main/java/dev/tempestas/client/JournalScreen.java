package dev.tempestas.client;

import com.fundamentum.guide.client.GuideScreen;
import com.fundamentum.port.FId;
import dev.tempestas.Tempestas;
import dev.tempestas.sky.CaughtSky;
import dev.tempestas.sky.Journal;
import dev.tempestas.sky.Skies;
import dev.tempestas.sky.Sky;
import dev.tempestas.sky.SkyData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;
import java.util.Map;

/** The sky journal: every sky in the table, one line each. Caught skies say where they were first caught; the rest give a hint. */
public class JournalScreen extends Screen {
    private static final int W = 236;
    private static final int H = 212;
    private static final int ROWS = 6;
    private static final int ROW_H = 26;
    private static final int PAPER = 0xFFE9DFC6;
    private static final int EDGE = 0xFF3A4A6A;
    private static final int INK = 0xFF2B2B33;
    private static final int FADED = 0xFF8A8478;

    private List<Map.Entry<Identifier, Sky>> skies = List.of();
    private int page;
    private Button prev;
    private Button next;

    public JournalScreen() {
        super(Component.translatable("screen.tempestas.journal"));
    }

    public static void open() {
        Minecraft.getInstance().gui.setScreen(new JournalScreen());
    }

    public static void openGuide() {
        Minecraft.getInstance().gui.setScreen(GuideScreen.at(FId.of(Tempestas.MOD_ID, "book")));
    }

    @Override
    protected void init() {
        // easy skies first, so the first page is the one a new player can fill
        skies = Skies.TABLE.entries().entrySet().stream()
                .sorted(java.util.Comparator.<Map.Entry<Identifier, Sky>>comparingInt(e -> e.getValue().priority())
                        .thenComparing(e -> e.getKey().toString()))
                .toList();
        int left = (width - W) / 2;
        int top = (height - H) / 2;
        prev = addRenderableWidget(Button.builder(Component.literal("<"), b -> turn(-1))
                .bounds(left + 8, top + H - 24, 20, 16).build());
        next = addRenderableWidget(Button.builder(Component.literal(">"), b -> turn(1))
                .bounds(left + W - 28, top + H - 24, 20, 16).build());
        addRenderableWidget(Button.builder(Component.literal("?"), b -> openGuide())
                .bounds(left + W - 24, top + 5, 16, 14).build());
        turn(0);
    }

    private int pages() {
        return Math.max(1, (skies.size() + ROWS - 1) / ROWS);
    }

    private void turn(int d) {
        page = Math.max(0, Math.min(pages() - 1, page + d));
        prev.active = page > 0;
        next.active = page < pages() - 1;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        super.extractBackground(g, mouseX, mouseY, partial);
        int left = (width - W) / 2;
        int top = (height - H) / 2;
        g.fill(left - 2, top - 2, left + W + 2, top + H + 2, EDGE);
        g.fill(left, top, left + W, top + H, PAPER);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        super.extractRenderState(g, mouseX, mouseY, partial);
        int left = (width - W) / 2;
        int top = (height - H) / 2;
        Journal journal = minecraft.player == null ? Journal.EMPTY : minecraft.player.getData(SkyData.JOURNAL);
        long caught = skies.stream().filter(e -> journal.has(e.getKey())).count();
        g.text(font, Component.translatable("screen.tempestas.journal.count", caught, skies.size()), left + 10, top + 8, EDGE, false);

        int y = top + 22;
        for (int i = page * ROWS; i < Math.min(skies.size(), (page + 1) * ROWS); i++) {
            Identifier id = skies.get(i).getKey();
            CaughtSky first = journal.firsts().get(id);
            int textX = left + 26;
            int textW = W - 34;
            if (first != null) {
                int colour = 0xFF000000 | skies.get(i).getValue().color();
                g.fill(left + 9, y + 1, left + 21, y + 13, EDGE);
                g.fill(left + 10, y + 2, left + 20, y + 12, colour);
                g.text(font, Skies.name(id), textX, y, INK, false);
                g.text(font, line(Skies.provenance(first), textW), textX, y + 11, FADED, false);
            } else {
                g.fill(left + 9, y + 1, left + 21, y + 13, FADED);
                g.text(font, "?", left + 13, y + 3, PAPER, false);
                g.text(font, line(Skies.hint(id), textW), textX, y + 4, FADED, false);
            }
            y += ROW_H;
        }
        g.centeredText(font, (page + 1) + " / " + pages(), left + W / 2, top + H - 20, EDGE);
    }

    private FormattedCharSequence line(Component text, int width) {
        List<FormattedCharSequence> lines = font.split(text, width);
        return lines.isEmpty() ? FormattedCharSequence.EMPTY : lines.getFirst();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
