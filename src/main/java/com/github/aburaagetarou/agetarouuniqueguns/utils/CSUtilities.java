package com.github.aburaagetarou.agetarouuniqueguns.utils;

import com.github.aburaagetarou.agetarouuniqueguns.WeaponConfig;
import me.DeeCaaD.CrackShotPlus.API;
import me.DeeCaaD.CrackShotPlus.CSPPlayer;
import me.DeeCaaD.CrackShotPlus.Events.WeaponSkinEvent;
import me.DeeCaaD.CrackShotPlus.Skin;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * CrackShot関連のユーティリティ
 * @author AburaAgeTarou
 */
public class CSUtilities {

	/**
	 * オリジナルの武器名を取得
	 * @param weaponName 武器名
	 * @return String 設定に"Original"が存在する場合はその値、存在しない場合は引数の値
	 */
	public static String getOriginalWeaponName(String weaponName) {
		ConfigurationSection config = WeaponConfig.getWeaponConfig(weaponName);
		if(config == null) {
			return weaponName;
		}
		if(!config.contains("Original")) {
			return weaponName;
		}
		return config.getString("Original");
	}

	/**
	 * 指定したスキン名を適用する
	 * @param player プレイヤー
	 * @param item 武器アイテム
	 * @param skinName スキン名
	 * @param skinType スキンタイプ
	 * @return ItemStack スキン適用後のアイテム
	 */
	public static ItemStack applySkin(Player player, ItemStack item, String skinName, Skin.SkinType skinType) {
		String weaponTitle = API.getCSUtility().getWeaponTitle(item);
		if(weaponTitle == null) return null;

		// 指定された名前のスキンを取得
		Skin skin = API.buildSkin(weaponTitle, weaponTitle + "_" + skinName, skinType);

		// デフォルトスキンを初期設定として適用
		if (skin == null && API.getS(weaponTitle + ".Skin.Default_Skin") != null) {
			String defaultSkin = API.getS(weaponTitle + ".Skin.Default_Skin");
			skin = API.buildSkin(weaponTitle, weaponTitle + "_" + defaultSkin, skinType);
		}

		// 適用するスキンが存在しない場合は処理終了
		if (skin == null) return item;

		// スキン変更イベントを発火
		WeaponSkinEvent event = new WeaponSkinEvent(player, weaponTitle, skin, null);
		Bukkit.getServer().getPluginManager().callEvent(event);
		if (event.isCancelled()) return item;

		// スキン適用
		skin.applySkinToItemStack(item);
		if (player != null) {
			CSPPlayer cspPlayer = API.getCSPPlayer(player);
			if (skin.getDurability() != 0) {
				cspPlayer.setLethalDurability((short) skin.getDurability());
			}

			if (skin.getCustomModelData() != 0) {
				cspPlayer.setLethalCustomModelData(skin.getCustomModelData());
			}
		}
		return item;
	}
}
