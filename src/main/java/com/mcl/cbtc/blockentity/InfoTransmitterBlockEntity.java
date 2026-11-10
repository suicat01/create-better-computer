package com.mcl.cbtc.blockentity;

import com.mcl.cbtc.registry.CbcBlockEntities;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 信息转发器的方块实体：按"行"接收显示链接器送来的内容。
 * <p>
 * 显示链接器会把数据写到输出目标的第几行（{@link DisplayLinkContext} 调用方的
 * {@code acceptText(line, ...)} 第一个参数），这里照着行号原样存进一个文本行表，
 * 积木就能按"第 N 行"读取。一个链接重新发送时会先清掉自己上次写的那些行，
 * 避免数据源从多行变成单行后残留旧内容。
 */
public class InfoTransmitterBlockEntity extends BlockEntity {
    /** 可写入的行数，与 {@code InfoTransmitterDisplayTarget#provideStats} 保持一致。 */
    public static final int MAX_ROWS = 16;
    private static final String TAG_ROWS = "Rows";
    private static final String TAG_LINKS = "Links";

    /** 每一行的内容，null 表示该行没有数据。 */
    private final String[] rows = new String[MAX_ROWS];
    /** 每个显示链接上一次写入的起始行与行数（链接坐标 asLong）。 */
    private final Map<Long, Written> written = new LinkedHashMap<>();

    public InfoTransmitterBlockEntity(BlockPos pos, BlockState state) {
        super(CbcBlockEntities.INFO_TRANSMITTER.get(), pos, state);
    }

    /** 显示链接把数据送进来时调用：从 {@code line} 行（0 起）开始依次写入。 */
    public void receive(int line, java.util.List<MutableComponent> text, DisplayLinkContext context) {
        int start = Math.max(0, line);
        long linkPos = context.blockEntity().getBlockPos().asLong();
        Written previous = written.get(linkPos);
        if (previous != null) {
            clear(previous.start(), previous.count());
        }
        if (text != null) {
            for (int i = 0; i < text.size() && start + i < MAX_ROWS; i++) {
                rows[start + i] = text.get(i).getString();
            }
        }
        written.put(linkPos, new Written(start, Math.min(text == null ? 0 : text.size(), MAX_ROWS - start)));
        setChanged();
    }

    /**
     * 取第 {@code line} 行（从 1 起，和显示链接配置界面上的"第 N 行"一致）的内容。
     * 行号越界或该行没有数据时返回空文本。
     */
    public String lineText(int line) {
        if (line < 1 || line > MAX_ROWS) {
            return "";
        }
        String value = rows[line - 1];
        return value == null ? "" : value;
    }

    private void clear(int start, int count) {
        for (int i = 0; i < count && start + i < MAX_ROWS; i++) {
            rows[start + i] = null;
        }
    }

    private record Written(int start, int count) {
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ListTag rowList = new ListTag();
        for (String row : rows) {
            rowList.add(net.minecraft.nbt.StringTag.valueOf(row == null ? "" : row));
        }
        tag.put(TAG_ROWS, rowList);

        ListTag linkList = new ListTag();
        for (Map.Entry<Long, Written> entry : written.entrySet()) {
            CompoundTag item = new CompoundTag();
            item.putLong("Pos", entry.getKey());
            item.putInt("Start", entry.getValue().start());
            item.putInt("Count", entry.getValue().count());
            linkList.add(item);
        }
        tag.put(TAG_LINKS, linkList);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        java.util.Arrays.fill(rows, null);
        ListTag rowList = tag.getList(TAG_ROWS, Tag.TAG_STRING);
        for (int i = 0; i < rowList.size() && i < MAX_ROWS; i++) {
            String value = rowList.getString(i);
            rows[i] = value.isEmpty() ? null : value;
        }
        written.clear();
        ListTag linkList = tag.getList(TAG_LINKS, Tag.TAG_COMPOUND);
        for (int i = 0; i < linkList.size(); i++) {
            CompoundTag item = linkList.getCompound(i);
            written.put(item.getLong("Pos"), new Written(item.getInt("Start"), item.getInt("Count")));
        }
    }
}
