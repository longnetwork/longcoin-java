package application;


import static application.Main.VERSION;

import gui.JobInfo;

import javafx.scene.Scene;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

public class Fading extends Stage {

	public static final double FADING_HEIGTH = Screen.getPrimary().getBounds().getHeight()*0.375;
	public static final double FADING_WIDTH = Screen.getPrimary().getBounds().getWidth()*0.375;
	
	
	private Scene scene;
	Pane root;				// Для jobInfo нужно не менее Pane
		ImageView logo;
		JobInfo jobInfo;
	
	public Fading() { super();
	
		this.setTitle(VERSION);
	
		this.initStyle(StageStyle.UNDECORATED); this.setResizable(false); this.setAlwaysOnTop(true);
		
		root=new Pane(); root.getStyleClass().add("fading");
			ImageView logo = new ImageView("res/long-dragon.jpg"); // .fading .image-view {}
			logo.setPreserveRatio(true); logo.setSmooth(true);
			logo.setFitHeight(FADING_HEIGTH); //logo.setFitWidth(FADING_WIDTH);
		
			jobInfo=new JobInfo(true);
	
			root.getChildren().addAll(logo,jobInfo);
			
			scene=new Scene(root);
		    scene.getStylesheets().add(ClassLoader.getSystemResource("res/application.css").toExternalForm());
		     
		    this.setScene(scene);
	        
	        this.show(); // Зам себя показывает и идет дальше        
	}
	public Fading(String info) { this();
		jobInfo.progress(info);
	}
	
	

}
