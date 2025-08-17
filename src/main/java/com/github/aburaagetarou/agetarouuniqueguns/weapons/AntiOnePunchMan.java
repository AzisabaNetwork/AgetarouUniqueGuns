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
import org.bukkit.scheduler.BukkitTask;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * AntiOnePunchMan
 * 効果時間中、1Tick内に受けるダメージの最大値を固定する
 * @author AburaAgeTarou
 */
public class AntiOnePunchMan implements Listener {

    public final static String WEAPON_NAME = "AntiOnePunchMan";

    public final static String KEY_MAX_DAMAGE_PER_TICK = "Max_Damage_Per_Tick";
    public final static String KEY_EFFECT_DURATION = "Effect_Duration";

    // 初期設定
    private static final ConfigurationSection defaultConfig = new YamlConfiguration(){{{
        set(KEY_MAX_DAMAGE_PER_TICK, 19.0d);
        set(KEY_EFFECT_DURATION, 240);
    }}};

    // 効果発動中のプレイヤー
    private static final Map<Entity, Integer> effectPlayers = new HashMap<>();

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
     * 効果発動
     * @param player プレイヤー
     */
    public static void apply(Player player) {
        String weaponTitle = API.getCSUtility().getWeaponTitle(player.getInventory().getItemInMainHand());
        if(weaponTitle == null) return;
        String orgWeaponTitle = CSUtilities.getOriginalWeaponName(weaponTitle);
        if(!WEAPON_NAME.equals(orgWeaponTitle)) return;

        String weaponName = API.getCSDirector().getString(weaponTitle + ".Item_Information.Item_Name");

        int duration = getEffectDuration();
        effectPlayers.put(player, Bukkit.getServer().getCurrentTick() + duration);
        Bukkit.getScheduler().runTaskLater(AgetarouUniqueGuns.getInstance(), () -> {
            player.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(weaponName + " &cの効果が切れました"));
        }, duration);
    }

    /**
     * 武器使用
     * @param event 武器使用イベント
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onWeaponShoot(WeaponShootEvent event) {
        String weaponTitle = CSUtilities.getOriginalWeaponName(event.getWeaponTitle());
        if(!WEAPON_NAME.equals(weaponTitle)) return;

        apply(event.getPlayer());
    }

    /**
     * 被ダメージ
     * @param event 被ダメージイベント
     */
    @EventHandler
    public void onDamage(WeaponDamageEntityEvent event) {
        Entity victim = event.getVictim();

        if(effectPlayers.getOrDefault(victim, 0) > Bukkit.getServer().getCurrentTick()) {
            double dpt = AntiOnePunchMan.damageCount.getOrDefault(victim, 0.0d);
            if(dpt >= getDamageLimitPerTick()) {
                event.setCancelled(true);
                return;
            }
            double newDpt = dpt + event.getDamage();
            if(newDpt >= getDamageLimitPerTick()) {
                event.setDamage(getDamageLimitPerTick() - dpt);
            }
            AntiOnePunchMan.damageCount.put(victim, newDpt);
        }
        else {
            effectPlayers.remove(victim);
        }
    }
}
