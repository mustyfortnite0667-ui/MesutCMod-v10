package net.friends.mesutcmod.ritual;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.friends.mesutcmod.entity.KayaEntity;
import net.friends.mesutcmod.entity.ModEntities;
import net.friends.mesutcmod.item.ModItems;
import net.friends.mesutcmod.world.MesutChristWorld;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.LecternBlockEntity;
import net.minecraft.block.LecternBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.HashSet;
import java.util.Set;

/**
 * The easy-to-discover story layer: clue chests appear in front of players while mining,
 * followed by a short horror sequence when the ritual is completed.
 */
public final class StoryHandler {
    private static final Map<UUID, Integer> PLAYER_COOLDOWNS = new HashMap<>();
    private static final Map<UUID, Integer> PLAYER_CLUES = new HashMap<>();
    private static int chestCooldown = 80;
    private static Sequence activeSequence = null;
    private static final Set<BlockPos> ACTIVE_PORTALS = new HashSet<>();

    private StoryHandler() {}

    /** Sunucu kapanınca çağrılır: yarım kalan sekans/portal gibi geçici durumu temizler. */
    public static void resetTransientState() {
        activeSequence = null;
        ACTIVE_PORTALS.clear();
        chestCooldown = 80;
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(StoryHandler::tick);
    }

    public static void startRitualSequence(ServerWorld world, BlockPos ritualPos) {
        if (activeSequence != null) return;
        activeSequence = new Sequence(world, ritualPos.toImmutable());
    }

    private static void tick(MinecraftServer server) {
        tickClueChests(server);
        tickSequence(server);
        tickMesutPortal(server);
        MesutChristWorld.tick(server);
    }

