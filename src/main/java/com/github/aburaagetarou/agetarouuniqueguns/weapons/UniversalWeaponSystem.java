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
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
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

    // ご提示いただいた元の確実なロック機構
    private static boolean isExplosionLock = false;

    private final Map<String, Long> cooldownMap = new HashMap<>();

    public UniversalWeaponSystem(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    // =======================================================
    // 1. 射撃・リロード (汎用機能)
    // =======================================================

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
                // シフト爆発弾のタグを付与
                event.getProjectile().setMetadata("CustomExplosive", new FixedMetadataValue(plugin, true));
                return true;
            }
            return false;
        });
    }

    @EventHandler
    public void onReload(WeaponReloadEvent event) {
        executeGenericFeature(event.getPlayer(), event.getWeaponTitle(), "Instant_Reload", () -> {
            event.setReloadDuration(0);
            return true;
        });
    }

    @EventHandler
    public void onWeaponDamage(WeaponDamageEntityEvent event) {
        Player p = event.getPlayer();
        String title = event.getWeaponTitle();
        if (title == null) return;

        // 弾数回復
        executeGenericFeature(p, title, "On_Hit_Recovery", () -> {
            modifyAmmo(p, title, 1);
            return true;
        });

        // 生命力吸収 (確実なCrackShotのダメージ量を使用)
        executeGenericFeature(p, title, "Life_Steal", () -> {
            ConfigurationSection sec = WeaponConfig.getWeaponConfig(title).getConfigurationSection("Life_Steal");
            double healAmount = event.getDamage() * sec.getDouble("Heal_Percent", 0.1);
            if (healAmount > 0) {
                p.setHealth(Math.min(p.getHealth() + healAmount, p.getMaxHealth()));
                return true;
            }
            return false;
        });

        // ヘッドショット回復
        if (event.isHeadshot()) {
            executeGenericFeature(p, title, "Headshot_Ammo_Recovery", () -> {
                modifyAmmo(p, title, 1);
                return true;
            });
        }
    }

    // =======================================================
    // 2. ご提示いただいた爆発ロジックの完全復元 + 条件判定
    // =======================================================

    /**
     * 地形（ブロック）着弾時の爆発判定
     */
    @EventHandler
    public void onHitBlock(WeaponHitBlockEvent event) {
        if (isExplosionLock) return;

        Player p = event.getPlayer();
        String title = event.getWeaponTitle();
        ConfigurationSection sec = WeaponConfig.getWeaponConfig(title).getConfigurationSection("On_Hit_Explosion");

        if (sec != null && sec.getBoolean("Enable", false)) {
            // クールタイムやポーションの条件をクリアしているか
            if (!checkConditions(p, "On_Hit_Explosion", sec)) return;

            // 地形用の確率判定
            double chance = sec.getDouble("Explosion_Chance_Block", 0.0);
            if (ThreadLocalRandom.current().nextDouble() < chance) {
                executeExplosion(p, event.getBlock().getLocation(), title);
                applyCooldown(p, "On_Hit_Explosion", sec); // 爆発成功時のみクールダウンを付与
            }
        }
    }

    /**
     * エンティティ着弾時の爆発判定
     */
    @EventHandler
    public void onHitEntityExplosion(EntityDamageByEntityEvent event) {
        if (isExplosionLock) return;

        // 弾丸によるダメージのみを対象にする（これで爆風による無限ループを阻止）
        if (!(event.getDamager() instanceof Projectile)) return;
        Projectile proj = (Projectile) event.getDamager();
        if (!(proj.getShooter() instanceof Player)) return;

        Player p = (Player) proj.getShooter();

        // 武器名の確認
        String title = cs.getWeaponTitle(proj);
        if (title == null) return;

        // 1. スニーク特殊弾（メタデータ）の判定
        if (proj.hasMetadata("CustomExplosive")) {
            executeExplosion(p, event.getEntity().getLocation(), title);
            return; // シフト爆発の場合は処理終了
        }

        // 2. 通常の確率爆発の判定
        ConfigurationSection sec = WeaponConfig.getWeaponConfig(title).getConfigurationSection("On_Hit_Explosion");
        if (sec != null && sec.getBoolean("Enable", false)) {
            if (!checkConditions(p, "On_Hit_Explosion", sec)) return;

            // エンティティ用の確率判定
            double chance = sec.getDouble("Explosion_Chance_Entity", 0.0);
            if (ThreadLocalRandom.current().nextDouble() < chance) {
                executeExplosion(p, event.getEntity().getLocation(), title);
                applyCooldown(p, "On_Hit_Explosion", sec); // 爆発成功時のみクールダウンを付与
            }
        }
    }

    /**
     * 爆発実行メソッド (元の完璧なロック機構)
     */
    private void executeExplosion(Player attacker, Location loc, String title) {
        isExplosionLock = true;
        try {
            API.getCSUtility().generateExplosion(attacker, loc, title);
        } finally {
            isExplosionLock = false;
        }
    }

    // =======================================================
    // 3. ユーティリティ (条件判定・クールダウン・バー表示)
    // =======================================================

    private void executeGenericFeature(Player p, String title, String feat, FeatureAction action) {
        ConfigurationSection root = WeaponConfig.getWeaponConfig(title);
        if (root == null) return;

        ConfigurationSection sec = root.getConfigurationSection(feat);
        if (sec == null || !sec.getBoolean("Enable", false)) return;

        if (!checkConditions(p, feat, sec)) return;
        if (ThreadLocalRandom.current().nextDouble() >= sec.getDouble("Chance", 1.0)) return;

        if (action.execute()) {
            applyCooldown(p, feat, sec);
        }
    }

    private boolean checkConditions(Player p, String feat, ConfigurationSection sec) {
        // クールタイム中かチェック
        String cdKey = p.getUniqueId().toString() + "_" + feat;
        if (cooldownMap.getOrDefault(cdKey, 0L) > System.currentTimeMillis()) return false;

        // スニーク必須かチェック
        if (sec.getBoolean("Require_Sneak", false) && !p.isSneaking()) return false;

        // エフェクト複数対応 (例: "SPEED,STRENGTH")
        String effStr = sec.getString("Required_Effect");
        if (effStr != null && !effStr.isEmpty()) {
            for (String eName : effStr.split(",")) {
                PotionEffectType t = PotionEffectType.getByName(eName.trim().toUpperCase());
                if (t != null && !p.hasPotionEffect(t)) return false; // 1つでも欠けていたら不発
            }
        }
        return true;
    }

    private void applyCooldown(Player p, String feat, ConfigurationSection sec) {
        String cdKey = p.getUniqueId().toString() + "_" + feat;
        int cdTicks = sec.getInt("Cooldown_Ticks", 0);

        if (cdTicks > 0) {
            cooldownMap.put(cdKey, System.currentTimeMillis() + (cdTicks * 50L));
            if (sec.contains("Delay_Bar")) {
                startDelayBar(p, sec.getConfigurationSection("Delay_Bar"), cdTicks);
            }
        }
        handleFeedback(p, sec);
    }

    private void startDelayBar(Player p, ConfigurationSection sec, int ticks) {
        new BukkitRunnable() {
            int i = 0;
            public void run() {
                if (i >= ticks || !p.isOnline()) { sendVisual(p, sec, "End_"); this.cancel(); return; }
                String bar = buildBar((double) i / ticks, sec);
                String act = sec.getString("Action_Bar");
                if (act != null) p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(translate(act.replace("{bar}", bar))));
                i += 2;
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    private String buildBar(double pct, ConfigurationSection sec) {
        int len = sec.getInt("Symbol_Amount", 15);
        String sym = sec.getString("Symbol", "|");
        int left = (int) (pct * len);
        return translate(sec.getString("Left_Color", "&a")) + repeat(sym, left) + translate(sec.getString("Right_Color", "&c")) + repeat(sym, len - left);
    }

    private void sendVisual(Player p, ConfigurationSection sec, String pre) {
        String act = sec.getString(pre + "Action_Bar");
        if (act != null) p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(translate(act)));
    }

    private void modifyAmmo(Player p, String t, int a) {
        ItemStack i = p.getInventory().getItemInMainHand();
        if (t.equals(API.getCSUtility().getWeaponTitle(i))) {
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