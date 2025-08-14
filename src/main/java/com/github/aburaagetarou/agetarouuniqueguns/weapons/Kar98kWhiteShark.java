package com.github.aburaagetarou.agetarouuniqueguns.weapons;

import com.github.aburaagetarou.agetarouuniqueguns.WeaponConfig;
import com.github.aburaagetarou.agetarouuniqueguns.listeners.CSListeners;
import com.github.aburaagetarou.agetarouuniqueguns.utils.CSUtilities;
import me.DeeCaaD.CrackShotPlus.API;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Kar98kWhiteShark
 * キルごとに残弾数が回復する
 * @author pino223
 */
public class Kar98kWhiteShark implements Listener {

    public final static String WEAPON_NAME = "Kar98kWhiteShark";

    public final static String KEY_SUPPLEMENT_CHANCE = "Supplement_Chance";

    // 初期設定
    private static final ConfigurationSection defaultConfig = new YamlConfiguration(){{{
        set(KEY_SUPPLEMENT_CHANCE, 1.0d);
    }}};

    /**
     * 設定情報の取得
     * @param key キー
     * @return Object 設定情報、存在しない場合はデフォルト設定
     */
    @SuppressWarnings("unchecked")
    public static <T> T getConfig(String key) {
        ConfigurationSection config = WeaponConfig.getWeaponConfig(WEAPON_NAME);
        if(config == null) {
            config = defaultConfig;
        }
        if(!config.contains(key)) {
            config = defaultConfig;
        }
        try {
            return (T) config.get(key);
        }
        catch (ClassCastException ignore) {
            return (T) defaultConfig.get(key);
        }
    }

    /**
     * キル時処理
     * @param event キルイベント
     */
    @EventHandler
    public void onPlayerKill(PlayerDeathEvent event) {
        if(event.getEntity().getKiller() == null) return;
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if(!WEAPON_NAME.equals(CSListeners.getDamagedWeaponTitle(killer))) return;

        // 所持していない場合は処理しない
        ItemStack weapon = killer.getInventory().getItemInMainHand();
        if(!WEAPON_NAME.equals(CSUtilities.getOriginalWeaponName(API.getCSUtility().getWeaponTitle(weapon)))) return;
        String weaponTitle = API.getCSUtility().getWeaponTitle(weapon);

        // 指定確率で残弾数回復
        double chance = getConfig(KEY_SUPPLEMENT_CHANCE);
        if(Double.compare(Math.random(), chance) > 0) return;

        // 残弾数回復
        int ammo = API.getCSDirector().getAmmoBetweenBrackets(killer, weaponTitle, weapon);
        API.getCSDirector().csminion.replaceBrackets(weapon, String.valueOf(ammo + 1), weaponTitle);
    }

}
