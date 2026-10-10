package net.friends.mesutcmod.entity;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.friends.mesutcmod.MesutCMod;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class ModEntities {

    public static final EntityType<MesutChristEntity> MESUT_CHRIST = Registry.register(
            Registries.ENTITY_TYPE,
            new Identifier(MesutCMod.MOD_ID, "mesut_christ"),
            EntityType.Builder.create(MesutChristEntity::new, SpawnGroup.MONSTER).setDimensions(0.6f, 1.95f).build("mesutcmod:mesut_christ")
    );

    public static final EntityType<MidasEntity> MIDAS = Registry.register(
            Registries.ENTITY_TYPE,
            new Identifier(MesutCMod.MOD_ID, "midas"),
            EntityType.Builder.create(MidasEntity::new, SpawnGroup.MONSTER).setDimensions(0.6f, 1.95f).build("mesutcmod:midas")
    );

    public static final EntityType<RiasEntity> RIAS = Registry.register(
            Registries.ENTITY_TYPE,
            new Identifier(MesutCMod.MOD_ID, "rias"),
            EntityType.Builder.create(RiasEntity::new, SpawnGroup.MISC).setDimensions(0.6f, 1.95f).build("mesutcmod:rias")
    );

    public static final EntityType<KayaEntity> KAYA = Registry.register(
            Registries.ENTITY_TYPE,
            new Identifier(MesutCMod.MOD_ID, "kaya"),
            EntityType.Builder.create(KayaEntity::new, SpawnGroup.MONSTER).setDimensions(0.6f, 1.95f).build("mesutcmod:kaya")
    );

    public static void register() {
        FabricDefaultAttributeRegistry.register(MIDAS, MidasEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(RIAS, RiasEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(KAYA, KayaEntity.createKayaAttributes());
        FabricDefaultAttributeRegistry.register(MESUT_CHRIST, MesutChristEntity.createAttributes());
    }
}
