package com.mcl.cbtc.mixin;

import com.mcl.cbtc.compat.SentryControl;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAmmoBox;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.resource.pojo.data.gun.Bolt;
import euphy.upo.sentrymechanicalarm.content.SentryArmBlockEntity;
import euphy.upo.sentrymechanicalarm.content.SentryArmBlockEntity.SentryStatus;
import euphy.upo.sentrymechanicalarm.network.SentryShootPacket.ActionType;
import euphy.upo.sentrymechanicalarm.util.SentryFakePlayer;
import euphy.upo.sentrymechanicalarm.util.SentryGunGeometry;
import euphy.upo.sentrymechanicalarm.util.SentryGunProfiles;
import net.createmod.catnip.animation.LerpedFloat;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 让 CB 电脑接管哨戒动力臂。
 * <p>
 * 接管状态保存在动力臂自己的 NBT 里，因此重新加载区块/重启服务器后依然有效：
 * 绑定到外设模块卡即停止自动索敌与自动开火，解绑后恢复原版行为。
 * 瞄准与开火分别由积木调用 {@link SentryControl} 上的方法完成。
 * <p>
 * 目标模组是软依赖，用 {@code targets} + {@code @Pseudo} 声明，未安装时整段逻辑会被跳过。
 */
@Pseudo
@Mixin(targets = "euphy.upo.sentrymechanicalarm.content.SentryArmBlockEntity", remap = false)
public abstract class SentryArmBlockEntityMixin implements SentryControl {
    @Shadow
    public LerpedFloat lowerArmAngle;
    @Shadow
    public LerpedFloat upperArmAngle;
    @Shadow
    public LerpedFloat headAngle;

    @Unique
    private boolean cbtc$controlled;
    @Unique
    private Vec3 cbtc$aim;
    @Unique
    private long cbtc$lastSync;
    /** 接管模式下换弹结束的 gameTime；期间扣扳机无效，换弹耗时跟随武器数据。 */
    @Unique
    private long cbtc$reloadUntil;

    @Shadow
    public abstract ItemStack getHeldItem();

    @Shadow
    public abstract boolean isCeiling();

    @Shadow
    public abstract Vec3 worldToSubLevel(Vec3 pos);

    @Shadow
    public abstract SentryGunGeometry.Pose getLocalGunPose();

    @Shadow
    public abstract SentryGunGeometry.Pose getGunPose();

    @Shadow
    public abstract boolean isShooterActive();

    @Shadow
    public abstract boolean hasAnyAmmo();

    @Shadow
    @Final
    public NonNullList<ItemStack> attachedAmmoBoxes;

    @Shadow
    private float shootDelayAccumulator;

    @Shadow
    private ItemStack heldItem;

    @org.spongepowered.asm.mixin.gen.Invoker("aimAtJoints")
    @Override
    public abstract void cbtc$aimAtJoints(float yaw, float pitch);

    @org.spongepowered.asm.mixin.gen.Invoker("fireGun")
    @Override
    public abstract void cbtc$fireGun(float yaw, float pitch);

    @org.spongepowered.asm.mixin.gen.Invoker("performInstantReload")
    @Override
    public abstract boolean cbtc$performInstantReload(FakePlayer player, IGun gun, ItemStack stack);

    @org.spongepowered.asm.mixin.gen.Invoker("sendActionPacket")
    @Override
    public abstract void cbtc$sendActionPacket(ActionType type);

    @org.spongepowered.asm.mixin.gen.Invoker("setStatus")
    @Override
    public abstract void cbtc$setStatus(SentryStatus status);

    @Override
    public void cbtc$setControlled(boolean controlled) {
        if (this.cbtc$controlled == controlled) {
            return;
        }
        this.cbtc$controlled = controlled;
        if (!controlled) {
            // 恢复自动攻击时清掉外部瞄准点，客户端不再覆盖原版的角度计算
            this.cbtc$aim = null;
        }
        cbtc$setChanged();
        Level level = cbtc$level();
        if (level != null && !level.isClientSide) {
            cbtc$sendData();
        }
    }

    @Override
    public boolean cbtc$isControlled() {
        return this.cbtc$controlled;
    }

    @Override
    public void cbtc$aimAt(Vec3 target) {
        if (target == null) {
            return;
        }
        this.cbtc$aim = target;
        if (!this.cbtc$controlled) {
            cbtc$setControlled(true);
        }
        Level level = cbtc$level();
        if (level == null) {
            return;
        }
        if (level.isClientSide) {
            return;
        }
        cbtc$applyAim(target);
        // 瞄准点每 tick 同步给客户端，客户端据此逐刻推算关节角度；同步太稀会一卡一卡
        long now = level.getGameTime();
        if (now != this.cbtc$lastSync) {
            this.cbtc$lastSync = now;
            cbtc$sendData();
        }
    }

