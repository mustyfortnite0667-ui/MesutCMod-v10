package net.friends.mesutcmod.client;

import net.friends.mesutcmod.entity.KayaEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.util.Identifier;

public class KayaRenderer extends MobEntityRenderer<KayaEntity, BipedEntityModel<KayaEntity>> {
    private static final Identifier TEXTURE = new Identifier("mesutcmod", "textures/entity/kaya.png");

    public KayaRenderer(EntityRendererFactory.Context context) {
        super(context, new BipedEntityModel<>(context.getPart(EntityModelLayers.PLAYER)), 0.55F);
    }

    @Override
    public Identifier getTexture(KayaEntity entity) {
        return TEXTURE;
    }
}
