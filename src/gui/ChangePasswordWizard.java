package gui;

import static application.Main.*;

import coredrv.RPCCommander.BoolResponse;
import javafx.application.Platform;
import javafx.geometry.Orientation;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Separator;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import util.L10n;
import util.Threads;

public class ChangePasswordWizard extends WizardBase {
	
	
	VBox page;
		Label pg_help;
		Separator pg_separator1;
		PasswordField pg_oldPassword;
		PasswordField pg_newPassword;
		PasswordField pg_renewPassword;
		Region pg_region;
		Separator pg_separator2;
		Button pg_ok;
	
	volatile boolean successfully=false;
	
	public ChangePasswordWizard() { super();
	
		page=new VBox();
			pg_help=new Label(L10n.t("ChangePasswordHelp")); pg_help.requestFocus();
			pg_separator1 = new Separator(Orientation.HORIZONTAL);
			
			pg_oldPassword = new PasswordField(); pg_oldPassword.setPromptText(L10n.t("Enter old Password"));
			pg_newPassword = new PasswordField(); pg_newPassword.setPromptText(L10n.t("Enter new Password"));
			pg_renewPassword = new PasswordField(); pg_renewPassword.setPromptText(L10n.t("Repeat new Password"));
			
			pg_region =new Region(); pg_region.setPrefHeight(Region.USE_COMPUTED_SIZE); VBox.setVgrow(pg_region, Priority.ALWAYS);
			pg_separator2 = new Separator(Orientation.HORIZONTAL);
			
			pg_ok= new Button(L10n.t("OK"));
			pg_ok.translateXProperty().bind(page.widthProperty().subtract(pg_ok.widthProperty()).subtract(2*8d+16d)); // FIXME hardcoded
			
		page.getChildren().addAll(pg_help,pg_separator1,pg_oldPassword,pg_newPassword,pg_renewPassword,pg_region,pg_separator2,pg_ok);
		pg_ok.setOnAction((ev)->{
			
			if(successfully) { this.next(); return; }
			
			final String oldPassword=pg_oldPassword.getText();
			final String newPassword=pg_newPassword.getText();
			final String renewPassword=pg_renewPassword.getText();
			
			if(oldPassword.isEmpty() || newPassword.isEmpty() || renewPassword.isEmpty()) jobInfo.alert("Blank fields are not allowed!");
			else if(!newPassword.equals(renewPassword)) jobInfo.alert("The new passwords do not match!");
			else {		
				jobInfo.progress(""); pg_ok.setDisable(true);
				Threads.runNow(()->{
					
					final BoolResponse res=rpcCommander.walletPassphraseChange(oldPassword, newPassword);
					if(res.isOk()) {
						successfully=true;
					}
					
					Platform.runLater(()->{
						if(successfully) {
							jobInfo.popup(L10n.t("Successfully"));
							
							pg_ok.setText(L10n.t("Close")); 
						}
						else {
							jobInfo.alert(res.message);
						}
						
						pg_ok.setDisable(false);
					});
					
					
	    		});
			}
		});
	
		this.addAll(page);
	}

	public ChangePasswordWizard(Parent owner) { this(); 
		this.setOwner(owner);	
	}
	
	// showAndWait() show() - наследуются
	
}
