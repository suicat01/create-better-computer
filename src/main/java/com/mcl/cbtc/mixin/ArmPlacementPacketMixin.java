package com.mcl.cbtc.mixin;

import com.mcl.cbtc.blockentity.SergeantValeryBlockEntity;
import com.mcl.cbtc.client.sergeant.SergeantArmAccess;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmPlacementPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 原版 {@code ArmPlacementPacket} 只把交互点 NBT 写进方块实体，不会让动力臂重新解析；
 * 新放置的方块靠构造函数里的初始标记才会生效，已放置的方块则会"绑定成功但没反应"。
 * <p>
 * 这里在写回之后补一个置脏 + 重置序列，让瓦列里中士的重绑立即生效。
 */
@Mixin(value = ArmPlacementPacket.class, remap = false)
public abstract class ArmPlacementPacketMixin {
    @Inject(method = "handle", at = @At("TAIL"))
    private void cbtc$refreshSergeantPoints(ServerPlayer player, CallbackInfo callback) {
        BlockPos pos = ((ArmPlacementPacket) (Object) this).pos();
        if (player.level().getBlockEntity(pos) instanceof SergeantValeryBlockEntity sergeant) {
            ((SergeantArmAccess) (Object) sergeant).cbtc$markPointsDirty();
            sergeant.onPointsBound();
        }
    }
}
