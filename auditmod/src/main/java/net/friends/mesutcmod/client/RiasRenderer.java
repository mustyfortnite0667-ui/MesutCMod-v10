package net.friends.mesutcmod.client;

import net.friends.mesutcmod.entity.RiasEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.util.Identifier;

public class RiasRenderer extends MobEntityRenderer<RiasEntity, BipedEntityModel<RiasEntity>> {
    private static final Identifier TEXTURE = new Identifier("mesutcmod", "textures/entity/rias.png");

    public RiasRenderer(EntityRendererFactory.Context context) {
        super(context, new BipedEntityModel<>(context.getPart(EntityModelLayers.PLAYER)), 0.35F);
    }

    @Override
    public Identifier getTexture(RiasEntity entity) {
        return TEXTURE;
    }
}
