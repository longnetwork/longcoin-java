package coredrv;

import util.LOGGER;

import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

// Постараться Все платформо-зависимые вещи только здесь делать и больше нигде

final public class DaemonSupervisor {
	
	public enum OSType { UNKNOWN, WINDOWS, MAC, UNIX, SOLARIS };
	
	final OSType OS;  

	final Path WORK_DIR = Paths.get("").toAbsolutePath(); // Должно дать рабочую директорию к месту запуска
	final Path HOME_DIR = Paths.get(System.getProperty("user.home")).toAbsolutePath();
	
	final String CONFIG_NAME="longcoin.conf"; 
		final String DAEMON_NAME; // Зависит от OS
		final String DEFAULT_DIR; // Зависит от OS
	
	Path daemonPath=null, configPath=null; Properties daemonConfig=new Properties(); Process daemonProcess=null; 
	ProcessBuilder processBilder; List<String> basicCmd=new ArrayList<>();
	
	public DaemonSupervisor() throws Exception {
		String OSName = System.getProperty("os.name").toLowerCase();
		if (OSName.indexOf("win") >= 0) OS=OSType.WINDOWS; 
//		else if (OSName.indexOf("mac") >= 0) OS=OSType.MAC;
		else if (OSName.indexOf("nix") >= 0 || OSName.indexOf("nux") >= 0 || OSName.indexOf("aix") > 0 ) OS=OSType.UNIX;
//		else if (OSName.indexOf("sunos") >= 0) OS=OSType.SOLARIS;
		else {LOGGER.error("Unknow OS"); throw new RuntimeException("Unknow OS");}
		switch (OS) {
			case WINDOWS: DAEMON_NAME="longcoind.exe"; DEFAULT_DIR="Longcoin"; break;
			case UNIX: DAEMON_NAME="longcoind"; DEFAULT_DIR=".longcoin"; break;
			default: DAEMON_NAME="longcoind.bin"; DEFAULT_DIR="Longcoin"; break; // FIXME
		}
		LOGGER.info("HomeDir="+HOME_DIR); LOGGER.info("workDir="+WORK_DIR);
		
		// Ищем демон и конфиг для запуска в текущей директории и на один уровень ниже текущей (можно создавать символьные ссылки)
		Files.walkFileTree(WORK_DIR, EnumSet.of(FileVisitOption.FOLLOW_LINKS), 2, new SimpleFileVisitor<Path>() { 
																			 // maxDepth==2 - для листинга содержимого поддиректорий
			@Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
				//LOGGER.console(file.toString()); // TODO debug
				if(daemonPath==null && file.endsWith(DAEMON_NAME)) daemonPath=file;
				if(configPath==null && file.endsWith(CONFIG_NAME)) configPath=file;
				if(daemonPath!=null && configPath!=null) return FileVisitResult.TERMINATE;
				else                            		 return FileVisitResult.CONTINUE;
			}
		});
		if (daemonPath==null) { 
			LOGGER.warning(DAEMON_NAME+" not found in current dir");
			daemonPath=Paths.get(DAEMON_NAME); // Пытаемся запустить из системных путей но в текущей директории
			LOGGER.warning("└ try "+daemonPath);
			
		}
		if (configPath==null) { 
			LOGGER.warning(CONFIG_NAME+" not found in current dir");
			configPath=HOME_DIR.resolve(DEFAULT_DIR).resolve(CONFIG_NAME); // Последний шанс найти конфиг в системной директории юзера
			LOGGER.warning("└ try "+configPath);
		}
		
		if(configPath!=null) {
			try {
				daemonConfig.load(Files.newInputStream(configPath)); // Грузим конфиг
			}
			catch(IOException ignore) {}
		}
		// daemonConfig.forEach((k,v)->LOGGER.console(k+"="+v)); // TODO debug
		
		// Забиваем важные для использование из вне но отсутствующие параметры значениями по умолчанию, которые будут в этом случае в демоне
		basicCmd.add(""); // Резервируем место под название бинарника для запуска
		// Праметры RPC либо из конфига либо по-умолчанию
		if(daemonConfig.getProperty("rpcport")==null) {daemonConfig.setProperty("rpcport","8878"); basicCmd.add("-rpcport=8878");}
		if(daemonConfig.getProperty("rpcuser")==null) {daemonConfig.setProperty("rpcuser","user"); basicCmd.add("-rpcuser=user");}
		if(daemonConfig.getProperty("rpcpassword")==null) {daemonConfig.setProperty("rpcpassword","password"); basicCmd.add("-rpcpassword=password");}
		
