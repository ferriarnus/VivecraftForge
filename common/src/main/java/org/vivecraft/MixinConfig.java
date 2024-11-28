package org.vivecraft;

import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;
import org.vivecraft.client.Xplat;
import org.vivecraft.client_vr.extensions.ClassDependentMixin;
import org.vivecraft.mod_compat_vr.sodium.SodiumHelper;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class MixinConfig implements IMixinConfigPlugin {

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    @Override
    public void onLoad(String mixinPackage) {}

    private static final Set<String> APPLIED_MOD_FIXES = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private static final Logger LOGGER = LoggerFactory.getLogger("VivecraftMixin");

    private static final String CLASS_DEPENDENT_MIXIN = "L" + ClassDependentMixin.class.getName().replace(".", "/") + ";";

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        // this is here because forge doesn't finish mod loading, if any mod fails to load, and would crash the game
        if (!Xplat.isModLoadedSuccess()) {
            LOGGER.info("Vivecraft: not loading '{}' because mod failed to load completely", mixinClassName);
            return false;
        }

        // only try to apply mod mixins if the target class was found
        if (mixinClassName.startsWith("org.vivecraft.mod_compat_vr")) {
            try {
                MixinService.getService().getBytecodeProvider().getClassNode(targetClassName);
            } catch (ClassNotFoundException | IOException e) {
                return false;
            }
            String mod = mixinClassName.split("\\.")[3];
            if (APPLIED_MOD_FIXES.add(mod)) {
                LOGGER.info("Vivecraft: applying '{}' fixes", mod);
            }
        }

        // some mixins need a specific classes to be present to not throw errors on applying
        try {
            ClassNode mixinClass = MixinService.getService().getBytecodeProvider().getClassNode(mixinClassName);
            if (mixinClass.visibleAnnotations != null) {
                for (AnnotationNode annotation : mixinClass.visibleAnnotations) {
                    if (annotation.desc.equals(CLASS_DEPENDENT_MIXIN)) {
                        String neededClass = (String) annotation.values.get(1);
                        MixinService.getService().getBytecodeProvider().getClassNode(neededClass);
                    }
                }
                return true;
            }
        } catch (ClassNotFoundException | IOException e) {
            LOGGER.info("Vivecraft: skipping mixin '{}'", mixinClassName);
            return false;
        }

        return !mixinClassName.contains("NoSodium") || !SodiumHelper.isLoaded();
    }
}
