package com.mcl.cbtc.event;

import com.mcl.cbtc.registry.CbcEntities;
import com.mcl.cbtc.world.SuperSpawnerTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BaseSpawner;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SpawnerBlock;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** 给烈焰人刷怪笼喂烈焰蛋糕 → 变成超级烈焰人刷怪笼，30 秒后还原。 */
public final class SuperSpawnerEvents {
    private static final ResourceLocation BLAZE_CAKE_ID = ResourceLocation.fromNamespaceAndPath("create", "blaze_cake");
    private static final long DURATION_TICKS = 600L;

    private SuperSpawnerEvents() {
    }

    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide) {
            return;
        }
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty() || !stack.is(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(BLAZE_CAKE_ID))) {
            return;
        }
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof SpawnerBlock)) {
            return;
        }
        if (!(level.getBlockEntity(pos) instanceof SpawnerBlockEntity spawnerBE)) {
            return;
        }
        BaseSpawner spawner = spawnerBE.getSpawner();
        String entityId = currentEntityId(spawner);
        // 只接受烈焰人刷怪笼（已转换过的再次喂食会刷新持续时间）
        if (!"minecraft:blaze".equals(entityId)
                && !CbcEntities.SUPER_BLAZE.getId().toString().equals(entityId)) {
            return;
        }

        convert(spawner, level, pos, CbcEntities.SUPER_BLAZE.get());
        spawnerBE.setChanged();
        level.sendBlockUpdated(pos, state, state, 3);
        SuperSpawnerTracker.get(level.getServer()).schedule(level, pos, level.getGameTime() + DURATION_TICKS);
        level.playSound(null, pos, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 1.0F, 0.6F);
        // 与烈焰人转换一致的爆发特效
        if (level instanceof ServerLevel serverLevel) {
            double x = pos.getX() + 0.5;
            double y = pos.getY() + 0.5;
            double z = pos.getZ() + 0.5;
            serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, x, y, z, 240, 0.4, 0.8, 0.4, 0.02);
            serverLevel.sendParticles(ParticleTypes.FLAME, x, y, z, 180, 0.4, 0.8, 0.4, 0.05);
            serverLevel.playSound(null, pos, SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 1.0F, 0.5F);
        }

        if (!event.getEntity().isCreative()) {
            stack.shrink(1);
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    /** 30 秒到期后把刷怪笼还原成普通烈焰人。 */
    public static void revert(Level level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof SpawnerBlockEntity spawnerBE)) {
            return;
        }
        convert(spawnerBE.getSpawner(), level, pos, EntityType.BLAZE);
        spawnerBE.setChanged();
        level.sendBlockUpdated(pos, level.getBlockState(pos), level.getBlockState(pos), 3);
    }

    /**
     * 只改生成实体类型：必须同时清空 SpawnPotentials，否则原版会继续按权重列表随机生成。
     * 改完后再 load 一遍，让内部权重列表同步成单一的新实体。
     */
    private static void convert(BaseSpawner spawner, Level level, BlockPos pos, EntityType<?> type) {
        spawner.setEntityId(type, level, level.random, pos);
        CompoundTag tag = spawner.save(new CompoundTag());
        tag.remove("SpawnPotentials");
        spawner.load(level, pos, tag);
    }

    private static String currentEntityId(BaseSpawner spawner) {
        CompoundTag tag = spawner.save(new CompoundTag());
        return tag.getCompound("SpawnData").getCompound("entity").getString("id");
    }
}
