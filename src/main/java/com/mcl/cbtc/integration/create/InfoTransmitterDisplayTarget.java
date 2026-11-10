package com.mcl.cbtc.integration.create;

import com.mcl.cbtc.blockentity.InfoTransmitterBlockEntity;
import com.simibubi.create.api.behaviour.display.DisplayTarget;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import java.util.List;
import net.minecraft.network.chat.MutableComponent;

/**
 * 把信息转发器注册成 Create 显示链接的输出目标。
 * <p>
 * 显示链接器（Display Link）会像对待显示板一样把数据源产出的文本送进
 * {@link #acceptText}，这里原样转交给信息转发器方块实体暂存。
 * 因此只要附属模组通过官方 DisplaySource 接入，信息转发器就能读到。
 */
public class InfoTransmitterDisplayTarget extends DisplayTarget {
    @Override
    public void acceptText(int line, List<MutableComponent> text, DisplayLinkContext context) {
        if (context.getTargetBlockEntity() instanceof InfoTransmitterBlockEntity transmitter) {
            // line 是显示链接配置里选的"写到第几行"，从 0 起
            transmitter.receive(line, text, context);
        }
    }

    @Override
    public DisplayTargetStats provideStats(DisplayLinkContext context) {
        // 行数决定显示链接界面里"第 N 行"的可选范围，与方块实体的行表一致
        return new DisplayTargetStats(InfoTransmitterBlockEntity.MAX_ROWS, 64, this);
    }
}
