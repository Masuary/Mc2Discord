package fr.denisd3d.mc2discord.forge;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class VaultHuntersData {

    private static final Logger LOGGER = LogManager.getLogger("mc2discord-vh");

    private static boolean available = false;
    private static boolean checked = false;

    private static Method statsDataGetMethod;
    private static Method getVaultStatsMethod;
    private static Method getVaultLevelMethod;

    private static boolean titlesAvailable = false;
    private static Method titlesDataGetMethod;
    private static Field titlesDataEntriesField;
    private static Method entryGetPrefixMethod;
    private static Method entryGetSuffixMethod;
    private static Method titleDisplayGetChatTextMethod;
    private static Method titleDisplayGetTabTextMethod;

    private static boolean emblemAvailable = false;
    private static Method emblemDataGetMethod;
    private static Method getSelectedEmblemMethod;
    private static Method isEmblemEnabledMethod;
    private static Method emblemGetSymbolMethod;

    private static void init() {
        if (checked) return;
        checked = true;

        try {
            Class<?> statsDataClass = Class.forName("iskallia.vault.world.data.PlayerVaultStatsData");
            Class<?> statsClass = Class.forName("iskallia.vault.skill.PlayerVaultStats");

            statsDataGetMethod = statsDataClass.getMethod("get", MinecraftServer.class);
            getVaultStatsMethod = statsDataClass.getMethod("getVaultStats", Player.class);
            getVaultLevelMethod = statsClass.getMethod("getVaultUncappedLevel");

            available = true;
            LOGGER.info("Vault Hunters data integration enabled");

            initTitles();
            initEmblems();
        } catch (ClassNotFoundException ignored) {
        } catch (Exception e) {
            LOGGER.warn("Failed to initialize VH data hook: {}", e.getMessage());
        }
    }

    private static void initTitles() {
        try {
            Class<?> titlesDataClass = Class.forName("iskallia.vault.world.data.PlayerTitlesData");
            Class<?> entryClass = Class.forName("iskallia.vault.world.data.PlayerTitlesData$Entry");
            Class<?> titleDisplayClass = Class.forName("iskallia.vault.world.data.PlayerTitlesData$TitleDisplay");

            titlesDataGetMethod = titlesDataClass.getMethod("get");
            titlesDataEntriesField = titlesDataClass.getField("entries");
            entryGetPrefixMethod = entryClass.getMethod("getPrefix");
            entryGetSuffixMethod = entryClass.getMethod("getSuffix");
            titleDisplayGetChatTextMethod = titleDisplayClass.getMethod("getChatText");
            titleDisplayGetTabTextMethod = titleDisplayClass.getMethod("getTabText");

            titlesAvailable = true;
        } catch (Exception e) {
            LOGGER.debug("VH title data not available: {}", e.getMessage());
        }
    }

    private static void initEmblems() {
        try {
            Class<?> emblemsDataClass = Class.forName("iskallia.vault.world.data.DiscoveredEmblemsData");
            Class<?> emblemConfigClass = Class.forName("iskallia.vault.config.EmblemConfig");

            emblemDataGetMethod = emblemsDataClass.getMethod("get", MinecraftServer.class);
            getSelectedEmblemMethod = emblemsDataClass.getMethod("getSelectedEmblem", UUID.class);
            isEmblemEnabledMethod = emblemsDataClass.getMethod("isEmblemEnabled", UUID.class);
            emblemGetSymbolMethod = emblemConfigClass.getMethod("getSymbol");

            emblemAvailable = true;
        } catch (Exception e) {
            LOGGER.debug("VH emblem data not available: {}", e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    public static Map<String, String> getPlayerExtras(Player player) {
        if (!checked) init();

        Map<String, String> extras = new HashMap<>();
        if (!available || !(player instanceof ServerPlayer serverPlayer)) return extras;

        MinecraftServer server = serverPlayer.getServer();
        if (server == null) return extras;

        extras.put("level", String.valueOf(getVaultLevel(serverPlayer, server)));
        extras.put("emblem", getEmblemSymbol(serverPlayer, server));
        extras.put("title_prefix", getTitleText(serverPlayer.getUUID(), true));
        extras.put("title_suffix", getTitleText(serverPlayer.getUUID(), false));

        return extras;
    }

    private static int getVaultLevel(ServerPlayer player, MinecraftServer server) {
        try {
            Object statsData = statsDataGetMethod.invoke(null, server);
            if (statsData == null) return 0;
            Object playerStats = getVaultStatsMethod.invoke(statsData, player);
            if (playerStats == null) return 0;
            Object result = getVaultLevelMethod.invoke(playerStats);
            return result instanceof Number n ? n.intValue() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private static String getEmblemSymbol(ServerPlayer player, MinecraftServer server) {
        if (!emblemAvailable) return "";
        try {
            Object emblemData = emblemDataGetMethod.invoke(null, server);
            if (emblemData == null) return "";
            boolean enabled = (boolean) isEmblemEnabledMethod.invoke(emblemData, player.getUUID());
            if (!enabled) return "";
            Object emblem = getSelectedEmblemMethod.invoke(emblemData, player.getUUID());
            if (emblem == null) return "";
            String symbol = (String) emblemGetSymbolMethod.invoke(emblem);
            return symbol != null ? symbol + " " : "";
        } catch (Exception e) {
            return "";
        }
    }

    @SuppressWarnings("unchecked")
    private static String getTitleText(UUID uuid, boolean isPrefix) {
        if (!titlesAvailable) return "";
        try {
            Object titlesData = titlesDataGetMethod.invoke(null);
            if (titlesData == null) return "";
            Map<UUID, ?> entries = (Map<UUID, ?>) titlesDataEntriesField.get(titlesData);
            Object entry = entries.get(uuid);
            if (entry == null) return "";

            Optional<?> displayOptional = (Optional<?>) (isPrefix
                    ? entryGetPrefixMethod.invoke(entry)
                    : entryGetSuffixMethod.invoke(entry));
            if (displayOptional.isEmpty()) return "";

            Object titleDisplay = displayOptional.get();
            Optional<String> chatText = (Optional<String>) titleDisplayGetChatTextMethod.invoke(titleDisplay);
            if (chatText.isPresent() && !chatText.get().isEmpty()) return chatText.get();

            Optional<String> tabText = (Optional<String>) titleDisplayGetTabTextMethod.invoke(titleDisplay);
            return tabText.orElse("");
        } catch (Exception e) {
            return "";
        }
    }
}
