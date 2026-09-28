package dev.thanly.expeditions;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;

/**
 * Marca los inventarios que son menus del plugin, y recuerda que hace cada casilla
 * (por ejemplo "exp:cave", "pet:&lt;uuid&gt;" o "back").
 */
public final class MenuHolder implements InventoryHolder {

    public enum Kind {
        /** tablero con todas las expediciones */
        BOARD,
        /** elegir que mascota enviar a una expedicion */
        PICK,
        /** mis mascotas: estado, reclamar, sacar al mundo */
        PETS
    }

    private final Kind kind;
    /** Para PICK: el id de la expedicion elegida. */
    private final String context;
    private final Map<Integer, String> actions = new HashMap<>();
    private Inventory inventory;

    public MenuHolder(Kind kind, String context) {
        this.kind = kind;
        this.context = context;
    }

    public Kind kind() {
        return kind;
    }

    public String context() {
        return context;
    }

    public void set(int slot, String action) {
        actions.put(slot, action);
    }

    public String action(int slot) {
        return actions.get(slot);
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
