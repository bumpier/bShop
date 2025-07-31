package net.bumpier.bshop.module;

import net.bumpier.bshop.BShop;
import net.bumpier.bshop.shop.ShopModule;

import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;

public class ModuleManager {

    private final BShop plugin;
    private final Set<Module> modules = new HashSet<>();

    public ModuleManager(BShop plugin) {
        this.plugin = plugin;
    }

    public void loadModules() {
        // Corrected: Pass the ModuleManager instance (this) to the ShopModule constructor.
        modules.add(new ShopModule(plugin, this));

        modules.forEach(module -> {
            try {
                module.onEnable();

            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to enable module: " + module.getName(), e);
            }
        });
    }

    public void unloadModules() {
        modules.forEach(module -> {
            try {
                module.onDisable();

            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to disable module: " + module.getName(), e);
            }
        });
        modules.clear();
    }

    /**
     * Gracefully unloads and reloads all plugin modules.
     * This is the primary method for applying configuration changes.
     */
    public void reloadModules() {
        unloadModules();
        loadModules();
    }
}