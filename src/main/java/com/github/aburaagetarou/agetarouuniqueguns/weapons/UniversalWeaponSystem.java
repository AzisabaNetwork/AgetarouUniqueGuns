package com.github.aburaagetarou.agetarouuniqueguns.weapons;

import com.github.aburaagetarou.agetarouuniqueguns.WeaponConfig;
import com.shampaggon.crackshot.CSUtility;
import com.shampaggon.crackshot.events.WeaponDamageEntityEvent;
import com.shampaggon.crackshot.events.WeaponHitBlockEvent;
import com.shampaggon.crackshot.events.WeaponReloadEvent;
import com.shampaggon.crackshot.events.WeaponShootEvent;
import me.DeeCaaD.CrackShotPlus.API;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import com.shampaggon.crackshot.events.WeaponPreShootEvent;
import me.DeeCaaD.CrackShotPlus.Events.WeaponHeldEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class UniversalWeaponSystem implements Listener {

    private final JavaPlugin plugin;
    private final CSUtility cs = new CSUtility();
    private static boolean isExplosionLock = false;
    private final Map<String, Long> cooldownMap = new HashMap<>();
    private final Map<UUID, Long> switchLockMap = new HashMap<>();
    // ★ 不足していた変数：元のモデルデータを一時保存するマップ
    private final Map<UUID, Integer> originalModelMap = new HashMap<>();

    public UniversalWeaponSystem(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    // --- 1. リロード機能 (シフト即リロード) ---
    @EventHandler
    public void onSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) return;
        Player p = event.getPlayer();
        ItemStack item = p.getInventory().getItemInMainHand();

        String title = cs.getWeaponTitle(item);
        if (title == null) return;

        ConfigurationSection root = WeaponConfig.getWeaponConfig(title);
        if (root == null) return;

        ConfigurationSection sec = root.getConfigurationSection("Instant_Reload");
        if (sec == null || !sec.getBoolean("Enable", false)) return;

        String cdKey = p.getUniqueId().toString() + "_Instant_Reload";
        if (cooldownMap.getOrDefault(cdKey, 0L) <= System.currentTimeMillis()) {

            int magSize = root.getInt("Shoot.Capacity", 0);
            int currentAmmo = API.getCSDirector().getAmmoBetweenBrackets(p, title, item);
            if (magSize > 0 && currentAmmo >= magSize) {
                return;
            }
            int addAmount = sec.getInt("Add_Amount", 1);
            int nextAmmo = currentAmmo + addAmount;

            if (magSize > 0 && nextAmmo > magSize) {
                nextAmmo = magSize;
            }

            API.getCSDirector().csminion.replaceBrackets(item, String.valueOf(nextAmmo), title);

            handleFeedback(p, sec);

            applyCooldown(p, "Instant_Reload", sec, title);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onReload(WeaponReloadEvent event) {
        Player p = event.getPlayer();
        String title = event.getWeaponTitle();
        if (title == null) return;

        ConfigurationSection root = WeaponConfig.getWeaponConfig(title);
        if (root == null) return;

        ConfigurationSection sec = root.getConfigurationSection("Instant_Reload");
        if (sec == null || !sec.getBoolean("Enable", false)) return;

        if (sec.getBoolean("Reload_On_Sneak", false) && p.isSneaking()) {
            event.setReloadDuration(1);

            int magSize = root.getInt("Shoot.Capacity", 0);
            if (magSize > 0) {
                fillAmmo(p, title, magSize);
            }
        }
        new BukkitRunnable() {
            @Override
            public void run() {
                if (p.isOnline()) {
                    ItemStack item = p.getInventory().getItemInMainHand();
                    if (title.equals(cs.getWeaponTitle(item))) {
                        applyHeldModel(p, item, title);
                    }
                }
            }
        }.runTaskLater(plugin, 1L);
    }

    // --- 2. 爆発制御 (二重防止 & CS設定の完全適用) ---
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onHitEntityExplosion(EntityDamageByEntityEvent event) {
        if (isExplosionLock || !(event.getDamager() instanceof Projectile)) return;
        Projectile proj = (Projectile) event.getDamager();
        if (!(proj.getShooter() instanceof Player)) return;

        Player p = (Player) proj.getShooter();
        String title = cs.getWeaponTitle(proj);
        if (title == null) return;

        ConfigurationSection root = WeaponConfig.getWeaponConfig(title);
        if (root == null) return;

        if (proj.hasMetadata("CustomExplosive")) {
            executeExplosion(p, event.getEntity().getLocation(), title);
            return;
        }

        ConfigurationSection sec = root.getConfigurationSection("On_Hit_Explosion");
        if (sec != null && sec.getBoolean("Enable", false)) {
            if (checkConditions(p, "On_Hit_Explosion", sec)) {
                double chance = sec.getDouble("Explosion_Chance_Entity", 0.0);
                if (ThreadLocalRandom.current().nextDouble() < chance) {
                    executeExplosion(p, event.getEntity().getLocation(), title);
                    applyCooldown(p, "On_Hit_Explosion", sec, title);
                }
            }
        }
    }

    @EventHandler
    public void onHitBlock(WeaponHitBlockEvent event) {
        if (isExplosionLock) return;
        String title = event.getWeaponTitle();
        if (title == null) return;

        ConfigurationSection root = WeaponConfig.getWeaponConfig(title);
        if (root == null) return;

        ConfigurationSection sec = root.getConfigurationSection("On_Hit_Explosion");
        if (sec != null && sec.getBoolean("Enable", false)) {
            if (checkConditions(event.getPlayer(), "On_Hit_Explosion", sec)) {
                double chance = sec.getDouble("Explosion_Chance_Block", 0.0);
                if (ThreadLocalRandom.current().nextDouble() < chance) {
                    executeExplosion(event.getPlayer(), event.getBlock().getLocation(), title);
                    applyCooldown(event.getPlayer(), "On_Hit_Explosion", sec, title);
                }
            }
        }
    }

    private void executeExplosion(Player attacker, Location loc, String title) {
        isExplosionLock = true;
        try {
            API.getCSUtility().generateExplosion(attacker, loc, title);
        } finally {
            isExplosionLock = false;
        }
    }

    // --- 3. クールダウン管理とモデル変更 ---
    private void applyCooldown(Player p, String feat, ConfigurationSection sec, String title) {
        String cdKey = p.getUniqueId().toString() + "_" + feat;
        int cdTicks = sec.getInt("Cooldown_Ticks", 0);
        if (cdTicks <= 0) return;

        cooldownMap.put(cdKey, System.currentTimeMillis() + (cdTicks * 50L));

        if (sec.contains("Custom_Model_Data_CD")) {
            ItemStack item = p.getInventory().getItemInMainHand();
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                int cdModel = sec.getInt("Custom_Model_Data_CD");
                String weaponTitle = cs.getWeaponTitle(item);

                // 現在のモデル（Heldモデルなど）を一時保存
                int previousModel = meta.hasCustomModelData() ? meta.getCustomModelData() : 0;

                meta.setCustomModelData(cdModel);
                item.setItemMeta(meta);

                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (p.isOnline()) {
                            ItemStack current = p.getInventory().getItemInMainHand();
                            // まだ同じ武器を持っていたら、前のモデル（Heldモデル）に戻す
                            if (weaponTitle != null && weaponTitle.equals(cs.getWeaponTitle(current))) {
                                ItemMeta m = current.getItemMeta();
                                m.setCustomModelData(previousModel);
                                current.setItemMeta(m);
                            }
                        }
                    }
                }.runTaskLater(plugin, (long) cdTicks);
            }
        }

        if (sec.contains("Delay_Bar")) {
            startDelayBar(p, sec.getConfigurationSection("Delay_Bar"), cdTicks, title);
        }
    }

    // --- 5. 持ち替えイベント ---
    @EventHandler
    public void onItemHeld(PlayerItemHeldEvent event) {
        Player p = event.getPlayer();

        // 元のモデルに戻す
        restoreModel(p, p.getInventory().getItem(event.getPreviousSlot()));

        ItemStack item = p.getInventory().getItem(event.getNewSlot());
        String title = cs.getWeaponTitle(item);

        if (title != null) {
            applyHeldModel(p, item, title); // 持っている間のモデル適用
            applySwitchLock(p, title);      // 持ち替えロック適用
        }
    }

    @EventHandler
    public void onWeaponHeld(WeaponHeldEvent event) {
        Player p = event.getPlayer();
        if (p == null) return;

        ItemStack item = p.getInventory().getItemInMainHand();
        String title = event.getWeaponTitle();

        applyHeldModel(p, item, title);
        applySwitchLock(p, title);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        originalModelMap.remove(event.getPlayer().getUniqueId());
        switchLockMap.remove(event.getPlayer().getUniqueId());
    }

    // --- 6. 持っている間だけモデル変更の内部処理 ---
    private void applyHeldModel(Player p, ItemStack item, String title) {
        if (item == null || !item.hasItemMeta() || title == null) return;
        ConfigurationSection root = WeaponConfig.getWeaponConfig(title);
        if (root == null) return;

        int heldModel = root.getInt("Custom_Model_Data_Held", -1);
        if (heldModel == -1) return;

        ItemMeta meta = item.getItemMeta();
        // 現在のモデルを保存（まだ保存されていない場合のみ）
        if (meta.hasCustomModelData() && !originalModelMap.containsKey(p.getUniqueId())) {
            originalModelMap.put(p.getUniqueId(), meta.getCustomModelData());
        }

        meta.setCustomModelData(heldModel);
        item.setItemMeta(meta);
    }

    private void restoreModel(Player p, ItemStack item) {
        if (item == null || !item.hasItemMeta() || !originalModelMap.containsKey(p.getUniqueId())) return;

        int originalId = originalModelMap.remove(p.getUniqueId());
        ItemMeta meta = item.getItemMeta();
        meta.setCustomModelData(originalId);
        item.setItemMeta(meta);
    }

    @EventHandler
    public void onDrop(org.bukkit.event.player.PlayerDropItemEvent event) {
        // 落としたアイテムのモデルを復元
        restoreModel(event.getPlayer(), event.getItemDrop().getItemStack());
    }

    @EventHandler
    public void onInventoryClick(org.bukkit.event.inventory.InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player p = (Player) event.getWhoClicked();

        ItemStack item = event.getCurrentItem();

        if (item != null && cs.getWeaponTitle(item) != null) {
            restoreModel(p, item);
        }
    }

    private void applySwitchLock(Player p, String title) {
        if (p == null || title == null) {
            if (p != null) switchLockMap.remove(p.getUniqueId());
            return;
        }

        ConfigurationSection root = WeaponConfig.getWeaponConfig(title);
        if (root == null) return;

        ConfigurationSection sec = root.getConfigurationSection("Switch_Lock");
        if (sec == null || !sec.getBoolean("Enable", false)) {
            switchLockMap.remove(p.getUniqueId());
            return;
        }

        int ticks = sec.getInt("Lock_Ticks", 0);
        if (ticks <= 0) return;

        switchLockMap.put(p.getUniqueId(), System.currentTimeMillis() + (ticks * 50L));

        handleFeedback(p, sec);
        if (sec.contains("Delay_Bar")) {
            startDelayBar(p, sec.getConfigurationSection("Delay_Bar"), ticks, title);
        }
    }

    // --- その他ユーティリティ ---
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPreShoot(WeaponPreShootEvent event) {
        Player p = event.getPlayer();
        Long unlockTime = switchLockMap.get(p.getUniqueId());

        if (unlockTime != null && unlockTime > System.currentTimeMillis()) {
            event.setCancelled(true);

            ItemStack item = p.getInventory().getItemInMainHand();
            String title = cs.getWeaponTitle(item);
            if (title != null) {
                ConfigurationSection root = WeaponConfig.getWeaponConfig(title);
                if (root != null) {
                    String shootBlockMsg = root.getString("Switch_Lock.Shoot_Block_Message");
                    if (shootBlockMsg != null && !shootBlockMsg.isEmpty()) {
                        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(translate(shootBlockMsg)));
                    }
                }
            }
        }
    }

    @EventHandler
    public void onShoot(WeaponShootEvent event) {
        String title = event.getWeaponTitle();
        if (title == null) return;

        executeGenericFeature(event.getPlayer(), title, "Sneak_Explosive_Shot", () -> {
            Player p = event.getPlayer();
            ConfigurationSection root = WeaponConfig.getWeaponConfig(title);
            if (root == null) return false;
            ConfigurationSection sec = root.getConfigurationSection("Sneak_Explosive_Shot");
            if (sec == null) return false;

            int cost = sec.getInt("Extra_Ammo_Cost", 1);
            ItemStack item = p.getInventory().getItemInMainHand();
            int currentAmmo = API.getCSDirector().getAmmoBetweenBrackets(p, title, item);

            if (currentAmmo >= cost) {
                API.getCSDirector().csminion.replaceBrackets(item, String.valueOf(currentAmmo - cost), title);
                event.getProjectile().setMetadata("CustomExplosive", new FixedMetadataValue(plugin, true));
                return true;
            }
            return false;
        });
    }

    @EventHandler
    public void onWeaponDamage(WeaponDamageEntityEvent event) {
        Player p = event.getPlayer();
        String title = event.getWeaponTitle();
        if (title == null) return;

        executeGenericFeature(p, title, "On_Hit_Recovery", () -> { modifyAmmo(p, title, 1); return true; });
        executeGenericFeature(p, title, "Life_Steal", () -> {
            ConfigurationSection root = WeaponConfig.getWeaponConfig(title);
            if(root == null) return false;
            ConfigurationSection sec = root.getConfigurationSection("Life_Steal");
            if(sec == null) return false;

            double heal = event.getDamage() * sec.getDouble("Heal_Percent", 0.1);
            if (heal > 0) { p.setHealth(Math.min(p.getHealth() + heal, p.getMaxHealth())); return true; }
            return false;
        });
        if (event.isHeadshot()) {
            executeGenericFeature(p, title, "Headshot_Ammo_Recovery", () -> { modifyAmmo(p, title, 1); return true; });
        }
    }

    private void executeGenericFeature(Player p, String title, String feat, FeatureAction action) {
        ConfigurationSection root = WeaponConfig.getWeaponConfig(title);
        if (root == null) return;

        ConfigurationSection sec = root.getConfigurationSection(feat);
        if (sec == null || !sec.getBoolean("Enable", false)) return;

        if (!checkConditions(p, feat, sec)) return;
        if (ThreadLocalRandom.current().nextDouble() >= sec.getDouble("Chance", 1.0)) return;

        if (action.execute()) {
            applyCooldown(p, feat, sec, title);
            handleFeedback(p, sec);
        }
    }

    private boolean checkConditions(Player p, String feat, ConfigurationSection sec) {
        if (cooldownMap.getOrDefault(p.getUniqueId().toString() + "_" + feat, 0L) > System.currentTimeMillis()) return false;
        if (sec.getBoolean("Require_Sneak", false) && !p.isSneaking()) return false;
        String effStr = sec.getString("Required_Effect");
        if (effStr != null && !effStr.isEmpty()) {
            boolean hasRequiredEffect = false;
            for (String eName : effStr.split(",")) {
                PotionEffectType t = PotionEffectType.getByName(eName.trim().toUpperCase());
                if (t != null && !p.hasPotionEffect(t)) return false;
                if (t != null && p.hasPotionEffect(t)) {
                    hasRequiredEffect = true;
                    break;
                }
            }
            if (!hasRequiredEffect) return false;
        }
        return true;
    }

    private void startDelayBar(Player p, ConfigurationSection sec, int ticks, String weaponTitle) {
        new BukkitRunnable() {
            int i = 0;
            public void run() {
                if (!p.isOnline()) { this.cancel(); return; }
                if (i >= ticks) {
                    String endMsg = sec.getString("End_Action_Bar");
                    if (endMsg != null && !endMsg.isEmpty()) {
                        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(translate(endMsg)));
                    }
                    String endSound = sec.getString("End_Sound");
                    if (endSound != null && !endSound.isEmpty()) {
                        handleFeedbackSound(p, endSound);
                    }
                    this.cancel();
                    return;
                }
                ItemStack currentItem = p.getInventory().getItemInMainHand();
                String currentTitle = cs.getWeaponTitle(currentItem);
                if (currentTitle != null && currentTitle.equals(weaponTitle)) {
                    String actionStr = sec.getString("Action_Bar");
                    if (actionStr != null) {
                        String bar = buildBar((double) i / ticks, sec);
                        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(translate(actionStr.replace("{bar}", bar))));
                    }
                }
                i += 2;
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    private String buildBar(double pct, ConfigurationSection sec) {
        int len = sec.getInt("Symbol_Amount", 15);
        int left = (int) (pct * len);
        String sym = sec.getString("Symbol", "|");
        return translate(sec.getString("Left_Color", "&a")) + repeat(sym, left) + translate(sec.getString("Right_Color", "&c")) + repeat(sym, len - left);
    }

    private void modifyAmmo(Player p, String t, int a) {
        ItemStack i = p.getInventory().getItemInMainHand();
        if (t.equals(cs.getWeaponTitle(i))) {
            int c = API.getCSDirector().getAmmoBetweenBrackets(p, t, i);
            API.getCSDirector().csminion.replaceBrackets(i, String.valueOf(c + a), t);
        }
    }

    private void fillAmmo(Player p, String title, int amount) {
        ItemStack item = p.getInventory().getItemInMainHand();
        if (title.equals(cs.getWeaponTitle(item))) {
            API.getCSDirector().csminion.replaceBrackets(item, String.valueOf(amount), title);
        }
    }

    private void handleFeedback(Player p, ConfigurationSection s) {
        String msg = s.getString("Message");
        if (msg != null && !msg.isEmpty()) {
            p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(translate(msg)));
        }
        String rawSounds = s.getString("Sounds", s.getString("Sound"));
        if (rawSounds != null) handleFeedbackSound(p, rawSounds);
    }

    private void handleFeedbackSound(Player p, String rawSounds) {
        for (String entry : rawSounds.split(",")) {
            String[] parts = entry.trim().split("-");
            if (parts.length == 0 || parts[0].isEmpty()) continue;
            try {
                Sound sound = Sound.valueOf(parts[0].toUpperCase());
                float volume = (parts.length > 1) ? Float.parseFloat(parts[1]) : 1.0f;
                float pitch = (parts.length > 2) ? Float.parseFloat(parts[2]) : 1.0f;
                int delay = (parts.length > 3) ? Integer.parseInt(parts[3]) : 0;
                if (delay <= 0) p.playSound(p.getLocation(), sound, volume, pitch);
                else {
                    new BukkitRunnable() {
                        @Override public void run() { if (p.isOnline()) p.playSound(p.getLocation(), sound, volume, pitch); }
                    }.runTaskLater(plugin, (long) delay);
                }
            } catch (Exception ignored) {}
        }
    }

    private String translate(String s) {
        if (s == null) return "";

        java.util.regex.Pattern hexPattern =
                java.util.regex.Pattern.compile("&x(&[0-9a-fA-F]){6}");
        java.util.regex.Matcher matcher = hexPattern.matcher(s);
        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            String hex = matcher.group().replace("&", "").substring(1);
            try {
                matcher.appendReplacement(
                        sb,
                        java.util.regex.Matcher.quoteReplacement(
                                net.md_5.bungee.api.ChatColor.of("#" + hex).toString()
                        )
                );
            } catch (Exception ignored) {
                matcher.appendReplacement(
                        sb,
                        java.util.regex.Matcher.quoteReplacement(matcher.group())
                );
            }
        }

        matcher.appendTail(sb);
        return ChatColor.translateAlternateColorCodes('&', sb.toString());
    }
    private String repeat(String s, int n) { StringBuilder b = new StringBuilder(); for(int i=0; i<n; i++) b.append(s); return b.toString(); }
    @FunctionalInterface interface FeatureAction { boolean execute(); }
}