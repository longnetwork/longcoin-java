package gui;

import static application.Main.*;


import coredrv.RPCCommanderCached;
import coredrv.RPCCommander.StringResponse;
import gui.AddressBookController.Listener;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.geometry.Orientation;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToolBar;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import util.Css;
import util.L10n;
import util.LOGGER;
import util.Threads;



public class PostingEditorForm extends Stage {

	static final double OFFSET = Css.getTextBounds("┃⟺",18).getHeight();
	
	private Scene scene;
		SplitPane splitV=new SplitPane();
			HTMLEditorExt webedit=new HTMLEditorExt(); // .html-editor {};
			VBox vbox=new VBox();
			
				AddressBookView addressBook=new AddressBookView();				
					
				ToolBar toolBar=new ToolBar();
					HBox info=new HBox();
						Label fromLabel=new Label(L10n.t("From")+":"); TextField fromField=new TextField();
						Label toLabel=new Label(L10n.t("To")+":"); TextField toField=new TextField();
					Separator separator1=new Separator();
					CheckBox encrypt=new CheckBox(L10n.t("Encrypt Message"));
					CheckBox pin=new CheckBox(L10n.t("Pin Message"));			// Закрепы для маркетов не предусматриваются (там пишит только владелиц)
					Separator separator2=new Separator();
					Button ok=new Button(L10n.t("Send")+" 0 kB");		
		JobInfo jobInfo=webedit.jobInfo;
	
	private Thread editControl;	// Поток для чтения текущего контента и вычисления его размера
	
	private final RPCCommanderCached rpc;
	
	private String fromPubKey=null, toPubKey=null;			// Могут быть как адресы так и пукеи (для команд отправки)
	private String fromAddress=null, toAddress=null;        // Это только адреса для TextField	
	
	private void initSize() {this.setHeight(SCREEN_HEIGHT*H1*0.875d); this.setWidth(SCREEN_WIDTH*W1*1.0d);}
	
