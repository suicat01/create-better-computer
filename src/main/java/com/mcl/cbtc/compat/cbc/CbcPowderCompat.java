package com.mcl.cbtc.compat.cbc;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import rbasamoyai.createbigcannons.cannon_control.cannon_mount.CannonMountBlockEntity;
import rbasamoyai.createbigcannons.cannon_control.cannon_mount.ExtendsCannonMount;
import rbasamoyai.createbigcannons.cannon_control.contraption.MountedBigCannonContraption;
import rbasamoyai.createbigcannons.cannon_control.contraption.PitchOrientedContraptionEntity;
import rbasamoyai.createbigcannons.cannon_control.fixed_cannon_mount.FixedCannonMountBlockEntity;
import rbasamoyai.createbigcannons.cannons.big_cannons.BigCannonBlock;
import rbasamoyai.createbigcannons.cannons.big_cannons.IBigCannonBlockEntity;
import rbasamoyai.createbigcannons.cannons.big_cannons.breeches.quickfiring_breech.QuickfiringBreechBlockEntity;
import rbasamoyai.createbigcannons.munitions.big_cannon.BigCannonMunitionBlock;
import rbasamoyai.createbigcannons.munitions.big_cannon.ProjectileBlock;

/**
 * 发射药包（Powder Charge）的自动装填。
 * <p>
 * CBC 的快发炮闩交互点只认「弹头」和「药筒」两种方块，发射药包会被原样退回；
 * 这里自己把炮膛里已装的一串（弹头 + 已有发射药）朝炮口推一格，再把新发射药放进炮座第一格，
 * 因此能一直装到炮管长度允许的上限，而不是像 {@code loadCartridge} 那样只支持一发。
 * <p>
 * 本类只会在安装 CBC 时被加载。
 */
public final class CbcPowderCompat {
    private CbcPowderCompat() {
    }

    /** 当前炮膛状态是否还能塞下一个发射药包（膛里得有弹头，且整条链还能向炮口挪一格）。 */
    public static boolean canLoad(Level level, BlockPos mountPos, ItemStack stack) {
        Target target = target(level, mountPos);
        if (target == null) {
            return false;
        }
        List<BlockPos> chain = chain(target.cannon);
        return !chain.isEmpty() && chainHasProjectile(target.cannon, chain) && hasRoom(target.cannon, chain);
    }

    /** 装填一个发射药包；返回未消耗的余量（装不进去时原样返回）。 */
    public static ItemStack load(Level level, BlockPos mountPos, ItemStack stack) {
        Target target = target(level, mountPos);
        if (target == null) {
            return stack;
        }
        if (!(stack.getItem() instanceof BlockItem blockItem)
                || !(blockItem.getBlock() instanceof BigCannonMunitionBlock munition)) {
            return stack;
        }
        MountedBigCannonContraption cannon = target.cannon;
        Direction pushDirection = cannon.initialOrientation();
        BlockPos start = cannon.getStartPos();
        List<BlockPos> chain = chain(cannon);
        if (chain.isEmpty() || !chainHasProjectile(cannon, chain) || !hasRoom(cannon, chain)) {
            return stack;
        }
        if (!(cannon.presentBlockEntities.get(start) instanceof IBigCannonBlockEntity startCbe)) {
            return stack;
        }
        StructureBlockInfo newInfo = munition.getHandloadingInfo(
                stack, start, pushDirection, level.registryAccess());
        // 先确认这个发射药对这门炮是合法的，再挪动整条链，避免挪完发现装不进去
        if (!IBigCannonBlockEntity.isValidMunitionState(pushDirection.getAxis(), newInfo)) {
            return stack;
        }
        // 整条链（弹头 + 已有的药）从炮口端开始逐格前移
        Set<BlockPos> changes = new HashSet<>();
        for (int i = chain.size() - 1; i >= 0; i--) {
            BlockPos from = chain.get(i);
            BlockPos to = from.relative(pushDirection);
            if (!(cannon.presentBlockEntities.get(from) instanceof IBigCannonBlockEntity fromCbe)
                    || !(cannon.presentBlockEntities.get(to) instanceof IBigCannonBlockEntity toCbe)) {
                return stack;
            }
            toCbe.cannonBehavior().loadBlock(fromCbe.cannonBehavior().block());
            fromCbe.cannonBehavior().removeBlock();
            changes.add(from);
            changes.add(to);
        }
        startCbe.cannonBehavior().tryLoadingBlock(newInfo);
        changes.add(start);
        BigCannonBlock.writeAndSyncMultipleBlockData(changes, target.entity, cannon);
        // 整轮装填是一次性完成的，不沿用 CBC 手装时 40 刻的装填冷却
        target.breech.setLoadingCooldown(0);
        ItemStack remainder = stack.copy();
        remainder.shrink(1);
        return remainder;
    }

