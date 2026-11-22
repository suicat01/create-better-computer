package com.mcl.cbtc.core;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

/**
 * 积木程序里流动的值。数值、布尔、文本、数列、时间与瞄准目标都在这里定义，
 * 前四种统一收敛成 double / boolean / String 便于比较。
 */
public sealed interface Value permits Value.Num, Value.Bool, Value.Str, Value.Seq, Value.Time, Value.Target {
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
            case 4 -> new Time(tag.getLong("G"), tag.getLong("R"), tag.getString("U"));
            case 5 -> {
                net.minecraft.nbt.ListTag clampTag = tag.getList("C", Tag.TAG_DOUBLE);
                double[] clamp = new double[clampTag.size()];
                for (int i = 0; i < clamp.length; i++) {
                    clamp[i] = clampTag.getDouble(i);
                }
                yield new Target(tag.contains("S", Tag.TAG_STRING) ? tag.getString("S") : null,
                        tag.getBoolean("R"), tag.getDouble("Y"), tag.getDouble("P"),
                        tag.getBoolean("F"),
                        clamp.length == 4 ? new Target.Clamp(clamp[0], clamp[1], clamp[2], clamp[3],
                                tag.getBoolean("CR")) : null);
            }
            default -> new Num(tag.getDouble("V"), false, tag.getString("U"));
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

    /**
     * 数值。
     * <p>
     * {@code unit} 是从"带单位的文本"里取出来的单位后缀（RPM、度、% 等），
     * 只用于显示与加减乘除的传播；{@code percent} 为真时 {@code v} 存的是 0~1 的比例，显示时乘 100。
     */
    record Num(double v, boolean percent, String unit) implements Value {
        public Num(double v) {
            this(v, false, "");
        }

        public Num(double v, boolean percent) {
            this(v, percent, "");
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
            String number = shown == Math.rint(shown) && !Double.isInfinite(shown)
                    ? Long.toString((long) shown) : Double.toString(shown);
            String suffix = unit == null ? "" : unit;
            // 英文字母开头的单位（RPM、kFE）补一个空格，中文单位与符号（度、%）直接贴上去
            if (!suffix.isEmpty() && suffix.charAt(0) < 128
                    && Character.isLetterOrDigit(suffix.charAt(0))) {
                suffix = " " + suffix;
            }
            return number + (percent ? "%" : "") + suffix;
        }

        @Override
        public void save(CompoundTag tag) {
            tag.putByte("T", (byte) 0);
            tag.putDouble("V", v);
            if (unit != null && !unit.isEmpty()) {
                tag.putString("U", unit);
            }
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
    record Time(long gameTicks, long realSeconds, String unit) implements Value {
        public Time(long gameTicks, long realSeconds) {
            this(gameTicks, realSeconds, "");
        }
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
            if (unit != null && !unit.isEmpty()) {
                tag.putString("U", unit);
            }
        }
    }

    /**
     * 瞄准目标：既可以是"某块瞄准组件佩戴者的准星处"，也可以是直接用水平/垂直角度指方向。
     * <p>
     * 水平角与 MC 朝向同一套：正南 0°，向西为正，正东 -90°，正北 ±180°；垂直角抬头为正。
     * {@code offset} 为真表示在准星方向的基础上再叠加这两个角度。
     * {@code relative} 为真表示角度以火控单位的初始朝向为 0°（而不是世界方位）。
     * {@code clamp} 不为空时，最终瞄准角会被夹进给定范围，由瞄准时统一处理。
     */
    record Target(@Nullable String sight, boolean offset, double yaw, double pitch,
                  boolean relative, @Nullable Clamp clamp) implements Value {
        /** 瞄准角限制范围（度）：先按参考系换算，再夹取。 */
        public record Clamp(double vMin, double vMax, double hMin, double hMax, boolean relative) {
            Clamp(double vMin, double vMax, double hMin, double hMax) {
                this(vMin, vMax, hMin, hMax, false);
            }
        }

        Target(String sight, boolean offset, double yaw, double pitch) {
            this(sight, offset, yaw, pitch, false, null);
        }

        /** 瞄准组件佩戴者的准星处。 */
        public static Target crosshair(String sight) {
            return new Target(sight, false, 0.0D, 0.0D, false, null);
        }

        /** 直接用水平/垂直角度指方向。 */
        public static Target angles(double yaw, double pitch) {
            return new Target(null, false, yaw, pitch, false, null);
        }

        /** 直接用水平/垂直角度指方向；{@code relative} 表示以火控单位的初始朝向为 0°。 */
        public static Target angles(double yaw, double pitch, boolean relative) {
            return new Target(null, false, yaw, pitch, relative, null);
        }

        /** 准星处再叠加角度偏移。 */
        public static Target crosshairOffset(String sight, double yaw, double pitch) {
            return new Target(sight, true, yaw, pitch, false, null);
        }

        /** 准星处再叠加角度偏移，并指定参考系。 */
        public static Target crosshairOffset(String sight, double yaw, double pitch, boolean relative) {
            return new Target(sight, true, yaw, pitch, relative, null);
        }

        /** 套上一层角度限制（范围与参考系由「限制瞄准角」提供，不改动内层目标的参考系）。 */
        public Target withClamp(Clamp limit) {
            return new Target(sight, offset, yaw, pitch, relative, limit);
        }

        /** 是否以准星处为基准；否则只看角度。 */
        public boolean usesSight() {
            return sight != null && !sight.isBlank();
        }

        @Override
        public double asNumber() {
            return 0;
        }

        @Override
        public boolean asBool() {
            return true;
        }

        @Override
        public String asText() {
            String base;
            if (!usesSight()) {
                base = "水平 " + formatNumber(yaw) + "° 垂直 " + formatNumber(pitch) + "°";
            } else {
                String sightBase = "准星 " + sight;
                base = offset ? sightBase + " 水平 " + formatNumber(yaw) + "° 垂直 " + formatNumber(pitch) + "°"
                        : sightBase;
            }
            return relative ? "相对 " + base : base;
        }

        @Override
        public void save(CompoundTag tag) {
            tag.putByte("T", (byte) 5);
            if (sight != null) {
                tag.putString("S", sight);
            }
            tag.putBoolean("R", offset);
            tag.putDouble("Y", yaw);
            tag.putDouble("P", pitch);
            tag.putBoolean("F", relative);
            if (clamp != null) {
                net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
                list.add(net.minecraft.nbt.DoubleTag.valueOf(clamp.vMin()));
                list.add(net.minecraft.nbt.DoubleTag.valueOf(clamp.vMax()));
                list.add(net.minecraft.nbt.DoubleTag.valueOf(clamp.hMin()));
                list.add(net.minecraft.nbt.DoubleTag.valueOf(clamp.hMax()));
                tag.put("C", list);
                tag.putBoolean("CR", clamp.relative());
            }
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
