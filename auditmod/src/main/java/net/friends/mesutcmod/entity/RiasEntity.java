package net.friends.mesutcmod.entity;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.world.World;

/** A summon-only, peaceful Rias NPC. Emits hearts for a short time after spawning. */
public class RiasEntity extends ZombieEntity {
    private int heartTicks = 100;
    private boolean spawnHeartsDone = false;

    public RiasEntity(EntityType<? extends ZombieEntity> type, World world) {
        super(type, world);
        setPersistent();
        setAiDisabled(true);
        setSilent(true);
        setCanPickUpLoot(false);
        setCustomName(Text.literal("Rias"));
        setCustomNameVisible(true);
        setHealth(getMaxHealth());
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return ZombieEntity.createZombieAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 40.0D)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.0D)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0D);
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putInt("RiasHeartTicks", heartTicks);
        nbt.putBoolean("RiasSpawnHeartsDone", spawnHeartsDone);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        if (nbt.contains("RiasHeartTicks")) heartTicks = nbt.getInt("RiasHeartTicks");
        spawnHeartsDone = nbt.getBoolean("RiasSpawnHeartsDone");
    }

    @Override
    public void tick() {
        super.tick();
        setAiDisabled(true);
        setSilent(true);
        if (!(getWorld() instanceof ServerWorld server) || heartTicks <= 0) return;

        if (!spawnHeartsDone) {
            server.spawnParticles(ParticleTypes.HEART, getX(), getY() + 1.0, getZ(), 28, .65, 1.0, .65, .02);
            spawnHeartsDone = true;
        }
        if (age % 8 == 0) {
            server.spawnParticles(ParticleTypes.HEART, getX(), getY() + 1.1, getZ(), 2, .35, .55, .35, .01);
        }
        heartTicks--;
    }
}
