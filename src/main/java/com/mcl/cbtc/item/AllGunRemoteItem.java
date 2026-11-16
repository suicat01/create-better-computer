package com.mcl.cbtc.item;

/**
 * 全能枪式遥控器：建模与贴图沿用枪式遥控器，但进入控制模式后，
 * 除了鼠标左右键，还能读取鼠标侧键与整个键盘。
 * <p>
 * 绑定、ID 管理与退出逻辑完全沿用 {@link GunRemoteItem}，这里只标记按键范围。
 */
public class AllGunRemoteItem extends GunRemoteItem {
    public AllGunRemoteItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean fullKeyboard() {
        return true;
    }
}
