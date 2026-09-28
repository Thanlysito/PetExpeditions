package dev.thanly.expeditions;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Guarda las mascotas de cada jugador en plugins/PetExpeditions/players/&lt;uuid&gt;.yml.
 * Las expediciones siguen corriendo aunque el jugador o el servidor esten apagados,
 * porque solo se guarda la hora en que terminan.
 */
public final class PetStore {

    private final PetExpeditions plugin;
    private final Map<UUID, List<Pet>> cache = new HashMap<>();

    public PetStore(PetExpeditions plugin) {
        this.plugin = plugin;
    }

    /** Las mascotas del jugador (se leen del archivo la primera vez). */
    public List<Pet> pets(UUID owner) {
        return cache.computeIfAbsent(owner, this::read);
    }

    public Pet find(UUID owner, UUID petId) {
        for (Pet p : pets(owner)) {
            if (p.id().equals(petId)) {
                return p;
            }
        }
        return null;
    }

    /** Jugadores que tienen datos cargados ahora mismo. */
    public Iterable<UUID> loaded() {
        return new ArrayList<>(cache.keySet());
    }

    private File file(UUID owner) {
        return new File(plugin.getDataFolder(), "players/" + owner + ".yml");
    }

    private List<Pet> read(UUID owner) {
        List<Pet> list = new ArrayList<>();
        File f = file(owner);
        if (!f.exists()) {
            return list;
        }
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(f);
        ConfigurationSection pets = yml.getConfigurationSection("pets");
        if (pets == null) {
            return list;
        }
        for (String key : pets.getKeys(false)) {
            ConfigurationSection s = pets.getConfigurationSection(key);
            if (s == null) {
                continue;
            }
            try {
                Pet p = new Pet(UUID.fromString(key), s.getString("type", "WOLF"));
                p.setName(s.getString("name"));
                p.setVariant(s.getString("variant"));
                p.setCollar(s.getString("collar"));
                p.setLevel(s.getInt("level", 1));
                p.setXp(s.getInt("xp", 0));
                String exp = s.getString("expedition");
                if (exp != null) {
                    p.startExpedition(exp, s.getLong("ends-at"));
                    p.setNotified(s.getBoolean("notified", false));
                }
                list.add(p);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Skipping broken pet " + key + " in " + f.getName());
            }
        }
        return list;
    }

    public void save(UUID owner) {
        List<Pet> list = cache.get(owner);
        if (list == null) {
            return;
        }
        YamlConfiguration yml = new YamlConfiguration();
        for (Pet p : list) {
            String k = "pets." + p.id() + ".";
            yml.set(k + "type", p.type());
            yml.set(k + "name", p.name());
            yml.set(k + "variant", p.variant());
            yml.set(k + "collar", p.collar());
            yml.set(k + "level", p.level());
            yml.set(k + "xp", p.xp());
            if (p.busy()) {
                yml.set(k + "expedition", p.expedition());
                yml.set(k + "ends-at", p.endsAt());
                yml.set(k + "notified", p.notified());
            }
        }
        try {
            File f = file(owner);
            f.getParentFile().mkdirs();
            yml.save(f);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save pets of " + owner + ": " + e.getMessage());
        }
    }

    /** Cuando el jugador se va: se guarda y se libera la memoria. */
    public void unload(UUID owner) {
        save(owner);
        cache.remove(owner);
    }

    public void saveAll() {
        for (UUID owner : loaded()) {
            save(owner);
        }
    }
}
