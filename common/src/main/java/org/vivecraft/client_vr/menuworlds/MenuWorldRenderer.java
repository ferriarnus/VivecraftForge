package org.vivecraft.client_vr.menuworlds;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import net.minecraft.Util;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.GraphicsStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.CubicSampler;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.tuple.Pair;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.vivecraft.client.Xplat;
import org.vivecraft.client.extensions.BufferBuilderExtension;
import org.vivecraft.client.utils.Utils;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.settings.VRSettings;
import org.vivecraft.mixin.client.renderer.RenderStateShardAccessor;
import org.vivecraft.mod_compat_vr.optifine.OptifineHelper;
import org.vivecraft.mod_compat_vr.sodium.SodiumHelper;

import java.io.InputStream;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class MenuWorldRenderer {
    private static final ResourceLocation MOON_LOCATION = new ResourceLocation("textures/environment/moon_phases.png");
    private static final ResourceLocation SUN_LOCATION = new ResourceLocation("textures/environment/sun.png");
    private static final ResourceLocation CLOUDS_LOCATION = new ResourceLocation("textures/environment/clouds.png");
    private static final ResourceLocation END_SKY_LOCATION = new ResourceLocation("textures/environment/end_sky.png");

    private static final ResourceLocation FORCEFIELD_LOCATION = new ResourceLocation("textures/misc/forcefield.png");

    private static final ResourceLocation RAIN_LOCATION = new ResourceLocation("textures/environment/rain.png");

    private static final ResourceLocation SNOW_LOCATION = new ResourceLocation("textures/environment/snow.png");

    private final Minecraft mc;
    private DimensionSpecialEffects dimensionInfo;
    private FakeBlockAccess blockAccess;
    private final DynamicTexture lightTexture;
    private final NativeImage lightPixels;
    private final ResourceLocation lightTextureLocation;
    private boolean lightmapUpdateNeeded;
    private float blockLightRedFlicker;
    private int waterVisionTime;

    public int ticks = 0;
    public long time = 1000;
    public boolean fastTime;
    private HashMap<RenderType, List<VertexBuffer>> vertexBuffers;
    private VertexBuffer starVBO;
    private VertexBuffer skyVBO;
    private VertexBuffer sky2VBO;
    private VertexBuffer cloudVBO;
    private int renderDistance;
    private int renderDistanceChunks;
    public MenuFogRenderer fogRenderer;
    public Set<TextureAtlasSprite> animatedSprites;
    private final Random rand;
    private boolean ready;
    private CloudStatus prevCloudsType;
    private int prevCloudX;
    private int prevCloudY;
    private int prevCloudZ;
    private Vec3 prevCloudColor;
    private boolean generateClouds = true;
    private int skyFlashTime;
    private float rainLevel;
    private float thunderLevel;

    private float worldRotation;

    private final float[] rainSizeX = new float[1024];
    private final float[] rainSizeZ = new float[1024];

    private CompletableFuture<FakeBlockAccess> getWorldTask;

    public int renderMaxTime = 40;
    public Vec3i segmentSize = new Vec3i(64, 64, 64);

    private boolean building = false;
    private long buildStartTime;
    private Map<Pair<RenderType, BlockPos>, BufferBuilder> bufferBuilders;
    private Map<Pair<RenderType, BlockPos>, BlockPos.MutableBlockPos> currentPositions;
    private Map<Pair<RenderType, BlockPos>, Integer> blockCounts;
    private Map<Pair<RenderType, BlockPos>, Long> renderTimes;
    private final List<CompletableFuture<Void>> builderFutures = new ArrayList<>();
    private final Queue<Thread> builderThreads = new ConcurrentLinkedQueue<>();
    private Throwable builderError;

    private static boolean FIRST_RENDER_DONE;

    public MenuWorldRenderer() {
        this.mc = Minecraft.getInstance();
        this.lightTexture = new DynamicTexture(16, 16, false);
        this.lightTextureLocation = this.mc.getTextureManager().register("vivecraft_light_map", this.lightTexture);
        this.lightPixels = this.lightTexture.getPixels();
        this.fogRenderer = new MenuFogRenderer(this);
        this.rand = new Random();
        this.rand.nextInt(); // toss some bits in the bin
    }

    public void init() {
        if (ClientDataHolderVR.getInstance().vrSettings.menuWorldSelection == VRSettings.MenuWorld.NONE) {
            VRSettings.LOGGER.info("Vivecraft: Main menu worlds disabled.");
            return;
        }

        try {
            VRSettings.LOGGER.info("Vivecraft: MenuWorlds: Initializing main menu world renderer...");
            loadRenderers();
            this.getWorldTask = CompletableFuture.supplyAsync(() -> {
                try (InputStream inputStream = MenuWorldDownloader.getRandomWorld()) {
                    VRSettings.LOGGER.info("Vivecraft: MenuWorlds: Loading world data...");
                    return inputStream != null ? MenuWorldExporter.loadWorld(inputStream) : null;
                } catch (Exception e) {
                    VRSettings.LOGGER.error("Vivecraft: Exception thrown when loading main menu world, falling back to old menu room.", e);
                    return null;
                }
            }, Util.backgroundExecutor());
        } catch (Exception e) {
            VRSettings.LOGGER.error("Vivecraft: Exception thrown when initializing main menu world renderer, falling back to old menu room.", e);
        }
    }

    public void checkTask() {
        if (this.getWorldTask == null || !this.getWorldTask.isDone()) {
            return;
        }

        try {
            FakeBlockAccess world = this.getWorldTask.get();
            if (world != null) {
                setWorld(world);
                prepare();
            } else {
                VRSettings.LOGGER.warn("Vivecraft: Failed to load any main menu world, falling back to old menu room");
            }
        } catch (Exception e) {
            VRSettings.LOGGER.error("Vivecraft: error starting menuworld building:", e);
        } finally {
            this.getWorldTask = null;
        }
    }

    public void render(PoseStack poseStack) {

        // temporarily disable fabulous to render the menu world
        GraphicsStatus current = this.mc.options.graphicsMode().get();
        if (current == GraphicsStatus.FABULOUS) {
            this.mc.options.graphicsMode().set(GraphicsStatus.FANCY);
        }

        turnOnLightLayer();

        poseStack.pushPose();

        //rotate World
        poseStack.mulPose(Axis.YP.rotationDegrees(this.worldRotation));

        // small offset to center on source block, and add the partial block offset, this shouldn't be too noticable on the fog
        poseStack.translate(-0.5, -this.blockAccess.getGround() + (int) this.blockAccess.getGround(), -0.5);

        // not sure why this needs to be rotated twice, but it works
        Vec3 offset = new Vec3(0.5, -this.blockAccess.getGround() + (int) this.blockAccess.getGround(), 0.5).yRot(this.worldRotation * 0.0174533f);
        Vec3 eyePosition = getEyePos().add(offset).yRot(-this.worldRotation * 0.0174533f);

        this.fogRenderer.levelFogColor();

        renderSky(poseStack, eyePosition);

        this.fogRenderer.setupFog(FogRenderer.FogMode.FOG_TERRAIN);

        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.enableBlend();

        Matrix4f modelView = poseStack.last().pose();
        Matrix4f projection = RenderSystem.getProjectionMatrix();

        RenderSystem.disableBlend();

        renderChunkLayer(RenderType.solid(), modelView, projection);
        renderChunkLayer(RenderType.cutoutMipped(), modelView, projection);
        renderChunkLayer(RenderType.cutout(), modelView, projection);

        RenderSystem.enableBlend();

        float cloudHeight = this.dimensionInfo.getCloudHeight();
        if (OptifineHelper.isOptifineLoaded()) {
            cloudHeight += (float) (OptifineHelper.getCloudHeight() * 128.0);
        }

        if (eyePosition.y + this.blockAccess.getGround() + this.blockAccess.getMinBuildHeight() < cloudHeight) {
            renderClouds(poseStack, eyePosition.x,
                eyePosition.y + this.blockAccess.getGround() + this.blockAccess.getMinBuildHeight(),
                eyePosition.z);
        }

        renderChunkLayer(RenderType.translucent(), modelView, projection);
        renderChunkLayer(RenderType.tripwire(), modelView, projection);

        if (eyePosition.y + this.blockAccess.getGround() + this.blockAccess.getMinBuildHeight() >= cloudHeight) {
            renderClouds(poseStack, eyePosition.x,
                eyePosition.y + this.blockAccess.getGround() + this.blockAccess.getMinBuildHeight(),
                eyePosition.z);
        }

        RenderSystem.depthMask(false);
        renderSnowAndRain(poseStack, eyePosition.x, 0, eyePosition.z);
        RenderSystem.depthMask(true);

        poseStack.popPose();
        turnOffLightLayer();
        this.mc.options.graphicsMode().set(current);
    }

    private void renderChunkLayer(RenderType layer, Matrix4f modelView, Matrix4f Projection) {
        List<VertexBuffer> buffers = this.vertexBuffers.get(layer);
        if (buffers.isEmpty()) {
            return;
        }

        layer.setupRenderState();
        ShaderInstance shaderInstance = RenderSystem.getShader();
        shaderInstance.apply();
        turnOnLightLayer();
        for (VertexBuffer vertexBuffer : buffers) {
            vertexBuffer.bind();
            vertexBuffer.drawWithShader(modelView, Projection, shaderInstance);
        }
        turnOffLightLayer();
    }

    public void prepare() {
        if (this.vertexBuffers == null && !this.building) {
            VRSettings.LOGGER.info("Vivecraft: MenuWorlds: Building geometry...");

            // random offset to make the player fly
            if (this.rand.nextInt(1000) == 0) {
                this.blockAccess.setGroundOffset(100);
            }
            this.fastTime = new Random().nextInt(10) == 0;

            this.animatedSprites = ConcurrentHashMap.newKeySet();
            this.blockCounts = new ConcurrentHashMap<>();
            this.renderTimes = new ConcurrentHashMap<>();

            try {
                this.vertexBuffers = new HashMap<>();
                this.bufferBuilders = new HashMap<>();
                this.currentPositions = new HashMap<>();

                for (RenderType layer : RenderType.chunkBufferLayers()) {
                    this.vertexBuffers.put(layer, new LinkedList<>());

                    for (int x = -this.blockAccess.getXSize() / 2; x < this.blockAccess.getXSize() / 2; x += this.segmentSize.getX()) {
                        for (int y = (int) -this.blockAccess.getGround(); y < this.blockAccess.getYSize() - (int) this.blockAccess.getGround(); y += this.segmentSize.getY()) {
                            for (int z = -this.blockAccess.getZSize() / 2; z < this.blockAccess.getZSize() / 2; z += this.segmentSize.getZ()) {
                                BlockPos pos = new BlockPos(x, y, z);
                                Pair<RenderType, BlockPos> pair = Pair.of(layer, pos);

                                BufferBuilder vertBuffer = new BufferBuilder(32768); // yields most efficient memory use for some reason
                                vertBuffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);

                                this.bufferBuilders.put(pair, vertBuffer);
                                this.currentPositions.put(pair, pos.mutable());
                            }
                        }
                    }
                }
            } catch (OutOfMemoryError e) {
                VRSettings.LOGGER.error("Vivecraft: OutOfMemoryError while building main menu world. Low system memory or 32-bit Java?", e);
                destroy();
                return;
            }

            this.buildStartTime = Utils.milliTime();
            this.building = true;
        }
    }

    public boolean isBuilding() {
        return this.building;
    }

    public void buildNext() {
        if (!this.builderFutures.stream().allMatch(CompletableFuture::isDone) || this.builderError != null) {
            return;
        }
        this.builderFutures.clear();

        if (this.currentPositions.entrySet().stream().allMatch(entry -> entry.getValue().getY() >= Math.min(this.segmentSize.getY() + entry.getKey().getRight().getY(), this.blockAccess.getYSize() - (int) this.blockAccess.getGround()))) {
            finishBuilding();
            return;
        }

        long startTime = Utils.milliTime();
        for (var pair : this.bufferBuilders.keySet()) {
            if (this.currentPositions.get(pair).getY() < Math.min(this.segmentSize.getY() + pair.getRight().getY(), this.blockAccess.getYSize() - (int) this.blockAccess.getGround())) {
                if (FIRST_RENDER_DONE || !SodiumHelper.isLoaded() || !SodiumHelper.hasIssuesWithParallelBlockBuilding()) {
                    // generate the data in parallel
                    this.builderFutures.add(CompletableFuture.runAsync(() -> buildGeometry(pair, startTime, this.renderMaxTime), Util.backgroundExecutor()));
                } else {
                    // generate first data in series to avoid weird class loading error
                    buildGeometry(pair, startTime, this.renderMaxTime);
                    if (this.blockCounts.getOrDefault(pair, 0) > 0) {
                        FIRST_RENDER_DONE = true;
                    }
                }
            }
        }

        CompletableFuture.allOf(this.builderFutures.toArray(new CompletableFuture[0])).thenRunAsync(this::handleError, Util.backgroundExecutor());
    }

    private void buildGeometry(Pair<RenderType, BlockPos> pair, long startTime, int maxTime) {
        if (Utils.milliTime() - startTime >= maxTime) {
            return;
        }

        RenderType layer = pair.getLeft();
        BlockPos offset = pair.getRight();
        this.builderThreads.add(Thread.currentThread());
        long realStartTime = Utils.milliTime();

        try {
            PoseStack thisPose = new PoseStack();
            int renderDistSquare = (this.renderDistance + 1) * (this.renderDistance + 1);
            BlockRenderDispatcher blockRenderer = this.mc.getBlockRenderer();
            BufferBuilder vertBuffer = this.bufferBuilders.get(pair);
            BlockPos.MutableBlockPos pos = this.currentPositions.get(pair);
            RandomSource randomSource = RandomSource.create();

            int count = 0;
            while (Utils.milliTime() - startTime < maxTime && pos.getY() < Math.min(this.segmentSize.getY() + offset.getY(), this.blockAccess.getYSize() - (int) this.blockAccess.getGround()) && this.building) {
                // only build blocks not obscured by fog
                if (Mth.abs(pos.getY()) <= this.renderDistance + 1 && Mth.lengthSquared(pos.getX(), pos.getZ()) <= renderDistSquare) {
                    BlockState state = this.blockAccess.getBlockState(pos);
                    if (state != null) {
                        FluidState fluidState = state.getFluidState();
                        if (!fluidState.isEmpty() && ItemBlockRenderTypes.getRenderLayer(fluidState) == layer) {
                            for (var sprite : Xplat.getFluidTextures(this.blockAccess, pos, fluidState)) {
                                if (sprite != null && sprite.contents().getUniqueFrames().sum() > 1) {
                                    this.animatedSprites.add(sprite);
                                }
                            }
                            blockRenderer.renderLiquid(pos, this.blockAccess, vertBuffer, state, new FluidStateWrapper(fluidState));
                            count++;
                        }

                        if (state.getRenderShape() != RenderShape.INVISIBLE && ItemBlockRenderTypes.getChunkRenderType(state) == layer) {
                            for (var quad : this.mc.getModelManager().getBlockModelShaper().getBlockModel(state).getQuads(state, null, randomSource)) {
                                if (quad.getSprite().contents().getUniqueFrames().sum() > 1) {
                                    this.animatedSprites.add(quad.getSprite());
                                }
                            }
                            thisPose.pushPose();
                            thisPose.translate(pos.getX(), pos.getY(), pos.getZ());
                            blockRenderer.renderBatched(state, pos, this.blockAccess, thisPose, vertBuffer, true, randomSource);
                            count++;
                            thisPose.popPose();
                        }
                    }
                }

                // iterate the position
                pos.setX(pos.getX() + 1);
                if (pos.getX() >= Math.min(this.segmentSize.getX() + offset.getX(), this.blockAccess.getXSize() / 2)) {
                    pos.setX(offset.getX());
                    pos.setZ(pos.getZ() + 1);
                    if (pos.getZ() >= Math.min(this.segmentSize.getZ() + offset.getZ(), this.blockAccess.getZSize() / 2)) {
                        pos.setZ(offset.getZ());
                        pos.setY(pos.getY() + 1);
                    }
                }
            }

            //VRSettings.logger.info("Vivecraft: MenuWorlds: Built segment of {} blocks in {} layer.", count, ((RenderStateShardAccessor) layer).getName());
            this.blockCounts.put(pair, this.blockCounts.getOrDefault(pair, 0) + count);
            this.renderTimes.put(pair, this.renderTimes.getOrDefault(pair, 0L) + (Utils.milliTime() - realStartTime));

            if (pos.getY() >= Math.min(this.segmentSize.getY() + offset.getY(), this.blockAccess.getYSize() - (int) this.blockAccess.getGround())) {
                VRSettings.LOGGER.debug("Vivecraft: MenuWorlds: Built {} blocks on {} layer at {},{},{} in {} ms",
                    this.blockCounts.get(pair),
                    ((RenderStateShardAccessor) layer).getName(),
                    offset.getX(), offset.getY(), offset.getZ(),
                    this.renderTimes.get(pair));
            }
        } catch (Throwable e) { // Only effective way of preventing crash on poop computers with low heap size
            this.builderError = e;
        } finally {
            this.builderThreads.remove(Thread.currentThread());
        }
    }

    private void finishBuilding() {
        this.building = false;

        // Sort buffers from nearest to furthest
        var entryList = new ArrayList<>(this.bufferBuilders.entrySet());
        entryList.sort(Comparator.comparing(entry -> entry.getKey().getRight(), (posA, posB) -> {
            Vec3i center = new Vec3i(this.segmentSize.getX() / 2, this.segmentSize.getY() / 2, this.segmentSize.getZ() / 2);
            double distA = posA.offset(center).distSqr(BlockPos.ZERO);
            double distB = posB.offset(center).distSqr(BlockPos.ZERO);
            return Double.compare(distA, distB);
        }));

        int totalMemory = 0, count = 0;
        for (var entry : entryList) {
            RenderType layer = entry.getKey().getLeft();
            BufferBuilder vertBuffer = entry.getValue();
            if (layer == RenderType.translucent()) {
                vertBuffer.setQuadSorting(VertexSorting.byDistance(0, Mth.frac(this.blockAccess.getGround()), 0));
            }
            BufferBuilder.RenderedBuffer renderedBuffer = vertBuffer.end();
            if (!renderedBuffer.isEmpty()) {
                uploadGeometry(layer, renderedBuffer);
                count++;
            }
            totalMemory += ((BufferBuilderExtension) vertBuffer).vivecraft$getBufferSize();
            ((BufferBuilderExtension) vertBuffer).vivecraft$freeBuffer();
        }

        this.bufferBuilders = null;
        this.currentPositions = null;
        this.ready = true;
        VRSettings.LOGGER.info("Vivecraft: MenuWorlds: Built {} blocks in {} ms ({} ms CPU time)",
            this.blockCounts.values().stream().reduce(Integer::sum).orElse(0),
            Utils.milliTime() - this.buildStartTime,
            this.renderTimes.values().stream().reduce(Long::sum).orElse(0L));
        VRSettings.LOGGER.info("Vivecraft: MenuWorlds: Used {} temporary buffers ({} MiB), uploaded {} non-empty buffers",
            entryList.size(),
            totalMemory / 1048576,
            count);
    }

    public boolean isOnBuilderThread() {
        return this.builderThreads.contains(Thread.currentThread());
    }

    private void handleError() {
        if (this.builderError == null) {
            return;
        }
        if (this.builderError instanceof OutOfMemoryError || this.builderError.getCause() instanceof OutOfMemoryError) {
            VRSettings.LOGGER.error("Vivecraft: OutOfMemoryError while building main menu world. Low system memory or 32-bit Java?", this.builderError);
        } else {
            VRSettings.LOGGER.error("Vivecraft: Exception thrown when building main menu world, falling back to old menu room.:", this.builderError);
        }
        destroy();
        setWorld(null);
        this.builderError = null;
    }

    private void uploadGeometry(RenderType layer, BufferBuilder.RenderedBuffer renderedBuffer) {
        VertexBuffer buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
        buffer.bind();
        buffer.upload(renderedBuffer);
        VertexBuffer.unbind();
        this.vertexBuffers.get(layer).add(buffer);
    }

    public void cancelBuilding() {
        this.building = false;
        this.builderFutures.forEach(CompletableFuture::join);
        this.builderFutures.clear();
        if (this.bufferBuilders != null) {
            for (BufferBuilder vertBuffer : this.bufferBuilders.values()) {
                ((BufferBuilderExtension) vertBuffer).vivecraft$freeBuffer();
            }
            this.bufferBuilders = null;
        }
        this.currentPositions = null;
    }

    public void destroy() {
        cancelBuilding();
        if (this.vertexBuffers != null) {
            for (List<VertexBuffer> buffers : this.vertexBuffers.values()) {
                for (VertexBuffer vertexBuffer : buffers) {
                    if (vertexBuffer != null) {
                        vertexBuffer.close();
                    }
                }
            }
            this.vertexBuffers = null;
        }
        this.animatedSprites = null;
        this.ready = false;
    }

    public void completeDestroy() {
        destroy();
        if (this.starVBO != null) {
            this.starVBO.close();
        }
        if (this.skyVBO != null) {
            this.skyVBO.close();
        }
        if (this.sky2VBO != null) {
            this.sky2VBO.close();
        }
        if (this.cloudVBO != null) {
            this.cloudVBO.close();
        }
        this.ready = false;
    }

    public void tick() {
        this.ticks++;
        this.updateTorchFlicker();

        if (this.areEyesInFluid(FluidTags.WATER)) {
            int i = 1; //this.isSpectator() ? 10 : 1;
            this.waterVisionTime = Mth.clamp(this.waterVisionTime + i, 0, 600);
        } else if (this.waterVisionTime > 0) {
            this.areEyesInFluid(FluidTags.WATER);
            this.waterVisionTime = Mth.clamp(this.waterVisionTime - 10, 0, 600);
        }
        if (SodiumHelper.isLoaded() && this.animatedSprites != null) {
            for (TextureAtlasSprite sprite : this.animatedSprites) {
                SodiumHelper.markTextureAsActive(sprite);
            }
        }
        if (OptifineHelper.isOptifineLoaded()) {
            for (TextureAtlasSprite sprite : this.animatedSprites) {
                OptifineHelper.markTextureAsActive(sprite);
            }
        }
    }

    public FakeBlockAccess getLevel() {
        return this.blockAccess;
    }

    public void setWorld(FakeBlockAccess blockAccess) {
        this.blockAccess = blockAccess;
        if (blockAccess != null) {
            this.dimensionInfo = blockAccess.getDimensionReaderInfo();
            this.lightmapUpdateNeeded = true;
            this.renderDistance = blockAccess.getXSize() / 2;
            this.renderDistanceChunks = this.renderDistance / 16;
            this.rainLevel = blockAccess.getRain() ? 1.0F : 0.0F;
            this.thunderLevel = blockAccess.getThunder() ? 1.0F : 0.0F;

            this.worldRotation = blockAccess.getRotation();
        }
    }

    public void loadRenderers() {
        for (int i = 0; i < 32; ++i) {
            for (int j = 0; j < 32; ++j) {
                float f = j - 16;
                float g = i - 16;
                float h = Mth.sqrt(f * f + g * g);
                this.rainSizeX[i << 5 | j] = -g / h;
                this.rainSizeZ[i << 5 | j] = f / h;
            }
        }

        this.generateSky();
        this.generateSky2();
        this.generateStars();
    }

    public boolean isReady() {
        return this.ready;
    }

    // VanillaFix support
    @SuppressWarnings("unchecked")
    private void copyVisibleTextures() {
		/*if (Reflector.VFTemporaryStorage.exists()) {
			if (Reflector.VFTemporaryStorage_texturesUsed.exists()) {
				visibleTextures.addAll((Collection<TextureAtlasSprite>)Reflector.getFieldValue(Reflector.VFTemporaryStorage_texturesUsed));
			} else if (Reflector.VFTextureAtlasSprite_needsAnimationUpdate.exists()) {
				for (TextureAtlasSprite texture : (Collection<TextureAtlasSprite>)MCReflection.TextureMap_listAnimatedSprites.get(mc.getTextureMapBlocks())) {
					if (Reflector.callBoolean(texture, Reflector.VFTextureAtlasSprite_needsAnimationUpdate))
						visibleTextures.add(texture);
				}
			}
		}*/
    }

    @SuppressWarnings("unchecked")
    public void pushVisibleTextures() {
		/*if (Reflector.VFTemporaryStorage.exists()) {
			if (Reflector.VFTemporaryStorage_texturesUsed.exists()) {
				Collection<TextureAtlasSprite> coll = (Collection<TextureAtlasSprite>)Reflector.getFieldValue(Reflector.VFTemporaryStorage_texturesUsed);
				coll.addAll(visibleTextures);
			} else if (Reflector.VFTextureAtlasSprite_markNeedsAnimationUpdate.exists()) {
				for (TextureAtlasSprite texture : visibleTextures)
					Reflector.call(texture, Reflector.VFTextureAtlasSprite_markNeedsAnimationUpdate);
			}
		}*/
    }
    // End VanillaFix support

    public void renderSky(PoseStack poseStack, Vec3 position) {
        if (this.dimensionInfo.skyType() == DimensionSpecialEffects.SkyType.END) {
            this.renderEndSky(poseStack);
        } else if (this.dimensionInfo.skyType() == DimensionSpecialEffects.SkyType.NORMAL) {
            RenderSystem.setShader(GameRenderer::getPositionShader);
            this.fogRenderer.setupFog(FogRenderer.FogMode.FOG_SKY);
            ShaderInstance skyShader = RenderSystem.getShader();
            //RenderSystem.disableTexture();

            Vec3 skyColor = this.getSkyColor(position);

            if (OptifineHelper.isOptifineLoaded()) {
                skyColor = OptifineHelper.getCustomSkyColor(skyColor, this.blockAccess, position.x, position.y, position.z);
            }

            this.fogRenderer.levelFogColor();

            BufferBuilder bufferBuilder = Tesselator.getInstance().getBuilder();
            RenderSystem.depthMask(false);
            RenderSystem.setShaderColor((float) skyColor.x, (float) skyColor.y, (float) skyColor.z, 1.0f);


            if (!OptifineHelper.isOptifineLoaded() || OptifineHelper.isSkyEnabled()) {
                this.skyVBO.bind();
                this.skyVBO.drawWithShader(poseStack.last().pose(), RenderSystem.getProjectionMatrix(), skyShader);
                VertexBuffer.unbind();
            }

            RenderSystem.enableBlend();
            RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);

            float[] sunriseColor = this.dimensionInfo.getSunriseColor(this.getTimeOfDay(), 0); // calcSunriseSunsetColors

            if (sunriseColor != null && (!OptifineHelper.isOptifineLoaded() || OptifineHelper.isSunMoonEnabled())) {
                //RenderSystem.disableTexture();
                RenderSystem.setShader(GameRenderer::getPositionColorShader);
                RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
                poseStack.pushPose();

                poseStack.mulPose(Axis.XP.rotationDegrees(90.0f));
                poseStack.mulPose(Axis.ZP.rotationDegrees(Mth.sin(this.getSunAngle()) < 0.0f ? 180.0f : 0.0f));
                poseStack.mulPose(Axis.ZP.rotationDegrees(90.0f));

                Matrix4f modelView = poseStack.last().pose();
                bufferBuilder.begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);
                bufferBuilder
                    .vertex(modelView, 0.0f, 100.0f, 0.0f)
                    .color(sunriseColor[0], sunriseColor[1], sunriseColor[2], sunriseColor[3])
                    .endVertex();

                for (int j = 0; j <= 16; ++j) {
                    float f6 = (float) j * ((float) Math.PI * 2F) / 16.0F;
                    float f7 = Mth.sin(f6);
                    float f8 = Mth.cos(f6);
                    bufferBuilder
                        .vertex(modelView, f7 * 120.0F, f8 * 120.0F, -f8 * 40.0F * sunriseColor[3])
                        .color(sunriseColor[0], sunriseColor[1], sunriseColor[2], 0.0F)
                        .endVertex();
                }

                BufferUploader.drawWithShader(bufferBuilder.end());
                poseStack.popPose();
            }

            //RenderSystem.enableTexture();

            RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
            poseStack.pushPose();

            float f10 = 1.0F - getRainLevel();
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, f10);
            poseStack.mulPose(Axis.YP.rotationDegrees(-90.0f));
            Matrix4f modelView = poseStack.last().pose();

            //if (OptifineHelper.isOptifineLoaded()) {
            // needs a full Level
            //CustomSky.renderSky(this.world, poseStack, Minecraft.getInstance().getFrameTime());
            //}

            poseStack.mulPose(Axis.XP.rotationDegrees(this.getTimeOfDay() * 360.0f));

            float size = 30.0F;
            if (!OptifineHelper.isOptifineLoaded() || OptifineHelper.isSunMoonEnabled()) {
                RenderSystem.setShader(GameRenderer::getPositionTexShader);
                RenderSystem.setShaderTexture(0, SUN_LOCATION);
                bufferBuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
                bufferBuilder.vertex(modelView, -size, 100.0F, -size).uv(0.0F, 0.0F).endVertex();
                bufferBuilder.vertex(modelView, size, 100.0F, -size).uv(1.0F, 0.0F).endVertex();
                bufferBuilder.vertex(modelView, size, 100.0F, size).uv(1.0F, 1.0F).endVertex();
                bufferBuilder.vertex(modelView, -size, 100.0F, size).uv(0.0F, 1.0F).endVertex();
                BufferUploader.drawWithShader(bufferBuilder.end());
            }

            size = 20.0F;
            if (!OptifineHelper.isOptifineLoaded() || OptifineHelper.isSunMoonEnabled()) {
                RenderSystem.setShaderTexture(0, MOON_LOCATION);
                int moonPhase = this.getMoonPhase();
                int l = moonPhase % 4;
                int i1 = moonPhase / 4 % 2;
                float u0 = (float) (l) / 4.0F;
                float v0 = (float) (i1) / 2.0F;
                float u1 = (float) (l + 1) / 4.0F;
                float v1 = (float) (i1 + 1) / 2.0F;
                bufferBuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
                bufferBuilder.vertex(modelView, -size, -100.0f, size).uv(u0, v1).endVertex();
                bufferBuilder.vertex(modelView, size, -100.0f, size).uv(u1, v1).endVertex();
                bufferBuilder.vertex(modelView, size, -100.0f, -size).uv(u1, v0).endVertex();
                bufferBuilder.vertex(modelView, -size, -100.0f, -size).uv(u0, v0).endVertex();
                BufferUploader.drawWithShader(bufferBuilder.end());
            }

            //GlStateManager.disableTexture();

            float starBrightness = this.getStarBrightness() * f10;

            if (starBrightness > 0.0F && (!OptifineHelper.isOptifineLoaded() || OptifineHelper.isStarsEnabled()) /*&& !CustomSky.hasSkyLayers(this.world)*/) {
                RenderSystem.setShaderColor(starBrightness, starBrightness, starBrightness, starBrightness);
                this.fogRenderer.setupNoFog();
                this.starVBO.bind();
                this.starVBO.drawWithShader(poseStack.last().pose(), RenderSystem.getProjectionMatrix(), GameRenderer.getPositionShader());
                VertexBuffer.unbind();
                this.fogRenderer.setupFog(FogRenderer.FogMode.FOG_SKY);
            }

            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            RenderSystem.disableBlend();
            RenderSystem.defaultBlendFunc();

            poseStack.popPose();
            //RenderSystem.disableTexture();

            double horizonDistance = position.y - this.blockAccess.getHorizon();

            if (horizonDistance < 0.0D) {
                RenderSystem.setShaderColor(0.0f, 0.0f, 0.0f, 1.0f);
                poseStack.pushPose();
                poseStack.translate(0.0f, 12.0f, 0.0f);
                this.sky2VBO.bind();
                this.sky2VBO.drawWithShader(poseStack.last().pose(), RenderSystem.getProjectionMatrix(), skyShader);
                VertexBuffer.unbind();
                poseStack.popPose();
            }

            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            RenderSystem.depthMask(true);
        }
    }

    private void renderEndSky(PoseStack poseStack) {
        if (!OptifineHelper.isOptifineLoaded() || OptifineHelper.isSkyEnabled()) {
            RenderSystem.enableBlend();
            RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
            RenderSystem.depthMask(false);
            RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
            RenderSystem.setShaderTexture(0, END_SKY_LOCATION);
            Tesselator tesselator = Tesselator.getInstance();
            BufferBuilder bufferBuilder = tesselator.getBuilder();

            for (int i = 0; i < 6; ++i) {
                poseStack.pushPose();
                switch (i) {
                    case 1 -> poseStack.mulPose(Axis.XP.rotationDegrees(90.0f));
                    case 2 -> poseStack.mulPose(Axis.XP.rotationDegrees(-90.0f));
                    case 3 -> poseStack.mulPose(Axis.XP.rotationDegrees(180.0f));
                    case 4 -> poseStack.mulPose(Axis.ZP.rotationDegrees(90.0f));
                    case 5 -> poseStack.mulPose(Axis.ZP.rotationDegrees(-90.0f));
                }

                Matrix4f modelView = poseStack.last().pose();
                bufferBuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

                int r = 40;
                int g = 40;
                int b = 40;

                if (OptifineHelper.isOptifineLoaded() && OptifineHelper.isCustomColors()) {
                    Vec3 newSkyColor = new Vec3((double) r / 255.0D, (double) g / 255.0D, (double) b / 255.0D);
                    newSkyColor = OptifineHelper.getCustomSkyColorEnd(newSkyColor);
                    r = (int) (newSkyColor.x * 255.0D);
                    g = (int) (newSkyColor.y * 255.0D);
                    b = (int) (newSkyColor.z * 255.0D);
                }
                bufferBuilder.vertex(modelView, -100.0f, -100.0f, -100.0f)
                    .uv(0.0f, 0.0f).color(r, g, b, 255).endVertex();
                bufferBuilder.vertex(modelView, -100.0f, -100.0f, 100.0f)
                    .uv(0.0f, 16.0f).color(r, g, b, 255).endVertex();
                bufferBuilder.vertex(modelView, 100.0f, -100.0f, 100.0f)
                    .uv(16.0f, 16.0f).color(r, g, b, 255).endVertex();
                bufferBuilder.vertex(modelView, 100.0f, -100.0f, -100.0f)
                    .uv(16.0f, 0.0f).color(r, g, b, 255).endVertex();
                tesselator.end();
                poseStack.popPose();
            }

            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
        }
    }

    public void renderClouds(PoseStack poseStack, double x, double y, double z) {
        float cloudHeight = this.dimensionInfo.getCloudHeight();

        if (!Float.isNaN(cloudHeight) && this.mc.options.getCloudsType() != CloudStatus.OFF) {
            // setup clouds

            RenderSystem.disableCull();
            RenderSystem.enableBlend();
            RenderSystem.enableDepthTest();
            RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            RenderSystem.depthMask(true);

            float cloudSizeXZ = 12.0f;
            float cloudSizeY = 4.0f;
            double cloudOffset = ((float) this.ticks + this.mc.getFrameTime()) * 0.03f;
            double cloudX = (x + cloudOffset) / 12.0;
            double cloudY = cloudHeight - y + 0.33;
            if (OptifineHelper.isOptifineLoaded()) {
                cloudY = cloudY + OptifineHelper.getCloudHeight() * 128.0;
            }

            double cloudZ = z / 12.0 + 0.33;
            cloudX -= Mth.floor(cloudX / 2048.0) * 2048;
            cloudZ -= Mth.floor(cloudZ / 2048.0) * 2048;
            float cloudXfract = (float) (cloudX - (double) Mth.floor(cloudX));
            float cloudYfract = (float) (cloudY / 4.0 - (double) Mth.floor(cloudY / 4.0)) * 4.0f;
            float cloudZfract = (float) (cloudZ - (double) Mth.floor(cloudZ));

            Vec3 cloudColor = this.getCloudColour();
            int cloudXfloor = (int) Math.floor(cloudX);
            int cloudYfloor = (int) Math.floor(cloudY / 4.0);
            int cloudZfloor = (int) Math.floor(cloudZ);
            if (cloudXfloor != this.prevCloudX ||
                cloudYfloor != this.prevCloudY ||
                cloudZfloor != this.prevCloudZ ||
                this.mc.options.getCloudsType() != this.prevCloudsType ||
                this.prevCloudColor.distanceToSqr(cloudColor) > 2.0E-4) {
                this.prevCloudX = cloudXfloor;
                this.prevCloudY = cloudYfloor;
                this.prevCloudZ = cloudZfloor;
                this.prevCloudColor = cloudColor;
                this.prevCloudsType = this.mc.options.getCloudsType();
                this.generateClouds = true;
            }
            if (this.generateClouds) {
                this.generateClouds = false;
                BufferBuilder bufferBuilder = Tesselator.getInstance().getBuilder();
                if (this.cloudVBO != null) {
                    this.cloudVBO.close();
                }
                this.cloudVBO = new VertexBuffer(VertexBuffer.Usage.STATIC);
                BufferBuilder.RenderedBuffer renderedBuffer = this.buildClouds(bufferBuilder, cloudX, cloudY, cloudZ, cloudColor);
                this.cloudVBO.bind();
                this.cloudVBO.upload(renderedBuffer);
                VertexBuffer.unbind();
            }

            // render
            RenderSystem.setShader(GameRenderer::getPositionTexColorNormalShader);
            RenderSystem.setShaderTexture(0, CLOUDS_LOCATION);
            this.fogRenderer.levelFogColor();
            poseStack.pushPose();
            poseStack.scale(12.0f, 1.0f, 12.0f);
            poseStack.translate(-cloudXfract, cloudYfract, -cloudZfract);
            if (this.cloudVBO != null) {
                this.cloudVBO.bind();
                // probably rendered twice, so only the front faces are there?
                for (int w = this.prevCloudsType == CloudStatus.FANCY ? 0 : 1; w < 2; ++w) {
                    if (w == 0) {
                        RenderSystem.colorMask(false, false, false, false);
                    } else {
                        RenderSystem.colorMask(true, true, true, true);
                    }
                    this.cloudVBO.drawWithShader(poseStack.last().pose(), RenderSystem.getProjectionMatrix(), RenderSystem.getShader());
                }
                VertexBuffer.unbind();
            }
            poseStack.popPose();
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
            RenderSystem.defaultBlendFunc();
        }
    }

    private BufferBuilder.RenderedBuffer buildClouds(BufferBuilder bufferBuilder, double cloudX, double cloudY, double cloudZ, Vec3 cloudColor) {
        final float texRes = 1.0F / 256.0F;

        float l = (float) Mth.floor(cloudX) * texRes;
        float m = (float) Mth.floor(cloudZ) * texRes;
        float redTop = (float) cloudColor.x;
        float greenTop = (float) cloudColor.y;
        float blueTop = (float) cloudColor.z;
        float redX = redTop * 0.9f;
        float greenX = greenTop * 0.9f;
        float blueX = blueTop * 0.9f;
        float redBottom = redTop * 0.7f;
        float greenBottom = greenTop * 0.7f;
        float blueBottom = blueTop * 0.7f;
        float redZ = redTop * 0.8f;
        float greenZ = greenTop * 0.8f;
        float blueZ = blueTop * 0.8f;
        RenderSystem.setShader(GameRenderer::getPositionTexColorNormalShader);
        bufferBuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL);
        float z = (float) Math.floor(cloudY / 4.0) * 4.0f;
        if (this.prevCloudsType == CloudStatus.FANCY) {
            for (int aa = -3; aa <= 4; aa++) {
                for (int ab = -3; ab <= 4; ab++) {
                    int ae;
                    float ac = aa * 8;
                    float ad = ab * 8;
                    if (z > -5.0f) {
                        bufferBuilder.vertex(ac + 0.0f, z + 0.0f, ad + 8.0f)
                            .uv((ac + 0.0f) * texRes + l, (ad + 8.0f) * texRes + m)
                            .color(redBottom, greenBottom, blueBottom, 0.8f)
                            .normal(0.0f, -1.0f, 0.0f).endVertex();
                        bufferBuilder.vertex(ac + 8.0f, z + 0.0f, ad + 8.0f)
                            .uv((ac + 8.0f) * texRes + l, (ad + 8.0f) * texRes + m)
                            .color(redBottom, greenBottom, blueBottom, 0.8f)
                            .normal(0.0f, -1.0f, 0.0f).endVertex();
                        bufferBuilder.vertex(ac + 8.0f, z + 0.0f, ad + 0.0f)
                            .uv((ac + 8.0f) * texRes + l, (ad + 0.0f) * texRes + m)
                            .color(redBottom, greenBottom, blueBottom, 0.8f)
                            .normal(0.0f, -1.0f, 0.0f).endVertex();
                        bufferBuilder.vertex(ac + 0.0f, z + 0.0f, ad + 0.0f)
                            .uv((ac + 0.0f) * texRes + l, (ad + 0.0f) * texRes + m)
                            .color(redBottom, greenBottom, blueBottom, 0.8f)
                            .normal(0.0f, -1.0f, 0.0f).endVertex();
                    }
                    if (z <= 5.0f) {
                        bufferBuilder.vertex(ac + 0.0f, z + 4.0f - 9.765625E-4f, ad + 8.0f)
                            .uv((ac + 0.0f) * texRes + l, (ad + 8.0f) * texRes + m)
                            .color(redTop, greenTop, blueTop, 0.8f)
                            .normal(0.0f, 1.0f, 0.0f).endVertex();
                        bufferBuilder.vertex(ac + 8.0f, z + 4.0f - 9.765625E-4f, ad + 8.0f)
                            .uv((ac + 8.0f) * texRes + l, (ad + 8.0f) * texRes + m)
                            .color(redTop, greenTop, blueTop, 0.8f)
                            .normal(0.0f, 1.0f, 0.0f).endVertex();
                        bufferBuilder.vertex(ac + 8.0f, z + 4.0f - 9.765625E-4f, ad + 0.0f)
                            .uv((ac + 8.0f) * texRes + l, (ad + 0.0f) * texRes + m)
                            .color(redTop, greenTop, blueTop, 0.8f)
                            .normal(0.0f, 1.0f, 0.0f).endVertex();
                        bufferBuilder.vertex(ac + 0.0f, z + 4.0f - 9.765625E-4f, ad + 0.0f)
                            .uv((ac + 0.0f) * texRes + l, (ad + 0.0f) * texRes + m)
                            .color(redTop, greenTop, blueTop, 0.8f)
                            .normal(0.0f, 1.0f, 0.0f).endVertex();
                    }
                    if (aa > -1) {
                        for (ae = 0; ae < 8; ae++) {
                            bufferBuilder.vertex(ac + (float) ae + 0.0f, z + 0.0f, ad + 8.0f)
                                .uv((ac + (float) ae + 0.5f) * texRes + l, (ad + 8.0f) * texRes + m)
                                .color(redX, greenX, blueX, 0.8f)
                                .normal(-1.0f, 0.0f, 0.0f).endVertex();
                            bufferBuilder.vertex(ac + (float) ae + 0.0f, z + 4.0f, ad + 8.0f)
                                .uv((ac + (float) ae + 0.5f) * texRes + l, (ad + 8.0f) * texRes + m)
                                .color(redX, greenX, blueX, 0.8f)
                                .normal(-1.0f, 0.0f, 0.0f).endVertex();
                            bufferBuilder.vertex(ac + (float) ae + 0.0f, z + 4.0f, ad + 0.0f)
                                .uv((ac + (float) ae + 0.5f) * texRes + l, (ad + 0.0f) * texRes + m)
                                .color(redX, greenX, blueX, 0.8f)
                                .normal(-1.0f, 0.0f, 0.0f).endVertex();
                            bufferBuilder.vertex(ac + (float) ae + 0.0f, z + 0.0f, ad + 0.0f)
                                .uv((ac + (float) ae + 0.5f) * texRes + l, (ad + 0.0f) * texRes + m)
                                .color(redX, greenX, blueX, 0.8f)
                                .normal(-1.0f, 0.0f, 0.0f).endVertex();
                        }
                    }
                    if (aa <= 1) {
                        for (ae = 0; ae < 8; ae++) {
                            bufferBuilder.vertex(ac + (float) ae + 1.0f - 9.765625E-4f, z + 0.0f, ad + 8.0f)
                                .uv((ac + (float) ae + 0.5f) * texRes + l, (ad + 8.0f) * texRes + m)
                                .color(redX, greenX, blueX, 0.8f)
                                .normal(1.0f, 0.0f, 0.0f).endVertex();
                            bufferBuilder.vertex(ac + (float) ae + 1.0f - 9.765625E-4f, z + 4.0f, ad + 8.0f)
                                .uv((ac + (float) ae + 0.5f) * texRes + l, (ad + 8.0f) * texRes + m)
                                .color(redX, greenX, blueX, 0.8f)
                                .normal(1.0f, 0.0f, 0.0f).endVertex();
                            bufferBuilder.vertex(ac + (float) ae + 1.0f - 9.765625E-4f, z + 4.0f, ad + 0.0f)
                                .uv((ac + (float) ae + 0.5f) * texRes + l, (ad + 0.0f) * texRes + m)
                                .color(redX, greenX, blueX, 0.8f)
                                .normal(1.0f, 0.0f, 0.0f).endVertex();
                            bufferBuilder.vertex(ac + (float) ae + 1.0f - 9.765625E-4f, z + 0.0f, ad + 0.0f)
                                .uv((ac + (float) ae + 0.5f) * texRes + l, (ad + 0.0f) * texRes + m)
                                .color(redX, greenX, blueX, 0.8f)
                                .normal(1.0f, 0.0f, 0.0f).endVertex();
                        }
                    }
                    if (ab > -1) {
                        for (ae = 0; ae < 8; ae++) {
                            bufferBuilder.vertex(ac + 0.0f, z + 4.0f, ad + (float) ae + 0.0f)
                                .uv((ac + 0.0f) * texRes + l, (ad + (float) ae + 0.5f) * texRes + m)
                                .color(redZ, greenZ, blueZ, 0.8f)
                                .normal(0.0f, 0.0f, -1.0f).endVertex();
                            bufferBuilder.vertex(ac + 8.0f, z + 4.0f, ad + (float) ae + 0.0f)
                                .uv((ac + 8.0f) * texRes + l, (ad + (float) ae + 0.5f) * texRes + m)
                                .color(redZ, greenZ, blueZ, 0.8f)
                                .normal(0.0f, 0.0f, -1.0f).endVertex();
                            bufferBuilder.vertex(ac + 8.0f, z + 0.0f, ad + (float) ae + 0.0f)
                                .uv((ac + 8.0f) * texRes + l, (ad + (float) ae + 0.5f) * texRes + m)
                                .color(redZ, greenZ, blueZ, 0.8f)
                                .normal(0.0f, 0.0f, -1.0f).endVertex();
                            bufferBuilder.vertex(ac + 0.0f, z + 0.0f, ad + (float) ae + 0.0f)
                                .uv((ac + 0.0f) * texRes + l, (ad + (float) ae + 0.5f) * texRes + m)
                                .color(redZ, greenZ, blueZ, 0.8f)
                                .normal(0.0f, 0.0f, -1.0f).endVertex();
                        }
                    }
                    if (ab > 1) {
                        continue;
                    }
                    for (ae = 0; ae < 8; ae++) {
                        bufferBuilder.vertex(ac + 0.0f, z + 4.0f, ad + (float) ae + 1.0f - 9.765625E-4f)
                            .uv((ac + 0.0f) * texRes + l, (ad + (float) ae + 0.5f) * texRes + m)
                            .color(redZ, greenZ, blueZ, 0.8f)
                            .normal(0.0f, 0.0f, 1.0f).endVertex();
                        bufferBuilder.vertex(ac + 8.0f, z + 4.0f, ad + (float) ae + 1.0f - 9.765625E-4f)
                            .uv((ac + 8.0f) * texRes + l, (ad + (float) ae + 0.5f) * texRes + m)
                            .color(redZ, greenZ, blueZ, 0.8f)
                            .normal(0.0f, 0.0f, 1.0f).endVertex();
                        bufferBuilder.vertex(ac + 8.0f, z + 0.0f, ad + (float) ae + 1.0f - 9.765625E-4f)
                            .uv((ac + 8.0f) * texRes + l, (ad + (float) ae + 0.5f) * texRes + m)
                            .color(redZ, greenZ, blueZ, 0.8f)
                            .normal(0.0f, 0.0f, 1.0f).endVertex();
                        bufferBuilder.vertex(ac + 0.0f, z + 0.0f, ad + (float) ae + 1.0f - 9.765625E-4f)
                            .uv((ac + 0.0f) * texRes + l, (ad + (float) ae + 0.5f) * texRes + m)
                            .color(redZ, greenZ, blueZ, 0.8f)
                            .normal(0.0f, 0.0f, 1.0f).endVertex();
                    }
                }
            }
        } else {
            boolean aa = true;
            int ab = 32;
            for (int af = -32; af < 32; af += 32) {
                for (int ag = -32; ag < 32; ag += 32) {
                    bufferBuilder.vertex(af, z, ag + 32)
                        .uv((float) (af) * texRes + l, (float) (ag + 32) * texRes + m)
                        .color(redTop, greenTop, blueTop, 0.8f)
                        .normal(0.0f, -1.0f, 0.0f).endVertex();
                    bufferBuilder.vertex(af + 32, z, ag + 32)
                        .uv((float) (af + 32) * texRes + l, (float) (ag + 32) * texRes + m)
                        .color(redTop, greenTop, blueTop, 0.8f)
                        .normal(0.0f, -1.0f, 0.0f).endVertex();
                    bufferBuilder.vertex(af + 32, z, ag)
                        .uv((float) (af + 32) * texRes + l, (float) (ag) * texRes + m)
                        .color(redTop, greenTop, blueTop, 0.8f)
                        .normal(0.0f, -1.0f, 0.0f).endVertex();
                    bufferBuilder.vertex(af, z, ag)
                        .uv((float) (af) * texRes + l, (float) (ag) * texRes + m)
                        .color(redTop, greenTop, blueTop, 0.8f)
                        .normal(0.0f, -1.0f, 0.0f).endVertex();
                }
            }
        }
        return bufferBuilder.end();
    }

    private void renderSnowAndRain(PoseStack poseStack, double inX, double inY, double inZ) {
        if (getRainLevel() <= 0.0f) {
            return;
        }

        RenderSystem.getModelViewStack().pushPose();
        RenderSystem.getModelViewStack().mulPoseMatrix(poseStack.last().pose());
        RenderSystem.applyModelViewMatrix();

        int xFloor = Mth.floor(inX);
        int yFloor = Mth.floor(inY);
        int zFloor = Mth.floor(inZ);
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bufferBuilder = tesselator.getBuilder();
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.enableDepthTest();
        int rainDistance = 5;
        if (Minecraft.useFancyGraphics()) {
            rainDistance = 10;
        }
        RenderSystem.depthMask(true);
        int count = -1;
        float rainAnimationTime = this.ticks + this.mc.getFrameTime();
        RenderSystem.setShader(GameRenderer::getParticleShader);
        turnOnLightLayer();
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();
        for (int rainZ = zFloor - rainDistance; rainZ <= zFloor + rainDistance; ++rainZ) {
            for (int rainX = xFloor - rainDistance; rainX <= xFloor + rainDistance; ++rainX) {
                int q = (rainZ - zFloor + 16) * 32 + rainX - xFloor + 16;
                double r = (double) this.rainSizeX[q] * 0.5;
                double s = (double) this.rainSizeZ[q] * 0.5;
                mutableBlockPos.set(rainX, inY, rainZ);
                Biome biome = this.blockAccess.getBiome(mutableBlockPos).value();
                if (!biome.hasPrecipitation()) {
                    continue;
                }

                int blockingHeight = this.blockAccess.getHeightBlocking(rainX, rainZ);
                int lower = Math.max(yFloor - rainDistance, blockingHeight);
                int upper = Math.max(yFloor + rainDistance, blockingHeight);

                if (lower == upper) {
                    // no rain
                    continue;
                }
                int rainY = Math.max(blockingHeight, yFloor);

                RandomSource randomSource = RandomSource.create(rainX * rainX * 3121L + rainX * 45238971L ^ rainZ * rainZ * 418711L + rainZ * 13761L);
                mutableBlockPos.setY(lower);
                Biome.Precipitation precipitation = biome.getPrecipitationAt(mutableBlockPos);
                if (precipitation == Biome.Precipitation.NONE) {
                    continue;
                }

                mutableBlockPos.setY(rainY);

                double localX = rainX + 0.5;
                double localZ = rainZ + 0.5;
                float distance = (float) Math.sqrt(localX * localX + localZ * localZ) / (float) rainDistance;
                float blend;
                float xOffset = 0;
                float yOffset = 0;

                int skyLight = this.blockAccess.getBrightness(LightLayer.SKY, mutableBlockPos) << 4;
                int blockLight = this.blockAccess.getBrightness(LightLayer.BLOCK, mutableBlockPos) << 4;

                if (precipitation == Biome.Precipitation.RAIN) {
                    if (count != 0) {
                        if (count >= 0) {
                            tesselator.end();
                        }
                        count = 0;
                        RenderSystem.setShaderTexture(0, RAIN_LOCATION);
                        bufferBuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
                    }

                    blend = ((1.0f - distance * distance) * 0.5f + 0.5f);
                    int x = this.ticks + rainX * rainX * 3121 + rainX * 45238971 + rainZ * rainZ * 418711 + rainZ * 13761 & 0x1F;
                    yOffset = -((float) x + this.mc.getFrameTime()) / 32.0f * (3.0f + randomSource.nextFloat());
                } else if (precipitation == Biome.Precipitation.SNOW) {
                    if (count != 1) {
                        if (count >= 0) {
                            tesselator.end();
                        }
                        count = 1;
                        RenderSystem.setShaderTexture(0, SNOW_LOCATION);
                        bufferBuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
                    }

                    blend = ((1.0f - distance * distance) * 0.3f + 0.5f);
                    xOffset = (float) (randomSource.nextDouble() + (double) rainAnimationTime * 0.01 * (double) ((float) randomSource.nextGaussian()));
                    float ae = -((float) (this.ticks & 0x1FF) + this.mc.getFrameTime()) / 512.0f;
                    float af = (float) (randomSource.nextDouble() + (double) (rainAnimationTime * (float) randomSource.nextGaussian()) * 0.001);
                    yOffset = ae + af;

                    //snow is brighter
                    skyLight = (skyLight * 3 + 240) / 4;
                    blockLight = (blockLight * 3 + 240) / 4;
                } else {
                    continue;
                }
                bufferBuilder
                    .vertex(localX - r, (double) upper - inY, localZ - s)
                    .uv(0.0f + xOffset, (float) lower * 0.25f + yOffset)
                    .color(1.0f, 1.0f, 1.0f, blend).uv2(blockLight, skyLight).endVertex();
                bufferBuilder
                    .vertex(localX + r, (double) upper - inY, localZ + s)
                    .uv(1.0f + xOffset, (float) lower * 0.25f + yOffset)
                    .color(1.0f, 1.0f, 1.0f, blend).uv2(blockLight, skyLight).endVertex();
                bufferBuilder
                    .vertex(localX + r, (double) lower - inY, localZ + s)
                    .uv(1.0f + xOffset, (float) upper * 0.25f + yOffset)
                    .color(1.0f, 1.0f, 1.0f, blend).uv2(blockLight, skyLight).endVertex();
                bufferBuilder
                    .vertex(localX - r, (double) lower - inY, localZ - s)
                    .uv(0.0f + xOffset, (float) upper * 0.25f + yOffset)
                    .color(1.0f, 1.0f, 1.0f, blend).uv2(blockLight, skyLight).endVertex();
            }
        }
        if (count >= 0) {
            tesselator.end();
        }
        RenderSystem.getModelViewStack().popPose();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        turnOffLightLayer();
    }

    public static int getLightColor(BlockAndTintGetter blockAndTintGetter, BlockPos blockPos) {
        int i = blockAndTintGetter.getBrightness(LightLayer.SKY, blockPos);
        int j = blockAndTintGetter.getBrightness(LightLayer.BLOCK, blockPos);
        return i << 20 | j << 4;
    }

    public float getTimeOfDay() {
        return this.blockAccess.dimensionType().timeOfDay(this.time);
    }

    public float getSunAngle() {
        float dayTime = this.getTimeOfDay();
        return dayTime * ((float) Math.PI * 2F);
    }

    public int getMoonPhase() {
        return this.blockAccess.dimensionType().moonPhase(this.time);
    }

    public float getSkyDarken() {
        float dayTime = this.getTimeOfDay();
        float h = 1.0f - (Mth.cos(dayTime * ((float) Math.PI * 2)) * 2.0f + 0.2f);
        h = Mth.clamp(h, 0.0f, 1.0f);
        h = 1.0f - h;
        h *= 1.0f - this.getRainLevel() * 5.0f / 16.0f;
        h *= 1.0f - this.getThunderLevel() * 5.0f / 16.0f;
        return h * 0.8f + 0.2f;
    }

    public float getRainLevel() {
        return this.rainLevel;
    }

    public float getThunderLevel() {
        return this.thunderLevel * this.getRainLevel();
    }

    public float getStarBrightness() {
        float f = this.getTimeOfDay();
        float f1 = 1.0F - (Mth.cos(f * ((float) Math.PI * 2F)) * 2.0F + 0.25F);
        f1 = Mth.clamp(f1, 0.0F, 1.0F);
        return f1 * f1 * 0.5F;
    }

    public Vec3 getSkyColor(Vec3 position) {
        float dayTime = this.getTimeOfDay();

        Vec3 samplePosition = position.subtract(2.0, 2.0, 2.0).scale(0.25);

        Vec3 skyColor = CubicSampler.gaussianSampleVec3(samplePosition, (i, j, k) -> Vec3.fromRGB24(this.blockAccess.getBiomeManager().getNoiseBiomeAtQuart(i, j, k).value().getSkyColor()));

        float h = Mth.cos(dayTime * ((float) Math.PI * 2)) * 2.0f + 0.5f;
        h = Mth.clamp(h, 0.0f, 1.0f);
        float skyColorR = (float) skyColor.x * h;
        float skyColorG = (float) skyColor.y * h;
        float skyColorB = (float) skyColor.z * h;

        float rain = this.getRainLevel();
        float thunder;
        if (rain > 0.0f) {
            float luminance = (skyColorR * 0.3f + skyColorG * 0.59f + skyColorB * 0.11f) * 0.6f;
            float darkening = 1.0f - rain * 0.75f;
            skyColorR = skyColorR * darkening + luminance * (1.0f - darkening);
            skyColorG = skyColorG * darkening + luminance * (1.0f - darkening);
            skyColorB = skyColorB * darkening + luminance * (1.0f - darkening);
        }
        if ((thunder = this.getThunderLevel()) > 0.0f) {
            float luminance = (skyColorR * 0.3f + skyColorG * 0.59f + skyColorB * 0.11f) * 0.2f;
            float darkening = 1.0f - thunder * 0.75f;
            skyColorR = skyColorR * darkening + luminance * (1.0f - darkening);
            skyColorG = skyColorG * darkening + luminance * (1.0f - darkening);
            skyColorB = skyColorB * darkening + luminance * (1.0f - darkening);
        }
        if (!this.mc.options.hideLightningFlash().get() && this.skyFlashTime > 0) {
            float flash = (float) this.skyFlashTime - this.mc.getFrameTime();
            if (flash > 1.0f) {
                flash = 1.0f;
            }
            skyColorR = skyColorR * (1.0f - (flash *= 0.45f)) + 0.8f * flash;
            skyColorG = skyColorG * (1.0f - flash) + 0.8f * flash;
            skyColorB = skyColorB * (1.0f - flash) + flash;
        }
        return new Vec3(skyColorR, skyColorG, skyColorB);
    }

    public Vec3 getFogColor(Vec3 pos) {
        float f = Mth.clamp(Mth.cos(this.getTimeOfDay() * ((float) Math.PI * 2F)) * 2.0F + 0.5F, 0.0F, 1.0F);
        Vec3 scaledPos = pos.subtract(2.0D, 2.0D, 2.0D).scale(0.25D);
        return CubicSampler.gaussianSampleVec3(scaledPos, (x, y, z) -> this.dimensionInfo.getBrightnessDependentFogColor(Vec3.fromRGB24(this.blockAccess.getBiomeManager().getNoiseBiomeAtQuart(x, y, z).value().getFogColor()), f));
    }

    public Vec3 getCloudColour() {
        float dayTime = this.getTimeOfDay();
        float f1 = Mth.cos(dayTime * ((float) Math.PI * 2F)) * 2.0F + 0.5F;
        f1 = Mth.clamp(f1, 0.0F, 1.0F);
        float r = 1.0F;
        float g = 1.0F;
        float b = 1.0F;
        float rain = this.getRainLevel();

        if (rain > 0.0F) {
            float luma = (r * 0.3F + g * 0.59F + b * 0.11F) * 0.6F;
            float dark = 1.0F - rain * 0.95F;
            r = r * dark + luma * (1.0F - dark);
            g = g * dark + luma * (1.0F - dark);
            b = b * dark + luma * (1.0F - dark);
        }

        r = r * (f1 * 0.9F + 0.1F);
        g = g * (f1 * 0.9F + 0.1F);
        b = b * (f1 * 0.85F + 0.15F);
        float thunder = this.getThunderLevel();

        if (thunder > 0.0F) {
            float luma = (r * 0.3F + g * 0.59F + b * 0.11F) * 0.2F;
            float dark = 1.0F - thunder * 0.95F;
            r = r * dark + luma * (1.0F - dark);
            g = g * dark + luma * (1.0F - dark);
            b = b * dark + luma * (1.0F - dark);
        }

        return new Vec3(r, g, b);
    }

    private void generateSky() {
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bufferBuilder = tesselator.getBuilder();
        if (this.skyVBO != null) {
            this.skyVBO.close();
        }
        this.skyVBO = new VertexBuffer(VertexBuffer.Usage.STATIC);
        BufferBuilder.RenderedBuffer renderedBuffer = buildSkyDisc(bufferBuilder, 16.0f);
        this.skyVBO.bind();
        this.skyVBO.upload(renderedBuffer);
        VertexBuffer.unbind();
    }

    private void generateSky2() {
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bufferBuilder = tesselator.getBuilder();
        if (this.sky2VBO != null) {
            this.sky2VBO.close();
        }
        this.sky2VBO = new VertexBuffer(VertexBuffer.Usage.STATIC);
        BufferBuilder.RenderedBuffer renderedBuffer = buildSkyDisc(bufferBuilder, -16.0f);
        this.sky2VBO.bind();
        this.sky2VBO.upload(renderedBuffer);
        VertexBuffer.unbind();
    }

    private static BufferBuilder.RenderedBuffer buildSkyDisc(BufferBuilder bufferBuilder, float posY) {
        float g = Math.signum(posY) * 512.0f;
        float h = 512.0f;
        RenderSystem.setShader(GameRenderer::getPositionShader);
        bufferBuilder.begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION);
        bufferBuilder.vertex(0.0, posY, 0.0).endVertex();
        for (int i = -180; i <= 180; i += 45) {
            bufferBuilder.vertex(g * Mth.cos((float) i * ((float) Math.PI / 180)), posY, 512.0f * Mth.sin((float) i * ((float) Math.PI / 180))).endVertex();
        }
        return bufferBuilder.end();
    }

    private void generateStars() {
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bufferBuilder = tesselator.getBuilder();
        RenderSystem.setShader(GameRenderer::getPositionShader);
        if (this.starVBO != null) {
            this.starVBO.close();
        }
        this.starVBO = new VertexBuffer(VertexBuffer.Usage.STATIC);
        BufferBuilder.RenderedBuffer renderedBuffer = this.buildStars(bufferBuilder);
        this.starVBO.bind();
        this.starVBO.upload(renderedBuffer);
        VertexBuffer.unbind();
    }

    private BufferBuilder.RenderedBuffer buildStars(BufferBuilder bufferBuilderIn) {
        Random random = new Random(10842L);
        bufferBuilderIn.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);

        for (int i = 0; i < 1500; ++i) {
            double d0 = random.nextFloat() * 2.0F - 1.0F;
            double d1 = random.nextFloat() * 2.0F - 1.0F;
            double d2 = random.nextFloat() * 2.0F - 1.0F;
            double d3 = 0.15F + random.nextFloat() * 0.1F;
            double d4 = d0 * d0 + d1 * d1 + d2 * d2;

            if (d4 < 1.0D && d4 > 0.01D) {
                d4 = 1.0D / Math.sqrt(d4);
                d0 = d0 * d4;
                d1 = d1 * d4;
                d2 = d2 * d4;
                double d5 = d0 * 100.0D;
                double d6 = d1 * 100.0D;
                double d7 = d2 * 100.0D;
                double d8 = Math.atan2(d0, d2);
                double d9 = Math.sin(d8);
                double d10 = Math.cos(d8);
                double d11 = Math.atan2(Math.sqrt(d0 * d0 + d2 * d2), d1);
                double d12 = Math.sin(d11);
                double d13 = Math.cos(d11);
                double d14 = random.nextDouble() * Math.PI * 2.0D;
                double d15 = Math.sin(d14);
                double d16 = Math.cos(d14);

                for (int j = 0; j < 4; ++j) {
                    double d17 = 0.0D;
                    double d18 = (double) ((j & 2) - 1) * d3;
                    double d19 = (double) ((j + 1 & 2) - 1) * d3;
                    double d20 = 0.0D;
                    double d21 = d18 * d16 - d19 * d15;
                    double d22 = d19 * d16 + d18 * d15;
                    double d23 = d21 * d12 + 0.0D * d13;
                    double d24 = 0.0D * d12 - d21 * d13;
                    double d25 = d24 * d9 - d22 * d10;
                    double d26 = d22 * d9 + d24 * d10;
                    bufferBuilderIn.vertex(d5 + d25, d6 + d23, d7 + d26).endVertex();
                }
            }
        }
        return bufferBuilderIn.end();
    }

    public void turnOffLightLayer() {
        RenderSystem.setShaderTexture(2, 0);
    }

    public void turnOnLightLayer() {
        RenderSystem.setShaderTexture(2, this.lightTextureLocation);
        this.mc.getTextureManager().bindForSetup(this.lightTextureLocation);
        RenderSystem.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        RenderSystem.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
    }

    public void updateTorchFlicker() {
        this.blockLightRedFlicker += (float) ((Math.random() - Math.random()) * Math.random() * Math.random() * 0.1);
        this.blockLightRedFlicker *= 0.9f;
        this.lightmapUpdateNeeded = true;
    }

    public void updateLightmap() {
        if (this.lightmapUpdateNeeded) {
            // not possible, needs a full world
			/*if (Config.isCustomColors())
			{
				boolean flag = this.client.player.isPotionActive(MobEffects.NIGHT_VISION) || this.client.player.isPotionActive(MobEffects.CONDUIT_POWER);

				if (CustomColors.updateLightmap(world, this.torchFlickerX, this.nativeImage, flag, partialTick))
				{
					this.dynamicTexture.updateDynamicTexture();
					this.needsUpdate = false;
					this.client.profiler.endSection();
					return;
				}
			}*/

            float skyLight = getSkyDarken();
            float effectiveSkyLight = this.skyFlashTime > 0 ? 1.0f : skyLight * 0.95F + 0.05F;

			/* no darkness effect, we don't have an actual player
			float darknessScale = this.mc.options.darknessEffectScale().get().floatValue();
			float darknessGamma = this.getDarknessGamma(0) * darknessScale;
			float effectiveDarknessScale = this.calculateDarknessScale(this.mc.player, darknessGamma, 0) * darknessScale;
			*/

            float waterVision = getWaterVision();
			/* no night vision, we don't have a player
			float nightVision = this.mc.player.hasEffect(MobEffects.NIGHT_VISION)
			 ? GameRenderer.getNightVisionScale(this.mc.player, 0)
			 : (waterVision > 0.0f && this.mc.player.hasEffect(MobEffects.CONDUIT_POWER) ? waterVision : 0.0f);
			*/
            float nightVision = 0.0f;

            Vector3f skylightColor = new Vector3f(skyLight, skyLight, 1.0f).lerp(new Vector3f(1.0f, 1.0f, 1.0f), 0.35f);

            Vector3f finalColor = new Vector3f();
            for (int i = 0; i < 16; ++i) {
                for (int j = 0; j < 16; ++j) {
                    float skyBrightness = LightTexture.getBrightness(this.blockAccess.dimensionType(), i) * effectiveSkyLight;
                    float blockBrightnessRed = LightTexture.getBrightness(this.blockAccess.dimensionType(), j) * (this.blockLightRedFlicker + 1.5f);
                    float blockBrightnessGreen = blockBrightnessRed * ((blockBrightnessRed * 0.6f + 0.4f) * 0.6f + 0.4f);
                    float blockBrightnessBlue = blockBrightnessRed * (blockBrightnessRed * blockBrightnessRed * 0.6f + 0.4f);

                    finalColor.set(blockBrightnessRed, blockBrightnessGreen, blockBrightnessBlue);

                    if (this.dimensionInfo.forceBrightLightmap()) {
                        finalColor.lerp(new Vector3f(0.99f, 1.12f, 1.0f), 0.25f);
                        finalColor.set(Mth.clamp(finalColor.x, 0.0f, 1.0f), Mth.clamp(finalColor.y, 0.0f, 1.0f), Mth.clamp(finalColor.z, 0.0f, 1.0f));
                    } else {
                        finalColor.add(new Vector3f(skylightColor).mul(skyBrightness));
                        finalColor.lerp(new Vector3f(0.75f, 0.75f, 0.75f), 0.04f);
                        // no darkening from bosses
//						if (getDarkenWorldAmount() > 0.0f) {
//							finalColor.lerp(new Vector3f(finalColor).mul(0.7f, 0.6f, 0.6f), getDarkenWorldAmount());
//						}
                    }

					/* no night vision, no player
					if (nightVision > 0.0f && (w = Math.max(finalColor.x(), Math.max(finalColor.y(), finalColor.z()))) < 1.0f) {
						v = 1.0f / w;
						vector3f4 = new Vector3f(finalColor).mul(v);
						finalColor.lerp(vector3f4, nightVision);
					}
					*/

                    if (!this.dimensionInfo.forceBrightLightmap()) {
						/* no darkness, no player
						if (effectiveDarknessScale > 0.0f) {
							finalColor.add(-effectiveDarknessScale, -effectiveDarknessScale, -effectiveDarknessScale);
						}
						 */
                        finalColor.set(Mth.clamp(finalColor.x, 0.0f, 1.0f), Mth.clamp(finalColor.y, 0.0f, 1.0f), Mth.clamp(finalColor.z, 0.0f, 1.0f));
                    }

                    float gamma = this.mc.options.gamma().get().floatValue();
                    Vector3f vector3f5 = new Vector3f(this.notGamma(finalColor.x), this.notGamma(finalColor.y), this.notGamma(finalColor.z));
                    finalColor.lerp(vector3f5, Math.max(0.0f, gamma /*- darknessGamma*/));
                    finalColor.lerp(new Vector3f(0.75f, 0.75f, 0.75f), 0.04f);
                    finalColor.set(Mth.clamp(finalColor.x, 0.0f, 1.0f), Mth.clamp(finalColor.y, 0.0f, 1.0f), Mth.clamp(finalColor.z, 0.0f, 1.0f));
                    finalColor.mul(255.0f);

                    int r = (int) finalColor.x();
                    int g = (int) finalColor.y();
                    int b = (int) finalColor.z();
                    this.lightPixels.setPixelRGBA(j, i, 0xFF000000 | b << 16 | g << 8 | r);
                }
            }

            this.lightTexture.upload();
            this.lightmapUpdateNeeded = false;
        }
    }

    private float notGamma(float f) {
        float g = 1.0f - f;
        return 1.0f - g * g * g * g;
    }

    public float getWaterVision() {
        if (!this.areEyesInFluid(FluidTags.WATER)) {
            return 0.0F;
        } else {
            if ((float) this.waterVisionTime >= 600.0F) {
                return 1.0F;
            } else {
                float f2 = Mth.clamp((float) this.waterVisionTime / 100.0F, 0.0F, 1.0F);
                float f3 = (float) this.waterVisionTime < 100.0F ? 0.0F : Mth.clamp(((float) this.waterVisionTime - 100.0F) / 500.0F, 0.0F, 1.0F);
                return f2 * 0.6F + f3 * 0.39999998F;
            }
        }
    }

    public boolean areEyesInFluid(TagKey<Fluid> tagIn) {
        if (this.blockAccess == null) {
            return false;
        }

        Vec3 pos = getEyePos();
        BlockPos blockpos = BlockPos.containing(pos);
        FluidState fluidstate = this.blockAccess.getFluidState(blockpos);
        return isFluidTagged(fluidstate, tagIn) && pos.y < (double) ((float) blockpos.getY() + fluidstate.getAmount() + 0.11111111F);
    }

    public Vec3 getEyePos() {
        return ClientDataHolderVR.getInstance().vrPlayer.vrdata_room_post.hmd.getPosition();
    }

    private boolean isFluidTagged(Fluid fluid, TagKey<Fluid> tag) {
        // Apparently fluid tags are server side, so we have to hard-code this shit.
        // Thanks Mojang.
        if (tag == FluidTags.WATER) {
            return fluid == Fluids.WATER || fluid == Fluids.FLOWING_WATER;
        } else if (tag == FluidTags.LAVA) {
            return fluid == Fluids.LAVA || fluid == Fluids.FLOWING_LAVA;
        }
        return false;
    }

    private boolean isFluidTagged(FluidState fluidState, TagKey<Fluid> tag) {
        return isFluidTagged(fluidState.getType(), tag);
    }

    public static class MenuFogRenderer {
        public float fogRed;
        public float fogGreen;
        public float fogBlue;
        private int targetBiomeFog;
        private int previousBiomeFog;
        private long biomeChangedTime;
        private final MenuWorldRenderer menuWorldRenderer;

        public MenuFogRenderer(MenuWorldRenderer menuWorldRenderer) {
            this.menuWorldRenderer = menuWorldRenderer;
        }

        public void setupFogColor() {
            Vec3 eyePos = this.menuWorldRenderer.getEyePos();

            FogType fogType = getEyeFogType();

            if (fogType == FogType.WATER) {
                this.updateWaterFog(this.menuWorldRenderer.getLevel());
            } else if (fogType == FogType.LAVA) {
                this.fogRed = 0.6F;
                this.fogGreen = 0.1F;
                this.fogBlue = 0.0F;
                this.biomeChangedTime = -1L;
            } else if (fogType == FogType.POWDER_SNOW) {
                this.fogRed = 0.623f;
                this.fogGreen = 0.734f;
                this.fogBlue = 0.785f;
                this.biomeChangedTime = -1L;
                // why is this here?
                RenderSystem.clearColor(this.fogRed, this.fogGreen, this.fogBlue, 0.0f);
            } else {
                this.updateSurfaceFog();
                this.biomeChangedTime = -1L;
            }

            float d0 = (float) ((eyePos.y + this.menuWorldRenderer.getLevel().getGround()) * this.menuWorldRenderer.getLevel().getVoidFogYFactor());

			/* no entity available
			MobEffectFogFunction mobEffectFogFunction = FogRenderer.getPriorityFogFunction(entity, f);
			if (mobEffectFogFunction != null) {
				LivingEntity livingEntity = (LivingEntity)entity;
				d0 = mobEffectFogFunction.getModifiedVoidDarkness(livingEntity, livingEntity.getEffect(mobEffectFogFunction.getMobEffect()), d0, f);
			}*/

            if (d0 < 1.0D && fogType != FogType.LAVA && fogType != FogType.POWDER_SNOW) {
                if (d0 < 0.0F) {
                    d0 = 0.0F;
                }

                d0 = d0 * d0;
                this.fogRed = this.fogRed * d0;
                this.fogGreen = this.fogGreen * d0;
                this.fogBlue = this.fogBlue * d0;
            }

            // no boss available
			/*if (this.gameRenderer.getDarkenWorldAmount(partialTick) > 0.0F)
			{
				float f = this.gameRenderer.getDarkenWorldAmount(partialTick);
				fogRed = fogRed * (1.0F - f) + fogRed * 0.7F * f;
				fogGreen = fogGreen * (1.0F - f) + fogGreen * 0.6F * f;
				fogBlue = fogBlue * (1.0F - f) + fogBlue * 0.6F * f;
			}*/

            if (fogType == FogType.WATER && this.fogRed != 0.0f && this.fogGreen != 0.0f && this.fogBlue != 0.0f) {
                float f1 = this.menuWorldRenderer.getWaterVision();
                float f3 = Math.min(1.0f / this.fogRed, Math.min(1.0f / this.fogGreen, 1.0f / this.fogBlue));

                this.fogRed = this.fogRed * (1.0F - f1) + this.fogRed * f3 * f1;
                this.fogGreen = this.fogGreen * (1.0F - f1) + this.fogGreen * f3 * f1;
                this.fogBlue = this.fogBlue * (1.0F - f1) + this.fogBlue * f3 * f1;
            }

            if (OptifineHelper.isOptifineLoaded()) {
                // custom fog colors
                if (fogType == FogType.WATER) {
                    Vec3 colUnderwater = OptifineHelper.getCustomUnderwaterColor(this.menuWorldRenderer.blockAccess, eyePos.x, eyePos.y, eyePos.z);
                    if (colUnderwater != null) {
                        this.fogRed = (float) colUnderwater.x;
                        this.fogGreen = (float) colUnderwater.y;
                        this.fogBlue = (float) colUnderwater.z;
                    }
                } else if (fogType == FogType.LAVA) {
                    Vec3 colUnderlava = OptifineHelper.getCustomUnderlavaColor(this.menuWorldRenderer.blockAccess, eyePos.x, eyePos.y, eyePos.z);
                    if (colUnderlava != null) {
                        this.fogRed = (float) colUnderlava.x;
                        this.fogGreen = (float) colUnderlava.y;
                        this.fogBlue = (float) colUnderlava.z;
                    }
                }
            }

            RenderSystem.clearColor(this.fogRed, this.fogGreen, this.fogBlue, 0.0f);
        }

        private void updateSurfaceFog() {
            float f = 0.25F + 0.75F * (float) this.menuWorldRenderer.renderDistanceChunks / 32.0F;
            f = 1.0F - (float) Math.pow(f, 0.25);
            Vec3 eyePos = this.menuWorldRenderer.getEyePos();
            Vec3 skyColor = this.menuWorldRenderer.getSkyColor(eyePos);
            if (OptifineHelper.isOptifineLoaded()) {
                if (this.menuWorldRenderer.blockAccess.dimensionType().effectsLocation().equals(BuiltinDimensionTypes.OVERWORLD_EFFECTS)) {
                    skyColor = OptifineHelper.getCustomSkyColor(skyColor, this.menuWorldRenderer.blockAccess, eyePos.x, eyePos.y, eyePos.z);
                } else if (this.menuWorldRenderer.blockAccess.dimensionType().effectsLocation().equals(BuiltinDimensionTypes.END_EFFECTS)) {
                    skyColor = OptifineHelper.getCustomSkyColorEnd(skyColor);
                }
            }
            float skyRed = (float) skyColor.x;
            float skyGreen = (float) skyColor.y;
            float skyBlue = (float) skyColor.z;
            Vec3 fogColor = this.menuWorldRenderer.getFogColor(eyePos);
            if (OptifineHelper.isOptifineLoaded()) {
                if (this.menuWorldRenderer.blockAccess.dimensionType().effectsLocation().equals(BuiltinDimensionTypes.OVERWORLD_EFFECTS)) {
                    fogColor = OptifineHelper.getCustomFogColor(fogColor, this.menuWorldRenderer.blockAccess, eyePos.x, eyePos.y, eyePos.z);
                } else if (this.menuWorldRenderer.blockAccess.dimensionType().effectsLocation().equals(BuiltinDimensionTypes.END_EFFECTS)) {
                    fogColor = OptifineHelper.getCustomFogColorEnd(fogColor);
                } else if (this.menuWorldRenderer.blockAccess.dimensionType().effectsLocation().equals(BuiltinDimensionTypes.NETHER_EFFECTS)) {
                    fogColor = OptifineHelper.getCustomFogColorNether(fogColor);
                }
            }
            this.fogRed = (float) fogColor.x;
            this.fogGreen = (float) fogColor.y;
            this.fogBlue = (float) fogColor.z;

            if (this.menuWorldRenderer.renderDistanceChunks >= 4) {
                float d0 = Mth.sin(this.menuWorldRenderer.getSunAngle()) > 0.0F ? -1.0F : 1.0F;
                float f5 = ClientDataHolderVR.getInstance().vrPlayer.vrdata_room_post.hmd.getDirection().rotateY(this.menuWorldRenderer.worldRotation).dot(d0, 0, 0);

                if (f5 < 0.0F) {
                    f5 = 0.0F;
                }

                if (f5 > 0.0F) {
                    float[] afloat = this.menuWorldRenderer.dimensionInfo.getSunriseColor(this.menuWorldRenderer.getTimeOfDay(), 0);

                    if (afloat != null) {
                        f5 = f5 * afloat[3];
                        this.fogRed = this.fogRed * (1.0F - f5) + afloat[0] * f5;
                        this.fogGreen = this.fogGreen * (1.0F - f5) + afloat[1] * f5;
                        this.fogBlue = this.fogBlue * (1.0F - f5) + afloat[2] * f5;
                    }
                }
            }

            this.fogRed += (skyRed - this.fogRed) * f;
            this.fogGreen += (skyGreen - this.fogGreen) * f;
            this.fogBlue += (skyBlue - this.fogBlue) * f;

            float f6 = this.menuWorldRenderer.getRainLevel();
            if (f6 > 0.0F) {
                float f4 = 1.0F - f6 * 0.5F;
                float f8 = 1.0F - f6 * 0.4F;
                this.fogRed *= f4;
                this.fogGreen *= f4;
                this.fogBlue *= f8;
            }

            float f7 = this.menuWorldRenderer.getThunderLevel();
            if (f7 > 0.0F) {
                float f9 = 1.0F - f7 * 0.5F;
                this.fogRed *= f9;
                this.fogGreen *= f9;
                this.fogBlue *= f9;
            }
            this.biomeChangedTime = -1L;
        }

        private void updateWaterFog(LevelReader levelIn) {
            long currentTime = Util.getMillis();
            int waterFogColor = levelIn.getBiome(BlockPos.containing(this.menuWorldRenderer.getEyePos())).value().getWaterFogColor();

            if (this.biomeChangedTime < 0L) {
                this.targetBiomeFog = waterFogColor;
                this.previousBiomeFog = waterFogColor;
                this.biomeChangedTime = currentTime;
            }

            int k = this.targetBiomeFog >> 16 & 255;
            int l = this.targetBiomeFog >> 8 & 255;
            int i1 = this.targetBiomeFog & 255;
            int j1 = this.previousBiomeFog >> 16 & 255;
            int k1 = this.previousBiomeFog >> 8 & 255;
            int l1 = this.previousBiomeFog & 255;
            float f = Mth.clamp((float) (currentTime - this.biomeChangedTime) / 5000.0F, 0.0F, 1.0F);

            float f1 = Mth.lerp(f, j1, k);
            float f2 = Mth.lerp(f, k1, l);
            float f3 = Mth.lerp(f, l1, i1);
            this.fogRed = f1 / 255.0F;
            this.fogGreen = f2 / 255.0F;
            this.fogBlue = f3 / 255.0F;

            if (this.targetBiomeFog != waterFogColor) {
                this.targetBiomeFog = waterFogColor;
                this.previousBiomeFog = Mth.floor(f1) << 16 | Mth.floor(f2) << 8 | Mth.floor(f3);
                this.biomeChangedTime = currentTime;
            }
        }

        public void setupFog(FogRenderer.FogMode fogMode) {
            FogType fogType = getEyeFogType();

            float fogStart, fogEnd;
            FogShape fogShape = FogShape.SPHERE;

            if (fogType == FogType.LAVA) {
                fogStart = 0.25f;
                fogEnd = 1.0f;
            } else if (fogType == FogType.POWDER_SNOW) {
                fogStart = 0.0f;
                fogEnd = 2.0f;
            } else if (fogType == FogType.WATER) {
                fogStart = -8.0f;
                fogEnd = 96.0f;

                Holder<Biome> holder = this.menuWorldRenderer.blockAccess.getBiome(BlockPos.containing(this.menuWorldRenderer.getEyePos()));
                if (holder.is(BiomeTags.HAS_CLOSER_WATER_FOG)) {
                    fogEnd *= 0.85f;
                }
                if (fogEnd > this.menuWorldRenderer.renderDistance) {
                    fogEnd = this.menuWorldRenderer.renderDistance;
                    fogShape = FogShape.CYLINDER;
                }
            } else if (this.menuWorldRenderer.blockAccess.getDimensionReaderInfo().isFoggyAt(0, 0)) {
                fogStart = this.menuWorldRenderer.renderDistance * 0.05f;
                fogEnd = Math.min(this.menuWorldRenderer.renderDistance, 192.0f) * 0.5f;
            } else if (fogMode == FogRenderer.FogMode.FOG_SKY) {
                fogStart = 0.0f;
                fogEnd = this.menuWorldRenderer.renderDistance;
                fogShape = FogShape.CYLINDER;
            } else {
                float h = Mth.clamp(this.menuWorldRenderer.renderDistance / 10.0f, 4.0f, 64.0f);
                fogStart = this.menuWorldRenderer.renderDistance - h;
                fogEnd = this.menuWorldRenderer.renderDistance;
                fogShape = FogShape.CYLINDER;
            }
            RenderSystem.setShaderFogStart(fogStart);
            RenderSystem.setShaderFogEnd(fogEnd);
            RenderSystem.setShaderFogShape(fogShape);
        }

        private FogType getEyeFogType() {
            FogType fogType = FogType.NONE;
            if (this.menuWorldRenderer.areEyesInFluid(FluidTags.WATER)) {
                fogType = FogType.WATER;
            } else if (this.menuWorldRenderer.areEyesInFluid(FluidTags.LAVA)) {
                fogType = FogType.LAVA;
            } else if (this.menuWorldRenderer.blockAccess.getBlockState(BlockPos.containing(this.menuWorldRenderer.getEyePos())).is(Blocks.POWDER_SNOW)) {
                fogType = FogType.POWDER_SNOW;
            }
            return fogType;
        }

        public void setupNoFog() {
            RenderSystem.setShaderFogStart(Float.MAX_VALUE);
        }

        public void levelFogColor() {
            RenderSystem.setShaderFogColor(this.fogRed, this.fogGreen, this.fogBlue);
        }
    }

    private static class FluidStateWrapper extends FluidState {
        private final FluidState fluidState;

        @SuppressWarnings("unchecked")
        public FluidStateWrapper(FluidState fluidState) {
            super(fluidState.getType(), fluidState.getValues(), fluidState.propertiesCodec);

            this.fluidState = fluidState;
        }

        @Override
        public boolean is(TagKey<Fluid> tagIn) {
            // Yeah I know this is super dirty, blame Mojang for making FluidTags server-side
            if (tagIn == FluidTags.WATER) {
                return this.getType() == Fluids.WATER || this.getType() == Fluids.FLOWING_WATER;
            } else if (tagIn == FluidTags.LAVA) {
                return this.getType() == Fluids.LAVA || this.getType() == Fluids.FLOWING_LAVA;
            }
            return this.fluidState.is(tagIn);
        }
    }
}
