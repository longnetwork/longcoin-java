package gui;

import static application.Main.*;

import java.util.ArrayList;
import java.util.List;

import application.Main;
import javafx.application.Platform;
import javafx.geometry.Orientation;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import util.L10n;
import util.LOGGER;
import util.Threads;

public class PreventionWalletWizard extends WizardBase {
	
	
	VBox page;
		Label pg_help;
		Separator pg_separator1; 
		CheckBox pg_rescan;			// -rescan
		CheckBox pg_zapwallettxes;  // -zapwallettxes
		CheckBox pg_reindex;		// -reindex
		TextField pg_addoptions;
		Region pg_region;
		Separator pg_separator2;
		Button pg_start;
	
	volatile boolean successfully=false;
	
	volatile String error;
	
	public PreventionWalletWizard() { super();
	
		page=new VBox();
			pg_help=new Label(L10n.t("WalletPreventionHelp")); pg_help.requestFocus();
			pg_separator1 = new Separator(Orientation.HORIZONTAL);
			
			pg_rescan=new CheckBox(L10n.t("SearchMissedTransactions"));
			pg_zapwallettxes=new CheckBox(L10n.t("CancellationTransactions"));
			pg_reindex=new CheckBox(L10n.t("FullBlockchainResynchronization"));
			
			pg_addoptions=new TextField(); pg_addoptions.setPromptText(L10n.t("AdditionalOptions"));
			
			
			pg_region =new Region(); pg_region.setPrefHeight(Region.USE_COMPUTED_SIZE); VBox.setVgrow(pg_region, Priority.ALWAYS);
			pg_separator2 = new Separator(Orientation.HORIZONTAL);
			
			pg_start= new Button(L10n.t("Start"));
			pg_start.translateXProperty().bind(page.widthProperty().subtract(pg_start.widthProperty()).subtract(2*8d+16d)); // FIXME hardcoded
			
		page.getChildren().addAll(pg_help,pg_separator1,pg_rescan,pg_zapwallettxes,pg_reindex,pg_addoptions,pg_region,pg_separator2,pg_start);
		
		
		pg_reindex.selectedProperty().addListener((obs, oldBool, newBool)->{if(newBool) pg_zapwallettxes.setSelected(true);});
		pg_zapwallettxes.selectedProperty().addListener((obs, oldBool, newBool)->{if(newBool) pg_rescan.setSelected(true);});
		
		pg_rescan.selectedProperty().addListener((obs, oldBool, newBool)->{if(!newBool) pg_zapwallettxes.setSelected(false);});
		pg_zapwallettxes.selectedProperty().addListener((obs, oldBool, newBool)->{if(!newBool) pg_reindex.setSelected(false);});
		
		pg_start.setOnAction((ev)->{
			
			if(successfully) { this.next(); return; }
			
			final List<String> options=new ArrayList<>();
			
			if(pg_rescan.isSelected())	 	  options.add("-rescan");
			if(pg_zapwallettxes.isSelected()) options.add("-zapwallettxes=1");
			if(pg_reindex.isSelected()) 	  options.add("-reindex");
			
			if(!pg_addoptions.getText().isBlank()) options.addAll(List.of( pg_addoptions.getText().trim().split(" ") ));

			jobInfo.progress("Core restarting..."); pg_start.setDisable(true);
			Threads.runNow(()->{
				
				try {
				
					LOGGER.info("Core restarting...");
					
					Main.daemonShutDown(); // Либо завершит либо кинет исключение
					
					Main.daemonStart(options.toArray(new String[options.size()])); // Стартуем заново с новыми опциями
					
					addressBookController.speedUp(); // -salvagewallet в доп опциях очищает адресную книгу
						
					successfully=true;
					
				} catch (Exception e) {
					error=e.getMessage();
				} 
				
				Platform.runLater(()->{
					if(successfully) {
						jobInfo.popup(L10n.t("Successfully"));
						
						pg_start.setText(L10n.t("Close")); 
					}
					else {
						jobInfo.alert(error);
					}
					
					pg_start.setDisable(false);
				});
				
    		});

		});
	
		this.addAll(page);
	}

	public PreventionWalletWizard(Parent owner) { this(); 
		this.setOwner(owner);	
	}
	
	// showAndWait() show() - наследуются
	
}
