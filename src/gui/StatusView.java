package gui;


import util.Binary;
import util.Css;
import util.L10n;
import util.LOGGER;
import gui.StatusController.Listener;

import java.text.NumberFormat;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.concurrent.Worker.State;
import javafx.event.EventTarget;
import javafx.geometry.Insets;
import javafx.geometry.VPos;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;

import javafx.scene.input.MouseEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.text.Text;
import javafx.scene.web.WebView;

public class StatusView extends GridPane {
	
	static final double PADDING_BOTTOM = Css.getTextBounds("┃⟺",14).getHeight()*2; // Для скроллбара запас для pinView
	
	Text balancesTitle=new Text(L10n.t("Balances"));
		Text availableField=new Text(L10n.t("Available")+":"); Text availableText=new Text();
		Text pendingField=new Text(L10n.t("Pending")+":"); Text pendingText=new Text();
	Text blockchainTitle=new Text(L10n.t("Blockchain"));
		Text blocksField=new Text(L10n.t("Blocks")+":"); Text blocksText=new Text();
		Text synchronizationField=new Text(L10n.t("Synchronization")+":"); Text synchronizationText=new Text();
		Text difficultyField=new Text(L10n.t("Difficulty")+":"); Text difficultyText=new Text();
	Text networkTitle=new Text(L10n.t("Network"));
		Text hashrateField=new Text(L10n.t("Hashrate")+":"); Text hashrateText=new Text();
		Text connectionsField=new Text(L10n.t("Connections")+":"); Text connectionsText=new Text();
	
	TransactionsView txView=new TransactionsView();	// Только для предпросмотра и все (disabled)
		
	WebViewWrp pinView = new WebViewWrp(); // TODO Место под рекламу например
		
	Label infoLabel=new Label();
		
	
	public StatusView() { super();
		
	
		balancesTitle.getStyleClass().add("title"); 		// .status-view .title {}
			availableField.getStyleClass().add("field");    // .status-view .field {}
			pendingField.getStyleClass().add("field");
		
		blockchainTitle.getStyleClass().add("title");
			blocksField.getStyleClass().add("field");
			synchronizationField.getStyleClass().add("field");
			difficultyField.getStyleClass().add("field");
		
		networkTitle.getStyleClass().add("title");
			hashrateField.getStyleClass().add("field");
			connectionsField.getStyleClass().add("field");		
		
		pinView.getNode().getStyleClass().add("pin-view");
		infoLabel.setId("info"); //.status-view #info.label {}
		
	
             this.add(balancesTitle, 0, 0);
            this.add(availableField, 0, 1);       this.add(availableText, 1, 1);
              this.add(pendingField, 0, 2);         this.add(pendingText, 1, 2);
              
           this.add(blockchainTitle, 0, 3);
               this.add(blocksField, 0, 4);          this.add(blocksText, 1, 4);
      this.add(synchronizationField, 0, 5); this.add(synchronizationText, 1, 5);		
           this.add(difficultyField, 0, 6);      this.add(difficultyText, 1, 6);	
	
              this.add(networkTitle, 0, 7);
             this.add(hashrateField, 0, 8);        this.add(hashrateText, 1, 8);
          this.add(connectionsField, 0, 9);     this.add(connectionsText, 1, 9);
		
                                                                                    this.add(txView, 2, 0, 1, 11);
          
         this.add(pinView.getNode(), 0, 10, 2, 1); 
          
                 this.add(infoLabel, 0, 11, 3 ,1); // размер на две колонки и одну строку    

	                                                                              
		
                 
	    GridPane.setValignment(connectionsField, VPos.TOP); GridPane.setValignment(connectionsText, VPos.TOP);
                 
	    GridPane.setVgrow(pinView.getNode(), Priority.SOMETIMES); pinView.setPrefHeight(USE_COMPUTED_SIZE);
	                                                                              
	    GridPane.setVgrow(txView, Priority.ALWAYS);
	    GridPane.setHgrow(txView, Priority.ALWAYS);
	    
	                                        
	    infoLabel.setWrapText(true); 
	    
	    txView.setDisable(true);  txView.setFocusTraversable(false); txView.setMouseTransparent(true);
	    
	    contextMenuInit();
	                                                                              
		
	    pinView.getEngine().getLoadWorker().stateProperty().addListener( (obs, oldState, newState) -> {
        	if(newState == State.SCHEDULED) { 
        		pinView.setZoom(1.0);
       			return;
        	}
        	if(newState == State.SUCCEEDED ) { // Будем вписыват контент в pinView
        		double h = PADDING_BOTTOM + 
 					   (Integer)pinView.getEngine().executeScript("" +
 							   "const body = document.body," +
                                "      html = document.documentElement;" +
                                "Math.min( body.scrollHeight, body.offsetHeight, " +
                                "          html.clientHeight, html.scrollHeight, html.offsetHeight);" +
 							   									  "");
        		
        		double viewHeight=pinView.getHeight();
        		
        		pinView.setZoom(viewHeight/h);
        		
	        	return;
        	}
        });
	    
	    pinView.getNode().setFocusTraversable(false);
	    
	    this.getStyleClass().add("status-view");
		
		// layoutInit() в layoutChildren()
	}
	private StatusController controller=null;
	public StatusView(StatusController controller) { this(); 
		this.controller=controller;
	}
	