	public PostingEditorForm(String ... fromto) { super(); // fromto - могут быть и адреса и публичные ключи
		this.initStyle(StageStyle.UTILITY); this.setResizable(true); initSize();
		
		if (rpcCommander==null) throw new NullPointerException("rpcCommander is null"); // Без источника данных работать не может
		this.rpc=rpcCommander; // XXX глобальный rpcCommander создается не статически и в статическом контексте не определен 
		
	
		splitV.getItems().addAll(webedit,vbox); splitV.getStyleClass().add("post-editor"); // .post-editor {};
			splitV.setOrientation(Orientation.VERTICAL);
			splitV.setDividerPosition(0, 0.4375d);
		
			vbox.getChildren().addAll(addressBook,toolBar); VBox.setVgrow(addressBook, Priority.ALWAYS); 
				toolBar.getItems().addAll(info,separator1,pin,encrypt,separator2,ok); toolBar.setId("send"); // .post-editor #send.tool-bar {}
					pin.setId("pin"); encrypt.setId("encrypt");
					ok.setId("send"); // .post-editor #send.button {}
					info.getChildren().addAll(fromLabel,fromField,toLabel,toField); info.setId("send"); //.post-editor #send HBox {}
					HBox.setHgrow(info, Priority.ALWAYS); 
						fromField.setPrefWidth(Css.getTextBounds("1GztQxGTKdEFhctBhR38wR8skjqkd4Cqt8 ", 15).getWidth()); // FIXME font-size hardcoded
					    toField.setPrefWidth(Css.getTextBounds("1GztQxGTKdEFhctBhR38wR8skjqkd4Cqt8 ", 15).getWidth());
					    //fromField.setFocusTraversable(false); toField.setFocusTraversable(false);
					    fromField.setPromptText(L10n.t("DragOrPasteTheAddress"));
					    toField.setPromptText(L10n.t("DragOrPasteTheAddress"));
		
			pin.setTooltip(new Tooltip(L10n.t("Cost")+" "+PIN_COST+" LONG"));	    
					    
		if(fromto!=null) {
			
			if(fromto.length>0) fromPubKey=fromto[0];
			if(fromto.length>1) toPubKey=fromto[1];
		}			    
					    
			
		addressBook.setController( new AddressBookController() ); // FIXME При выходе загасить поток или можно юзать глобальный addressBookController
		addressBook.getController().addListener((obj)-> { if(obj instanceof Listener.AddressBook) { // Это крутится в отдельном потоке контроллера
			if(fromto!=null) {
				if(fromto.length>0 && fromPubKey!=null ) fromAddress=addressBook.getController().getAddressFromPubKey(fromPubKey);
				if(fromto.length>1 && toPubKey!=null ) toAddress=addressBook.getController().getAddressFromPubKey(toPubKey);
			}
			
			Platform.runLater(()->{
				if(fromAddress!=null) fromField.setText(fromAddress);
				if(toAddress!=null) toField.setText(toAddress);
				
				sendControlsDefaults();
			});
		}});
		addressBook.getController().speedUp();

		
		fromField.textProperty().addListener((obs, oldValue, newValue)->sendControlsDefaults());
		toField.textProperty().addListener((obs, oldValue, newValue)->sendControlsDefaults());
		
		sendControlsDefaults();
		
		
		dragInit();
		
		editControl=new Thread(){ 
			volatile Boolean progress=false;
			volatile Boolean exception=false;
			@Override public void run() { // this потока
				try {
					while(!this.isInterrupted()) { 
						
						synchronized(this) {
							if(!exception) this.wait(100); // 1/10 секунды - стандарт для тормазов
							else {this.wait(5000); exception=false;} // Чтобы не спамил в лог
						} 
						
						if(!progress) { progress=true; // Без этого флага может накапливаться очередь runLatter если wait маленькое
						
							Platform.runLater(()->{ // В среднем 5ms				
									String currentContent=webedit.getHtmlText();
									if(currentContent!=null) { // null если в getHtmlText() были исключения					
										double sizeKb=(double)currentContent.length()/1000;
										
										ok.setText(String.format(L10n.t("Send")+" %.2f kB",sizeKb));
										if(sizeKb*1000 > WebEngineWrp.MAX_CONTENT_SIZE) Css.pseudoClassStateSwitch(ok, Css.ALERT_PCS);
										else Css.pseudoClassStateSwitch(ok, Css.NONE_PCS);
									}
									else exception=true;
								progress=false;
							});
						}
					}
				}
				catch (InterruptedException ignore) {}
				LOGGER.info("editControl Thread completed"); LOGGER.console("editControl Thread completed"); // TODO debug
			}
		}; editControl.setDaemon(true); editControl.start();
		
		
		
		ok.setOnAction((ev)->{ // Понеслась
			
			AddressBookModel[] ft=_getFromTo(); // Всегда 2 элемента
			AddressBookModel from=ft[0]; AddressBookModel to=ft[1];	
				
			
			if(from==null || to==null) return; // Не должно быть из-за sendControlsDefaults()
			
			if(!to.isScript()) { // Обычная отправка без сигнатуры, шифрованная или нет в зависимости от наличия ключей
				
				final boolean open= !encrypt.isSelected(); // Юзер может принудительно отрубать шифрование
				final String _content=webedit.getHtmlText(), content=(_content!=null) ? _content: "";
				
				final boolean pinned= pin.isSelected();
				
				final String toStr= (!to.getPubKey().isBlank()) ? to.getPubKey() : to.getAddress(); 
				final String fromStr; // XXX Стараемся брать по максимум для шифрованного ответа

				if(!pinned) fromStr=from.getAddress(); // Для команды sendhexdata подаем только адрес на вход (остальное она найдет сама)
				else { // Для команды createrawdata все сложнее
					fromStr=from.getPrivKey().isBlank() ? from.getPubKey().isBlank() ? from.getAddress() : from.getPubKey() : from.getPrivKey();
				}
				
				jobInfo.progress(""); ok.setDisable(true);
	    		Threads.runNow(()->{	
					StringResponse res;
					
					if(pinned) res=rpc.pinContent(fromStr, toStr, content, open);
					else       res=rpc.sendContent(fromStr, toStr, content, open);
					
					if(res.isOk()) Platform.runLater(()->jobInfo.progress(null));
					else Platform.runLater(()->jobInfo.alert(res.message));
					
					Platform.runLater(()->ok.setDisable(false));
	    		});

			}
			else { // Шлем на маркет или канал (свой) 
		           // sendControlsDefaults() гарантирует что во from - образующий адрес для подписи контента

				   final String _content=webedit.getHtmlText(), content=(_content!=null) ? _content: "";

				final String toStr= to.getAddress(); // у p2sh адресов нет ключей
				final String fromStr= from.getAddress(); // sendMessage принимает только адрес и остальное делает сама
				
				jobInfo.progress(""); ok.setDisable(true);
	    		Threads.runNow(()->{	
					StringResponse res=rpc.sendMessage(fromStr, toStr, content); // Делеает незашифрованно но с подписью сигнатуры
					if(res.isOk()) Platform.runLater(()->jobInfo.progress(null));
					else Platform.runLater(()->jobInfo.alert(res.message));
					
					Platform.runLater(()->ok.setDisable(false));
	    		});					

			}
		});
		
		
		
		
		scene = new Scene(splitV);
		
		this.setScene(scene);
		
		this.setOnShown((ev)->{ initSize(); this.requestFocus(); });
		
		this.addEventHandler(KeyEvent.KEY_PRESSED, (ev)->{
			if(ev.getCode()==KeyCode.ESCAPE) {
				this.close();
			}
		});
		this.setOnCloseRequest((ev)->{
		});
	}
	
