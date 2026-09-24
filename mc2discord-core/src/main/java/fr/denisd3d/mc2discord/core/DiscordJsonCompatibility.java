package fr.denisd3d.mc2discord.core;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.deser.BeanDeserializerBuilder;
import com.fasterxml.jackson.databind.deser.BeanDeserializerModifier;
import com.fasterxml.jackson.databind.deser.SettableBeanProperty;
import com.fasterxml.jackson.databind.deser.impl.NullsConstantProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import discord4j.common.JacksonResources;
import discord4j.discordjson.json.ChannelData;

import java.util.Iterator;

public final class DiscordJsonCompatibility {
    private DiscordJsonCompatibility() {
    }

    public static JacksonResources createJacksonResources() {
        SimpleModule compatibilityModule = new SimpleModule("Mc2DiscordChannelNullIds");
        compatibilityModule.setDeserializerModifier(new BeanDeserializerModifier() {
            @Override
            public BeanDeserializerBuilder updateBuilder(DeserializationConfig config,
                                                         BeanDescription beanDescription,
                                                         BeanDeserializerBuilder builder) {
                if (!ChannelData.class.isAssignableFrom(beanDescription.getBeanClass())) {
                    return builder;
                }

                Iterator<SettableBeanProperty> properties = builder.getProperties();
                while (properties.hasNext()) {
                    SettableBeanProperty property = properties.next();
                    if ("application_id".equals(property.getName()) || "owner_id".equals(property.getName())) {
                        // discord-json 1.7.7 models these as Possible<Id>, which cannot hold explicit nulls.
                        // Preserve null semantics for other fields such as parent_id and topic.
                        builder.addOrReplaceProperty(property.withNullProvider(NullsConstantProvider.skipper()), true);
                    }
                }
                return builder;
            }
        });
        return JacksonResources.create().withMapperFunction(mapper -> mapper.registerModule(compatibilityModule));
    }
}
