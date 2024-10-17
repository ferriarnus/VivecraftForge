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
    public static ConfigBuilder.BooleanValue DEBUG;
    public static ConfigBuilder.BooleanValue DEBUG_PARTICLES;
    public static ConfigBuilder.BooleanValue DEBUG_PARTICLES_HEAD;
    public static ConfigBuilder.BooleanValue CHECK_FOR_UPDATES;
    public static ConfigBuilder.InListValue<String> CHECK_FOR_UPDATE_TYPE;
    public static ConfigBuilder.BooleanValue VR_ONLY;
    public static ConfigBuilder.BooleanValue VIVE_ONLY;
    public static ConfigBuilder.BooleanValue ALLOW_OP;
    public static ConfigBuilder.DoubleValue MESSAGE_KICK_DELAY;
    public static ConfigBuilder.BooleanValue VR_FUN;

    public static ConfigBuilder.BooleanValue MESSAGES_ENABLED;
    public static ConfigBuilder.StringValue MESSAGES_WELCOME_VR;
    public static ConfigBuilder.StringValue MESSAGES_WELCOME_NONVR;
    public static ConfigBuilder.StringValue MESSAGES_WELCOME_SEATED;
    public static ConfigBuilder.StringValue MESSAGES_WELCOME_VANILLA;
    public static ConfigBuilder.StringValue MESSAGES_DEATH_VR;
    public static ConfigBuilder.StringValue MESSAGES_DEATH_NONVR;
    public static ConfigBuilder.StringValue MESSAGES_DEATH_SEATED;
    public static ConfigBuilder.StringValue MESSAGES_DEATH_VANILLA;
    public static ConfigBuilder.StringValue MESSAGES_DEATH_BY_MOB_VR;
    public static ConfigBuilder.StringValue MESSAGES_DEATH_BY_MOB_NONVR;
    public static ConfigBuilder.StringValue MESSAGES_DEATH_BY_MOB_SEATED;
    public static ConfigBuilder.StringValue MESSAGES_DEATH_BY_MOB_VANILLA;
    public static ConfigBuilder.StringValue MESSAGES_LEAVE_MESSAGE;
    public static ConfigBuilder.StringValue MESSAGES_KICK_VIVE_ONLY;
    public static ConfigBuilder.StringValue MESSAGES_KICK_VR_ONLY;

    public static ConfigBuilder.DoubleValue CREEPER_SWELL_DISTANCE;
    public static ConfigBuilder.DoubleValue BOW_STANDING_MULTIPLIER;
    public static ConfigBuilder.DoubleValue BOW_SEATED_MULTIPLIER;
    public static ConfigBuilder.DoubleValue BOW_STANDING_HEADSHOT_MULTIPLIER;
    public static ConfigBuilder.DoubleValue BOW_SEATED_HEADSHOT_MULTIPLIER;
    public static ConfigBuilder.DoubleValue BOW_VANILLA_HEADSHOT_MULTIPLIER;

    public static ConfigBuilder.BooleanValue PVP_VR_VS_VR;
    public static ConfigBuilder.BooleanValue PVP_SEATEDVR_VS_SEATEDVR;
    public static ConfigBuilder.BooleanValue PVP_VR_VS_NONVR;
    public static ConfigBuilder.BooleanValue PVP_SEATEDVR_VS_NONVR;
    public static ConfigBuilder.BooleanValue PVP_VR_VS_SEATEDVR;
    public static ConfigBuilder.BooleanValue PVP_NOTIFY_BLOCKED_DAMAGE;

    public static ConfigBuilder.BooleanValue CLIMBEY_ENABLED;
    public static ConfigBuilder.EnumValue<ClimbeyBlockmode> CLIMBEY_BLOCKMODE;
    public static ConfigBuilder.ListValue<String> CLIMBEY_BLOCKLIST;

    public static ConfigBuilder.BooleanValue CRAWLING_ENABLED;

    public static ConfigBuilder.BooleanValue TELEPORT_ENABLED;
    public static ConfigBuilder.BooleanValue TELEPORT_LIMITED_SURVIVAL;
    public static ConfigBuilder.IntValue TELEPORT_UP_LIMIT;
    public static ConfigBuilder.IntValue TELEPORT_DOWN_LIMIT;
    public static ConfigBuilder.IntValue TELEPORT_HORIZONTAL_LIMIT;

    public static ConfigBuilder.BooleanValue WORLDSCALE_LIMITED;
    public static ConfigBuilder.DoubleValue WORLDSCALE_MAX;
    public static ConfigBuilder.DoubleValue WORLDSCALE_MIN;

    public static ConfigBuilder.BooleanValue FORCE_THIRD_PERSON_ITEMS;
    public static ConfigBuilder.BooleanValue FORCE_THIRD_PERSON_ITEMS_CUSTOM;

    public static ConfigBuilder.BooleanValue VR_SWITCHING_ENABLED;

    private static CommentedFileConfig CONFIG;
    private static ConfigBuilder BUILDER;

    public static List<ConfigBuilder.ConfigValue> getConfigValues() {
        return BUILDER.getConfigValues();
    }

    public static void init(ConfigSpec.CorrectionListener listener) {
        Config.setInsertionOrderPreserved(true);
        CONFIG = CommentedFileConfig
            .builder(Xplat.getConfigPath("vivecraft-server-config.toml"))
            .autosave()
            .sync()
            .concurrent()
            .build();

        CONFIG.load();

        if (listener == null) {
            listener = (action, path, incorrectValue, correctedValue) -> {
                if (incorrectValue != null) {
                    ServerNetworking.LOGGER.info("Vivecraft: Corrected setting '{}': was '{}', is now '{}'", String.join(".", path),
                        incorrectValue, correctedValue);
                }
            };
        }

        fixConfig(CONFIG, listener);

        CONFIG.save();
    }

    private static void fixConfig(CommentedConfig config, ConfigSpec.CorrectionListener listener) {

        BUILDER = new ConfigBuilder(config, new ConfigSpec());

        BUILDER
            .push("general");
        DEBUG = BUILDER
            .push("debug")
            .comment("will print clients that connect with vivecraft, and what version they are using, to the log.")
            .define(false);
        CHECK_FOR_UPDATES = BUILDER
            .push("checkForUpdate")
            .comment("will check for a newer version and alert any OP when they login to the server.")
            .define(true);
        CHECK_FOR_UPDATE_TYPE = BUILDER
            .push("checkForUpdateType")
            .comment("What updates to check for.\n r: Release, b: Beta, a: Alpha")
            .defineInList("r", Arrays.asList("r", "b", "a"));
        VR_ONLY = BUILDER
            .push("vr_only")
            .comment("Set to true to only allow VR players to play.\n If enabled, VR hotswitching will be automatically disabled.")
            .define(false);
        VIVE_ONLY = BUILDER
            .push("vive_only")
            .comment("Set to true to only allow vivecraft players to play.")
            .define(false);
        ALLOW_OP = BUILDER
            .push("allow_op")
            .comment("If true, will allow server ops to be in any mode. No effect if vive-only/vr-only is false.")
            .define(true);
        MESSAGE_KICK_DELAY = BUILDER
            .push("messageAndKickDelay")
            .comment("Seconds to wait before kicking a player or sending welcome messages. The player's client must send a Vivecraft VERSION info in that time.")
            .defineInRange(10.0, 0.0, 100.0);
        VR_FUN = BUILDER
            .push("vrFun")
            .comment("Gives VR Players fun cakes and drinks at random, when they respawn.")
            .define(true);
        // end general
        BUILDER.pop();

        BUILDER
            .push("messages");
        MESSAGES_ENABLED = BUILDER
            .push("enabled")
            .comment("Enable or disable all messages.")
            .define(false);

        // welcome messages
        MESSAGES_WELCOME_VR = BUILDER
            .push("welcomeVR")
            .comment("set message to nothing to not send. ex: leaveMessage = \"\"\n put '%s' in any message for the player name")
            .define("%s has joined with standing VR!");
        MESSAGES_WELCOME_NONVR = BUILDER
            .push("welcomeNonVR")
            .define("%s has joined with Non-VR companion!");
        MESSAGES_WELCOME_SEATED = BUILDER
            .push("welcomeSeated")
            .define("%s has joined with seated VR!");

        MESSAGES_LEAVE_MESSAGE = BUILDER
            .push("leaveMessage")
            .define("%s has disconnected from the server!");

        // general death messages
        MESSAGES_WELCOME_VANILLA = BUILDER
            .push("welcomeVanilla")
            .define("%s has joined as a Muggle!");

        MESSAGES_LEAVE_MESSAGE = BUILDER
            .push("leaveMessage")
            .define("%s has disconnected from the server!");

        // general death messages
        MESSAGES_DEATH_VR = BUILDER
            .push("deathVR")
            .define("%s died in standing VR!");
        MESSAGES_DEATH_NONVR = BUILDER
            .push("deathNonVR")
            .define("%s died in Non-VR companion!");
        MESSAGES_DEATH_SEATED = BUILDER
            .push("deathSeated")
            .define("%s died in seated VR!");
        MESSAGES_DEATH_VANILLA = BUILDER
            .push("deathVanilla")
            .define("%s died as a Muggle!");

        // death messages by mobs
        MESSAGES_DEATH_BY_MOB_VR = BUILDER
            .push("deathByMobVR")
            .comment("death by mob messages use '%1$s' for the player name and '%2$s' for the mob name")
            .define("%1$s was slain by %2$s in standing VR!");
        MESSAGES_DEATH_BY_MOB_NONVR = BUILDER
            .push("deathByMobNonVR")
            .define("%1$s was slain by %2$s in Non-VR companion!");
        MESSAGES_DEATH_BY_MOB_SEATED = BUILDER
            .push("deathByMobSeated")
            .define("%1$s was slain by %2$s in seated VR!");
        MESSAGES_DEATH_BY_MOB_VANILLA = BUILDER
            .push("deathByMobVanilla")
            .define("%1$s was slain by %2$s as a Muggle!");

        // kick messages
        MESSAGES_KICK_VIVE_ONLY = BUILDER
            .push("KickViveOnly")
            .comment("The message to show kicked non vivecraft players.")
            .define("This server is configured for Vivecraft players only.");
        MESSAGES_KICK_VR_ONLY = BUILDER
            .push("KickVROnly")
            .comment("The message to show kicked non VR players.")
            .define("This server is configured for VR players only.");
        // end messages
        BUILDER.pop();

        BUILDER
            .push("vrChanges")
            .comment("Vanilla modifications for VR players");
        CREEPER_SWELL_DISTANCE = BUILDER
            .push("creeperSwellDistance")
            .comment("Distance at which creepers swell and explode for VR players. Vanilla: 3")
            .defineInRange(1.75, 0.1, 10.0);

        BUILDER
            .push("bow")
            .comment("Bow damage adjustments");
        BOW_STANDING_MULTIPLIER = BUILDER
            .push("standingMultiplier")
            .comment("Archery damage multiplier for Vivecraft (standing) users. Set to 1.0 to disable")
            .defineInRange(2.0, 1.0, 10.0);
        BOW_SEATED_MULTIPLIER = BUILDER
            .push("seatedMultiplier")
            .comment("Archery damage multiplier for Vivecraft (seated) users. Set to 1.0 to disable")
            .defineInRange(1.0, 1.0, 10.0);
        BOW_STANDING_HEADSHOT_MULTIPLIER = BUILDER
            .push("standingHeadshotMultiplier")
            .comment("Headshot damage multiplier for Vivecraft (standing) users. Set to 1.0 to disable")
            .defineInRange(3.0, 1.0, 10.0);
        BOW_SEATED_HEADSHOT_MULTIPLIER = BUILDER
            .push("seatedHeadshotMultiplier")
            .comment("Headshot damage multiplier for Vivecraft (seated) users. Set to 1.0 to disable")
            .defineInRange(2.0, 1.0, 10.0);
        BOW_VANILLA_HEADSHOT_MULTIPLIER = BUILDER
            .push("vanillaHeadshotMultiplier")
            .comment("Headshot damage multiplier for Vanilla/NonVR users. Set to 1.0 to disable")
            .defineInRange(1.0, 1.0, 10.0);
        // end bow
        BUILDER.pop();
        // end vrChanges
        BUILDER.pop();

        BUILDER
            .push("pvp")
            .comment("VR vs. non-VR vs. seated player PVP settings");
        PVP_NOTIFY_BLOCKED_DAMAGE = BUILDER
            .push("notifyBlockedDamage")
            .comment("Notifies the player that would cause damage, that it was blocked.")
            .define(false);
        PVP_VR_VS_VR = BUILDER
            .push("VRvsVR")
            .comment("Allows Standing VR players to damage each other.")
            .define(true);
        PVP_SEATEDVR_VS_SEATEDVR = BUILDER
            .push("SEATEDVRvsSEATEDVR")
            .comment("Allows Seated VR players to damage each other.")
            .define(true);
        PVP_VR_VS_NONVR = BUILDER
            .push("VRvsNONVR")
            .comment("Allows Standing VR players and Non VR players to damage each other.")
            .define(true);
        PVP_SEATEDVR_VS_NONVR = BUILDER
            .push("SEATEDVRvsNONVR")
            .comment("Allows Seated VR players and Non VR players to damage each other.")
            .define(true);
        PVP_VR_VS_SEATEDVR = BUILDER
            .push("VRvsSEATEDVR")
            .comment("Allows Standing VR players and Seated VR Players to damage each other.")
            .define(true);
        // end pvp
        BUILDER.pop();

        BUILDER
            .push("climbey")
            .comment("Climbey motion settings");
        CLIMBEY_ENABLED = BUILDER
            .push("enabled")
            .comment("Allows use of jump_boots and climb_claws.")
            .define(true);
        CLIMBEY_BLOCKMODE = BUILDER
            .push("blockmode")
            .comment("Sets which blocks are climb-able. Options are:\n \"DISABLED\" = List ignored. All blocks are climbable.\n \"WHITELIST\" = Only blocks on the list are climbable.\n \"BLACKLIST\" = All blocks are climbable except those on the list")
            .defineEnum(ClimbeyBlockmode.DISABLED, ClimbeyBlockmode.class);
        CLIMBEY_BLOCKLIST = BUILDER
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
        BUILDER.pop();

        BUILDER
            .push("crawling")
            .comment("Roomscale crawling settings");
        CRAWLING_ENABLED = BUILDER.
            push("enabled")
            .comment("Allows use of roomscale crawling. Disabling does not prevent vanilla crawling.")
            .define(true);
        // end crawling
        BUILDER.pop();

        BUILDER
            .push("teleport")
            .comment("Teleport settings");
        TELEPORT_ENABLED = BUILDER
            .push("enabled")
            .comment("Whether direct teleport is enabled. It is recommended to leave this enabled for players prone to VR sickness.")
            .define(true);
        TELEPORT_LIMITED_SURVIVAL = BUILDER
            .push("limitedSurvival")
            .comment("Enforce limited teleport range and frequency in survival.")
            .define(false);
        TELEPORT_UP_LIMIT = BUILDER
            .push("upLimit")
            .comment("Maximum blocks players can teleport up. Set to 0 to disable.")
            .defineInRange(4, 1, 16);
        TELEPORT_DOWN_LIMIT = BUILDER
            .push("downLimit")
            .comment("Maximum blocks players can teleport down. Set to 0 to disable.")
            .defineInRange(4, 1, 16);
        TELEPORT_HORIZONTAL_LIMIT = BUILDER
            .push("horizontalLimit")
            .comment("Maximum blocks players can teleport horizontally. Set to 0 to disable.")
            .defineInRange(16, 1, 32);
        // end teleport
        BUILDER.pop();

        BUILDER
            .push("worldScale")
            .comment("World scale settings");
        WORLDSCALE_LIMITED = BUILDER
            .push("limitRange")
            .comment("Limit the range of world scale players can use")
            .define(false);
        WORLDSCALE_MIN = BUILDER
            .push("min")
            .comment("Lower limit of range")
            .defineInRange(0.5, 0.1, 100.0);
        WORLDSCALE_MAX = BUILDER
            .push("max")
            .comment("Upper limit of range")
            .defineInRange(2.0, 0.1, 100.0);
        // end worldScale
        BUILDER.pop();

        BUILDER
            .push("settingOverrides")
            .comment("Other client settings to override");
        FORCE_THIRD_PERSON_ITEMS = BUILDER
            .push("thirdPersonItems")
            .comment("Forces players to use the raw item position setting")
            .define(false);
        FORCE_THIRD_PERSON_ITEMS_CUSTOM = BUILDER
            .push("thirdPersonItemsCustom")
            .comment("Forces players to use the raw item position setting, only for items with custom model data")
            .define(false);
        // end settingOverrides
        BUILDER.pop();

        BUILDER
            .push("vrSwitching")
            .comment("VR hotswitch settings");
        VR_SWITCHING_ENABLED = BUILDER
            .push("enabled")
            .comment("Allows players to switch between VR and NONVR on the fly.\n If disabled, they will be locked to the mode they joined with.")
            .define(true);
        // end vrSwitching
        BUILDER.pop();

        BUILDER
            .push("debug");
        DEBUG_PARTICLES = BUILDER
            .push("debugParticles")
            .comment("will spawn particles at VR players device positions, to indicate the server VR data state.")
            .define(false);
        DEBUG_PARTICLES_HEAD = BUILDER
            .push("debugParticlesHead")
            .comment("will spawn particles at VR players head position, to indicate the server VR data state.")
            .define(false);
        BUILDER.pop();

        // fix any enums that are loaded as strings first
        for (ConfigBuilder.ConfigValue<?> configValue: BUILDER.getConfigValues()) {
            if (configValue instanceof ConfigBuilder.EnumValue enumValue && enumValue.get() != null) {
                enumValue.set(enumValue.getEnumValue(enumValue.get()));
            }
        }

        // if the config is outdated, or is missing keys, re add them
        BUILDER.correct(listener);
    }
}
