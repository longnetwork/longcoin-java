package gui;

import util.Css;
import util.L10n;
import util.LOGGER;
import util.Threads;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import gui.AddressBookController.Listener;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableColumn.SortType;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.StackPane;

public class AddressBookTable extends TableView<AddressBookModel> { // Адресная книга с фильтром типа адреса
	
	TableColumn<AddressBookModel, String> topColumn = new TableColumn<>();	// Название зависит от типа фильтра
	TableColumn<AddressBookModel, String> accountsColumn = new TableColumn<>(L10n.t("Aliases"));
	TableColumn<AddressBookModel, String> addressesColumn = new TableColumn<>(L10n.t("Addresses"));
	
	public AddressBookTable() { super();
	
		topColumn.getColumns().addAll(Arrays.asList (accountsColumn,addressesColumn) );
		topColumn.setReorderable(false); accountsColumn.setReorderable(false); addressesColumn.setReorderable(false);
		accountsColumn.setEditable(true);
										 
		accountsColumn.setSortable(true); addressesColumn.setSortable(true);
										 
		this.getColumns().add(topColumn);
										 
		this.setEditable(true);
		this.setTableMenuButtonVisible(true);
		
		
		this.getSortOrder().add(accountsColumn); 	// Стартовая сортировка по псевдонимам адресов
		
		this.getSortOrder().addListener( (ListChangeListener<TableColumn<AddressBookModel, ?>>)(change)->{ 					// Удержании сортировки при кликах
			
			if(change.next() && change.getRemoved().size()!=0 && change.getAddedSubList().size()==0) {
				
				TableColumn<AddressBookModel, ?> column=change.getRemoved().get(change.getRemoved().size()/2); // change.getRemoved().get(0)
				if(column.getSortType()==SortType.ASCENDING) 
					column.setSortType(SortType.DESCENDING);
				else 
					column.setSortType(SortType.ASCENDING);
			
				this.getSortOrder().add(column); // Вернули на место  
			}
			
		});
		
		accountsColumn.setCellValueFactory(new PropertyValueFactory<>("account"));
		accountsColumn.setCellFactory(TextFieldTableCell.forTableColumn());			// Editabled cell
		
		accountsColumn.setOnEditCommit((ev)->{ 
			
			if(controller!=null) {
				final AddressBookModel row=ev.getRowValue();
				final String address=row.getAddress();
				final String oldAccount=ev.getOldValue();
				final String newAccount=ev.getNewValue();
				
				 Threads.runNow(()->{
				
					final String adr=controller.setAccount(address, newAccount);
					
					Platform.runLater(()->{
						if(adr!=null) {
							//controller.speedUp();
							row.setAccount(newAccount);
						}
						else row.setAccount(oldAccount);
						
						this.refresh();
					});
					
				 });
				 
				this.requestFocus();
			}
			
		});
		
		addressesColumn.setCellValueFactory(new PropertyValueFactory<>("address"));
		addressesColumn.setCellFactory( (column) -> new TableCell<>() { // Для применения селекторов CSS
			{
				getStyleClass().add("address-table-cell"); // .book-table .address-table-cell {}
			}
			@Override protected void updateItem(String address, boolean empty) { super.updateItem(address, empty);
				setText(address == null ? "" : address);
				
		    	AddressBookModel row= getTableRow().getItem();
		    	
		    	StringBuilder tip=new StringBuilder();
		    	if(row!=null) {
		    		String addressesStr=row.getAddresses(); if(addressesStr==null) addressesStr=row.getMultisigs(); // или или 
		    		
		    		if(addressesStr!=null) {
		    			String[] addresses=addressesStr.split(",");
		    			for(String addr: addresses) tip.append(addr).append("\n");
		    		}
		    	}
		    	if(tip.length()>0) this.setTooltip(new Tooltip(tip.toString()));
		    	else this.setTooltip(null);
		    	
		    	
	    		if(row!=null) {
	    			if(row.isScript() || row.getMultisigs()!=null)  {
	    				Css.pseudoClassStateSwitch(this, Css.MULTISIG_PCS);
	    				return;
	    			}
	    			
	    			
	    			if(row.getType().equals("ismine")) {
	    				Css.pseudoClassStateSwitch(this, Css.NONE_PCS);
	    				return;
	    			}
	    			
	    			if(row.getType().equals("isother")) {	
	    				// Сохраненные чужие адреса у которых есть публичный ключ имеют возможность дешифровки
	    				// исходящих в их адрес сообщений
	    				if(!row.getPubKey().isBlank()) Css.pseudoClassStateSwitch(this, Css.ENCRYPT_PCS); 		// .address-table-cell:encrypt {}
	    				else Css.pseudoClassStateSwitch(this, Css.NOENCRYPT_PCS);								// .address-table-cell:noencrypt {}
	    				return;
	    			}
	    			
	    			if(row.getType().equals("iswatched")) {
	    				//Css.pseudoClassStateSwitch(this, Css.WATCHED_PCS); 	// .address-table-cell:watched {}
	    				if(!row.getPubKey().isBlank()) Css.pseudoClassStateSwitch(this, Css.ENCRYPT_PCS); 		// .address-table-cell:encrypt {}
	    				else Css.pseudoClassStateSwitch(this, Css.NOENCRYPT_PCS);								// .address-table-cell:noencrypt {}	    				
	    				return;
	    			}
	    		}
	    		
	    		Css.pseudoClassStateSwitch(this, Css.NONE_PCS);
		    }
		});	
		
		
		this.setItems( FXCollections.observableArrayList()); // Доступ через getItems() (not null)	
				
		contextMenuInit();
		
		dragInit();
		
		// layoutInit() в layoutChildren()
		
		this.getStyleClass().add("book-table"); // .book-table {}
	}
	
