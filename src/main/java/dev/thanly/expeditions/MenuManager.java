package dev.thanly.expeditions;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Los tres menus tipo cofre:
 * el tablero de expediciones, elegir mascota, y mis mascotas.
 */
public final class MenuManager {

    private static final int SIZE = 54;
    private static final int MAX_ITEMS = 45; // 5 filas de contenido + 1 fila de botones
    private static final int SLOT_INFO = 45;
    private static final int SLOT_BACK = 45;
    private static final int SLOT_PETS = 47;
    private static final int SLOT_CLOSE = 49;
    private static final int SLOT_CLAIM_ALL = 51;

    private final PetExpeditions plugin;

    public MenuManager(PetExpeditions plugin) {
        this.plugin = plugin;
    }

    // ---------- tablero de expediciones ----------

    public void openBoard(Player player) {
        Messages msg = plugin.messages();
        ExpeditionManager em = plugin.expeditions();
        MenuHolder holder = new MenuHolder(MenuHolder.Kind.BOARD, null);
        Inventory inv = create(holder, msg.get("menu-title-board"));

        List<Integer> slots = boardSlots(em.all().size());
        int index = 0;
        for (Expedition exp : em.all()) {
            if (index >= slots.size()) {
                break;
            }
            int slot = slots.get(index++);
            List<Component> lore = new ArrayList<>();
            for (String line : exp.description()) {
                lore.add(msg.parse(line));
            }
            if (!exp.description().isEmpty()) {
                lore.add(Component.empty());
            }
            lore.add(msg.get("exp-duration", Messages.ph("time", Messages.time(em.durationMillis(exp)))));
            lore.add(msg.get("exp-level", Messages.ph("level", exp.requiredLevel())));
            if (exp.synergy().isEmpty()) {
                lore.add(msg.get("exp-synergy-none"));
            } else {
                List<String> names = new ArrayList<>();
                for (String t : exp.synergy()) {
                    names.add(msg.typeName(t));
                }
                lore.add(msg.get("exp-synergy", Messages.ph("type", String.join(", ", names))));
            }
            lore.add(msg.get("exp-chance", Messages.ph("chance", (int) exp.baseChance())));
            lore.add(msg.get("exp-xp", Messages.ph("xp", exp.petXp())));
            if (!exp.rewards().isEmpty()) {
                lore.add(Component.empty());
                lore.add(msg.get("exp-loot-title"));
                for (Expedition.Reward r : exp.rewards()) {
                    lore.add(rewardLine(r));
                }
            }
            lore.add(Component.empty());
            lore.add(msg.get("exp-click"));
            inv.setItem(slot, item(exp.icon(), msg.parse(exp.name()), lore, false));
            holder.set(slot, "exp:" + exp.id());
        }

        // Fondo oscuro donde no hay expediciones, para que se vean centradas.
        ItemStack dark = item(Material.BLACK_STAINED_GLASS_PANE, Component.text(" "), List.of(), false);
        for (int i = 0; i < MAX_ITEMS; i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, dark);
            }
        }
        bottomRow(inv);
        UUID id = player.getUniqueId();
        inv.setItem(SLOT_INFO, item(Material.BOOK, msg.get("menu-info"), msg.lines("menu-info-lore"), false));
        int pets = plugin.store().pets(id).size();
        inv.setItem(SLOT_PETS, item(Material.BONE, msg.get("menu-pets",
                        Messages.ph("count", pets), Messages.ph("max", plugin.settings().maxPets())),
                List.of(msg.get("menu-pets-lore",
                        Messages.ph("active", plugin.expeditions().activeCount(id)),
                        Messages.ph("slots", plugin.expeditions().maxActive(player)))), false));
        holder.set(SLOT_PETS, "pets");
        closeButton(inv, holder);
        int ready = plugin.expeditions().readyCount(id);
        inv.setItem(SLOT_CLAIM_ALL, item(ready > 0 ? Material.CHEST : Material.ENDER_CHEST,
                msg.get("menu-claim-all", Messages.ph("count", ready)),
                List.of(msg.get(ready > 0 ? "menu-claim-all-lore" : "menu-claim-all-none")), ready > 0));
        holder.set(SLOT_CLAIM_ALL, "claimall");
        player.openInventory(inv);
    }

    /**
     * Casillas del tablero para que las expediciones queden centradas:
     * hasta 12 -> filas de hasta 4 con un espacio entre cada una;
     * hasta 21 -> filas de 7 dentro de un marco; mas -> todas las casillas.
     */
    private static List<Integer> boardSlots(int n) {
        List<Integer> out = new ArrayList<>();
        if (n <= 0) {
            return out;
        }
        if (n <= 12) {
            int rows = (n + 3) / 4;
            int perRow = (n + rows - 1) / rows;
            int[] rowIndex = rows == 1 ? new int[]{2} : rows == 2 ? new int[]{1, 3} : new int[]{1, 2, 3};
            int left = n;
            for (int r : rowIndex) {
                int count = Math.min(perRow, left);
                int start = (9 - (2 * count - 1)) / 2;
                for (int i = 0; i < count; i++) {
                    out.add(r * 9 + start + 2 * i);
                }
                left -= count;
            }
            return out;
        }
        if (n <= 21) {
            int left = n;
            for (int r = 1; r <= 3 && left > 0; r++) {
                int count = Math.min(7, left);
                int start = (9 - count) / 2;
                for (int i = 0; i < count; i++) {
                    out.add(r * 9 + start + i);
                }
                left -= count;
            }
            return out;
        }
        for (int i = 0; i < MAX_ITEMS; i++) {
            out.add(i);
        }
        return out;
    }

    private Component rewardLine(Expedition.Reward r) {
        Messages msg = plugin.messages();
        String chance = r.chance() == Math.floor(r.chance()) ? String.valueOf((int) r.chance()) : String.valueOf(r.chance());
        if (r.pet() != null) {
            CustomPets.Def def = plugin.customPets().get(r.pet());
            Component name = def == null ? Component.text(r.pet())
                    : msg.parse(plugin.customPets().rarity(def.rarity()).color() + def.name());
            return msg.get("exp-loot-egg", Messages.comp("pet", name), Messages.ph("chance", chance));
        }
        if (r.command() != null) {
            return msg.get("exp-loot-command", Messages.comp("reward", msg.parse(r.display())), Messages.ph("chance", chance));
        }
        String amount = r.min() == r.max() ? String.valueOf(r.min()) : r.min() + "-" + r.max();
        return msg.get("exp-loot-item", Messages.ph("amount", amount),
                Messages.comp("item", Component.translatable(r.item().translationKey())), Messages.ph("chance", chance));
    }

    // ---------- elegir mascota para una expedicion ----------

    public void openPick(Player player, Expedition exp) {
        Messages msg = plugin.messages();
        ExpeditionManager em = plugin.expeditions();
        List<Pet> pets = new ArrayList<>(plugin.store().pets(player.getUniqueId()));
        if (pets.isEmpty()) {
            player.closeInventory();
            msg.send(player, "no-pets");
            return;
        }
        // Las mejores primero: libres y con nivel suficiente, luego por probabilidad de exito.
        pets.sort(Comparator.comparing((Pet p) -> !canGo(p, exp)).thenComparing(p -> -em.chance(p, exp)));

        MenuHolder holder = new MenuHolder(MenuHolder.Kind.PICK, exp.id());
        Inventory inv = create(holder, msg.get("menu-title-pick", Messages.comp("expedition", msg.parse(exp.name()))));
        long now = System.currentTimeMillis();
        boolean bestShown = false;
        int slot = 0;
        for (Pet pet : pets) {
            if (slot >= MAX_ITEMS) {
                break;
            }
            List<Component> lore = new ArrayList<>();
            lore.add(levelLine(pet));
            lore.add(Component.empty());
            boolean best = false;
            if (pet.busy()) {
                lore.add(statusLine(pet, now));
            } else if (pet.level() < exp.requiredLevel()) {
                lore.add(msg.get("pick-level-low", Messages.ph("level", exp.requiredLevel())));
            } else {
                if (!bestShown) {
                    best = true;
                    bestShown = true;
                    lore.add(msg.get("pick-best"));
                }
                lore.add(msg.get("pick-chance", Messages.ph("chance", em.chance(pet, exp))));
                if (exp.synergyWith(pet.type())) {
                    lore.add(msg.get("pick-synergy"));
                }
                lore.add(Component.empty());
                lore.add(msg.get("pick-click"));
            }
            inv.setItem(slot, decorate(plugin.customPets().iconItem(pet.type()), petTitle(pet), lore, best));
            holder.set(slot, "pet:" + pet.id());
            slot++;
        }
        bottomRow(inv);
        backButton(inv, holder);
        closeButton(inv, holder);
        player.openInventory(inv);
    }

    private boolean canGo(Pet pet, Expedition exp) {
        return !pet.busy() && pet.level() >= exp.requiredLevel();
    }

    // ---------- mis mascotas ----------

    public void openPets(Player player) {
        Messages msg = plugin.messages();
        List<Pet> pets = plugin.store().pets(player.getUniqueId());
        MenuHolder holder = new MenuHolder(MenuHolder.Kind.PETS, null);
        Inventory inv = create(holder, msg.get("menu-title-pets"));
        long now = System.currentTimeMillis();
        if (pets.isEmpty()) {
            inv.setItem(22, item(Material.BARRIER, msg.get("menu-no-pets"), msg.lines("menu-info-lore"), false));
        }
        int slot = 0;
        for (Pet pet : pets) {
            if (slot >= MAX_ITEMS) {
                break;
            }
            List<Component> lore = new ArrayList<>();
            lore.add(levelLine(pet));
            lore.add(statusLine(pet, now));
            lore.add(Component.empty());
            if (pet.ready(now)) {
                lore.add(msg.get("pets-click-claim"));
            } else if (!pet.busy()) {
                lore.add(msg.get(plugin.customPets().isCustom(pet.type()) ? "pets-click-egg" : "pets-click-release"));
            }
            inv.setItem(slot, decorate(plugin.customPets().iconItem(pet.type()), petTitle(pet), lore, pet.ready(now)));
            holder.set(slot, "pet:" + pet.id());
            slot++;
        }
        bottomRow(inv);
        backButton(inv, holder);
        closeButton(inv, holder);
        player.openInventory(inv);
    }

    // ---------- clics ----------

    public void handleClick(Player player, MenuHolder holder, int slot, ClickType click) {
        String action = holder.action(slot);
        if (action == null) {
            return;
        }
        ExpeditionManager em = plugin.expeditions();
        switch (action) {
            case "close" -> {
                player.closeInventory();
                return;
            }
            case "back" -> {
                openBoard(player);
                return;
            }
            case "pets" -> {
                openPets(player);
                return;
            }
            case "claimall" -> {
                em.claimAll(player);
                openBoard(player);
                return;
            }
            default -> {
            }
        }

        if (action.startsWith("exp:")) {
            Expedition exp = em.get(action.substring(4));
            if (exp != null) {
                openPick(player, exp);
            }
            return;
        }
        if (!action.startsWith("pet:")) {
            return;
        }
        Pet pet = plugin.store().find(player.getUniqueId(), UUID.fromString(action.substring(4)));
        if (pet == null) {
            refresh(player, holder);
            return;
        }
        long now = System.currentTimeMillis();

        if (holder.kind() == MenuHolder.Kind.PICK) {
            if (pet.ready(now)) {
                em.claim(player, pet);
                refresh(player, holder);
                return;
            }
            Expedition exp = em.get(holder.context());
            if (exp != null && em.send(player, pet, exp)) {
                openBoard(player);
            } else {
                refresh(player, holder);
            }
            return;
        }

        // Menu de mis mascotas
        if (pet.ready(now)) {
            em.claim(player, pet);
            refresh(player, holder);
        } else if (pet.busy()) {
            plugin.messages().send(player, "pet-busy-time", Messages.ph("pet", plugin.messages().petName(pet)),
                    Messages.ph("time", Messages.time(pet.endsAt() - now)));
        } else if (click.isShiftClick()) {
            release(player, pet);
            refresh(player, holder);
        }
    }

    /**
     * Saca la mascota al mundo otra vez y la quita de la lista de expediciones.
     * Las mascotas propias no existen en el mundo: vuelven a ser un huevo (con su nivel),
     * asi se pueden guardar, regalar o vender.
     */
    private void release(Player player, Pet pet) {
        CustomPets custom = plugin.customPets();
        if (custom.isCustom(pet.type())) {
            CustomPets.Def def = custom.get(pet.type());
            if (def == null) {
                plugin.messages().send(player, "pet-unknown");
                return;
            }
            plugin.expeditions().give(player, custom.egg(def, pet.level(), pet.xp(), pet.name()));
            plugin.store().pets(player.getUniqueId()).remove(pet);
            plugin.store().save(player.getUniqueId());
            plugin.messages().send(player, "egged", Messages.ph("pet", plugin.messages().petName(pet)));
            return;
        }
        PetType.spawn(pet, player);
        plugin.store().pets(player.getUniqueId()).remove(pet);
        plugin.store().save(player.getUniqueId());
        plugin.messages().send(player, "released", Messages.ph("pet", plugin.messages().petName(pet)));
    }

    private void refresh(Player player, MenuHolder holder) {
        switch (holder.kind()) {
            case PICK -> {
                Expedition exp = plugin.expeditions().get(holder.context());
                if (exp != null) {
                    openPick(player, exp);
                } else {
                    openBoard(player);
                }
            }
            case PETS -> openPets(player);
            default -> openBoard(player);
        }
    }

    // ---------- piezas comunes ----------

    private Component petTitle(Pet pet) {
        Messages msg = plugin.messages();
        return msg.get("pet-title", Messages.ph("pet", msg.petName(pet)), Messages.ph("type", msg.typeName(pet.type())));
    }

    private Component levelLine(Pet pet) {
        Component line = levelOnly(pet);
        CustomPets custom = plugin.customPets();
        CustomPets.Def def = custom.isCustom(pet.type()) ? custom.get(pet.type()) : null;
        if (def == null) {
            return line;
        }
        // Mascotas propias: "Legendaria · Nivel 3 ||||||...."
        return custom.rarityLabel(def.rarity()).append(Component.text(" · ", NamedTextColor.DARK_GRAY)).append(line);
    }

    private Component levelOnly(Pet pet) {
        Messages msg = plugin.messages();
        Settings cfg = plugin.settings();
        if (pet.level() >= cfg.maxLevel()) {
            return msg.get("pet-level-max", Messages.ph("level", pet.level()));
        }
        int need = plugin.expeditions().xpNeeded(pet.level());
        int filled = Math.min(10, pet.xp() * 10 / Math.max(1, need));
        String bar = "<green>" + "|".repeat(filled) + "</green><dark_gray>" + "|".repeat(10 - filled) + "</dark_gray>";
        return msg.get("pet-level", Messages.ph("level", pet.level()), Messages.comp("bar", msg.parse(bar)),
                Messages.ph("xp", pet.xp()), Messages.ph("need", need));
    }

    private Component statusLine(Pet pet, long now) {
        Messages msg = plugin.messages();
        if (!pet.busy()) {
            return msg.get("status-free");
        }
        Expedition exp = plugin.expeditions().get(pet.expedition());
        Component name = exp == null ? Component.text(pet.expedition()) : msg.parse(exp.name());
        if (pet.ready(now)) {
            return msg.get("status-ready", Messages.comp("expedition", name));
        }
        return msg.get("status-away", Messages.comp("expedition", name), Messages.ph("time", Messages.time(pet.endsAt() - now)));
    }

    private Inventory create(MenuHolder holder, Component title) {
        Inventory inv = Bukkit.createInventory(holder, SIZE, title);
        holder.setInventory(inv);
        return inv;
    }

    private void bottomRow(Inventory inv) {
        ItemStack pane = item(Material.GRAY_STAINED_GLASS_PANE, Component.text(" "), List.of(), false);
        for (int i = MAX_ITEMS; i < SIZE; i++) {
            inv.setItem(i, pane);
        }
    }

    private void closeButton(Inventory inv, MenuHolder holder) {
        inv.setItem(SLOT_CLOSE, item(Material.BARRIER, plugin.messages().get("menu-close"), List.of(), false));
        holder.set(SLOT_CLOSE, "close");
    }

    private void backButton(Inventory inv, MenuHolder holder) {
        inv.setItem(SLOT_BACK, item(Material.ARROW, plugin.messages().get("menu-back"), List.of(), false));
        holder.set(SLOT_BACK, "back");
    }

    private static ItemStack item(Material material, Component name, List<Component> lore, boolean glow) {
        return decorate(new ItemStack(material), name, lore, glow);
    }

    private static ItemStack decorate(ItemStack stack, Component name, List<Component> lore, boolean glow) {
        ItemMeta meta = stack.getItemMeta();
        // Minecraft pone los nombres de objetos en cursiva; aqui se quita.
        meta.displayName(name.decoration(TextDecoration.ITALIC, false));
        List<Component> clean = new ArrayList<>();
        for (Component line : lore) {
            clean.add(line.decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(clean);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        if (glow) {
            meta.setEnchantmentGlintOverride(true);
        }
        stack.setItemMeta(meta);
        return stack;
    }
}
