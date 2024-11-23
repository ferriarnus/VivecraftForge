package org.vivecraft.mixin.client_vr;

import com.mojang.blaze3d.pipeline.MainTarget;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.shaders.ProgramManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
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
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
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
import org.vivecraft.client.extensions.RenderTargetExtension;
import org.vivecraft.client.gui.VivecraftClickEvent;
import org.vivecraft.client.gui.screens.ErrorScreen;
import org.vivecraft.client.gui.screens.GarbageCollectorScreen;
import org.vivecraft.client.gui.screens.UpdateScreen;
import org.vivecraft.client.network.ClientNetworking;
import org.vivecraft.client.utils.UpdateChecker;
import org.vivecraft.client.utils.Utils;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.VRState;
import org.vivecraft.client_vr.extensions.*;
import org.vivecraft.client_vr.gameplay.screenhandlers.GuiHandler;
import org.vivecraft.client_vr.gameplay.trackers.TelescopeTracker;
import org.vivecraft.client_vr.menuworlds.MenuWorldDownloader;
import org.vivecraft.client_vr.menuworlds.MenuWorldExporter;
import org.vivecraft.client_vr.provider.control.VRInputAction;
import org.vivecraft.client_vr.render.RenderConfigException;
import org.vivecraft.client_vr.render.RenderPass;
import org.vivecraft.client_vr.render.VRFirstPersonArmSwing;
import org.vivecraft.client_vr.render.VRShaders;
import org.vivecraft.client_vr.settings.VRHotkeys;
import org.vivecraft.client_vr.settings.VRSettings;
import org.vivecraft.client_xr.render_pass.RenderPassManager;
import org.vivecraft.common.utils.math.Vector3;
import org.vivecraft.mod_compat_vr.optifine.OptifineHelper;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Mixin(Minecraft.class)
public abstract class MinecraftVRMixin implements MinecraftExtension {

    @Unique
    private boolean vivecraft$lastClick;

    @Unique
    private int vivecraft$currentHand = 0;

    @Unique
    private long vivecraft$mirroNotifyStart;

    @Unique
    private long vivecraft$mirroNotifyLen;

    @Unique
    private boolean vivecraft$mirrorNotifyClear;

    @Unique
    private String vivecraft$mirrorNotifyText;

    @Unique
    private List<String> vivecraft$resourcepacks;

    @Final
    @Shadow
    public Gui gui;

    @Shadow
    @Final
    public File gameDirectory;

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

    @Final
    @Shadow
    public static boolean ON_OSX;

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
    private int rightClickDelay;

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

