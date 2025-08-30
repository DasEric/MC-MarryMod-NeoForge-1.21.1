package com.github.marriagemod;

import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.slf4j.Logger;

import java.util.*;

@Mod(MarriageMod.MODID)
public class MarriageMod {
    public static final String MODID = "marriagemod";
    public static final Logger LOGGER = LogUtils.getLogger();

    // --- Start of logic moved from MarriageManager ---
    private static final Map<UUID, UUID> marriedPlayers = new HashMap<>();
    private static final Map<UUID, UUID> pendingRequests = new HashMap<>(); // Key: Target, Value: Requester
    private static final Map<UUID, ChatFormatting> coupleColors = new HashMap<>();
    private static final List<ChatFormatting> AVAILABLE_COLORS = Arrays.asList(
            ChatFormatting.GOLD,
            ChatFormatting.AQUA,
            ChatFormatting.LIGHT_PURPLE,
            ChatFormatting.YELLOW,
            ChatFormatting.GREEN
    );
    private static int nextColorIndex = 0;

    public static void createRequest(UUID requester, UUID target) {
        pendingRequests.put(target, requester);
    }

    public static boolean hasPendingRequestFrom(UUID target, UUID requester) {
        return pendingRequests.containsKey(target) && pendingRequests.get(target).equals(requester);
    }

    public static void acceptRequest(UUID target, UUID requester) {
        if (hasPendingRequestFrom(target, requester)) {
            marry(requester, target);
            pendingRequests.remove(target);
        }
    }

    public static void denyRequest(UUID target, UUID requester) {
        if (hasPendingRequestFrom(target, requester)) {
            pendingRequests.remove(target);
        }
    }

    public static boolean areMarried(UUID player1, UUID player2) {
        return marriedPlayers.containsKey(player1) && marriedPlayers.get(player1).equals(player2);
    }

    public static void marry(UUID player1, UUID player2) {
        marriedPlayers.put(player1, player2);
        marriedPlayers.put(player2, player1);

        ChatFormatting color = AVAILABLE_COLORS.get(nextColorIndex);
        nextColorIndex = (nextColorIndex + 1) % AVAILABLE_COLORS.size();

        UUID coupleId = getCoupleId(player1, player2);
        coupleColors.put(coupleId, color);
    }

    public static void divorce(UUID player1, UUID player2) {
        UUID coupleId = getCoupleId(player1, player2);
        coupleColors.remove(coupleId);
        marriedPlayers.remove(player1);
        marriedPlayers.remove(player2);
    }

    public static boolean isMarried(UUID player) {
        return marriedPlayers.containsKey(player);
    }

    public static UUID getPartner(UUID player) {
        return marriedPlayers.get(player);
    }

    public static ChatFormatting getCoupleColor(UUID player) {
        if (!isMarried(player)) {
            return null;
        }
        UUID partner = getPartner(player);
        UUID coupleId = getCoupleId(player, partner);
        return coupleColors.get(coupleId);
    }

    private static UUID getCoupleId(UUID player1, UUID player2) {
        return player1.compareTo(player2) < 0 ? player1 : player2;
    }
    // --- End of logic moved from MarriageManager ---


    public MarriageMod(IEventBus modEventBus, ModContainer modContainer) {
        NeoForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        MarriageCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public void onPlayerTabListNameFormat(PlayerEvent.TabListNameFormat event) {
        // Updated to call local methods
        if (isMarried(event.getEntity().getUUID())) {
            ChatFormatting color = getCoupleColor(event.getEntity().getUUID());
            if (color != null) {
                Component currentDisplayName = event.getDisplayName();
                if (currentDisplayName == null) {
                    currentDisplayName = event.getEntity().getName();
                }
                MutableComponent prefix = Component.translatable("chat.marriagemod.married_prefix").append(" ").withStyle(color);
                MutableComponent nameComponent = Component.literal(currentDisplayName.getString()).withStyle(color);
                event.setDisplayName(prefix.append(nameComponent));
            }
        }
    }
}
