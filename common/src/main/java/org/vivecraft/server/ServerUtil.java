package org.vivecraft.server;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.*;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.blocks.BlockInput;
import net.minecraft.commands.arguments.blocks.BlockStateArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.vivecraft.client.utils.UpdateChecker;
import org.vivecraft.server.config.ConfigBuilder;
import org.vivecraft.server.config.ServerConfig;

import java.util.IllegalFormatException;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ServerUtil {

    /**
     * scheduler to send delayed messages
     */
    public static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor();

    static {
        // shut down the scheduler when the jvm terminates
        Runtime.getRuntime().addShutdownHook(new Thread(SCHEDULER::shutdownNow));
    }

    /**
     * schedules delayed welcome/kick messages for the give player <br>
     * the delay is for  the case that the clients VERSION packed isn't received immediately
     * @param serverPlayer player to send messages for / kick
     */
    public static void scheduleWelcomeMessageOrKick(ServerPlayer serverPlayer) {
        if (ServerConfig.MESSAGES_ENABLED.get() ||
            (ServerConfig.VIVE_ONLY.get() || ServerConfig.VR_ONLY.get())) {
            SCHEDULER.schedule(() -> {
                // only do stuff, if the player is still on the server
                if (!serverPlayer.hasDisconnected()) {
                    ServerVivePlayer vivePlayer = ServerVRPlayers.getVivePlayer(serverPlayer);
                    String message = "";

                    boolean isOpAndAllowed = ServerConfig.ALLOW_OP.get() && serverPlayer.server.getPlayerList().isOp(serverPlayer.getGameProfile());

                    // kick non VR players
                    if (!isOpAndAllowed && ServerConfig.VR_ONLY.get() && (vivePlayer == null || !vivePlayer.isVR())) {
                        String kickMessage = ServerConfig.MESSAGES_KICK_VR_ONLY.get();
                        try {
                            kickMessage = kickMessage.formatted(serverPlayer.getName().getString());
                        } catch (IllegalFormatException e) {
                            // catch errors users might put into the messages, to not crash other stuff
                            ServerNetworking.LOGGER.error("Vivecraft: KickVROnly message '{}' has errors: ", kickMessage, e);
                        }
                        serverPlayer.connection.disconnect(Component.literal(kickMessage));
                        return;
                    }

                    // kick non vivecraft players
                    if (!isOpAndAllowed && ServerConfig.VIVE_ONLY.get()
                        && (vivePlayer == null)) {
                        String kickMessage = ServerConfig.MESSAGES_KICK_VIVE_ONLY.get();
                        try {
                            kickMessage = kickMessage.formatted(serverPlayer.getName().getString());
                        } catch (IllegalFormatException e) {
                            // catch errors users might put into the messages, to not crash other stuff
                            ServerNetworking.LOGGER.error("Vivecraft: KickViveOnly message '{}' has errors: ", kickMessage, e);
                        }
                        serverPlayer.connection.disconnect(Component.literal(kickMessage));
                        return;
                    }


                    // welcome message
                    if (ServerConfig.MESSAGES_ENABLED.get()) {
                        // get the right message
                        if (vivePlayer == null) {
                            message = ServerConfig.MESSAGES_WELCOME_VANILLA.get();
                        } else if (!vivePlayer.isVR()) {
                            message = ServerConfig.MESSAGES_WELCOME_NONVR.get();
                        } else if (vivePlayer.isSeated()) {
                            message = ServerConfig.MESSAGES_WELCOME_SEATED.get();
                        } else {
                            message = ServerConfig.MESSAGES_WELCOME_VR.get();
                        }
                        // actually send the message, if there is one set
                        if (!message.isEmpty()) {
                            try {
                                serverPlayer.server.getPlayerList().broadcastSystemMessage(Component.literal(message.formatted(serverPlayer.getName().getString())), false);
                            } catch (IllegalFormatException e) {
                                // catch errors users might put into the messages, to not crash other stuff
                                ServerNetworking.LOGGER.error("Vivecraft: Welcome message '{}' has errors: ", message, e);
                            }
                        }
                    }
                }
            }, (long) (ServerConfig.MESSAGE_KICK_DELAY.get() * 1000), TimeUnit.MILLISECONDS);
        }
    }

    /**
     * notifies the given player for vivecraft updates, if they areOP and the setting is enabled
     * @param serverPlayer player to notify
     */
    public static void sendUpdateNotificationIfOP(ServerPlayer serverPlayer) {
        if (ServerConfig.CHECK_FOR_UPDATES.get()) {
            // don't send update notifications on singleplayer
            if (serverPlayer.server.isDedicatedServer() && serverPlayer.server.getPlayerList().isOp(serverPlayer.getGameProfile())) {
                // check for update on not the main thread
                SCHEDULER.schedule(() -> {
                    if (UpdateChecker.checkForUpdates()) {
                        serverPlayer.sendSystemMessage(Component.literal("Vivecraft update available: §a" + UpdateChecker.NEWEST_VERSION));
                    }
                }, 0, TimeUnit.MILLISECONDS);
            }
        }
    }

    /**
     * registers Vivecraft server commands, to change config settings with commands
     * @param dispatcher dispatcher to use for registering
     */
    public static void registerCommands(CommandDispatcher<net.minecraft.commands.CommandSourceStack> dispatcher, CommandBuildContext buildContext) {
        // reload command
        dispatcher.register(Commands.literal("vivecraft-server-config")
            .requires(source -> source.hasPermission(4))
            .then(Commands.literal("reload")
                .executes(context -> {
                    ServerConfig.init((action, path, incorrectValue, correctedValue) -> context.getSource()
                        .sendSystemMessage(Component.literal(
                            "Corrected §a[%s]§r: was '(%s)%s', is now '(%s)%s'".formatted(
                                String.join("§r.§a", path),
                                incorrectValue.getClass().getSimpleName(), incorrectValue,
                                correctedValue.getClass().getSimpleName(), correctedValue
                            ))));
                    return 1;
                })
            )
        );

        // register commands for each setting
        for (var setting : ServerConfig.getConfigValues()) {
            Class<?> clazz = setting.get().getClass();
            final ArgumentType<?> argument;
            String argumentName;
            if (clazz == Integer.class) {
                argumentName = "int";
                argument = IntegerArgumentType.integer();
            } else if (clazz == Double.class) {
                argumentName = "double";
                argument = DoubleArgumentType.doubleArg();
            } else if (clazz == Boolean.class) {
                argumentName = "bool";
                argument = BoolArgumentType.bool();
            } else {
                argumentName = "string";
                argument = StringArgumentType.string();
            }

            LiteralArgumentBuilder<CommandSourceStack> baseCommand = Commands.literal(setting.getPath());

            // value set commands
            if (setting instanceof ConfigBuilder.InListValue<?> inListValue) {
                baseCommand.then(Commands.literal("set")
                    .then(Commands.argument(argumentName, argument)
                        .suggests((context, builder) -> {
                            for (var value : inListValue.getValidValues()) {
                                if (value.toString().toLowerCase().contains(builder.getRemainingLowerCase())) {
                                    builder.suggest(value.toString());
                                }
                            }
                            return builder.buildFuture();
                        })
                        .executes(context -> {
                            Object newValue = context.getArgument(argumentName, clazz);
                            if (inListValue.getValidValues().contains(newValue)) {
                                setting.set(newValue);
                                context.getSource().sendSystemMessage(
                                    Component.literal(
                                        "set §a[%s]§r to '%s'".formatted(setting.getPath(), newValue)));
                                return 1;
                            } else {
                                throw new CommandSyntaxException(
                                    CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownArgument(),
                                    Component.literal("Unsupported value: " + newValue.toString()));
                            }
                        })
                    )
                );
            } else if (setting instanceof ConfigBuilder.EnumValue<?> enumValue) {
                baseCommand.then(Commands.literal("set")
                    .then(Commands.argument(argumentName, argument)
                        .suggests((context, builder) -> {
                            for (var value : enumValue.getValidValues()) {
                                if (value.toString().toLowerCase().contains(builder.getRemainingLowerCase())) {
                                    builder.suggest(value.toString());
                                }
                            }
                            return builder.buildFuture();
                        })
                        .executes(context -> {
                            Object newValue = context.getArgument(argumentName, String.class);
                            Object newEnumValue = enumValue.getEnumValue(newValue);
                            if (newEnumValue != null) {
                                setting.set(newEnumValue);
                                context.getSource().sendSystemMessage(
                                    Component.literal(
                                        "set §a[%s]§r to '%s'".formatted(setting.getPath(), newEnumValue)));
                                return 1;
                            } else {
                                throw new CommandSyntaxException(
                                    CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownArgument(),
                                    Component.literal("Unsupported value: " + newValue.toString()));
                            }
                        })
                    )
                );
            } else if (!(setting.get() instanceof List)) {
                baseCommand.then(Commands.literal("set")
                    .then(Commands.argument(argumentName, argument)
                        .executes(context -> {
                            Object newValue = context.getArgument(argumentName, clazz);
                            setting.set(newValue);
                            context.getSource().sendSystemMessage(
                                Component.literal(
                                    "set §a[%s]§r to '%s'".formatted(setting.getPath(), newValue)));
                            return 1;
                        })
                    )
                );
            } else {
                ConfigBuilder.ConfigValue<List<? extends String>> listConfig = setting;
                baseCommand.then(Commands.literal("add")
                    .then(Commands.argument("block", BlockStateArgument.block(buildContext))
                        .executes(context -> {
                            try {
                                String newValue = BuiltInRegistries.BLOCK.getKey(context.getArgument("block", BlockInput.class).getState().getBlock()).toString();
                                List list = listConfig.get();
                                list.add(newValue);
                                listConfig.set(list);
                                context.getSource().sendSystemMessage(
                                    Component.literal(
                                        "added '%s' to §a[%s]§r".formatted(newValue, setting.getPath())));
                                context.getSource().sendSystemMessage(
                                    Component.literal("is now '%s'".formatted(setting.get())));
                                return 1;
                            } catch (Exception e) {
                                ServerNetworking.LOGGER.error("Vivecraft: error adding block to list:", e);
                                return 0;
                            }
                        })
                    )
                );
                baseCommand.then(Commands.literal("remove")
                    .then(Commands.argument("block", StringArgumentType.greedyString())
                        .suggests((context, builder) -> {
                            for (String block : listConfig.get()) {
                                if (block.contains(builder.getRemaining())) {
                                    builder.suggest(block);
                                }
                            }
                            return builder.buildFuture();
                        })
                        .executes(context -> {
                            String newValue = context.getArgument("block", String.class);
                            List<? extends String> list = listConfig.get();
                            list.remove(newValue);
                            listConfig.set(list);
                            context.getSource().sendSystemMessage(
                                Component.literal("removed '%s' from §a[%s]§r".formatted(
                                    newValue, setting.getPath())));
                            context.getSource().sendSystemMessage(
                                Component.literal("is now '%s'".formatted(setting.get())));
                            return 1;
                        })
                    )
                );
            }

            // reset command
            baseCommand.then(Commands.literal("reset")
                .executes(context -> {
                    Object newValue = setting.reset();
                    context.getSource().sendSystemMessage(
                        Component.literal("reset §a[%s]§r to '%s'".formatted(setting.getPath(), newValue)));
                    return 1;
                })
            );

            // query command
            baseCommand.executes(context -> {
                    context.getSource().sendSystemMessage(
                        Component.literal("§a[%s]§r is set to '%s'".formatted(setting.getPath(), setting.get())));
                    return 1;
                }
            );

            dispatcher.register(Commands.literal("vivecraft-server-config")
                .requires(source -> source.hasPermission(4))
                .then(baseCommand)
            );
        }
    }
}
