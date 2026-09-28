package dev.thanly.expeditions;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Clase principal. Paper la carga al encender el servidor (ver plugin.yml).
 */
public final class PetExpeditions extends JavaPlugin {

    private Settings settings;
    private Messages messages;
    private PetStore store;
    private CustomPets customPets;
    private ExpeditionManager expeditions;
    private MenuManager menus;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        messages = new Messages(this);
        store = new PetStore(this);
        customPets = new CustomPets(this);
        expeditions = new ExpeditionManager(this);
        menus = new MenuManager(this);
        loadSettings();

        PluginCommand command = getCommand("expeditions");
        if (command != null) {
            ExpeditionCommand executor = new ExpeditionCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }
        getServer().getPluginManager().registerEvents(new PetListener(this), this);
        expeditions.startReturnTask();
        getLogger().info("PetExpeditions enabled: " + expeditions.all().size() + " expeditions and "
                + customPets.all().size() + " custom pets loaded.");
    }

    @Override
    public void onDisable() {
        if (store != null) {
            store.saveAll();
        }
    }

    /** Lee config.yml, el idioma y las expediciones otra vez. */
    public void loadSettings() {
        reloadConfig();
        settings = Settings.from(getConfig());
        messages.load(settings.language());
        customPets.load(getConfig());
        expeditions.load(getConfig());
    }

    public Settings settings() {
        return settings;
    }

    public Messages messages() {
        return messages;
    }

    public PetStore store() {
        return store;
    }

    public CustomPets customPets() {
        return customPets;
    }

    public ExpeditionManager expeditions() {
        return expeditions;
    }

    public MenuManager menus() {
        return menus;
    }
}
