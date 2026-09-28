package dev.thanly.expeditions;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Las opciones generales de config.yml (las expediciones se leen aparte). */
public record Settings(
        String language,
        int maxPets,
        int maxActive,
        int maxLevel,
        int xpPerLevel,
        double levelBonus,
        double synergyBonus,
        double synergyLoot,
        int failXpPercent,
        boolean sounds,
        boolean testMode,
        List<String> petTypes) {

    public static Settings from(FileConfiguration c) {
        List<String> types = new ArrayList<>();
        for (String t : c.getStringList("pet-types")) {
            String up = t.toUpperCase(Locale.ROOT);
            if (PetType.isSupported(up)) {
                types.add(up);
            }
        }
        if (types.isEmpty()) {
            types.addAll(List.of("WOLF", "CAT", "PARROT"));
        }
        return new Settings(
                c.getString("language", "en"),
                clamp(c.getInt("max-pets", 12), 1, 45),
                Math.max(1, c.getInt("max-active-expeditions", 2)),
                Math.max(1, c.getInt("leveling.max-level", 10)),
                Math.max(1, c.getInt("leveling.xp-per-level", 100)),
                c.getDouble("leveling.success-bonus-per-level", 2.0),
                c.getDouble("synergy.success-bonus", 15.0),
                Math.max(1.0, c.getDouble("synergy.loot-multiplier", 1.5)),
                clamp(c.getInt("fail-xp-percent", 25), 0, 100),
                c.getBoolean("sounds", true),
                c.getBoolean("test-mode", false),
                types);
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
