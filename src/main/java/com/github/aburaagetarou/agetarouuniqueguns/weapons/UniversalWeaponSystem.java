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
import com.shampaggon.crackshot.events.WeaponPreShootEvent;
import me.DeeCaaD.CrackShotPlus.Events.WeaponHeldEvent;


import org.bukkit.event.player.PlayerItemHeldEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class UniversalWeaponSystem implements Listener {

    private final JavaPlugin plugin;
    private final CSUtility cs = new CSUtility();
    private static boolean isExplosionLock = false;
    private final Map<String, Long> cooldownMap = new HashMap<>();
    // プレイヤーごとの武器ロック解除時間を保持 (UUID -> 解除ミリ秒)
    private final Map<java.util.UUID, Long> switchLockMap = new HashMap<>();

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

        // クールタイムチェック
        String cdKey = p.getUniqueId().toString() + "_Instant_Reload";
        if (cooldownMap.getOrDefault(cdKey, 0L) <= System.currentTimeMillis()) {

            // 1. 最大弾数を取得
            int magSize = root.getInt("Shoot.Capacity", 0);

            // 2. 現在の弾数を取得
            int currentAmmo = API.getCSDirector().getAmmoBetweenBrackets(p, title, item);

            // 3. 足したい数を取得 (設定になければ 1)
            int addAmount = sec.getInt("Add_Amount", 1);

            // 4. 新しい弾数を計算 (現在 + 追加)
            int nextAmmo = currentAmmo + addAmount;

            // ★ 対策：最大弾数を超えないように制限する
            if (magSize > 0 && nextAmmo > magSize) {
                nextAmmo = magSize;
            }

            // 5. 弾数を書き換え (modifyAmmo ではなく直接 replaceBrackets を使うのが確実です)
            API.getCSDirector().csminion.replaceBrackets(item, String.valueOf(nextAmmo), title);

            // フィードバックとクールタイム
            handleFeedback(p, sec);
            applyCooldown(p, "Instant_Reload", sec);
        }
    }

    private int getMagSize(String title) {
        ConfigurationSection config = WeaponConfig.getWeaponConfig(title);
        if (config == null) return 0;

        // CrackShotの標準的なパス「Shoot.Capacity」を確認
        if (config.contains("Shoot.Capacity")) {
            return config.getInt("Shoot.Capacity");
        }
        // もしダメなら「Reload.Reload_Amount」などを確認（武器によって異なるため）
        if (config.contains("Reload.Reload_Amount")) {
            return config.getInt("Reload.Reload_Amount");
        }
        return 0;
    }

    // 優先順位を最高に設定することで、CrackShotの設定を上書きします
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
            // すでに始まってしまった通常リロードの時間を最小(1)にする
            event.setReloadDuration(1);

            // 弾を補充
            int magSize = root.getInt("Shoot.Capacity", 0);
            if (magSize > 0) {
                fillAmmo(p, title, magSize);
            }
        }
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
        if (root == null) return; // ★ NPE対策

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
        if (title == null) return; // ★ NPE対策

        ConfigurationSection root = WeaponConfig.getWeaponConfig(title);
        if (root == null) return; // ★ NPE対策

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
        isExplosionLock = true;
        try {
            API.getCSUtility().generateExplosion(attacker, loc, title);
        } finally {
            isExplosionLock = false;
        }
    }

    // --- 3. クールダウン管理とモデル変更 ---
    private void applyCooldown(Player p, String feat, ConfigurationSection sec) {
        String cdKey = p.getUniqueId().toString() + "_" + feat;
        int cdTicks = sec.getInt("Cooldown_Ticks", 0);
        if (cdTicks <= 0) return;

        cooldownMap.put(cdKey, System.currentTimeMillis() + (cdTicks * 50L));

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
        // フィードバックは executeGenericFeature 側で一括処理するためここでは呼ばない（二重表示防止）
    }

    // --- 5. 持ち替えロック機能 ---
    @EventHandler
    public void onItemHeld(PlayerItemHeldEvent event) {
        Player p = event.getPlayer();
        ItemStack item = p.getInventory().getItem(event.getNewSlot());
        String title = cs.getWeaponTitle(item);
        applySwitchLock(p, title);
    }

    @EventHandler
    public void onWeaponHeld(me.DeeCaaD.CrackShotPlus.Events.WeaponHeldEvent event) {
        // 3枚目の画像で確認した通り、CSP 1.108 のこのイベントには getPlayer() があります
        Player p = event.getPlayer();
        if (p == null) return;

        // 武器名を取得してロックを適用
        applySwitchLock(p, event.getWeaponTitle());
    }

    /**
     * ロックを適用する共通メソッド
     */
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

        // ロック時間をミリ秒で保存
        switchLockMap.put(p.getUniqueId(), System.currentTimeMillis() + (ticks * 50L));

        // フィードバック表示（音・メッセージ・バー）
        handleFeedback(p, sec);
        if (sec.contains("Delay_Bar")) {
            startDelayBar(p, sec.getConfigurationSection("Delay_Bar"), ticks);
        }
    }

    // --- その他ユーティリティ (射撃・ダメージ) ---
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPreShoot(com.shampaggon.crackshot.events.WeaponPreShootEvent event) {
        Player p = event.getPlayer();
        // switchLockMap から解除時刻を取得
        Long unlockTime = switchLockMap.get(p.getUniqueId());

        if (unlockTime != null && unlockTime > System.currentTimeMillis()) {
            // 解除時刻より前なら、射撃イベントそのものをキャンセルする
            event.setCancelled(true);
        }
    }
    @EventHandler
    public void onShoot(WeaponShootEvent event) {
        String title = event.getWeaponTitle();
        if (title == null) return;

        // スニーク特殊弾のロジック
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
                // 追加の弾数を消費
                API.getCSDirector().csminion.replaceBrackets(item, String.valueOf(currentAmmo - cost), title);
                // 弾に爆発用のメタデータを付与
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
        if (title == null) return; // ★ NPE対策

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

    // ★ 最も重要なNPEガードを実装した共通処理メソッド
    private void executeGenericFeature(Player p, String title, String feat, FeatureAction action) {
        ConfigurationSection root = WeaponConfig.getWeaponConfig(title);
        if (root == null) return; // ★ 設定が存在しない武器の場合は即終了

        ConfigurationSection sec = root.getConfigurationSection(feat);
        if (sec == null || !sec.getBoolean("Enable", false)) return; // ★ セクションがない、または無効なら終了

        if (!checkConditions(p, feat, sec)) return;
        if (ThreadLocalRandom.current().nextDouble() >= sec.getDouble("Chance", 1.0)) return;

        if (action.execute()) {
            applyCooldown(p, feat, sec);
            // クールダウンの有無に関わらず、発動した場合はフィードバック(音・メッセージ)を鳴らす
            handleFeedback(p, sec);
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
                // クールタイム終了時
                if (i >= ticks || !p.isOnline()) {
                    // ★ 修正：終了時のメッセージを表示する
                    String endMsg = sec.getString("End_Action_Bar");
                    if (endMsg != null && !endMsg.isEmpty() && p.isOnline()) {
                        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(translate(endMsg)));
                    }
                    this.cancel();
                    return;
                }

                // ゲージ表示中
                String actionStr = sec.getString("Action_Bar");
                if (actionStr != null) {
                    String bar = buildBar((double) i / ticks, sec);
                    p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(translate(actionStr.replace("{bar}", bar))));
                }
                i += 2; // 2ティックごとに更新
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

    /**
     * 弾を指定数に書き換えるユーティリティ
     */
    private void fillAmmo(Player p, String title, int amount) {
        ItemStack item = p.getInventory().getItemInMainHand();
        if (title.equals(cs.getWeaponTitle(item))) {
            // CrackShotPlus APIを使用して弾数を書き換え
            API.getCSDirector().csminion.replaceBrackets(item, String.valueOf(amount), title);
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