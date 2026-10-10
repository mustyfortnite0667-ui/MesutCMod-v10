package net.friends.mesutcmod.entity;

import net.friends.mesutcmod.sound.ModSounds;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.World;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.damage.DamageSource;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class KayaEntity extends ZombieEntity {
    private boolean phase2 = false;
    private boolean phase3 = false;
    private boolean minion = false;
    private boolean guardian = false;
    private boolean spawnedMinions = false;
    private boolean spawnedMinionsPhase3 = false;
    private int totalMinionsSpawned = 0;
    private int pulseTimer = 0;
    private int explosionCountdown = -1;
    private int musicTimer = 0;
    private int specialAttackCooldown = 60;
    private boolean berserk = false;
    private int hiddenEventCooldown = 180;
    private int delayedPulseTimer = -1;
    private UUID mainKayaUuid = null;
    private UUID minionAnchorUuid = null;
    private final List<UUID> summonedMinionUuids = new ArrayList<>();

    private final ServerBossBar bossBar = new ServerBossBar(Text.literal("Ahmet Kaya"), BossBar.Color.PURPLE, BossBar.Style.PROGRESS);
    private final ServerBossBar minionBar = new ServerBossBar(Text.literal("Ahmet Taşları 10/10"), BossBar.Color.WHITE, BossBar.Style.PROGRESS);

    public KayaEntity(EntityType<? extends ZombieEntity> type, World world) {
        super(type, world);
        // Boss/minions must not despawn when the player dies or leaves the area briefly.
        setPersistent();
        setCustomName(Text.literal("Ahmet Kaya"));
        setCustomNameVisible(true);
        bossBar.setDarkenSky(false);
        minionBar.setVisible(false);
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putBoolean("KayaPhase2", phase2);
        nbt.putBoolean("KayaPhase3", phase3);
        nbt.putBoolean("KayaMinion", minion);
        nbt.putBoolean("KayaGuardian", guardian);
        nbt.putBoolean("KayaSpawnedMinions", spawnedMinions);
        nbt.putBoolean("KayaSpawnedMinionsPhase3", spawnedMinionsPhase3);
        nbt.putBoolean("KayaBerserk", berserk);
        nbt.putInt("KayaTotalMinions", totalMinionsSpawned);
        if (mainKayaUuid != null) nbt.putUuid("KayaMainUuid", mainKayaUuid);
        if (minionAnchorUuid != null) nbt.putUuid("KayaMinionAnchor", minionAnchorUuid);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        phase2 = nbt.getBoolean("KayaPhase2");
        phase3 = nbt.getBoolean("KayaPhase3");
        minion = nbt.getBoolean("KayaMinion");
        guardian = nbt.getBoolean("KayaGuardian");
        spawnedMinions = nbt.getBoolean("KayaSpawnedMinions");
        spawnedMinionsPhase3 = nbt.getBoolean("KayaSpawnedMinionsPhase3");
        berserk = nbt.getBoolean("KayaBerserk");
        totalMinionsSpawned = nbt.getInt("KayaTotalMinions");
        if (nbt.containsUuid("KayaMainUuid")) mainKayaUuid = nbt.getUuid("KayaMainUuid");
        if (nbt.containsUuid("KayaMinionAnchor")) minionAnchorUuid = nbt.getUuid("KayaMinionAnchor");
        if (minion) addCommandTag("KayaMinion");
        if (guardian) {
            addCommandTag("KayaGuardian");
            setCustomName(Text.literal("Koruma Ahmet"));
            setCustomNameVisible(true);
            bossBar.setVisible(false);
            minionBar.setVisible(false);
        }
    }

    public void debugForcePhase(int phase) {
        if (!(getWorld() instanceof ServerWorld server)) return;
        setAttr(this, EntityAttributes.GENERIC_MAX_HEALTH, 160);
        if (phase >= 2) {
            phase2 = true;
            phase3 = false;
            berserk = false;
            spawnedMinions = true;
            spawnedMinionsPhase3 = false;
            setHealth(160);
            setAttr(this, EntityAttributes.GENERIC_ATTACK_DAMAGE, 18);
            setAttr(this, EntityAttributes.GENERIC_MOVEMENT_SPEED, .38);
            setAttr(this, EntityAttributes.GENERIC_ARMOR, 16);
        }
        if (phase >= 3) {
            phase3 = true;
            setHealth(160);
            setAttr(this, EntityAttributes.GENERIC_ATTACK_DAMAGE, 20);
            setAttr(this, EntityAttributes.GENERIC_MOVEMENT_SPEED, .38);
            setAttr(this, EntityAttributes.GENERIC_ARMOR, 17);
        }
        if (phase >= 4) {
            berserk = true;
            setAttr(this, EntityAttributes.GENERIC_MOVEMENT_SPEED, .47);
            setAttr(this, EntityAttributes.GENERIC_ATTACK_DAMAGE, 20);
        }
        setCustomName(Text.literal(phase >= 4 ? "Ahmet Kaya - BERSERK" : phase >= 3 ? "Ahmet Kaya - FINAL PHASE" : phase >= 2 ? "Ahmet Kaya - PHASE 2" : "Ahmet Kaya"));
        bossBar.setVisible(true);
        bossBar.setColor(phase >= 3 ? BossBar.Color.RED : phase >= 2 ? BossBar.Color.RED : BossBar.Color.PURPLE);
        bossBar.setName(Text.literal(phase >= 4 ? "Ahmet Kaya - BERSERK" : phase >= 3 ? "Ahmet Kaya - FINAL PHASE" : phase >= 2 ? "Ahmet Kaya - PHASE 2" : "Ahmet Kaya"));
        updateBossBar(server);
    }

    public void setMainKayaUuid(UUID uuid) { this.mainKayaUuid = uuid; }
    public void setMinionAnchor(UUID uuid) { this.minionAnchorUuid = uuid; }
    public void setGuardianAhmet() {
        guardian = true;
        minion = false;
        phase2 = false;
        phase3 = false;
        berserk = false;
        spawnedMinions = true;
        spawnedMinionsPhase3 = true;
        addCommandTag("KayaGuardian");
        setCustomName(Text.literal("Koruma Ahmet"));
        setCustomNameVisible(true);
        bossBar.setVisible(false);
        minionBar.setVisible(false);
        setAttr(this, EntityAttributes.GENERIC_MAX_HEALTH, 110.0D);
        setAttr(this, EntityAttributes.GENERIC_ATTACK_DAMAGE, 8.0D);
        setAttr(this, EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.29D);
        setAttr(this, EntityAttributes.GENERIC_ARMOR, 8.0D);
        setAttr(this, EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 0.65D);
        setHealth(110.0F);
    }

    public boolean isGuardianAhmet() { return guardian; }

    @Override
    protected boolean shouldDropLoot() {
        return !guardian && super.shouldDropLoot();
    }

    @Override
    public boolean shouldDropXp() {
        return !guardian && super.shouldDropXp();
    }

    public void setMinion(boolean value) {
        minion = value;
        if (value) {
            addCommandTag("KayaMinion");
            bossBar.setVisible(false);
            minionBar.setVisible(false);
        }
    }
    private boolean isMinion() { return minion; }

    public static net.minecraft.entity.attribute.DefaultAttributeContainer.Builder createKayaAttributes() {
        return ZombieEntity.createZombieAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 160.0D)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 12.0D)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.32D)
                .add(EntityAttributes.GENERIC_ARMOR, 12.0D)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 0.85D)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 48.0D);
    }

    @Override protected SoundEvent getAmbientSound() { return null; }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.ENTITY_PLAYER_HURT; }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.ENTITY_PLAYER_DEATH; }
    @Override protected SoundEvent getStepSound() { return SoundEvents.ENTITY_ZOMBIE_STEP; }
    @Override protected boolean burnsInDaylight() { return false; }

    @Override
    protected void playHurtSound(DamageSource source) {
        playSound(SoundEvents.ENTITY_PLAYER_HURT, 1.0F, 1.0F);
    }

    @Override
    public void tick() {
        super.tick();
        if (getWorld().isClient) return;
        if (!minion && !guardian && getWorld() instanceof ServerWorld server) {
            updateBossBar(server);
            updateMinionBar(server);
            if (!isDead() && musicTimer++ >= 2200) {
                server.playSound(null, getBlockPos(), ModSounds.HOPES_AND_DREAMS, SoundCategory.MUSIC, 1.0F, 1.0F);
                musicTimer = 0;
            }
        }

        pulseTimer++;
        if (pulseTimer % 30 == 0 && getWorld() instanceof ServerWorld server) {
            server.spawnParticles(ParticleTypes.SOUL, getX(), getY() + 1.0D, getZ(), 10, .35, .65, .35, .01);
        }

        if (guardian) {
            if (getTarget() == null && getWorld() instanceof ServerWorld sw) {
                PlayerEntity nearest = sw.getClosestPlayer(getX(), getY(), getZ(), 32.0, false);
                if (nearest != null) setTarget(nearest);
            }
            return;
        }

        if (minion) {
            keepNearMainKaya();
            if (getTarget() == null && getWorld() instanceof ServerWorld sw) {
                PlayerEntity nearest = sw.getClosestPlayer(getX(), getY(), getZ(), 18.0, false);
                if (nearest != null) setTarget(nearest);
            }
            return;
        }
        if (!phase2 && getHealth() <= 10.0F) activatePhase2();
        if (phase2 && !spawnedMinions && getHealth() <= getMaxHealth() * .50F) spawnMinions();

        if (phase2 && getWorld() instanceof ServerWorld server2) {
            handlePhase2Specials(server2);
            handleHiddenEvents(server2);
        }

        if (explosionCountdown >= 0 && getWorld() instanceof ServerWorld server) {
            if (explosionCountdown > 0 && pulseTimer % 20 == 0) {
                setCustomName(Text.literal((phase2 ? "Ahmet Kaya - PHASE 2 - PATLAMA: " : "Ahmet Kaya - PATLAMA: ") + explosionCountdown));
                server.spawnParticles(ParticleTypes.FLAME, getX(), getY()+1, getZ(), 18, .35,.7,.35,.02);
                explosionCountdown--;
            }
            if (explosionCountdown == 0) {
                explosionCountdown = -2;
                setCustomName(Text.literal(phase2 ? "Ahmet Kaya - PHASE 2" : "Ahmet Kaya - ÖFKELİ"));
                server.createExplosion(this, getX(), getY(), getZ(), phase2 ? 6.5F : 5.0F, false, World.ExplosionSourceType.TNT);
                server.spawnParticles(ParticleTypes.EXPLOSION, getX(), getY()+1, getZ(), 1, 0,0,0,0);
            }
        }
    }

    private void updateBossBar(ServerWorld server) {
        bossBar.setPercent(Math.max(0, Math.min(1, getHealth() / getMaxHealth())));
        for (ServerPlayerEntity player : server.getPlayers()) if (!bossBar.getPlayers().contains(player)) bossBar.addPlayer(player);
        boolean finalPhase = phase3 || berserk;
        bossBar.setName(Text.literal(finalPhase ? "Ahmet Kaya - FINAL PHASE" : (phase2 ? "Ahmet Kaya - PHASE 2" : "Ahmet Kaya")));
        bossBar.setColor(finalPhase ? BossBar.Color.RED : (phase2 ? BossBar.Color.RED : BossBar.Color.PURPLE));
    }

    private void spawnMinions() {
        spawnedMinions = true;
        if (!(getWorld() instanceof ServerWorld server)) return;
        for (int i=0;i<10;i++) {
            KayaEntity m = new KayaEntity(ModEntities.KAYA, server);
            m.setMinion(true);
            setAttr(m, EntityAttributes.GENERIC_MAX_HEALTH, 20);
            m.setHealth(20);
            setAttr(m, EntityAttributes.GENERIC_ATTACK_DAMAGE, 3);
            setAttr(m, EntityAttributes.GENERIC_MOVEMENT_SPEED, .23);
            setAttr(m, EntityAttributes.GENERIC_ARMOR, 2);
            setAttr(m, EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 0);
            m.refreshPositionAndAngles(getX() + (server.getRandom().nextDouble()-.5)*6, getY(), getZ() + (server.getRandom().nextDouble()-.5)*6, server.getRandom().nextFloat()*360, 0);
            m.setMainKayaUuid(getUuid());
            server.spawnEntity(m);
            summonedMinionUuids.add(m.getUuid());
        }
        totalMinionsSpawned = 10;
        server.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, getX(), getY()+1, getZ(), 80, .8,1,.8,.03);
        server.playSound(null, getBlockPos(), SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.HOSTILE, 1,.8f);
    }

    private void spawnMinionsPhase3() {
        spawnedMinionsPhase3 = true;
        if (!(getWorld() instanceof ServerWorld server)) return;
        for (int i=0;i<10;i++) {
            KayaEntity m = new KayaEntity(ModEntities.KAYA, server);
            m.setMinion(true);
            setAttr(m, EntityAttributes.GENERIC_MAX_HEALTH, 22);
            m.setHealth(22);
            setAttr(m, EntityAttributes.GENERIC_ATTACK_DAMAGE, 3);
            setAttr(m, EntityAttributes.GENERIC_MOVEMENT_SPEED, .25);
            setAttr(m, EntityAttributes.GENERIC_ARMOR, 2);
            setAttr(m, EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 0);
            m.refreshPositionAndAngles(getX() + (server.getRandom().nextDouble()-.5)*7, getY(), getZ() + (server.getRandom().nextDouble()-.5)*7, server.getRandom().nextFloat()*360, 0);
            m.setMainKayaUuid(getUuid());
            server.spawnEntity(m);
            summonedMinionUuids.add(m.getUuid());
        }
        totalMinionsSpawned = 10;
        server.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, getX(), getY()+1, getZ(), 120, 1.0,1.2,1.0,.04);
        server.spawnParticles(ParticleTypes.FLAME, getX(), getY()+1, getZ(), 60, .8,1,.8,.03);
        server.playSound(null, getBlockPos(), SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.HOSTILE, 1.3f, .6f);
    }

    private void setAttr(KayaEntity e, net.minecraft.entity.attribute.EntityAttribute attr, double value) {
        EntityAttributeInstance a = e.getAttributeInstance(attr);
        if (a != null) a.setBaseValue(value);
    }

    private LivingEntity findMinionAnchor(ServerWorld server) {
        if (minionAnchorUuid != null) {
            var entity = server.getEntity(minionAnchorUuid);
            if (entity instanceof LivingEntity living && !living.isDead()) return living;
        }
        if (mainKayaUuid != null) {
            var entity = server.getEntity(mainKayaUuid);
            if (entity instanceof KayaEntity kaya && !kaya.isMinion() && !kaya.isDead()) return kaya;
        }
        return null;
    }

    private void keepNearMainKaya() {
        if (!(getWorld() instanceof ServerWorld server)) return;
        LivingEntity anchor=findMinionAnchor(server); if(anchor==null)return;
        if(squaredDistanceTo(anchor)>25){ getNavigation().startMovingTo(anchor,1.25); }
    }

    private void updateMinionBar(ServerWorld server) {
        if(totalMinionsSpawned <= 0){minionBar.setVisible(false);return;}
        int alive=countLivingMinions();
        minionBar.setPercent(Math.max(0f, Math.min(1f, alive/(float) totalMinionsSpawned)));
        minionBar.setName(Text.literal("Ahmet Taşları "+alive+"/"+totalMinionsSpawned));
        if(alive>0){
            minionBar.setVisible(true);
            addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE,30,255,false,false,true));
        } else { minionBar.setVisible(false); removeStatusEffect(StatusEffects.RESISTANCE); }
        for(ServerPlayerEntity p:server.getPlayers()){
            if(alive>0&&!minionBar.getPlayers().contains(p))minionBar.addPlayer(p);
            else if(alive==0&&minionBar.getPlayers().contains(p))minionBar.removePlayer(p);
        }
    }

    private int countLivingMinions(){
        if(!(getWorld() instanceof ServerWorld server))return 0;
        int alive=0; for(UUID u:summonedMinionUuids){var e=server.getEntity(u);if(e instanceof KayaEntity k&&!k.isDead())alive++;} return alive;
    }
    private boolean hasLivingMinions(){return countLivingMinions()>0;}

    private void activatePhase2(){
        phase2=true;
        setAttr(this,EntityAttributes.GENERIC_MAX_HEALTH,160); setHealth(160);
        setAttr(this,EntityAttributes.GENERIC_ATTACK_DAMAGE,14); setAttr(this,EntityAttributes.GENERIC_MOVEMENT_SPEED,.34); setAttr(this,EntityAttributes.GENERIC_ARMOR,13);
        explosionCountdown=3;
        if(getWorld() instanceof ServerWorld server){
            server.spawnParticles(ParticleTypes.EXPLOSION_EMITTER,getX(),getY()+1,getZ(),1,0,0,0,0);
            server.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME,getX(),getY()+1,getZ(),120,1,1.3,1,.04);
            server.playSound(null,getBlockPos(),SoundEvents.ENTITY_WITHER_SPAWN,SoundCategory.HOSTILE,1.2f,.5f);
        }
        bossBar.setColor(BossBar.Color.RED); bossBar.setName(Text.literal("Ahmet Kaya - PHASE 2")); setCustomName(Text.literal("Ahmet Kaya - PHASE 2"));
    }

    private void handlePhase2Specials(ServerWorld server) {
        if (specialAttackCooldown > 0) {
            specialAttackCooldown--;
        } else if (getTarget() != null) {
            performShockwave(server);
            if (phase3) {
                performFinalPulse(server);
                specialAttackCooldown = 125 + server.getRandom().nextInt(55);
            } else {
                performPhase2RockWave(server);
                specialAttackCooldown = 145 + server.getRandom().nextInt(55);
            }
        }

        // Phase 2 -> Phase 3: canın %20'sine düşünce can tam dolar, boss daha da güçlenir.
        if (!phase3 && getHealth() <= getMaxHealth() * 0.20F) {
            activatePhase3(server);
        }

        // Phase 3'te canı 50'ye düşünce ikinci bir dalga Ahmet çağırır.
        if (phase3 && !spawnedMinionsPhase3 && getHealth() <= 50.0F) {
            spawnMinionsPhase3();
        }

        // Phase 3'te canı yeniden %20'ye düşünce gerçek berserk moduna girer.
        if (phase3 && !berserk && getHealth() <= getMaxHealth() * 0.20F) {
            activateBerserk(server);
        }

        if (berserk) {
            handleBerserkExtra(server);
        }
    }

    private void activatePhase3(ServerWorld server) {
        phase3 = true;
        setAttr(this, EntityAttributes.GENERIC_MAX_HEALTH, 160);
        setHealth(160.0F);
        setAttr(this, EntityAttributes.GENERIC_ATTACK_DAMAGE, 20);
        setAttr(this, EntityAttributes.GENERIC_MOVEMENT_SPEED, .38);
        setAttr(this, EntityAttributes.GENERIC_ARMOR, 17);
        addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 20 * 999, 1));
        addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 20 * 999, 0));

        server.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getY() + 1.0D, getZ(), 1, 0, 0, 0, 0);
        server.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, getX(), getY() + 1.0D, getZ(), 150, 1.2, 1.4, 1.2, 0.05);
        server.playSound(null, getBlockPos(), SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.HOSTILE, 1.4F, 0.4F);

        setCustomName(Text.literal("Ahmet Kaya - FINAL PHASE"));
        bossBar.setName(Text.literal("Ahmet Kaya - FINAL PHASE"));
        bossBar.setColor(BossBar.Color.RED);
    }

    private void activateBerserk(ServerWorld server) {
        berserk = true;
        EntityAttributeInstance kb = getAttributeInstance(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE);
        if (kb != null) kb.setBaseValue(0.95D);
        EntityAttributeInstance spd = getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (spd != null) spd.setBaseValue(0.47D);
        addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 20 * 999, 2));
        server.spawnParticles(ParticleTypes.ANGRY_VILLAGER, getX(), getY() + 2.0D, getZ(), 30, 0.4, 0.4, 0.4, 0.02);
        playSound(SoundEvents.ENTITY_WITHER_AMBIENT, 1.5F, 0.6F);
    }

    private int blinkCooldown = 0;

    private void handleBerserkExtra(ServerWorld server) {
        if (blinkCooldown > 0) {
            blinkCooldown--;
            return;
        }
        var target = getTarget();
        if (target == null) return;
        if (squaredDistanceTo(target) > 12.0D * 12.0D) {
            double angle = server.getRandom().nextDouble() * Math.PI * 2;
            double tx = target.getX() + Math.cos(angle) * 3.0D;
            double tz = target.getZ() + Math.sin(angle) * 3.0D;
            server.spawnParticles(ParticleTypes.PORTAL, getX(), getY() + 1.0D, getZ(), 30, 0.3, 0.5, 0.3, 0.1);
            teleport(tx, target.getY(), tz);
            server.spawnParticles(ParticleTypes.PORTAL, getX(), getY() + 1.0D, getZ(), 30, 0.3, 0.5, 0.3, 0.1);
            playSound(SoundEvents.ENTITY_ENDERMAN_TELEPORT, 1.0F, 0.7F);
            blinkCooldown = 150 + server.getRandom().nextInt(70);
        }
    }

    // Hidden encounter events: deliberately subtle and uncommon.
    private void handleHiddenEvents(ServerWorld server) {
        if (hiddenEventCooldown > 0) hiddenEventCooldown--;

        if (delayedPulseTimer >= 0) {
            if (delayedPulseTimer-- == 0) {
                Box echo = getBoundingBox().expand(5.5D);
                for (LivingEntity nearby : server.getEntitiesByClass(LivingEntity.class, echo,
                        e -> e != this && e.isAlive() && !(e instanceof KayaEntity))) {
                    nearby.damage(getWorld().getDamageSources().mobAttack(this), 2.0F);
                    nearby.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 30, 0));
                }
                server.spawnParticles(ParticleTypes.SOUL, getX(), getY() + 0.8D, getZ(), 35, 1.4, 0.5, 1.4, 0.01);
                playSound(SoundEvents.BLOCK_RESPAWN_ANCHOR_DEPLETE.value(), 0.8F, 0.8F);
                delayedPulseTimer = -1;
            }
        }

        if (hiddenEventCooldown > 0 || getTarget() == null) return;

        // A rare second-wave echo after a special attack in the later phases.
        if ((phase3 || berserk) && server.getRandom().nextFloat() < 0.30F) {
            delayedPulseTimer = 35 + server.getRandom().nextInt(25);
            server.spawnParticles(ParticleTypes.REVERSE_PORTAL, getX(), getY() + 1.0D, getZ(), 16, 0.5, 0.6, 0.5, 0.03);
            hiddenEventCooldown = 260 + server.getRandom().nextInt(180);
            return;
        }

        // Rare Phase 2 stone burst around the boss; low damage, mostly a surprise.
        if (phase2 && !phase3 && server.getRandom().nextFloat() < 0.18F) {
            Box burst = getBoundingBox().expand(4.5D);
            for (LivingEntity nearby : server.getEntitiesByClass(LivingEntity.class, burst,
                    e -> e != this && e.isAlive() && !(e instanceof KayaEntity))) {
                nearby.damage(getWorld().getDamageSources().mobAttack(this), 2.0F);
                nearby.takeKnockback(0.45D, nearby.getX() - getX(), nearby.getZ() - getZ());
            }
            server.spawnParticles(ParticleTypes.POOF, getX(), getY() + 0.2D, getZ(), 30, 1.2, 0.1, 1.2, 0.08);
            playSound(SoundEvents.BLOCK_STONE_BREAK, 1.0F, 0.7F);
            hiddenEventCooldown = 220 + server.getRandom().nextInt(160);
        }
    }

    private void performShockwave(ServerWorld server) {
        Box area = getBoundingBox().expand(6.0D);
        float damage = phase3 ? 7.0F : 5.0F;
        for (LivingEntity nearby : server.getEntitiesByClass(
                LivingEntity.class, area,
                e -> e != this && e.isAlive() && !(e instanceof KayaEntity))) {
            double dx = nearby.getX() - getX();
            double dz = nearby.getZ() - getZ();
            nearby.damage(getWorld().getDamageSources().mobAttack(this), damage);
            nearby.takeKnockback(1.3D, -dx, -dz);
            nearby.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 100, 1));
        }
        server.spawnParticles(ParticleTypes.EXPLOSION, getX(), getY() + 0.2D, getZ(), 1, 0, 0, 0, 0);
        playSound(SoundEvents.ENTITY_RAVAGER_ROAR, 2.0F, 0.8F);
    }

    // Phase 2 özel saldırısı: kısa menzilli taş dalgası. Hasarı düşük, ancak oyuncuyu geri iter.
    private void performPhase2RockWave(ServerWorld server) {
        Box area = getBoundingBox().expand(8.0D);
        for (LivingEntity nearby : server.getEntitiesByClass(
                LivingEntity.class, area,
                e -> e != this && e.isAlive() && !(e instanceof KayaEntity))) {
            double distance = Math.sqrt(squaredDistanceTo(nearby));
            if (distance > 2.5D) {
                nearby.damage(getWorld().getDamageSources().mobAttack(this), 3.0F);
                double dx = nearby.getX() - getX();
                double dz = nearby.getZ() - getZ();
                nearby.takeKnockback(0.75D, -dx, -dz);
                nearby.addStatusEffect(new StatusEffectInstance(StatusEffects.MINING_FATIGUE, 60, 0));
            }
        }
        server.spawnParticles(ParticleTypes.POOF, getX(), getY() + 0.15D, getZ(), 45, 1.8, 0.1, 1.8, 0.12);
        server.spawnParticles(ParticleTypes.CLOUD, getX(), getY() + 0.2D, getZ(), 20, 1.4, 0.1, 1.4, 0.04);
        playSound(SoundEvents.ENTITY_IRON_GOLEM_ATTACK, 1.2F, 0.65F);
    }

    // Final Phase özel saldırısı: ruh dalgası. Hafif hasar verir, yavaşlatır ve kısa süreliğine geri iter.
    private void performFinalPulse(ServerWorld server) {
        Box area = getBoundingBox().expand(9.0D);
        for (LivingEntity nearby : server.getEntitiesByClass(
                LivingEntity.class, area,
                e -> e != this && e.isAlive() && !(e instanceof KayaEntity))) {
            double dx = getX() - nearby.getX();
            double dz = getZ() - nearby.getZ();
            double distance = Math.max(1.0D, Math.sqrt(dx * dx + dz * dz));
            nearby.damage(getWorld().getDamageSources().mobAttack(this), 4.0F);
            nearby.takeKnockback(0.65D, -dx, -dz);
            nearby.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 50, 0));
        }
        server.spawnParticles(ParticleTypes.SOUL, getX(), getY() + 1.0D, getZ(), 55, 2.0, 0.8, 2.0, 0.02);
        server.spawnParticles(ParticleTypes.REVERSE_PORTAL, getX(), getY() + 1.0D, getZ(), 20, 1.3, 0.7, 1.3, 0.05);
        playSound(SoundEvents.ENTITY_ENDERMAN_STARE, 1.0F, 0.7F);
    }

    /** Entity ölüm dışında da (discard/kill/unload) kaldırılsa boss bar'lar ekranda takılı kalmasın. */
    @Override
    public void remove(RemovalReason reason) {
        bossBar.clearPlayers();
        minionBar.clearPlayers();
        super.remove(reason);
    }

    @Override
    public void onDeath(DamageSource source){
        if (guardian) {
            bossBar.clearPlayers();
            bossBar.setVisible(false);
            minionBar.clearPlayers();
            minionBar.setVisible(false);
            if (getWorld() instanceof ServerWorld server) {
                server.spawnParticles(ParticleTypes.SOUL, getX(), getY() + 1.0D, getZ(), 45, .8, 1.0, .8, .03);
                server.playSound(null, getBlockPos(), SoundEvents.ENTITY_PLAYER_DEATH, SoundCategory.HOSTILE, 1.0F, .75F);
            }
            super.onDeath(source);
            return;
        }
        if(!minion&&phase2&&hasLivingMinions()){setHealth(1);return;}
        if(!minion){
            bossBar.clearPlayers();bossBar.setVisible(false);minionBar.clearPlayers();minionBar.setVisible(false);
            if (getWorld() instanceof ServerWorld server) {
                server.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getY() + 1.0D, getZ(), 3, 0.6, 0.6, 0.6, 0);
                server.createExplosion(this, getX(), getY(), getZ(), 8.0F, true, World.ExplosionSourceType.MOB);
                // Ahmet's death reveals the next chapter of the story.
                net.friends.mesutcmod.ritual.StoryHandler.spawnMesutChronicle(server, getBlockPos());
            }
        }
        super.onDeath(source);
    }
}
