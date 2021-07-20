package gui;

import static application.Main.*;

import javafx.geometry.Bounds;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import util.Css;
import util.L10n;

public class TextFieldsForm extends Stage { // Число полей ввода задается числом подсказок в конструкторе

	static final double OFFSET = Css.getTextBounds("┃⟺",18).getHeight();

	private Scene scene;
		VBox vbox;
			TextField[] fields;
			Button ok;
	
			
	private String[] result=null;
	private boolean result_ok=false;
	
	
	public TextFieldsForm(String ...prompts) { super();
		this.initStyle(StageStyle.UTILITY); this.setResizable(true); this.setWidth(SCREEN_WIDTH*W1*W2*W3);
		
		if(prompts!=null) {
			fields=new TextField[prompts.length];
			for(int i=0; i<fields.length; i++) {
				fields[i]=new TextField();
				fields[i].setPromptText(prompts[i]);
			}
		}
		
		ok= new Button(L10n.t("ОК")); 
		ok.setOnAction((ev)->{ result_ok=true; this.close(); });
			
		vbox= new VBox(); vbox.getStyleClass().add("vbox-form");
		if(fields!=null) vbox.getChildren().addAll(fields);
		vbox.getChildren().add(ok);
		
		
		
		
		
		scene = new Scene(vbox);
		
		this.setScene(scene);
		
		this.setOnShown((ev)->{	
			this.setHeight(vbox.getHeight()+
					       vbox.getPadding().getBottom()+vbox.getPadding().getTop());
			
			this.setWidth(SCREEN_WIDTH*W1*W2*W3); 
			
			ok.requestFocus();
			
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
	public TextFieldsForm(Parent owner, String ...prompts) { this(prompts); this.owner=owner;
		if(owner!=null) {
			
			this.initOwner(owner.getScene().getWindow()); this.initModality(Modality.WINDOW_MODAL);  // Если родитель есть, то MODAL-mode
			scene.getStylesheets().setAll(owner.getScene().getStylesheets()); // Наследование всех стилей от родителя
			
			Bounds bounds=owner.localToScreen(owner.getBoundsInLocal());
			
			this.setX(bounds.getMinX()+OFFSET);
			this.setY(bounds.getMinY()+OFFSET);
		}
	}
	
	@Override public void hide() { // Вызывается всегда вне зависимости от способа закрытия
		
		if(result_ok) {
			if(fields!=null) {
				result=new String[fields.length];
				for(int i=0; i<fields.length; i++) result[i]=fields[i].getText(); 		
			}
		}
		
		super.hide(); if(owner!=null) owner.requestFocus();
	}
	
	////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	public String[] getResult() {return result_ok ? result: null;}
	// show(); showAndWait(); - Наследуются. После юзать getResult()
	
}