	volatile String filter="";				   		// volatile дает атомарный доступ (когда можно обойтись без synchronized)
	private AddressBookController controller=null; // Кодить так, чтобы контроллер был разделяемым между несколькими AddressBookTable
	public AddressBookTable(AddressBookController controller) { this();
		this.controller=controller; // setController(this.controller) в layoutChildren() после подготовки всех view
	}
	public AddressBookTable(String name) { this();
		topColumn.setText(name);
	}
	
	// Внутренности TableView будут определены в layoutChildren(), а до этого null (делать проверки)
	StackPane menuButton;
	Label tooltip=new Label(" ");		
	
	void layoutInit() {
		
		// Настраиваем поведение кишок стандартной таблицы
		Node node;
		
		node=this.lookup(".show-hide-columns-button");
		menuButton=node instanceof StackPane ? (StackPane)node : null;
		if(menuButton!=null) {
			menuButton.setCursor(Cursor.HAND);
		}

		node=this.lookup(".show-hide-column-image");
		if(node instanceof StackPane) {
			((StackPane)node).getChildren().setAll(tooltip);
			if(menuButton!=null) {
				tooltip.setMinHeight(USE_PREF_SIZE); 
				tooltip.setMinWidth(USE_PREF_SIZE); 
				
				tooltip.prefHeightProperty().bind(menuButton.heightProperty());
				tooltip.prefWidthProperty().bind(menuButton.widthProperty());
			}
			
			
		}		
		
		
		topColumn.prefWidthProperty().bind(this.widthProperty().subtract(menuButton.widthProperty()).add(-1));
			addressesColumn.prefWidthProperty().bind(topColumn.widthProperty().subtract(accountsColumn.widthProperty()));		
		
		//FIXME - размер шрифта захардкожен
			
		double accountPrefWidth=Css.getTextBounds(" PUBLIC PUBLIC", 15).getWidth();
		double addressPrefWidth=Css.getTextBounds("1GztQxGTKdEFhctBhR38wR8skjqkd4Cqt8", 15).getWidth();
		
		if(accountPrefWidth+addressPrefWidth < topColumn.getWidth()) {
			accountsColumn.setPrefWidth(topColumn.getWidth()-addressPrefWidth);
		}
		else {
			accountsColumn.setPrefWidth(topColumn.getWidth()*accountPrefWidth/addressPrefWidth);
		}
	}
		
