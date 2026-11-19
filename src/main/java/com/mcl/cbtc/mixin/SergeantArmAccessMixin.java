package com.mcl.cbtc.mixin;

import com.mcl.cbtc.client.sergeant.SergeantArmAccess;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmAngleTarget;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmBlockEntity;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPoint;
import java.util.List;
import net.createmod.catnip.animation.LerpedFloat;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * 把动力臂方块实体的动画与装填状态暴露给自定义渲染器 / 装填逻辑。
 * <p>
 * 这些字段是包内可见的，瓦列里中士的代码在别的包里读不到，所以用接口注入的方式开放访问。
 */
@Mixin(value = ArmBlockEntity.class, remap = false)
public abstract class SergeantArmAccessMixin implements SergeantArmAccess {
    @Shadow
    LerpedFloat baseAngle;
    @Shadow
    LerpedFloat lowerArmAngle;
    @Shadow
    LerpedFloat upperArmAngle;
    @Shadow
    LerpedFloat headAngle;
    @Shadow
    boolean goggles;
    @Shadow
    ItemStack heldItem;
    @Shadow
    List<ArmInteractionPoint> outputs;
    @Shadow
    ArmBlockEntity.Phase phase;
    @Shadow
    int chasedPointIndex;
    @Shadow
    float chasedPointProgress;
    @Shadow
    protected int lastOutputIndex;
    @Shadow
    ListTag interactionPointTag;
    @Shadow
    boolean updateInteractionPoints;
    @Shadow
    ArmAngleTarget previousTarget;
    @Shadow
    float previousBaseAngle;

    @Override
    public float cbtc$baseAngle(float partialTicks) {
        return baseAngle.getValue(partialTicks);
    }

    @Override
    public float cbtc$lowerArmAngle(float partialTicks) {
        return lowerArmAngle.getValue(partialTicks);
    }

    @Override
    public float cbtc$upperArmAngle(float partialTicks) {
        return upperArmAngle.getValue(partialTicks);
    }

    @Override
    public float cbtc$headAngle(float partialTicks) {
        return headAngle.getValue(partialTicks);
    }

    @Override
    public boolean cbtc$goggles() {
        return goggles;
    }

    @Override
    public ItemStack cbtc$heldItem() {
        return heldItem;
    }

    @Override
    public List<ArmInteractionPoint> cbtc$outputs() {
        return outputs;
    }

    @Override
    public int cbtc$chasedIndex() {
        return chasedPointIndex;
    }

    @Override
    public void cbtc$setHeldItem(ItemStack stack) {
        heldItem = stack;
    }

    @Override
    public void cbtc$moveToOutput(int index) {
        phase = ArmBlockEntity.Phase.MOVE_TO_OUTPUT;
        chasedPointIndex = index;
        chasedPointProgress = 0.0F;
        lastOutputIndex = index;
        flush();
    }

    @Override
    public void cbtc$resetToIdle() {
        phase = ArmBlockEntity.Phase.SEARCH_INPUTS;
        chasedPointIndex = -1;
        chasedPointProgress = 0.0F;
        flush();
    }

    @Override
    public void cbtc$settleIdlePose() {
        // 没有转动动力时 chasedPointProgress 不会推进，原版逻辑会让手臂一直停在
        // 上一次目标的插值起点（看起来就是"手臂前伸"）；这里直接把它落回待机姿态。
        ArmAngleTarget rest = restTarget();
        if (rest == null) {
            return;
        }
        previousTarget = rest;
        float base = restAngle("baseAngle");
        previousBaseAngle = base;
        baseAngle.setValue(base);
        lowerArmAngle.setValue(restAngle("lowerArmAngle"));
        upperArmAngle.setValue(restAngle("upperArmAngle"));
        headAngle.setValue(restAngle("headAngle"));
    }

    /** NO_TARGET 是包内静态字段，反射读取。 */
    private static ArmAngleTarget restTarget() {
        try {
            var field = ArmAngleTarget.class.getDeclaredField("NO_TARGET");
            field.setAccessible(true);
            return (ArmAngleTarget) field.get(null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** ArmAngleTarget 的角度字段也是包内可见，反射读取。 */
    private static float restAngle(String name) {
        try {
            var field = ArmAngleTarget.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.getFloat(restTarget());
        } catch (Throwable ignored) {
            return 0.0F;
        }
    }

    @Override
    public ListTag cbtc$interactionPoints() {
        return interactionPointTag;
    }

    @Override
    public void cbtc$setInteractionPoints(ListTag tag) {
        interactionPointTag = tag;
        updateInteractionPoints = true;
        flush();
    }

    @Override
    public void cbtc$markPointsDirty() {
        updateInteractionPoints = true;
        flush();
    }

    private void flush() {
        ArmBlockEntity self = (ArmBlockEntity) (Object) this;
        self.setChanged();
        self.sendData();
    }
}
