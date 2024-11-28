package org.vivecraft.mixin.client_vr;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;
import com.mojang.blaze3d.pipeline.MainTarget;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.*;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.profiling.ProfileResults;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import org.lwjgl.glfw.GLFW;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.vivecraft.client.VRPlayersClient;
import org.vivecraft.client.VivecraftVRMod;
import org.vivecraft.client.gui.VivecraftClickEvent;
import org.vivecraft.client.gui.screens.ErrorScreen;
import org.vivecraft.client.gui.screens.GarbageCollectorScreen;
import org.vivecraft.client.gui.screens.UpdateScreen;
import org.vivecraft.client.network.ClientNetworking;
import org.vivecraft.client.utils.TextUtils;
import org.vivecraft.client.utils.UpdateChecker;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.MethodHolder;
import org.vivecraft.client_vr.VRState;
import org.vivecraft.client_vr.extensions.*;
import org.vivecraft.client_vr.gameplay.screenhandlers.GuiHandler;
import org.vivecraft.client_vr.gameplay.trackers.TelescopeTracker;
import org.vivecraft.client_vr.menuworlds.MenuWorldDownloader;
import org.vivecraft.client_vr.menuworlds.MenuWorldExporter;
import org.vivecraft.client_vr.provider.openvr_lwjgl.VRInputAction;
import org.vivecraft.client_vr.render.RenderConfigException;
import org.vivecraft.client_vr.render.VRFirstPersonArmSwing;
import org.vivecraft.client_vr.render.MirrorNotification;
import org.vivecraft.client_vr.render.helpers.RenderHelper;
import org.vivecraft.client_vr.render.helpers.ShaderHelper;
import org.vivecraft.client_vr.settings.VRHotkeys;
import org.vivecraft.client_vr.settings.VRSettings;
import org.vivecraft.client_xr.render_pass.RenderPassManager;
import org.vivecraft.common.network.packet.c2s.VRActivePayloadC2S;
import org.vivecraft.mod_compat_vr.optifine.OptifineHelper;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Mixin(Minecraft.class)
public abstract class MinecraftVRMixin implements MinecraftExtension {

    // keeps track if an attack was initiated by pressing the attack key
    @Unique
    private boolean vivecraft$attackKeyDown;

    // stores the list of resourcepacks that were loaded before a reload, to know if the menuworld should be rebuilt
    @Unique
    private List<String> vivecraft$resourcepacks;

    @Final
    @Shadow
    public Gui gui;

    @Shadow
    @Final
    public Options options;

    @Shadow
    public Screen screen;

    @Shadow
    private ProfilerFiller profiler;

    @Shadow
    @Final
    private Window window;

    @Final
    @Shadow
    public Font font;

    @Shadow
    private boolean pause;

    @Shadow
    private float pausePartialTick;

    @Final
    @Shadow
    private Timer timer;

    @Final
    @Shadow
    public GameRenderer gameRenderer;

    @Shadow
    public ClientLevel level;

    @Shadow
    public RenderTarget mainRenderTarget;

    @Shadow
    public LocalPlayer player;

    @Shadow
    private ProfileResults fpsPieResults;

    @Shadow
    public MultiPlayerGameMode gameMode;

    @Shadow
    @Final
    private RenderBuffers renderBuffers;

    @Shadow
    @Final
    private EntityRenderDispatcher entityRenderDispatcher;

    @Shadow
    @Final
    public LevelRenderer levelRenderer;

    @Shadow
    @Final
    private TextureManager textureManager;

    @Shadow
    @Final
    private ReloadableResourceManager resourceManager;

    @Shadow
    @Final
    public MouseHandler mouseHandler;

    @Shadow
    public abstract Entity getCameraEntity();

    @Shadow
    protected abstract void renderFpsMeter(GuiGraphics guiGraphics, ProfileResults profileResults);

    @Shadow
    public abstract void tick();

    @Shadow
    public abstract CompletableFuture<Void> reloadResourcePacks();

    @Shadow
    @Nullable
    public abstract ClientPacketListener getConnection();

    @Shadow
    public abstract boolean isLocalServer();

    @Shadow
    public abstract IntegratedServer getSingleplayerServer();

    @Shadow
    public abstract void resizeDisplay();

    @Shadow
    public abstract void setScreen(Screen guiScreen);

    @Shadow
    public abstract SoundManager getSoundManager();

