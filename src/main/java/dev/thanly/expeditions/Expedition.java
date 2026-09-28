package dev.thanly.expeditions;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/** Una expedicion definida en config.yml. */
public record Expedition(
        String id,
        String name,
        Material icon,
        int durationMinutes,
        int requiredLevel,
        List<String> synergy,
        double baseChance,
        int petXp,
        List<String> description,
        List<Reward> rewards) {

    /**
     * Un posible premio: un objeto con cantidad al azar, un comando de consola,
     * o el huevo de una mascota propia (pet = id de custom-pets).
     */
    public record Reward(Material item, int min, int max, double chance, String command, String display, String pet) {
    }

    /** true si este tipo de mascota tiene sinergia (bonificacion) con la expedicion. */
    public boolean synergyWith(String petType) {
        for (String s : synergy) {
            if (s.equalsIgnoreCase(petType)) {
                return true;
            }
        }
        return false;
    }

    public static Expedition read(String id, ConfigurationSection s, Logger log) {
        Material icon = Material.matchMaterial(s.getString("icon", "MAP"));
        if (icon == null || !icon.isItem()) {
            log.warning("Expedition '" + id + "': unknown icon, using MAP.");
            icon = Material.MAP;
        }
        // synergy puede ser un solo tipo (WOLF) o una lista ([WOLF, baby_dragon])
        List<String> syn = new ArrayList<>();
        List<String> raw = s.isList("synergy") ? s.getStringList("synergy") : List.of(s.getString("synergy", "NONE"));
        for (String t : raw) {
            String up = t.trim().toUpperCase(Locale.ROOT);
            if (!up.isEmpty() && !up.equals("NONE")) {
                syn.add(PetType.isSupported(up) ? up : t.trim().toLowerCase(Locale.ROOT));
            }
        }
        List<Reward> rewards = new ArrayList<>();
        for (Map<?, ?> m : s.getMapList("rewards")) {
            Reward r = readReward(id, m, log);
            if (r != null) {
                rewards.add(r);
            }
        }
        return new Expedition(
                id,
                s.getString("name", id),
                icon,
                Math.max(1, s.getInt("duration-minutes", 10)),
                Math.max(1, s.getInt("required-level", 1)),
                syn,
                s.getDouble("success-chance", 80),
                Math.max(0, s.getInt("pet-xp", 20)),
                s.getStringList("description"),
                rewards);
    }

    private static Reward readReward(String expId, Map<?, ?> m, Logger log) {
        double chance = number(m.get("chance"), 100);
        Object cmd = m.get("command");
        if (cmd != null) {
            Object display = m.get("display");
            return new Reward(null, 0, 0, chance, cmd.toString(), display == null ? cmd.toString() : display.toString(), null);
        }
        Object pet = m.get("pet");
        if (pet != null) {
            return new Reward(null, 1, 1, chance, null, null, pet.toString().toLowerCase(Locale.ROOT));
        }
        Object itemName = m.get("item");
        Material mat = itemName == null ? null : Material.matchMaterial(itemName.toString());
        if (mat == null || !mat.isItem()) {
            log.warning("Expedition '" + expId + "': unknown reward item '" + itemName + "', skipped.");
            return null;
        }
        // amount puede ser "3" o "1-4"
        String amount = String.valueOf(m.get("amount") == null ? "1" : m.get("amount"));
        int min;
        int max;
        try {
            String[] parts = amount.split("-");
            min = Integer.parseInt(parts[0].trim());
            max = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : min;
        } catch (NumberFormatException e) {
            min = 1;
            max = 1;
        }
        min = Math.max(1, min);
        max = Math.max(min, max);
        return new Reward(mat, min, max, chance, null, null, null);
    }

    private static double number(Object o, double def) {
        if (o instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return o == null ? def : Double.parseDouble(o.toString());
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
