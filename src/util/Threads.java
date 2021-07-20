package util;

public final class Threads {

	public static void runNow(Runnable runnable) {
		Thread th=new Thread(runnable); th.setDaemon(true); th.start();
	}
	
}
