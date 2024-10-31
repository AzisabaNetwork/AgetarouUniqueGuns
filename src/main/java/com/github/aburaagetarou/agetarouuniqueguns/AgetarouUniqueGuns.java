package com.github.aburaagetarou.agetarouuniqueguns;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.MessageKeys;
import co.aikar.commands.MessageType;
import co.aikar.commands.PaperCommandManager;
import com.github.aburaagetarou.agetarouuniqueguns.listeners.CSListeners;
import com.github.aburaagetarou.agetarouuniqueguns.weapons.*;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * AburaAgeTarou製ユニーク武器プラグイン
 * @author AburaAgeTarou
 */
public final class AgetarouUniqueGuns extends JavaPlugin {

	// プラグインインスタンス
	private static AgetarouUniqueGuns instance;

	// コマンドマネージャ
	private static PaperCommandManager manager;

	// コマンドリスト
	private static final List<BaseCommand> commands = new ArrayList<>();

	@Override
	public void onEnable() {
		instance = this;

		// 設定読み込み
		WeaponConfig.clearWeaponConfig();
		loadDefaultWeaponConfig();

		// コマンド登録
		manager = new PaperCommandManager(this);

		// brigadierを有効化しろと言われたので有効化
		manager.enableUnstableAPI("brigadier");

		// helpを有効化
		manager.enableUnstableAPI("help");

		// コマンド登録
		manager.registerCommand(new AUGCommand().setExceptionHandler((command, registeredCommand, sender, args, t) -> {
			sender.sendMessage(MessageType.ERROR, MessageKeys.ERROR_GENERIC_LOGGED);
			return true;
		}), true);

		// リスナー登録
		Bukkit.getPluginManager().registerEvents(new CSListeners(), this);
		Bukkit.getPluginManager().registerEvents(new HurtfulSpine(), this);
		Bukkit.getPluginManager().registerEvents(new HurtlessSpine(), this);
		Bukkit.getPluginManager().registerEvents(new EPT_Jager(), this);
		Bukkit.getPluginManager().registerEvents(new Sorcerers_Rod(), this);
		Grimoire.registerEvents();

		// タイマー開始
		EPT_Jager.initTimer();
		Sorcerers_Rod.initManaRefillTask();

		getLogger().info("AgetarouUniqueGunsを有効化しました。");
	}

	@Override
	public void onDisable() {

		// タイマー停止
		EPT_Jager.stopTimer();
		Sorcerers_Rod.stopManaRefillTask();

		// コマンド登録解除
		commands.forEach(manager::unregisterCommand);
		manager.unregisterCommands();

		getLogger().info("AgetarouUniqueGunsを無効化しました。");
	}

	/**
	 * プラグインインスタンスを取得
	 * @return AgetarouUniqueGuns インスタンス
	 */
	public static AgetarouUniqueGuns getInstance() {
		return instance;
	}

	/**
	 * デフォルト武器設定の読み込み
	 */
	public void loadDefaultWeaponConfig() {
		File weaponsDir = new File(getDataFolder(), "weapons");
		if(!weaponsDir.exists()) weaponsDir.mkdirs();
		WeaponConfig.loadWeaponConfig(weaponsDir);
	}

	/**
	 * コマンド追加
	 * @param command コマンド
	 */
	public static void addCommand(BaseCommand command) {
		commands.add(command);
	}
}
