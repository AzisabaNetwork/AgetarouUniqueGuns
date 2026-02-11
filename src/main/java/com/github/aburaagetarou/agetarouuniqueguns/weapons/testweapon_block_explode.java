package com.github.aburaagetarou.agetarouuniqueguns.weapons;

import com.github.aburaagetarou.agetarouuniqueguns.WeaponConfig;
import com.shampaggon.crackshot.events.WeaponDamageEntityEvent;
import com.shampaggon.crackshot.events.WeaponHitBlockEvent;
import me.DeeCaaD.CrackShotPlus.API;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import java.util.concurrent.ThreadLocalRandom;

/**
 * testweapon_instant_reload
 * 着弾時確率で爆発発生
 * 爆発規模はcsのexploode:を参照
 * @author pino223
 */

public class testweapon_block_explode implements Listener {

    public final static String WEAPON_NAME = "testweapon_block_explode";
    public final static String KEY_EXPLOSION_CHANCE = "Explosion_Chance";

    private static final ConfigurationSection defaultConfig = new YamlConfiguration(){{
        set(KEY_EXPLOSION_CHANCE, 0.2d);
    }};

    @SuppressWarnings("unchecked")
    public static <T> T getConfig(String key) {
        ConfigurationSection config = WeaponConfig.getWeaponConfig(WEAPON_NAME);
        if(config == null || !config.contains(key)) {
            config = defaultConfig;
        }
        try {
            return (T) config.get(key);
        } catch (ClassCastException ignore) {
            return (T) defaultConfig.get(key);
        }
    }

    /**
     * ブロック着弾時の爆発判定
     */
    @EventHandler
    public void onHitBlock(WeaponHitBlockEvent event) {
        if (!WEAPON_NAME.equals(event.getWeaponTitle())) return;
        executeExplosion(event.getPlayer(), event.getBlock().getLocation());
    }

    /**
     * エンティティ（プレイヤー等）着弾時の爆発判定
     */
    @EventHandler
    public void onHitEntity(WeaponDamageEntityEvent event) {
        if (!WEAPON_NAME.equals(event.getWeaponTitle())) return;
        // 被害者の足元で爆発を発生させる
        executeExplosion(event.getPlayer(), event.getVictim().getLocation());
    }

    /**
     * 共通の爆発実行ロジック
     */
    private void executeExplosion(org.bukkit.entity.Player attacker, Location loc) {
        double chance = getConfig(KEY_EXPLOSION_CHANCE);
        if (ThreadLocalRandom.current().nextDouble() < chance) {
            // このWEAPON_NAMEと同じ名前のCS設定ファイル内のExplosions設定が使われます
            API.getCSUtility().generateExplosion(attacker, loc, WEAPON_NAME);
        }
    }
}