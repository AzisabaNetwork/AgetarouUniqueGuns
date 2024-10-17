package com.github.aburaagetarou.agetarouuniqueguns.utils;

import com.github.aburaagetarou.agetarouuniqueguns.WeaponConfig;
import org.bukkit.configuration.ConfigurationSection;

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
}
