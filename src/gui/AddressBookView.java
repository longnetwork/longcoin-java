package gui;

import util.L10n;
import util.LOGGER;
import util.Threads;
import gui.AddressBookController.Listener;

import java.util.regex.Pattern;

import javafx.application.Platform;
import javafx.collections.ObservableList;

import javafx.scene.control.SplitPane;

public class AddressBookView extends SplitPane {
	
	static final Pattern addressPattern=Pattern.compile("^\\s*[13][a-km-zA-HJ-NP-Z1-9]{25,34}\\s*$");
	
	
	AddressBookTable tableMine=new AddressBookTable(L10n.t("my"));
	AddressBookTable tableOther=new AddressBookTable(L10n.t("other"));
	AddressBookTable tableWatched=new AddressBookTable(L10n.t("watched"));
		
	JobInfo jobInfo;	   // Это индикация длительных операций и ошибок	

	public AddressBookView() { super();
	
		tableMine.setFilter("ismine"); tableMine.setTooltip(L10n.t("newAddressOrPrivateKey"));
		tableOther.setFilter("isother"); tableOther.setTooltip(L10n.t("saveAddressOrPublicKey"));
		tableWatched.setFilter("iswatched"); tableWatched.setTooltip(L10n.t("importAddressOrPublicKey"));

		this.getItems().addAll(tableMine,tableOther,tableWatched); // Объединение таблиц в сплите
			this.setDividerPosition(0, 1.0/3); this.setDividerPosition(1, 2.0/3);
		
		jobInfo=new JobInfo(true); 
		this.getChildren().addAll(jobInfo); // Рисуется поверх родителя
		
		this.getStyleClass().add("book-view");
		
		// layoutInit() в layoutChildren()
	}
	private AddressBookController controller=null;
	public AddressBookView(AddressBookController controller) { this();
		this.controller=controller;
	}
	
	void layoutInit() {
	}
	
		
	private volatile int layoutInit=-1; // Для доступа к внешнему контейнеру, когда он готов (чтобы настройки внешнего вида не во вне кодить)
	@Override protected void layoutChildren() { super.layoutChildren(); // Вызывается как минимум 1 раз при отображении
		if(layoutInit<0) {layoutInit++; 								// lookup в конструкторе до загрузки css не работает (а здесь уже работает)
		}
		else if(layoutInit<1) {layoutInit++;
		}
		else if(layoutInit<2) {layoutInit++;
			layoutInit();
			childFormsInit();
			
			setController(this.controller);
		}
	}
		
	
	private volatile String autoSelectAddress=null; // Для автоселеции новых адресов после обновления адресной книги
	public synchronized void autoSelectAddress(String adr) {autoSelectAddress=adr; controller.speedUp();}
	
	void childFormsInit() {
		
		tableMine.setAddAddressHandler(()->{
			TextFieldsForm addAddress=new TextFieldsForm(tableMine,
					L10n.t("addressAlias"),
					L10n.t("importedSignature") 										    );
			addAddress.showAndWait();
			
			if(addAddress.getResult()!=null) {
				final String account=addAddress.getResult()[0];
				final String signature=addAddress.getResult()[1];
				
				if(signature!=null && !signature.isBlank()) { // Импорт приватника или создание multisig-адреса
					
					if(addressPattern.matcher(signature).find()) {
						if(controller!=null) {
							jobInfo.progress("");
							Threads.runNow(()->{
								final String p2sh=controller.addMultisigAddress(signature.trim(),account!=null ? account: "");						
								if(p2sh!=null) autoSelectAddress(p2sh);
				    		});						
						}
					}
					else { // Это приватный ключ для импорта
						if(controller!=null) {
							// Запускаем в отдельном потоке чтобы UI не зависал на период сканирования
							jobInfo.progress("Blockchain scanning..."); 
							Threads.runNow(()->{
								final String address=controller.importPrivKey(signature.trim(), account!=null ? account: "");						
								if(address!=null) autoSelectAddress(address); 
								// При начале загрузки jobInfo.progress перекроется 
								// в случае ошибок - сработает Listener и перекроет jobInfo.progress
				    		});
						}
					}
				}
				else { // генерация обычного нового адреса
					if(controller!=null) {
						jobInfo.progress("");
						Threads.runNow(()->{
							final String address=controller.getNewAddress(account!=null ? account: "");						
							if(address!=null) autoSelectAddress(address);
			    		});						
					}
				}
				
			}
			
			tableMine.requestFocus();	
			//tableMine.refresh();
		});
		
		tableOther.setAddAddressHandler(()->{
			TextFieldsForm addAddress=new TextFieldsForm(tableOther,
					L10n.t("savedPublicKey"),
					L10n.t("addressAlias") 										    );
			addAddress.showAndWait();
			
			if(addAddress.getResult()!=null) {
				final String account=addAddress.getResult()[1];
				final String addressID=addAddress.getResult()[0];
				
				if(controller!=null) {
					jobInfo.progress("");
					Threads.runNow(()->{
						final String address=controller.setAccount( addressID!=null ? addressID.trim(): ""  , account!=null ? account: "" );						
						if(address!=null) autoSelectAddress(address);
		    		});						
				}
			}
			
			tableOther.requestFocus();	
			//tableOther.refresh();
		});

		tableWatched.setAddAddressHandler(()->{
			
			// FIXME Ошибочно импортированные образующие адреса маркетов зашифрованные транзы от чужаков не ловят (так как они на пубкей а не на адресс)
			// Финансовые ловит (что и должно быть для наблюдаемых адресов).
			
			TextFieldsForm addAddress=new TextFieldsForm(tableWatched,
					L10n.t("addressAlias"),
					L10n.t("importedPublicKey") 										    );
			addAddress.showAndWait();
			
			if(addAddress.getResult()!=null) {
				final String account=addAddress.getResult()[0];
				final String addressID=addAddress.getResult()[1];
				
				
				
				if(addressID!=null && addressPattern.matcher(addressID).find()) {		
					if(controller!=null) {
						jobInfo.progress("Blockchain scanning...");
						Threads.runNow(()->{
							final String address=controller.importAddress(addressID.trim() , account!=null ? account: "" );						
							if(address!=null) autoSelectAddress(address);
			    		});						
					}
				}
				else if(addressID!=null) {
					if(controller!=null) {
						jobInfo.progress("Blockchain scanning...");
						Threads.runNow(()->{
							final String address=controller.importPubKey(addressID.trim() , account!=null ? account: "" );						
							if(address!=null) autoSelectAddress(address);
			    		});						
					}					
				}
			}
			
			tableWatched.requestFocus();	
			//tableWatched.refresh();
		});
		
		
	}
	
	
	/////////////////////////////////////////////////////////// Интерфейс /////////////////////////////////////////////////////
	
