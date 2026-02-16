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
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class UniversalWeaponSystem implements Listener {

    private final JavaPlugin plugin;
    private final CSUtility cs = new CSUtility();
    private static boolean isExplosionLock = false;
    private final Map<String, Long> cooldownMap = new HashMap<>();

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

        ConfigurationSection sec = WeaponConfig.getWeaponConfig(title).getConfigurationSection("Instant_Reload");
        if (sec != null && sec.getBoolean("Enable", false) && sec.getBoolean("Reload_On_Sneak", true)) {
            String cdKey = p.getUniqueId().toString() + "_Instant_Reload";
            if (cooldownMap.getOrDefault(cdKey, 0L) <= System.currentTimeMillis()) {
                p.performCommand("shot reload");
            }
        }
    }

    @EventHandler
    public void onReload(WeaponReloadEvent event) {
        executeGenericFeature(event.getPlayer(), event.getWeaponTitle(), "Instant_Reload", () -> {
            event.setReloadDuration(0);
            return true;
        });
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

        // シフト特殊弾
        if (proj.hasMetadata("CustomExplosive")) {
            executeExplosion(p, event.getEntity().getLocation(), title);
            return;
        }

        // 通常命中爆発
        ConfigurationSection sec = root.getConfigurationSection("On_Hit_Explosion");
        if (sec != null && sec.getBoolean("Enable", false)) {
            if (checkConditions(p, "On_Hit_Explosion", sec)) {
                double chance = sec.getDouble("Explosion_Chance_Entity", 0.0);
                if (ThreadLocalRandom.current().nextDouble() < chance) {
                    executeExplosion(p, event.getEntity().getLocation(), title);
                    applyCooldown(p, "On_Hit_Explosion", sec);
                }
            }
        }
    }

    @EventHandler
    public void onHitBlock(WeaponHitBlockEvent event) {
        if (isExplosionLock) return;
        String title = event.getWeaponTitle();
        ConfigurationSection root = WeaponConfig.getWeaponConfig(title);
        ConfigurationSection sec = root.getConfigurationSection("On_Hit_Explosion");

        if (sec != null && sec.getBoolean("Enable", false)) {
            if (checkConditions(event.getPlayer(), "On_Hit_Explosion", sec)) {
                double chance = sec.getDouble("Explosion_Chance_Block", 0.0);
                if (ThreadLocalRandom.current().nextDouble() < chance) {
                    executeExplosion(event.getPlayer(), event.getBlock().getLocation(), title);
                    applyCooldown(event.getPlayer(), "On_Hit_Explosion", sec);
                }
            }
        }
    }

    private void executeExplosion(Player attacker, Location loc, String title) {
        // ロックをかけて二重発動を阻止
        isExplosionLock = true;
        try {
            // CSのAPIを使用。これによりDamage_MultiplierやExplosion_No_Griefが100%反映されます。
            API.getCSUtility().generateExplosion(attacker, loc, title);
        } finally {
            // 処理後に必ずロック解除
            isExplosionLock = false;
        }
    }

    // --- 3. クールダウン管理とモデル変更 ---
    private void applyCooldown(Player p, String feat, ConfigurationSection sec) {
        String cdKey = p.getUniqueId().toString() + "_" + feat;
        int cdTicks = sec.getInt("Cooldown_Ticks", 0);
        if (cdTicks <= 0) return;

        cooldownMap.put(cdKey, System.currentTimeMillis() + (cdTicks * 50L));

        // モデルID変更
        if (sec.contains("Custom_Model_Data_CD")) {
            ItemStack item = p.getInventory().getItemInMainHand();
            ItemMeta meta = item.getItemMeta();
            if (meta != null && meta.hasCustomModelData()) {
                int originalModel = meta.getCustomModelData();
                int cdModel = sec.getInt("Custom_Model_Data_CD");
                String weaponTitle = cs.getWeaponTitle(item);

                meta.setCustomModelData(cdModel);
                item.setItemMeta(meta);

                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (p.isOnline()) {
                            ItemStack current = p.getInventory().getItemInMainHand();
                            if (weaponTitle != null && weaponTitle.equals(cs.getWeaponTitle(current))) {
                                ItemMeta m = current.getItemMeta();
                                m.setCustomModelData(originalModel);
                                current.setItemMeta(m);
                            }
                        }
                    }
                }.runTaskLater(plugin, (long) cdTicks);
            }
        }

        if (sec.contains("Delay_Bar")) startDelayBar(p, sec.getConfigurationSection("Delay_Bar"), cdTicks);
        handleFeedback(p, sec);
    }

    // --- その他ユーティリティ (射撃・ダメージ) ---
    @EventHandler
    public void onShoot(WeaponShootEvent event) {
        executeGenericFeature(event.getPlayer(), event.getWeaponTitle(), "Sneak_Explosive_Shot", () -> {
            Player p = event.getPlayer();
            ConfigurationSection sec = WeaponConfig.getWeaponConfig(event.getWeaponTitle()).getConfigurationSection("Sneak_Explosive_Shot");
            int cost = sec.getInt("Extra_Ammo_Cost", 1);
            ItemStack item = p.getInventory().getItemInMainHand();
            int currentAmmo = API.getCSDirector().getAmmoBetweenBrackets(p, event.getWeaponTitle(), item);
            if (currentAmmo >= cost) {
                API.getCSDirector().csminion.replaceBrackets(item, String.valueOf(currentAmmo - cost), event.getWeaponTitle());
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
            ConfigurationSection sec = WeaponConfig.getWeaponConfig(title).getConfigurationSection("Life_Steal");
            double heal = event.getDamage() * sec.getDouble("Heal_Percent", 0.1);
            if (heal > 0) { p.setHealth(Math.min(p.getHealth() + heal, p.getMaxHealth())); return true; }
            return false;
        });
        if (event.isHeadshot()) executeGenericFeature(p, title, "Headshot_Ammo_Recovery", () -> { modifyAmmo(p, title, 1); return true; });
    }

    private void executeGenericFeature(Player p, String title, String feat, FeatureAction action) {
        ConfigurationSection sec = WeaponConfig.getWeaponConfig(title).getConfigurationSection(feat);
        if (sec == null || !sec.getBoolean("Enable", false)) return;
        if (!checkConditions(p, feat, sec)) return;
        if (ThreadLocalRandom.current().nextDouble() >= sec.getDouble("Chance", 1.0)) return;
        if (action.execute()) {
            applyCooldown(p, feat, sec);
            if (sec.getInt("Cooldown_Ticks", 0) <= 0) {
                handleFeedback(p, sec);
            }
        }
    }

    private boolean checkConditions(Player p, String feat, ConfigurationSection sec) {
        if (cooldownMap.getOrDefault(p.getUniqueId().toString() + "_" + feat, 0L) > System.currentTimeMillis()) return false;
        if (sec.getBoolean("Require_Sneak", false) && !p.isSneaking()) return false;
        String effStr = sec.getString("Required_Effect");
        if (effStr != null && !effStr.isEmpty()) {
            for (String eName : effStr.split(",")) {
                PotionEffectType t = PotionEffectType.getByName(eName.trim().toUpperCase());
                if (t != null && !p.hasPotionEffect(t)) return false;
            }
        }
        return true;
    }

    private void startDelayBar(Player p, ConfigurationSection sec, int ticks) {
        new BukkitRunnable() {
            int i = 0;
            public void run() {
                if (i >= ticks || !p.isOnline()) { this.cancel(); return; }
                String bar = buildBar((double) i / ticks, sec);
                p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(translate(sec.getString("Action_Bar").replace("{bar}", bar))));
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

    private void handleFeedback(Player p, ConfigurationSection s) {
        String snd = s.getString("Sound");
        if (snd != null && !snd.isEmpty()) p.playSound(p.getLocation(), Sound.valueOf(snd), 1f, 1f);
        String msg = s.getString("Message");
        if (msg != null && !msg.isEmpty()) p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(translate(msg)));
    }

    private String translate(String s) { return s == null ? "" : ChatColor.translateAlternateColorCodes('&', s); }
    private String repeat(String s, int n) { StringBuilder b = new StringBuilder(); for(int i=0; i<n; i++) b.append(s); return b.toString(); }
    @FunctionalInterface interface FeatureAction { boolean execute(); }
}