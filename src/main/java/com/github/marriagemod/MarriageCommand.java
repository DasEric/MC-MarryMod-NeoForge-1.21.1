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
        dispatcher.register(Commands.literal("heiraten")
                .then(Commands.argument("player", StringArgumentType.string())
                        .executes(MarriageCommand::propose)));

        dispatcher.register(Commands.literal("scheiden")
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
            requester.sendSystemMessage(Component.literal("Spieler nicht gefunden!"));
            return 0;
        }

        if (requester.getUUID().equals(target.getUUID())) {
            requester.sendSystemMessage(Component.literal("Du kannst dich nicht selbst heiraten!"));
            return 0;
        }

        if (MarriageMod.isMarried(requester.getUUID())) {
            requester.sendSystemMessage(Component.literal("Du bist bereits verheiratet!"));
            return 0;
        }

        if (MarriageMod.isMarried(target.getUUID())) {
            requester.sendSystemMessage(Component.literal(target.getName().getString() + " ist bereits verheiratet!"));
            return 0;
        }

        MarriageMod.createRequest(requester.getUUID(), target.getUUID());

        requester.sendSystemMessage(Component.literal("Heiratsantrag an " + target.getName().getString() + " gesendet."));

        // Create clickable components
        Component acceptText = Component.literal("[Akzeptieren]")
                .setStyle(Style.EMPTY
                        .withColor(0x55FF55) // Green
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Heiratsantrag annehmen")))
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/heiraten-akzeptieren " + requester.getName().getString())));

        Component denyText = Component.literal("[Ablehnen]")
                .setStyle(Style.EMPTY
                        .withColor(0xFF5555) // Red
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Heiratsantrag ablehnen")))
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/heiraten-ablehnen " + requester.getName().getString())));

        target.sendSystemMessage(Component.literal(requester.getName().getString() + " möchte dich heiraten! "));
        target.sendSystemMessage(Component.literal("").append(acceptText).append(" ").append(denyText));

        return 1;
    }

    private static int accept(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer target = context.getSource().getPlayerOrException();
        String requesterName = StringArgumentType.getString(context, "player");
        ServerPlayer requester = context.getSource().getServer().getPlayerList().getPlayerByName(requesterName);

        if (requester == null) {
            target.sendSystemMessage(Component.literal("Der Spieler, der den Antrag gestellt hat, ist nicht mehr online."));
            return 0;
        }

        if (MarriageMod.hasPendingRequestFrom(target.getUUID(), requester.getUUID())) {
            MarriageMod.acceptRequest(target.getUUID(), requester.getUUID());
            context.getSource().getServer().getPlayerList().broadcastSystemMessage(Component.literal("Spieler " + requester.getName().getString() + " hat " + target.getName().getString() + " geheiratet!"), false);

            // Refresh tab list names
            requester.refreshTabListName();
            target.refreshTabListName();
        } else {
            target.sendSystemMessage(Component.literal("Du hast keinen Heiratsantrag von " + requesterName + "."));
        }
        return 1;
    }

    private static int deny(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer target = context.getSource().getPlayerOrException();
        String requesterName = StringArgumentType.getString(context, "player");
        ServerPlayer requester = context.getSource().getServer().getPlayerList().getPlayerByName(requesterName);

        if (MarriageMod.hasPendingRequestFrom(target.getUUID(), requester.getUUID())) {
            MarriageMod.denyRequest(target.getUUID(), requester.getUUID());
            target.sendSystemMessage(Component.literal("Du hast den Heiratsantrag abgelehnt."));
            if (requester != null) {
                requester.sendSystemMessage(Component.literal(target.getName().getString() + " hat deinen Heiratsantrag abgelehnt."));
            }
        } else {
            target.sendSystemMessage(Component.literal("Du hast keinen Heiratsantrag von " + requesterName + "."));
        }
        return 1;
    }

    private static int divorce(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String targetName = StringArgumentType.getString(context, "player");
        ServerPlayer target = context.getSource().getServer().getPlayerList().getPlayerByName(targetName);

        if (target == null) {
            player.sendSystemMessage(Component.literal("Spieler nicht gefunden!"));
            return 0;
        }

        if (!MarriageMod.areMarried(player.getUUID(), target.getUUID())) {
            player.sendSystemMessage(Component.literal("Du bist nicht mit diesem Spieler verheiratet!"));
            return 0;
        }

        MarriageMod.divorce(player.getUUID(), target.getUUID());
        context.getSource().getServer().getPlayerList().broadcastSystemMessage(Component.literal("Spieler " + player.getName().getString() + " hat sich von " + target.getName().getString() + " geschieden!"), false);

        // Refresh tab list names
        player.refreshTabListName();
        target.refreshTabListName();

        return 1;
    }
}
