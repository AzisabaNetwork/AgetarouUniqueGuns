package com.github.aburaagetarou.agetarouuniqueguns.weapons;

import com.github.aburaagetarou.agetarouuniqueguns.AgetarouUniqueGuns;
import com.github.aburaagetarou.agetarouuniqueguns.WeaponConfig;
import com.github.aburaagetarou.agetarouuniqueguns.utils.CSUtilities;
import com.shampaggon.crackshot.events.WeaponDamageEntityEvent;
import com.shampaggon.crackshot.events.WeaponShootEvent;
import me.DeeCaaD.CrackShotPlus.API;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.potion.PotionEffectType;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

/**
 * AntiOnePunchMan
 * 効果時間中、1Tick内に受けるダメージの最大値を固定する
 * @author AburaAgeTarou
 */
public class AntiOnePunchMan implements Listener {

    public final static String WEAPON_NAME = "Deflection_Shield";
    public final static String KEY_POTION_EFFECTS = "Potion_Effect";

    public final static String KEY_MAX_DAMAGE_PER_TICK = "Max_Damage_Per_Tick";
    public final static String KEY_THRESHOLD_DAMAGE = "Threshold_Damage";
    public final static String KEY_EFFECT_DURATION = "Effect_Duration";

    // 初期設定
    private static final ConfigurationSection defaultConfig = new YamlConfiguration(){{{
        set(KEY_MAX_DAMAGE_PER_TICK, 19.0d);
        set(KEY_THRESHOLD_DAMAGE, 20.0d);
        set(KEY_EFFECT_DURATION, 240);
    }}};

    // 効果発動中のプレイヤー（値は効果が終了するサーバーTick）
    private static final Map<Entity, Integer> effectPlayers = new HashMap<>();

    // 右クリックして被弾を待っている「待機状態」のプレイヤー
    private static final Map<Entity, Boolean> readyPlayers = new HashMap<>();

    // 被ダメージカウント
    private static final Map<Entity, Double> damageCount = new HashMap<>();

    private static BukkitTask timerTask;

    /**
     * 被ダメージ数カウントリセットタイマー開始
     */
    public static void initTimer() {
        timerTask = Bukkit.getScheduler().runTaskTimer(AgetarouUniqueGuns.getInstance(), damageCount::clear, 0, 1);
    }

    /**
     * 設定からポーションエフェクトリストを取得
     * 書式: "EFFECT_NAME-DURATION-AMPLIFIER" (例: "SLOW_DIGGING-50-1")
     * @return List<PotionEffect>
     */
    public static List<PotionEffect> getPotionEffects() {
        List<PotionEffect> result = new ArrayList<>();
        ConfigurationSection config = WeaponConfig.getWeaponConfig(WEAPON_NAME);
        if (config == null || !config.contains(KEY_POTION_EFFECTS)) return result;

        List<String> entries = config.getStringList(KEY_POTION_EFFECTS);
        // 単一文字列でも対応 (Java 8 対応)
        if (entries.isEmpty() && config.isString(KEY_POTION_EFFECTS)) {
            entries = Collections.singletonList(config.getString(KEY_POTION_EFFECTS));
        }

        for (String entry : entries) {
            String[] parts = entry.split("-");
            if (parts.length != 3) {
                AgetarouUniqueGuns.getInstance().getLogger().warning(
                        "Invalid potion effect format: '" + entry + "'. Expected EFFECT_NAME-DURATION-AMPLIFIER");
                continue;
            }
            PotionEffectType type = PotionEffectType.getByName(parts[0]);
            if (type == null) {
                AgetarouUniqueGuns.getInstance().getLogger().warning(
                        "Unknown potion effect type: " + parts[0]);
                continue;
            }
            try {
                int duration = Integer.parseInt(parts[1]);
                int amplifier = Integer.parseInt(parts[2]) - 1; // 0始まりなので-1
                result.add(new PotionEffect(type, duration, amplifier, false, true));
            } catch (NumberFormatException e) {
                AgetarouUniqueGuns.getInstance().getLogger().warning(
                        "Invalid number in potion effect: '" + entry + "'");
            }
        }
        return result;
    }

    /**
     * タイマー停止
     */
    public static void stopTimer() {
        if(timerTask != null) timerTask.cancel();
    }

    /**
     * 設定情報の取得
     * @param key キー
     * @return Object 設定情報、存在しない場合はデフォルト設定
     */
    public static Object getConfig(String key) {
        ConfigurationSection config = WeaponConfig.getWeaponConfig(WEAPON_NAME);
        if(config == null) {
            config = defaultConfig;
        }
        if(!config.contains(key)) {
            config = defaultConfig;
        }
        return config.get(key);
    }