    @ModifyArg(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;setOverlay(Lnet/minecraft/client/gui/screens/Overlay;)V"), index = 0)
    private Overlay vivecraft$initVivecraft(Overlay overlay) {
        RenderPassManager.INSTANCE = new RenderPassManager((MainTarget) this.mainRenderTarget);
        VRSettings.initSettings();
        new Thread(UpdateChecker::checkForUpdates, "VivecraftUpdateThread").start();

        // register a resource reload listener, to reload the menu world
        this.resourceManager.registerReloadListener((ResourceManagerReloadListener) resourceManager -> {
            List<String> newPacks = resourceManager.listPacks().map(PackResources::packId).toList();
            if ((this.vivecraft$resourcepacks == null || !this.vivecraft$resourcepacks.equals(newPacks)) &&
                ClientDataHolderVR.getInstance().menuWorldRenderer != null &&
                ClientDataHolderVR.getInstance().menuWorldRenderer.isReady())
            {
                this.vivecraft$resourcepacks = newPacks;
                try {
                    ClientDataHolderVR.getInstance().menuWorldRenderer.destroy();
                    ClientDataHolderVR.getInstance().menuWorldRenderer.prepare();
                } catch (Exception e) {
                    VRSettings.LOGGER.error("Vivecraft: error reloading Menuworld:", e);
                }
            }
        });
        return overlay;
    }

    // on first resource load finished
    @Inject(method = { "method_53522", // fabric
        "lambda$new$6"}, // forge
        at = @At("HEAD"), remap = false)
    private void vivecraft$initVROnLaunch(CallbackInfo ci) {
        // set initial resourcepacks
        this.vivecraft$resourcepacks = this.resourceManager.listPacks().map(PackResources::packId).toList();

        if (OptifineHelper.isOptifineLoaded() && ClientDataHolderVR.getInstance().menuWorldRenderer != null &&
            ClientDataHolderVR.getInstance().menuWorldRenderer.isReady())
        {
            // with optifine this texture somehow fails to load, so manually reload it
            try {
                this.textureManager.getTexture(Gui.CROSSHAIR_SPRITE).load(this.resourceManager);
            } catch (IOException e) {
                // if there was an error, just reload everything
                reloadResourcePacks();
            }
        }
    }

    @Inject(method = "onGameLoadFinished", at = @At("TAIL"))
    private void vivecraft$showGarbageCollectorScreen(CallbackInfo ci) {
        // set the Garbage collector screen here, when it got reset after loading, but don't set it when using quickplay, because it would be removed after loading has finished
        if (VRState.VR_INITIALIZED && !ClientDataHolderVR.getInstance().incorrectGarbageCollector.isEmpty() &&
            !(this.screen instanceof LevelLoadingScreen ||
                this.screen instanceof ReceivingLevelScreen ||
                this.screen instanceof ConnectScreen ||
                this.screen instanceof GarbageCollectorScreen
            ))
        {
            setScreen(new GarbageCollectorScreen(ClientDataHolderVR.getInstance().incorrectGarbageCollector));
            ClientDataHolderVR.getInstance().incorrectGarbageCollector = "";
        }
    }

    @Inject(method = "destroy", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;delayedCrash:Ljava/util/function/Supplier;"))
    private void vivecraft$destroyVR(CallbackInfo ci) {
        try {
            // the game crashed probably not because of us, so keep the vr choice
            VRState.destroyVR(false);
        } catch (Exception ignored) {}
    }

    @Inject(method = "runTick", at = @At("HEAD"))
    private void vivecraft$toggleVRState(CallbackInfo callback) {
        if (VRState.VR_ENABLED) {
            VRState.initializeVR();
        } else if (VRState.VR_INITIALIZED) {
            // turn off VR if it was on before
            vivecraft$switchVRState(false);
            VRState.destroyVR(true);
        }
        if (!VRState.VR_INITIALIZED) {
            return;
        }
        boolean vrActive = !ClientDataHolderVR.getInstance().vrSettings.vrHotswitchingEnabled || ClientDataHolderVR.getInstance().vr.isActive();
        if (VRState.VR_RUNNING != vrActive && (ClientNetworking.SERVER_ALLOWS_VR_SWITCHING || this.player == null)) {
            // switch vr in the menu, or when allowed by the server
            vivecraft$switchVRState(vrActive);
        }
        if (VRState.VR_RUNNING) {
            ClientDataHolderVR.getInstance().frameIndex++;
            RenderPassManager.setGUIRenderPass();
            // reset camera position, if there is one, since it only gets set at the start of rendering, and the last renderpass can be anywhere
            if (this.gameRenderer != null && this.gameRenderer.getMainCamera() != null && this.level != null &&
                this.getCameraEntity() != null)
            {
                this.gameRenderer.getMainCamera().setup(this.level, this.getCameraEntity(), false, false, this.pause ? this.pausePartialTick : this.timer.partialTick);
            }

            this.profiler.push("VR Poll/VSync");
            ClientDataHolderVR.getInstance().vr.poll(ClientDataHolderVR.getInstance().frameIndex);
            this.profiler.pop();
            ClientDataHolderVR.getInstance().vrPlayer.postPoll();
        }
    }

    @Inject(method = "runTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;tick()V"))
    private void vivecraft$preTickTasks(CallbackInfo ci) {
        if (VRState.VR_RUNNING) {
            ClientDataHolderVR.getInstance().vrPlayer.preTick();
        }
        if (VRState.VR_ENABLED) {
            if (ClientDataHolderVR.getInstance().menuWorldRenderer != null) {
                ClientDataHolderVR.getInstance().menuWorldRenderer.checkTask();
                if (ClientDataHolderVR.getInstance().menuWorldRenderer.isBuilding()) {
                    this.profiler.push("Build Menu World");
                    ClientDataHolderVR.getInstance().menuWorldRenderer.buildNext();
                    this.profiler.pop();
                }
            }
        }
    }

    @Inject(method = "runTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;tick()V", shift = Shift.AFTER))
    private void vivecraft$postTickTasks(CallbackInfo ci) {
        if (VRState.VR_RUNNING) {
            ClientDataHolderVR.getInstance().vrPlayer.postTick();
        }
    }

