package com.github.aburaagetarou.agetarouuniqueguns.utils;

import org.bukkit.Bukkit;

/**
 * BukkitのSchedulerで処理を同期実行する拡張タスククラス
 * @author AburaAgeTarou
 */
public class SyncEnhancedTask extends EnhancedTask {

	@Override
	public void run() {

		// 停止
		if(stop) {
			stopLoop();
			return;
		}

		// 開始
		if(count++ < 0) {
			count = 0;
			if(loopTask != null){
				if(times == 1) bukkitTask = Bukkit.getScheduler().runTaskTimer(plugin, this, delay, 0L);
				else           bukkitTask = Bukkit.getScheduler().runTaskTimer(plugin, this, 0L, delay);
			}
			return;
		}

		// ループ
		loopTask.accept(this);

		// 終了
		if(count > times || cancel) {
			if(loopEndTask != null) Bukkit.getScheduler().runTask(plugin, loopEndTask);
			if(afterTask != null) {
				afterTask.start();
			}
			stopLoop();
		}
	}

	@Override
	public void start() {

		if(beforeTask != null) beforeTask.run();

		// 遅延がない場合
		if(delay <= 0L){
			for(int i = 0; i < times; i++){
				Bukkit.getScheduler().runTask(plugin, () -> loopTask.accept(this));
			}
			if(loopEndTask != null) Bukkit.getScheduler().runTask(plugin, loopEndTask);
			if(afterTask != null) afterTask.start();
		}
		else {
			Bukkit.getScheduler().runTask(plugin, this);
		}
	}
}
