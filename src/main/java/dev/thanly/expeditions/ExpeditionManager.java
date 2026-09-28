package dev.thanly.expeditions;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Enviar mascotas, ver cuando vuelven, calcular el exito y repartir el botin. */
public final class ExpeditionManager {

    private final PetExpeditions plugin;
    private final Map<String, Expedition> expeditions = new LinkedHashMap<>();

    public ExpeditionManager(PetExpeditions plugin) {
        this.plugin = plugin;
    }

    public void load(FileConfiguration config) {
        expeditions.clear();
        ConfigurationSection sec = config.getConfigurationSection("expeditions");
        if (sec == null) {
            plugin.getLogger().warning("No expeditions found in config.yml.");
            return;
        }
        for (String id : sec.getKeys(false)) {
            ConfigurationSection s = sec.getConfigurationSection(id);
            if (s != null) {
                expeditions.put(id, Expedition.read(id, s, plugin.getLogger()));
            }
        }
    }

    public Collection<Expedition> all() {
        return expeditions.values();
    }

    public Expedition get(String id) {
        return id == null ? null : expeditions.get(id);
    }

    // ---------- numeros ----------

    /** Probabilidad de exito en % (nivel, rareza y sinergia suben la base). Entre 5 y 95. */
    public int chance(Pet pet, Expedition exp) {
        Settings cfg = plugin.settings();
        double c = exp.baseChance() + (pet.level() - 1) * cfg.levelBonus()
                + plugin.customPets().rarityOf(pet).successBonus();
        if (exp.synergyWith(pet.type())) {
            c += cfg.synergyBonus();
        }
        return (int) Math.round(Math.max(5, Math.min(95, c)));
    }

    public long durationMillis(Expedition exp) {
        // En modo prueba cada "minuto" dura un segundo, para probar rapido.
        return exp.durationMinutes() * (plugin.settings().testMode() ? 1000L : 60_000L);
    }

    public int xpNeeded(int level) {
        return plugin.settings().xpPerLevel() * level;
    }

    /** Cuantas expediciones puede tener a la vez (config, o el permiso petexpeditions.slots.N). */
    public int maxActive(Player player) {
        int max = plugin.settings().maxActive();
        for (int n = 20; n > max; n--) {
            String node = "petexpeditions.slots." + n;
            if (player.isPermissionSet(node) && player.hasPermission(node)) {
                return n;
            }
        }
        return max;
    }

    public int activeCount(UUID owner) {
        int n = 0;
        for (Pet p : plugin.store().pets(owner)) {
            if (p.busy()) {
                n++;
            }
        }
        return n;
    }

    public int readyCount(UUID owner) {
        long now = System.currentTimeMillis();
        int n = 0;
        for (Pet p : plugin.store().pets(owner)) {
            if (p.ready(now)) {
                n++;
            }
        }
        return n;
    }

    // ---------- acciones ----------

    public boolean send(Player player, Pet pet, Expedition exp) {
        Messages msg = plugin.messages();
        String petName = msg.petName(pet);
        if (pet.busy()) {
            msg.send(player, "pet-busy", Messages.ph("pet", petName));
            return false;
        }
        if (pet.level() < exp.requiredLevel()) {
            msg.send(player, "pet-level-too-low", Messages.ph("pet", petName), Messages.ph("level", exp.requiredLevel()));
            return false;
        }
        int max = maxActive(player);
        if (activeCount(player.getUniqueId()) >= max) {
            msg.send(player, "no-free-slots", Messages.ph("max", max));
            return false;
        }
        long duration = durationMillis(exp);
        pet.startExpedition(exp.id(), System.currentTimeMillis() + duration);
        plugin.store().save(player.getUniqueId());
        msg.send(player, "sent", Messages.ph("pet", petName), Messages.comp("expedition", msg.parse(exp.name())),
                Messages.ph("time", Messages.time(duration)), Messages.ph("chance", chance(pet, exp)));
        sound(player, Sound.ENTITY_WOLF_AMBIENT, 1f, 1.2f);
        return true;
    }

    /** Reclama el botin de una mascota que ya volvio. */
    public void claim(Player player, Pet pet) {
        Messages msg = plugin.messages();
        if (!pet.ready(System.currentTimeMillis())) {
            return;
        }
        Expedition exp = get(pet.expedition());
        String petName = msg.petName(pet);
        pet.finishExpedition();
        if (exp == null) {
            // la expedicion se borro de config.yml mientras la mascota estaba fuera
            msg.send(player, "expedition-removed", Messages.ph("pet", petName));
            plugin.store().save(player.getUniqueId());
            return;
        }

        boolean synergy = exp.synergyWith(pet.type());
        boolean success = ThreadLocalRandom.current().nextDouble(100) < chance(pet, exp);
        Component expName = msg.parse(exp.name());
        int xp;
        if (success) {
            msg.send(player, "claim-success", Messages.ph("pet", petName), Messages.comp("expedition", expName));
            giveRewards(player, exp, synergy);
            xp = exp.petXp();
            sound(player, Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.6f);
        } else {
            msg.send(player, "claim-fail", Messages.ph("pet", petName), Messages.comp("expedition", expName));
            xp = exp.petXp() * plugin.settings().failXpPercent() / 100;
            sound(player, Sound.ENTITY_WOLF_WHINE, 1f, 1f);
        }
        addXp(player, pet, xp);
        plugin.store().save(player.getUniqueId());
    }

