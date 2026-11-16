package com.mcl.cbtc.item;

import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * 枪式遥控器：手持右键进入"使用状态"，此时鼠标左右键会像打字机键盘一样被电脑读取。
 * <p>
 * 按 Esc、切换到别的物品或打开界面都会退出使用状态。
 * 每件遥控器自带稳定 ID，工作台里和外设绑定模块卡合成后作为物品型外设登记，
 * 电脑按这个 ID 读取它的按键。
 */
public class GunRemoteItem extends Item {
    private static final String TAG_ID = "GunRemoteId";

    public GunRemoteItem(Properties properties) {
        super(properties);
    }

    /** 是否读取整个键盘与鼠标侧键；普通枪式遥控器只读左右键。 */
    public boolean fullKeyboard() {
        return false;
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        // 服务端先分配好 ID 再同步给客户端，保证两边拿到的 ID 一致
        if (!level.isClientSide) {
            ensureId(stack);
        }
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            com.mcl.cbtc.client.GunRemoteInput.beginSession(stack, hand);
        }
        // 用 CONSUME：吞掉这次右键，既不触发方块交互也不播挥手动画
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        // 右键方块同样进入使用状态，并且不触发方块自身的交互
        if (context.getLevel().isClientSide) {
            com.mcl.cbtc.client.GunRemoteInput.beginSession(context.getItemInHand(), context.getHand());
        }
        return InteractionResult.CONSUME;
    }

    /** 遥控器 ID；没有生成过时返回空串。 */
    public static String id(ItemStack stack) {
        return tag(stack).getString(TAG_ID);
    }

    /** 遥控器 ID，缺失时生成一个并写回物品。 */
    public static String ensureId(ItemStack stack) {
        String id = id(stack);
        if (!id.isBlank()) {
            return id;
        }
        id = UUID.randomUUID().toString();
        CompoundTag tag = tag(stack);
        tag.putString(TAG_ID, id);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return id;
    }

    private static CompoundTag tag(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? new CompoundTag() : data.copyTag();
    }
}
