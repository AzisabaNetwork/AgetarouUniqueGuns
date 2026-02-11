package com.github.aburaagetarou.agetarouuniqueguns.weapons;

import com.github.aburaagetarou.agetarouuniqueguns.WeaponConfig;
import com.github.aburaagetarou.agetarouuniqueguns.utils.CSUtilities;
import com.shampaggon.crackshot.events.WeaponDamageEntityEvent; // CrackShotのイベントをインポート
import me.DeeCaaD.CrackShotPlus.API;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

import java.util.concurrent.ThreadLocalRandom;

/**
 * testweapon_damage_to_ammo
 * ダメージを与えたとき確率で弾数が+1される
 * @author pino223
 */
public class testweapon_damage_to_ammo implements Listener {

    public final static String WEAPON_NAME = "testweapon_damage_to_ammo";
    public final static String KEY_SUPPLEMENT_CHANCE = "Supplement_Chance";

    // 初期設定 (確率はここで 0.1 = 10% と定義されています)
    private static final ConfigurationSection defaultConfig = new YamlConfiguration(){{
        set(KEY_SUPPLEMENT_CHANCE, 0.1d);
    }};

    /**
     * 設定情報の取得
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
     * CrackShot武器でダメージを与えたときの処理
     * @param event 武器ダメージイベント
     */
    @EventHandler
    public void onWeaponDamageEntity(WeaponDamageEntityEvent event) {
        // 攻撃者がプレイヤーでない場合は無視
        if (!(event.getPlayer() instanceof Player)) return;

        Player attacker = event.getPlayer();
        String eventWeaponTitle = event.getWeaponTitle();

        // アタッチメント等を考慮して元の武器名を取得し、このクラスの対象武器か確認
        // (CSUtilitiesの実装によりますが、アタッチメント名から親武器名を取得できる想定です)
        String originalName = CSUtilities.getOriginalWeaponName(eventWeaponTitle);

        // 対象の武器でない場合は終了
        if (!WEAPON_NAME.equals(originalName)) return;

        // 設定された確率(10%)で処理を実行
        double chance = getConfig(KEY_SUPPLEMENT_CHANCE); // 0.1
        // ThreadLocalRandomを使うとパフォーマンスが良いですが、Math.random()でも構いません
        if (ThreadLocalRandom.current().nextDouble() > chance) return;

        // プレイヤーが手に持っているアイテムを取得
        ItemStack weaponItem = attacker.getInventory().getItemInMainHand();

        // 手に持っているアイテムが本当にその武器か念のため確認 (名前チェック)
        String heldWeaponTitle = API.getCSUtility().getWeaponTitle(weaponItem);
        if (heldWeaponTitle == null || !WEAPON_NAME.equals(CSUtilities.getOriginalWeaponName(heldWeaponTitle))) {
            return;
        }

        // --- 弾薬回復処理 ---

        // 現在の弾数を取得 (CrackShotPlusのAPIを使用)
        // [10/30] のような表記から左側の数字を取得します
        int currentAmmo = API.getCSDirector().getAmmoBetweenBrackets(attacker, heldWeaponTitle, weaponItem);

        // 弾数を+1して更新
        // replaceBracketsはアイテムのDisplay Nameの数字部分を書き換えます
        API.getCSDirector().csminion.replaceBrackets(weaponItem, String.valueOf(currentAmmo + 1), heldWeaponTitle);

        // 必要であれば効果音やメッセージを追加
        attacker.sendMessage("§a弾薬が回復しました！");
    }
}