    /**
     * 設定から最大被ダメージ値を取得
     * @return double 最大被ダメージ値
     */
    public static double getDamageLimitPerTick() {
        try {
            return new BigDecimal(getConfig(KEY_MAX_DAMAGE_PER_TICK).toString()).doubleValue();
        }
        catch (ClassCastException e) {
            AgetarouUniqueGuns.getInstance().getLogger().warning("Invalid configuration value: " + WEAPON_NAME + "." + KEY_MAX_DAMAGE_PER_TICK);
            return defaultConfig.getDouble(KEY_MAX_DAMAGE_PER_TICK);
        }
    }

    /**
     * 設定から被ダメージ反応閾値を取得
     * @return double 最大被ダメージ値
     */
    public static double getThresholdDamage() {
        try {
            return new BigDecimal(getConfig(KEY_THRESHOLD_DAMAGE).toString()).doubleValue();
        }
        catch (ClassCastException e) {
            AgetarouUniqueGuns.getInstance().getLogger().warning("Invalid configuration value: " + WEAPON_NAME + "." + KEY_THRESHOLD_DAMAGE);
            return defaultConfig.getDouble(KEY_THRESHOLD_DAMAGE);
        }
    }

    /**
     * 設定から効果時間を取得
     * @return int 効果時間(Tick)
     */
    public static int getEffectDuration() {
        try {
            return Integer.parseInt(getConfig(KEY_EFFECT_DURATION).toString());
        }
        catch (ClassCastException e) {
            AgetarouUniqueGuns.getInstance().getLogger().warning("Invalid configuration value: " + WEAPON_NAME + "." + KEY_EFFECT_DURATION);
            return defaultConfig.getInt(KEY_EFFECT_DURATION);
        }
    }

    /**
     * 被弾時に呼び出され、実際にシールドとポーションの効果を発動する
     * @param player プレイヤー
     * @param weaponTitle 武器の識別称号
     */
    public static void apply(Player player, String weaponTitle) {
        String weaponName = API.getCSDirector().getString(weaponTitle + ".Item_Information.Item_Name");
        if (weaponName == null) weaponName = WEAPON_NAME;
        final String finalWeaponName = weaponName;
        int duration = getEffectDuration();
        effectPlayers.put(player, Bukkit.getServer().getCurrentTick() + duration);
        Bukkit.getScheduler().runTaskLater(AgetarouUniqueGuns.getInstance(), () -> {
            player.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(finalWeaponName + " &cの効果が切れました"));
            effectPlayers.remove(player);
        }, duration);
    }

    /**
     * 武器使用（右クリックした瞬間）
     * 実際に発動はせず、被弾を待つ「待機状態」に登録する
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onWeaponShoot(WeaponShootEvent event) {
        String weaponTitle = CSUtilities.getOriginalWeaponName(event.getWeaponTitle());
        if(!WEAPON_NAME.equals(weaponTitle)) return;

        // 待機状態（構え状態）にする
        readyPlayers.put(event.getPlayer(), true);
    }

    /**
     * 被ダメージイベント
     */
    @EventHandler
    public void onDamage(WeaponDamageEntityEvent event) {
        Entity victim = event.getVictim();
        if (!(victim instanceof Player)) return;
        Player player = (Player) victim;

        int currentTick = Bukkit.getServer().getCurrentTick();

        // 1. 待機状態中にダメージを受けたら、ここで初めてシールド効果を「発動」させる
        if (readyPlayers.getOrDefault(player, false) && effectPlayers.getOrDefault(player, 0) <= currentTick) {
            readyPlayers.remove(player); // 待機状態を解除

            // メインハンドの武器のタイトルを取得してapplyに渡す
            String weaponTitle = API.getCSUtility().getWeaponTitle(player.getInventory().getItemInMainHand());
            if (weaponTitle != null) {
                apply(player, weaponTitle);
            }
        }

        // 2. シールド効果時間中のダメージ軽減・無効化処理
        if (effectPlayers.getOrDefault(player, 0) > currentTick) {
			// ダメージが閾値未満であれば
			if(event.getDamage() < getThresholdDamage()) {
				return;
			}
			double dpt = AntiOnePunchMan.damageCount.getOrDefault(player, 0.0d);
            if(dpt >= getDamageLimitPerTick()) {
                player.addPotionEffects(getPotionEffects());
                event.setCancelled(true);
                return;
            }

            double newDpt = dpt + event.getDamage();

            if(newDpt >= getDamageLimitPerTick()) {
                player.addPotionEffects(getPotionEffects());
                event.setDamage(getDamageLimitPerTick() - dpt);
            }

            AntiOnePunchMan.damageCount.put(player, newDpt);
        }
    }
}