    @Inject(method = "runTick", at = @At(value = "CONSTANT", args = "stringValue=render"))
    private void vivecraft$preRender(CallbackInfo ci) {
        if (VRState.VR_RUNNING) {
            this.profiler.push("preRender");
            ClientDataHolderVR.getInstance().vrPlayer.preRender(this.pause ? this.pausePartialTick : this.timer.partialTick);
            VRHotkeys.updateMovingThirdPersonCam();
            this.profiler.pop();
        }
    }

    @ModifyArg(method = "runTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;render(FJZ)V"))
    private boolean vivecraft$setupRenderGUI(boolean renderLevel) {
        if (VRState.VR_RUNNING) {
            // set gui pass before setup, to always be in that pass and not a random one from last frame
            RenderPassManager.setGUIRenderPass();

            try {
                this.profiler.push("setupRenderConfiguration");
                RenderHelper.checkGLError("pre render setup");
                ClientDataHolderVR.getInstance().vrRenderer.setupRenderConfiguration();
                RenderHelper.checkGLError("post render setup");
            } catch (Exception e) {
                // something went wrong, disable VR
                vivecraft$switchVRState(false);
                VRState.destroyVR(true);
                VRSettings.LOGGER.error("Vivecraft: setupRenderConfiguration failed:", e);
                if (e instanceof RenderConfigException renderConfigException) {
                    setScreen(new ErrorScreen(renderConfigException.title, renderConfigException.error));
                } else {
                    setScreen(new ErrorScreen(Component.translatable("vivecraft.messages.vrrendererror"), TextUtils.throwableToComponent(e)));
                }
                return renderLevel;
            } finally {
                this.profiler.pop();
            }

            RenderSystem.depthMask(true);
            RenderSystem.colorMask(true, true, true, true);
            RenderSystem.defaultBlendFunc();
            this.mainRenderTarget.clear(Minecraft.ON_OSX);
            this.mainRenderTarget.bindWrite(true);

            // draw screen/gui to buffer
            // push pose so we can pop it later
            RenderSystem.getModelViewStack().pushPose();
            ((GameRendererExtension) this.gameRenderer).vivecraft$setShouldDrawScreen(true);
            // only draw the gui when the level was rendered once, since some mods expect that
            ((GameRendererExtension) this.gameRenderer).vivecraft$setShouldDrawGui(
                renderLevel && this.entityRenderDispatcher.camera != null);
            // don't draw the level when we only want the GUI
            return false;
        } else {
            return renderLevel;
        }
    }

    @ModifyExpressionValue(method = "runTick", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;fpsPieResults:Lnet/minecraft/util/profiling/ProfileResults;", ordinal = 0))
    private ProfileResults vivecraft$cancelRegularFpsPie(ProfileResults original) {
        return VRState.VR_RUNNING ? null : original;
    }

    @WrapOperation(method = "runTick", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/pipeline/RenderTarget;blitToScreen(II)V"))
    private void vivecraft$blitMirror(RenderTarget instance, int width, int height, Operation<Void> original) {
        if (!VRState.VR_RUNNING) {
            original.call(instance, width, height);
        } else {
            this.profiler.popPush("vrMirror");
            ShaderHelper.drawMirror();
            RenderHelper.checkGLError("post-mirror");
        }
    }

    // the VR runtime handles the frame limit, no need to manually limit it 60fps
    @ModifyExpressionValue(method = "doWorldLoad", at = @At(value = "CONSTANT", args = "longValue=16"))
    private long vivecraft$noWaitOnLevelLoad(long original) {
        return VRState.VR_RUNNING ? 0L : original;
    }

    @Inject(method = "resizeDisplay", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;getMainRenderTarget()Lcom/mojang/blaze3d/pipeline/RenderTarget;"))
    private void vivecraft$restoreVanillaState(CallbackInfo ci) {
        if (VRState.VR_INITIALIZED) {
            // restore vanilla post chains before the resize, or it will resize the wrong ones
            if (this.levelRenderer != null) {
                ((LevelRendererExtension) this.levelRenderer).vivecraft$restoreVanillaPostChains();
            }
            if (VRState.VR_RUNNING) {
                RenderPassManager.setGUIRenderPass();
            } else {
                RenderPassManager.setVanillaRenderPass();
            }
        }
    }

    @WrapOperation(method = {"continueAttack", "startAttack"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;swing(Lnet/minecraft/world/InteractionHand;)V"))
    private void vivecraft$swingArmAttack(LocalPlayer instance, InteractionHand hand, Operation<Void> original) {
        if (VRState.VR_RUNNING) {
            ClientDataHolderVR.getInstance().swingType = VRFirstPersonArmSwing.Attack;
        }
        original.call(instance, hand);
    }

    @WrapWithCondition(method = "continueAttack", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;stopDestroyBlock()V"))
    private boolean vivecraft$destroyReset(MultiPlayerGameMode instance) {
        // only stop destroying blocks when triggered with a button
        boolean call = !VRState.VR_RUNNING || ClientDataHolderVR.getInstance().vrSettings.seated || this.vivecraft$attackKeyDown;
        this.vivecraft$attackKeyDown = false;
        return call;
    }

    @ModifyExpressionValue(method = "startUseItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;isDestroying()Z"))
    private boolean vivecraft$skipDestroyCheck(boolean isDestroying) {
        // in standing the player can use items even when a block is being destroyed
        // the result of this is inverted
        // this final result is '!isDestroying || (VRState.vrRunning && !seated)'
        return isDestroying && (!VRState.VR_RUNNING || ClientDataHolderVR.getInstance().vrSettings.seated);
    }

    @ModifyExpressionValue(method = "startUseItem", at = @At(value = "CONSTANT", args = "intValue=4"))
    private int vivecraft$customUseDelay(int delay) {
        if (VRState.VR_RUNNING) {
            return switch(ClientDataHolderVR.getInstance().vrSettings.rightclickDelay) {
                case VANILLA -> delay;
                case SLOW -> 6;
                case SLOWER -> 8;
                case SLOWEST -> 10;
            };
        } else {
            return delay;
        }
    }

    @WrapOperation(method = "startUseItem", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;hitResult:Lnet/minecraft/world/phys/HitResult;", ordinal = 1))
    private HitResult vivecraft$activeHand(Minecraft instance, Operation<HitResult> original, @Local InteractionHand hand, @Local ItemStack itemstack) {
        if (VRState.VR_RUNNING) {
            if (ClientDataHolderVR.getInstance().vrSettings.seated || !TelescopeTracker.isTelescope(itemstack)) {
                ClientNetworking.sendActiveHand((byte) hand.ordinal());
            } else {
                // no telescope use in standing vr
                return null;
            }
        }

        return original.call(instance);
    }

    @WrapOperation(method = "startUseItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;swing(Lnet/minecraft/world/InteractionHand;)V"))
    private void vivecraft$swingArmUse(LocalPlayer instance, InteractionHand hand, Operation<Void> original) {
        if (VRState.VR_RUNNING) {
            ClientDataHolderVR.getInstance().swingType = VRFirstPersonArmSwing.Use;
        }
        original.call(instance, hand);
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void vivecraft$vrTick(CallbackInfo ci) {
        ClientDataHolderVR.getInstance().tickCounter++;

        // general chat notifications
        if (this.level != null) {
            // update notification
            if (!ClientDataHolderVR.getInstance().showedUpdateNotification && UpdateChecker.HAS_UPDATE &&
                (ClientDataHolderVR.getInstance().vrSettings.alwaysShowUpdates ||
                    !UpdateChecker.NEWEST_VERSION.equals(ClientDataHolderVR.getInstance().vrSettings.lastUpdate)
                ))
            {
                ClientDataHolderVR.getInstance().vrSettings.lastUpdate = UpdateChecker.NEWEST_VERSION;
                ClientDataHolderVR.getInstance().vrSettings.saveOptions();
                ClientDataHolderVR.getInstance().showedUpdateNotification = true;
                this.gui.getChat().addMessage(Component.translatable("vivecraft.messages.updateAvailable",
                    Component.literal(UpdateChecker.NEWEST_VERSION)
                        .withStyle(ChatFormatting.ITALIC, ChatFormatting.GREEN)).withStyle(
                    style -> style.withClickEvent(
                            new VivecraftClickEvent(VivecraftClickEvent.VivecraftAction.OPEN_SCREEN, new UpdateScreen()))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                            Component.translatable("vivecraft.messages.click")))));
            }
        }

        // VR enabled only chat notifications
        if (VRState.VR_INITIALIZED && this.level != null && ClientDataHolderVR.getInstance().vrPlayer != null) {
            // garbage collector screen
            if (!ClientDataHolderVR.getInstance().incorrectGarbageCollector.isEmpty()) {
                if (!(this.screen instanceof GarbageCollectorScreen)) {
                    // set the Garbage collector screen here, quickplay is used, this shouldn't be triggered in other cases, since the GarbageCollectorScreen resets the string on closing
                    Minecraft.getInstance().setScreen(new GarbageCollectorScreen(ClientDataHolderVR.getInstance().incorrectGarbageCollector));
                }
                ClientDataHolderVR.getInstance().incorrectGarbageCollector = "";
            }
            // server watrnings
            if (ClientDataHolderVR.getInstance().vrPlayer.chatWarningTimer >= 0 &&
                --ClientDataHolderVR.getInstance().vrPlayer.chatWarningTimer == 0)
            {
                boolean showMessage = !ClientNetworking.DISPLAYED_CHAT_WARNING || ClientDataHolderVR.getInstance().vrSettings.showServerPluginMissingMessageAlways;

                // no server mod
                if (ClientDataHolderVR.getInstance().vrPlayer.teleportWarning) {
                    if (showMessage) {
                        this.gui.getChat().addMessage(Component.translatable("vivecraft.messages.noserverplugin"));
                    }
                    ClientDataHolderVR.getInstance().vrPlayer.teleportWarning = false;

                    // allow vr switching on vanilla server
                    ClientNetworking.SERVER_ALLOWS_VR_SWITCHING = true;
                }
                // old server mod
                if (ClientDataHolderVR.getInstance().vrPlayer.vrSwitchWarning) {
                    if (showMessage) {
                        this.gui.getChat().addMessage(Component.translatable("vivecraft.messages.novrhotswitchinglegacy"));
                    }
                    ClientDataHolderVR.getInstance().vrPlayer.vrSwitchWarning = false;
                }
                ClientNetworking.DISPLAYED_CHAT_WARNING = true;
            }
        }

        if (VRState.VR_RUNNING) {
            if (ClientDataHolderVR.getInstance().menuWorldRenderer.isReady() && MethodHolder.isInMenuRoom()) {
                ClientDataHolderVR.getInstance().menuWorldRenderer.tick();
            }

            this.profiler.push("vrProcessInputs");
            ClientDataHolderVR.getInstance().vr.processInputs();
            ClientDataHolderVR.getInstance().vr.processBindings();

            this.profiler.popPush("vrInputActionsTick");
            for (VRInputAction vrinputaction : ClientDataHolderVR.getInstance().vr.getInputActions()) {
                vrinputaction.tick();
            }

            if (this.level != null && ClientDataHolderVR.getInstance().vrPlayer != null) {
                ClientDataHolderVR.getInstance().vrPlayer.updateFreeMove();
            }

            this.profiler.pop();
        }

        this.profiler.push("vrPlayers");
        VRPlayersClient.getInstance().tick();

        this.profiler.popPush("Vivecraft Keybindings");
        vivecraft$processAlwaysAvailableKeybindings();

        this.profiler.pop();
    }

    @Unique
    private void vivecraft$processAlwaysAvailableKeybindings() {
        // menuworld export
        if (VivecraftVRMod.INSTANCE.keyExportWorld.consumeClick() && this.level != null && this.player != null) {
            Throwable error = null;
            try {
                final BlockPos blockpos = this.player.blockPosition();
                int size = 320;
                int offset = size / 2;
                File dir = new File(MenuWorldDownloader.CUSTOM_WORLD_FOLDER);
                dir.mkdirs();

                File foundFile;
                for (int i = 0;; i++) {
                    foundFile = new File(dir, "world" + i + ".mmw");
                    if (!foundFile.exists())
                        break;
                }

                VRSettings.LOGGER.info("Vivecraft: Exporting world... area size: {}", size);
                VRSettings.LOGGER.info("Vivecraft: Saving to {}", foundFile.getAbsolutePath());

                if (isLocalServer()) {
                    final Level level = getSingleplayerServer().getLevel(this.player.level().dimension());
                    File finalFoundFile = foundFile;
                    CompletableFuture<Throwable> completablefuture = getSingleplayerServer().submit(() -> {
                        try {
                            MenuWorldExporter.saveAreaToFile(level, blockpos.getX() - offset, blockpos.getZ() - offset,
                                size, size, blockpos.getY(), finalFoundFile);
                        } catch (Throwable throwable) {
                            VRSettings.LOGGER.error("Vivecraft: error exporting menuworld:", throwable);
                            return throwable;
                        }
                        return null;
                    });

                    error = completablefuture.get();
                } else {
                    MenuWorldExporter.saveAreaToFile(this.level, blockpos.getX() - offset, blockpos.getZ() - offset, size, size, blockpos.getY(), foundFile);
                    this.gui.getChat().addMessage(Component.translatable("vivecraft.messages.menuworldexportclientwarning"));
                }

                if (error == null) {
                    this.gui.getChat().addMessage(Component.translatable("vivecraft.messages.menuworldexportcomplete.1", size));
                    this.gui.getChat().addMessage(Component.translatable("vivecraft.messages.menuworldexportcomplete.2", foundFile.getAbsolutePath()));
                }
            } catch (Throwable throwable) {
                VRSettings.LOGGER.error("Vivecraft: Error exporting Menuworld:", throwable);
                error = throwable;
            } finally {
                if (error != null) {
                    this.gui.getChat().addMessage(Component.translatable("vivecraft.messages.menuworldexporterror", error.getMessage()));
                }
            }
        }

        // quick commands
        for (int i = 0; i < VivecraftVRMod.INSTANCE.keyQuickCommands.length; i++) {
            if (VivecraftVRMod.INSTANCE.keyQuickCommands[i].consumeClick()) {
                String command = ClientDataHolderVR.getInstance().vrSettings.vrQuickCommands[i];
                if (command.startsWith("/")) {
                    this.player.connection.sendCommand(command.substring(1));
                } else {
                    this.player.connection.sendChat(command);
                }
            }
        }
    }

    @WrapWithCondition(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;pick(F)V"))
    private boolean vivecraft$removePick(GameRenderer instance, float partialTicks) {
        // not exactly why we remove that, probably to safe some performance
        return !VRState.VR_RUNNING;
    }

    @WrapOperation(method = "handleKeybinds", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Options;setCameraType(Lnet/minecraft/client/CameraType;)V"))
    private void vivecraft$changeVrMirror(Options instance, CameraType pointOfView, Operation<Void> original) {
        if (VRState.VR_RUNNING) {
            ClientDataHolderVR.getInstance().vrSettings.setOptionValue(VRSettings.VrOptions.MIRROR_DISPLAY);
            MirrorNotification.notify(
                ClientDataHolderVR.getInstance().vrSettings.getButtonDisplayString(VRSettings.VrOptions.MIRROR_DISPLAY),
                false, 3000);
        } else {
            original.call(instance, pointOfView);
        }
    }

    @WrapWithCondition(method = "handleKeybinds", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;checkEntityPostEffect(Lnet/minecraft/world/entity/Entity;)V"))
    private boolean vivecraft$noPostEffectVR(GameRenderer instance, Entity entity) {
        return !VRState.VR_RUNNING;
    }

    @WrapOperation(method = "handleKeybinds", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;swing(Lnet/minecraft/world/InteractionHand;)V"))
    private void vivecraft$swingArmDrop(LocalPlayer instance, InteractionHand hand, Operation<Void> original) {
        if (VRState.VR_RUNNING) {
            ClientDataHolderVR.getInstance().swingType = VRFirstPersonArmSwing.Attack;
        }
        original.call(instance, hand);
    }

    @ModifyExpressionValue(method = "handleKeybinds", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/KeyMapping;isDown()Z", ordinal = 2))
    private boolean vivecraft$useKeyOverride(boolean useKeyDown) {
        if (!VRState.VR_RUNNING || ClientDataHolderVR.getInstance().vrSettings.seated) {
            return useKeyDown;
        } else {
            return useKeyDown || ClientDataHolderVR.getInstance().bowTracker.isActive(this.player) ||
                ClientDataHolderVR.getInstance().autoFood.isEating();
        }
    }

    @Inject(method = "handleKeybinds", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;releaseUsingItem(Lnet/minecraft/world/entity/player/Player;)V"))
    private void vivecraft$sendActiveHand(CallbackInfo ci) {
        if (VRState.VR_RUNNING) {
            ClientNetworking.sendActiveHand((byte) this.player.getUsedItemHand().ordinal());
        }
    }

    @Inject(method = "handleKeybinds", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;startAttack()Z"))
    private void vivecraft$markAttackKeyDown(CallbackInfo ci) {
        // detect, if the attack button was used to destroy blocks
        this.vivecraft$attackKeyDown = true;
    }

    @ModifyExpressionValue(method = "handleKeybinds", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/MouseHandler;isMouseGrabbed()Z"))
    private boolean vivecraft$vrAlwaysGrabbed(boolean isMouseGrabbed) {
        return isMouseGrabbed || VRState.VR_RUNNING;
    }

    @Inject(method = "setLevel", at = @At("HEAD"))
    private void vivecraft$resetRoomOrigin(CallbackInfo ci) {
        if (VRState.VR_RUNNING) {
            ClientDataHolderVR.getInstance().vrPlayer.setRoomOrigin(0.0D, 0.0D, 0.0D, true);
        }
    }

    @Inject(method = "setOverlay", at = @At("TAIL"))
    private void vivecraft$onOverlaySet(CallbackInfo ci) {
        GuiHandler.onScreenChanged(this.screen, this.screen, true);
    }

    @Inject(method = "setScreen", at = @At("HEAD"))
    private void vivecraft$onScreenChange(Screen guiScreen, CallbackInfo ci, @Share("guiScale") LocalIntRef guiScaleRef) {
        if (guiScreen == null) {
            GuiHandler.GUI_APPEAR_OVER_BLOCK_ACTIVE = false;
        }
        // cache gui scale so it can be checked after screen apply
        guiScaleRef.set(this.options.guiScale().get());
    }

    @Inject(method = "setScreen", at = @At(value = "FIELD", opcode = Opcodes.PUTFIELD, target = "Lnet/minecraft/client/Minecraft;screen:Lnet/minecraft/client/gui/screens/Screen;", ordinal = 0))
    private void vivecraft$onScreenSet(Screen guiScreen, CallbackInfo ci) {
        GuiHandler.onScreenChanged(this.screen, guiScreen, true);
    }

    @Inject(method = "setScreen", at = @At("RETURN"))
    private void vivecraft$checkGuiScaleChangePost(CallbackInfo ci, @Share("guiScale") LocalIntRef guiScaleRef) {
        if (guiScaleRef.get() != this.options.guiScale().get()) {
            // checks if something changed the GuiScale during screen change
            // and tries to adjust the VR GuiScale accordingly
            int maxScale = VRState.VR_RUNNING ? GuiHandler.GUI_SCALE_FACTOR_MAX :
                this.window.calculateScale(0, this.options.forceUnicodeFont().get());

            // auto uses max scale
            if (guiScaleRef.get() == 0) {
                guiScaleRef.set(maxScale);
            }

            int newScale = this.options.guiScale().get() == 0 ? maxScale : this.options.guiScale().get();

            if (newScale < guiScaleRef.get()) {
                // if someone reduced the gui scale, try to reduce the VR gui scale by the same steps
                int newVRScale = VRState.VR_RUNNING ? newScale :
                    Math.max(1, GuiHandler.GUI_SCALE_FACTOR_MAX - (guiScaleRef.get() - newScale));
                GuiHandler.GUI_SCALE_FACTOR = GuiHandler.calculateScale(newVRScale, this.options.forceUnicodeFont().get(),
                    GuiHandler.GUI_WIDTH, GuiHandler.GUI_HEIGHT);
            } else {
                // new gui scale is bigger than before, so just reset to the default
                VRSettings vrSettings = ClientDataHolderVR.getInstance().vrSettings;
                GuiHandler.GUI_SCALE_FACTOR = GuiHandler.calculateScale(
                    vrSettings.doubleGUIResolution ? vrSettings.guiScale : (int) Math.ceil(vrSettings.guiScale * 0.5f),
                    this.options.forceUnicodeFont().get(), GuiHandler.GUI_WIDTH, GuiHandler.GUI_HEIGHT);
            }

            // resize the screen for the new gui scale
            if (VRState.VR_RUNNING && this.screen != null) {
                this.screen.resize(Minecraft.getInstance(), GuiHandler.SCALED_WIDTH, GuiHandler.SCALED_HEIGHT);
            }
        }
    }

    /**
     * switches the VR state
     * @param vrActive if VR is now on or off
     */
    @Unique
    private void vivecraft$switchVRState(boolean vrActive) {
        VRState.VR_RUNNING = vrActive;
        if (vrActive) {
            if (this.player != null) {
                // snap room origin to the player
                ClientDataHolderVR.getInstance().vrPlayer.snapRoomOriginToPlayerEntity(this.player, false, false);
            }
            // release mouse when switching to standing
            if (!ClientDataHolderVR.getInstance().vrSettings.seated) {
                InputConstants.grabOrReleaseMouse(this.window.getWindow(), GLFW.GLFW_CURSOR_NORMAL,
                    this.mouseHandler.xpos(), this.mouseHandler.ypos());
            }
        } else {
            // VR got disabled
            // reset gui
            GuiHandler.GUI_POS_ROOM = null;
            GuiHandler.GUI_ROTATION_ROOM = null;
            GuiHandler.GUI_SCALE = 1.0F;

            if (this.player != null) {
                // remove vr player instance
                VRPlayersClient.getInstance().disableVR(this.player.getUUID());
            }
            if (this.gameRenderer != null) {
                // update active effect, since VR does block t hem
                this.gameRenderer.checkEntityPostEffect(this.options.getCameraType().isFirstPerson() ? this.getCameraEntity() : null);
            }
            if (this.screen != null || this.level == null) {
                // release mouse
                this.mouseHandler.releaseMouse();
                InputConstants.grabOrReleaseMouse(this.window.getWindow(), GLFW.GLFW_CURSOR_NORMAL,
                    this.mouseHandler.xpos(), this.mouseHandler.ypos());
            } else {
                // grab mouse when in a menu
                this.mouseHandler.grabMouse();
                InputConstants.grabOrReleaseMouse(this.window.getWindow(), GLFW.GLFW_CURSOR_DISABLED,
                    this.mouseHandler.xpos(), this.mouseHandler.ypos());
            }
        }

        // send new VR state to the server
        if (this.getConnection() != null) {
            this.getConnection().send(ClientNetworking.createServerPacket(new VRActivePayloadC2S(vrActive)));
        }

        // reload sound manager, to toggle HRTF between VR and NONVR one
        if (!getSoundManager().getAvailableSounds().isEmpty()) {
            getSoundManager().reload();
        }
        resizeDisplay();
        this.window.updateVsync(this.options.enableVsync().get());
    }

    /**
     * method to draw the profiler pie separately
     */
    @Unique
    @Override
    public void vivecraft$drawProfiler() {
        if (this.fpsPieResults != null) {
            this.profiler.push("fpsPie");
            GuiGraphics guiGraphics = new GuiGraphics((Minecraft) (Object) this, this.renderBuffers.bufferSource());
            this.renderFpsMeter(guiGraphics, this.fpsPieResults);
            guiGraphics.flush();
            this.profiler.pop();
        }
    }
}
