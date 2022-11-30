package me.noverita.mcdetectives;

import org.bukkit.command.CommandExecutor;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

public class MCDetectives extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {
    Fingerprints fp;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        FileConfiguration config = getConfig();

        fp = new Fingerprints(this);
        WeaponAttributes wa = new WeaponAttributes(this);
    }

    @Override
    public void onDisable() {
        fp.save();
    }
}
