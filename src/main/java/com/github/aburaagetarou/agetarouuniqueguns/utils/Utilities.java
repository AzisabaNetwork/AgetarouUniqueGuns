package com.github.aburaagetarou.agetarouuniqueguns.utils;

import com.github.aburaagetarou.agetarouuniqueguns.AgetarouUniqueGuns;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * ユーティリティクラス
 * @author AburaAgeTarou
 */
public class Utilities {

	/**
	 * 音を繰り返し再生
	 * @param player プレイヤー
	 * @param sound サウンド
	 * @param volume 音量
	 * @param pitch ピッチ
	 * @param delay 遅延
	 * @param repeat 繰り返し
	 */
	public static void playSound(Player player, Sound sound, float volume, float pitch, long delay, long period, long repeat) {
		BukkitRunnable task = new BukkitRunnable() {

			int count = 0;

			@Override
			public void run() {
				player.playSound(player.getLocation(), sound, volume, pitch);
				if(++count >= repeat) cancel();
			}
		};

		task.runTaskTimer(AgetarouUniqueGuns.getInstance(), delay, period);
	}

	/**
	 * メッセージを&着色して送信
	 * @param player プレイヤー
	 * @param message メッセージ
	 */
	public static void sendColoredMessage(Player player, String message) {
		player.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(message));
	}
}
