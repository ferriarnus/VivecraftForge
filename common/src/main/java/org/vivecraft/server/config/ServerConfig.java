package org.vivecraft.server.config;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.ConfigSpec;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import net.minecraft.ResourceLocationException;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.vivecraft.client.Xplat;
import org.vivecraft.server.ServerNetworking;

import java.util.Arrays;
import java.util.List;

public class ServerConfig {

    // config keys
    public static ConfigBuilder.BooleanValue debug;
    public static ConfigBuilder.BooleanValue checkForUpdate;
    public static ConfigBuilder.InListValue<String> checkForUpdateType;
    public static ConfigBuilder.BooleanValue vr_only;
    public static ConfigBuilder.BooleanValue vive_only;
    public static ConfigBuilder.BooleanValue allow_op;
    public static ConfigBuilder.DoubleValue messageKickDelay;
    public static ConfigBuilder.BooleanValue vrFun;

    public static ConfigBuilder.BooleanValue messagesEnabled;
    public static ConfigBuilder.StringValue messagesWelcomeVR;
    public static ConfigBuilder.StringValue messagesWelcomeNonVR;
    public static ConfigBuilder.StringValue messagesWelcomeSeated;
    public static ConfigBuilder.StringValue messagesWelcomeVanilla;
    public static ConfigBuilder.StringValue messagesDeathVR;
    public static ConfigBuilder.StringValue messagesDeathNonVR;
    public static ConfigBuilder.StringValue messagesDeathSeated;
    public static ConfigBuilder.StringValue messagesDeathVanilla;
    public static ConfigBuilder.StringValue messagesDeathByMobVR;
    public static ConfigBuilder.StringValue messagesDeathByMobNonVR;
    public static ConfigBuilder.StringValue messagesDeathByMobSeated;
    public static ConfigBuilder.StringValue messagesDeathByMobVanilla;
    public static ConfigBuilder.StringValue messagesLeaveMessage;
    public static ConfigBuilder.StringValue messagesKickViveOnly;
    public static ConfigBuilder.StringValue messagesKickVROnly;

    public static ConfigBuilder.DoubleValue creeperSwellDistance;
    public static ConfigBuilder.DoubleValue bowStandingMultiplier;
    public static ConfigBuilder.DoubleValue bowSeatedMultiplier;
    public static ConfigBuilder.DoubleValue bowStandingHeadshotMultiplier;
    public static ConfigBuilder.DoubleValue bowSeatedHeadshotMultiplier;
    public static ConfigBuilder.DoubleValue bowVanillaHeadshotMultiplier;

    public static ConfigBuilder.BooleanValue pvpVRvsVR;
    public static ConfigBuilder.BooleanValue pvpSEATEDVRvsSEATEDVR;
    public static ConfigBuilder.BooleanValue pvpVRvsNONVR;
    public static ConfigBuilder.BooleanValue pvpSEATEDVRvsNONVR;
    public static ConfigBuilder.BooleanValue pvpVRvsSEATEDVR;
    public static ConfigBuilder.BooleanValue pvpNotifyBlockedDamage;

    public static ConfigBuilder.BooleanValue climbeyEnabled;
    public static ConfigBuilder.EnumValue<ClimbeyBlockmode> climbeyBlockmode;
    public static ConfigBuilder.ListValue<String> climbeyBlocklist;

    public static ConfigBuilder.BooleanValue crawlingEnabled;

    public static ConfigBuilder.BooleanValue teleportEnabled;
    public static ConfigBuilder.BooleanValue teleportLimitedSurvival;
    public static ConfigBuilder.IntValue teleportUpLimit;
    public static ConfigBuilder.IntValue teleportDownLimit;
    public static ConfigBuilder.IntValue teleportHorizontalLimit;

    public static ConfigBuilder.BooleanValue worldscaleLimited;
    public static ConfigBuilder.DoubleValue worldscaleMax;
    public static ConfigBuilder.DoubleValue worldscaleMin;

    public static ConfigBuilder.BooleanValue forceThirdPersonItems;
    public static ConfigBuilder.BooleanValue forceThirdPersonItemsCustom;

