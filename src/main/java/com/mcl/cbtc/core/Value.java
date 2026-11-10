package com.mcl.cbtc.core;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

/**
 * 积木程序里流动的值。当前有数值、布尔、文本、数列四种，都统一收敛成 double / boolean / String 便于比较。
 */
public sealed interface Value permits Value.Num, Value.Bool, Value.Str, Value.Seq, Value.Time {
    Value ZERO = new Num(0);

    static Value of(double v) {
        return new Num(v);
    }

    static Value of(boolean v) {
        return new Bool(v);
    }

    static Value of(String v) {
        return new Str(v);
    }

    static Value of(Time v) {
        return v;
    }

    double asNumber();

    boolean asBool();

    String asText();

    void save(CompoundTag tag);

    static Value load(CompoundTag tag) {
        return switch (tag.getByte("T")) {
            case 1 -> new Bool(tag.getBoolean("V"));
            case 2 -> new Str(tag.getString("V"));
            case 3 -> new Seq(readSeq(tag));
            case 4 -> new Time(tag.getLong("G"), tag.getLong("R"));
            default -> new Num(tag.getDouble("V"));
        };
    }

    /** 从 NBT 里读回数列。 */
    private static java.util.List<Item> readSeq(CompoundTag tag) {
        net.minecraft.nbt.ListTag list = tag.getList("V", Tag.TAG_COMPOUND);
        java.util.List<Item> items = new java.util.ArrayList<>(list.size());
        for (Tag item : list) {
            items.add(Item.load((CompoundTag) item));
        }
        return items;
    }

    record Num(double v, boolean percent) implements Value {
        public Num(double v) {
            this(v, false);
        }

        @Override
        public double asNumber() {
            return v;
        }

        @Override
        public boolean asBool() {
            return v != 0;
        }

        @Override
        public String asText() {
            double shown = percent ? v * 100 : v;
            return (shown == Math.rint(shown) && !Double.isInfinite(shown)
                    ? Long.toString((long) shown) : Double.toString(shown)) + (percent ? "%" : "");
        }

        @Override
        public void save(CompoundTag tag) {
            tag.putByte("T", (byte) 0);
            tag.putDouble("V", v);
        }
    }

    record Bool(boolean v) implements Value {
        @Override
        public double asNumber() {
            return v ? 1 : 0;
        }

        @Override
        public boolean asBool() {
            return v;
        }

        @Override
        public String asText() {
            return Boolean.toString(v);
        }

        @Override
        public void save(CompoundTag tag) {
            tag.putByte("T", (byte) 1);
            tag.putBoolean("V", v);
        }
    }

    record Str(String v) implements Value {
        @Override
        public double asNumber() {
            try {
                return Double.parseDouble(v.trim());
            } catch (NumberFormatException e) {
                return 0;
            }
        }

        @Override
        public boolean asBool() {
            return !v.isEmpty() && !"false".equalsIgnoreCase(v) && !"0".equals(v);
        }

        @Override
        public String asText() {
            return v;
        }

        @Override
        public void save(CompoundTag tag) {
            tag.putByte("T", (byte) 2);
            tag.putString("V", v);
        }
    }

    /**
     * 数列：一串项，显示格式为"数，数，数"。
     * <p>
     * 每一项要么是常量数字，要么是对某个变量的引用（硬盘 / 局域网 / 互联网）。
     * 引用在显示与统计时才去取变量当前的值，所以变量变了数列里那一项也跟着变；
     * 想存固定数值，就把变量套进数字积木后再写入。
     */
    record Seq(java.util.List<Item> items) implements Value {
        @Override
        public double asNumber() {
            return 0;
        }

        @Override
        public boolean asBool() {
            return !items.isEmpty();
        }

        /** 只保留常量项的形式，用于不支持引用解析的场合（如直接拼文本）。 */
        @Override
        public String asText() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < items.size(); i++) {
                if (i > 0) {
                    sb.append('，');
                }
                sb.append(formatNumber(items.get(i).literal()));
            }
            return sb.toString();
        }

        @Override
        public void save(CompoundTag tag) {
            tag.putByte("T", (byte) 3);
            net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
            for (Item item : items) {
                CompoundTag entry = new CompoundTag();
                item.save(entry);
                list.add(entry);
            }
            tag.put("V", list);
        }
    }

    /**
     * 时间点：同时带游戏时间与现实时间两个分量，各自是"单位换算后的绝对量"。
     * <p>
     * 游戏时间以游戏刻为单位（1 游戏日 = 24000 刻，1 游戏时 = 1000 刻，1 游戏分 = 20 刻），
     * 现实时间以秒为单位。两个分量互不相干，只有当运算/比较双方都只用其中一个分量时才成立；
     * 相差一个分量时按"时间差"处理，直接输出差值。
     */
    record Time(long gameTicks, long realSeconds) implements Value {
        /** 是否是时间差（任一分量为负或以差值形式存在）。 */
        @Override
        public double asNumber() {
            // 时间接进数值槽时，取"游戏刻"作为主单位
            return gameTicks;
        }

        @Override
        public boolean asBool() {
            return gameTicks != 0 || realSeconds != 0;
        }

        @Override
        public String asText() {
            StringBuilder sb = new StringBuilder();
            if (gameTicks != 0 || realSeconds == 0) {
                sb.append(formatNumber(gameTicks)).append("游戏刻");
            }
            if (realSeconds != 0) {
                if (sb.length() > 0) {
                    sb.append(' ');
                }
                sb.append(formatNumber(realSeconds)).append("现实秒");
            }
            return sb.toString();
        }

        @Override
        public void save(CompoundTag tag) {
            tag.putByte("T", (byte) 4);
            tag.putLong("G", gameTicks);
            tag.putLong("R", realSeconds);
        }
    }

    /** 数列里的一项：常量数字，或对某个变量的引用。 */
    record Item(double literal, @Nullable String scope, @Nullable String network,
                @Nullable String name) {
        /** 常量项。 */
        public static Item constant(double value) {
            return new Item(value, null, null, null);
        }

        /** 变量引用项。scope 取 drive / lan / net。 */
        public static Item reference(String scope, @Nullable String network, String name) {
            return new Item(0, scope, network == null ? "" : network, name);
        }

        /** 是否是对变量的引用。 */
        public boolean isReference() {
            return scope != null && name != null && !name.isEmpty();
        }

        void save(CompoundTag tag) {
            tag.putDouble("L", literal);
            if (isReference()) {
                tag.putString("S", scope);
                tag.putString("N", network == null ? "" : network);
                tag.putString("M", name);
            }
        }

        static Item load(CompoundTag tag) {
            if (!tag.contains("S", Tag.TAG_STRING)) {
                return constant(tag.getDouble("L"));
            }
            return reference(tag.getString("S"), tag.getString("N"), tag.getString("M"));
        }
    }

    /** 数字转显示文本：整数不带小数点。 */
    static String formatNumber(double v) {
        return v == Math.rint(v) && !Double.isInfinite(v) ? Long.toString((long) v) : Double.toString(v);
    }

    static Value readOptional(CompoundTag tag, String key) {
        return tag.contains(key, Tag.TAG_COMPOUND) ? load(tag.getCompound(key)) : ZERO;
    }
}
