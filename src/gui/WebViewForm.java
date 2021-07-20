package gui;

import static application.Main.*;
import javafx.geometry.Bounds;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import util.Css;

public class WebViewForm extends Stage {

	static final double OFFSET = Css.getTextBounds("┃⟺",18).getHeight();
	
	private Scene scene;
		private WebViewWrp webview;
	
	private void initSize() {this.setHeight(SCREEN_HEIGHT*H1*H2); this.setWidth(SCREEN_WIDTH*W1*W2);}	
		
	public WebViewForm() { super();
		this.initStyle(StageStyle.UTILITY); this.setResizable(true); initSize();

		webview = new WebViewWrp(); // .web-view {}
					
		scene = new Scene((Parent)webview.getNode());
		
		this.setScene(scene);
		
		this.setOnShown((ev)->{ initSize(); this.requestFocus(); }); 
		
		this.addEventHandler(KeyEvent.KEY_PRESSED, (ev)->{
			if(ev.getCode()==KeyCode.ESCAPE) {
				this.close();
			}
		});
		this.setOnCloseRequest((ev)->{
		});
	}
	
	private Parent owner;
	WebViewForm(Parent owner) { this(); this.owner=owner;
		if(owner!=null) {
			
			this.initOwner(owner.getScene().getWindow()); this.initModality(Modality.WINDOW_MODAL);  // Если родитель есть, то MODAL-mode
			scene.getStylesheets().setAll(owner.getScene().getStylesheets()); // Наследование всех стилей от родителя
			
			Bounds bounds=owner.localToScreen(owner.getBoundsInLocal());
			
			this.setX(bounds.getMinX()+OFFSET);
			this.setY(bounds.getMinY()+OFFSET);
		}
		
	}
	
	public void setContent(String content) {
		webview.getEngine().loadContent(content);
	}

	@Override public void hide() { // Вызывается всегда вне зависимости от способа закрытия
		
		this.setContent(null); // Чтобы всякая предзагрузка прекратилась
		
		super.hide(); if(owner!=null) owner.requestFocus();
	}
	////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	
	// show(); showAndWait(); - Наследуются
	
}
