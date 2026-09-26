package com.github.aburaagetarou.agetarouuniqueguns.utils;

import com.github.aburaagetarou.agetarouuniqueguns.AgetarouUniqueGuns;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.function.Consumer;

/**
 * 拡張タスククラス
 * 従来のTimer要素に加え、回数指定や容易な中断が可能
 * @author AburaAgeTarou
 */
public abstract class EnhancedTask implements Runnable {

	// スケジュールの実行に必要なプラグインインスタンス
	protected JavaPlugin plugin = AgetarouUniqueGuns.getInstance();

	// スケジュールに割り振られたタスクID
	protected BukkitTask bukkitTask;

	// 回数
	protected long times = 0;

	// 遅延
	protected long delay = 0L;

	// 残り時間
	protected int count = -1;

	// キャンセルフラグ
	protected boolean cancel = false;

	// 停止フラグ
	protected boolean stop = false;

	// 処理
	protected Consumer<EnhancedTask> loopTask;
	protected Runnable beforeTask;
	protected Runnable loopEndTask;
	protected EnhancedTask afterTask;
	protected EnhancedTask root;

	@Override
	public abstract void run();

	public abstract void start();

	public void cancel(){
		this.cancel = true;
	}

	public void stop(){
		this.cancel = true;
		this.stop = true;
	}

	public EnhancedTask before(Runnable beforeTask){
		this.beforeTask = beforeTask;
		return this;
	}

	public EnhancedTask loop(long times, long delay, Consumer<EnhancedTask> loopTask){
		this.times = times;
		this.delay = delay;
		this.loopTask = loopTask;
		return this;
	}

	public EnhancedTask whileTask(long delay, Consumer<EnhancedTask> loopTask){
		this.times = Long.MAX_VALUE;
		this.delay = delay;
		this.loopTask = loopTask;
		return this;
	}

	public EnhancedTask waitTick(long delay){
		this.times = 1L;
		this.delay = delay;
		this.loopTask = (task) -> {};
		return this;
	}

	public EnhancedTask loopEnd(Runnable loopEndTask){
		this.loopEndTask = loopEndTask;
		return this;
	}

	public EnhancedTask single(long delay, Consumer<EnhancedTask> loopTask){
		this.times = 1L;
		this.delay = delay;
		this.loopTask = loopTask;
		return this;
	}

	public EnhancedTask after(EnhancedTask afterTask){
		this.afterTask = afterTask;
		if(this.root == null) this.root = this;
		this.afterTask.root = this.root;
		return this.afterTask;
	}

	public EnhancedTask root(){
		if(root == null) return this;
		return root;
	}

	public void stopLoop(){
		if(bukkitTask != null) bukkitTask.cancel();
	}

	public int getCount(){ return count; }

	public void setCount(int count){ this.count = count; }

	public boolean isCancel() { return cancel; }
}
