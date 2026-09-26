package com.github.aburaagetarou.agetarouuniqueguns.utils;

import com.github.aburaagetarou.agetarouuniqueguns.AgetarouUniqueGuns;
import com.github.aburaagetarou.agetarouuniqueguns.WeaponConfig;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import javax.annotation.Nullable;
import java.util.*;

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
	 * 効果音を再生
	 * @param sounds 効果音
	 * @param players 対象プレイヤー(Nullの場合は全員)
	 */
	public static EnhancedTask playSounds(Collection<WeaponConfig.SoundSetting> sounds, @Nullable Player ...players) {
		if(sounds == null || sounds.isEmpty()) return null;

		// 対象プレイヤー
		List<Player> playerList;
		if(players != null && players.length > 0) {
			playerList = new ArrayList<>(Arrays.asList(players));
		}
		else {
			playerList = new ArrayList<>(Bukkit.getOnlinePlayers());
		}

		// 遅延時間の昇順でソート
		List<WeaponConfig.SoundSetting> sorted = new ArrayList<>(sounds);
		sorted.sort(Comparator.comparingInt(WeaponConfig.SoundSetting::getDelay));

		// 遅延時間を考慮して再生
		int maxDelay = sorted.stream().max(Comparator.comparingInt(WeaponConfig.SoundSetting::getDelay)).get().getDelay();
		SyncEnhancedTask syncedTask = new SyncEnhancedTask();
		syncedTask.loop(maxDelay, 1L, (task) -> {
			int count = (syncedTask.getCount() - 1);
			for(WeaponConfig.SoundSetting sound : sorted) {
				if(count == sound.getDelay()) {
					playerList.forEach(player -> player.playSound(player.getLocation(), sound.getSound(), SoundCategory.MASTER, sound.getVolume(), sound.getPitch()));
				}
			}
		}).start();

		return syncedTask;
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