    @Override
    public void cbtc$fireOnce() {
        Level level = cbtc$level();
        if (level == null || level.isClientSide || !isShooterActive()) {
            return;
        }
        if (level.getGameTime() < this.cbtc$reloadUntil) {
            // 换弹还没结束，扣扳机无效
            return;
        }
        // 弹匣打空时，这一次"射击"自动变成换弹
        if (cbtc$outOfAmmo()) {
            cbtc$reload();
            return;
        }
        if (!hasAnyAmmo()) {
            return;
        }
        // 当前枪口姿态只决定开枪位置；方向直接指向瞄准点，
        // 这样扫射转移时子弹跟着准星走，不用等机械臂转到位。
        SentryGunGeometry.Pose pose = getGunPose();
        float yaw = pose.yaw();
        float pitch = pose.pitch();
        Vec3 aim = this.cbtc$aim;
        if (aim != null) {
            Vec3 dir = aim.subtract(pose.position());
            if (dir.lengthSqr() > 1.0E-6D) {
                double horizontal = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
                yaw = (float) (Mth.atan2(-dir.x, dir.z) * (180.0D / Math.PI));
                pitch = (float) (-Mth.atan2(dir.y, horizontal) * (180.0D / Math.PI));
            }
        }
        cbtc$fireGun(yaw, pitch);
    }

    /** 与 TaCZ 的判定一致：开膛枪的膛内子弹不算可用弹药。 */
    @Unique
    private boolean cbtc$outOfAmmo() {
        ItemStack held = getHeldItem();
        IGun gun = held.isEmpty() ? null : IGun.getIGunOrNull(held);
        if (gun == null || gun.useInventoryAmmo(held)) {
            return false;
        }
        boolean openBolt = TimelessAPI.getCommonGunIndex(gun.getGunId(held))
                .map(index -> index.getGunData().getBolt() == Bolt.OPEN_BOLT)
                .orElse(false);
        int count = gun.getCurrentAmmoCount(held) + ((!openBolt && gun.hasBulletInBarrel(held)) ? 1 : 0);
        return count <= 0;
    }

    @Override
    public void cbtc$reload() {
        Level level = cbtc$level();
        if (level == null || level.isClientSide || !isShooterActive()) {
            return;
        }
        FakePlayer player = SentryFakePlayer.get((SentryArmBlockEntity) (Object) this);
        if (player == null) {
            return;
        }
        ItemStack stack = player.getMainHandItem();
        IGun gun = IGun.getIGunOrNull(stack);
        if (gun == null) {
            return;
        }
        int before = gun.getCurrentAmmoCount(stack);
        if (!cbtc$performInstantReload(player, gun, stack)) {
            // 动力臂里已经没有匹配的弹药
            cbtc$setStatus(SentryStatus.NO_AMMO);
            return;
        }
        if (gun.getCurrentAmmoCount(stack) <= before) {
            // 弹匣本来就是满的：什么都不做，也不动射击冷却
            return;
        }
        // 换弹耗时跟随武器自身的空弹换弹时间（供弹时间 + 收尾时间）
        this.cbtc$reloadUntil = level.getGameTime()
                + Math.max(1L, (long) Math.ceil(cbtc$reloadTicks(gun, stack)));
        // 原版每 tick 都会用动力臂本体的 heldItem / attachedAmmoBoxes 覆盖假玩家，
        // 所以换弹结果必须写回本体，否则下一次同步会把刚补满的弹匣又冲掉。
        this.heldItem = stack.copy();
        for (int i = 0; i < this.attachedAmmoBoxes.size(); i++) {
            this.attachedAmmoBoxes.set(i, player.getInventory().getItem(9 + i).copy());
        }
        cbtc$setChanged();
        cbtc$sendData();
        cbtc$setStatus(SentryStatus.RELOADING);
        cbtc$sendActionPacket(ActionType.RELOAD_EMPTY);
    }

    /** 武器数据里的空弹换弹时长（秒 → 刻）。没有换弹数据时返回 0。 */
    @Unique
    private static float cbtc$reloadTicks(IGun gun, ItemStack stack) {
        double seconds = 0;
        var gunIndex = TimelessAPI.getCommonGunIndex(gun.getGunId(stack));
        if (gunIndex.isPresent()) {
            var reload = gunIndex.get().getGunData().getReloadData();
            if (reload != null) {
                seconds = reload.getFeed().getEmptyTime() + reload.getCooldown().getEmptyTime();
            }
        }
        double ticks = Double.isFinite(seconds) ? seconds * 20.0D : 0.0D;
        return (float) Math.ceil(Math.max(0.0D, Math.min(72000.0D, ticks)));
    }

