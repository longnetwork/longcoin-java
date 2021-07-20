package gui;

import static application.Main.*;

import coredrv.RPCCommander.StringResponse;
import javafx.application.Platform;
import javafx.geometry.Orientation;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import util.L10n;
import util.Threads;

public class CreateChannelWizard extends WizardBase {
	
	
	VBox page0;
		Label pg0_help;
		Separator pg0_separator1;
		TextField pg0_alias;	
		Region pg0_region;
		Separator pg0_separator2;
		Button pg0_next;
			
	VBox page1;
		Label pg1_help;
		Separator pg1_separator1;
		TextField pg1_p2sh;	
		Region pg1_region;
		Separator pg1_separator2;
		Button pg1_finish;
		
	volatile String error=null;

	String address=null;
	String p2sh=null;
		
	public CreateChannelWizard() { super();
	
		page0=new VBox();
			pg0_help=new Label(L10n.t("CreateChannelHelp0")); pg0_help.requestFocus();
			pg0_separator1 = new Separator(Orientation.HORIZONTAL);
			pg0_alias = new TextField(); pg0_alias.setPromptText(L10n.t("Channel Name"));
			pg0_region =new Region(); pg0_region.setPrefHeight(Region.USE_COMPUTED_SIZE); VBox.setVgrow(pg0_region, Priority.ALWAYS);
			pg0_separator2 = new Separator(Orientation.HORIZONTAL);
			
			pg0_next= new Button(L10n.t("Next"));
			pg0_next.translateXProperty().bind(page0.widthProperty().subtract(pg0_next.widthProperty()).subtract(2*8d+16d)); // FIXME hardcoded
			
		page0.getChildren().addAll(pg0_help,pg0_separator1,pg0_alias,pg0_region,pg0_separator2,pg0_next);
		pg0_next.setOnAction((ev)->{
			
			final String account=pg0_alias.getText();

			if(addressBookController!=null) {
				jobInfo.progress(""); pg0_next.setDisable(true);
				
				Threads.runNow(()->{
					StringResponse adr=rpcCommander.getNewAddress(account!=null ? account: "");
					if(!adr.isOk()) error=adr.message;
					else {		
						addressBookController.speedUp();
						
						address=adr.data;
						
						StringResponse adm=rpcCommander.addMultisigAddress(address, account!=null ? account: "");
						if(!adm.isOk()) error=adm.message;
						else {
							addressBookController.speedUp();
							
							p2sh=adm.data;
						}
					}
					
					Platform.runLater(()->{
						if(error==null) {
							jobInfo.progress(null);
							
							pg1_p2sh.setText(p2sh); pg1_p2sh.selectAll();
							this.next();
						}
						else {
							jobInfo.alert(error);  
						}
						
						pg0_next.setDisable(false);
					});
	    		});
			}
		});
		
		page1=new VBox();
			pg1_help=new Label(L10n.t("CreateChannelHelp1")); pg1_help.requestFocus();
			pg1_separator1 = new Separator(Orientation.HORIZONTAL);
			
			pg1_p2sh = new TextField(); pg1_p2sh.setEditable(false); 
			pg1_p2sh.selectedTextProperty().addListener((obs, oldSelect, newSelect)->{ if(!p2sh.equals(newSelect)) pg1_p2sh.selectAll(); });
			
			pg1_region =new Region(); pg1_region.setPrefHeight(Region.USE_COMPUTED_SIZE); VBox.setVgrow(pg1_region, Priority.ALWAYS);
			pg1_separator2 = new Separator(Orientation.HORIZONTAL);
			
			pg1_finish= new Button(L10n.t("Finish"));
			pg1_finish.translateXProperty().bind(page1.widthProperty().subtract(pg1_finish.widthProperty()).subtract(2*8d+16d)); // FIXME hardcoded
			
		page1.getChildren().addAll(pg1_help,pg1_separator1,pg1_p2sh,pg1_region,pg1_separator2,pg1_finish);		
		pg1_finish.setOnAction((ev)->{
			this.next();
		});
		
	
		this.addAll(page0,page1);
	}

	public CreateChannelWizard(Parent owner) { this(); 
		this.setOwner(owner);	
	}
	
	// showAndWait() show() - наследуются
	
}
