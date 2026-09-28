package dev.thanly.expeditions;

import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.entity.Cat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Parrot;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.Wolf;

import java.util.Locale;

/**
 * Todo lo que depende del tipo de mascota: guardar su aspecto al registrarla,
 * volver a crearla en el mundo y el icono del menu.
 */
public final class PetType {

    private PetType() {
    }

    public static boolean isSupported(String type) {
        return type.equals("WOLF") || type.equals("CAT") || type.equals("PARROT");
    }

    /** "WOLF", "CAT" o "PARROT"; null si la entidad no es una mascota que el plugin acepte. */
    public static String of(Entity entity) {
        if (entity instanceof Wolf) {
            return "WOLF";
        }
        if (entity instanceof Cat) {
            return "CAT";
        }
        if (entity instanceof Parrot) {
            return "PARROT";
        }
        return null;
    }

    public static Material icon(String type) {
        return switch (type) {
            case "CAT" -> Material.CAT_SPAWN_EGG;
            case "PARROT" -> Material.PARROT_SPAWN_EGG;
            default -> Material.WOLF_SPAWN_EGG;
        };
    }

    /** Guarda en la mascota el aspecto del animal (raza, color del collar). */
    public static void capture(Entity entity, Pet pet) {
        if (entity instanceof Wolf wolf) {
            pet.setVariant(wolf.getVariant().getKey().toString());
            pet.setCollar(wolf.getCollarColor().name());
        } else if (entity instanceof Cat cat) {
            pet.setVariant(cat.getCatType().getKey().toString());
            pet.setCollar(cat.getCollarColor().name());
        } else if (entity instanceof Parrot parrot) {
            pet.setVariant(parrot.getVariant().name());
        }
    }

    /** Vuelve a crear el animal junto al jugador, ya domesticado y con su aspecto. */
    @SuppressWarnings("deprecation")
    public static void spawn(Pet pet, Player owner) {
        Location loc = owner.getLocation();
        switch (pet.type()) {
            case "CAT" -> loc.getWorld().spawn(loc, Cat.class, cat -> {
                common(cat, pet, owner);
                Cat.Type t = variantKey(pet) == null ? null : Registry.CAT_VARIANT.get(variantKey(pet));
                if (t != null) {
                    cat.setCatType(t);
                }
                DyeColor c = collar(pet);
                if (c != null) {
                    cat.setCollarColor(c);
                }
            });
            case "PARROT" -> loc.getWorld().spawn(loc, Parrot.class, parrot -> {
                common(parrot, pet, owner);
                try {
                    parrot.setVariant(Parrot.Variant.valueOf(pet.variant()));
                } catch (RuntimeException ignored) {
                    // variante desconocida: se queda la que salga al azar
                }
            });
            default -> loc.getWorld().spawn(loc, Wolf.class, wolf -> {
                common(wolf, pet, owner);
                Wolf.Variant v = variantKey(pet) == null ? null : Registry.WOLF_VARIANT.get(variantKey(pet));
                if (v != null) {
                    wolf.setVariant(v);
                }
                DyeColor c = collar(pet);
                if (c != null) {
                    wolf.setCollarColor(c);
                }
            });
        }
    }

    @SuppressWarnings("deprecation")
    private static void common(Tameable animal, Pet pet, Player owner) {
        animal.setTamed(true);
        animal.setOwner(owner);
        animal.setAdult();
        if (pet.name() != null) {
            animal.setCustomName(pet.name());
        }
    }

    private static NamespacedKey variantKey(Pet pet) {
        return pet.variant() == null ? null : NamespacedKey.fromString(pet.variant().toLowerCase(Locale.ROOT));
    }

    private static DyeColor collar(Pet pet) {
        if (pet.collar() == null) {
            return null;
        }
        try {
            return DyeColor.valueOf(pet.collar());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
