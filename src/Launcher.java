
import application.Splash;
import javafx.application.Application;

public class Launcher {
    
    public static void main(String[] args) {
    	//application.Main.main(args);
    	
    	System.setProperty("javafx.preloader", Splash.class.getCanonicalName()); Application.launch(application.Main.class,args);
    }
}