    /** 从炮座第一格向炮口方向收集已经装进去的物品位置。 */
    private static List<BlockPos> chain(MountedBigCannonContraption cannon) {
        List<BlockPos> chain = new ArrayList<>();
        Direction direction = cannon.initialOrientation();
        BlockPos pos = cannon.getStartPos();
        while (cannon.presentBlockEntities.get(pos) instanceof IBigCannonBlockEntity cbe) {
            if (cbe.cannonBehavior().block().state().isAir()) {
                break;
            }
            chain.add(pos);
            pos = pos.relative(direction);
        }
        return chain;
    }

    /** 膛里是否已经有弹头（发射药只能装在弹头后面）。 */
    private static boolean chainHasProjectile(MountedBigCannonContraption cannon, List<BlockPos> chain) {
        for (BlockPos pos : chain) {
            if (cannon.presentBlockEntities.get(pos) instanceof IBigCannonBlockEntity cbe
                    && cbe.cannonBehavior().block().state().getBlock() instanceof ProjectileBlock) {
                return true;
            }
        }
        return false;
    }

    /** 整条链前方是否还有一格空的炮管。 */
    private static boolean hasRoom(MountedBigCannonContraption cannon, List<BlockPos> chain) {
        BlockPos last = chain.isEmpty() ? cannon.getStartPos() : chain.get(chain.size() - 1);
        BlockPos next = last.relative(cannon.initialOrientation());
        return cannon.presentBlockEntities.get(next) instanceof IBigCannonBlockEntity cbe
                && cbe.cannonBehavior().block().state().isAir();
    }

    /** 炮膛里已经装了几个这种物品（沿整条装填链计数）。 */
    public static int countLoaded(Level level, BlockPos mountPos, net.minecraft.world.item.Item item) {
        MountedBigCannonContraption cannon = cannonAt(level, mountPos);
        if (cannon == null) {
            return 0;
        }
        net.minecraft.world.level.block.Block block = net.minecraft.world.level.block.Block.byItem(item);
        int count = 0;
        for (BlockPos pos : chain(cannon)) {
            if (cannon.presentBlockEntities.get(pos) instanceof IBigCannonBlockEntity cbe
                    && cbe.cannonBehavior().block().state().getBlock() == block) {
                count++;
            }
        }
        return count;
    }

    /** 清掉 CBC 装填后打上的冷却，让同一轮里的下一件物品立刻能进膛。 */
    public static void clearLoadingCooldown(Level level, BlockPos mountPos) {
        QuickfiringBreechBlockEntity breech = breechAt(level, mountPos);
        if (breech != null) {
            breech.setLoadingCooldown(0);
        }
    }

    /** 炮膛第一格是否为空（已经打出去了）。 */
    public static boolean barrelEmpty(Level level, BlockPos mountPos) {
        MountedBigCannonContraption cannon = cannonAt(level, mountPos);
        if (cannon == null) {
            return true;
        }
        if (!(cannon.presentBlockEntities.get(cannon.getStartPos()) instanceof IBigCannonBlockEntity startEntity)) {
            return true;
        }
        return startEntity.cannonBehavior().block().state().isAir();
    }