	private Parent owner;
	PostingEditorForm(Parent owner, String ... fromto) { this(fromto); this.owner=owner;
		if(owner!=null) {
			
			this.initOwner(owner.getScene().getWindow()); this.initModality(Modality.WINDOW_MODAL);  // Если родитель есть, то MODAL-mode
			scene.getStylesheets().setAll(owner.getScene().getStylesheets()); // Наследование всех стилей от родителя
			
			Bounds bounds=owner.localToScreen(owner.getBoundsInLocal());
			
			this.setX(bounds.getMinX()+OFFSET);
			this.setY(bounds.getMinY()+OFFSET);
		}
		
	}
	
	@Override public void hide() { // Вызывается всегда вне зависимости от способа закрытия
		
		addressBook.getController().interrupt();
		editControl.interrupt();
		
		this.setContent(null); // Чтобы всякая предзагрузка прекратилась
		
		super.hide(); if(owner!=null) owner.requestFocus();
	}
	
	private void dragInit() {
		fromField.setOnDragOver((ev)->{
			 if (ev.getGestureSource() != fromField && ev.getGestureSource() == addressBook.tableMine && ev.getDragboard().hasString()) 
				 ev.acceptTransferModes(TransferMode.COPY_OR_MOVE);
			 ev.consume();
		});
		toField.setOnDragOver((ev)->{
			 if (ev.getGestureSource() != toField && ev.getDragboard().hasString()) 
				 ev.acceptTransferModes(TransferMode.COPY_OR_MOVE);
			 ev.consume();
		});
		
		fromField.setOnDragDropped((ev)->{
	        Dragboard db = ev.getDragboard();
	        boolean success = false;
	        if (db.hasString()) { fromField.setText(db.getString()); success = true; }
	        ev.setDropCompleted(success);
	        
	        ev.consume();
	        Platform.runLater(()->{this.toFront(); this.requestFocus();});
		});
		toField.setOnDragDropped((ev)->{
	        Dragboard db = ev.getDragboard();
	        boolean success = false;
	        if (db.hasString()) { toField.setText(db.getString()); success = true; }
	        ev.setDropCompleted(success);
	        
	        ev.consume();
	        Platform.runLater(()->{this.toFront(); this.requestFocus();});
		});
				
		fromField.setOnDragEntered((ev)->{			
			fromField.requestFocus();
			ev.consume();
		});
		toField.setOnDragEntered((ev)->{			
			toField.requestFocus();
			ev.consume();
		});
		
		// FIXME баг утери фокуса окна Stage после завершение DragAndDrop. (Нужно кликать лишний раз)
	}
	
	private AddressBookModel[] _getFromTo() { // XXX Смотри sendControlsDefaults() и ok.setOnAction()
		AddressBookModel from=addressBook.tableMine.getItemByAddress(fromField.getText().trim()); // Только свои адреса могут быть во from
		AddressBookModel to=addressBook.getItemByAddress(toField.getText().trim());
		if(to==null) {			
			if(toField.getText().trim().equals(toAddress)) 			// Делаем запись для подставленного в конструктор адреса, которого нет в адресной книги,
				to=new AddressBookModel(toAddress,toPubKey);		// но только если его не сменили в форме
			else if(AddressBookView.addressPattern.matcher(toField.getText()).find())
				to=new AddressBookModel(toField.getText().trim(),null);	// Отправка на копи-пастэ корректные адреса разрешена
		}
		else { // Еще может быть так что в адресной книге есть to но без пукея а пукей есть на входе в конструкторе
			if(to.getAddress().equals(toAddress))
				if(to.getPubKey().isBlank()) to.setPubKey(toPubKey);
		}
		
		return new AddressBookModel[] {from,to};
	}
	