	public AddressBookModel getItemByAddress(String address) {
		AddressBookModel ret=null;
		
		if( (ret=tableMine.getItemByAddress(address)) !=null) return ret;
		if( (ret=tableOther.getItemByAddress(address)) !=null) return ret;
		if( (ret=tableWatched.getItemByAddress(address)) !=null) return ret;
		
		return ret;
	}
	
	public AddressBookController getController() {return this.controller;}
	
	public void setController(AddressBookController controller) { this.controller=controller;
	
		tableMine.setController(controller);   // Platform.runLater запускает код в порядке размещения (FIFO)
		tableOther.setController(controller);
		tableWatched.setController(controller);
	
	
		if(controller!=null && layoutInit>=0) { // Помним, что слушатели исполняются в стандартном потоке контроллера!
			
			controller.addListener((obj)-> { if(obj instanceof Listener.Error) { // Обработчик ошибок
				
				final Listener.Error err=(Listener.Error)obj;	// final заставляет захватить локальную err до момента вызова лямбды со ссылкой на нее		
				Platform.runLater(()->{ 						// Platform.runLater() исполняет в потоке javaFX
					jobInfo.alert(err.message);
				});
			}});	

			controller.addListener((obj)-> { if(obj instanceof Boolean) { // Индикация начала загрузки
				
				final Boolean update=(Boolean)obj;
				if(update) {
					Platform.runLater(()->{if(!jobInfo.inProgress()) jobInfo.progress("");});
				}
			}});
				
			
			controller.addListener((obj)-> { if(obj instanceof Listener.AddressBook) {
				
				// Слушатели таблиц сами все обрабатывают
				
				
				Platform.runLater(()->{ 
					jobInfo.progress(null); 
					
					synchronized(this) { // Перевод селекции на новые адреса
						if(autoSelectAddress!=null) {
							ObservableList<AddressBookModel> items;
							try { // Только для запуска finally перед выходом
								items=tableMine.getItems();
								for(AddressBookModel row: items) if(row.getAddress().equals(autoSelectAddress)) {
									tableMine.getSelectionModel().select(row); tableMine.scrollTo(row);
									tableMine.requestFocus();
									return;
								}
								items=tableOther.getItems();
								for(AddressBookModel row: items) if(row.getAddress().equals(autoSelectAddress)) {
									tableOther.getSelectionModel().select(row); tableOther.scrollTo(row);
									tableOther.requestFocus();
									return;
								}
								items=tableWatched.getItems();
								for(AddressBookModel row: items) if(row.getAddress().equals(autoSelectAddress)) {
									tableWatched.getSelectionModel().select(row); tableWatched.scrollTo(row);
									tableWatched.requestFocus();
									return;
								}
							}
							finally {
								autoSelectAddress=null;
							}
						}
					}
					
					LOGGER.console("AddressBook Updated");		// TODO debug
				});
						
			}});
			
			// Все Грузим адресную книгу		
			this.controller.speedUp(); jobInfo.progress(""); 
		}
	}
}
