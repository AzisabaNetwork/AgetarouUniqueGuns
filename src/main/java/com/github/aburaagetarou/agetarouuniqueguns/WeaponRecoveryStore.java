package com.github.aburaagetarou.agetarouuniqueguns;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * インベントリへ復元できなかったアイテムを、運営が後から確認・付与できるよう保存する。
 */
public final class WeaponRecoveryStore {

    private static final String ROOT = "recoveries";

    private final JavaPlugin plugin;
    private final File file;
    private final YamlConfiguration config;

    public WeaponRecoveryStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "recovery-items.yml");
        this.config = YamlConfiguration.loadConfiguration(file);
    }

    public synchronized String store(Player player, ItemStack item, String reason) {
        if (item == null) return null;

        String id = createRecoveryId();
        String path = ROOT + "." + id;

        config.set(path + ".player_uuid", player.getUniqueId().toString());
        config.set(path + ".player_name", player.getName());
        config.set(path + ".created_at", System.currentTimeMillis());
        config.set(path + ".reason", reason);
        config.set(path + ".item", item.clone());

        boolean persisted = save();
        plugin.getLogger().warning("[WeaponRecovery] id=" + id
                + " player=" + player.getName()
                + " reason=" + reason
                + " persisted=" + persisted);
        return id;
    }

    public synchronized RecoveryEntry get(String id) {
        if (id == null) return null;

        String normalizedId = id.toLowerCase();
        ConfigurationSection section = config.getConfigurationSection(ROOT + "." + normalizedId);
        if (section == null) return null;

        String uuidText = section.getString("player_uuid");
        ItemStack item = section.getItemStack("item");
        if (uuidText == null || item == null) return null;

        try {
            return new RecoveryEntry(
                    normalizedId,
                    UUID.fromString(uuidText),
                    section.getString("player_name", "unknown"),
                    section.getLong("created_at", 0L),
                    section.getString("reason", "unknown"),
                    item.clone()
            );
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("Invalid recovery entry: " + normalizedId);
            return null;
        }
    }

    public synchronized List<RecoveryEntry> list() {
        List<RecoveryEntry> entries = new ArrayList<>();
        ConfigurationSection root = config.getConfigurationSection(ROOT);
        if (root == null) return entries;

        for (String id : root.getKeys(false)) {
            RecoveryEntry entry = get(id);
            if (entry != null) entries.add(entry);
        }
        entries.sort(Comparator.comparingLong(RecoveryEntry::getCreatedAt));
        return entries;
    }

    public synchronized boolean remove(String id) {
        if (id == null) return false;

        String path = ROOT + "." + id.toLowerCase();
        RecoveryEntry backup = get(id);
        if (backup == null) return false;

        config.set(path, null);
        if (save()) return true;

        restoreInMemory(backup);
        return false;
    }

    private void restoreInMemory(RecoveryEntry entry) {
        String path = ROOT + "." + entry.getId();
        config.set(path + ".player_uuid", entry.getPlayerUuid().toString());
        config.set(path + ".player_name", entry.getPlayerName());
        config.set(path + ".created_at", entry.getCreatedAt());
        config.set(path + ".reason", entry.getReason());
        config.set(path + ".item", entry.getItem());
    }

    private String createRecoveryId() {
        String id;
        do {
            id = UUID.randomUUID().toString().substring(0, 8).toLowerCase();
        } while (config.contains(ROOT + "." + id));
        return id;
    }

    private boolean save() {
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                plugin.getLogger().severe("Could not create recovery directory: " + parent);
                return false;
            }
            config.save(file);
            return true;
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not save recovery-items.yml: " + ex.getMessage());
            return false;
        }
    }

    public static final class RecoveryEntry {
        private final String id;
        private final UUID playerUuid;
        private final String playerName;
        private final long createdAt;
        private final String reason;
        private final ItemStack item;

        private RecoveryEntry(String id, UUID playerUuid, String playerName,
                              long createdAt, String reason, ItemStack item) {
            this.id = id;
            this.playerUuid = playerUuid;
            this.playerName = playerName;
            this.createdAt = createdAt;
            this.reason = reason;
            this.item = item;
        }

        public String getId() {
            return id;
        }

        public UUID getPlayerUuid() {
            return playerUuid;
        }

        public String getPlayerName() {
            return playerName;
        }

        public long getCreatedAt() {
            return createdAt;
        }

        public String getReason() {
            return reason;
        }

        public ItemStack getItem() {
            return item.clone();
        }
    }
}
