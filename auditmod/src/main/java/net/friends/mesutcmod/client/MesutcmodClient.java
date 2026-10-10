package net.friends.mesutcmod.client;

import net.fabricmc.api.ClientModInitializer;
import net.friends.mesutcmod.entity.ModEntities;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public class MesutcmodClient implements ClientModInitializer {
    private boolean introShownThisVisit = false;

    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(ModEntities.KAYA, KayaRenderer::new);
        EntityRendererRegistry.register(ModEntities.MESUT_CHRIST, MesutChristRenderer::new);
        EntityRendererRegistry.register(ModEntities.MIDAS, MidasRenderer::new);
        EntityRendererRegistry.register(ModEntities.RIAS, RiasRenderer::new);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // Minecraft'ın kendi müziği HİÇ çalmasın (menüde de oyunda da). Müzik takipçisi her tick
            // durdurulunca sonraki parça için sayaç hiç dolmuyor. Modun kendi müziği (Once Upon a Time)
            // bu takipçiden bağımsız çalıştığı için etkilenmez; jukebox/plak müzikleri de etkilenmez.
            client.getMusicTracker().stop();

            if (client.player == null || client.world == null) {
                // Dünyadan çıkıldı (ana menüye dönüldü): bir sonraki girişte intro tekrar gösterilsin.
                introShownThisVisit = false;
                return;
            }
            // Dünyaya girince (boyut değişince değil) intro bir kez gösterilir.
            // Yükleme ekranı kapanana kadar (currentScreen == null olana kadar) beklenir.
            if (!introShownThisVisit && client.currentScreen == null) {
                introShownThisVisit = true;
                client.setScreen(new StoryIntroScreen());
            }
        });
    }
}
