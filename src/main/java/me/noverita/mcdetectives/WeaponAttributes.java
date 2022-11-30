package me.noverita.mcdetectives;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.awt.*;
import java.time.Instant;
import java.util.*;
import java.util.List;
import java.util.logging.Level;

public class WeaponAttributes implements Listener {
    private final Set<Material> tools = new HashSet<>();
    private List<String> attributes;
    private List<String> durabilityStates;
    private final Random random = new Random();
    private double durabilityCertainty;
    private double attributeCertainty;

    public WeaponAttributes(JavaPlugin plugin) {
        FileConfiguration config = plugin.getConfig();
        if (config.getBoolean("tool-attributes.enabled")) {

            attributeCertainty = config.getDouble("tool-attributes.attribute-certainty");
            durabilityCertainty = config.getDouble("tool-attributes.durability-certainty");

            List<String> toolNames = config.getStringList("tool-attributes.tools");
            for (String s : toolNames) {
                try {
                    Material mat = Material.getMaterial(s.toUpperCase());
                    tools.add(mat);
                } catch (IllegalArgumentException e) {
                    Bukkit.getLogger().log(Level.SEVERE, "tool-attributes.tools: " + s + " is not a valid material.");
                }
            }

            attributes = config.getStringList("tool-attributes.attributes");
            durabilityStates = config.getStringList("tool-attributes.durability");

            Bukkit.getPluginManager().registerEvents(this, plugin);
        }
    }

    @EventHandler
    private void onPlayerDamage(EntityDamageByEntityEvent event) {
        if (event.getEntityType() == EntityType.PLAYER && event.getDamager().getType() == EntityType.PLAYER) {
            Player damager = (Player) event.getDamager();
            ItemStack item = damager.getInventory().getItemInMainHand();
            ItemMeta meta = item.getItemMeta();
            List<String> lore = meta.getLore();

            PointOfInterest poi = new PointOfInterest();

            for (String s: lore) {
                if (s.startsWith("MCDetectives Attribute: ")) {
                    String attribute = s.substring(24);
                    Bukkit.broadcastMessage(attribute);
                    poi.attribute = attribute;
                }
            }
            if (meta instanceof Damageable dmgmeta) {
                int damage = dmgmeta.getDamage();
                int max = item.getType().getMaxDurability();
                double percent = ((double) damage) / max;
                poi.durability = percent;
                Bukkit.broadcastMessage(durabilityStates.get((int) Math.floor(percent * durabilityStates.size())));
            }

            poi.time = Instant.now().getEpochSecond();

            Player player = (Player) event.getEntity();

            Vector a = player.getEyeLocation().getDirection().setY(0);
            Vector b = damager.getEyeLocation().getDirection().setY(0);
            poi.directionDifference = a.angle(b);

            poi.damage = event.getDamage();

            investigatePOI(poi);
        }
    }

    @EventHandler
    private void onCraftTool(PrepareItemCraftEvent event) {
        Recipe recipe = event.getRecipe();
        if (recipe != null) {
            ItemStack item = recipe.getResult();
            if (tools.contains(item.getType())) {
                ItemMeta meta = item.getItemMeta();
                ArrayList<String> lore = new ArrayList<>();
                lore.add("MCDetectives Attribute: " + attributes.get(random.nextInt(attributes.size())));
                meta.setLore(lore);
                item.setItemMeta(meta);
                event.getInventory().setResult(item);
            }
        }
    }

    private void investigatePOI(PointOfInterest poi) {
        double[] attributeValues = new double[attributes.size()];
        double total = 0;
        for (int i = 0; i < attributes.size(); ++i) {
            double value = random.nextDouble();
            if (attributes.get(i).equals(poi.attribute)) {
                value += attributeCertainty;
            }
            attributeValues[i] = value;
            total += value;
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < attributes.size(); ++i) {
            sb.append(attributes.get(i));
            sb.append(": ");
            for (int j = 0; j < 10 * attributeValues[i] / total; ++j) {
                sb.append('*');
            }
            sb.append('\n');
        }
        Bukkit.broadcastMessage(sb.toString());

        String message = "Error";
        if (poi.directionDifference < 1) {
            message = "The person that was hurt was running away.";
        } else if (poi.directionDifference > 2) {
            message = "The person that was hurt was facing their attacker.";
        } else {
            message = "The person that was hurt had their side to their attacker.";
        }
        Bukkit.broadcastMessage(message);

        Bukkit.broadcastMessage(Double.toString(poi.damage));
    }

    private static class PointOfInterest {
        String attribute;
        double durability;
        double directionDifference;
        long time;
        double damage;
    }
}
