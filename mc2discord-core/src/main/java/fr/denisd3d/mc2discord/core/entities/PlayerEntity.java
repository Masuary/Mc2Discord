package fr.denisd3d.mc2discord.core.entities;

import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;

public class PlayerEntity extends Entity {
    private static final String prefix = "player_";

    public final String name;
    public final String displayName;
    public final UUID uuid;
    private final Map<String, String> extraReplacements;

    public PlayerEntity(String name, String displayName, UUID uuid) {
        this(name, displayName, uuid, Map.of());
    }

    public PlayerEntity(String name, String displayName, UUID uuid, Map<String, String> extraReplacements) {
        this.name = name;
        this.displayName = displayName.replaceAll("§.", "");
        this.uuid = uuid;
        this.extraReplacements = extraReplacements;
    }

    @Override
    public void getReplacements(Map<String, String> replacements, Map<String, BiFunction<String, String, String>> formatters) {
        replacements.put(prefix + "name", this.name);
        replacements.put(prefix + "display_name", this.displayName);
        replacements.put(prefix + "uuid", this.uuid != null ? this.uuid.toString() : "");
        for (var entry : extraReplacements.entrySet()) {
            replacements.put(prefix + entry.getKey(), entry.getValue());
        }
    }
}
