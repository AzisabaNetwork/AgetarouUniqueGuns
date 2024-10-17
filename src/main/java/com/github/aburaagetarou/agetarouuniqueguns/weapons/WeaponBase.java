package com.github.aburaagetarou.agetarouuniqueguns.weapons;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;

/**
 * 武器のベースクラス
 * @author AburaAgeTarou
 */
public abstract class WeaponBase implements Listener {

	/**
	 * プレイヤーのステータスを更新する
	 * @param player プレイヤー
	 */
	public abstract void updateStats(Player player);

	/**
	 * メインハンドアイテム持ち替え時にステータス更新
	 * @param event 持ち替えイベント
	 */
	@EventHandler
	public void onPlayerSwapHandItems(PlayerItemHeldEvent event) {
		updateStats(event.getPlayer());
	}

	/**
	 * プレイヤー死亡時にステータス更新
	 * @param event 死亡イベント
	 */
	@EventHandler
	public void onPlayerDeath(PlayerDeathEvent event) {
		updateStats(event.getEntity());
	}
}
