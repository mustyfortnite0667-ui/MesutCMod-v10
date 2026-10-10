package net.friends.mesutcmod;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.friends.mesutcmod.entity.KayaEntity;
import net.friends.mesutcmod.entity.ModEntities;
import net.friends.mesutcmod.item.ModItems;
import net.friends.mesutcmod.ritual.RitualHandler;
import net.friends.mesutcmod.ritual.StoryHandler;
import net.friends.mesutcmod.world.MesutChristWorld;
import net.friends.mesutcmod.sound.ModSounds;
import net.minecraft.entity.EntityType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MesutCMod implements ModInitializer {
    public static final String MOD_ID="mesutcmod";
    public static final Logger LOGGER=LoggerFactory.getLogger(MOD_ID);
    @Override public void onInitialize(){ModItems.register();ModEntities.register();ModSounds.register();RitualHandler.register();StoryHandler.register();registerTestCommands();
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            MesutChristWorld.reset();
            StoryHandler.resetTransientState();
        });
        LOGGER.info("[MesutCMod] Yükleme tamamlandı.");}
    private static void registerTestCommands(){
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            LiteralArgumentBuilder<ServerCommandSource> test = CommandManager.literal("test");

            test.then(CommandManager.literal("clue")
                .executes(ctx -> {
                    var p = ctx.getSource().getPlayerOrThrow();
                    StoryHandler.testClue(p);
                    return 1;
                })
                .then(CommandManager.argument("stage", IntegerArgumentType.integer(1, 4)).executes(ctx -> {
                    var p = ctx.getSource().getPlayerOrThrow();
                    StoryHandler.testClue(p, IntegerArgumentType.getInteger(ctx, "stage"));
                    return 1;
                }))
            );

            test.then(CommandManager.literal("clue_reset").executes(ctx -> {
                var p = ctx.getSource().getPlayerOrThrow();
                StoryHandler.resetClues(p);
                return 1;
            }));

            test.then(CommandManager.literal("horror").executes(ctx -> {
                var p = ctx.getSource().getPlayerOrThrow();
                StoryHandler.startRitualSequence(p.getServerWorld(), p.getBlockPos());
                p.sendMessage(Text.literal("[TEST] Horror sequence başlatıldı."), false);
                return 1;
            }));

            test.then(CommandManager.literal("boss").executes(ctx -> {
                var p = ctx.getSource().getPlayerOrThrow();
                KayaEntity kaya = new KayaEntity(ModEntities.KAYA, p.getServerWorld());
                BlockPos pos = p.getBlockPos().up();
                kaya.refreshPositionAndAngles(pos.getX()+.5, pos.getY(), pos.getZ()+.5, p.getYaw(), 0);
                p.getServerWorld().spawnEntity(kaya);
                p.sendMessage(Text.literal("[TEST] Ahmet Kaya spawnlandı."), false);
                return 1;
            }));

            test.then(CommandManager.literal("phase2").executes(ctx -> {
                var p = ctx.getSource().getPlayerOrThrow();
                KayaEntity kaya = spawnTestKaya(p);
                kaya.debugForcePhase(2);
                return 1;
            }));

            test.then(CommandManager.literal("final").executes(ctx -> {
                var p = ctx.getSource().getPlayerOrThrow();
                KayaEntity kaya = spawnTestKaya(p);
                kaya.debugForcePhase(3);
                return 1;
            }));

            test.then(CommandManager.literal("berserk").executes(ctx -> {
                var p = ctx.getSource().getPlayerOrThrow();
                KayaEntity kaya = spawnTestKaya(p);
                kaya.debugForcePhase(4);
                return 1;
            }));

            test.then(CommandManager.literal("mesut").executes(ctx -> {
                var p = ctx.getSource().getPlayerOrThrow();
                var server = p.getServer();
                MesutChristWorld.prepare(server);
                var world = MesutChristWorld.getWorld(server);
                if (world == null) {
                    p.sendMessage(Text.literal("[TEST] Mesut Christ boyutu yüklenemedi."), false);
                    return 0;
                }
                MesutChristWorld.teleportParty(server);
                // Test komutu artık oyuncuyu gönderdiği anda bossu da oluşturuyor.
                MesutChristWorld.spawnBoss(world);
                p.sendMessage(Text.literal("[TEST] Mesut Christ arenasına gönderildiniz ve boss çağrıldı."), false);
                return 1;
            }));

            test.then(CommandManager.literal("clear").executes(ctx -> {
                var w = ctx.getSource().getServer().getOverworld();
                int removed=0;
                for (var e : new java.util.ArrayList<>(w.getEntitiesByType(ModEntities.KAYA, x -> true))) { e.remove(net.minecraft.entity.Entity.RemovalReason.DISCARDED); removed++; }
                final int removedCount = removed;
                ctx.getSource().sendFeedback(() -> Text.literal("[TEST] " + removedCount + " Kaya entity temizlendi."), false);
                return removed;
            }));

            dispatcher.register(
                CommandManager.literal("mesutcmod")
                    .requires(source -> source.hasPermissionLevel(2))
                    .then(test)
            );
        });
    }

    private static KayaEntity spawnTestKaya(net.minecraft.server.network.ServerPlayerEntity p){
        KayaEntity kaya = new KayaEntity(ModEntities.KAYA, p.getServerWorld());
        BlockPos pos = p.getBlockPos().up();
        kaya.refreshPositionAndAngles(pos.getX()+.5, pos.getY(), pos.getZ()+.5, p.getYaw(), 0);
        p.getServerWorld().spawnEntity(kaya);
        return kaya;
    }

}