    public static ConfigBuilder.BooleanValue vrSwitchingEnabled;

    private static CommentedFileConfig config;
    private static ConfigBuilder builder;

    public static List<ConfigBuilder.ConfigValue> getConfigValues() {
        return builder.getConfigValues();
    }

    public static void init(ConfigSpec.CorrectionListener listener) {
        Config.setInsertionOrderPreserved(true);
        config = CommentedFileConfig
            .builder(Xplat.getConfigPath("vivecraft-server-config.toml"))
            .autosave()
            .sync()
            .concurrent()
            .build();

        config.load();

        if (listener == null) {
            listener = (action, path, incorrectValue, correctedValue) -> {
                if (incorrectValue != null) {
                    ServerNetworking.LOGGER.info("Vivecraft: Corrected setting '{}': was '{}', is now '{}'", String.join(".", path),
                        incorrectValue, correctedValue);
                }
            };
        }

        fixConfig(config, listener);

        config.save();
    }

    private static void fixConfig(CommentedConfig config, ConfigSpec.CorrectionListener listener) {

        builder = new ConfigBuilder(config, new ConfigSpec());

        builder
            .push("general");
        debug = builder
            .push("debug")
            .comment("will print clients that connect with vivecraft, and what version they are using, to the log.")
            .define(false);
        checkForUpdate = builder
            .push("checkForUpdate")
            .comment("will check for a newer version and alert any OP when they login to the server.")
            .define(true);
        checkForUpdateType = builder
            .push("checkForUpdateType")
            .comment("What updates to check for.\n r: Release, b: Beta, a: Alpha")
            .defineInList("r", Arrays.asList("r", "b", "a"));
        vr_only = builder
            .push("vr_only")
            .comment("Set to true to only allow VR players to play.\n If enabled, VR hotswitching will be automatically disabled.")
            .define(false);
        vive_only = builder
            .push("vive_only")
            .comment("Set to true to only allow vivecraft players to play.")
            .define(false);
        allow_op = builder
            .push("allow_op")
            .comment("If true, will allow server ops to be in any mode. No effect if vive-only/vr-only is false.")
            .define(true);
        messageKickDelay = builder
            .push("messageAndKickDelay")
            .comment("Seconds to wait before kicking a player or sending welcome messages. The player's client must send a Vivecraft VERSION info in that time.")
            .defineInRange(10.0, 0.0, 100.0);
        vrFun = builder
            .push("vrFun")
            .comment("Gives VR Players fun cakes and drinks at random, when they respawn.")
            .define(true);
        // end general
        builder.pop();

        builder
            .push("messages");
        messagesEnabled = builder
            .push("enabled")
            .comment("Enable or disable all messages.")
            .define(false);

        // welcome messages
        messagesWelcomeVR = builder
            .push("welcomeVR")
            .comment("set message to nothing to not send. ex: leaveMessage = \"\"\n put '%s' in any message for the player name")
            .define("%s has joined with standing VR!");
        messagesWelcomeNonVR = builder
            .push("welcomeNonVR")
            .define("%s has joined with Non-VR companion!");
        messagesWelcomeSeated = builder
            .push("welcomeSeated")
            .define("%s has joined with seated VR!");

        messagesLeaveMessage = builder
            .push("leaveMessage")
            .define("%s has disconnected from the server!");

        // general death messages
        messagesWelcomeVanilla = builder
            .push("welcomeVanilla")
            .define("%s has joined as a Muggle!");

        messagesLeaveMessage = builder
            .push("leaveMessage")
            .define("%s has disconnected from the server!");

        // general death messages
        messagesDeathVR = builder
            .push("deathVR")
            .define("%s died in standing VR!");
        messagesDeathNonVR = builder
            .push("deathNonVR")
            .define("%s died in Non-VR companion!");
        messagesDeathSeated = builder
            .push("deathSeated")
            .define("%s died in seated VR!");
        messagesDeathVanilla = builder
            .push("deathVanilla")
            .define("%s died as a Muggle!");

        // death messages by mobs
        messagesDeathByMobVR = builder
            .push("deathByMobVR")
            .comment("death by mob messages use '%1$s' for the player name and '%2$s' for the mob name")
            .define("%1$s was slain by %2$s in standing VR!");
        messagesDeathByMobNonVR = builder
            .push("deathByMobNonVR")
            .define("%1$s was slain by %2$s in Non-VR companion!");
        messagesDeathByMobSeated = builder
            .push("deathByMobSeated")
            .define("%1$s was slain by %2$s in seated VR!");
        messagesDeathByMobVanilla = builder
            .push("deathByMobVanilla")
            .define("%1$s was slain by %2$s as a Muggle!");

        // kick messages
        messagesKickViveOnly = builder
            .push("KickViveOnly")
            .comment("The message to show kicked non vivecraft players.")
            .define("This server is configured for Vivecraft players only.");
        messagesKickVROnly = builder
            .push("KickVROnly")
            .comment("The message to show kicked non VR players.")
            .define("This server is configured for VR players only.");
        // end messages
        builder.pop();

        builder
            .push("vrChanges")
            .comment("Vanilla modifications for VR players");
        creeperSwellDistance = builder
            .push("creeperSwellDistance")
            .comment("Distance at which creepers swell and explode for VR players. Vanilla: 3")
            .defineInRange(1.75, 0.1, 10.0);

        builder
            .push("bow")
            .comment("Bow damage adjustments");
        bowStandingMultiplier = builder
            .push("standingMultiplier")
            .comment("Archery damage multiplier for Vivecraft (standing) users. Set to 1.0 to disable")
            .defineInRange(2.0, 1.0, 10.0);
        bowSeatedMultiplier = builder
            .push("seatedMultiplier")
            .comment("Archery damage multiplier for Vivecraft (seated) users. Set to 1.0 to disable")
            .defineInRange(1.0, 1.0, 10.0);
        bowStandingHeadshotMultiplier = builder
            .push("standingHeadshotMultiplier")
            .comment("Headshot damage multiplier for Vivecraft (standing) users. Set to 1.0 to disable")
            .defineInRange(3.0, 1.0, 10.0);
        bowSeatedHeadshotMultiplier = builder
            .push("seatedHeadshotMultiplier")
            .comment("Headshot damage multiplier for Vivecraft (seated) users. Set to 1.0 to disable")
            .defineInRange(2.0, 1.0, 10.0);
        bowVanillaHeadshotMultiplier = builder
            .push("vanillaHeadshotMultiplier")
            .comment("Headshot damage multiplier for Vanilla/NonVR users. Set to 1.0 to disable")
            .defineInRange(1.0, 1.0, 10.0);
        // end bow
        builder.pop();
        // end vrChanges
        builder.pop();

        builder
            .push("pvp")
            .comment("VR vs. non-VR vs. seated player PVP settings");
        pvpNotifyBlockedDamage = builder
            .push("notifyBlockedDamage")
            .comment("Notifies the player that would cause damage, that it was blocked.")
            .define(false);
        pvpVRvsVR = builder
            .push("VRvsVR")
            .comment("Allows Standing VR players to damage each other.")
            .define(true);
        pvpSEATEDVRvsSEATEDVR = builder
            .push("SEATEDVRvsSEATEDVR")
            .comment("Allows Seated VR players to damage each other.")
            .define(true);
        pvpVRvsNONVR = builder
            .push("VRvsNONVR")
            .comment("Allows Standing VR players and Non VR players to damage each other.")
            .define(true);
        pvpSEATEDVRvsNONVR = builder
            .push("SEATEDVRvsNONVR")
            .comment("Allows Seated VR players and Non VR players to damage each other.")
            .define(true);
        pvpVRvsSEATEDVR = builder
            .push("VRvsSEATEDVR")
            .comment("Allows Standing VR players and Seated VR Players to damage each other.")
            .define(true);
        // end pvp
        builder.pop();

        builder
            .push("climbey")
            .comment("Climbey motion settings");
        climbeyEnabled = builder
            .push("enabled")
            .comment("Allows use of jump_boots and climb_claws.")
            .define(true);
        climbeyBlockmode = builder
            .push("blockmode")
            .comment("Sets which blocks are climb-able. Options are:\n \"DISABLED\" = List ignored. All blocks are climbable.\n \"WHITELIST\" = Only blocks on the list are climbable.\n \"BLACKLIST\" = All blocks are climbable except those on the list")
            .defineEnum(ClimbeyBlockmode.DISABLED, ClimbeyBlockmode.class);
        climbeyBlocklist = builder
            .push("blocklist")
            .comment("The list of block names for use with include/exclude block mode.")
            .defineList(Arrays.asList("white_wool", "dirt", "grass_block"), (s) -> {
                boolean valid = true;
                try {
                    // check if valid block
                    Block b = BuiltInRegistries.BLOCK.get(new ResourceLocation((String) s));
                    if (b == Blocks.AIR) {
                        valid = false;
                    }
                } catch (ResourceLocationException e) {
                    valid = false;
                }
                if (!valid) {
                    ServerNetworking.LOGGER.error("Vivecraft: Ignoring invalid/unknown block in climbey blocklist: {}", s);
                }
                // return true or the whole list would be reset
                return true;
            });
        // end climbey
        builder.pop();

        builder
            .push("crawling")
            .comment("Roomscale crawling settings");
        crawlingEnabled = builder.
            push("enabled")
            .comment("Allows use of roomscale crawling. Disabling does not prevent vanilla crawling.")
            .define(true);
        // end crawling
        builder.pop();

        builder
            .push("teleport")
            .comment("Teleport settings");
        teleportEnabled = builder
            .push("enabled")
            .comment("Whether direct teleport is enabled. It is recommended to leave this enabled for players prone to VR sickness.")
            .define(true);
        teleportLimitedSurvival = builder
            .push("limitedSurvival")
            .comment("Enforce limited teleport range and frequency in survival.")
            .define(false);
        teleportUpLimit = builder
            .push("upLimit")
            .comment("Maximum blocks players can teleport up. Set to 0 to disable.")
            .defineInRange(4, 1, 16);
        teleportDownLimit = builder
            .push("downLimit")
            .comment("Maximum blocks players can teleport down. Set to 0 to disable.")
            .defineInRange(4, 1, 16);
        teleportHorizontalLimit = builder
            .push("horizontalLimit")
            .comment("Maximum blocks players can teleport horizontally. Set to 0 to disable.")
            .defineInRange(16, 1, 32);
        // end teleport
        builder.pop();

        builder
            .push("worldScale")
            .comment("World scale settings");
        worldscaleLimited = builder
            .push("limitRange")
            .comment("Limit the range of world scale players can use")
            .define(false);
        worldscaleMin = builder
            .push("min")
            .comment("Lower limit of range")
            .defineInRange(0.5, 0.1, 100.0);
        worldscaleMax = builder
            .push("max")
            .comment("Upper limit of range")
            .defineInRange(2.0, 0.1, 100.0);
        // end worldScale
        builder.pop();

        builder
            .push("settingOverrides")
            .comment("Other client settings to override");
        forceThirdPersonItems = builder
            .push("thirdPersonItems")
            .comment("Forces players to use the raw item position setting")
            .define(false);
        forceThirdPersonItemsCustom = builder
            .push("thirdPersonItemsCustom")
            .comment("Forces players to use the raw item position setting, only for items with custom model data")
            .define(false);
        // end settingOverrides
        builder.pop();

        builder
            .push("vrSwitching")
            .comment("VR hotswitch settings");
        vrSwitchingEnabled = builder
            .push("enabled")
            .comment("Allows players to switch between VR and NONVR on the fly.\n If disabled, they will be locked to the mode they joined with.")
            .define(true);
        // end vrSwitching
        builder.pop();

        // fix any enums that are loaded as strings first
        for (ConfigBuilder.ConfigValue<?> configValue: builder.getConfigValues()) {
            if (configValue instanceof ConfigBuilder.EnumValue enumValue && enumValue.get() != null) {
                enumValue.set(enumValue.getEnumValue(enumValue.get()));
            }
        }

        // if the config is outdated, or is missing keys, re add them
        builder.correct(listener);
    }
}
