package fr.denisd3d.mc2discord.core;

import discord4j.core.object.presence.Activity;
import discord4j.core.object.presence.ClientActivity;
import discord4j.core.object.presence.ClientPresence;
import discord4j.discordjson.json.ChannelModifyRequest;
import discord4j.discordjson.possible.Possible;
import fr.denisd3d.mc2discord.core.config.StatusChannels;
import fr.denisd3d.mc2discord.core.entities.Entity;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Timer;
import java.util.function.Supplier;

public class StatusManager {
    private static Timer timer;
    private static final List<ScheduledDiscordUpdate> updates = new ArrayList<>();

    public static synchronized void init() {
        stop();
        timer = new Timer("Mc2Discord status updates", true);
        Mc2Discord discord = Mc2Discord.INSTANCE;

        if (!discord.config.style.presence.message.getValues().get(0).isEmpty() && discord.config.style.presence.update != 0) {
            schedule(() -> updatePresence(discord), "bot presence", discord.config.style.presence.update);
        }

        for (StatusChannels.StatusChannel statusChannel : discord.config.statusChannels.channels) {
            if (statusChannel.channel_id.equals(M2DUtils.NIL_SNOWFLAKE) || statusChannel.update_period == 0 || (statusChannel.name_message.getValues().get(0).isEmpty() && statusChannel.topic_message.getValues().get(0).isEmpty())) {
                continue;
            }
            schedule(() -> updateChannel(discord, statusChannel), "channel " + statusChannel.channel_id.asString(), statusChannel.update_period);
        }
    }

    private static void schedule(Supplier<Mono<Void>> update, String description, long periodSeconds) {
        ScheduledDiscordUpdate task = new ScheduledDiscordUpdate(
                () -> M2DUtils.isNotConfigured() ? Mono.empty() : update.get(),
                failure -> Mc2Discord.LOGGER.error("Failed to update Discord {}; another update will be attempted at the next scheduled interval", description, failure));
        updates.add(task);
        timer.schedule(task, 0, periodSeconds * 1000);
    }

    public static synchronized void stop() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
        updates.forEach(ScheduledDiscordUpdate::cancel);
        updates.clear();
    }

    private static Mono<Void> updateChannel(Mc2Discord discord, StatusChannels.StatusChannel statusChannel) {
        String nameMessage = statusChannel.name_message.asString();
        String topicMessage = statusChannel.topic_message.asString();

        return discord.client.rest()
                .getChannelById(statusChannel.channel_id)
                .modify(ChannelModifyRequest.builder()
                        .name(!nameMessage.isEmpty() ? Possible.of(Entity.replace(nameMessage, Collections.emptyList())) : Possible.absent())
                        .topic(!topicMessage.isEmpty() ? Possible.of(Entity.replace(topicMessage, Collections.emptyList())) : Possible.absent())
                        .build(), null)
                .then();
    }

    private static Mono<Void> updatePresence(Mc2Discord discord) {
        var presence = discord.config.style.presence;
        String message = Entity.replace(presence.message.asString(), Collections.emptyList());
        ClientActivity activity = switch (presence.type) {
            case "PLAYING", "LISTENING", "WATCHING" ->
                    ClientActivity.of(Activity.Type.valueOf(presence.type), message, null);
            case "STREAMING" -> ClientActivity.streaming(message, presence.link.isEmpty() ? null : presence.link);
            case "CUSTOM" -> ClientActivity.custom(message);
            default -> ClientActivity.playing(message);
        };
        return discord.client.updatePresence(ClientPresence.online(activity));
    }
}
