package gui;

import static application.Main.*;

import javafx.geometry.Bounds;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import util.Css;
import util.L10n;

public class SliderSizeForm extends Stage {

	static final double OFFSET = Css.getTextBounds("┃⟺",18).getHeight();

	private Scene scene;
		VBox vbox;
			Label label;
			Slider slider;
			Button ok;
		private String caption=L10n.t("Size selection");
	
	private Double result=Double.NaN;
	private boolean result_ok=false;
	
	public SliderSizeForm(double min, double max, double def) { super();
		this.initStyle(StageStyle.UTILITY); this.setResizable(true); this.setWidth(SCREEN_WIDTH*W1*W2*W3);

		label= new Label(caption+": "+(int)def);
		
		slider = new Slider(min,max,def);
		
		slider.valueProperty().addListener((obs, oldValue, newValue)->{
			label.setText(caption+": "+newValue.intValue());
		});
			
		
		ok= new Button(L10n.t("ОК")); ok.setOnAction((ev)->{ result_ok=true; this.close(); });
			
		vbox= new VBox(label,slider,ok); vbox.getStyleClass().add("vbox-form");

		
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
		});
		this.setOnCloseRequest((ev)->{
		});	
	}

	private Parent owner;
	public SliderSizeForm(Parent owner,double min, double max, double def) { this(min,max,def); this.owner=owner;
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
			result=slider.getValue();
		}
		
		super.hide(); if(owner!=null) owner.requestFocus();
	}
	
	////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	public Double getResult() {return result_ok ? result: null;}
	// show(); showAndWait(); - Наследуются. После юзать getResult()
	
}
