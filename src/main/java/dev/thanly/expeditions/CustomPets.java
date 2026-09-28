package dev.thanly.expeditions;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Mascotas propias del plugin (no existen en Minecraft normal): Dragon bebe, Fenix...
 * Se consiguen como huevo en el botin de las expediciones y se abren con clic derecho.
 */
public final class CustomPets {

    /** Una mascota propia definida en config.yml. */
    public record Def(String id, String name, String rarity, Material icon, String texture, List<String> description) {
    }

    /** Una rareza: color y bonificacion de exito. */
    public record Rarity(String id, String color, double successBonus) {
    }

    private final PetExpeditions plugin;
    private final Map<String, Def> pets = new LinkedHashMap<>();
    private final Map<String, Rarity> rarities = new LinkedHashMap<>();
    private final NamespacedKey eggKey;
    private final NamespacedKey levelKey;
    private final NamespacedKey xpKey;
    private final NamespacedKey nameKey;

    public CustomPets(PetExpeditions plugin) {
        this.plugin = plugin;
        this.eggKey = new NamespacedKey(plugin, "pet_egg");
        this.levelKey = new NamespacedKey(plugin, "pet_level");
        this.xpKey = new NamespacedKey(plugin, "pet_xp");
        this.nameKey = new NamespacedKey(plugin, "pet_name");
    }

    public void load(FileConfiguration config) {
        rarities.clear();
        ConfigurationSection rs = config.getConfigurationSection("rarities");
        if (rs != null) {
            for (String id : rs.getKeys(false)) {
                ConfigurationSection s = rs.getConfigurationSection(id);
                if (s != null) {
                    String up = id.toUpperCase(Locale.ROOT);
                    rarities.put(up, new Rarity(up, s.getString("color", "<white>"), s.getDouble("success-bonus", 0)));
                }
            }
        }
        rarities.putIfAbsent("COMMON", new Rarity("COMMON", "<white>", 0));

        pets.clear();
        ConfigurationSection ps = config.getConfigurationSection("custom-pets");
        if (ps == null) {
            return;
        }
        for (String rawId : ps.getKeys(false)) {
            ConfigurationSection s = ps.getConfigurationSection(rawId);
            String id = rawId.toLowerCase(Locale.ROOT);
            if (s == null || PetType.isSupported(id.toUpperCase(Locale.ROOT))) {
                continue; // no se puede llamar igual que un animal normal (wolf, cat, parrot)
            }
            Material icon = Material.matchMaterial(s.getString("icon", "PLAYER_HEAD"));
            if (icon == null || !icon.isItem()) {
                plugin.getLogger().warning("Custom pet '" + id + "': unknown icon, using PLAYER_HEAD.");
                icon = Material.PLAYER_HEAD;
            }
            String rarity = s.getString("rarity", "COMMON").toUpperCase(Locale.ROOT);
            if (!rarities.containsKey(rarity)) {
                rarity = "COMMON";
            }
            pets.put(id, new Def(id, s.getString("name", id), rarity, icon, s.getString("texture"),
                    s.getStringList("description")));
        }
    }

    public Collection<Def> all() {
        return pets.values();
    }

    public Def get(String id) {
        return id == null ? null : pets.get(id.toLowerCase(Locale.ROOT));
    }

    /** true si el tipo es una mascota propia (y no un lobo, gato o loro). */
    public boolean isCustom(String type) {
        return type != null && !PetType.isSupported(type);
    }

    public Rarity rarity(String rarityId) {
        Rarity r = rarities.get(rarityId);
        return r != null ? r : rarities.get("COMMON");
    }

    /** Rareza de una mascota: las propias tienen la suya; las normales son comunes. */
    public Rarity rarityOf(Pet pet) {
        Def d = isCustom(pet.type()) ? get(pet.type()) : null;
        return rarity(d == null ? "COMMON" : d.rarity());
    }