    /** 炮膛第一格是否是弹头（发射药就等它后面装）。 */
    public static boolean firstBlockIsProjectile(Level level, BlockPos mountPos) {
        MountedBigCannonContraption cannon = cannonAt(level, mountPos);
        if (cannon == null) {
            return false;
        }
        if (!(cannon.presentBlockEntities.get(cannon.getStartPos()) instanceof IBigCannonBlockEntity startEntity)) {
            return false;
        }
        return startEntity.cannonBehavior().block().state().getBlock() instanceof ProjectileBlock;
    }

    /** 切换速射炮闩的开合状态（用于装填时的开/关动画）。 */
    public static void toggleBreech(Level level, BlockPos mountPos) {
        QuickfiringBreechBlockEntity breech = breechAt(level, mountPos);
        if (breech != null) {
            breech.toggleOpening();
        }
    }

    /** 炮闩开合动画的时长（刻）。 */
    public static int breechOpeningTime() {
        return QuickfiringBreechBlockEntity.getOpeningTime();
    }

    /** 炮闩当前张开进度：0 = 完全关闭，openingTime = 完全打开。 */
    public static int breechOpenProgress(Level level, BlockPos mountPos) {
        QuickfiringBreechBlockEntity breech = breechAt(level, mountPos);
        return breech == null ? 0 : breech.getOpenProgress();
    }

    /** 炮闩当前开合方向：1 打开中，-1 关闭中，0 静止。 */
    public static int breechOpenDirection(Level level, BlockPos mountPos) {
        QuickfiringBreechBlockEntity breech = breechAt(level, mountPos);
        return breech == null ? 0 : breech.getOpenDirection();
    }

    private static QuickfiringBreechBlockEntity breechAt(Level level, BlockPos mountPos) {
        MountedBigCannonContraption cannon = cannonAt(level, mountPos);
        if (cannon == null) {
            return null;
        }
        BlockPos breechPos = cannon.getStartPos().relative(cannon.initialOrientation().getOpposite());
        return cannon.presentBlockEntities.get(breechPos) instanceof QuickfiringBreechBlockEntity breech
                ? breech : null;
    }

    private static MountedBigCannonContraption cannonAt(Level level, BlockPos mountPos) {
        BlockEntity blockEntity = level.getBlockEntity(mountPos);
        PitchOrientedContraptionEntity entity = null;
        if (blockEntity instanceof ExtendsCannonMount mount) {
            CannonMountBlockEntity cannonMount = mount.getCannonMount();
            if (cannonMount != null) {
                entity = cannonMount.getContraption();
            }
        } else if (blockEntity instanceof FixedCannonMountBlockEntity fixedMount) {
            entity = fixedMount.getContraption();
        }
        return entity != null && entity.getContraption() instanceof MountedBigCannonContraption cannon
                ? cannon : null;
    }

    private static Target target(Level level, BlockPos mountPos) {
        BlockEntity blockEntity = level.getBlockEntity(mountPos);
        PitchOrientedContraptionEntity entity = null;
        if (blockEntity instanceof ExtendsCannonMount mount) {
            CannonMountBlockEntity cannonMount = mount.getCannonMount();
            if (cannonMount != null) {
                entity = cannonMount.getContraption();
            }
        } else if (blockEntity instanceof FixedCannonMountBlockEntity fixedMount) {
            entity = fixedMount.getContraption();
        }
        if (entity == null || !(entity.getContraption() instanceof MountedBigCannonContraption cannon)) {
            return null;
        }
        Direction dir = cannon.initialOrientation();
        BlockPos start = cannon.getStartPos();
        BlockPos breechPos = start.relative(dir.getOpposite());
        if (!(cannon.presentBlockEntities.get(breechPos) instanceof QuickfiringBreechBlockEntity breech)) {
            return null;
        }
        // 这里刻意不看 CBC 的装填冷却与炮闩开度：中士是一整轮一次性装填，
        // 冷却会把下一件物品卡住 2 秒，炮闩开度则取决于我们自己的动画
        if (!(cannon.presentBlockEntities.get(start) instanceof IBigCannonBlockEntity)) {
            return null;
        }
        return new Target(cannon, entity, breech);
    }

    private record Target(MountedBigCannonContraption cannon, PitchOrientedContraptionEntity entity,
                          QuickfiringBreechBlockEntity breech) {
    }
}
