package gui;

import static application.Main.*;

import javafx.geometry.Orientation;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import util.Css;
import util.L10n;

public class DonationWizard extends WizardBase {
	
	static final double TICKER_WIDTH = Css.getTextBounds("┃⟺",12).getWidth()*2;
	
	VBox page;
		Label pg_help;
		Separator pg_separator1;
		
		HBox longBox;
			Label longLabel; TextField longAddress; Button longButton;
		HBox dogeBox;
			Label dogeLabel; TextField dogeAddress; Button dogeButton;
		HBox ethBox;
			Label ethLabel; TextField ethAddress; Button ethButton;		
		HBox ltcBox;
			Label ltcLabel; TextField ltcAddress; Button ltcButton;				
		HBox btcBox;
			Label btcLabel; TextField btcAddress; Button btcButton;				
			
		Region pg_region;
		
		Hyperlink site;
		
		Separator pg_separator2;
		Button pg_close;
	
	
	private void donationInit(final HBox box, final Label label, final TextField address, final Button button) {
		String coin=label.getText();
		
		label.setText(coin+":"); label.setMinWidth(TICKER_WIDTH);
		
		address.setEditable(false); address.selectAll();
		address.selectedTextProperty().addListener((obs, oldSelect, newSelect)->{ 
				if(!address.getText().equals(newSelect)) address.selectAll(); 
			});
		
		button.setId(coin); 
		button.prefWidthProperty().bind(button.heightProperty());
		button.setTooltip(new Tooltip(L10n.t("Copy to Clipboard")));
		button.setOnAction((ev)->{
					final ClipboardContent content = new ClipboardContent();
					content.putString(address.getText());
					Clipboard.getSystemClipboard().setContent(content);
				});
		
		box.getChildren().addAll(label,address,button); HBox.setHgrow(address, Priority.ALWAYS);
	}
	
	public DonationWizard() { super();
	
		page=new VBox();
			pg_help=new Label(L10n.t("DonationHelp")); pg_help.requestFocus();
			pg_separator1 = new Separator(Orientation.HORIZONTAL);
			
			
			donationInit(longBox=new HBox(),longLabel=new Label("LONG"),longAddress=new TextField(DONAT_LONG),longButton=new Button());
			donationInit(dogeBox=new HBox(),dogeLabel=new Label("DOGE"),dogeAddress=new TextField(DONAT_DOGE),dogeButton=new Button());
			donationInit(ethBox=new HBox(),  ethLabel=new Label("ETH"),ethAddress=new TextField(DONAT_ETH),ethButton=new Button());
			donationInit(ltcBox=new HBox(),  ltcLabel=new Label("LTC"),ltcAddress=new TextField(DONAT_LTC),ltcButton=new Button());
			donationInit(btcBox=new HBox(),  btcLabel=new Label("BTC"),btcAddress=new TextField(DONAT_BTC),btcButton=new Button());
			
			pg_region =new Region(); pg_region.setPrefHeight(Region.USE_COMPUTED_SIZE); VBox.setVgrow(pg_region, Priority.ALWAYS);
			
			site = new Hyperlink(SITE); if(application!=null) site.setOnAction((ev)-> application.getHostServices().showDocument(site.getText()));
			site.translateXProperty().bind(page.widthProperty().subtract(site.widthProperty()).subtract(2*8d+16d)); // FIXME hardcoded
			
			pg_separator2 = new Separator(Orientation.HORIZONTAL);
			
			pg_close= new Button(L10n.t("Close"));
			pg_close.translateXProperty().bind(page.widthProperty().subtract(pg_close.widthProperty()).subtract(2*8d+16d)); // FIXME hardcoded
		
			
			
			
			
			
			
			
		page.getChildren().addAll(pg_help,pg_separator1,
				
								  longBox,dogeBox,ethBox,ltcBox,btcBox,
								  
								  pg_region,
								  
								  site,
									
								  pg_separator2,pg_close);
		
		pg_close.setOnAction((ev)->{		
			 this.next(); return;
		});
	
		this.addAll(page);
	}

	public DonationWizard(Parent owner) { this(); 
		this.setOwner(owner);	
	}
	
	// showAndWait() show() - наследуются
	
}