    /** "Legendaria" en el color de la rareza. */
    public Component rarityLabel(String rarityId) {
        Messages msg = plugin.messages();
        Rarity r = rarity(rarityId);
        return msg.parse(r.color() + msg.raw("rarity." + r.id().toLowerCase(Locale.ROOT)));
    }

    // ---------- iconos ----------

    /** El objeto que representa a la mascota en los menus. */
    public ItemStack iconItem(String type) {
        if (!isCustom(type)) {
            return new ItemStack(PetType.icon(type));
        }
        Def d = get(type);
        if (d == null) {
            return new ItemStack(Material.PAPER); // la quitaron de config.yml
        }
        ItemStack stack = new ItemStack(d.icon());
        applyTexture(stack, d.texture());
        return stack;
    }

    /**
     * Si el icono es una cabeza de jugador y hay "texture", le pone esa piel.
     * Acepta el valor largo en base64 o solo el codigo de textures.minecraft.net.
     */
    private void applyTexture(ItemStack stack, String texture) {
        if (texture == null || texture.isBlank() || stack.getType() != Material.PLAYER_HEAD) {
            return;
        }
        String value = texture.trim();
        if (value.matches("[0-9a-fA-F]{40,80}")) {
            String json = "{\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/" + value + "\"}}}";
            value = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        }
        SkullMeta meta = (SkullMeta) stack.getItemMeta();
        PlayerProfile profile = Bukkit.createProfile(UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8)), "PetExpeditions");
        profile.setProperty(new ProfileProperty("textures", value));
        meta.setPlayerProfile(profile);
        stack.setItemMeta(meta);
    }

    // ---------- huevos ----------

    /** Crea un huevo de mascota. level/xp/name permiten guardar el progreso al volver a convertirla en huevo. */
    public ItemStack egg(Def d, int level, int xp, String name) {
        Messages msg = plugin.messages();
        ItemStack stack = iconItem(d.id());
        ItemMeta meta = stack.getItemMeta();
        Rarity r = rarity(d.rarity());
        meta.displayName(msg.parse(r.color() + msg.raw("egg-name").replace("<pet>", name != null ? name : d.name()))
                .decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(msg.get("egg-rarity", Messages.comp("rarity", rarityLabel(d.rarity()))).decoration(TextDecoration.ITALIC, false));
        if (level > 1 || xp > 0) {
            lore.add(msg.get("egg-level", Messages.ph("level", level)).decoration(TextDecoration.ITALIC, false));
        }
        for (String line : d.description()) {
            lore.add(msg.parse(line).decoration(TextDecoration.ITALIC, false));
        }
        lore.add(Component.empty());
        lore.add(msg.get("egg-use").decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        meta.setEnchantmentGlintOverride(true);
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(eggKey, PersistentDataType.STRING, d.id());
        pdc.set(levelKey, PersistentDataType.INTEGER, level);
        pdc.set(xpKey, PersistentDataType.INTEGER, xp);
        if (name != null) {
            pdc.set(nameKey, PersistentDataType.STRING, name);
        }
        stack.setItemMeta(meta);
        return stack;
    }

    /** Si el objeto es un huevo de mascota, devuelve su id; si no, null. */
    public String eggId(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return null;
        }
        return stack.getItemMeta().getPersistentDataContainer().get(eggKey, PersistentDataType.STRING);
    }

    /** Crea la mascota que sale del huevo (con el nivel que tenia, si lo tenia). */
    public Pet hatch(ItemStack egg) {
        String id = eggId(egg);
        if (id == null) {
            return null;
        }
        PersistentDataContainer pdc = egg.getItemMeta().getPersistentDataContainer();
        Pet pet = new Pet(UUID.randomUUID(), id);
        pet.setLevel(pdc.getOrDefault(levelKey, PersistentDataType.INTEGER, 1));
        pet.setXp(pdc.getOrDefault(xpKey, PersistentDataType.INTEGER, 0));
        pet.setName(pdc.get(nameKey, PersistentDataType.STRING));
        return pet;
    }
}
