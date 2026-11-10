package com.mcl.cbtc.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.sounds.SoundEvent;

/**
 * 客户端音效候选表：把游戏内全部声音事件整理成"中文名 + 原名"的列表，供积木选择与搜索。
 * <p>
 * 中文名来自声音定义里的 subtitle 键（sounds.json 的 subtitle 字段），
 * 不能简单用 id 拼 subtitles.*，因为很多事件（如 block.note_block.harp）共用同一个字幕键。
 */
public final class SoundCatalog {
    /** 一条候选：资源 id、显示名（可能为空）、以及用于搜索的小写全文。 */
    public record Entry(ResourceLocation id, String display, String searchText) {
    }

    private static List<Entry> cache;

    private SoundCatalog() {
    }

    /** 全部声音事件，按 id 排序。首次调用后缓存。 */
    public static List<Entry> all() {
        if (cache != null) {
            return cache;
        }
        List<Entry> list = new ArrayList<>();
        var mc = Minecraft.getInstance();
        if (mc.level == null) {
            return list;
        }
        Map<ResourceLocation, String> subtitles = loadSubtitles();
        var registry = mc.level.registryAccess().registryOrThrow(Registries.SOUND_EVENT);
        for (SoundEvent event : registry) {
            ResourceLocation id = event.getLocation();
            // 先从 sounds.json 拿到该事件自己的字幕键，再翻译成中文
            String subtitleKey = event.getLocation() == null ? null : subtitles.get(id);
            String display = translateOrEmpty(subtitleKey);
            String search = (id.toString() + " " + display).toLowerCase();
            list.add(new Entry(id, display, search));
        }
        list.sort((a, b) -> a.id().toString().compareTo(b.id().toString()));
        cache = list;
        return list;
    }

    /** 读所有命名空间的 sounds.json，建立 声音事件 id → subtitle 键 的映射。 */
    private static Map<ResourceLocation, String> loadSubtitles() {
        Map<ResourceLocation, String> map = new HashMap<>();
        var mc = Minecraft.getInstance();
        var rm = mc.getResourceManager();
        // sounds.json 的路径固定是 <命名空间>/sounds.json
        Map<ResourceLocation, Resource> files = rm.listResources("sounds", p -> p.getPath().equals("sounds.json"));
        for (var entry : files.entrySet()) {
            String namespace = entry.getKey().getNamespace();
            try (var in = entry.getValue().open()) {
                JsonObject root = JsonParser.parseReader(
                        new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                for (var e : root.entrySet()) {
                    if (!e.getValue().isJsonObject()) {
                        continue;
                    }
                    JsonObject def = e.getValue().getAsJsonObject();
                    JsonElement sub = def.get("subtitle");
                    if (sub == null || !sub.isJsonPrimitive()) {
                        continue;
                    }
                    map.put(ResourceLocation.fromNamespaceAndPath(namespace, e.getKey()), sub.getAsString());
                }
            } catch (Exception ignored) {
                // 单个 sounds.json 解析失败不影响其它命名空间
            }
        }
        return map;
    }

    /** 翻译一个键；键不存在或没有翻译时返回空串。 */
    private static String translateOrEmpty(String key) {
        if (key == null || key.isBlank()) {
            return "";
        }
        Component translated = Component.translatable(key);
        String text = translated.getString();
        return text.equals(key) ? "" : text;
    }

    /** 按关键字过滤；空关键字返回全部。支持英文 id、中文名与分段匹配。 */
    public static List<Entry> search(String query, int limit) {
        String q = query == null ? "" : query.trim().toLowerCase();
        List<Entry> result = new ArrayList<>();
        for (Entry e : all()) {
            if (matches(e, q)) {
                result.add(e);
                if (result.size() >= limit) {
                    break;
                }
            }
        }
        return result;
    }

    /** 完全包含优先；否则要求查询里的每个字符都按顺序出现在搜索文本中。 */
    private static boolean matches(Entry e, String q) {
        if (q.isEmpty() || e.searchText().contains(q)) {
            return true;
        }
        int cursor = 0;
        for (char c : q.toCharArray()) {
            int found = e.searchText().indexOf(c, cursor);
            if (found < 0) {
                return false;
            }
            cursor = found + 1;
        }
        return true;
    }

    /** 在客户端试听一条音效。 */
    public static void preview(ResourceLocation id) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || id == null) {
            return;
        }
        mc.level.playLocalSound(mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                SoundEvent.createVariableRangeEvent(id), net.minecraft.sounds.SoundSource.MASTER,
                1.0F, 1.0F, false);
    }
}
