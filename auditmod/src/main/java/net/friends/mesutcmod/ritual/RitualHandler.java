package net.friends.mesutcmod.ritual;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.Blocks;
import net.minecraft.block.LecternBlock;
import net.minecraft.block.entity.LecternBlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.friends.mesutcmod.item.ModItems;
import net.friends.mesutcmod.world.MesutChristWorld;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.world.World;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class RitualHandler {
    private static final String REQUIRED_PHRASE = "i summon ahmet kaya";
    private static final int OUTER_LECTERN_DISTANCE = 1;

    public static void register() {
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (world.isClient) return ActionResult.PASS;
            BlockPos pos = hit.getBlockPos();
            // Mesut Key ritual: the center is now an EMPTY lectern. Right-click it with the key.
            if (world.getBlockState(pos).isOf(Blocks.LECTERN) && player.getStackInHand(hand).isOf(ModItems.MESUT_ANAHTARI)) {
                if (performMesutKeyRitual((ServerWorld) world, pos, player, hand)) return ActionResult.SUCCESS;
                return ActionResult.PASS;
            }
            if (!world.getBlockState(pos).isOf(Blocks.LECTERN)) return ActionResult.PASS;
            if (!(world.getBlockEntity(pos) instanceof LecternBlockEntity lectern)) return ActionResult.PASS;

            ItemStack book = lectern.getBook();
            if (book.isEmpty() || !book.isOf(Items.WRITTEN_BOOK)) {
                if (player instanceof ServerPlayerEntity sp)
                    sp.sendMessage(Text.literal("[Ritüel] Ortadaki kürsüde imzalı kitap yok."), false);
                return ActionResult.PASS;
            }

            if (!bookHasCorrectPhrase(book, player)) return ActionResult.PASS;
            if (!areAllFourCluesPlaced(world, pos)) {
                if (player instanceof ServerPlayerEntity sp)
                    sp.sendMessage(Text.literal("[Ritüel] Dört notun da çevredeki dört kürsüde olması ve ışıkların doğru dizilmesi gerekiyor."), false);
                return ActionResult.PASS;
            }

            performRitual(world, pos);
            return ActionResult.SUCCESS;
        });
    }

    private static boolean bookHasCorrectPhrase(ItemStack book, net.minecraft.entity.player.PlayerEntity player) {
        if (!book.hasNbt() || !book.getNbt().contains("pages", 9)) {
            if (player instanceof ServerPlayerEntity sp)
                sp.sendMessage(Text.literal("[Ritüel] Kitabın sayfası boş görünüyor."), false);
            return false;
        }
        NbtCompound nbt = book.getNbt();
        if (!nbt.contains("author", 8) || !nbt.contains("title", 8)) {
            if (player instanceof ServerPlayerEntity sp)
                sp.sendMessage(Text.literal("[Ritüel] Bu kitap imzalanmış olmalı."), false);
            return false;
        }
        NbtList pages = nbt.getList("pages", 8);
        if (pages.isEmpty()) return false;
        String normalized = pages.getString(0).trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        return normalized.contains(REQUIRED_PHRASE);
    }

    /**
     * Ritual layout:
     *
     *                 [NORTH LECTERN]
     *                  [5 GLOWSTONE]
     *
     * [4 GLOWSTONE] [CENTER LECTERN] [4 GLOWSTONE]
     * [WEST LECTERN]                 [EAST LECTERN]
     *
     *                 [SOUTH LECTERN]
     *                  [4 GLOWSTONE]
     *
     * The four outer lecterns each hold one different clue book.
     * The outer lecterns sit directly next to the center lectern.
     * Four glowstones run outward from each outer lectern; the north side has five.
     */
    private static boolean areAllFourCluesPlaced(World world, BlockPos center) {
        Set<Integer> found = new HashSet<>();
        Direction[] directions = new Direction[]{
                Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST
        };

        for (Direction dir : directions) {
            BlockPos lecternPos = center.offset(dir, OUTER_LECTERN_DISTANCE);
            if (!world.getBlockState(lecternPos).isOf(Blocks.LECTERN)) return false;

            if (!(world.getBlockEntity(lecternPos) instanceof LecternBlockEntity lectern)) return false;
            ItemStack stack = lectern.getBook();
            if (!isClueBook(stack)) return false;

            NbtCompound nbt = stack.getNbt();
            int clue = nbt.getInt("MesutCModClue");
            if (clue < 1 || clue > 4 || !found.add(clue)) return false;

            int glowstones = dir == Direction.NORTH ? 5 : 4;
            for (int i = 1; i <= glowstones; i++) {
                BlockPos glowPos = center.offset(dir, OUTER_LECTERN_DISTANCE + i);
                if (!world.getBlockState(glowPos).isOf(Blocks.GLOWSTONE)) return false;
            }
        }

        return found.size() == 4;
    }

    private static boolean isClueBook(ItemStack stack) {
        if (stack.isEmpty() || !stack.isOf(Items.WRITTEN_BOOK) || !stack.hasNbt()) return false;
        NbtCompound nbt = stack.getNbt();
        if (nbt == null || !nbt.contains("MesutCModClue", 3)) return false;
        int clue = nbt.getInt("MesutCModClue");
        if (clue < 1 || clue > 4) return false;
        if (!nbt.contains("pages", 9)) return false;
        NbtList pages = nbt.getList("pages", 8);
        return !pages.isEmpty();
    }

    private static void performRitual(World world, BlockPos pos) {
        if (world.getBlockEntity(pos) instanceof LecternBlockEntity lectern)
            lectern.setBook(ItemStack.EMPTY);
        world.setBlockState(pos, Blocks.AIR.getDefaultState(), 3);

        for (Direction dir : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
            BlockPos lecternPos = pos.offset(dir, OUTER_LECTERN_DISTANCE);
            world.setBlockState(lecternPos, Blocks.AIR.getDefaultState(), 3);

            int glowstones = dir == Direction.NORTH ? 5 : 4;
            for (int i = 1; i <= glowstones; i++) {
                BlockPos glowPos = pos.offset(dir, OUTER_LECTERN_DISTANCE + i);
                world.setBlockState(glowPos, Blocks.AIR.getDefaultState(), 3);
            }
        }

        world.playSound(null, pos, SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.HOSTILE, .5f, .65f);
        if (world instanceof ServerWorld server) {
            server.spawnParticles(net.minecraft.particle.ParticleTypes.SOUL_FIRE_FLAME,
                    pos.getX() + .5, pos.getY() + 1, pos.getZ() + .5,
                    70, .7, .8, .7, .04);
            // The boss is intentionally delayed. The story sequence gathers everyone first.
            StoryHandler.startRitualSequence(server, pos);
        }
    }

    /**
     * New Mesut Key ritual.
     * Center: lodestone. The key is physically used on it.
     * Around it: four crying-obsidian anchors, four chiseled-blackstone corner seals,
     * and four soul lanterns on the outer diagonals. This deliberately does not reuse
     * the old four-relic-drop altar.
     */
    private static boolean performMesutKeyRitual(ServerWorld world, BlockPos c,
                                                   net.minecraft.entity.player.PlayerEntity player,
                                                   net.minecraft.util.Hand hand) {
        // The lectern itself is the trigger. The surrounding glowstone is purely the visual ritual.
        // This intentionally does not block teleportation if a player built the structure slightly differently.
        if (!world.getBlockState(c).isOf(Blocks.LECTERN)) return false;
        if (world.getBlockEntity(c) instanceof LecternBlockEntity lectern && !lectern.getBook().isEmpty()) return false;

        player.getStackInHand(hand).decrement(1);
        world.playSound(null, c, SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.BLOCKS, 1.5F, .55F);
        world.playSound(null, c, SoundEvents.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.HOSTILE, 1.2F, .45F);
        world.spawnParticles(net.minecraft.particle.ParticleTypes.REVERSE_PORTAL,
                c.getX()+.5, c.getY()+1.0, c.getZ()+.5, 320, 2.2, 2.8, 2.2, .08);
        world.spawnParticles(net.minecraft.particle.ParticleTypes.SOUL_FIRE_FLAME,
                c.getX()+.5, c.getY()+1.0, c.getZ()+.5, 140, 1.5, 1.2, 1.5, .035);
        world.spawnParticles(net.minecraft.particle.ParticleTypes.ENCHANT,
                c.getX()+.5, c.getY()+1.0, c.getZ()+.5, 100, 2.0, 2.0, 2.0, .25);
        player.sendMessage(Text.literal("Mesut Anahtarı kabul edildi. Mesut C'ye ışınlanıyorsunuz..."), false);

        // No portal block is needed for now: the key directly opens the way to Mesut C.
        MesutChristWorld.teleportParty(world.getServer());
        StoryHandler.startMesutEncounter(world.getServer());
        return true;
    }

    private static boolean validMesutKeyRitual(World world, BlockPos c) {
        // New simple Mesut Key ritual:
        //
        //              G  <- 6th glowstone
        //              G
        //              G  <- 4th glowstone has a 2-block arm on both sides
        //          G G G G G
        //              G
        //              G
        //            LECTERN
        //
        // The lectern is empty. The key is used by right-clicking the lectern.
        if (!world.getBlockState(c).isOf(Blocks.LECTERN)) return false;
        if (world.getBlockEntity(c) instanceof LecternBlockEntity lectern && !lectern.getBook().isEmpty()) return false;

        // Six glowstones directly above the empty lectern.
        for (int y = 1; y <= 6; y++) {
            if (!world.getBlockState(c.up(y)).isOf(Blocks.GLOWSTONE)) return false;
        }

        // On the 4th glowstone, two extra glowstones extend in each direction.
        // Accept either horizontal orientation so the ritual is not locked to one facing.
        BlockPos crossCenter = c.up(4);
        boolean eastWest = true;
        for (int distance = 1; distance <= 2; distance++) {
            if (!world.getBlockState(crossCenter.east(distance)).isOf(Blocks.GLOWSTONE)
                    || !world.getBlockState(crossCenter.west(distance)).isOf(Blocks.GLOWSTONE)) {
                eastWest = false;
                break;
            }
        }

        boolean northSouth = true;
        for (int distance = 1; distance <= 2; distance++) {
            if (!world.getBlockState(crossCenter.north(distance)).isOf(Blocks.GLOWSTONE)
                    || !world.getBlockState(crossCenter.south(distance)).isOf(Blocks.GLOWSTONE)) {
                northSouth = false;
                break;
            }
        }

        return eastWest || northSouth;
    }

    private static void buildKeyPortal(ServerWorld world, BlockPos c) {
        // Remove only the ritual's center/inner pieces; the outer seal remains as a permanent doorway.
        for (int x=-3; x<=3; x++) for (int y=0; y<=6; y++) {
            if (Math.abs(x) <= 2 && y >= 1 && y <= 5) continue;
            world.setBlockState(c.add(x,y,0), Blocks.CRYING_OBSIDIAN.getDefaultState(), 3);
        }
        for (int y=0; y<=6; y++) {
            world.setBlockState(c.add(-3,y,0), Blocks.CRYING_OBSIDIAN.getDefaultState(), 3);
            world.setBlockState(c.add(3,y,0), Blocks.CRYING_OBSIDIAN.getDefaultState(), 3);
        }
        for (int x=-3; x<=3; x++) {
            world.setBlockState(c.add(x,0,0), Blocks.CRYING_OBSIDIAN.getDefaultState(), 3);
            world.setBlockState(c.add(x,6,0), Blocks.CRYING_OBSIDIAN.getDefaultState(), 3);
        }
        for (int x=-2; x<=2; x++) for (int y=1; y<=5; y++)
            world.setBlockState(c.add(x,y,0), Blocks.PURPLE_STAINED_GLASS.getDefaultState(), 3);
        for (int y=1; y<=5; y++) {
            world.setBlockState(c.add(-4,y,0), Blocks.POLISHED_BLACKSTONE_BRICKS.getDefaultState(), 3);
            world.setBlockState(c.add(4,y,0), Blocks.POLISHED_BLACKSTONE_BRICKS.getDefaultState(), 3);
        }
    }

}
