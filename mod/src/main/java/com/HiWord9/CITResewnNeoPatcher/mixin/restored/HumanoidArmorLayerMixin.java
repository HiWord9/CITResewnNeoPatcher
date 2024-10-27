package com.HiWord9.CITResewnNeoPatcher.mixin.restored;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import shcm.shsupercm.fabric.citresewn.cit.CIT;
import shcm.shsupercm.fabric.citresewn.cit.CITContext;
import shcm.shsupercm.fabric.citresewn.defaults.cit.types.TypeArmor;

import java.util.Map;

import static shcm.shsupercm.fabric.citresewn.defaults.cit.types.TypeArmor.CONTAINER;

// original is shcm.shsupercm.fabric.citresewn.defaults.mixin.types.armor.ArmorFeatureRendererMixin
@Mixin(HumanoidArmorLayer.class)
public class HumanoidArmorLayerMixin<T extends LivingEntity, M extends HumanoidModel<T>, A extends HumanoidModel<T>> {
    private Map<String, ResourceLocation> citresewn$cachedTextures = null;

    @Inject(
            method = "renderArmorPiece(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/EquipmentSlot;ILnet/minecraft/client/model/HumanoidModel;FFFFFF)V",
            at = @At(
                    "HEAD"
            )
    )
    public void citresewn$renderArmor(
            PoseStack matrices, MultiBufferSource vertexConsumers, T entity,
            EquipmentSlot armorSlot, int light, A model,
            float limbSwing, float limbSwingAmount, float partialTick,
            float ageInTicks, float netHeadYaw, float headPitch,
            CallbackInfo ci
    ) {
        citresewn$cachedTextures = null;
        if (!CONTAINER.active())
            return;

        ItemStack equippedStack = CONTAINER.getVisualItemInSlot(entity, armorSlot);

        CIT<TypeArmor> cit = CONTAINER.getCIT(new CITContext(equippedStack, entity.level(), entity));
        if (cit != null)
            citresewn$cachedTextures = cit.type.textures;
    }

    @WrapOperation(
            method = "renderArmorPiece(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/EquipmentSlot;ILnet/minecraft/client/model/HumanoidModel;FFFFFF)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/neoforged/neoforge/client/ClientHooks;getArmorTexture(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ArmorMaterial$Layer;ZLnet/minecraft/world/entity/EquipmentSlot;)Lnet/minecraft/resources/ResourceLocation;"
            )
    )
    public ResourceLocation citresewn$replaceArmorTexture(
            Entity entity, ItemStack armor,
            ArmorMaterial.Layer layer, boolean secondLayer,
            EquipmentSlot slot,
            Operation<ResourceLocation> original
    ) {
        if (citresewn$cachedTextures != null) {
            String layerPath = layer.texture(secondLayer).getPath();
            ResourceLocation identifier = citresewn$cachedTextures.get(layerPath.substring("textures/models/armor/".length(), layerPath.length() - ".png".length()));
            if (identifier != null)
                return identifier;
        }
        return original.call(entity, armor, layer, secondLayer, slot);
    }
}