	private volatile int layoutInit=-1; // Для доступа к внешнему контейнеру, когда он готов (чтобы настройки внешнего вида не во вне кодить)
	@Override protected void layoutChildren() { super.layoutChildren(); // Вызывается как минимум 1 раз при отображении
		if(layoutInit<0) {layoutInit++; // lookup в конструкторе до загрузки css не работает (а здесь уже работает)
		}
		else if(layoutInit<1) {layoutInit++;
			layoutInit();				// FIXME При первом layoutInit еще setPrefWidth может быть перекрыта внутренне	
			childFormsInit();	
			
			setController(this.controller);
		}
		else if(layoutInit<2) {layoutInit++;
		}
	}
	
	
	ContextMenu contextMenu;
		MenuItem copyAddressMenuItem;
		MenuItem copyAccountMenuItem;
		MenuItem refreshMenuItem;
		SeparatorMenuItem separatorMenuItem;
		MenuItem copyPubKeyMenuItem;
		MenuItem copyPrivKeyMenuItem;
	
	void contextMenuInit() {
		
		contextMenu=new ContextMenu();
		
		copyAddressMenuItem = new MenuItem(L10n.t("Copy Address"));
		copyAddressMenuItem.setOnAction( (ev) -> {
			AddressBookModel row=this.getSelectionModel().getSelectedItem();
			if(row!=null) {
				ClipboardContent content = new ClipboardContent(); content.putString(row.getAddress());
				Clipboard.getSystemClipboard().setContent(content);
			}
		});		
		
		copyAccountMenuItem = new MenuItem(L10n.t("Сopy Alias"));
		copyAccountMenuItem.setOnAction( (ev) -> {
			AddressBookModel row=this.getSelectionModel().getSelectedItem();
			if(row!=null) {
				ClipboardContent content = new ClipboardContent(); content.putString(row.getAccount());
				Clipboard.getSystemClipboard().setContent(content);
			}
		});
		
		refreshMenuItem = new MenuItem(L10n.t("Refresh"));
		refreshMenuItem.setOnAction( (ev) -> {
			if(controller!=null) controller.speedUp();	// Пробуждение потока обновит все таблицы через слушателей
		});
		
		separatorMenuItem=new SeparatorMenuItem();

		copyPubKeyMenuItem = new MenuItem(L10n.t("Сopy Public Key"));
		copyPubKeyMenuItem.setOnAction( (ev) -> {
			AddressBookModel row=this.getSelectionModel().getSelectedItem();
			if(row!=null) {
				ClipboardContent content = new ClipboardContent(); content.putString(row.getPubKey());
				Clipboard.getSystemClipboard().setContent(content);
			}
		});				
		
		copyPrivKeyMenuItem = new MenuItem(L10n.t("Сopy Private Key"));
		copyPrivKeyMenuItem.setOnAction( (ev) -> {
			AddressBookModel row=this.getSelectionModel().getSelectedItem();
			if(row!=null) {
				ClipboardContent content = new ClipboardContent(); content.putString(row.getPrivKey());
				Clipboard.getSystemClipboard().setContent(content);
			}
		});		
		
		
		contextMenu.getItems().addAll( 	copyAddressMenuItem, 
									   	copyAccountMenuItem, 
									   	refreshMenuItem,
									   		separatorMenuItem,
									   	copyPubKeyMenuItem,	
									   	copyPrivKeyMenuItem );
		contextMenu.setOnShowing((ev)->{ // Запретить пункты копирования ключей, если их нет
			AddressBookModel row=this.getSelectionModel().getSelectedItem();
			
			if(row==null) {
				copyAddressMenuItem.setDisable(true);
				copyAccountMenuItem.setDisable(true);
				
				
				copyPubKeyMenuItem.setDisable(true);
				copyPrivKeyMenuItem.setDisable(true);
				return;
			}
			
			copyAddressMenuItem.setDisable(false);
			copyAccountMenuItem.setDisable(false);
			
			if(row.getPubKey().isBlank()) copyPubKeyMenuItem.setDisable(true);
			else copyPubKeyMenuItem.setDisable(false);
			
			if(row.getPrivKey().isBlank()) copyPrivKeyMenuItem.setDisable(true);
			else copyPrivKeyMenuItem.setDisable(false);
			
		});
		
		
		this.setContextMenu(contextMenu);	
	}
	
