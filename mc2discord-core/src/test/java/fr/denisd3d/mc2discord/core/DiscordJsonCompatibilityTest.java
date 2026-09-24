package fr.denisd3d.mc2discord.core;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import discord4j.discordjson.json.ChannelData;
import discord4j.discordjson.json.ChannelModifyRequest;
import discord4j.discordjson.possible.Possible;
import org.junit.Test;

import java.io.IOException;
import java.util.Optional;

import static org.junit.Assert.*;

public class DiscordJsonCompatibilityTest {
    private final ObjectMapper mapper = DiscordJsonCompatibility.createJacksonResources().getObjectMapper();

    @Test
    public void acceptsNullApplicationAndOwnerIdsInGuildChannelList() throws IOException {
        ChannelData[] channels = mapper.readValue("""
                [{"id":"101","type":0,"name":"chat"},
                 {"id":"102","type":2,"application_id":null},
                 {"id":"103","type":2,"owner_id":null},
                 {"id":"104","type":2,"application_id":null,"owner_id":null}]
                """, ChannelData[].class);

        assertEquals(4, channels.length);
        assertTrue(channels[1].applicationId().isAbsent());
        assertTrue(channels[2].ownerId().isAbsent());
        assertTrue(channels[3].applicationId().isAbsent());
        assertTrue(channels[3].ownerId().isAbsent());
    }

    @Test
    public void preservesPresentAndOmittedIds() throws IOException {
        ChannelData channel = mapper.readValue("""
                {"id":"101","type":2,"guild_id":"201","application_id":"301","owner_id":"401"}
                """, ChannelData.class);

        assertEquals(201L, channel.guildId().get().asLong());
        assertEquals(301L, channel.applicationId().get().asLong());
        assertEquals(401L, channel.ownerId().get().asLong());

        ChannelData omitted = mapper.readValue("{\"id\":\"102\",\"type\":0}", ChannelData.class);
        assertTrue(omitted.applicationId().isAbsent());
        assertTrue(omitted.ownerId().isAbsent());
        assertTrue(omitted.parentId().isAbsent());
        assertTrue(omitted.topic().isAbsent());
    }

    @Test
    public void preservesExplicitNullsOnNullableChannelFields() throws IOException {
        ChannelData channel = mapper.readValue("""
                {"id":"101","type":0,"parent_id":null,"last_message_id":null,"topic":null}
                """, ChannelData.class);

        assertFalse(channel.parentId().isAbsent());
        assertEquals(Optional.empty(), channel.parentId().get());
        assertFalse(channel.lastMessageId().isAbsent());
        assertEquals(Optional.empty(), channel.lastMessageId().get());
        assertFalse(channel.topic().isAbsent());
        assertEquals(Optional.empty(), channel.topic().get());
    }

    @Test
    public void preservesOutgoingParentClearing() throws IOException {
        ChannelModifyRequest request = ChannelModifyRequest.builder().parentId(Possible.of(Optional.empty())).build();
        var json = mapper.readTree(mapper.writeValueAsString(request));

        assertTrue(json.has("parent_id"));
        assertTrue(json.get("parent_id").isNull());
        assertFalse(json.has("name"));
    }

    @Test
    public void rejectsMalformedNonNullIds() {
        assertThrows(JsonMappingException.class, () -> mapper.readValue(
                "{\"id\":\"101\",\"type\":2,\"application_id\":\"invalid\"}", ChannelData.class));
    }
}
