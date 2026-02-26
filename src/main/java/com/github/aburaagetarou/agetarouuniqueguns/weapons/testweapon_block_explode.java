package com.github.aburaagetarou.agetarouuniqueguns.weapons;

import com.github.aburaagetarou.agetarouuniqueguns.WeaponConfig;
import com.shampaggon.crackshot.CSUtility;
import com.shampaggon.crackshot.events.WeaponHitBlockEvent;
import me.DeeCaaD.CrackShotPlus.API;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import java.util.concurrent.ThreadLocalRandom;

/**
 * testweapon_instant_reload
 * 着弾時確率で爆発発生
 * 爆発規模はcsのexploode:を参照
 * @author pino223
 */

public class testweapon_block_explode implements Listener {

    public final static String WEAPON_NAME = "testweapon_block_explode";
    public final static String KEY_CHANCE_BLOCK = "Explosion_Chance_Block";
    public final static String KEY_CHANCE_ENTITY = "Explosion_Chance_Entity";
    private static final CSUtility cs = new CSUtility();

    private static boolean isLock = false;

    private static final ConfigurationSection defaultConfig = new YamlConfiguration(){{
        set(KEY_CHANCE_BLOCK, 0.2d);
        set(KEY_CHANCE_ENTITY, 0.2d);
    }};

    @SuppressWarnings("unchecked")
    public static <T> T getConfig(String key) {
        ConfigurationSection config = WeaponConfig.getWeaponConfig(WEAPON_NAME);
        if(config == null || !config.contains(key)) config = defaultConfig;
        try { return (T) config.get(key); } catch (ClassCastException ignore) { return (T) defaultConfig.get(key); }
    }

    /**
     * ブロック着弾時の爆発判定
     */
    @EventHandler
    public void onHitBlock(WeaponHitBlockEvent event) {
        if (!WEAPON_NAME.equals(event.getWeaponTitle()) || isLock) return;
        double chance = getConfig(KEY_CHANCE_BLOCK);
        if (ThreadLocalRandom.current().nextDouble() < chance) {
            executeExplosion(event.getPlayer(), event.getBlock().getLocation());
        }
    }

    /**
     * エンティティ（プレイヤー等）着弾時の爆発判定
     */
    @EventHandler
    public void onHitEntity(EntityDamageByEntityEvent event) {
        if (isLock) return;

        // 弾丸によるダメージのみを対象にする（これで爆風による無限ループを阻止）
        if (!(event.getDamager() instanceof Projectile)) return;
        Projectile projectile = (Projectile) event.getDamager();
        if (!(projectile.getShooter() instanceof Player)) return;

        // 武器名の確認
        String title = cs.getWeaponTitle(projectile);
        if (title == null || !WEAPON_NAME.equals(title)) return;

        double chance = getConfig(KEY_CHANCE_ENTITY);
        if (ThreadLocalRandom.current().nextDouble() < chance) {
            executeExplosion((Player) projectile.getShooter(), event.getEntity().getLocation());
        }
    }

    private void executeExplosion(Player attacker, Location loc) {
        isLock = true;
        try {
            API.getCSUtility().generateExplosion(attacker, loc, WEAPON_NAME);
        } finally {
            isLock = false;
        }
    }
}