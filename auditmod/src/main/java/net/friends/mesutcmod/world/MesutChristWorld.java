package net.friends.mesutcmod.world;

import net.friends.mesutcmod.entity.MesutChristEntity;
import net.friends.mesutcmod.entity.MidasEntity;
import net.friends.mesutcmod.entity.ModEntities;
import net.friends.mesutcmod.MesutCMod;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.GameRules;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.text.Text;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;

import java.io.InputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Mesut C world: an ocean dimension using the supplied Montspire Castle map.
 * The castle's Anvil region files are bundled with the mod and installed into
 * the dimension save folder on first use. Chunks outside the supplied map are
 * generated as a simple deep-ocean world.
 */
public final class MesutChristWorld {
    public static final RegistryKey<net.minecraft.world.World> WORLD_KEY = RegistryKey.of(
            RegistryKeys.WORLD, new Identifier("mesutcmod", "mesut_christ"));

    private static boolean prepared;
    private static int lootScanCooldown;
    private static final String MAP_INSTALL_VERSION = "montspire-map-v2";
    private static final String[] MONT_SPIRE_REGIONS = new String[] {
        "r.-2.-2.mca", "r.-2.-1.mca", "r.-2.0.mca",
        "r.-1.-2.mca", "r.-1.-1.mca", "r.-1.0.mca", "r.-1.1.mca",
        "r.0.-2.mca", "r.0.-1.mca", "r.0.0.mca", "r.0.1.mca",
        "r.1.-2.mca", "r.1.-1.mca", "r.1.0.mca", "r.1.1.mca"
    };

    private MesutChristWorld() {}

    /** Sunucu kapanınca çağrılır: static durumun bir sonraki dünyaya sızmasını engeller. */
    public static void reset() {
        prepared = false;
        lootScanCooldown = 0;
    }

    public static ServerWorld getWorld(MinecraftServer server) {
        return server.getWorld(WORLD_KEY);
    }

    public static void prepare(MinecraftServer server) {
        if (prepared) return;
        try {
            installMontspireMap(server);
            ServerWorld mesutWorld = getWorld(server);
            if (mesutWorld != null) {
                // Mesut boyutunda doğal mob spawnlarını tamamen kapat.
                // Bosslar ve modun kendi entityleri world.spawnEntity ile doğrudan oluşturulabildiği için çalışmaya devam eder.
                mesutWorld.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(false, server);
                mesutWorld.getGameRules().get(GameRules.COMMAND_BLOCK_OUTPUT).set(false, server);
            }
            prepared = true;
            MesutCMod.LOGGER.info("[MesutCMod] Montspire Castle okyanus haritası hazırlandı; doğal mob spawnları kapatıldı.");
        } catch (IOException e) {
            MesutCMod.LOGGER.error("[MesutCMod] Montspire Castle haritası kurulamadı.", e);
        }
    }

