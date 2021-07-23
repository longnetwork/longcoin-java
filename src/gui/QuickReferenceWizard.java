package gui;


import javafx.geometry.Orientation;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import util.L10n;

public class QuickReferenceWizard extends WizardBase {
		
	VBox page;
		Label pg_help;
		Separator pg_separator1;		
			
		Region pg_region;
		
		Separator pg_separator2;
		Button pg_close;
	
	
	public QuickReferenceWizard() { super();
	
		page=new VBox();
			pg_help=new Label(L10n.t("QuickReferenceHelp")); pg_help.requestFocus(); pg_help.setStyle("-fx-font-size: 12;");
			pg_separator1 = new Separator(Orientation.HORIZONTAL);
			
			pg_region =new Region(); pg_region.setPrefHeight(Region.USE_COMPUTED_SIZE); VBox.setVgrow(pg_region, Priority.ALWAYS);
						
			pg_separator2 = new Separator(Orientation.HORIZONTAL);
			
			pg_close= new Button(L10n.t("Close"));
			pg_close.translateXProperty().bind(page.widthProperty().subtract(pg_close.widthProperty()).subtract(2*8d+16d)); // FIXME hardcoded
		

		page.getChildren().addAll(pg_help,pg_separator1,
								  
								  pg_region,
								  
								  pg_separator2,pg_close);
		
		pg_close.setOnAction((ev)->{		
			 this.next(); return;
		});
	
		this.addAll(page);
	}

	public QuickReferenceWizard(Parent owner) { this(); 
		this.setOwner(owner);	
	}
	
	// showAndWait() show() - наследуются
	
}
