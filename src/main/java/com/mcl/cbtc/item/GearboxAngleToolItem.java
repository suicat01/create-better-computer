package com.mcl.cbtc.item;

import com.mcl.cbtc.device.Devices;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * 齿轮箱角度标定器：右键 CB编程齿轮箱，把它的输出轴当前朝向记作 0°。
 * <p>
 * 零点存在齿轮箱方块自身的 NBT 里，标定一次即可；之后积木里的「旋转至」角度
 * 与「齿轮箱角度」积木都相对这个零点计算。物品本身不携带任何数据。
 */
public class GearboxAngleToolItem extends Item {
    public GearboxAngleToolItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getPlayer() == null) {
            return InteractionResult.PASS;
        }
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (com.mcl.cbtc.device.Devices.kindAt(level, pos) != com.mcl.cbtc.device.DeviceKind.GEARBOX) {
            return InteractionResult.PASS;
        }
        // 标定写的是方块 NBT，只能由服务端执行；客户端只负责让动作有反馈
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        Devices.zeroGearboxAngle(level, pos);
        level.playSound(null, pos, SoundEvents.ITEM_FRAME_ROTATE_ITEM,
                SoundSource.BLOCKS, 0.6F, 1.4F);
        context.getPlayer().displayClientMessage(
                Component.translatable("item.cbtc.gearbox_angle_tool.done"), true);
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip,
            TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("item.cbtc.gearbox_angle_tool.hint")
                .withStyle(ChatFormatting.GRAY));
    }
}
