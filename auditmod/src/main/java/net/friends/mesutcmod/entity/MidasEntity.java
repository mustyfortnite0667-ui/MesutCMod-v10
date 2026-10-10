package net.friends.mesutcmod.entity;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Midas starts as a friendly NPC. The first player who approaches triggers a
 * short cinematic: Mesut Christ appears behind him, lifts him into the air,
 * and blesses him. Midas then becomes the actual boss of this encounter.
 */
public class MidasEntity extends ZombieEntity {
    private int introState = 0; // 0 waiting, 1 dialogue, 2 Mesut appears, 3 lifting, 4 blessing, 5 boss
    private int introTimer = 0;
    private boolean introStarted = false;
    private boolean blessed = false;
    private int attackCooldown = 45;
    /** Canı bu orana (maks. canın %20'si) düşecek bir hasar gelirse Midas ölmez, yenilgi sahnesine girer. */
    private static final float DEFEAT_HEALTH_FRACTION = 0.20F;
    private static final int MESUT_ARRIVAL_DELAY_TICKS = 50; // Midas gittikten 2,5 sn sonra Mesut gelir
    private static final int DEFEAT_DURATION_TICKS = 70; // "Ne? Noluyor?" sonrası portalda yok olana kadar ~3.5 sn
    private boolean defeated = false;
    private int defeatTimer = 0;
    private final ServerBossBar bossBar = new ServerBossBar(Text.literal("Kutsanmış Midas"), BossBar.Color.YELLOW, BossBar.Style.PROGRESS);

    public MidasEntity(EntityType<? extends ZombieEntity> type, World world) {
        super(type, world);
        setPersistent();
        setCustomName(Text.literal("Midas"));
        setCustomNameVisible(true);
        setCanPickUpLoot(false);
        setAiDisabled(true);
        bossBar.setDarkenSky(true);
        bossBar.setVisible(false);
    }

