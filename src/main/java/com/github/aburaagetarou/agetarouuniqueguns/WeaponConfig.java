package com.github.aburaagetarou.agetarouuniqueguns;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class WeaponConfig {

	// 武器設定
	private static final Map<String, ConfigurationSection> weaponConfigs = new HashMap<>();

	/**
	 * 武器設定の初期化
	 */
	public static void clearWeaponConfig() {
		weaponConfigs.clear();
	}

	/**
	 * 武器設定の取得
	 * @param weapon 武器名
	 * @return YamlConfiguration 武器設定
	 */
	public static ConfigurationSection getWeaponConfig(String weapon) {
		return weaponConfigs.get(weapon);
	}

	/**
	 * 武器設定の読み込み
	 * @param dir ディレクトリ
	 */
	public static void loadWeaponConfig(File dir) {

		// ディレクトリが存在しない場合は処理しない
		if (!dir.exists()) return;

		// ディレクトリ内のファイルを取得
		File[] files = dir.listFiles();
		if(files == null) return;

		// 設定ファイルの読み込み
		for (File file : files) {

			// ディレクトリの場合は再帰呼び出し
			if(file.isDirectory()) {
				loadWeaponConfig(file);
				continue;
			}

			// 設定の内容を保存
			YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
			for(String key : config.getKeys(false)) {
				weaponConfigs.put(key, config.getConfigurationSection(key));
			}
		}
	}
}
