package application;

import static application.Main.*;

import javafx.animation.FadeTransition;
import javafx.application.Preloader;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.image.ImageView;
import javafx.scene.text.Text;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

public class Splash extends Preloader {

	public static final double SPLACH_HEIGTH = Screen.getPrimary().getBounds().getHeight()*0.375;
	public static final double SPLASH_WIDTH = Screen.getPrimary().getBounds().getWidth()*0.375;
	
	Stage stage; FadeTransition fadeIn, fadeOut;
	
	public Splash() { super();
	
		//LOGGER.console(System.getProperty("javafx.animation.fullspeed"));
		//LOGGER.console(System.getProperty("javafx.animation.framerate"));
		//LOGGER.console(System.getProperty("javafx.animation.pulse"));
	}

	@Override public void start(Stage primaryStage) throws Exception {
		
		primaryStage.setTitle(VERSION);

		stage=primaryStage;
		
		stage.initStyle(StageStyle.UNDECORATED); stage.setResizable(false); stage.setAlwaysOnTop(true);
		
		Group root=new Group(); root.getStyleClass().add("splash");
			ImageView logo = new ImageView("res/long-dragon.jpg"); 
				logo.setPreserveRatio(true); logo.setSmooth(true);
				logo.setFitHeight(SPLACH_HEIGTH); //logo.setFitWidth(SPLASH_WIDTH);
				
			Text text=new Text(VERSION); text.setLayoutY(SPLACH_HEIGTH); // .splash Text {}
			
		root.getChildren().addAll(logo,text);
		
		
		
        fadeIn = new FadeTransition(Duration.seconds(3), root);
	        fadeIn.setFromValue(0.3);
	        fadeIn.setToValue(1);
	        fadeIn.setCycleCount(1);
 
        fadeOut = new FadeTransition(Duration.seconds(3), root);
	        fadeOut.setFromValue(1);
	        fadeOut.setToValue(0.3);
	        fadeOut.setCycleCount(1);
 
        fadeIn.setOnFinished((e) -> {
            fadeOut.play();
        });
 
        fadeOut.setOnFinished((e) -> {
        	fadeIn.play();
        });	
		
		
        Scene scene=new Scene(root);
        scene.getStylesheets().add(ClassLoader.getSystemResource("res/application.css").toExternalForm());
        
        primaryStage.setScene(scene);
        
        primaryStage.setOnShowing((ev)->{
        	fadeOut.play();
        });
        
        
        primaryStage.show();
        
        
	}
	
	@Override public void handleApplicationNotification(PreloaderNotification pn) {
		if (pn instanceof StateChangeNotification) { //hide after get any state update from application
			
			fadeIn.stop(); fadeOut.stop();
			
            stage.close();
		}
	}
	
}
