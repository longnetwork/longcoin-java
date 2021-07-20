package gui;

import static application.Main.*;

import application.Main;
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
import util.LOGGER;
import util.Threads;

public class EncryptWalletWizard extends WizardBase {
	
	
	VBox page;
		Label pg_help;
		Separator pg_separator1;
		PasswordField pg_Password;
		PasswordField pg_rePassword;
		Region pg_region;
		Separator pg_separator2;
		Button pg_start;
	
	volatile boolean successfully=false;
	
	public EncryptWalletWizard() { super();
	
		page=new VBox();
			pg_help=new Label(L10n.t("EncryptWalletHelp")); pg_help.requestFocus();
			pg_separator1 = new Separator(Orientation.HORIZONTAL);
			
			pg_Password = new PasswordField(); pg_Password.setPromptText(L10n.t("Enter Password"));
			pg_rePassword = new PasswordField(); pg_rePassword.setPromptText(L10n.t("Repeat Password"));
			
			pg_region =new Region(); pg_region.setPrefHeight(Region.USE_COMPUTED_SIZE); VBox.setVgrow(pg_region, Priority.ALWAYS);
			pg_separator2 = new Separator(Orientation.HORIZONTAL);
			
			pg_start= new Button(L10n.t("Start"));
			pg_start.translateXProperty().bind(page.widthProperty().subtract(pg_start.widthProperty()).subtract(2*8d+16d)); // FIXME hardcoded
			
		page.getChildren().addAll(pg_help,pg_separator1,pg_Password,pg_rePassword,pg_region,pg_separator2,pg_start);
		pg_start.setOnAction((ev)->{
			
			if(successfully) { this.next(); return; }
			
			final String password=pg_Password.getText();
			final String repassword=pg_rePassword.getText();
			
			if(password.isEmpty() || repassword.isEmpty()) jobInfo.alert("Blank fields are not allowed!");
			else if(!password.equals(repassword)) jobInfo.alert("The passwords do not match!");
			else {		
				jobInfo.progress("Encrypting wallet..."); pg_start.setDisable(true);
				Threads.runNow(()->{
					
					LOGGER.info("Encrypting wallet...");
					
					final BoolResponse res=rpcCommander.encryptWallet(password);
					if(res.isOk()) {
						
						LOGGER.info("Core restarting...");
						
						Platform.runLater(()->jobInfo.progress("Core restarting..."));
						
						try {
							// Ждем завершения процесса
							if(longcoinDaemon.isAlive()) {
								if(!longcoinDaemon.waitFor(180*1000)) { // FIXME hardcoded
									LOGGER.error("Core does not stop for a long time");
									throw new RuntimeException("Core does not stop for a long time");
								}
							}
							
							Main.daemonStart(); // Стартуем заново как при запуске программы
								
							successfully=true;
							
						} catch (Exception e) {
							res.message=e.getMessage();
						} 
						
					}
					
					Platform.runLater(()->{
						if(successfully) {
							jobInfo.popup(L10n.t("Successfully"));
							
							pg_start.setText(L10n.t("Close")); 
						}
						else {
							jobInfo.alert(res.message);
						}
						
						pg_start.setDisable(false);
					});
					
	    		});
			}
		});
	
		this.addAll(page);
	}

	public EncryptWalletWizard(Parent owner) { this(); 
		this.setOwner(owner);	
	}
	
	// showAndWait() show() - наследуются
	
}