    // Midas is a custom humanoid NPC, so never play inherited zombie noises or footsteps.
    @Override protected SoundEvent getAmbientSound() { return null; }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return null; }
    @Override protected SoundEvent getDeathSound() { return null; }
    @Override protected SoundEvent getStepSound() { return null; }
    @Override protected void playStepSound(net.minecraft.util.math.BlockPos pos, net.minecraft.block.BlockState state) { }

    public static net.minecraft.entity.attribute.DefaultAttributeContainer.Builder createAttributes() {
        return ZombieEntity.createZombieAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 180.0D)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 12.0D)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.32D)
                .add(EntityAttributes.GENERIC_ARMOR, 12.0D)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 0.65D)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 40.0D);
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putInt("MidasIntroState", introState);
        nbt.putInt("MidasIntroTimer", introTimer);
        nbt.putBoolean("MidasIntroStarted", introStarted);
        nbt.putBoolean("MidasBlessed", blessed);
        nbt.putBoolean("MidasDefeated", defeated);
        nbt.putInt("MidasDefeatTimer", defeatTimer);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        introState = nbt.getInt("MidasIntroState");
        introTimer = nbt.getInt("MidasIntroTimer");
        introStarted = nbt.getBoolean("MidasIntroStarted");
        blessed = nbt.getBoolean("MidasBlessed");
        defeated = nbt.getBoolean("MidasDefeated");
        defeatTimer = nbt.getInt("MidasDefeatTimer");
        if (blessed) {
            setCustomName(Text.literal("Kutsanmış Midas"));
            setAiDisabled(defeated);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (!(getWorld() instanceof ServerWorld server)) return;

        if (!blessed) {
            setAiDisabled(true);
            tickIntro(server);
            return;
        }

        if (defeated) {
            tickDefeat(server);
            return;
        }

        setAiDisabled(false);
        tickBoss(server);
    }

    private void tickIntro(ServerWorld server) {
        if (!introStarted) {
            PlayerEntity first = server.getClosestPlayer(getX(), getY(), getZ(), 4.5, p -> !p.isSpectator());
            if (first != null) {
                introStarted = true;
                introState = 1;
                introTimer = 0;
                first.sendMessage(Text.literal("Midas: Merhaba, ben Midas. Uzun zamandır Mesut Christ'ı yenmeye çalışıyorum. Başta arkadaşlarımla denedik ama hepsini kaybettim, onlara noldu bilmiyorum. Bana yardım eder misiniz?"), false);
                server.spawnParticles(ParticleTypes.END_ROD, getX(), getY() + 1, getZ(), 35, .45, .7, .45, .02);
            }
            return;
        }

        introTimer++;
        if (introState == 1 && introTimer >= 120) { // 6 saniye (120 tick)
            introState = 2;
            introTimer = 0;
            spawnMesutBehind(server);
        } else if (introState == 2 && introTimer >= 100) {
            for (ServerPlayerEntity player : server.getPlayers()) {
                player.sendMessage(Text.literal("Mesut Christ: Midas, sonunda seni buldum."), false);
            }
            introState = 3;
            introTimer = 0;
            setNoGravity(true);
            playSound(SoundEvents.ENTITY_ENDERMAN_TELEPORT, 1.5F, .55F);
        } else if (introState == 3) {
            liftSequence(server);
            if (introTimer >= 240) {
                introState = 4;
                introTimer = 0;
            }
        } else if (introState == 4) {
            blessingSequence(server);
            if (introTimer >= 240) {
                finishBlessing(server);
            }
        }
    }

    private void spawnMesutBehind(ServerWorld server) {
        if (!server.getEntitiesByType(ModEntities.MESUT_CHRIST, e -> e.isAlive()).isEmpty()) return;
        MesutChristEntity mesut = new MesutChristEntity(ModEntities.MESUT_CHRIST, server);
        mesut.refreshPositionAndAngles(getX(), getY(), getZ() - 3.2, getYaw() + 180, 0);
        mesut.setAiDisabled(true);
        mesut.setCinematicMode(true);
        mesut.setTarget(null);
        server.spawnEntity(mesut);
        server.spawnParticles(ParticleTypes.REVERSE_PORTAL, mesut.getX(), mesut.getY() + 1, mesut.getZ(), 180, 1.2, 1.8, 1.2, .08);
        server.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, mesut.getX(), mesut.getY() + 1, mesut.getZ(), 70, .8, 1.0, .8, .03);
        server.playSound(null, mesut.getBlockPos(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.HOSTILE, 1.8F, .45F);
    }

    private void liftSequence(ServerWorld server) {
        double newY = getY() + 0.045D;
        requestTeleport(getX(), newY, getZ());

        MesutChristEntity mesut = server.getEntitiesByType(ModEntities.MESUT_CHRIST, e -> e.isAlive()).stream().findFirst().orElse(null);
        if (mesut != null) {
            mesut.setAiDisabled(true);
            mesut.refreshPositionAndAngles(getX(), getY() - 2.0, getZ() - 1.2, getYaw() + 180, 0);
        }

        server.spawnParticles(ParticleTypes.END_ROD, getX(), getY() + .2, getZ(), 10, .5, .7, .5, .025);
        if (introTimer % 4 == 0) {
            server.spawnParticles(ParticleTypes.GLOW, getX(), getY() + 1, getZ(), 10, .45, .8, .45, .01);
            server.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING, getX(), getY() + 1, getZ(), 6, .4, .8, .4, .08);
        }
    }

    private void blessingSequence(ServerWorld server) {
        setGlowing(true);
        // Deliberate, rapid chat spam throughout the entire blessing cutscene.
        for (ServerPlayerEntity player : server.getPlayers()) {
            player.sendMessage(Text.literal("Midas: AAAAAAAA"), false);
        }
        addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 80, 0, false, false, true));
        server.spawnParticles(ParticleTypes.END_ROD, getX(), getY() + 1, getZ(), 40, 1.1, 1.4, 1.1, .04);
        server.spawnParticles(ParticleTypes.GLOW, getX(), getY() + 1, getZ(), 30, 1.0, 1.2, 1.0, .02);
        server.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING, getX(), getY() + 1, getZ(), 18, .8, 1.2, .8, .1);
        if (introTimer % 10 == 0) {
            server.playSound(null, getBlockPos(), SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.HOSTILE, 1.4F, 1.15F + introTimer * .002F);
        }
    }

    private void finishBlessing(ServerWorld server) {
        blessed = true;
        introState = 5;
        introTimer = 0;
        setNoGravity(false);
        setAiDisabled(false);
        setCustomName(Text.literal("Kutsanmış Midas"));
        setCustomNameVisible(true);
        setHealth(getMaxHealth());
        bossBar.setVisible(true);
        bossBar.setPercent(1.0F);
        server.spawnParticles(ParticleTypes.FLASH, getX(), getY() + 1, getZ(), 1, 0, 0, 0, 0);
        server.spawnParticles(ParticleTypes.END_ROD, getX(), getY() + 1, getZ(), 180, 2.0, 2.0, 2.0, .08);
        server.spawnParticles(ParticleTypes.GLOW, getX(), getY() + 1, getZ(), 120, 1.7, 1.8, 1.7, .03);
        server.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING, getX(), getY() + 1, getZ(), 60, 1.5, 2.0, 1.5, .12);
        server.playSound(null, getBlockPos(), SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.HOSTILE, 2.0F, .55F);
        MesutChristEntity mesut = server.getEntitiesByType(ModEntities.MESUT_CHRIST, e -> e.isAlive()).stream().findFirst().orElse(null);
        if (mesut != null) {
            server.spawnParticles(ParticleTypes.REVERSE_PORTAL, mesut.getX(), mesut.getY() + 1, mesut.getZ(), 120, 1, 1, 1, .06);
            mesut.remove(net.minecraft.entity.Entity.RemovalReason.DISCARDED);
        }
    }

    private void tickBoss(ServerWorld server) {
        updateBossBar(server);
        LivingEntity target = getTarget();
        if (target == null || !target.isAlive() || squaredDistanceTo(target) > 40 * 40) {
            target = server.getClosestPlayer(getX(), getY(), getZ(), 40.0, p -> !p.isSpectator());
            if (target != null) setTarget(target);
        }
        if (target == null) return;

        if (attackCooldown > 0) attackCooldown--;
        if (attackCooldown <= 0) {
            useBlessedAttack(server, target);
            attackCooldown = 45 + server.getRandom().nextInt(30);
        }
    }

    private void useBlessedAttack(ServerWorld server, LivingEntity target) {
        int roll = server.getRandom().nextInt(100);
        if (roll < 35) goldenWave(server, target);
        else if (roll < 70) goldenBolt(server, target);
        else goldenRain(server, target);
    }

    private void goldenWave(ServerWorld server, LivingEntity target) {
        double radius = 5.5;
        for (int i = 0; i < 48; i++) {
            double a = Math.PI * 2 * i / 48.0;
            server.spawnParticles(ParticleTypes.GLOW, getX() + Math.cos(a) * radius, getY() + .1, getZ() + Math.sin(a) * radius, 2, .03, .05, .03, .01);
        }
        Box area = getBoundingBox().expand(radius);
        for (LivingEntity e : server.getEntitiesByClass(LivingEntity.class, area, x -> x != this && x.isAlive() && !(x instanceof MidasEntity))) {
            e.damage(getWorld().getDamageSources().mobAttack(this), 8.0F);
            e.takeKnockback(1.0D, e.getX() - getX(), e.getZ() - getZ());
            e.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 60, 0));
        }
        playSound(SoundEvents.BLOCK_BELL_USE, 1.5F, .7F);
    }

    private void goldenBolt(ServerWorld server, LivingEntity target) {
        Vec3d start = getPos().add(0, 1.4, 0);
        Vec3d end = target.getPos().add(0, target.getHeight() * .5, 0);
        Vec3d dir = end.subtract(start).normalize();
        for (int i = 1; i <= 20; i++) {
            Vec3d p = start.add(dir.multiply(i * 1.1));
            server.spawnParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 5, .05, .05, .05, .01);
        }
        if (squaredDistanceTo(target) <= 24 * 24) {
            target.damage(getWorld().getDamageSources().mobAttack(this), 10.0F);
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 80, 0));
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 30, 0));
        }
        playSound(SoundEvents.BLOCK_BEACON_ACTIVATE, 1.2F, 1.3F);
    }

    private void goldenRain(ServerWorld server, LivingEntity target) {
        for (int i = 0; i < 60; i++) {
            double x = target.getX() + (server.getRandom().nextDouble() - .5) * 7;
            double z = target.getZ() + (server.getRandom().nextDouble() - .5) * 7;
            server.spawnParticles(ParticleTypes.GLOW, x, target.getY() + 3 + server.getRandom().nextDouble() * 4, z, 1, 0, 0, 0, 0);
        }
        Box area = new Box(target.getX() - 3.5, target.getY() - 1, target.getZ() - 3.5, target.getX() + 3.5, target.getY() + 6, target.getZ() + 3.5);
        for (LivingEntity e : server.getEntitiesByClass(LivingEntity.class, area, x -> x != this && x.isAlive() && !(x instanceof MidasEntity))) {
            e.damage(getWorld().getDamageSources().mobAttack(this), 7.0F);
        }
        playSound(SoundEvents.BLOCK_BEACON_AMBIENT, 1.4F, .8F);
    }

    private void updateBossBar(ServerWorld server) {
        bossBar.setPercent(Math.max(0F, Math.min(1F, getHealth() / getMaxHealth())));
        for (ServerPlayerEntity p : server.getPlayers()) {
            if (!bossBar.getPlayers().contains(p)) bossBar.addPlayer(p);
        }
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (!blessed || defeated) return false;
        // Ölümcül (ya da canı %20'nin altına düşürecek) vuruş: ölme, yenilgi sahnesini başlat.
        // /kill gibi her şeyi aşan hasarlar bunu atlar.
        if (getWorld() instanceof ServerWorld server
                && !source.isIn(net.minecraft.registry.tag.DamageTypeTags.BYPASSES_INVULNERABILITY)
                && getHealth() - amount <= getMaxHealth() * DEFEAT_HEALTH_FRACTION) {
            startDefeat(server);
            return false;
        }
        return super.damage(source, amount);
    }

    /** Boss bar kapanır, Midas pasifleşir, canı fullenir ve olduğu yerde şaşırır. */
    private void startDefeat(ServerWorld server) {
        defeated = true;
        defeatTimer = 0;
        bossBar.clearPlayers();
        bossBar.setVisible(false);
        setHealth(getMaxHealth());
        setTarget(null);
        setAiDisabled(true);
        setVelocity(Vec3d.ZERO);
        setGlowing(false);
        removeStatusEffect(StatusEffects.GLOWING);
        for (ServerPlayerEntity player : server.getPlayers()) {
            player.sendMessage(Text.literal("Midas: Ne? Noluyor?"), false);
        }
        server.playSound(null, getBlockPos(), SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.HOSTILE, 1.6F, .8F);
    }

    /** Olduğu yerde durur; portal efektleri yoğunlaşır ve sonunda portal efektiyle yok olur. */
    private void tickDefeat(ServerWorld server) {
        defeatTimer++;
        setAiDisabled(true);
        setTarget(null);
        setVelocity(Vec3d.ZERO);
        bossBar.setVisible(false);

        int intensity = 8 + defeatTimer * 2;
        server.spawnParticles(ParticleTypes.REVERSE_PORTAL, getX(), getY() + 1, getZ(), intensity, .45, .9, .45, .08);
        server.spawnParticles(ParticleTypes.PORTAL, getX(), getY() + 1, getZ(), intensity, .45, .9, .45, .5);

        if (defeatTimer >= DEFEAT_DURATION_TICKS) {
            server.spawnParticles(ParticleTypes.REVERSE_PORTAL, getX(), getY() + 1, getZ(), 220, 1.0, 1.5, 1.0, .15);
            server.spawnParticles(ParticleTypes.PORTAL, getX(), getY() + 1, getZ(), 120, 1.0, 1.5, 1.0, .6);
            server.playSound(null, getBlockPos(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.HOSTILE, 1.8F, .7F);

            // Midas gidince kısa bir sessizlikten sonra gerçek Mesut Christ boss olarak gelir.
            final double bx = getX(), by = getY(), bz = getZ();
            final float byaw = getYaw();
            net.friends.mesutcmod.ritual.DelayedTasks.schedule(MESUT_ARRIVAL_DELAY_TICKS, () -> {
                if (server.getServer().isRunning()) spawnRealMesut(server, bx, by, bz, byaw);
            });
            remove(RemovalReason.DISCARDED);
        }
    }

    /** Midas'ın yerine gerçek Mesut Christ (boss savaşı) gelir. */
    private static void spawnRealMesut(ServerWorld server, double x, double y, double z, float yaw) {
        if (!server.getEntitiesByType(ModEntities.MESUT_CHRIST, e -> e.isAlive()).isEmpty()) return;
        MesutChristEntity mesut = new MesutChristEntity(ModEntities.MESUT_CHRIST, server);
        mesut.refreshPositionAndAngles(x, y, z, yaw, 0);
        server.spawnEntity(mesut);
        PlayerEntity nearest = server.getClosestPlayer(x, y, z, 64.0, p -> !p.isSpectator());
        if (nearest != null) mesut.setTarget(nearest);

        server.spawnParticles(ParticleTypes.REVERSE_PORTAL, x, y + 1, z, 260, 1.6, 2.0, 1.6, .1);
        server.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, x, y + 1, z, 100, 1.0, 1.4, 1.0, .04);
        server.spawnParticles(ParticleTypes.FLASH, x, y + 1, z, 1, 0, 0, 0, 0);
        server.playSound(null, mesut.getBlockPos(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.HOSTILE, 2.0F, .45F);
        server.playSound(null, mesut.getBlockPos(), SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.HOSTILE, 1.6F, .6F);
        for (ServerPlayerEntity player : server.getPlayers()) {
            player.sendMessage(Text.literal("Mesut Christ: Verdiğim kutsamayı geri aldım. Şimdi sıra sizde."), false);
        }
    }

    /** Entity ölüm dışında da (discard/kill/unload) kaldırılsa boss bar'lar ekranda takılı kalmasın. */
    @Override
    public void remove(RemovalReason reason) {
        bossBar.clearPlayers();
        super.remove(reason);
    }

    @Override
    public void onDeath(DamageSource source) {
        if (getWorld() instanceof ServerWorld server) {
            bossBar.clearPlayers();
            bossBar.setVisible(false);
            server.spawnParticles(ParticleTypes.FLASH, getX(), getY() + 1, getZ(), 1, 0, 0, 0, 0);
            server.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING, getX(), getY() + 1, getZ(), 100, 1.4, 1.8, 1.4, .12);
            server.playSound(null, getBlockPos(), SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE, 1.2F, .8F);
        }
        super.onDeath(source);
    }
}
