package dev.thanly.expeditions;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Registrar mascotas (Shift + clic derecho), clics en los menus, entrar y salir del servidor. */
public final class PetListener implements Listener {

    /** Tiempo para confirmar el registro con el segundo Shift + clic derecho. */
    private static final long CONFIRM_MS = 5000;

    private final PetExpeditions plugin;
    /** jugador -> (mascota que quiere registrar, hora del primer clic) */
    private final Map<UUID, UUID> confirmPet = new HashMap<>();
    private final Map<UUID, Long> confirmTime = new HashMap<>();

    public PetListener(PetExpeditions plugin) {
        this.plugin = plugin;
    }

    // ---------- registrar una mascota ----------

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        if (event.getHand() != EquipmentSlot.HAND || !player.isSneaking()) {
            return;
        }
        // Con la mano vacia, para no chocar con dar de comer, tenir collares, etc.
        if (player.getInventory().getItemInMainHand().getType() != Material.AIR) {
            return;
        }
        Entity entity = event.getRightClicked();
        String type = PetType.of(entity);
        if (type == null || !(entity instanceof Tameable pet) || !pet.isTamed()) {
            return;
        }
        if (!plugin.settings().petTypes().contains(type) || !player.hasPermission("petexpeditions.use")) {
            return;
        }
        event.setCancelled(true);
        Messages msg = plugin.messages();
        if (!player.getUniqueId().equals(pet.getOwnerUniqueId())) {
            msg.send(player, "register-not-owner");
            return;
        }
        List<Pet> pets = plugin.store().pets(player.getUniqueId());
        if (pets.size() >= plugin.settings().maxPets()) {
            msg.send(player, "register-full", Messages.ph("max", plugin.settings().maxPets()));
            return;
        }

        // Primer clic: pedir confirmacion. Segundo clic (en 5 s, al mismo animal): registrar.
        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();
        boolean confirmed = entity.getUniqueId().equals(confirmPet.get(id))
                && now - confirmTime.getOrDefault(id, 0L) <= CONFIRM_MS;
        if (!confirmed) {
            confirmPet.put(id, entity.getUniqueId());
            confirmTime.put(id, now);
            msg.send(player, "register-confirm");
            return;
        }
        confirmPet.remove(id);
        confirmTime.remove(id);

        Pet data = new Pet(UUID.randomUUID(), type);
        if (entity.customName() != null) {
            data.setName(entity.getName());
        }
        PetType.capture(entity, data);
        if (pet.isLeashed()) {
            pet.setLeashHolder(null);
        }
        entity.remove();
        pets.add(data);
        plugin.store().save(id);
        msg.send(player, "registered", Messages.ph("pet", msg.petName(data)));
        plugin.expeditions().sound(player, Sound.ENTITY_ITEM_PICKUP, 1f, 0.8f);
    }

    // ---------- huevos de mascotas propias ----------

    /** Clic derecho con un huevo en la mano: nace la mascota y entra al equipo. */
    @EventHandler(priority = EventPriority.LOW)
    public void onEggUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack egg = event.getItem();
        CustomPets custom = plugin.customPets();
        String id = custom.eggId(egg);
        if (id == null) {
            return;
        }
        // Nunca se coloca ni se usa como el objeto normal (cabeza de dragon, carga de fuego...).
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        Messages msg = plugin.messages();
        if (!player.hasPermission("petexpeditions.use")) {
            return;
        }
        if (custom.get(id) == null) {
            msg.send(player, "pet-unknown");
            return;
        }
        List<Pet> pets = plugin.store().pets(player.getUniqueId());
        if (pets.size() >= plugin.settings().maxPets()) {
            msg.send(player, "register-full", Messages.ph("max", plugin.settings().maxPets()));
            return;
        }
        Pet pet = custom.hatch(egg);
        egg.setAmount(egg.getAmount() - 1);
        pets.add(pet);
        plugin.store().save(player.getUniqueId());
        msg.send(player, "hatched", Messages.comp("pet", msg.parse(
                custom.rarity(custom.get(id).rarity()).color() + msg.petName(pet))),
                Messages.comp("rarity", custom.rarityLabel(custom.get(id).rarity())));
        plugin.expeditions().sound(player, Sound.ENTITY_TURTLE_EGG_HATCH, 1f, 1f);
        plugin.expeditions().sound(player, Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.8f);
    }

    /** Por si acaso: un huevo nunca se coloca como bloque. */
    @EventHandler(ignoreCancelled = true)
    public void onEggPlace(BlockPlaceEvent event) {
        if (plugin.customPets().eggId(event.getItemInHand()) != null) {
            event.setCancelled(true);
        }
    }

    /** Un huevo no se puede usar para craftear (por ejemplo, si su icono es un fragmento de amatista). */
    @EventHandler
    public void onEggCraft(PrepareItemCraftEvent event) {
        for (ItemStack it : event.getInventory().getMatrix()) {
            if (plugin.customPets().eggId(it) != null) {
                event.getInventory().setResult(null);
                return;
            }
        }
    }

    // ---------- menus ----------

    @EventHandler
    public void onMenuClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (!(holder instanceof MenuHolder menu)) {
            return;
        }
        // Nadie puede sacar ni meter objetos en los menus.
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getView().getTopInventory().getSize()) {
            return; // clic en el inventario del jugador, no en el menu
        }
        ClickType click = event.getClick();
        // Se hace en el siguiente tick: cerrar o abrir inventarios dentro del evento da problemas.
        Bukkit.getScheduler().runTask(plugin, () -> {
            // Con Shift + clic el juego a veces "muestra" el objeto en tu inventario aunque se cancelo
            // (objeto fantasma). Esto le vuelve a mandar al jugador su inventario real.
            player.updateInventory();
            plugin.menus().handleClick(player, menu, slot, click);
        });
    }

    @EventHandler
    public void onMenuDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof MenuHolder) {
            event.setCancelled(true);
        }
    }

    // ---------- entrar / salir ----------

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // Un poco despues de entrar, para que el mensaje no se pierda entre los del servidor.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            int ready = plugin.expeditions().readyCount(player.getUniqueId());
            if (ready > 0) {
                // Ya se avisa aqui, asi que no hace falta el aviso de "volvio" para cada una.
                long now = System.currentTimeMillis();
                for (Pet p : plugin.store().pets(player.getUniqueId())) {
                    if (p.ready(now)) {
                        p.setNotified(true);
                    }
                }
                plugin.messages().send(player, "join-ready", Messages.ph("count", ready));
            }
        }, 60L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        confirmPet.remove(id);
        confirmTime.remove(id);
        plugin.store().unload(id);
    }
}
