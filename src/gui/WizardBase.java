package gui;

import static application.Main.*;

import java.util.ArrayList;
import java.util.List;

import javafx.geometry.Bounds;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import util.Css;

public abstract class WizardBase extends Stage {
	
	static final double OFFSET = Css.getTextBounds("┃⟺",18).getHeight();

	private Scene scene=new Scene(new Region());
	
	volatile JobInfo jobInfo;
	
	private List<Pane> pages=new ArrayList<>();  private int index = -1;
	public void add(Pane page) {pages.add(page);}
	public void addAll(Pane ...pages) {for(Pane p: pages) add(p);}
	
	public boolean next() {
		if( ++index < pages.size() ) { 
			
			Pane page=pages.get(index); page.getStyleClass().add("wizard-page"); page.getChildren().add(jobInfo=new JobInfo());
			
			initSize();
			
			scene.setRoot(page); //this.requestFocus();
			
			return true;
		}
		else {
			this.close();
			
			return false;
		}
	}
	
	private void initSize() {this.setHeight(SCREEN_HEIGHT*H1*H2*0.875d); this.setWidth(SCREEN_WIDTH*W1*W2*W3);}
	
	public WizardBase() { super();
		this.initStyle(StageStyle.UTILITY); this.setResizable(true); initSize();
		
		
		this.setScene(scene);

		this.setOnShown((ev)->{	
			if(!pages.isEmpty()) { index=0; 
			
				Pane page=pages.get(index); page.getStyleClass().add("wizard-page"); page.getChildren().add(jobInfo=new JobInfo());
				
				initSize();
				
				scene.setRoot(page); //this.requestFocus();
			}
		});
		
		this.addEventHandler(KeyEvent.KEY_PRESSED, (ev)->{
			if(ev.getCode()==KeyCode.ESCAPE) {
				this.close();
			}
		});
		this.setOnCloseRequest((ev)->{
		});			
	
	
	}

	private Parent owner;
	public WizardBase(Parent owner) { this(); setOwner(owner);	}
	protected void setOwner(Parent owner) { this.owner=owner;
		if(owner!=null) {
			
			this.initOwner(owner.getScene().getWindow()); this.initModality(Modality.WINDOW_MODAL);  // Если родитель есть, то MODAL-mode
			
			scene.getStylesheets().setAll(owner.getScene().getStylesheets()); // Наследование всех стилей от родителя
			
			Bounds bounds=owner.localToScreen(owner.getBoundsInLocal());
			
			//this.setX(bounds.getMinX()+OFFSET);
			//this.setY(bounds.getMinY()+OFFSET);
			this.setX(bounds.getCenterX()-this.getWidth()/2);
			this.setY(bounds.getCenterY()-this.getHeight()/2);
		}
	}
	
	
	
	@Override public void hide() { // Вызывается всегда вне зависимости от способа закрытия
		super.hide(); if(owner!=null) owner.requestFocus();
	}

	
}