	void layoutInit() {
		txView.setPrefWidth(this.getWidth()*0.625d); // FIXME  hardcoded
		
		
		txView.paddingProperty().bind( Bindings.createObjectBinding(() -> {
			
			double top=txView.getHeight()*(1-txView.getScaleY())/(2*txView.getScaleY());
			double bottom=top;
			double left=txView.getWidth()*(1-txView.getScaleX())/(2*txView.getScaleX());
			double right=left;
			
			return new Insets(-top,-right,-bottom,-left);
					
		}, txView.widthProperty(), txView.heightProperty()) ); 	
		
	}
	
		
	private volatile int layoutInit=-1; // Для доступа к внешнему контейнеру, когда он готов (чтобы настройки внешнего вида не во вне кодить)
	@Override protected void layoutChildren() { super.layoutChildren(); // Вызывается как минимум 1 раз при отображении
		if(layoutInit<0) {layoutInit++; // lookup в конструкторе до загрузки css не работает (а здесь уже работает)
			layoutInit();
			
			setController(this.controller);
		}
		else if(layoutInit<1) {layoutInit++;
		}
		else if(layoutInit<2) {layoutInit++;

		}
	}
	
	ContextMenu contextMenu;
		MenuItem refreshMenuItem;
		//SeparatorMenuItem separatorMenuItem;
	
	void contextMenuInit() {
		contextMenu=new ContextMenu();
		
		refreshMenuItem = new MenuItem(L10n.t("Refresh"));
		refreshMenuItem.setOnAction( (ev) -> {
				txView.refresh();
		});
		
		contextMenu.getItems().addAll(refreshMenuItem);
		
		this.setOnContextMenuRequested((ev)->{
			if(contextMenu.getUserData()!=null) contextMenu.show(this.getScene().getWindow(), ev.getScreenX(), ev.getScreenY());
		});
		
		
		this.addEventFilter(MouseEvent.MOUSE_PRESSED, (ev)-> { // Чтобы не перекрывало контекстное меню pinView
			EventTarget et=ev.getTarget();
			if(et instanceof WebView) contextMenu.setUserData(null); // Флаг выключения, если кликаем на webView
			else contextMenu.setUserData(true);
		});
	}
	
	
	private volatile long pinHash=-1; 
	
	/////////////////////////////////////////////////////////// Интерфейс /////////////////////////////////////////////////////
	
	public StatusController getController() {return this.controller;}
	
	public void setController(StatusController controller) { this.controller=controller;
	
		if(controller!=null)
			txView.setController(new TransactionsController());
	
		if(controller!=null && layoutInit>=0) { // Помним, что слушатели исполняются в стандартном потоке контроллера!
			
			controller.addListener((obj)-> { if(obj instanceof Listener.Error) { // Обработчик ошибок
				
				final Listener.Error err=(Listener.Error)obj;	// final заставляет захватить локальную err до момента вызова лямбды со ссылкой на нее		
				Platform.runLater(()->{ 						// Platform.runLater() исполняет в потоке javaFX
					
					infoLabel.setText(err.message); Css.pseudoClassStateSwitch(infoLabel, Css.ALERT_PCS);
					
					LOGGER.warning(err.message); LOGGER.console(err.message); // TODO debug
				});
			}});	
			
			controller.addListener((obj)-> { if(obj instanceof Listener.FullStatus) {
				
				
				final Listener.FullStatus status=(Listener.FullStatus)obj;
				
				Platform.runLater(()->{ 
					
					if(status.unlocked_until==0) { // Нужна дешифровка кошелька
						infoLabel.setText(L10n.t("walletEncrypted"));
						Css.pseudoClassStateSwitch(infoLabel, Css.ALERT_PCS);
					}
					else					
						if(!status.errors.isBlank()) {
							infoLabel.setText(status.errors);
							Css.pseudoClassStateSwitch(infoLabel, Css.ALERT_PCS);
						}
						else if(!status.warnings.isBlank()) {
							infoLabel.setText(status.warnings);
							Css.pseudoClassStateSwitch(infoLabel, Css.INFO_PCS);
						}
						else {
							infoLabel.setText(String.format(L10n.t("Synchronization")+" %.2f %%", status.synchronization*100));
							Css.pseudoClassStateSwitch(infoLabel, Css.NONE_PCS);
						}
					
					availableText.setText( NumberFormat.getInstance().format(status.balance)+" LONG" );
					pendingText.setText( NumberFormat.getInstance().format(status.unconfirmed_balance+status.immature_balance)+" LONG" );
					
					blocksText.setText( String.format("%d", status.blocks) );
					synchronizationText.setText( String.format("%.2f %%", status.synchronization*100) );
					difficultyText.setText( String.format("%.3f", status.difficulty) );
					
					hashrateText.setText( String.format("%.0f H/s", status.networkhashps) );
					connectionsText.setText( String.format("%d", status.connections) );
					
				});
						
			}});
			
			controller.addListener((obj)-> { if(obj instanceof String) { // Приходит при наличии pinned Message
				
				final String content=(String)obj;
				if(content!=null) {
					long hash=Binary.longHash(content);
					if(pinHash!=hash) { pinHash=hash;
					
						Platform.runLater(()->{
							pinView.getEngine().loadContent(content);
						});
						
					}
				}
				
			}});
			
					
			this.controller.speedUp();
		}
	}
}

