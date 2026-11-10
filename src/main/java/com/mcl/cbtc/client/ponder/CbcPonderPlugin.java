package com.mcl.cbtc.client.ponder;

import com.mcl.cbtc.CreateBetterComputer;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
import net.minecraft.resources.ResourceLocation;

/** 注册 CB电脑 的思索（Ponder）教程。 */
public class CbcPonderPlugin implements PonderPlugin {
    @Override
    public String getModId() {
        return CreateBetterComputer.MODID;
    }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        CbcPonderScenes.register(helper);
    }

    @Override
    public void registerTags(PonderTagRegistrationHelper<ResourceLocation> helper) {
        CbcPonderScenes.registerTags(helper);
    }
}