		if (daemonPath!=null) {  basicCmd.set(0,daemonPath.toString()); // бинарник
			// Принудительные параметры запуска на тот случай если конфиг вообще не нашли а также предотвращаем демонизацию 
			basicCmd.add("-daemon=0"); basicCmd.add("-discover"); /*basicCmd.add("-listen");*/ basicCmd.add("-server"); basicCmd.add("-upnp"); basicCmd.add("-peerbloomfilters");
			//basicCmd.add("-txindex"); basicCmd.add("-addressindex"); basicCmd.add("-timestampindex"); basicCmd.add("-spentindex");
			basicCmd.add("-rpcthreads=2"); basicCmd.add("-rpcworkqueue=1024");
				
			// Если datadir в конфиге нет, то демон данные располагает в системной директории, иначе - он требует чтобы каталог уже был
			if(daemonConfig.getProperty("datadir")!=null && !Files.exists(WORK_DIR.resolve(daemonConfig.getProperty("datadir"))))
				Files.createDirectory(WORK_DIR.resolve(daemonConfig.getProperty("datadir")));
			processBilder = new ProcessBuilder().directory(WORK_DIR.toFile()).inheritIO(); 
			// XXX inheritIO покажет в консоле чего хочет демон при мульти-инстантном запуске ( другой порт rpc и -listen=0)
		}
	}
	
	
	
	public void start(String ...additionOptions) throws Exception {
		if(this.isAlive()) {
			LOGGER.error(DAEMON_NAME+" is already running"); 
			throw new RuntimeException(DAEMON_NAME+" is already running");
		}
		// XXX Случай когда подцепились по портам уже запущеного процесса (daemonProcess!=null но isAlive дает false) означает только, что снова старт зафейлится
		
		try {
			List<String> cmd=new ArrayList<>(basicCmd); for(String option: additionOptions) cmd.add(option);
			
			processBilder.command(cmd);
			
			daemonProcess= processBilder.start(); // Старту-у-у-е-ем!!!! 
			// Процесс стартует и не отцепляется от родителя если -daemon=0, тогда и только тогда он контролируем через daemonProcess !
			// Демон молча не запускается с залоченой директорией data другим демоном или с занятыми портами 
			// (доступно подцепление по RPC в обоих случаях к уже запущенному демону по параметрам в локальном конфиге)
		}
		catch(IOException e) { // Когда запускаем демон, но его нет в системе (только тогда daemonProcess=null)
			LOGGER.error(e.toString()); //throw e;
		} 
		
	}
	
	
	public void shutdown() throws Exception { // Не асинхронная
		if(this.isAlive()) {
			LOGGER.info(DAEMON_NAME+" shutdown ..."); 
			LOGGER.console(DAEMON_NAME+" shutdown ..."); // TODO debug
			this.destroy();
			if(!this.waitFor(120*1000)) {
				LOGGER.warning(DAEMON_NAME+" does not stop for a long time - forced termination ...");
				LOGGER.console(DAEMON_NAME+" does not stop for a long time - forced termination ..."); // TODO debug
				this.destroyForcibly();
				if(!this.waitFor(120*1000)) {
					LOGGER.error(DAEMON_NAME+" did not termination ..."); 
					throw new RuntimeException(DAEMON_NAME+" did not termination ...");
				}
			}
			
			daemonProcess=null; // Если мы управляем процессом то мы можем и дать null
		}
	}
	
	
	public boolean isAlive() { return daemonProcess==null ? false : daemonProcess.isAlive(); }
	public void destroy() { if(daemonProcess!=null) daemonProcess.destroy(); }
	public void destroyForcibly() { if(daemonProcess!=null) daemonProcess.destroyForcibly(); }
	public int waitFor() throws InterruptedException { if(daemonProcess!=null) return daemonProcess.waitFor(); else return 0; } // XXX 0 - код успешного завершения процесса
	public boolean waitFor(long timeout) throws InterruptedException { if(daemonProcess!=null) return daemonProcess.waitFor(timeout,TimeUnit.MILLISECONDS); else return true; }
	
	public int exitValue() {return daemonProcess==null ? 0 : daemonProcess.exitValue();} // XXX 0 - код успешного завершения процесса
	
	public int getPort() {return Integer.parseInt(daemonConfig.getProperty("rpcport"));}
	public String getUser() {return daemonConfig.getProperty("rpcuser");}
	public String getPassword() {return daemonConfig.getProperty("rpcpassword");}
	public String getDaemonName() {return DAEMON_NAME;}
	public OSType getOsType() {return OS;}
}
