package com.fontainerepublic;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

@Mod(FontaineRepublic.MOD_ID)
public class FontaineRepublic {
    public static final String MOD_ID = "fontainerepublic";
    private static final Logger LOGGER = LogUtils.getLogger();

    public FontaineRepublic() {
        LOGGER.info("[FontaineRepublic] Loading");
    }
}
