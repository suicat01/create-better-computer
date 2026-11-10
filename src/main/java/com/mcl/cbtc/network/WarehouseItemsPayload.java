package com.mcl.cbtc.network;

import com.mcl.cbtc.CreateBetterComputer;
import com.simibubi.create.content.logistics.BigItemStack;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

/** 服务端 → 客户端：物品选择器所需的完整物品堆与库存数量。 */
public record WarehouseItemsPayload(BlockPos pos, List<BigItemStack> stacks) implements CustomPacketPayload {
    public static final Type<WarehouseItemsPayload> TYPE =
            new Type<>(CreateBetterComputer.asResource("warehouse_items"));

    public static final StreamCodec<RegistryFriendlyByteBuf, WarehouseItemsPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        BlockPos.STREAM_CODEC.encode(buf, payload.pos());
                        buf.writeVarInt(payload.stacks().size());
                        for (BigItemStack stack : payload.stacks()) {
                            ItemStack.STREAM_CODEC.encode(buf, stack.stack);
                            buf.writeVarInt(stack.count);
                        }
                    },
                    buf -> {
                        BlockPos pos = BlockPos.STREAM_CODEC.decode(buf);
                        int size = buf.readVarInt();
                        List<BigItemStack> stacks = new ArrayList<>(size);
                        for (int i = 0; i < size; i++) {
                            ItemStack stack = ItemStack.STREAM_CODEC.decode(buf);
                            stacks.add(new BigItemStack(stack, buf.readVarInt()));
                        }
                        return new WarehouseItemsPayload(pos, stacks);
                    });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
