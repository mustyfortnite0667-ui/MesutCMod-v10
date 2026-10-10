package net.friends.mesutcmod.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import org.lwjgl.glfw.GLFW;

/** Pixel-art story prologue. Undertale-style: each slide fades in from black, holds, fades out to black. SPACE skips (with fade). */
public final class StoryIntroScreen extends Screen {
    private static final int SLIDE_COUNT = 13;
    // Her slayt toplam 8 saniye: 2.0 (yavaşça belir) + 3.2 (dur) + 2.0 (yavaşça sön) + 0.8 (tam siyah) = 8.0 sn. Buradan ayarlayabilirsin.
    private static final long FADE_IN_MS = 2_000L;
    private static final long HOLD_MS = 3_200L;
    private static final long FADE_OUT_MS = 2_000L;
    private static final long BLACK_GAP_MS = 800L;
    private static final long SLIDE_DURATION_MS = FADE_IN_MS + HOLD_MS + FADE_OUT_MS + BLACK_GAP_MS;
    private static final Identifier[] SLIDES = new Identifier[SLIDE_COUNT];

    static {
        for (int i = 0; i < SLIDE_COUNT; i++) {
            SLIDES[i] = new Identifier("mesutcmod", "textures/gui/story/slide_" + (i + 1) + ".png");
        }
    }

    /** Müzik slayt gösterisi boyunca baştan başa tekrar eder (loop). */
    private static final class LoopingMusic extends PositionedSoundInstance {
        LoopingMusic(Identifier id) {
            super(id, SoundCategory.MUSIC, 1.0F, 1.0F, SoundInstance.createRandom(),
                    true, 0, SoundInstance.AttenuationType.NONE, 0.0, 0.0, 0.0, true);
        }
    }

    private int tickCounter = 0;
    private int slide = 0;
    private long slideStartedAt;
    private boolean musicStarted = false;
    private long fadeOutStart = FADE_IN_MS + HOLD_MS;
    private long fadeOutDuration = FADE_OUT_MS;
    private long blackGap = BLACK_GAP_MS;
    private boolean skipping = false;

    public StoryIntroScreen() {
        super(Text.literal("Mesut Christ - Hikâye"));
        this.slideStartedAt = System.currentTimeMillis();
    }

    @Override
    protected void init() {
        super.init();
        // init() pencere yeniden boyutlanınca/F11'de tekrar çağrılır; müziği sadece bir kez başlat.
        if (this.client != null && !musicStarted) {
            musicStarted = true;
            Identifier musicId = new Identifier("mesutcmod", "once_upon_a_time");
            // Slayt gösterisi boyunca Minecraft'ın kendi müziğini sustur (bizim müzik "Müzik" ses
            // kanalından çalıyor, o yüzden kanalı kapatmak yerine vanilla parçayı durduruyoruz).
            this.client.getMusicTracker().stop();
            this.client.getSoundManager().play(new LoopingMusic(musicId));
        }
    }

    @Override
    public void tick() {
        super.tick();
        // Çok oyunculuda ya da yeni bir vanilla parça başlarsa tekrar durdur (2 saniyede bir).
        if (this.client != null && ++tickCounter % 40 == 0) {
            this.client.getMusicTracker().stop();
        }
    }

    @Override
    public void removed() {
        if (this.client != null) {
            this.client.getSoundManager().stopSounds(new Identifier("mesutcmod", "once_upon_a_time"), SoundCategory.MUSIC);
        }
        super.removed();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        long now = System.currentTimeMillis();
        long elapsed = now - slideStartedAt;
        long totalDuration = fadeOutStart + fadeOutDuration + blackGap;
        if (elapsed >= totalDuration) {
            nextSlide();
            if (this.client == null || this.client.currentScreen != this) return;
            elapsed = System.currentTimeMillis() - slideStartedAt;
        }

        // Undertale-inspired framing: black screen with the art centered, not stretched edge-to-edge.
        context.fill(0, 0, this.width, this.height, 0xFF000000);
        int textureWidth = 1920;
        int textureHeight = 1080;
        // Slaytlar artık metin kutusuyla birlikte tam 16:9 görsel; ekrana büyükçe ve ortalı sığdır.
        double scale = Math.min((this.width * 0.94) / textureWidth, (this.height * 0.90) / textureHeight);
        int drawWidth = Math.max(1, (int) (textureWidth * scale));
        int drawHeight = Math.max(1, (int) (textureHeight * scale));
        int x = (this.width - drawWidth) / 2;
        int y = (this.height - drawHeight) / 2;

        // Sinematik geçiş: siyahtan yavaşça belirir, durur, sonra siyaha gömülür; kısa bir tam siyah bekleme olur.
        float fadeIn = Math.min(1.0F, elapsed / (float) FADE_IN_MS);
        float fadeOut = 1.0F - Math.min(1.0F, Math.max(0.0F, (elapsed - fadeOutStart) / (float) fadeOutDuration));
        float alpha = Math.min(fadeIn, fadeOut);
        alpha = alpha * alpha * (3.0F - 2.0F * alpha); // smoothstep: yumuşak giriş/çıkış
        // Resmi tam parlaklıkta çiz, üstüne yarı saydam siyah perde çek.
        // (Eski yöntem olan setShaderColor alfası GUI'de blend kapalı olduğu için işe yaramıyor,
        //  geçişler bu yüzden "düz" görünüyordu. fill() ise alfayı kendisi uyguluyor.)
        context.drawTexture(SLIDES[slide], x, y, drawWidth, drawHeight, 0.0F, 0.0F, textureWidth, textureHeight, textureWidth, textureHeight);
        int curtain = Math.max(0, Math.min(255, Math.round((1.0F - alpha) * 255.0F)));
        if (curtain > 0) {
            context.fill(0, 0, this.width, this.height, (curtain << 24));
        }

        String skip = "SPACE: sonraki  ▶";
        int textWidth = this.textRenderer.getWidth(skip);
        context.drawTextWithShadow(this.textRenderer, skip, this.width - textWidth - 14, this.height - 18, 0xFFFFFFFF);
    }

    private void nextSlide() {
        if (slide < SLIDE_COUNT - 1) {
            slide++;
            slideStartedAt = System.currentTimeMillis();
            fadeOutStart = FADE_IN_MS + HOLD_MS;
            fadeOutDuration = FADE_OUT_MS;
            blackGap = BLACK_GAP_MS;
            skipping = false;
        } else if (this.client != null) {
            this.client.setScreen(null);
        }
    }

    /** Mevcut slaytı hızlıca siyaha söndürüp sonrakine geçer. */
    private void skipSlide() {
        if (skipping) return;
        skipping = true;
        long elapsed = System.currentTimeMillis() - slideStartedAt;
        fadeOutStart = Math.min(fadeOutStart, elapsed);
        fadeOutDuration = 450L;
        blackGap = 150L;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_SPACE || keyCode == GLFW.GLFW_KEY_ENTER
                || keyCode == GLFW.GLFW_KEY_KP_ENTER || keyCode == GLFW.GLFW_KEY_RIGHT) {
            skipSlide();
            return true;
        }
        // Prevent accidental closing with Escape; the intro should finish or be skipped with SPACE.
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        skipSlide();
        return true;
    }

    @Override
    public boolean shouldPause() {
        return true;
    }
}
