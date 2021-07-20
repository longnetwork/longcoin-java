package gui;

import static application.Main.*;

import javafx.geometry.Bounds;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import util.Css;
import util.L10n;

public class PasswordForm extends Stage {

	static final double OFFSET = Css.getTextBounds("┃⟺",18).getHeight();

	private Scene scene;
		VBox vbox;
			Label label;
			PasswordField password;
			Button ok;
	
		private String result=null;
		private boolean result_ok=false;
	
	public PasswordForm() { super();
		this.initStyle(StageStyle.UTILITY); this.setResizable(true); this.setWidth(SCREEN_WIDTH*W1*W2*W3); this.setAlwaysOnTop(true); // XXX setAlwaysOnTop
		
		label=new Label(L10n.t("walletEncrypted"));
		password=new PasswordField();	
		ok= new Button(L10n.t("ОК")); ok.setOnAction((ev)->{ result_ok=true; this.close(); });
			
		vbox= new VBox(label,password,ok); vbox.getStyleClass().add("vbox-form");
		
		scene = new Scene(vbox);
		
		this.setScene(scene);
		
		this.setOnShown((ev)->{	
			this.setHeight(vbox.getHeight()+
					       vbox.getPadding().getBottom()+vbox.getPadding().getTop());
			
			this.setWidth(SCREEN_WIDTH*W1*W2*W3);
			
			//ok.requestFocus();
			password.requestFocus();
			
			this.requestFocus();
		});
		
		this.addEventHandler(KeyEvent.KEY_PRESSED, (ev)->{
			if(ev.getCode()==KeyCode.ESCAPE) {
				this.close();
			}
			if(ev.getCode()==KeyCode.ENTER) { result_ok=true; this.close(); }
		});
		this.setOnCloseRequest((ev)->{
		});	
	}

	private Parent owner;
	public PasswordForm(Parent owner) { this(); this.owner=owner;
		if(owner!=null) {
			
			this.initOwner(owner.getScene().getWindow()); this.initModality(Modality.WINDOW_MODAL);  // Если родитель есть, то MODAL-mode
			scene.getStylesheets().setAll(owner.getScene().getStylesheets()); // Наследование всех стилей от родителя
			
			Bounds bounds=owner.localToScreen(owner.getBoundsInLocal());
			
			//this.setX(bounds.getMinX()+OFFSET);
			//this.setY(bounds.getMinY()+OFFSET);
			this.setX(bounds.getCenterX()-this.getWidth()/2);
			this.setY(bounds.getCenterY());	// FIXME здесь высота формы не определена еще
			
		}
	}
	
	@Override public void hide() { // Вызывается всегда вне зависимости от способа закрытия
		if(result_ok) {
			result=password.getText();
		}
		
		super.hide(); if(owner!=null) owner.requestFocus();
	}
	
	////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	public String getResult() {return result_ok ? result: null;}
	// show(); showAndWait(); - Наследуются. После юзать getResult()
	
}