    private static void tickClueChests(MinecraftServer server) {
        PLAYER_COOLDOWNS.replaceAll((uuid, value) -> Math.max(0, value - 1));
        if (chestCooldown-- > 0) return;

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            // Spectators are observers, not story participants. They should never trigger or receive clue spawns.
            if (player.isSpectator()) continue;
            UUID uuid = player.getUuid();
            if (PLAYER_CLUES.getOrDefault(uuid, 0) >= 4) continue;
            if (PLAYER_COOLDOWNS.getOrDefault(uuid, 0) > 0) continue;
            if (!isMiningEnvironment(player)) continue;

            int clue = PLAYER_CLUES.getOrDefault(uuid, 0);
            if (spawnClueChest(player, clue)) {
                PLAYER_CLUES.put(uuid, clue + 1);
                PLAYER_COOLDOWNS.put(uuid, 1200); // 60 seconds before this player can trigger another clue.
                chestCooldown = 120;
                break;
            }
        }
    }

    private static boolean isMiningEnvironment(ServerPlayerEntity player) {
        if (player.getWorld().getRegistryKey() != World.OVERWORLD) return false;
        if (player.getY() > 63) return false;
        if (player.getWorld().isSkyVisible(player.getBlockPos().up())) return false;
        // At least one solid block close to the player keeps the event feeling like a mine/cave encounter.
        BlockPos p = player.getBlockPos();
        int solid = 0;
        for (Direction d : Direction.values()) {
            if (!player.getWorld().getBlockState(p.offset(d)).isAir()) solid++;
        }
        return solid >= 2;
    }

    public static void testClue(ServerPlayerEntity player) {
        if (player.isSpectator()) {
            player.sendMessage(Text.literal("[TEST] Spectator modundasın; ipuçları senin için spawnlanmaz."), false);
            return;
        }
        UUID uuid = player.getUuid();
        int clue = PLAYER_CLUES.getOrDefault(uuid, 0);
        if (clue >= 4) {
            player.sendMessage(Text.literal("[TEST] Bu oyuncunun 4 ipucunun tamamı zaten tamamlandı. /mesutcmod test clue <1-4> ile belirli bir aşamayı test edebilirsin."), false);
            return;
        }
        if (spawnClueChest(player, clue)) {
            PLAYER_CLUES.put(uuid, clue + 1);
            PLAYER_COOLDOWNS.put(uuid, 0);
            player.sendMessage(Text.literal("[TEST] İpucu " + (clue + 1) + "/4 oluşturuldu."), false);
        } else {
            player.sendMessage(Text.literal("[TEST] İpucu oluşturulamadı: uygun sandık konumu bulunamadı."), false);
        }
    }

    public static void testClue(ServerPlayerEntity player, int requestedClue) {
        if (player.isSpectator()) {
            player.sendMessage(Text.literal("[TEST] Spectator modundasın; ipuçları senin için spawnlanmaz."), false);
            return;
        }
        int clue = requestedClue - 1;
        if (clue < 0 || clue > 3) return;
        if (spawnClueChest(player, clue)) {
            player.sendMessage(Text.literal("[TEST] İpucu " + requestedClue + "/4 oluşturuldu."), false);
        } else {
            player.sendMessage(Text.literal("[TEST] İpucu oluşturulamadı: uygun sandık konumu bulunamadı."), false);
        }
    }

    public static void resetClues(ServerPlayerEntity player) {
        PLAYER_CLUES.remove(player.getUuid());
        PLAYER_COOLDOWNS.remove(player.getUuid());
        player.sendMessage(Text.literal("[TEST] İpucu ilerlemesi sıfırlandı. Bir sonraki normal ipucu 1/4 olacak."), false);
    }

    private static boolean spawnClueChest(ServerPlayerEntity player, int clue) {
        ServerWorld world = (ServerWorld) player.getWorld();
        Direction facing = player.getHorizontalFacing();
        BlockPos base = player.getBlockPos();
        BlockPos target = null;

        // Prefer directly in front, then nearby spots so the chest almost always appears visibly.
        BlockPos[] candidates = new BlockPos[] {
                base.offset(facing, 2),
                base.offset(facing, 3),
                base.offset(facing.rotateYClockwise(), 2),
                base.offset(facing.rotateYCounterclockwise(), 2)
        };
        for (BlockPos c : candidates) {
            if (world.getBlockState(c).isAir() && world.getBlockState(c.up()).isAir()) {
                target = c;
                break;
            }
        }
        // If the player is tunnelling into a wall, replace the block directly ahead instead of making the clue miss.
        if (target == null) {
            BlockPos c = base.offset(facing, 2);
            if (!world.getBlockState(c).isOf(Blocks.BEDROCK)) target = c;
        }
        if (target == null) return false;

        world.setBlockState(target, Blocks.CHEST.getDefaultState(), 3);
        if (!(world.getBlockEntity(target) instanceof ChestBlockEntity chest)) return false;

        chest.setCustomName(Text.literal("Tuhaf Sandık"));
        chest.setStack(0, createClueBook(clue));
        chest.setStack(1, new ItemStack(Items.GLOWSTONE, clue == 3 ? 4 : 1));
        chest.markDirty();
        world.spawnParticles(ParticleTypes.POOF, target.getX() + .5, target.getY() + .5, target.getZ() + .5,
                25, .35, .35, .35, .03);
        world.spawnParticles(ParticleTypes.SOUL, target.getX() + .5, target.getY() + 1, target.getZ() + .5,
                12, .3, .4, .3, .02);
        world.playSound(null, target, SoundEvents.BLOCK_CHEST_OPEN, SoundCategory.BLOCKS, .8F, .55F);
        player.sendMessage(Text.literal("Bir şey önünde belirdi..."), true);
        return true;
    }

    /** Spawns the post-Ahmet story altar and its Mesut Christ journal. */
    public static void spawnMesutChronicle(ServerWorld world, BlockPos deathPos) {
        // Put it just above the ground where the boss died, so it feels like the arena itself revealed it.
        BlockPos base = deathPos.toImmutable();
        BlockPos center = base;
        // If the immediate spot is cramped, try a few nearby positions.
        BlockPos[] candidates = new BlockPos[]{
                base, base.east(3), base.west(3), base.north(3), base.south(3),
                base.east(4), base.west(4), base.north(4), base.south(4)
        };
        for (BlockPos c : candidates) {
            boolean clear = true;
            for (int dx = -1; dx <= 1 && clear; dx++) for (int dz = -1; dz <= 1; dz++) {
                if (!world.getBlockState(c.add(dx, 0, dz)).isAir()
                        || !world.getBlockState(c.add(dx, 1, dz)).isAir()) {
                    clear = false;
                    break;
                }
            }
            if (clear && !world.getBlockState(c.down()).isAir()) {
                center = c;
                break;
            }
        }

        // A compact treasure altar: the lectern is the focus, surrounded by valuable blocks.
        world.setBlockState(center, Blocks.QUARTZ_BLOCK.getDefaultState(), 3);
        world.setBlockState(center.up(), Blocks.LECTERN.getDefaultState()
                .with(LecternBlock.FACING, Direction.SOUTH), 3);

        BlockPos[] ring = new BlockPos[]{
                center.north(), center.south(), center.east(), center.west(),
                center.north().east(), center.north().west(), center.south().east(), center.south().west()
        };
        net.minecraft.block.Block[] treasure = new net.minecraft.block.Block[]{
                Blocks.DIAMOND_BLOCK, Blocks.GOLD_BLOCK, Blocks.EMERALD_BLOCK, Blocks.NETHERITE_BLOCK,
                Blocks.CRYING_OBSIDIAN, Blocks.AMETHYST_BLOCK, Blocks.IRON_BLOCK, Blocks.GOLD_BLOCK
        };
        for (int i = 0; i < ring.length; i++) world.setBlockState(ring[i], treasure[i].getDefaultState(), 3);

        if (world.getBlockEntity(center.up()) instanceof LecternBlockEntity lectern) {
            lectern.setBook(createMesutChronicle());
            lectern.markDirty();
        }

        world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, center.getX() + .5, center.getY() + 1.2, center.getZ() + .5,
                90, 1.2, .8, 1.2, .025);
        world.spawnParticles(ParticleTypes.REVERSE_PORTAL, center.getX() + .5, center.getY() + 1.2, center.getZ() + .5,
                70, 1.0, .7, 1.0, .03);
        world.playSound(null, center, SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.BLOCKS, 1.2F, .55F);
    }

    private static ItemStack createMesutChronicle() {
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        NbtCompound nbt = book.getOrCreateNbt();
        NbtList pages = new NbtList();
        String[] pagesText = new String[]{
                "AHMET KAYA'NIN ARDINDAN\n\nEğer bu kitabı bulduysan, çağrıyı başarıyla tamamladın. Ama gördüğün şey yalnızca başlangıçtı.\n\nAhmet Kaya bu dünyanın sahibi değildi.",
                "YILLAR ÖNCE\n\nBir grup insan, dünyanın sınırlarının ötesinde bir varlıktan söz eden eski kayıtlar buldu.\n\nKayıtlarda tek bir isim tekrar ediliyordu:\n\nMESUT CHRIST",
                "MÜHÜRLER\n\nOnu dünyadan uzak tutmak için dört parçalı bir mühür oluşturuldu.\n\nMühür parçaları zamanla kayboldu. Ahmet Kaya'nın ölümüyle birlikte mühür yeniden parçalandı.\n\nArtık kapı yeniden açılabilir.",
                "KALE\n\nMesut Christ'ın bulunduğu yerde sıradan bir kale yoktur. Taşların arasındaki işaretler, onun orada beklediğini gösteriyor.\n\nKapıyı açmak için mühürleri bir araya getir.\n\nAma geri dönüşün olmayabilir.",
                "SON UYARI\n\nMesut Christ ile savaşmaya karar verdiyseniz, hazırlıksız gitmeyin.\n\nKalenin sırlarını araştırın ve mühürleri kullanın.\n\nBUNLAR MESUT CHRIST İLE SAVAŞACAKSANIZ LAZIM OLACAK."
        };
        for (String page : pagesText)
            pages.add(net.minecraft.nbt.NbtString.of("{\"text\":\"" + escapeJson(page) + "\"}"));
        nbt.put("pages", pages);
        nbt.putString("title", "Mesut Christ'ın Günlüğü");
        nbt.putString("author", "Bilinmeyen");
        nbt.putInt("generation", 0);
        return book;
    }

    private static ItemStack createClueBook(int clue) {
        String title;
        String[] pagesText;
        switch (clue) {
            case 0 -> {
                title = "Birinci Not - Merkez";
                pagesText = new String[]{
                        "BU NOTLARI SAKLA.\n\nRitüelin kalbi merkezdeki kürsüdür.\nDört not onu çevreleyecek.\n\nİlk not kuzeyde durur.\nKürsüsü kuzeye bakmalı.",
                        "Onun dış tarafında beş ışık taşı olacak.\n\nÇağrının ilk kelimesi:\nI\n\nBunu unutma."
                };
            }
            case 1 -> {
                title = "İkinci Not - Dört Yön";
                pagesText = new String[]{
                        "BU NOTLARI SAKLA.\n\nMerkezin doğusunda, güneyinde ve batısında da birer kürsü olmalı.\n\nHer kürsü kendi dış yönüne bakmalı.",
                        "Dört not, dört yön.\n\nHer kürsünün dış tarafında dört ışık taşı bulunmalı.\n\nAma kuzey farklıdır.\n\nÇağrının ikinci kelimesi:\nsummon"
                };
            }
            case 2 -> {
                title = "Üçüncü Not - İmza";
                pagesText = new String[]{
                        "BU NOTLARI SAKLA.\n\nDört not yerlerine konduğunda merkez boş kalır.\n\nOraya sıradan bir kitap koyma.",
                        "Merkezdeki kürsüye imzalanmış kitap gelecek.\n\nİlk sayfasındaki çağrının üçüncü kelimesi:\nahmet\n\nSözün parçalarını sırayla birleştir."
                };
            }
            default -> {
                title = "Dördüncü Not - Beşinci Işık";
                pagesText = new String[]{
                        "BU NOTLARI SAKLA.\n\nDört dış kürsü tamamlanmalı.\n\nDört kürsünün üçünde dört ışık taşı var.\nKuzeye bakan kürsüde ise beş.",
                        "Merkezde imzalı çağırma kitabı.\nÇevresinde dört not.\n\nÇağrının son kelimesi:\nkaya\n\nDört parçayı sırayla birleştir:\nI / summon / ahmet / kaya"
                };
            }
        }

        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        NbtCompound nbt = book.getOrCreateNbt();
        NbtList pages = new NbtList();
        for (String pageText : pagesText) {
            pages.add(net.minecraft.nbt.NbtString.of("{\"text\":\"" + escapeJson(pageText) + "\"}"));
        }
        nbt.put("pages", pages);
        nbt.putString("title", title);
        nbt.putString("author", "Bilinmeyen");
        nbt.putInt("generation", 0);
        nbt.putInt("MesutCModClue", clue + 1);
        return book;
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    // Mesut Key portal: the key ritual itself is handled by RitualHandler.
    public static void activateMesutKeyPortal(BlockPos center) {
        ACTIVE_PORTALS.add(center.toImmutable());
    }

    private static void tickMesutPortal(MinecraftServer server) {
        if (ACTIVE_PORTALS.isEmpty()) return;
        for (BlockPos center : new HashSet<>(ACTIVE_PORTALS)) {
            ServerWorld overworld = server.getOverworld();
            if (!overworld.getBlockState(center.add(0,3,0)).isOf(Blocks.PURPLE_STAINED_GLASS)) {
                ACTIVE_PORTALS.remove(center);
                continue;
            }
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                if (p.isSpectator() || p.getServerWorld() != overworld) continue;
                if (Math.abs(p.getX()-(center.getX()+.5)) < 1.5 && Math.abs(p.getY()-(center.getY()+3)) < 3.0 && Math.abs(p.getZ()-(center.getZ()+.5)) < 1.5) {
                    MesutChristWorld.teleportParty(server);
                    startMesutEncounter(server);
                    return;
                }
            }
        }
    }

    public static void startMesutEncounter(MinecraftServer server) {
        ServerWorld target = MesutChristWorld.getWorld(server);
        if (target == null) return;
        boolean alreadyAlive = !target.getEntitiesByClass(
                net.friends.mesutcmod.entity.MesutChristEntity.class,
                new net.minecraft.util.math.Box(-100000, 0, -100000, 100000, 256, 100000),
                e -> e.isAlive()
        ).isEmpty();
        if (alreadyAlive) return;
        MesutChristWorld.prepare(server);
        target.getPlayers().forEach(p -> p.sendMessage(Text.literal("Mesut Christ seni bekliyordu."), false));
        // Boss spawns shortly after arrival so the party has a moment to see the castle.
        ServerTickEvents.END_SERVER_TICK.register(new ServerTickEvents.EndTick() {
            int timer = 80;
            boolean done;
            @Override public void onEndTick(MinecraftServer s) {
                if (done) return;
                if (--timer <= 0) {
                    MesutChristWorld.spawnBoss(target);
                    done = true;
                }
            }
        });
    }

    private static void tickSequence(MinecraftServer server) {
        if (activeSequence == null) return;
        activeSequence.tick(server);
        if (activeSequence.finished) activeSequence = null;
    }

    private static final class Sequence {
        final ServerWorld world;
        final BlockPos pos;
        int tick;
        boolean finished;
        boolean spawned;

        Sequence(ServerWorld world, BlockPos pos) {
            this.world = world;
            this.pos = pos;
        }

        void tick(MinecraftServer server) {
            tick++;

            if (tick == 1) {
                world.playSound(null, pos, SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.HOSTILE, .35F, .55F);
                world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, pos.getX() + .5, pos.getY() + 1, pos.getZ() + .5,
                        45, .7, .8, .7, .025);
                messageAll(server, "Bir şeyler ters gitti...");
            }
            if (tick == 55) {
                messageAll(server, "Işıklar söndü.");
                world.spawnParticles(ParticleTypes.SMOKE, pos.getX() + .5, pos.getY() + 1, pos.getZ() + .5,
                        60, 1.2, 1.0, 1.2, .03);
            }
            if (tick == 95) {
                messageAll(server, "Kaçmanın bir anlamı yok.");
                world.playSound(null, pos, SoundEvents.ENTITY_ENDERMAN_STARE, SoundCategory.HOSTILE, 1.0F, .45F);
            }
            if (tick >= 120 && tick < 190 && tick % 10 == 0) {
                world.spawnParticles(ParticleTypes.REVERSE_PORTAL, pos.getX() + .5, pos.getY() + 1, pos.getZ() + .5,
                        20, 2.0, 1.0, 2.0, .04);
            }
            if (tick == 120) {
                for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                    p.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                            net.minecraft.entity.effect.StatusEffects.DARKNESS, 60, 0, false, false, true));
                }
            }
            if (tick >= 145 && tick < 205 && tick % 5 == 0) {
                pullPlayers(server, 0.10D);
            }
            if (tick == 205) {
                teleportEveryoneToRitual(server);
                messageAll(server, "Artık hepiniz buradasınız.");
            }
            if (tick >= 215 && tick < 275 && tick % 5 == 0) {
                world.spawnParticles(ParticleTypes.SOUL, pos.getX() + .5, pos.getY() + 1, pos.getZ() + .5,
                        55, 2.4, 1.2, 2.4, .02);
                world.spawnParticles(ParticleTypes.PORTAL, pos.getX() + .5, pos.getY() + 1, pos.getZ() + .5,
                        30, 1.8, 1.0, 1.8, .06);
            }
            if (tick == 270) {
                world.playSound(null, pos, SoundEvents.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.HOSTILE, 1.8F, .5F);
                world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, pos.getX() + .5, pos.getY() + 1, pos.getZ() + .5,
                        3, .5, .8, .5, 0);
                world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, pos.getX() + .5, pos.getY() + 1, pos.getZ() + .5,
                        220, 2.0, 2.2, 2.0, .06);
            }
            if (tick == 300 && !spawned) {
                spawned = true;
                KayaEntity kaya = new KayaEntity(ModEntities.KAYA, world);
                kaya.refreshPositionAndAngles(pos.getX() + .5, pos.getY() + 1, pos.getZ() + .5, 0, 0);
                world.spawnEntity(kaya);
                world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, pos.getX() + .5, pos.getY() + 1, pos.getZ() + .5,
                        5, .7, 1.0, .7, 0);
                world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, pos.getX() + .5, pos.getY() + 1, pos.getZ() + .5,
                        300, 2.2, 2.5, 2.2, .08);
                world.spawnParticles(ParticleTypes.REVERSE_PORTAL, pos.getX() + .5, pos.getY() + 1, pos.getZ() + .5,
                        120, 2.0, 2.0, 2.0, .09);
                world.playSound(null, pos, SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.HOSTILE, 2.0F, .65F);
            }
            if (tick >= 320) finished = true;
        }

        void messageAll(MinecraftServer server, String msg) {
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                p.sendMessage(Text.literal(msg), false);
            }
        }

        void pullPlayers(MinecraftServer server, double strength) {
            Vec3d center = Vec3d.ofCenter(pos).add(0, 1, 0);
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                if (p.getServerWorld() != world) continue;
                Vec3d delta = center.subtract(p.getPos());
                if (delta.lengthSquared() < 4) continue;
                p.setVelocity(p.getVelocity().multiply(.75).add(delta.normalize().multiply(strength)));
                p.velocityModified = true;
            }
        }

        void teleportEveryoneToRitual(MinecraftServer server) {
            double angle = 0;
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                double x = pos.getX() + .5 + Math.cos(angle) * 3.0;
                double z = pos.getZ() + .5 + Math.sin(angle) * 3.0;
                p.teleport(world, x, pos.getY() + 1, z, p.getYaw(), p.getPitch());
                angle += Math.PI * 2.0 / Math.max(1, server.getPlayerManager().getCurrentPlayerCount());
            }
        }
    }
}