    @Override
    public int cbtc$reserveAmmo() {
        ItemStack held = getHeldItem();
        IGun gun = held.isEmpty() ? null : IGun.getIGunOrNull(held);
        if (gun == null) {
            return 0;
        }
        var gunIndex = TimelessAPI.getCommonGunIndex(gun.getGunId(held));
        if (gunIndex.isEmpty()) {
            return 0;
        }
        ResourceLocation ammoId = gunIndex.get().getGunData().getAmmoId();
        int total = 0;
        for (ItemStack box : this.attachedAmmoBoxes) {
            if (box.isEmpty() || !(box.getItem() instanceof IAmmoBox ammoBox)) {
                continue;
            }
            // 创造弹药箱按"无限"处理，给一个足够大的数便于条件判断
            if (ammoBox.isAllTypeCreative(box)) {
                return 9999;
            }
            if (ammoId == null || !ammoId.equals(ammoBox.getAmmoId(box))) {
                continue;
            }
            if (ammoBox.isCreative(box)) {
                return 9999;
            }
            total += Math.max(0, ammoBox.getAmmoCount(box));
        }
        return total;
    }

    @Override
    public int cbtc$magazineAmmo() {
        ItemStack held = getHeldItem();
        IGun gun = held.isEmpty() ? null : IGun.getIGunOrNull(held);
        return gun == null ? 0 : Math.max(0, gun.getCurrentAmmoCount(held));
    }

    /** 用与原版相同的几何解算把枪口指向目标点。 */
    @Unique
    private void cbtc$applyAim(Vec3 target) {
        Vec3 local = worldToSubLevel(target).subtract(getLocalGunPose().position());
        Vec2 angles = SentryGunGeometry.aim(local, isCeiling(),
                lowerArmAngle.getValue(), upperArmAngle.getValue(), headAngle.getValue(),
                SentryGunProfiles.get(getHeldItem()));
        cbtc$aimAtJoints(angles.x, angles.y);
    }

    /**
     * 接管期间跳过原版的自动索敌/瞄准/开火。
     * <p>
     * 只拦这一个私有方法，转速、弹药补充、假玩家同步等外围逻辑仍按原版运行；
     * 客户端在这里按同步过来的瞄准点自行推算角度，保证转动动画平滑。
     */
    @Inject(method = "sentryLogic", at = @At("HEAD"), cancellable = true, remap = false)
    private void cbtc$skipAutoSentry(CallbackInfo ci) {
        if (!this.cbtc$controlled) {
            return;
        }
        Level level = cbtc$level();
        if (level != null && level.isClientSide && this.cbtc$aim != null) {
            cbtc$applyAim(this.cbtc$aim);
        }
        ci.cancel();
    }

    /**
     * 这些方法继承自 Minecraft/Create 的父类，Mixin 的 @Shadow 只查目标类自身，
     * 所以统一用父类转型访问，避免启动期找不到目标。
     */
    @Unique
    private Level cbtc$level() {
        return ((net.minecraft.world.level.block.entity.BlockEntity) (Object) this).getLevel();
    }

    @Unique
    private void cbtc$setChanged() {
        ((net.minecraft.world.level.block.entity.BlockEntity) (Object) this).setChanged();
    }

    @Unique
    private void cbtc$sendData() {
        ((com.simibubi.create.foundation.blockEntity.SyncedBlockEntity) (Object) this).sendData();
    }

    @Inject(method = "write(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/core/HolderLookup$Provider;Z)V",
            at = @At("TAIL"), remap = false)
    private void cbtc$writeControl(CompoundTag tag, HolderLookup.Provider provider, boolean clientPacket,
                                   CallbackInfo ci) {
        if (this.cbtc$controlled) {
            tag.putBoolean("CBTCControlled", true);
        } else {
            tag.remove("CBTCControlled");
        }
        if (this.cbtc$aim != null) {
            CompoundTag aim = new CompoundTag();
            aim.putDouble("X", this.cbtc$aim.x);
            aim.putDouble("Y", this.cbtc$aim.y);
            aim.putDouble("Z", this.cbtc$aim.z);
            tag.put("CBTCSentryAim", aim);
        } else {
            tag.remove("CBTCSentryAim");
        }
    }

    @Inject(method = "read(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/core/HolderLookup$Provider;Z)V",
            at = @At("TAIL"), remap = false)
    private void cbtc$readControl(CompoundTag tag, HolderLookup.Provider provider, boolean clientPacket,
                                  CallbackInfo ci) {
        this.cbtc$controlled = tag.getBoolean("CBTCControlled");
        if (tag.contains("CBTCSentryAim")) {
            CompoundTag aim = tag.getCompound("CBTCSentryAim");
            this.cbtc$aim = new Vec3(aim.getDouble("X"), aim.getDouble("Y"), aim.getDouble("Z"));
        } else {
            this.cbtc$aim = null;
        }
    }
}
