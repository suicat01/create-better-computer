package com.mcl.cbtc.client.sergeant;

import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPoint;
import java.util.List;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;

/**
 * 读出 / 驱动 Create 动力臂方块实体内部状态的访问口。
 * <p>
 * {@code ArmBlockEntity} 的角度、交互点、手持物、执行阶段都是包内可见字段，外部包拿不到；
 * 由 {@code SergeantArmAccessMixin} 在目标类上实现本接口，渲染器与装填逻辑按接口取值。
 */
public interface SergeantArmAccess {
    // ---------------------------------------------------------------- 渲染
    float cbtc$baseAngle(float partialTicks);

    float cbtc$lowerArmAngle(float partialTicks);

    float cbtc$upperArmAngle(float partialTicks);

    float cbtc$headAngle(float partialTicks);

    boolean cbtc$goggles();

    ItemStack cbtc$heldItem();

    // ---------------------------------------------------------------- 装填
    /** 投放点列表（原版 outputs）。 */
    List<ArmInteractionPoint> cbtc$outputs();

    /** 当前正在靠近的投放点下标；空闲时为 -1。 */
    int cbtc$chasedIndex();

    void cbtc$setHeldItem(ItemStack stack);

    /** 进入"正在伸向第 index 个投放点"的状态，原版动画会自己插值过去。 */
    void cbtc$moveToOutput(int index);

    /** 回到空闲态（SEARCH_INPUTS），进度清零。 */
    void cbtc$resetToIdle();

    /** 没有转动动力时把姿态落回待机位，避免手臂僵在上一个目标的角度上。 */
    void cbtc$settleIdlePose();

    /** 已绑定的交互点原始 NBT。 */
    ListTag cbtc$interactionPoints();

    /** 写回交互点，并让动力臂重新解析它们。 */
    void cbtc$setInteractionPoints(ListTag tag);

    /** 仅把交互点标记为需要重新解析（用于重绑后强制刷新）。 */
    void cbtc$markPointsDirty();
}
