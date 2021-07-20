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

public class ConnectChatWizard extends WizardBase {
	
	
	VBox page0;
		Label pg0_help;
		Separator pg0_separator1;
		TextField pg0_alias;
		TextField pg0_privkey;
		Region pg0_region;
		Separator pg0_separator2;
		Button pg0_next;
			
	VBox page1;
		Label pg1_help;
		Separator pg1_separator1;
		TextField pg1_address;	
		Region pg1_region;
		Separator pg1_separator2;
		Button pg1_finish;

	volatile String error=null;
	
	String address=null;
		
	public ConnectChatWizard() { super();
	
		page0=new VBox();
			pg0_help=new Label(L10n.t("ConnectChatHelp0")); pg0_help.requestFocus();
			pg0_separator1 = new Separator(Orientation.HORIZONTAL);
			pg0_alias = new TextField(); pg0_alias.setPromptText(L10n.t("Private Group Name"));
			pg0_privkey = new TextField(); pg0_privkey.setPromptText(L10n.t("Group Private Key"));
			pg0_region =new Region(); pg0_region.setPrefHeight(Region.USE_COMPUTED_SIZE); VBox.setVgrow(pg0_region, Priority.ALWAYS);
			pg0_separator2 = new Separator(Orientation.HORIZONTAL);
			
			pg0_next= new Button(L10n.t("Next"));
			pg0_next.translateXProperty().bind(page0.widthProperty().subtract(pg0_next.widthProperty()).subtract(2*8d+16d)); // FIXME hardcoded
			
		page0.getChildren().addAll(pg0_help,pg0_separator1,pg0_alias,pg0_privkey,pg0_region,pg0_separator2,pg0_next);
		pg0_next.setOnAction((ev)->{
			
			final String account=pg0_alias.getText();
			final String privkey=pg0_privkey.getText();

			if(addressBookController!=null) {
				jobInfo.progress("Blockchain scanning..."); pg0_next.setDisable(true);
				
				Threads.runNow(()->{
					StringResponse adr=rpcCommander.importPrivKey(privkey.trim(), account!=null ? account: "");
					if(!adr.isOk()) error=adr.message;
					else {
						addressBookController.speedUp();
						
						address=adr.data;
					}
					
					Platform.runLater(()->{
						if(error==null) {
							jobInfo.progress(null);
							
							pg1_address.setText(address); pg1_address.selectAll();
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
			pg1_help=new Label(L10n.t("ConnectChatHelp1")); pg1_help.requestFocus();
			pg1_separator1 = new Separator(Orientation.HORIZONTAL);
			
			pg1_address = new TextField(); pg1_address.setEditable(false); 
			pg1_address.selectedTextProperty().addListener((obs, oldSelect, newSelect)->{ if(!address.equals(newSelect)) pg1_address.selectAll(); });
			
			pg1_region =new Region(); pg1_region.setPrefHeight(Region.USE_COMPUTED_SIZE); VBox.setVgrow(pg1_region, Priority.ALWAYS);
			pg1_separator2 = new Separator(Orientation.HORIZONTAL);
			
			pg1_finish= new Button(L10n.t("Finish"));
			pg1_finish.translateXProperty().bind(page1.widthProperty().subtract(pg1_finish.widthProperty()).subtract(2*8d+16d)); // FIXME hardcoded
			
		page1.getChildren().addAll(pg1_help,pg1_separator1,pg1_address,pg1_region,pg1_separator2,pg1_finish);		
		pg1_finish.setOnAction((ev)->{
			this.next();
		});
		
	
		this.addAll(page0,page1);
	}

	public ConnectChatWizard(Parent owner) { this(); 
		this.setOwner(owner);	
	}
	
	// showAndWait() show() - наследуются
	
}
