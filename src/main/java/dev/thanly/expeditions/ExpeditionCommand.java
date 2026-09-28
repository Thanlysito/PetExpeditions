package dev.thanly.expeditions;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /expeditions [pets|claim|help|reload|give] */
public final class ExpeditionCommand implements CommandExecutor, TabCompleter {

    private final PetExpeditions plugin;

    public ExpeditionCommand(PetExpeditions plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Messages msg = plugin.messages();
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);

        if (sub.equals("reload")) {
            if (!sender.hasPermission("petexpeditions.admin")) {
                msg.send(sender, "no-permission");
                return true;
            }
            plugin.loadSettings();
            msg.send(sender, "reloaded", Messages.ph("count", plugin.expeditions().all().size()));
            return true;
        }
        if (sub.equals("give")) {
            give(sender, args);
            return true;
        }
        if (!(sender instanceof Player player)) {
            msg.send(sender, "players-only");
            return true;
        }
        switch (sub) {
            case "" -> plugin.menus().openBoard(player);
            case "pets" -> plugin.menus().openPets(player);
            case "claim" -> plugin.expeditions().claimAll(player);
            default -> {
                for (var line : msg.lines("help")) {
                    player.sendMessage(line);
                }
            }
        }
        return true;
    }

    /** /expeditions give &lt;jugador&gt; &lt;mascota&gt; [cantidad] - da huevos (para tiendas, cajas, eventos). */
    private void give(CommandSender sender, String[] args) {
        Messages msg = plugin.messages();
        if (!sender.hasPermission("petexpeditions.admin")) {
            msg.send(sender, "no-permission");
            return;
        }
        if (args.length < 3) {
            msg.send(sender, "give-usage");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            msg.send(sender, "player-not-found", Messages.ph("player", args[1]));
            return;
        }
        CustomPets.Def def = plugin.customPets().get(args[2]);
        if (def == null) {
            msg.send(sender, "pet-unknown");
            return;
        }
        int amount = 1;
        if (args.length > 3) {
            try {
                amount = Math.max(1, Math.min(64, Integer.parseInt(args[3])));
            } catch (NumberFormatException ignored) {
                // se queda en 1
            }
        }
        ItemStack egg = plugin.customPets().egg(def, 1, 0, null);
        egg.setAmount(amount);
        plugin.expeditions().give(target, egg);
        msg.send(sender, "given", Messages.ph("amount", amount), Messages.ph("pet", def.name()),
                Messages.ph("player", target.getName()));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        boolean admin = sender.hasPermission("petexpeditions.admin");
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            options.addAll(List.of("pets", "claim", "help"));
            if (admin) {
                options.addAll(List.of("reload", "give"));
            }
        } else if (admin && args[0].equalsIgnoreCase("give") && args.length == 2) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                options.add(p.getName());
            }
        } else if (admin && args[0].equalsIgnoreCase("give") && args.length == 3) {
            for (CustomPets.Def d : plugin.customPets().all()) {
                options.add(d.id());
            }
        }
        String typed = args[args.length - 1].toLowerCase(Locale.ROOT);
        for (String o : options) {
            if (o.toLowerCase(Locale.ROOT).startsWith(typed)) {
                out.add(o);
            }
        }
        return out;
    }
}