	private void sendControlsDefaults() { // Обработка состояния по умолчанию для encrypt CheckBox и кнопки send
		
		AddressBookModel[] ft=_getFromTo(); // Всегда 2 элемента
		AddressBookModel from=ft[0]; AddressBookModel to=ft[1];
		
		pin.setDisable(true); pin.setSelected(false); // По умолчанию галочка закрепа всегда снята
		
		encrypt.setDisable(true); ok.setDisable(true);
		
		if( from==null || to==null ) { // Обязаны быть from и to
			return;
		}
		if(!from.isMine()) { // Слать можно только со своих адресов
			return;
		}
		if(to.isScript() && !to.isMine()) { // в to p2sh адрес (канал или маркет) может писать только владелец
			return;
		}
		
		// Выбор p2sh from адреса допустим (будет заменен на образующий адрес)
		
		if(to.isScript() && !from.isScript()) { // в to p2sh адрес можно писать только от образующего адреса для подписи данных  
			
			String[] addresses=to.getAddresses().split(","); 
			boolean find=false;
			for(String addr: addresses) { if(addr.isBlank()) continue;
				if(from.getAddress().equals(addr)) {find=true; break;}
			}
			
			if(!find) return;
		}
		if(to.isScript() && from.isScript()) { // в to p2sh адрес можно писать только от образующего адреса для подписи данных
			String[] from_addresses=from.getAddresses().split(",");
			String[] to_addresses=to.getAddresses().split(",");
			boolean find=false;
			L1: for(String from_addr: from_addresses) for(String to_addr : to_addresses) { if(from_addr.isBlank() && to_addr.isBlank()) continue;
					if(from_addr.equals(to_addr)) {
						find=true;
						
						fromField.setText(from_addr); // Автозамена на первый найденный образующий
												
						break L1;
					}
			}
			
			if(!find) return;
		}
		
		if(!to.isScript() && from.isScript()) { // Автозамена на образующий
			
			String[] addresses=from.getAddresses().split(",");
			String from_address=addresses[0]; 
			for(String addr: addresses) { if(addr.isBlank()) continue;
				if(to.getAddress().equals(addr)) {from_address=addr; break;}
			}
			
			fromField.setText(from_address); // Если найдем в списке образующих адрес to, то значит он и подразумевался, иначе - берем первый
								  			 // Здесь это может быть только свой адрес (с приватником), а значит шифрация не запрещается
		}
			
			
		ok.setDisable(false);
		
		//Выбраны оба адреса, при этом во from только свои адреса (интерфейс не предоставляет возможность абьюза) и всегда обычные (не p2sh)
		
		
		// Шифрация включена автоматически если оправка на адрес с известным публичным ключем (юзер может выключить),
		// Шифрации не может быть при посылке на свой канал (маркет), так как читатели не имеют приватника от канала. 
		// Отвечать читатели могут только в личку (зашифровано) на адрес в сообщении
		
		// p2sh адреса не имеют ключей - на них не возможна шифрация, но возможно сопровождение сигнатурой подписи адреса from
		
		encrypt.setSelected(false);
		
		if(to.isScript()) { // в to p2sh адрес (канал или маркет). Шифрации быть не может (будет сигнатура подписи)
			return;
		}
		
		// Для индикации определим какое будет состояние шифрации по наличию публичных ключей адресата
		if(!to.getPubKey().isBlank()) { // Шифрация по умолчанию включена, но юзер может ее отключть
			encrypt.setSelected(true); encrypt.setDisable(false);
		}
		else { // Шифрация не возможна
			encrypt.setSelected(false);
		}
		
		// Закрепленное сообщение возможно на любой не p2sh адресс
		pin.setDisable(false);
	}
	
	///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	public void setContent(String content) {
		webedit.setHtmlText(content); 
		// null в перегруженном HTMLEditor заменится на "" чтобы реально подчистить все потоки визуализации в HTMLEditor
		// иначе при закрытие приложения фоновые потоки HTMLEditor выкидывают сегфолты
	}

	// show(); showAndWait(); - Наследуются
	
}
