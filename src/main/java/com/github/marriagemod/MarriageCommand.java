package com.github.marriagemod;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;

public class MarriageCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // German commands
        dispatcher.register(Commands.literal("heiraten")
                .then(Commands.argument("player", StringArgumentType.string())
                        .executes(MarriageCommand::propose)));

        dispatcher.register(Commands.literal("scheiden")
                .then(Commands.argument("player", StringArgumentType.string())
                        .executes(MarriageCommand::divorce)));

        // English commands
        dispatcher.register(Commands.literal("marry")
                .then(Commands.argument("player", StringArgumentType.string())
                        .executes(MarriageCommand::propose)));

        dispatcher.register(Commands.literal("divorce")
                .then(Commands.argument("player", StringArgumentType.string())
                        .executes(MarriageCommand::divorce)));

        // Internal commands for buttons
        dispatcher.register(Commands.literal("heiraten-akzeptieren")
                .then(Commands.argument("player", StringArgumentType.string())
                        .executes(MarriageCommand::accept)));

        dispatcher.register(Commands.literal("heiraten-ablehnen")
                .then(Commands.argument("player", StringArgumentType.string())
                        .executes(MarriageCommand::deny)));
    }

    private static int propose(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer requester = context.getSource().getPlayerOrException();
        String targetName = StringArgumentType.getString(context, "player");
        ServerPlayer target = context.getSource().getServer().getPlayerList().getPlayerByName(targetName);

        if (target == null) {
            requester.sendSystemMessage(Component.translatable("chat.marriagemod.player_not_found"));
            return 0;
        }

        if (requester.getUUID().equals(target.getUUID())) {
            requester.sendSystemMessage(Component.translatable("chat.marriagemod.propose_self"));
            return 0;
        }

        if (MarriageMod.isMarried(requester.getUUID())) {
            requester.sendSystemMessage(Component.translatable("chat.marriagemod.requester_already_married"));
            return 0;
        }

        if (MarriageMod.isMarried(target.getUUID())) {
            requester.sendSystemMessage(Component.translatable("chat.marriagemod.target_already_married", target.getName()));
            return 0;
        }

        MarriageMod.createRequest(requester.getUUID(), target.getUUID());

        requester.sendSystemMessage(Component.translatable("chat.marriagemod.proposal_sent", target.getName()));

        // Create clickable components
        Component acceptText = Component.translatable("chat.marriagemod.proposal_accept")
                .setStyle(Style.EMPTY
                        .withColor(0x55FF55) // Green
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("chat.marriagemod.proposal_accept_hover")))
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/heiraten-akzeptieren " + requester.getName().getString())));

        Component denyText = Component.translatable("chat.marriagemod.proposal_deny")
                .setStyle(Style.EMPTY
                        .withColor(0xFF5555) // Red
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("chat.marriagemod.proposal_deny_hover")))
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/heiraten-ablehnen " + requester.getName().getString())));

        target.sendSystemMessage(Component.translatable("chat.marriagemod.proposal_received", requester.getName()));
        target.sendSystemMessage(Component.literal("").append(acceptText).append(" ").append(denyText));

        return 1;
    }

    private static int accept(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer target = context.getSource().getPlayerOrException();
        String requesterName = StringArgumentType.getString(context, "player");
        ServerPlayer requester = context.getSource().getServer().getPlayerList().getPlayerByName(requesterName);

        if (requester == null) {
            target.sendSystemMessage(Component.translatable("chat.marriagemod.requester_offline"));
            return 0;
        }

        if (MarriageMod.hasPendingRequestFrom(target.getUUID(), requester.getUUID())) {
            MarriageMod.acceptRequest(target.getUUID(), requester.getUUID());
            context.getSource().getServer().getPlayerList().broadcastSystemMessage(Component.translatable("chat.marriagemod.marriage_success", requester.getName(), target.getName()), false);

            // Refresh tab list names
            requester.refreshTabListName();
            target.refreshTabListName();
        } else {
            target.sendSystemMessage(Component.translatable("chat.marriagemod.no_pending_request", requesterName));
        }
        return 1;
    }

    private static int deny(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer target = context.getSource().getPlayerOrException();
        String requesterName = StringArgumentType.getString(context, "player");
        ServerPlayer requester = context.getSource().getServer().getPlayerList().getPlayerByName(requesterName);

        if (MarriageMod.hasPendingRequestFrom(target.getUUID(), requester.getUUID())) {
            MarriageMod.denyRequest(target.getUUID(), requester.getUUID());
            target.sendSystemMessage(Component.translatable("chat.marriagemod.proposal_denied_target"));
            if (requester != null) {
                requester.sendSystemMessage(Component.translatable("chat.marriagemod.proposal_denied_requester", target.getName()));
            }
        } else {
            target.sendSystemMessage(Component.translatable("chat.marriagemod.no_pending_request", requesterName));
        }
        return 1;
    }

    private static int divorce(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String targetName = StringArgumentType.getString(context, "player");
        ServerPlayer target = context.getSource().getServer().getPlayerList().getPlayerByName(targetName);

        if (target == null) {
            player.sendSystemMessage(Component.translatable("chat.marriagemod.player_not_found"));
            return 0;
        }

        if (!MarriageMod.areMarried(player.getUUID(), target.getUUID())) {
            player.sendSystemMessage(Component.translatable("chat.marriagemod.not_married_to_player"));
            return 0;
        }

        MarriageMod.divorce(player.getUUID(), target.getUUID());
        context.getSource().getServer().getPlayerList().broadcastSystemMessage(Component.translatable("chat.marriagemod.divorce_success", player.getName(), target.getName()), false);

        // Refresh tab list names
        player.refreshTabListName();
        target.refreshTabListName();

        return 1;
    }
}
