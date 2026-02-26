package com.github.aburaagetarou.agetarouuniqueguns.weapons;

import com.github.aburaagetarou.agetarouuniqueguns.WeaponConfig;
import com.shampaggon.crackshot.events.WeaponReloadEvent;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import java.util.concurrent.ThreadLocalRandom;

/**
 * testweapon_instant_reload
 * リロード時確率で即座にリロードが終わる
 * @author pino223
 */

public class testweapon_instant_reload implements Listener {

    public final static String WEAPON_NAME = "testweapon_instant_reload";
    public final static String KEY_RELOAD_CHANCE = "Instant_Reload_Chance";

    // デフォルト設定 (10%)
    private static final ConfigurationSection defaultConfig = new YamlConfiguration(){{
        set(KEY_RELOAD_CHANCE, 0.1d);
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

    @EventHandler
    public void onReload(WeaponReloadEvent event) {
        if (!WEAPON_NAME.equals(event.getWeaponTitle())) return;

        // configから確率を取得
        double chance = getConfig(KEY_RELOAD_CHANCE);

        if (ThreadLocalRandom.current().nextDouble() < chance) {
            event.setReloadDuration(0); // 即座に完了
            event.getPlayer().sendMessage("§b§lINSTANT RELOAD!");
        }
    }
}