package fr.denisd3d.mc2discord.forge;

import fr.denisd3d.mc2discord.core.entities.AdvancementEntity;
import fr.denisd3d.mc2discord.core.entities.DeathEntity;
import fr.denisd3d.mc2discord.core.entities.PlayerEntity;
import fr.denisd3d.mc2discord.core.events.MinecraftEvents;
import fr.denisd3d.mc2discord.minecraft.Mc2DiscordMinecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.AdvancementEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Optional;

public class ForgeEvents {

    private static String getTeamFormattedName(Player player) {
        return PlayerTeam.formatNameForTeam(player.getTeam(), player.getName()).getString();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onServerChat(ServerChatEvent event) {
        if (event.isCanceled())
            return;

        MinecraftEvents.onMinecraftChatMessageEvent(event.getMessage(), new PlayerEntity(event.getPlayer().getGameProfile().getName(),
                getTeamFormattedName(event.getPlayer()), event.getPlayer().getGameProfile().getId()));
    }

    @SubscribeEvent
    public static void onPlayerConnectEvent(PlayerEvent.PlayerLoggedInEvent event) {
        MinecraftEvents.onPlayerConnectEvent(new PlayerEntity(event.getPlayer().getGameProfile().getName(),
                getTeamFormattedName((Player) event.getEntity()), event.getPlayer().getGameProfile().getId()));
    }

    @SubscribeEvent
    public static void onPlayerDisconnectEvent(PlayerEvent.PlayerLoggedOutEvent event) {
        MinecraftEvents.onPlayerDisconnectEvent(new PlayerEntity(event.getPlayer().getGameProfile().getName(),
                getTeamFormattedName((Player) event.getEntity()), event.getPlayer().getGameProfile().getId()));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlayerDeathEvent(LivingDeathEvent event) {
        if (event.isCanceled())
            return;

        if (event.getEntity() instanceof Player player) {
            MinecraftEvents.onPlayerDeathEvent(new PlayerEntity(player.getGameProfile().getName(), getTeamFormattedName(player), player.getGameProfile().getId()), new DeathEntity(event.getSource()
                    .getMsgId(), player.getCombatTracker().getDeathMessage().getString(), player.getCombatTracker().getCombatDuration(), Optional.ofNullable(player.getCombatTracker().getKiller())
                    .map(livingEntity -> livingEntity.getDisplayName().getString())
                    .orElse(""), Optional.ofNullable(player.getCombatTracker().getKiller()).map(LivingEntity::getHealth).orElse(0.0f)));
        }
    }

    @SubscribeEvent
    public static void onAdvancementEvent(AdvancementEvent event) {
        if (event.getAdvancement().getDisplay() != null && event.getAdvancement().getDisplay().shouldAnnounceChat()) {
            MinecraftEvents.onAdvancementEvent(new PlayerEntity(event.getPlayer().getGameProfile().getName(),
                    getTeamFormattedName((Player) event.getEntity()), event.getPlayer()
                    .getGameProfile()
                    .getId()), new AdvancementEntity(event.getAdvancement().getId().getPath(), event.getAdvancement().getChatComponent().getString(), event.getAdvancement()
                    .getDisplay()
                    .getTitle()
                    .getString(), event.getAdvancement().getDisplay().getDescription().getString()));
        }
    }

    @SubscribeEvent
    public static void onCommandEvent(CommandEvent event) {
        Mc2DiscordMinecraft.onCommand(event.getParseResults());
    }
}
