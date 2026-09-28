package dev.thanly.expeditions;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Textos del plugin. Se leen de plugins/PetExpeditions/lang/&lt;idioma&gt;.yml y se
 * convierten con MiniMessage (colores, negritas, etc.).
 */
public final class Messages {

    private static final String[] BUNDLED = {"en", "es"};

    private final PetExpeditions plugin;
    private final MiniMessage mini = MiniMessage.miniMessage();
    private YamlConfiguration lang = new YamlConfiguration();
    private YamlConfiguration fallback = new YamlConfiguration();

    public Messages(PetExpeditions plugin) {
        this.plugin = plugin;
    }

    public void load(String language) {
        // Copia los idiomas incluidos la primera vez, para que el dueno del servidor los pueda editar.
        for (String code : BUNDLED) {
            File file = new File(plugin.getDataFolder(), "lang/" + code + ".yml");
            if (!file.exists()) {
                plugin.saveResource("lang/" + code + ".yml", false);
            }
        }
        fallback = readBundled("en");
        File file = new File(plugin.getDataFolder(), "lang/" + language + ".yml");
        if (file.exists()) {
            lang = YamlConfiguration.loadConfiguration(file);
        } else {
            plugin.getLogger().warning("Language file lang/" + language + ".yml not found, using English.");
            lang = fallback;
        }
    }

    private YamlConfiguration readBundled(String code) {
        InputStream in = plugin.getResource("lang/" + code + ".yml");
        if (in == null) {
            return new YamlConfiguration();
        }
        return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
    }

    public String raw(String key) {
        String value = lang.getString(key);
        if (value == null) {
            value = fallback.getString(key, key);
        }
        return value;
    }

    /** Un texto sin prefijo (menus, titulos, lineas de descripcion). */
    public Component get(String key, TagResolver... resolvers) {
        return mini.deserialize(raw(key), resolvers);
    }

    /** Una lista de lineas del idioma (para descripciones largas en los menus). */
    public java.util.List<Component> lines(String key, TagResolver... resolvers) {
        java.util.List<String> raw = lang.getStringList(key);
        if (raw.isEmpty()) {
            raw = fallback.getStringList(key);
        }
        java.util.List<Component> out = new java.util.ArrayList<>();
        for (String line : raw) {
            out.add(mini.deserialize(line, resolvers));
        }
        return out;
    }

    /** Convierte un texto MiniMessage que viene de config.yml (nombres de expediciones, etc.). */
    public Component parse(String text, TagResolver... resolvers) {
        return mini.deserialize(text == null ? "" : text, resolvers);
    }

    public void send(CommandSender to, String key, TagResolver... resolvers) {
        to.sendMessage(mini.deserialize(raw("prefix")).append(get(key, resolvers)));
    }

    /** Igual que send, pero sin el prefijo (para las lineas de botin, que van debajo de otro mensaje). */
    public void sendPlain(CommandSender to, String key, TagResolver... resolvers) {
        to.sendMessage(get(key, resolvers));
    }

    /** Variable de texto simple, por ejemplo &lt;pet&gt; o &lt;time&gt;. */
    public static TagResolver ph(String name, Object value) {
        return Placeholder.unparsed(name, String.valueOf(value));
    }

    /** Variable que ya es un Component (nombre con colores, objeto traducido...). */
    public static TagResolver comp(String name, Component value) {
        return Placeholder.component(name, value);
    }

    /** Nombre del tipo de mascota en el idioma elegido: "Lobo", "Wolf"... */
    public String typeName(String type) {
        CustomPets.Def custom = plugin.customPets().isCustom(type) ? plugin.customPets().get(type) : null;
        if (custom != null) {
            return custom.name();
        }
        if (plugin.customPets().isCustom(type)) {
            return type; // mascota propia que ya no esta en config.yml
        }
        return raw("pet-type." + type.toLowerCase(java.util.Locale.ROOT));
    }

    /** Nombre para mostrar de una mascota: el que le puso el jugador, o el del tipo. */
    public String petName(Pet pet) {
        return pet.name() != null ? pet.name() : typeName(pet.type());
    }

    /** "1h 05m", "12m 30s", "45s". */
    public static String time(long millis) {
        long s = Math.max(0, (millis + 999) / 1000);
        long h = s / 3600;
        long m = (s % 3600) / 60;
        long sec = s % 60;
        if (h > 0) {
            return h + "h " + String.format("%02dm", m);
        }
        if (m > 0) {
            return m + "m " + String.format("%02ds", sec);
        }
        return sec + "s";
    }
}
