package util;


import java.io.IOException;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class LOGGER { // Нужно следить чтобы этот класс загрузился до всех остальных
    static final Logger logger = Logger.getGlobal();
    static FileHandler logHandler=null;
    static {
    	try {
    		System.setProperty("java.util.logging.SimpleFormatter.format", "[%1$tF %1$tT] %4$s: %5$s %n");
    		logger.setUseParentHandlers(false); // Отключит дублирование в консоль средствами Logger
    		
    		logHandler = new FileHandler("longcoin-java.log", true); // append
    		
    		logHandler.setFormatter(new SimpleFormatter());
    		logger.addHandler(logHandler);
    	}
    	catch (IOException e) {
    		e.printStackTrace();
    		if (logHandler!=null) logHandler.close();
    	}
    }
    public static void log(final Level level, final String msg){
    	if (level==Level.SEVERE) System.out.println(msg); // Критические ошибки дублируются в консоль
    	
    	logger.log(level, msg); 
    	
    	//if(logHandler!=null) logHandler.flush();
    }
    public static void info(String msg){log(Level.INFO,msg);}
    public static void error(String msg){log(Level.SEVERE,msg);} // Дублирует в консоль
    public static void warning(String msg){log(Level.WARNING,msg);}
    public static void console(String msg){System.out.println("INFO: "+msg);}
/*    
    The levels in descending order are:
        SEVERE (highest value)
        WARNING
        INFO
        CONFIG
        FINE
        FINER
        FINEST (lowest value) 
    In addition there is a level OFF that can be used to turn off logging, and a level ALL that can be used to enable logging of all messages.
*/ 
	@Override public void finalize() {
		//if(logHandler!=null) logHandler.close();
	}
}