    private static void installMontspireMap(MinecraftServer server) throws IOException {
        Path dimensionRoot = server.getSavePath(WorldSavePath.ROOT)
                .resolve("dimensions")
                .resolve("mesutcmod")
                .resolve("mesut_christ");
        Path regionDir = dimensionRoot.resolve("region");
        Files.createDirectories(regionDir);
        Path versionFile = dimensionRoot.resolve("montspire-map-version.txt");
        boolean installMap = !Files.exists(versionFile)
                || !MAP_INSTALL_VERSION.equals(Files.readString(versionFile).trim());

        for (String region : MONT_SPIRE_REGIONS) {
            Path target = regionDir.resolve(region);
            if (!installMap && Files.exists(target) && Files.size(target) > 0) continue;
            String resource = "/montspire/region/" + region;
            try (InputStream in = MesutChristWorld.class.getResourceAsStream(resource)) {
                if (in == null) {
                    MesutCMod.LOGGER.warn("[MesutCMod] Eksik Montspire region: {}", region);
                    continue;
                }
                Files.copy(in, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        }
        if (installMap) {
            Files.writeString(versionFile, MAP_INSTALL_VERSION + System.lineSeparator());
            MesutCMod.LOGGER.info("[MesutCMod] Montspire haritası {} sürümüne güncellendi.", MAP_INSTALL_VERSION);
        }
    }


    public static void tick(MinecraftServer server) {
        ServerWorld world = getWorld(server);
        if (world == null || world.getPlayers().isEmpty()) return;
        if (lootScanCooldown-- > 0) return;
        lootScanCooldown = 20;

        MesutChestLootState state = MesutChestLootState.get(world);
        boolean changed = false;
        for (var player : world.getPlayers()) {
            int pcx = player.getChunkPos().x;
            int pcz = player.getChunkPos().z;
            // Sadece oyuncunun çevresindeki zaten yüklenmiş kale chunk'larını tarıyoruz.
            for (int cx = pcx - 8; cx <= pcx + 8; cx++) {
                for (int cz = pcz - 8; cz <= pcz + 8; cz++) {
                    if (!world.getChunkManager().isChunkLoaded(cx, cz)) continue;
                    var chunk = world.getChunk(cx, cz);
                    for (BlockEntity be : chunk.getBlockEntities().values()) {
                        if (!(be instanceof ChestBlockEntity) && !(be instanceof BarrelBlockEntity) && !(be instanceof ShulkerBoxBlockEntity)) continue;
                        BlockPos pos = be.getPos();
                        if (state.isAssigned(pos)) continue;

                        // Shulker kutuları yalnızca bodrumda özel, çok değerli loot alır.
                        if (be instanceof ShulkerBoxBlockEntity) {
                            if (pos.getY() > 130) continue;
                            ((ShulkerBoxBlockEntity) be).setLootTable(
                                    new Identifier(MesutCMod.MOD_ID, "chests/montspire_shulker"),
                                    world.getRandom().nextLong());
                            be.markDirty();
                            state.markAssigned(pos);
                            changed = true;
                            continue;
                        }

                        // Çift sandıkta iki kez loot üretilmesin: yalnızca iki yarıdan biri tabloyu alır.
                        if (be instanceof ChestBlockEntity) {
                            boolean paired = false;
                            for (var dir : new net.minecraft.util.math.Direction[] {
                                    net.minecraft.util.math.Direction.NORTH, net.minecraft.util.math.Direction.SOUTH,
                                    net.minecraft.util.math.Direction.EAST, net.minecraft.util.math.Direction.WEST}) {
                                if (world.getBlockState(pos.offset(dir)).isOf(Blocks.CHEST) &&
                                        pos.asLong() > pos.offset(dir).asLong()) {
                                    paired = true;
                                    break;
                                }
                            }
                            if (paired) continue;
                        }

                        if (be instanceof ChestBlockEntity chest) {
                            chest.setLootTable(new Identifier(MesutCMod.MOD_ID, "chests/montspire"), world.getRandom().nextLong());
                            chest.markDirty();
                        } else if (be instanceof BarrelBlockEntity barrel) {
                            barrel.setLootTable(new Identifier(MesutCMod.MOD_ID, "chests/montspire"), world.getRandom().nextLong());
                            barrel.markDirty();
                        }

                        state.markAssigned(pos);
                        changed = true;
                    }
                }
            }
        }
        if (changed) state.markDirty();
    }


    public static void spawnBoss(ServerWorld world) {
        // Mesut C karşılaşması artık Midas sinematiğiyle başlar.
        if (!world.getEntitiesByType(ModEntities.MIDAS, e -> e.isAlive()).isEmpty()) return;
        if (!world.getEntitiesByType(ModEntities.MESUT_CHRIST, e -> e.isAlive()).isEmpty()) return;

        MidasEntity midas = new MidasEntity(ModEntities.MIDAS, world);
        midas.refreshPositionAndAngles(21.31, 95.50, -7.63, 135.0F, 2.9F);
        midas.setBodyYaw(135.0F);
        midas.setHeadYaw(135.0F);
        world.spawnEntity(midas);
        world.spawnParticles(net.minecraft.particle.ParticleTypes.END_ROD,
                21.31, 96.50, -7.63, 60, 1.0, 1.0, 1.0, .025);
        world.playSound(null, new BlockPos(21,95,-8), SoundEvents.BLOCK_BEACON_AMBIENT,
                SoundCategory.HOSTILE, 1.2F, 1.0F);
    }

    public static void teleportParty(MinecraftServer server) {
        prepare(server);
        ServerWorld target = getWorld(server);
        if (target == null) return;

        for (var p : server.getPlayerManager().getPlayerList()) {
            if (p.isSpectator()) continue;
            // All players arrive at the requested exact introduction point and face the same direction.
            p.teleport(target, 10.39, 96.06, -14.00, 106.0F, 2.9F);
            p.setSpawnPoint(WORLD_KEY, new BlockPos(10, 96, -14), 106.0F, true, false);
            p.sendMessage(Text.literal("Suların ortasında bir kale yükseliyor..."), false);
        }
    }
}
