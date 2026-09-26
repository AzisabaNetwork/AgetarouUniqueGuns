package com.github.aburaagetarou.agetarouuniqueguns;

import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import javax.annotation.Nullable;
import java.io.File;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

public class WeaponConfig {

	// 武器設定
	private static final Map<String, ConfigurationSection> weaponConfigs = new HashMap<>();
	// 効果音設定
	private static final Map<String, List<SoundSetting>> soundSettings = new HashMap<>();

	// 効果音リスト
	private static final Map<String, Sound> sounds = new HashMap<>();
	static {
		Arrays.stream(Sound.values()).forEach(sound -> sounds.put(sound.name().replace(".", "_"), sound));
	}

	/**
	 * 武器設定の初期化
	 */
	public static void clearWeaponConfig() {
		weaponConfigs.clear();
		soundSettings.clear();
	}

	/**
	 * 武器設定の取得
	 * @param weapon 武器名
	 * @return YamlConfiguration 武器設定
	 */
	public static ConfigurationSection getWeaponConfig(String weapon) {
		return weaponConfigs.get(weapon);
	}

	public static Map<String, ConfigurationSection> getWeaponConfigs() {
		return Collections.unmodifiableMap(new HashMap<>(weaponConfigs));
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

	/**
	 * 効果音設定を取得
	 * @param key キー名
	 * @return 効果音設定リスト
	 */
	@Nullable
	public static List<SoundSetting> getSoundSetting(String weapon, String key) {
		ConfigurationSection weaponConfig = getWeaponConfig(weapon);
		if(!soundSettings.containsKey(key)) {
			if(!weaponConfig.isString(key)) {
				return null;
			}
			List<SoundSetting> settings = Arrays.stream(weaponConfig.getString(key).split(","))
					.map(WeaponConfig::parseSoundSetting)
					.filter(Objects::nonNull)
					.collect(Collectors.toList());
			soundSettings.put(key, settings);
		}
		return soundSettings.get(key);
	}

	/**
	 * 効果音設定の分解
	 * @param key 設定キー("SOUND_NAME-VOLUME-PITCH(-DELAY)")
	 */
	@Nullable
	public static SoundSetting parseSoundSetting(String key) {
		// 設定の分解("SOUND_NAME-VOLUME-PITCH(-DELAY)")
		String[] soundSetting = key.split("-");
		if(soundSetting.length < 3) {
			return null;
		}

		// 効果音の取得
		Sound sound = sounds.get(soundSetting[0].toUpperCase());
		if(sound == null) return null;
		BigDecimal volume = new BigDecimal(soundSetting[1]);
		BigDecimal pitch = new BigDecimal(soundSetting[2]);
		BigDecimal delay = new BigDecimal(0);
		if(soundSetting.length >= 4) {
			delay = new BigDecimal(soundSetting[3]);
		}
		return new SoundSetting(sound, volume.floatValue(), pitch.floatValue(), delay.intValue());
	}

	/**
	 * 効果音設定
	 */
	public static class SoundSetting {
		private final Sound sound;
		private final float volume;
		private final float pitch;
		private final int delay;

		/**
		 * コンストラクタ
		 * @param sound 効果音
		 * @param volume 音量
		 * @param pitch ピッチ
		 */
		public SoundSetting(Sound sound, float volume, float pitch, int delay) {
			this.sound = sound;
			this.volume = volume;
			this.pitch = pitch;
			this.delay = delay;
		}

		/**
		 * 効果音を取得
		 * @return 効果音
		 */
		public Sound getSound() {
			return sound;
		}

		/**
		 * 音量を取得
		 * @return 音量
		 */
		public float getVolume() {
			return volume;
		}

		/**
		 * ピッチを取得
		 * @return ピッチ
		 */
		public float getPitch() {
			return pitch;
		}

		/**
		 * 遅延時間を取得
		 * @return 遅延時間
		 */
		public int getDelay() {
			return delay;
		}
	}
}