    public int claimAll(Player player) {
        long now = System.currentTimeMillis();
        int n = 0;
        for (Pet p : List.copyOf(plugin.store().pets(player.getUniqueId()))) {
            if (p.ready(now)) {
                claim(player, p);
                n++;
            }
        }
        if (n == 0) {
            plugin.messages().send(player, "nothing-to-claim");
        }
        return n;
    }

    private void giveRewards(Player player, Expedition exp, boolean synergy) {
        Messages msg = plugin.messages();
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        boolean any = false;
        for (Expedition.Reward r : exp.rewards()) {
            if (rnd.nextDouble(100) >= r.chance()) {
                continue;
            }
            any = true;
            if (r.command() != null) {
                String cmd = r.command().replace("{player}", player.getName());
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.startsWith("/") ? cmd.substring(1) : cmd);
                msg.sendPlain(player, "loot-line-command", Messages.comp("reward", msg.parse(r.display())));
                continue;
            }
            if (r.pet() != null) {
                CustomPets.Def def = plugin.customPets().get(r.pet());
                if (def == null) {
                    plugin.getLogger().warning("Expedition '" + exp.id() + "' gives unknown custom pet '" + r.pet() + "'.");
                    continue;
                }
                give(player, plugin.customPets().egg(def, 1, 0, null));
                msg.sendPlain(player, "loot-line-egg", Messages.comp("pet", msg.parse(
                        plugin.customPets().rarity(def.rarity()).color() + def.name())),
                        Messages.comp("rarity", plugin.customPets().rarityLabel(def.rarity())));
                sound(player, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                continue;
            }
            int amount = rnd.nextInt(r.min(), r.max() + 1);
            if (synergy) {
                amount = (int) Math.ceil(amount * plugin.settings().synergyLoot());
            }
            give(player, new ItemStack(r.item(), amount));
            msg.sendPlain(player, "loot-line", Messages.ph("amount", amount),
                    Messages.comp("item", Component.translatable(r.item().translationKey())));
        }
        if (!any) {
            msg.sendPlain(player, "loot-empty");
        }
    }

    /** Da un objeto; si el inventario esta lleno, lo que sobra cae al suelo. */
    public void give(Player player, ItemStack stack) {
        for (ItemStack left : player.getInventory().addItem(stack).values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), left);
        }
    }

    private void addXp(Player player, Pet pet, int xp) {
        Settings cfg = plugin.settings();
        Messages msg = plugin.messages();
        if (xp <= 0 || pet.level() >= cfg.maxLevel()) {
            return;
        }
        pet.setXp(pet.xp() + xp);
        msg.sendPlain(player, "xp-gained", Messages.ph("pet", msg.petName(pet)), Messages.ph("xp", xp));
        while (pet.level() < cfg.maxLevel() && pet.xp() >= xpNeeded(pet.level())) {
            pet.setXp(pet.xp() - xpNeeded(pet.level()));
            pet.setLevel(pet.level() + 1);
            msg.send(player, "level-up", Messages.ph("pet", msg.petName(pet)), Messages.ph("level", pet.level()));
            sound(player, Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.6f, 1.4f);
        }
        if (pet.level() >= cfg.maxLevel()) {
            pet.setXp(0);
        }
    }

    // ---------- aviso cuando vuelven ----------

    /** Cada 5 segundos mira si alguna mascota de un jugador conectado ya volvio. */
    public void startReturnTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();
            for (Player player : Bukkit.getOnlinePlayers()) {
                boolean changed = false;
                for (Pet p : plugin.store().pets(player.getUniqueId())) {
                    if (p.ready(now) && !p.notified()) {
                        p.setNotified(true);
                        changed = true;
                        Expedition exp = get(p.expedition());
                        Component expName = exp == null ? Component.text(p.expedition()) : plugin.messages().parse(exp.name());
                        plugin.messages().send(player, "returned", Messages.ph("pet", plugin.messages().petName(p)),
                                Messages.comp("expedition", expName));
                        sound(player, Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 1.2f);
                    }
                }
                if (changed) {
                    plugin.store().save(player.getUniqueId());
                }
            }
        }, 100L, 100L);
    }

    public void sound(Player player, Sound sound, float volume, float pitch) {
        if (plugin.settings().sounds()) {
            player.playSound(player.getLocation(), sound, volume, pitch);
        }
    }
}
