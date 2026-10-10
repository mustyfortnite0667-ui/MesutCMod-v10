package net.friends.mesutcmod.entity;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MesutChristEntity extends ZombieEntity {
    private int phase = 1;
    private int attackCooldown = 55;
    private int pulseTimer = 0;
    private int shieldCooldown = 180;
    private boolean phaseTransitioning = false;
    private boolean waitingForGuardians = false;
    private boolean cinematicMode = false;
    private final List<UUID> phaseGuardians = new ArrayList<>();
    private final ServerBossBar bossBar = new ServerBossBar(Text.literal("Mesut Christ - PHASE 1"), BossBar.Color.PURPLE, BossBar.Style.PROGRESS);

    public MesutChristEntity(EntityType<? extends ZombieEntity> type, World world) {
        super(type, world);
        setPersistent();
        setCustomName(Text.literal("Mesut Christ"));
        setCustomNameVisible(true);
        setCanPickUpLoot(false);
        bossBar.setDarkenSky(true);
    }

    public static net.minecraft.entity.attribute.DefaultAttributeContainer.Builder createAttributes() {
        return ZombieEntity.createZombieAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 160.0D)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 14.0D)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.34D)
                .add(EntityAttributes.GENERIC_ARMOR, 14.0D)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 0.9D)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 64.0D);
    }

    public int getPhase() { return phase; }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putInt("MesutChristPhase", phase);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        phase = Math.max(1, Math.min(4, nbt.getInt("MesutChristPhase")));
    }

    @Override protected net.minecraft.sound.SoundEvent getAmbientSound() { return SoundEvents.ENTITY_WITHER_AMBIENT; }
    @Override protected net.minecraft.sound.SoundEvent getHurtSound(DamageSource source) { return SoundEvents.ENTITY_PLAYER_HURT; }
    @Override protected net.minecraft.sound.SoundEvent getDeathSound() { return SoundEvents.ENTITY_WITHER_DEATH; }
    @Override protected net.minecraft.sound.SoundEvent getStepSound() { return SoundEvents.ENTITY_IRON_GOLEM_STEP; }
    @Override protected boolean burnsInDaylight() { return false; }

    @Override
    public void tick() {
        super.tick();
        if (getWorld().isClient || !(getWorld() instanceof ServerWorld server)) return;

        if (cinematicMode) return;

        updateBossBar(server);
        pulseTimer++;

        // Constant arena aura: the boss visibly owns the arena without dealing free damage.
        if (pulseTimer % 18 == 0) {
            server.spawnParticles(phase >= 3 ? ParticleTypes.REVERSE_PORTAL : ParticleTypes.SOUL,
                    getX(), getY() + 1.0, getZ(), 8 + phase * 3,
                    .55 + phase * .1, .45, .55 + phase * .1, .02);
        }

        if (phaseTransitioning) return;

        if (waitingForGuardians) {
            if (hasLivingGuardians(server)) {
                return;
            }
            waitingForGuardians = false;
            phaseGuardians.clear();
            LivingEntity target = getTarget();
            if (target != null && target.isAlive()) {
                teleportNearTarget(server, target, 8.0D);
                server.spawnParticles(ParticleTypes.REVERSE_PORTAL, getX(), getY() + 1, getZ(), 100, 1.2, 1.3, 1.2, .05);
                server.playSound(null, getBlockPos(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.HOSTILE, 1.5F, .45F);
            }
        }

        if (getHealth() <= 40.0F && phase < 4) {
            transitionPhase(server);
            return;
        }

        if (shieldCooldown > 0) shieldCooldown--;
        if (phase >= 3 && shieldCooldown <= 0) {
            activateDarkShield(server);
            shieldCooldown = 250 + server.getRandom().nextInt(100);
        }

        if (attackCooldown > 0) attackCooldown--;
        if (attackCooldown <= 0) {
            LivingEntity target = getTarget();
            if (target != null && target.isAlive()) {
                usePhaseAttack(server, target);
                attackCooldown = Math.max(38, 78 - phase * 8) + server.getRandom().nextInt(20);
            }
        }
    }


    public void setCinematicMode(boolean cinematicMode) {
        this.cinematicMode = cinematicMode;
        bossBar.setVisible(!cinematicMode);
        if (cinematicMode) bossBar.clearPlayers();
    }

    private void transitionPhase(ServerWorld server) {
        phaseTransitioning = true;
        phase++;
        setHealth(getMaxHealth());

        setAttr(EntityAttributes.GENERIC_ATTACK_DAMAGE, 12 + phase * 2);
        setAttr(EntityAttributes.GENERIC_MOVEMENT_SPEED, .32 + phase * .025);
        setAttr(EntityAttributes.GENERIC_ARMOR, 12 + phase * 1.5);

        bossBar.setColor(phase == 1 ? BossBar.Color.PURPLE : phase == 2 ? BossBar.Color.RED : phase == 3 ? BossBar.Color.YELLOW : BossBar.Color.WHITE);
        bossBar.setName(Text.literal("Mesut Christ - PHASE " + phase));
        setCustomName(Text.literal("Mesut Christ - PHASE " + phase));
        clearPhaseGuardians();

        // Phase 2: one real Ahmet, nerfed and renamed Koruma Ahmet.
        if (phase == 2) spawnGuardianAhmets(server, 1);

        // Final phase: Mesut retreats and refuses to re-engage until both guardians are defeated.
        if (phase == 4) {
            waitingForGuardians = true;
            spawnGuardianAhmets(server, 2);
            teleportFarFromTarget(server);
        }

        server.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getY() + 1, getZ(), 2, .4, .6, .4, 0);
        server.spawnParticles(ParticleTypes.REVERSE_PORTAL, getX(), getY() + 1, getZ(), 180, 1.8, 1.6, 1.8, .06);
        server.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, getX(), getY() + 1, getZ(), 80, 1.1, 1.2, 1.1, .03);
        server.playSound(null, getBlockPos(), SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.HOSTILE, 1.8F, .45F + phase * .08F);

        for (ServerPlayerEntity p : server.getPlayers()) {
            p.sendMessage(Text.literal("Mesut Christ PHASE " + phase + " başladı. Canı tamamen yenilendi."), false);
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 35, 0, false, false, true));
        }
        phaseTransitioning = false;
    }

    private void spawnGuardianAhmets(ServerWorld server, int count) {
        phaseGuardians.clear();
        LivingEntity target = getTarget();
        double centerX = target != null ? target.getX() : getX();
        double centerY = target != null ? target.getY() : getY();
        double centerZ = target != null ? target.getZ() : getZ();
        for (int i = 0; i < count; i++) {
            KayaEntity guardian = new KayaEntity(ModEntities.KAYA, server);
            guardian.setGuardianAhmet();
            double angle = (Math.PI * 2.0 * i) / Math.max(1, count) + server.getRandom().nextDouble() * 0.25;
            double radius = count == 1 ? 4.0 : 5.0;
            guardian.refreshPositionAndAngles(centerX + Math.cos(angle) * radius, centerY, centerZ + Math.sin(angle) * radius, 0, 0);
            server.spawnEntity(guardian);
            phaseGuardians.add(guardian.getUuid());
        }
        server.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, centerX, centerY + 1, centerZ, 100, 1.0, 1.0, 1.0, .035);
        server.playSound(null, new net.minecraft.util.math.BlockPos((int) centerX, (int) centerY, (int) centerZ), SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.HOSTILE, 1.2F, .72F);
    }

    private boolean hasLivingGuardians(ServerWorld server) {
        boolean alive = false;
        for (UUID id : phaseGuardians) {
            var e = server.getEntity(id);
            if (e instanceof KayaEntity k && !k.isRemoved() && k.isAlive() && k.isGuardianAhmet()) {
                alive = true;
            }
        }
        return alive;
    }

    private void clearPhaseGuardians() {
        if (!(getWorld() instanceof ServerWorld server)) return;
        for (UUID id : phaseGuardians) {
            var e = server.getEntity(id);
            if (e != null && !e.isRemoved()) e.discard();
        }
        phaseGuardians.clear();
    }

    private void teleportFarFromTarget(ServerWorld server) {
        LivingEntity target = getTarget();
        if (target == null) return;
        double angle = server.getRandom().nextDouble() * Math.PI * 2.0;
        double distance = 30.0 + server.getRandom().nextDouble() * 10.0;
        double tx = target.getX() + Math.cos(angle) * distance;
        double tz = target.getZ() + Math.sin(angle) * distance;
        server.spawnParticles(ParticleTypes.PORTAL, getX(), getY() + 1, getZ(), 70, .7, 1.0, .7, .06);
        teleport(tx, target.getY(), tz);
        server.spawnParticles(ParticleTypes.REVERSE_PORTAL, getX(), getY() + 1, getZ(), 120, 1.0, 1.2, 1.0, .06);
        playSound(SoundEvents.ENTITY_ENDERMAN_TELEPORT, 1.7F, .45F);
    }

    private void teleportNearTarget(ServerWorld server, LivingEntity target, double distance) {
        double angle = server.getRandom().nextDouble() * Math.PI * 2.0;
        teleport(target.getX() + Math.cos(angle) * distance, target.getY(), target.getZ() + Math.sin(angle) * distance);
    }

    private void usePhaseAttack(ServerWorld server, LivingEntity target) {
        int roll = server.getRandom().nextInt(100);
        if (phase == 1) {
            if (roll < 55) meleePulse(server, target, 6.0F, 1.1D);
            else darkLance(server, target, 7.0F);
        } else if (phase == 2) {
            if (roll < 35) darkLance(server, target, 8.0F);
            else if (roll < 70) crossWave(server, target, 5.0F);
            else portalStrike(server, target, 7.0F);
        } else if (phase == 3) {
            if (roll < 35) soulRain(server, target, 8.0F);
            else if (roll < 65) crossWave(server, target, 6.0F);
            else portalStrike(server, target, 9.0F);
        } else {
            if (roll < 35) doomRing(server, target);
            else if (roll < 65) soulRain(server, target, 9.0F);
            else portalStrike(server, target, 10.0F);
        }
    }

    private void meleePulse(ServerWorld server, LivingEntity target, float damage, double knockback) {
        if (squaredDistanceTo(target) <= 16.0D) {
            target.damage(getWorld().getDamageSources().mobAttack(this), damage);
            target.takeKnockback(knockback, target.getX() - getX(), target.getZ() - getZ());
        } else {
            getNavigation().startMovingTo(target, 1.15 + phase * .08);
        }
        server.spawnParticles(ParticleTypes.SOUL, getX(), getY() + 1, getZ(), 30, 1, .5, 1, .03);
    }

    private void darkLance(ServerWorld server, LivingEntity target, float damage) {
        Vec3d start = getPos().add(0, 1.3, 0);
        Vec3d end = target.getPos().add(0, target.getHeight() * .5, 0);
        Vec3d dir = end.subtract(start).normalize();
        for (int i = 1; i <= 16; i++) {
            Vec3d p = start.add(dir.multiply(i * 1.15));
            server.spawnParticles(ParticleTypes.REVERSE_PORTAL, p.x, p.y, p.z, 7, .08, .08, .08, .01);
        }
        if (squaredDistanceTo(target) < 24 * 24) {
            target.damage(getWorld().getDamageSources().mobAttack(this), damage);
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 35 + phase * 5, phase >= 3 ? 1 : 0));
        }
        playSound(SoundEvents.ENTITY_ENDERMAN_TELEPORT, 1.15F, .45F + phase * .12F);
    }

    private void crossWave(ServerWorld server, LivingEntity target, float damage) {
        double dx = target.getX() - getX();
        double dz = target.getZ() - getZ();
        double len = Math.max(.01, Math.sqrt(dx * dx + dz * dz));
        dx /= len; dz /= len;
        Vec3d side = new Vec3d(-dz, 0, dx);
        for (int i = -8; i <= 8; i++) {
            Vec3d p1 = getPos().add(dx * i, .25, dz * i);
            Vec3d p2 = getPos().add(side.x * i, .25, side.z * i);
            server.spawnParticles(ParticleTypes.SOUL, p1.x, p1.y, p1.z, 2, .08, .04, .08, .01);
            server.spawnParticles(ParticleTypes.SOUL, p2.x, p2.y, p2.z, 2, .08, .04, .08, .01);
        }
        Box area = getBoundingBox().expand(7.0D);
        for (LivingEntity e : server.getEntitiesByClass(LivingEntity.class, area,
                x -> x != this && x.isAlive() && !(x instanceof KayaEntity))) {
            double ax = e.getX() - getX();
            double az = e.getZ() - getZ();
            double dot = Math.abs(ax * dx + az * dz);
            double cross = Math.abs(ax * dz - az * dx);
            if ((dot < 7.0 && cross < 1.0) || (cross < 7.0 && dot < 1.0)) {
                e.damage(getWorld().getDamageSources().mobAttack(this), damage);
                e.takeKnockback(.65D, -ax, -az);
            }
        }
        playSound(SoundEvents.ENTITY_RAVAGER_ROAR, 1.2F, .65F + phase * .05F);
    }

    private void portalStrike(ServerWorld server, LivingEntity target, float damage) {
        double angle = server.getRandom().nextDouble() * Math.PI * 2;
        double x = target.getX() + Math.cos(angle) * 3.0;
        double z = target.getZ() + Math.sin(angle) * 3.0;
        server.spawnParticles(ParticleTypes.PORTAL, getX(), getY() + 1, getZ(), 45, .35, .6, .35, .08);
        teleport(x, target.getY(), z);
        server.spawnParticles(ParticleTypes.REVERSE_PORTAL, getX(), getY() + 1, getZ(), 60, .45, .8, .45, .08);
        Box hit = getBoundingBox().expand(2.4D);
        for (LivingEntity e : server.getEntitiesByClass(LivingEntity.class, hit, x2 -> x2 != this && x2.isAlive() && !(x2 instanceof KayaEntity))) {
            e.damage(getWorld().getDamageSources().mobAttack(this), damage);
            e.takeKnockback(1.0D, e.getX() - getX(), e.getZ() - getZ());
        }
        playSound(SoundEvents.ENTITY_ENDERMAN_TELEPORT, 1.4F, .55F + phase * .08F);
    }

    private void soulRain(ServerWorld server, LivingEntity target, float damage) {
        // Telegraph first with a ring of souls around the target, then hit a small area immediately.
        for (int i = 0; i < 18; i++) {
            double a = (Math.PI * 2 * i) / 18.0;
            double r = 2.0 + (i % 3);
            server.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    target.getX() + Math.cos(a) * r, target.getY() + .1, target.getZ() + Math.sin(a) * r,
                    4, .05, .2, .05, .01);
        }
        Box area = new Box(target.getX() - 2.2, target.getY() - 1, target.getZ() - 2.2,
                target.getX() + 2.2, target.getY() + 2, target.getZ() + 2.2);
        for (LivingEntity e : server.getEntitiesByClass(LivingEntity.class, area, x -> x != this && x.isAlive() && !(x instanceof KayaEntity))) {
            e.damage(getWorld().getDamageSources().mobAttack(this), damage);
            e.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 55, 0));
        }
        playSound(SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, 1.1F, .65F);
    }

    private void doomRing(ServerWorld server, LivingEntity target) {
        double radius = 5.5;
        for (int i = 0; i < 48; i++) {
            double a = (Math.PI * 2 * i) / 48.0;
            server.spawnParticles(ParticleTypes.REVERSE_PORTAL,
                    target.getX() + Math.cos(a) * radius, target.getY() + .15, target.getZ() + Math.sin(a) * radius,
                    2, .05, .1, .05, .01);
        }
        Box area = new Box(target.getX() - radius, target.getY() - 1, target.getZ() - radius,
                target.getX() + radius, target.getY() + 2, target.getZ() + radius);
        for (LivingEntity e : server.getEntitiesByClass(LivingEntity.class, area, x -> x != this && x.isAlive() && !(x instanceof KayaEntity))) {
            e.damage(getWorld().getDamageSources().mobAttack(this), 11.0F);
            e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 45, 1));
            e.takeKnockback(1.2D, e.getX() - getX(), e.getZ() - getZ());
        }
        server.spawnParticles(ParticleTypes.EXPLOSION, target.getX(), target.getY() + .5, target.getZ(), 1, 0, 0, 0, 0);
        playSound(SoundEvents.ENTITY_WITHER_SHOOT, 1.2F, .55F);
    }

    private void activateDarkShield(ServerWorld server) {
        addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 45, 0, false, false, true));
        server.spawnParticles(ParticleTypes.END_ROD, getX(), getY() + 1, getZ(), 45, 1.2, 1.0, 1.2, .03);
        server.spawnParticles(ParticleTypes.REVERSE_PORTAL, getX(), getY() + 1, getZ(), 70, 1.4, 1.2, 1.4, .04);
        playSound(SoundEvents.BLOCK_BEACON_POWER_SELECT, 1.0F, .55F);
    }

    private void updateBossBar(ServerWorld server) {
        bossBar.setPercent(Math.max(0F, Math.min(1F, getHealth() / getMaxHealth())));
        bossBar.setColor(phase == 1 ? BossBar.Color.PURPLE : phase == 2 ? BossBar.Color.RED : phase == 3 ? BossBar.Color.YELLOW : BossBar.Color.WHITE);
        for (ServerPlayerEntity p : server.getPlayers()) if (!bossBar.getPlayers().contains(p)) bossBar.addPlayer(p);
    }

    private void setAttr(net.minecraft.entity.attribute.EntityAttribute attr, double value) {
        EntityAttributeInstance a = getAttributeInstance(attr);
        if (a != null) a.setBaseValue(value);
    }

    /** Entity ölüm dışında da (discard/kill/unload) kaldırılsa boss bar'lar ekranda takılı kalmasın. */
    @Override
    public void remove(RemovalReason reason) {
        bossBar.clearPlayers();
        super.remove(reason);
    }

    @Override
    public void onDeath(DamageSource source) {
        clearPhaseGuardians();
        bossBar.clearPlayers();
        bossBar.setVisible(false);
        if (getWorld() instanceof ServerWorld server) {
            server.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getY() + 1, getZ(), 7, .9, .9, .9, 0);
            server.spawnParticles(ParticleTypes.REVERSE_PORTAL, getX(), getY() + 1, getZ(), 350, 2.8, 2.8, 2.8, .08);
            server.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, getX(), getY() + 1, getZ(), 160, 1.8, 1.8, 1.8, .05);
            server.playSound(null, getBlockPos(), SoundEvents.ENTITY_WITHER_DEATH, SoundCategory.HOSTILE, 2.2F, .5F);
        }
        super.onDeath(source);
    }
}
