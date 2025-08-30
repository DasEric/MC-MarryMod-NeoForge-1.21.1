package com.github.marriagemod;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.slf4j.Logger;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

@Mod(MarriageMod.MODID)
public class MarriageMod {
    public static final String MODID = "marriagemod";
    public static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path SAVE_FILE_PATH = null; // Will be set on server start

    private static MinecraftServer server;

    // --- Marriage Logic Data ---
    private static final Map<UUID, UUID> marriedPlayers = new HashMap<>();
    private static final Map<UUID, UUID> pendingRequests = new HashMap<>();
    private static final Map<UUID, ChatFormatting> coupleColors = new HashMap<>();
    private static final List<ChatFormatting> AVAILABLE_COLORS = Arrays.asList(
            ChatFormatting.GOLD, ChatFormatting.AQUA, ChatFormatting.LIGHT_PURPLE,
            ChatFormatting.YELLOW, ChatFormatting.GREEN
    );
    private static int nextColorIndex = 0;

    public MarriageMod(IEventBus modEventBus, ModContainer modContainer) {
        NeoForge.EVENT_BUS.register(this);
    }

    // --- Save/Load Logic ---
    private static Path getSavePath(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve(MODID + ".json");
    }

    public static void saveData() {
        if (server == null) return;
        Path savePath = getSavePath(server);
        try (FileWriter writer = new FileWriter(savePath.toFile())) {
            SaveData data = new SaveData();
            data.marriedPlayers = marriedPlayers.entrySet().stream()
                    .collect(Collectors.toMap(e -> e.getKey().toString(), e -> e.getValue().toString()));
            data.coupleColors = coupleColors.entrySet().stream()
                    .collect(Collectors.toMap(e -> e.getKey().toString(), e -> e.getValue().getName()));
            GSON.toJson(data, writer);
        } catch (IOException e) {
            LOGGER.error("Could not save marriage data", e);
        }
    }

    public static void loadData() {
        if (server == null) return;
        Path savePath = getSavePath(server);
        if (!savePath.toFile().exists()) return;

        try (FileReader reader = new FileReader(savePath.toFile())) {
            Type dataType = new TypeToken<SaveData>() {}.getType();
            SaveData data = GSON.fromJson(reader, dataType);
            if (data != null) {
                marriedPlayers.clear();
                if (data.marriedPlayers != null) {
                    data.marriedPlayers.forEach((key, value) -> marriedPlayers.put(UUID.fromString(key), UUID.fromString(value)));
                }
                coupleColors.clear();
                if (data.coupleColors != null) {
                    data.coupleColors.forEach((key, value) -> coupleColors.put(UUID.fromString(key), ChatFormatting.valueOf(value.toUpperCase(Locale.ROOT))));
                }
            }
        } catch (IOException e) {
            LOGGER.error("Could not load marriage data", e);
        }
    }

    // --- Marriage Logic Methods ---
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
        saveData(); // Save on change
    }

    public static void divorce(UUID player1, UUID player2) {
        UUID coupleId = getCoupleId(player1, player2);
        coupleColors.remove(coupleId);
        marriedPlayers.remove(player1);
        marriedPlayers.remove(player2);
        saveData(); // Save on change
    }

    public static boolean isMarried(UUID player) {
        return marriedPlayers.containsKey(player);
    }

    public static UUID getPartner(UUID player) {
        return marriedPlayers.get(player);
    }

    public static ChatFormatting getCoupleColor(UUID player) {
        if (!isMarried(player)) return null;
        UUID partner = getPartner(player);
        UUID coupleId = getCoupleId(player, partner);
        return coupleColors.get(coupleId);
    }

    private static UUID getCoupleId(UUID player1, UUID player2) {
        return player1.compareTo(player2) < 0 ? player1 : player2;
    }

    // --- Event Handlers ---
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        server = event.getServer();
        loadData();
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        saveData();
        server = null;
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        MarriageCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public void onPlayerTabListNameFormat(PlayerEvent.TabListNameFormat event) {
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

    // --- Inner Class for Serialization ---
    private static class SaveData {
        Map<String, String> marriedPlayers;
        Map<String, String> coupleColors;
    }
}