    @ModifyArg(at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;setOverlay(Lnet/minecraft/client/gui/screens/Overlay;)V"), method = "<init>", index = 0)
    public Overlay vivecraft$initVivecraft(Overlay overlay) {
        RenderPassManager.INSTANCE = new RenderPassManager((MainTarget) this.mainRenderTarget);
        VRSettings.initSettings((Minecraft) (Object) this, this.gameDirectory);
        new Thread(UpdateChecker::checkForUpdates, "VivecraftUpdateThread").start();

        // register a resource reload listener, to reload the menu world
        resourceManager.registerReloadListener((ResourceManagerReloadListener) resourceManager -> {
            List<String> newPacks = resourceManager.listPacks().map(PackResources::packId).toList();
            if ((vivecraft$resourcepacks == null || !vivecraft$resourcepacks.equals(newPacks)) &&
                ClientDataHolderVR.getInstance().menuWorldRenderer != null
                && ClientDataHolderVR.getInstance().menuWorldRenderer.isReady()) {
                vivecraft$resourcepacks = newPacks;
                try {
                    ClientDataHolderVR.getInstance().menuWorldRenderer.destroy();
                    ClientDataHolderVR.getInstance().menuWorldRenderer.prepare();
                } catch (Exception exception) {
                    exception.printStackTrace();
                }
            }
        });
        return overlay;
    }

    // on first resource load finished
    @Inject(at = @At("HEAD"), method = {
        "method_53522", // fabric
        "lambda$new$6"} // forge
        , remap = false)
    public void vivecraft$initVROnLaunch(CallbackInfo ci) {
        // set initial resourcepacks
        vivecraft$resourcepacks = resourceManager.listPacks().map(PackResources::packId).toList();

        if (OptifineHelper.isOptifineLoaded() && ClientDataHolderVR.getInstance().menuWorldRenderer != null && ClientDataHolderVR.getInstance().menuWorldRenderer.isReady()) {
            // with optifine this texture somehow fails to load, so manually reload it
            try {
                textureManager.getTexture(Gui.CROSSHAIR_SPRITE).load(resourceManager);
            } catch (IOException e) {
                // if there was an error, just reload everything
                reloadResourcePacks();
            }
        }
    }

    @Inject(at = @At("TAIL"), method = "onGameLoadFinished")
    private void vivecraft$showGarbageCollectorScreen(CallbackInfo ci) {
        // set the Garbage collector screen here, when it got reset after loading, but don't set it when using quickplay, because it would be removed after loading has finished
        if (VRState.vrEnabled && !ClientDataHolderVR.getInstance().incorrectGarbageCollector.isEmpty()
            && !(screen instanceof LevelLoadingScreen
            || screen instanceof ReceivingLevelScreen
            || screen instanceof ConnectScreen
            || screen instanceof GarbageCollectorScreen)) {
            Minecraft.getInstance().setScreen(new GarbageCollectorScreen(ClientDataHolderVR.getInstance().incorrectGarbageCollector));
            ClientDataHolderVR.getInstance().incorrectGarbageCollector = "";
        }
    }

    @Inject(at = @At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;delayedCrash:Ljava/util/function/Supplier;", shift = Shift.BEFORE), method = "destroy()V")
    public void vivecraft$destroy(CallbackInfo info) {
        try {
            // the game crashed probably not because of us, so keep the vr choice
            VRState.destroyVR(false);
        } catch (Exception ignored) {
        }
    }

    @Inject(at = @At("HEAD"), method = "runTick(Z)V")
    public void vivecraft$toggleVRState(boolean tick, CallbackInfo callback) {
        if (VRState.vrEnabled) {
            VRState.initializeVR();
        } else if (VRState.vrInitialized) {
            vivecraft$switchVRState(false);
            VRState.destroyVR(true);
        }
        if (!VRState.vrInitialized) {
            return;
        }
        boolean vrActive = ClientDataHolderVR.getInstance().vr.isActive() || !ClientDataHolderVR.getInstance().vrSettings.vrHotswitchingEnabled;
        if (VRState.vrRunning != vrActive && (ClientNetworking.serverAllowsVrSwitching || player == null)) {
            vivecraft$switchVRState(vrActive);
        }
        if (VRState.vrRunning) {
            ++ClientDataHolderVR.getInstance().frameIndex;
            RenderPassManager.setGUIRenderPass();
            // reset camera position, if there is one, since it only gets set at the start of rendering, and the last renderpass can be anywhere
            if (gameRenderer != null && gameRenderer.getMainCamera() != null && level != null && this.getCameraEntity() != null) {
                this.gameRenderer.getMainCamera().setup(this.level, this.getCameraEntity(), false, false, this.pause ? this.pausePartialTick : this.timer.partialTick);
            }

            this.profiler.push("VR Poll/VSync");
            ClientDataHolderVR.getInstance().vr.poll(ClientDataHolderVR.getInstance().frameIndex);
            this.profiler.pop();
            ClientDataHolderVR.getInstance().vrPlayer.postPoll();
        }
    }

    @Inject(at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;tick()V", shift = At.Shift.BEFORE), method = "runTick")
    public void vivecraft$preTickTasks(CallbackInfo ci) {
        if (VRState.vrRunning) {
            ClientDataHolderVR.getInstance().vrPlayer.preTick();
        }
        if (VRState.vrEnabled) {
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

    @Inject(at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;tick()V", shift = At.Shift.AFTER), method = "runTick")
    public void vivecraft$postTickTasks(CallbackInfo ci) {
        if (VRState.vrRunning) {
            ClientDataHolderVR.getInstance().vrPlayer.postTick();
        }
    }

    @Inject(at = @At(value = "CONSTANT", args = "stringValue=render"), method = "runTick")
    public void vivecraft$preRender(CallbackInfo ci) {
        if (VRState.vrRunning) {
            this.profiler.push("preRender");
            ClientDataHolderVR.getInstance().vrPlayer.preRender(this.pause ? this.pausePartialTick : this.timer.partialTick);
            VRHotkeys.updateMovingThirdPersonCam();
            this.profiler.pop();
        }
    }

    @ModifyArg(at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;render(FJZ)V"), method = "runTick")
    public boolean vivecraft$setupRenderGUI(boolean renderLevel) {
        if (VRState.vrRunning) {

            this.profiler.push("setupRenderConfiguration");
            try {
                this.vivecraft$checkGLError("pre render setup ");
                ClientDataHolderVR.getInstance().vrRenderer.setupRenderConfiguration(true);
                this.vivecraft$checkGLError("post render setup ");
            } catch (RenderConfigException renderConfigException) {
                vivecraft$switchVRState(false);
                VRState.destroyVR(true);
                Minecraft.getInstance().setScreen(new ErrorScreen("VR Render Error", renderConfigException.error));
                this.profiler.pop();
                return renderLevel;
            } catch (Exception exception2) {
                exception2.printStackTrace();
            }
            this.profiler.pop();

            RenderPassManager.setGUIRenderPass();
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
            ((GameRendererExtension) this.gameRenderer).vivecraft$setShouldDrawGui(renderLevel && this.entityRenderDispatcher.camera != null);
            return false;
        } else {
            return renderLevel;
        }
    }

    @Redirect(at = @At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;fpsPieResults:Lnet/minecraft/util/profiling/ProfileResults;"), method = "runTick")
    public ProfileResults vivecraft$cancelRegularFpsPie(Minecraft instance) {
        return VRState.vrRunning ? null : fpsPieResults;
    }

    @Redirect(at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/pipeline/RenderTarget;blitToScreen(II)V"), method = "runTick")
    public void vivecraft$blitMirror(RenderTarget instance, int width, int height) {
        if (!VRState.vrRunning) {
            instance.blitToScreen(width, height);
        } else {
            this.profiler.popPush("vrMirror");
            this.vivecraft$copyToMirror();
            this.vivecraft$drawNotifyMirror();
            this.vivecraft$checkGLError("post-mirror ");
        }
    }

    // the VR runtime handles the frame limit, no need to manually limit it 60fps
    @ModifyConstant(constant = @Constant(longValue = 16), method = "doWorldLoad", expect = 0)
    private long vivecraft$noWaitOnLevelLoadFabric(long constant) {
        if (VRState.vrRunning) {
            return 0L;
        }
        return constant;
    }

    @Inject(at = @At("HEAD"), method = "resizeDisplay")
    void vivecraft$restoreVanillaState(CallbackInfo ci) {
        if (VRState.vrInitialized) {
            // restore vanilla post chains before the resize, or it will resize the wrong ones
            if (levelRenderer != null) {
                ((LevelRendererExtension) levelRenderer).vivecraft$restoreVanillaPostChains();
            }
            RenderPassManager.setVanillaRenderPass();
        }
    }

    @Redirect(at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;swing(Lnet/minecraft/world/InteractionHand;)V"), method = "continueAttack(Z)V")
    public void vivecraft$swingArmContinueAttack(LocalPlayer player, InteractionHand hand) {
        if (VRState.vrRunning) {
            ((PlayerExtension) player).vivecraft$swingArm(InteractionHand.MAIN_HAND, VRFirstPersonArmSwing.Attack);
        } else {
            player.swing(hand);
        }
    }

    @Redirect(at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;stopDestroyBlock()V"), method = "continueAttack(Z)V")
    public void vivecraft$destroyseated(MultiPlayerGameMode gm) {
        if (!VRState.vrRunning || ClientDataHolderVR.getInstance().vrSettings.seated || vivecraft$lastClick) {
            this.gameMode.stopDestroyBlock();
            vivecraft$lastClick = false;
        }
    }

    @Redirect(at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;isDestroying()Z"), method = "startUseItem()V")
    public boolean vivecraft$seatedCheck(MultiPlayerGameMode gameMode) {
        return gameMode.isDestroying() && (!VRState.vrRunning || ClientDataHolderVR.getInstance().vrSettings.seated);
    }

    @Inject(at = @At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;rightClickDelay:I", shift = Shift.AFTER, opcode = Opcodes.PUTFIELD), method = "startUseItem()V")
    public void vivecraft$breakDelay(CallbackInfo info) {
        if (VRState.vrRunning) {
            if (ClientDataHolderVR.getInstance().vrSettings.rightclickDelay == VRSettings.RightClickDelay.VANILLA) {
                this.rightClickDelay = 4;
            } else if (ClientDataHolderVR.getInstance().vrSettings.rightclickDelay == VRSettings.RightClickDelay.SLOW) {
                this.rightClickDelay = 6;
            } else if (ClientDataHolderVR.getInstance().vrSettings.rightclickDelay == VRSettings.RightClickDelay.SLOWER) {
                this.rightClickDelay = 8;
            } else if (ClientDataHolderVR.getInstance().vrSettings.rightclickDelay == VRSettings.RightClickDelay.SLOWEST) {
                this.rightClickDelay = 10;
            }
        }
    }

    @Inject(at = @At("HEAD"), method = "startUseItem")
    private void vivecraft$resetHand(CallbackInfo ci) {
        vivecraft$currentHand = 0;
    }

    @Redirect(at = @At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;hitResult:Lnet/minecraft/world/phys/HitResult;", ordinal = 1), method = "startUseItem")
    public HitResult vivecraft$activeHand(Minecraft instance) {
        boolean isTelescope = false;
        if (VRState.vrRunning) {
            InteractionHand interactionHand = InteractionHand.values()[vivecraft$currentHand++];
            ItemStack itemInHand = this.player.getItemInHand(interactionHand);
            isTelescope = TelescopeTracker.isTelescope(itemInHand);
            if (ClientDataHolderVR.getInstance().vrSettings.seated || !isTelescope) {
                ClientNetworking.sendActiveHand((byte) interactionHand.ordinal());
            }
        }
        if (!VRState.vrRunning || ClientDataHolderVR.getInstance().vrSettings.seated || !isTelescope) {
            return instance.hitResult;
        }
        return null;
    }


    @Redirect(at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;swing(Lnet/minecraft/world/InteractionHand;)V"), method = "startUseItem")
    public void vivecraft$swingUse(LocalPlayer instance, InteractionHand interactionHand) {
        if (VRState.vrRunning) {
            ((PlayerExtension) instance).vivecraft$swingArm(interactionHand, VRFirstPersonArmSwing.Use);
        } else {
            instance.swing(interactionHand);
        }
    }

    @Inject(at = @At("HEAD"), method = "tick()V")
    public void vivecraft$vrTick(CallbackInfo info) {
        ++ClientDataHolderVR.getInstance().tickCounter;

        // general chat notifications
        if (this.level != null) {
            if (!ClientDataHolderVR.getInstance().showedUpdateNotification && UpdateChecker.hasUpdate && (ClientDataHolderVR.getInstance().vrSettings.alwaysShowUpdates || !UpdateChecker.newestVersion.equals(ClientDataHolderVR.getInstance().vrSettings.lastUpdate))) {
                ClientDataHolderVR.getInstance().vrSettings.lastUpdate = UpdateChecker.newestVersion;
                ClientDataHolderVR.getInstance().vrSettings.saveOptions();
                ClientDataHolderVR.getInstance().showedUpdateNotification = true;
                this.gui.getChat().addMessage(Component.translatable("vivecraft.messages.updateAvailable", Component.literal(UpdateChecker.newestVersion).withStyle(ChatFormatting.ITALIC, ChatFormatting.GREEN))
                    .withStyle(style -> style
                        .withClickEvent(new VivecraftClickEvent(VivecraftClickEvent.VivecraftAction.OPEN_SCREEN, new UpdateScreen()))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("vivecraft.messages.click")))));
            }
        }

        // VR enabled only chat notifications
        if (VRState.vrInitialized && this.level != null && ClientDataHolderVR.getInstance().vrPlayer != null) {
            if (!ClientDataHolderVR.getInstance().incorrectGarbageCollector.isEmpty()) {
                if (!(screen instanceof GarbageCollectorScreen)) {
                    // set the Garbage collector screen here, quickplay is used, this shouldn't be triggered in other cases, since the GarbageCollectorScreen resets the string on closing
                    Minecraft.getInstance().setScreen(new GarbageCollectorScreen(ClientDataHolderVR.getInstance().incorrectGarbageCollector));
                }
                ClientDataHolderVR.getInstance().incorrectGarbageCollector = "";
            }
            if (ClientDataHolderVR.getInstance().vrPlayer.chatWarningTimer >= 0 && --ClientDataHolderVR.getInstance().vrPlayer.chatWarningTimer == 0) {
                boolean showMessage = !ClientNetworking.displayedChatWarning || ClientDataHolderVR.getInstance().vrSettings.showServerPluginMissingMessageAlways;

                if (ClientDataHolderVR.getInstance().vrPlayer.teleportWarning) {
                    if (showMessage) {
                        this.gui.getChat().addMessage(Component.translatable("vivecraft.messages.noserverplugin"));
                    }
                    ClientDataHolderVR.getInstance().vrPlayer.teleportWarning = false;

                    // allow vr switching on vanilla server
                    ClientNetworking.serverAllowsVrSwitching = true;
                }
                if (ClientDataHolderVR.getInstance().vrPlayer.vrSwitchWarning) {
                    if (showMessage) {
                        this.gui.getChat().addMessage(Component.translatable("vivecraft.messages.novrhotswitchinglegacy"));
                    }
                    ClientDataHolderVR.getInstance().vrPlayer.vrSwitchWarning = false;
                }
                ClientNetworking.displayedChatWarning = true;
            }
        }

        if (VRState.vrRunning) {

            if (ClientDataHolderVR.getInstance().menuWorldRenderer.isReady()) {
                // update textures in the menu
                if (this.level == null) {
                    this.textureManager.tick();
                }
                ClientDataHolderVR.getInstance().menuWorldRenderer.tick();
            }

            this.profiler.push("vrProcessInputs");
            ClientDataHolderVR.getInstance().vr.processInputs();
            ClientDataHolderVR.getInstance().vr.processBindings();

            this.profiler.popPush("vrInputActionsTick");

            for (VRInputAction vrinputaction : ClientDataHolderVR.getInstance().vr.getInputActions()) {
                vrinputaction.tick();
            }

            if (ClientDataHolderVR.getInstance().vrSettings.displayMirrorMode == VRSettings.MirrorMode.MIXED_REALITY || ClientDataHolderVR.getInstance().vrSettings.displayMirrorMode == VRSettings.MirrorMode.THIRD_PERSON) {
                VRHotkeys.handleMRKeys();
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
        if (VivecraftVRMod.INSTANCE.keyExportWorld.consumeClick() && level != null && player != null) {
            Throwable error = null;
            try {
                final BlockPos blockpos = player.blockPosition();
                int size = 320;
                int offset = size / 2;
                File file1 = new File(MenuWorldDownloader.customWorldFolder);
                file1.mkdirs();
                int i = 0;

                while (true) {
                    final File file2 = new File(file1, "world" + i + ".mmw");

                    if (!file2.exists()) {
                        VRSettings.logger.info("Exporting world... area size: " + size);
                        VRSettings.logger.info("Saving to " + file2.getAbsolutePath());

                        if (isLocalServer()) {
                            final Level level = getSingleplayerServer().getLevel(player.level().dimension());
                            CompletableFuture<Throwable> completablefuture = getSingleplayerServer().submit(() -> {
                                try {
                                    MenuWorldExporter.saveAreaToFile(level, blockpos.getX() - offset, blockpos.getZ() - offset, size, size, blockpos.getY(), file2);
                                } catch (Throwable throwable) {
                                    throwable.printStackTrace();
                                    return throwable;
                                }
                                return null;
                            });

                            error = completablefuture.get();
                        } else {
                            MenuWorldExporter.saveAreaToFile(level, blockpos.getX() - offset, blockpos.getZ() - offset, size, size, blockpos.getY(), file2);
                            gui.getChat().addMessage(Component.translatable("vivecraft.messages.menuworldexportclientwarning"));
                        }

                        if (error == null) {
                            gui.getChat().addMessage(Component.translatable("vivecraft.messages.menuworldexportcomplete.1", size));
                            gui.getChat().addMessage(Component.translatable("vivecraft.messages.menuworldexportcomplete.2", file2.getAbsolutePath()));
                        }
                        break;
                    }

                    i++;
                }
            } catch (Throwable throwable) {
                throwable.printStackTrace();
                error = throwable;
            } finally {
                if (error != null) {
                    gui.getChat().addMessage(Component.translatable("vivecraft.messages.menuworldexporterror", error.getMessage()));
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

    @Redirect(at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;pick(F)V"), method = "tick")
    public void vivecraft$removePick(GameRenderer instance, float f) {
        if (!VRState.vrRunning) {
            instance.pick(f);
        }
    }

    @Redirect(at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Options;setCameraType(Lnet/minecraft/client/CameraType;)V"), method = "handleKeybinds")
    public void vivecraft$vrMirrorOption(Options instance, CameraType cameraType) {
        if (VRState.vrRunning) {
            ClientDataHolderVR.getInstance().vrSettings.setOptionValue(VRSettings.VrOptions.MIRROR_DISPLAY);
            this.vivecraft$notifyMirror(ClientDataHolderVR.getInstance().vrSettings.getButtonDisplayString(VRSettings.VrOptions.MIRROR_DISPLAY), false, 3000);
            // this.levelRenderer.needsUpdate();
        } else {
            instance.setCameraType(cameraType);
        }
    }

    @Redirect(at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;checkEntityPostEffect(Lnet/minecraft/world/entity/Entity;)V"), method = "handleKeybinds")
    public void vivecraft$noPosEffect(GameRenderer instance, Entity entity) {
        if (!VRState.vrRunning) {
            instance.checkEntityPostEffect(entity);
        }
    }

    @Redirect(at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;swing(Lnet/minecraft/world/InteractionHand;)V"), method = "handleKeybinds()V")
    public void vivecraft$swingArmhandleKeybinds(LocalPlayer instance, InteractionHand interactionHand) {
        if (VRState.vrRunning) {
            ((PlayerExtension) player).vivecraft$swingArm(InteractionHand.MAIN_HAND, VRFirstPersonArmSwing.Attack);
        } else {
            instance.swing(interactionHand);
        }
    }

    @Redirect(at = @At(value = "INVOKE", target = "Lnet/minecraft/client/KeyMapping;isDown()Z", ordinal = 2), method = "handleKeybinds")
    public boolean vivecraft$vrKeyuse(KeyMapping instance) {
        return !(!instance.isDown() && (!VRState.vrRunning || ((!ClientDataHolderVR.getInstance().bowTracker.isActive(this.player) || ClientDataHolderVR.getInstance().vrSettings.seated) && !ClientDataHolderVR.getInstance().autoFood.isEating())));
    }

    @Inject(at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;releaseUsingItem(Lnet/minecraft/world/entity/player/Player;)V", shift = Shift.BEFORE), method = "handleKeybinds")
    public void vivecraft$activeHand(CallbackInfo ci) {
        if (VRState.vrRunning) {
            ClientNetworking.sendActiveHand((byte) this.player.getUsedItemHand().ordinal());
        }
    }

    @Inject(at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;startAttack()Z"), method = "handleKeybinds")
    public void vivecraft$attackDown(CallbackInfo ci) {
        // detect, if the attack button was used to destroy blocks
        this.vivecraft$lastClick = true;
    }

    @Redirect(at = @At(value = "INVOKE", target = "Lnet/minecraft/client/MouseHandler;isMouseGrabbed()Z"), method = "handleKeybinds")
    public boolean vivecraft$vrAlwaysGrapped(MouseHandler instance) {
        return VRState.vrRunning || instance.isMouseGrabbed();
    }

    @Inject(at = @At("HEAD"), method = "setLevel(Lnet/minecraft/client/multiplayer/ClientLevel;)V")
    public void vivecraft$roomScale(ClientLevel pLevelClient, CallbackInfo info) {
        if (VRState.vrRunning) {
            ClientDataHolderVR.getInstance().vrPlayer.setRoomOrigin(0.0D, 0.0D, 0.0D, true);
        }
    }

    @Inject(at = @At(value = "FIELD", opcode = Opcodes.PUTFIELD, target = "Lnet/minecraft/client/Minecraft;screen:Lnet/minecraft/client/gui/screens/Screen;", shift = At.Shift.BEFORE, ordinal = 0), method = "setScreen(Lnet/minecraft/client/gui/screens/Screen;)V")
    public void vivecraft$onOpenScreen(Screen pGuiScreen, CallbackInfo info) {
        GuiHandler.onScreenChanged(this.screen, pGuiScreen, true);
    }

    @Inject(at = @At("TAIL"), method = "setOverlay")
    public void vivecraft$onOverlaySet(Overlay overlay, CallbackInfo ci) {
        GuiHandler.onScreenChanged(this.screen, this.screen, true);
    }

    @Inject(method = "setScreen", at = @At("HEAD"))
    public void vivecraft$onCloseScreen(Screen screen, CallbackInfo info) {
        if (screen == null) {
            GuiHandler.guiAppearOverBlockActive = false;
        }
    }

    @Override
    @Unique
    public void vivecraft$notifyMirror(String text, boolean clear, int lengthMs) {
        this.vivecraft$mirroNotifyStart = System.currentTimeMillis();
        this.vivecraft$mirroNotifyLen = lengthMs;
        this.vivecraft$mirrorNotifyText = text;
        this.vivecraft$mirrorNotifyClear = clear;
    }

    @Unique
    private void vivecraft$drawNotifyMirror() {
        if (System.currentTimeMillis() < this.vivecraft$mirroNotifyStart + this.vivecraft$mirroNotifyLen) {
            int screenX = ((WindowExtension) (Object) this.window).vivecraft$getActualScreenWidth();
            int screenY = ((WindowExtension) (Object) this.window).vivecraft$getActualScreenHeight();
            RenderSystem.viewport(0, 0, screenX, screenY);
            Matrix4f matrix4f = new Matrix4f().setOrtho(0.0F, (float) screenX,
                screenY, 0.0F, 1000.0F, 3000.0F);
            RenderSystem.setProjectionMatrix(matrix4f, VertexSorting.ORTHOGRAPHIC_Z);
            RenderSystem.getModelViewStack().pushPose();
            RenderSystem.getModelViewStack().setIdentity();
            RenderSystem.getModelViewStack().translate(0, 0, -2000);
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setShaderFogStart(Float.MAX_VALUE);

            GuiGraphics guiGraphics = new GuiGraphics((Minecraft) (Object) this, renderBuffers.bufferSource());
            guiGraphics.pose().scale(3, 3, 3);
            RenderSystem.clear(256, ON_OSX);

            if (this.vivecraft$mirrorNotifyClear) {
                RenderSystem.clearColor(0, 0, 0, 0);
                RenderSystem.clear(16384, ON_OSX);
            }

            int i = this.window.getScreenWidth() / 22;
            ArrayList<String> arraylist = new ArrayList<>();

            if (this.vivecraft$mirrorNotifyText != null) {
                Utils.wordWrap(this.vivecraft$mirrorNotifyText, i, arraylist);
            }

            int j = 1;
            int k = 12;

            for (String s : arraylist) {
                guiGraphics.drawString(this.font, s, 1, j, 16777215);
                j += 12;
            }
            guiGraphics.flush();
            RenderSystem.getModelViewStack().popPose();
        }
    }

    @Unique
    private void vivecraft$switchVRState(boolean vrActive) {
        VRState.vrRunning = vrActive;
        if (vrActive) {
            if (player != null) {
                ClientDataHolderVR.getInstance().vrPlayer.snapRoomOriginToPlayerEntity(player, false, false);
            }
            // release mouse when switching to standing
            if (!ClientDataHolderVR.getInstance().vrSettings.seated) {
                InputConstants.grabOrReleaseMouse(window.getWindow(), GLFW.GLFW_CURSOR_NORMAL, mouseHandler.xpos(), mouseHandler.ypos());
            }
        } else {
            GuiHandler.guiPos_room = null;
            GuiHandler.guiRotation_room = null;
            GuiHandler.guiScale = 1.0F;

            if (player != null) {
                VRPlayersClient.getInstance().disableVR(player.getUUID());
            }
            if (gameRenderer != null) {
                gameRenderer.checkEntityPostEffect(this.options.getCameraType().isFirstPerson() ? this.getCameraEntity() : null);
            }
            // grab/release mouse
            if (screen != null || level == null) {
                mouseHandler.releaseMouse();
                InputConstants.grabOrReleaseMouse(window.getWindow(), GLFW.GLFW_CURSOR_NORMAL, mouseHandler.xpos(), mouseHandler.ypos());
            } else {
                mouseHandler.grabMouse();
                InputConstants.grabOrReleaseMouse(window.getWindow(), GLFW.GLFW_CURSOR_DISABLED, mouseHandler.xpos(), mouseHandler.ypos());
            }
        }
        var connection = this.getConnection();
        if (connection != null) {
            connection.send(ClientNetworking.createVRActivePacket(vrActive));
        }
        // reload sound manager, to toggle HRTF between VR and NONVR one
        if (!Minecraft.getInstance().getSoundManager().getAvailableSounds().isEmpty()) {
            Minecraft.getInstance().getSoundManager().reload();
        }
        resizeDisplay();
        window.updateVsync(options.enableVsync().get());
    }

    @Unique
    @Override
    public void vivecraft$drawProfiler() {
        if (this.fpsPieResults != null) {
            this.profiler.push("fpsPie");
            GuiGraphics guiGraphics = new GuiGraphics((Minecraft) (Object) this, renderBuffers.bufferSource());
            this.renderFpsMeter(guiGraphics, this.fpsPieResults);
            guiGraphics.flush();
            this.profiler.pop();
        }
    }

    @Unique
    private void vivecraft$checkGLError(String string) {
        // TODO optifine
        if (GlStateManager._getError() != 0) {
            System.err.println(string);
        }
    }

    @Unique
    private void vivecraft$copyToMirror() {
        if (ClientDataHolderVR.getInstance().vrSettings.displayMirrorMode == VRSettings.MirrorMode.OFF
            && ClientDataHolderVR.getInstance().vr.isHMDTracking()) {
            this.vivecraft$notifyMirror("Mirror is OFF", true, 1000);
        } else if (ClientDataHolderVR.getInstance().vrSettings.displayMirrorMode == VRSettings.MirrorMode.MIXED_REALITY) {
            if (VRShaders.depthMaskShader != null) {
                this.vivecraft$doMixedRealityMirror();
            } else {
                this.vivecraft$notifyMirror("Shader compile failed, see log", true, 10000);
            }
        } else if (ClientDataHolderVR.getInstance().vrSettings.displayMirrorMode == VRSettings.MirrorMode.DUAL &&
            (!ClientDataHolderVR.getInstance().vrSettings.displayMirrorUseScreenshotCamera ||
                !ClientDataHolderVR.getInstance().cameraTracker.isVisible())) {
            RenderTarget rendertarget = ClientDataHolderVR.getInstance().vrRenderer.getLeftEyeTarget();
            RenderTarget rendertarget1 = ClientDataHolderVR.getInstance().vrRenderer.getRightEyeTarget();

            int screenWidth = ((WindowExtension) (Object) this.window).vivecraft$getActualScreenWidth() / 2;
            int screenHeight = ((WindowExtension) (Object) this.window).vivecraft$getActualScreenHeight();
            if (rendertarget != null) {
                ((RenderTargetExtension) rendertarget).vivecraft$blitToScreen(0, screenWidth,
                    screenHeight, 0, true, 0.0F, 0.0F, false);
            }

            if (rendertarget1 != null) {
                ((RenderTargetExtension) rendertarget1).vivecraft$blitToScreen(screenWidth,
                    screenWidth, screenHeight, 0, true, 0.0F, 0.0F, false);
            }
        } else {
            float xcrop = 0.0F;
            float ycrop = 0.0F;
            boolean ar = false;
            RenderTarget source = ClientDataHolderVR.getInstance().vrRenderer.getLeftEyeTarget();

            if (ClientDataHolderVR.getInstance().vrSettings.displayMirrorUseScreenshotCamera && ClientDataHolderVR.getInstance().cameraTracker.isVisible()) {
                source = ClientDataHolderVR.getInstance().vrRenderer.cameraFramebuffer;
                ar = true;
            } else if (ClientDataHolderVR.getInstance().vrSettings.displayMirrorMode == VRSettings.MirrorMode.FIRST_PERSON) {
                source = ClientDataHolderVR.getInstance().vrRenderer.framebufferUndistorted;
            } else if (ClientDataHolderVR.getInstance().vrSettings.displayMirrorMode == VRSettings.MirrorMode.THIRD_PERSON) {
                source = ClientDataHolderVR.getInstance().vrRenderer.framebufferMR;
            } else if (ClientDataHolderVR.getInstance().vrSettings.displayMirrorMode == VRSettings.MirrorMode.GUI) {
                source = GuiHandler.guiFramebuffer;
            } else if (ClientDataHolderVR.getInstance().vrSettings.displayMirrorMode == VRSettings.MirrorMode.SINGLE
                || ClientDataHolderVR.getInstance().vrSettings.displayMirrorMode == VRSettings.MirrorMode.OFF) {
                if (!ClientDataHolderVR.getInstance().vrSettings.displayMirrorLeftEye) {
                    source = ClientDataHolderVR.getInstance().vrRenderer.getRightEyeTarget();
                }
            } else if (ClientDataHolderVR.getInstance().vrSettings.displayMirrorMode == VRSettings.MirrorMode.CROPPED) {
                if (!ClientDataHolderVR.getInstance().vrSettings.displayMirrorLeftEye) {
                    source = ClientDataHolderVR.getInstance().vrRenderer.getRightEyeTarget();
                }

                xcrop = ClientDataHolderVR.getInstance().vrSettings.mirrorCrop;
                ycrop = ClientDataHolderVR.getInstance().vrSettings.mirrorCrop;
                ar = true;
            }
            // Debug
            // source = GuiHandler.guiFramebuffer;
            // source = DataHolder.getInstance().vrRenderer.telescopeFramebufferR;
            //
            if (source != null) {
                ((RenderTargetExtension) source).vivecraft$blitToScreen(0, ((WindowExtension) (Object) this.window).vivecraft$getActualScreenWidth(),
                    ((WindowExtension) (Object) this.window).vivecraft$getActualScreenHeight(), 0, true, xcrop, ycrop, ar);
            }
        }
    }

    @Unique
    private void vivecraft$doMixedRealityMirror() {
        // set viewport to fullscreen, since it would be still on the one from the last pass
        RenderSystem.viewport(0, 0,
            ((WindowExtension) (Object) this.window).vivecraft$getActualScreenWidth(),
            ((WindowExtension) (Object) this.window).vivecraft$getActualScreenHeight());

        Vec3 camPlayer = ClientDataHolderVR.getInstance().vrPlayer.vrdata_room_pre.getHeadPivot()
            .subtract(ClientDataHolderVR.getInstance().vrPlayer.vrdata_room_pre.getEye(RenderPass.THIRD).getPosition());
        Matrix4f viewMatrix = ClientDataHolderVR.getInstance().vrPlayer.vrdata_room_pre.getEye(RenderPass.THIRD)
            .getMatrix().transposed().toMCMatrix();
        Vector3 cameraLook = ClientDataHolderVR.getInstance().vrPlayer.vrdata_room_pre.getEye(RenderPass.THIRD).getMatrix()
            .transform(Vector3.forward());

        // set uniforms
        VRShaders._DepthMask_projectionMatrix.set(((GameRendererExtension) this.gameRenderer).vivecraft$getThirdPassProjectionMatrix());
        VRShaders._DepthMask_viewMatrix.set(viewMatrix);

        VRShaders._DepthMask_hmdViewPosition.set((float) camPlayer.x, (float) camPlayer.y, (float) camPlayer.z);
        VRShaders._DepthMask_hmdPlaneNormal.set(-cameraLook.getX(), 0.0F, -cameraLook.getZ());

        boolean alphaMask = ClientDataHolderVR.getInstance().vrSettings.mixedRealityUnityLike
            && ClientDataHolderVR.getInstance().vrSettings.mixedRealityAlphaMask;
        if (!alphaMask) {
            VRShaders._DepthMask_keyColorUniform.set(
                (float) ClientDataHolderVR.getInstance().vrSettings.mixedRealityKeyColor.getRed() / 255.0F,
                (float) ClientDataHolderVR.getInstance().vrSettings.mixedRealityKeyColor.getGreen() / 255.0F,
                (float) ClientDataHolderVR.getInstance().vrSettings.mixedRealityKeyColor.getBlue() / 255.0F);
        } else {
            VRShaders._DepthMask_keyColorUniform.set(0F, 0F, 0F);
        }
        VRShaders._DepthMask_alphaModeUniform.set(alphaMask ? 1 : 0);

        VRShaders._DepthMask_firstPersonPassUniform.set(
            ClientDataHolderVR.getInstance().vrSettings.mixedRealityUnityLike ? 1 : 0);

        // bind textures
        RenderSystem.setShaderTexture(0, ClientDataHolderVR.getInstance().vrRenderer.framebufferMR.getColorTextureId());
        RenderSystem.setShaderTexture(1, ClientDataHolderVR.getInstance().vrRenderer.framebufferMR.getDepthTextureId());

        VRShaders.depthMaskShader.setSampler("thirdPersonColor", RenderSystem.getShaderTexture(0));
        VRShaders.depthMaskShader.setSampler("thirdPersonDepth", RenderSystem.getShaderTexture(1));

        if (ClientDataHolderVR.getInstance().vrSettings.mixedRealityUnityLike) {
            if (ClientDataHolderVR.getInstance().vrSettings.displayMirrorUseScreenshotCamera && ClientDataHolderVR.getInstance().cameraTracker.isVisible()) {
                RenderSystem.setShaderTexture(2,
                    ClientDataHolderVR.getInstance().vrRenderer.cameraFramebuffer.getColorTextureId());
            } else if (ClientDataHolderVR.getInstance().vrSettings.mixedRealityUndistorted) {
                RenderSystem.setShaderTexture(2,
                    ClientDataHolderVR.getInstance().vrRenderer.framebufferUndistorted.getColorTextureId());
            } else {
                if (ClientDataHolderVR.getInstance().vrSettings.displayMirrorLeftEye) {
                    RenderSystem.setShaderTexture(2,
                        ClientDataHolderVR.getInstance().vrRenderer.getLeftEyeTarget().getColorTextureId());
                } else {
                    RenderSystem.setShaderTexture(2,
                        ClientDataHolderVR.getInstance().vrRenderer.getRightEyeTarget().getColorTextureId());
                }
            }
            VRShaders.depthMaskShader.setSampler("firstPersonColor", RenderSystem.getShaderTexture(2));
        }

        VRShaders.depthMaskShader.apply();

        Tesselator tesselator = RenderSystem.renderThreadTesselator();
        BufferBuilder bufferbuilder = tesselator.getBuilder();
        bufferbuilder.begin(VertexFormat.Mode.QUADS, VRShaders.depthMaskShader.getVertexFormat());
        bufferbuilder.vertex(-1, -1, 0.0).uv(0, 0).endVertex();
        bufferbuilder.vertex(1, -1, 0.0).uv(2, 0).endVertex();
        bufferbuilder.vertex(1, 1, 0.0).uv(2, 2).endVertex();
        bufferbuilder.vertex(-1, 1, 0.0).uv(0, 2).endVertex();
        BufferUploader.draw(bufferbuilder.end());
        VRShaders.depthMaskShader.clear();

        ProgramManager.glUseProgram(0);
    }
}