	void dragInit() {
		this.setOnDragDetected((ev)->{
			AddressBookModel item=this.getSelectionModel().getSelectedItem();
			if (item!=null) {
				Dragboard db = this.startDragAndDrop(TransferMode.COPY);
				
				ClipboardContent content = new ClipboardContent();
			    content.putString(item.getAddress());
			    db.setContent(content);
			}
			ev.consume();
		});
		this.setOnDragDone((ev)->{
			
			ev.consume();
		});
	}
	
	
	interface AddAddressHandler { // Интерфейсы неявно статические
		  void handle();
	}
	AddAddressHandler menuButtonHandler= ()->LOGGER.error("AddAddressHandler not set");
	
	public void setAddAddressHandler(AddAddressHandler handler) {
		menuButtonHandler=handler;
	}
	
	void childFormsInit() {
		if(menuButton!=null) {
			menuButton.addEventFilter(MouseEvent.MOUSE_PRESSED, (ev)-> { ev.consume();// Это меню реагирует на MOUSE_PRESSED.
				if(menuButtonHandler!=null) menuButtonHandler.handle();
			});
		}
	}
		
	
	/////////////////////////////////////////////////////////// Интерфейс /////////////////////////////////////////////////////
	// Если все методы вызываются только из потока javaFX и доступ к Items только из Platform.runLater, то synchronized не нужен
	
	public void setFilter(String filter) {
		this.filter=filter;
	}
	public void setTooltip(String tip) {
		tooltip.setTooltip(new Tooltip(tip));
	}
	
	public AddressBookModel getItemByAddress(String address) { if(address==null) return null;
		// Адреса уникальные в отличии от меток
		for(AddressBookModel item: this.getItems()) if(address.equals(item.getAddress())) return item;
		return null;
	}
	
	public AddressBookController getController() {return this.controller;} // XXX Контроллер разделяемый между View-ами
	
	public void setController(AddressBookController controller) { this.controller=controller; // В конструкторах (до layoutChildren() ) только готовится this.controller 
		
		if(controller!=null && layoutInit>=0) { // Помним, что слушатели исполняются в стандартном потоке контроллера!	
				
			// Обработчик ошибок (цепляется где-то во вне)
	
			
			controller.addListener((obj)-> { if(obj instanceof Listener.AddressBook) {
				
				// final заставляет захватить локальную err до момента вызова лямбды со ссылкой на нее
				// Platform.runLater() исполняет в потоке javaFX
				
				Listener.AddressBook ab=(Listener.AddressBook)obj;
				
				final ObservableList<AddressBookModel> all=FXCollections.observableArrayList(ab);
				final ObservableList<AddressBookModel> filtered=all.filtered((item)->item.filter(filter)); // filter volatile (хапнит атомарно)
				
				Platform.runLater(()->{ // Выполняются последовательно в потоке javaFX
					
					// В объектах унаследованных от Object нужно переопределять equals и hashcode для операций сравнения и хеширования
					// Списки при сличении вызывают эти методы, но порядок играет роль!
					
					long oldHash=0; for(AddressBookModel item: this.getItems()) oldHash+= 0x00000000ffffffffL & item.hashCode(); //Для суммы не важен порядок обхода
					
					long newHash=0; for(AddressBookModel item: filtered) newHash+= 0x00000000ffffffffL & item.hashCode(); //Для суммы не важен порядок обхода
					
					if(oldHash==newHash) return; // Нет смысла обновлять  
					 
					
					int selection=this.getSelectionModel().getSelectedIndex(); 			  
					
					Map<TableColumn<AddressBookModel,?>, SortType> sorts=new HashMap<>(); 
					this.getSortOrder().forEach((column)->{
						sorts.put(column, column.getSortType()); column.setSortable(false); // Ссылки на колонки сохранены
					});
											
						this.getItems().setAll( filtered );
						
					sorts.forEach((column, sortType)->{										// Удержание сортировки при обновлении таблицы
						column.setSortType(sortType);  column.setSortable(true);
					});
					
					this.getSelectionModel().select(selection); 							// Удержание селекции при обновлении таблицы
					
					
					LOGGER.info("AddressBook "+topColumn.getText()+" updated");
					LOGGER.console("AddressBook "+topColumn.getText()+" updated"); // TODO debug
				});

			}});				
			
			
			
			this.controller.speedUp(); 
		}
	}
}
