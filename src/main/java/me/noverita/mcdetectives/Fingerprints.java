package me.noverita.mcdetectives;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.time.Instant;
import java.util.*;
import java.util.logging.Level;

public class Fingerprints implements Listener {
    private final Set<Material> fingerprintableBlocks = new HashSet<>();
    private final Map<Block, Queue<Fingerprint>> fingerprints = new HashMap<>();
    private final Map<UUID, Byte> playerData = new HashMap<>();
    private final Random random = new Random();
    private final File dataFolder;
    private long lifetime;

    public Fingerprints(JavaPlugin plugin) {
        dataFolder = plugin.getDataFolder();
        FileConfiguration config = plugin.getConfig();
        if (config.getBoolean("fingerprints.enabled")) {
            // Load the list of blocks which can hold fingerprints
            List<String> fpblocks = config.getStringList("fingerprints.blocks");
            for (String s : fpblocks) {
                try {
                    Material mat = Material.getMaterial(s.toUpperCase());
                    fingerprintableBlocks.add(mat);
                } catch (IllegalArgumentException e) {
                    Bukkit.getLogger().log(Level.SEVERE, "fingerprints.blocks: " + s + " is not a valid material.");
                }
            }

            lifetime = config.getLong("fingerprints.lifetime");

            // Clean out old fingerprints every 5 minutes.
            // They shouldn't use too much memory, but it's good to be careful.
            long lifetime = config.getLong("fingerprints.lifetime");
            Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
                long time = Instant.now().getEpochSecond();
                for (Queue<Fingerprint> temp: fingerprints.values()) {
                    while (!temp.isEmpty() && ((temp.peek().time - time) > lifetime)) {
                        temp.poll();
                    }
                }
            }, 6000, 6000);

            // Load all fingerprints in.
            File playerPrints = new File(plugin.getDataFolder() + "/players.csv");
            try {
                playerPrints.createNewFile();
                try (BufferedReader br = new BufferedReader(new FileReader(playerPrints))) {
                    String line = br.readLine();
                    while (line != null) {
                        String[] split = line.split(",");
                        playerData.put(UUID.fromString(split[0]), parseByteString(split[1]));
                        line = br.readLine();
                    }
                } catch (IOException e) {
                    Bukkit.getLogger().log(Level.SEVERE, "Could not load player fingerprint data.");
                }
            } catch (IOException e) {
                Bukkit.getLogger().log(Level.SEVERE, "Could not create fingerprints data file.");
            }
        }

        Bukkit.getPluginManager().registerEvents(this, plugin);

        // Load existing fingerprints
        try (BufferedReader br = new BufferedReader(new FileReader(dataFolder + "/fingerprints.csv"))) {
            String line = br.readLine();
            while (line != null) {
                String[] split = line.split(",");

                Block block = Bukkit.getWorld(UUID.fromString(split[0])).getBlockAt(Integer.parseInt(split[1]),Integer.parseInt(split[2]),Integer.parseInt(split[3]));
                Queue<Fingerprint> prints;
                if (fingerprints.containsKey(block)) {
                    prints = fingerprints.get(block);
                } else {
                    prints = new LinkedList<>();
                    fingerprints.put(block, prints);
                }
                prints.add(new Fingerprint(UUID.fromString(split[4]), Long.parseLong(split[5])));

                line = br.readLine();
            }
        } catch (IOException e) {
            Bukkit.getLogger().log(Level.SEVERE, "Could not load any existing fingerprints.");
        }
    }

    private static byte parseByteString(String s) {
        byte value = 0;
        for (int i = 0; i < 8; ++i) {
            value <<= 1;
            if (s.charAt(i) == '1') {
                value |= 1;
            }
        }
        return value;
    }

    private static String fromByte(byte data) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 8; ++i) {
            if (((data >> i) & 1) == 1) {
                sb.append('1');
            } else {
                sb.append('0');
            }
        }
        return sb.toString();
    }

    void save() {
        try (PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(dataFolder + "/fingerprints.csv", false)))) {
            for (Block block: fingerprints.keySet()) {
                Location loc = block.getLocation();
                String base = String.format("%s,%d,%d,%d,",loc.getWorld().getUID(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
                Queue<Fingerprint> queue = fingerprints.get(block);
                if (!queue.isEmpty()) {
                    Fingerprint fp = queue.poll();
                    while (!queue.isEmpty()) {
                        pw.println(base + fp.uuid.toString() + "," + fp.time);
                        fp = queue.poll();
                    }
                }
            }
            pw.flush();
        } catch (IOException e) {
            Bukkit.getLogger().log(Level.SEVERE, "Failed to save fingerprints.");
        }
    }

    @EventHandler
    private void onPlayerJoin(PlayerJoinEvent event) {
        if (!playerData.containsKey(event.getPlayer().getUniqueId())) {
            byte[] bytes = new byte[1];
            random.nextBytes(bytes);
            playerData.put(event.getPlayer().getUniqueId(), bytes[0]);
            try (PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(dataFolder + "/players.csv", true)))) {
                pw.println(event.getPlayer().getUniqueId() + "," + fromByte(bytes[0]));
                pw.flush();
            } catch (IOException e) {
                Bukkit.getLogger().log(Level.SEVERE, "Failed to log fingerprint for " + event.getPlayer().getUniqueId());
            }
        }
    }

    @EventHandler
    private void onContainerUse(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block != null && fingerprintableBlocks.contains(block.getType())) {
            ItemStack i = event.getItem();
            if (i != null && i.getType() == Material.GUNPOWDER && i.getItemMeta().getDisplayName().equals("Dusting Powder")) {
                event.setCancelled(true);
                long time = Instant.now().getEpochSecond();
                Queue<Fingerprint> queue = fingerprints.get(block);

                if (queue != null) {

                    if (queue.isEmpty()) {
                        event.getPlayer().sendMessage("There are no fingerprints on that chest.");
                    } else {
                        StringBuilder sb = new StringBuilder();
                        sb.append("You found the following fingerprints:\n");
                        while (!queue.isEmpty()) {
                            Fingerprint fp = queue.poll();
                            if (time - fp.time <= lifetime) {
                                double age = ((double) (time - fp.time)) / lifetime;
                                byte data = playerData.get(fp.uuid);
                                for (int j = 0; j < 8; ++j) {
                                    double temp = random.nextDouble();
                                    if (temp < age) {
                                        sb.append('?');
                                    } else {
                                        if (((data >> j) & 1) == 1) {
                                            sb.append('#');
                                        } else {
                                            sb.append('_');
                                        }
                                    }
                                }
                                sb.append('\n');
                            }
                        }
                        event.getPlayer().sendMessage(sb.toString());
                    }
                } else {
                    event.getPlayer().sendMessage("There are no fingerprints on that chest.");
                }
            } else {
                Queue<Fingerprint> queue = fingerprints.computeIfAbsent(block, k -> new LinkedList<>());
                queue.add(new Fingerprint(event.getPlayer().getUniqueId(), Instant.now().getEpochSecond()));
            }
        }
    }

    private static class Fingerprint {
        UUID uuid;
        long time;
        public Fingerprint(UUID uuid, long time) {
            this.uuid = uuid;
            this.time = time;
        }
    }
}
