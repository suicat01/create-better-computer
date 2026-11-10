package com.mcl.cbtc.integration.create;

import com.mcl.cbtc.blockentity.CBComputerBlockEntity;
import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * CB电脑 作为机械动力"显示链接"的数据源：显示链接对着 CB电脑 时，读取它的屏幕输出行。
 * <p>
 * 与 Create 自带的"计算机"数据源行为一致，显示链接每 10 tick 拉取一次。
 */
public class ComputerDisplaySource extends DisplaySource {
    @Override
    public List<MutableComponent> provideText(DisplayLinkContext context, DisplayTargetStats stats) {
        List<MutableComponent> lines = new ArrayList<>();
        if (context.getSourceBlockEntity() instanceof CBComputerBlockEntity computer) {
            for (String line : computer.displayLineList()) {
                lines.add(Component.literal(line));
            }
        }
        return lines;
    }

    @Override
    public boolean shouldPassiveReset() {
        return false;
    }

    @Override
    public int getPassiveRefreshTicks() {
        return 10;
    }
}
