package com.mcl.cbtc.event;

import com.mcl.cbtc.CreateBetterComputer;
import com.mcl.cbtc.entity.SuperBlaze;
import com.mcl.cbtc.registry.CbcEntities;
import com.mcl.cbtc.registry.CbcItems;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlockEntity;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * 超级烈焰人的火球增强：伤害翻倍（原版 5 → 10），着火时间延长到 20 秒。
 */
public final class SuperBlazeEvents {
    private static final ResourceLocation BLAZE_CAKE_ID =
            ResourceLocation.fromNamespaceAndPath("create", "blaze_cake");

    private SuperBlazeEvents() {
    }

    /** 手持烈焰蛋糕（机械动力）喂食普通烈焰人 → 转换为超级烈焰人。 */
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide) {
            return;
        }
        Entity target = event.getTarget();
        if (!(target instanceof Blaze blaze) || blaze instanceof SuperBlaze) {
            return;
        }
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty() || stack.getItem() != BuiltInRegistries.ITEM.get(BLAZE_CAKE_ID)) {
            return;
        }
        SuperBlaze converted = CbcEntities.SUPER_BLAZE.get().create(event.getLevel());
        if (converted == null) {
            return;
        }
        converted.moveTo(blaze.getX(), blaze.getY(), blaze.getZ(), blaze.getYRot(), blaze.getXRot());
        converted.setYHeadRot(blaze.getYHeadRot());
        event.getLevel().addFreshEntity(converted);
        blaze.discard();
        // 转换特效：灵魂火+普通火焰粒子，配火焰弹音效
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            double y = blaze.getY() + blaze.getBbHeight() * 0.5;
            serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    blaze.getX(), y, blaze.getZ(), 240, 0.4, 0.8, 0.4, 0.02);
            serverLevel.sendParticles(ParticleTypes.FLAME,
                    blaze.getX(), y, blaze.getZ(), 180, 0.4, 0.8, 0.4, 0.05);
            serverLevel.playSound(null, blaze.blockPosition(), SoundEvents.BLAZE_SHOOT,
                    SoundSource.HOSTILE, 1.0F, 0.5F);
            serverLevel.playSound(null, blaze.blockPosition(), SoundEvents.FIRECHARGE_USE,
                    SoundSource.NEUTRAL, 1.0F, 0.9F);
        }
        Player player = event.getEntity();
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    public static void onDamagePre(LivingDamageEvent.Pre event) {
        if (isSuperBlazeFireball(event.getSource())) {
            event.setNewDamage(event.getNewDamage() * 2.0F);
        }
    }

    public static void onDamagePost(LivingDamageEvent.Post event) {
        if (isSuperBlazeFireball(event.getSource())) {
            event.getEntity().igniteForSeconds(20);
        }
    }

    private static boolean isSuperBlazeFireball(DamageSource source) {
        return source.getDirectEntity() instanceof SmallFireball fireball
                && fireball.getOwner() instanceof SuperBlaze;
    }

    /**
     * 首次把超级烈焰棒喂进烈焰人燃烧室时授予进度【本是同根生...】。
     * 用 Create 的 tryInsert 模拟一次喂养，只有真正能喂进去时才授予。
     */
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide) {
            return;
        }
        ItemStack stack = event.getItemStack();
        if (!stack.is(CbcItems.SUPER_BLAZE_ROD.get())) {
            return;
        }
        BlockState state = event.getLevel().getBlockState(event.getPos());
        if (!(state.getBlock() instanceof BlazeBurnerBlock)) {
            return;
        }
        Player player = event.getEntity();
        InteractionResultHolder<ItemStack> simulated = BlazeBurnerBlock.tryInsert(state, event.getLevel(),
                event.getPos(), stack, player.isCreative(), !(player instanceof FakePlayer), true);
        if (simulated.getResult() != InteractionResult.SUCCESS) {
            // 燃烧室里已有超过 500 刻的超级燃烧时，Create 会拒绝再喂任何超级燃料（烈焰蛋糕同理）
            if (event.getLevel().getBlockEntity(event.getPos()) instanceof BlazeBurnerBlockEntity burner) {
                CreateBetterComputer.LOGGER.info(
                        "[cbtc] 喂养被燃烧室拒绝：item={} 当前燃料={} 剩余={}刻 创造模式={}",
                        stack.getItem(), burner.getActiveFuel(), burner.getRemainingBurnTime(), burner.isCreative());
            }
            return;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            AdvancementHolder advancement = serverPlayer.server.getAdvancements()
                    .get(ResourceLocation.fromNamespaceAndPath(CreateBetterComputer.MODID, "same_root"));
            if (advancement != null) {
                serverPlayer.getAdvancements().award(advancement, "super_rod");
            }
        }
    }
}
