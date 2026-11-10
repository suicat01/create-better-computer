package com.mcl.cbtc.block;

import com.mcl.cbtc.blockentity.CBGearboxBlockEntity;
import com.mcl.cbtc.registry.CbcBlockEntities;
import com.simibubi.create.content.kinetics.transmission.sequencer.SequencedGearshiftBlock;
import com.simibubi.create.content.kinetics.transmission.sequencer.SequencedGearshiftBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * CB编程齿轮箱：外观复用机械动力的序列齿轮箱（黄铜外壳 + 指示灯），
 * 转速控制改由积木下发，不读取自身的序列指令表。
 */
public class CBGearboxBlock extends SequencedGearshiftBlock {
    public CBGearboxBlock(Properties properties) {
        super(properties);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Class<SequencedGearshiftBlockEntity> getBlockEntityClass() {
        return (Class<SequencedGearshiftBlockEntity>) (Class<?>) CBGearboxBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends SequencedGearshiftBlockEntity> getBlockEntityType() {
        return CbcBlockEntities.GEARBOX.get();
    }

    /**
     * 不使用原版的序列编辑界面：右键不弹序列配置，避免和积木控制混淆。
     * 仍保留扳手旋转等原有交互。
     */
    @Override
    protected void displayScreen(SequencedGearshiftBlockEntity be, Player player) {
    }

    /**
     * 原版序列齿轮箱会在手持任意物品右键时返回 SUCCESS（打开序列界面），
     * 这会把交互直接消费掉，导致外设模块卡的 useOn 永远不被调用、无法绑定。
     * 这里一律放行，让右键落到手持物品（外设模块卡、扳手等）自身的逻辑上。
     */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hitResult) {
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }
}
