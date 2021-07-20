package gui;

import util.Css;
import util.L10n;
import util.LOGGER;
import gui.TransactionsController.Listener;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.Transition;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.EventTarget;
import javafx.geometry.Insets;
import javafx.scene.CacheHint;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.skin.VirtualFlow;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.web.WebView;
import javafx.util.Duration;

public class TransactionsView extends TableView<TransactionModel> { // Скроллируемый список транзакцый
	
	static final Pattern COLOR_STRING_PATTERN= Pattern.compile("(?i)0x[0-9a-f]+");
	
	static final Color UPDATE_COLOR=Css.getColor("-fx-update-color");
	static final Color BACKGROUND_COLOR=Css.getColor("-fx-background-default");
	
	static final int ROWS_MAX=47;					// Число транзакций на одной страници
	int rowsMax=+ROWS_MAX;							// Знак определяет направление просмотра в listtransactions
	volatile int txStart=0; //8180;	//TODO debug	// Текущая позиция скроллинга (по RPC)
	volatile int txLimit=txStart+ROWS_MAX;	    	// Конец транзакций (по RPC) в странице скролла
	String txFilter="";
	
	int blcount=0;								// Счетчик блоков
	int connections=0;
	double synchronization=0.0;
	
	static final double MIN_CONTENT_HEIGHT = Css.getTextBounds("┃⟺",14).getHeight()*1.75;
	
	// Items будет ObservableList<TransactionModel>
		
		TableColumn<TransactionModel, Double> statusColumn = new TableColumn<>(L10n.t("Status"));
		TableColumn<TransactionModel, String> typeColumn = new TableColumn<>(L10n.t("Type"));
		TableColumn<TransactionModel, /*LocalDateTime*/ String> dateColumn = new TableColumn<>(L10n.t("Date"));
		TableColumn<TransactionModel, String[]> labelsColumn = new TableColumn<>(L10n.t("Aliases")); // Будет биндится на "from", "to"
		TableColumn<TransactionModel, String> contentColumn = new TableColumn<>(L10n.t("Content"));
		TableColumn<TransactionModel, Double> amountColumn = new TableColumn<>(L10n.t("Amount"));
		
	JobInfo jobInfo;	   // Это индикация длительных операций скролла и текущего среза транзакций 
		
	public TransactionsView() { super();
	
		this.getColumns().addAll( Arrays.asList( statusColumn, typeColumn, dateColumn, labelsColumn, contentColumn, amountColumn) );
			statusColumn.setSortable(false); statusColumn.setReorderable(false);
			typeColumn.setSortable(false); typeColumn.setReorderable(false);
			dateColumn.setSortable(false); dateColumn.setReorderable(false);
			labelsColumn.setSortable(false); labelsColumn.setReorderable(false);
			contentColumn.setSortable(false); contentColumn.setReorderable(false); contentColumn.getStyleClass().add("content-column"); // .content-column
			amountColumn.setSortable(false); amountColumn.setReorderable(false);
			
		this.setEditable(false);
		this.scrollTo(Integer.MAX_VALUE);
		this.setTableMenuButtonVisible(true);
		this.setRowFactory( (table)->new TableRow<>() {	// Чтобы выделять начало транзакций в странице скролла специальным фоном
			{
				this.setMinHeight(MIN_CONTENT_HEIGHT);
			}
			@Override protected void updateItem(TransactionModel item, boolean empty) { super.updateItem(item, empty);
				if(item==null || empty) Css.pseudoClassStateSwitch(this,Css.EMPTY_PCS); // .tx-view .table-row-cell:empty {}
				else Css.pseudoClassStateSwitch(this,Css.NONE_PCS);
			}
		}); 
		//this.setFocusTraversable(false);
		this.getSelectionModel().selectedIndexProperty().addListener((obs, oldIndex, newIndex)->{
			
			if(this.isDisabled()) {
				this.getSelectionModel().select(null);
				return;
			}
			
			final int idx=newIndex!=null ? newIndex.intValue(): -1;
			this.getFocusModel().focus(idx); // Фокус следует за селекцией
			
			this.requestFocus();
			
		});
	
		statusColumn.setCellFactory(ProgressIndicatorTableCell.forTableColumn());
		statusColumn.setCellValueFactory(new PropertyValueFactory<>("confirmations")); // Это точные имена свойств в TransactionModel (юзается рефлексия)
		
		typeColumn.setCellFactory( (column) -> new TableCell<>() { // Для индикации разных типов транзакций через CSS
			{
				getStyleClass().add("type-table-cell"); // .tx-view .type-table-cell {}
			}
			@Override protected void updateItem(String info, boolean empty) { super.updateItem(info, empty);
		    	setText(info == null ? "" : info);
	    		TransactionModel row= getTableRow().getItem();
	    		if(row!=null && row.isDataTx()) {
	    			
	    			if( !row.isDecryption() || !row.isTrusted() ) Css.pseudoClassStateSwitch(this, Css.NOTRUSTED_PCS);	// .type-table-cell:nodecrypt .text {
	    			else if(row.isEncryption())  Css.pseudoClassStateSwitch(this, Css.ENCRYPT_PCS); 					// .type-table-cell:encrypt {}
	    			else  					     Css.pseudoClassStateSwitch(this, Css.NOENCRYPT_PCS);	    			// .type-table-cell:noencrypt {}
	    		}
	    		if(row==null || !row.isDataTx()) Css.pseudoClassStateSwitch(this, Css.NONE_PCS);
		    }
		});		
		typeColumn.setCellValueFactory(new PropertyValueFactory<>("category")); // Забинжено на поле в TransactionModel которое может меняться со временем
		
		//dateColumn.setCellValueFactory(new PropertyValueFactory<>("time")); // Найдет в модели через рефлексию
		dateColumn.setCellValueFactory((cellData)-> {
			
			if(cellData.getValue()==null) return null;
			
			String date=cellData.getValue().getTime().toLocalDate().toString();
			String time=cellData.getValue().getTime().toLocalTime().toString();
			return new ReadOnlyStringWrapper(date+"\n "+time);
		});
		
		//labelsColumn.setCellFactory(LabelsBoxTableCell.forTableColumn());
		labelsColumn.setCellFactory((column)-> new LabelsBoxTableCell<>() {
			@Override public void updateItem(String[] item, boolean empty) { super.updateItem(item, empty);
				
				if(item!=null && !empty) {
					StringBuilder tip = new StringBuilder("");
					for(String s: item) tip.append(s).append("\n");
					this.setTooltip(new Tooltip(tip.toString()));
				}
				else this.setTooltip(null);
		    }
		});
		labelsColumn.setCellValueFactory(LabelsBoxTableCell.forValueFactory("from","to")); // .labels-box-table-cell #from.label , #to.label  {Заебок сделал!}
		
		contentColumn.setCellFactory(WebViewTableCell.forTableColumn());
		contentColumn.setCellValueFactory(new PropertyValueFactory<>("content"));
		
		amountColumn.setCellFactory( (column) -> new TableCell<>() {
			{
				getStyleClass().add("amount-table-cell"); // .tx-view .amount-table-cell {}
			}
		    @Override protected void updateItem(Double amount, boolean empty) { super.updateItem(amount, empty);
		    	if(amount==null) setText(""); 
		    	else {	
		    		setText(String.format("%12.0f",amount)); // %xx - ширина всего поля на все знаки числа
		    		if(amount>0) Css.pseudoClassStateSwitch(this, Css.POSITIVE_PCS);
		    		if(amount<0) Css.pseudoClassStateSwitch(this, Css.NEGATIVE_PCS);
		    		if(amount==0) Css.pseudoClassStateSwitch(this, Css.NONE_PCS);
		    	}
		    }
		});
		amountColumn.setCellValueFactory(new PropertyValueFactory<>("amount"));
		
		
		this.setItems( FXCollections.observableArrayList()); // Доступ через getItems() (not null)	
		
		contextMenuInit();
		
		jobInfo=new JobInfo(true); 
		this.getChildren().addAll(jobInfo); // Рисуется поверх родителя
        
		// layoutInit() в layoutChildren()
		
		this.getStyleClass().add("tx-view"); // .tx-view {}
	
	}
	private TransactionsController controller=null;	// Контроллер не может быть разделяемым, так как принимает на вход фильтр и пагинацию
	public TransactionsView(TransactionsController controller) { this();
		this.controller=controller; // setController() требует завершения всех инициализаций (вызов в layoutChildren)
	}

	// Внутренности TableView будут определены в layoutChildren(), а до этого null (делать проверки)
	Label placeholder;
	ScrollBar verticalBar; VirtualFlow<?> flow;
	StackPane menuButton;
		Label envelope=new Label("✉"); // Для перекрытия кнопки меню таблицы
			Animation envelopeAnimation;
	Label contentLabel;
		TextField filter;
		String getFilter() { return filter!=null ? filter.getText() : ""; }
			
	void layoutInit() {
		
		/*{	// TODO debug
			Set<Node> nodes=this.lookupAll("*");
			for(Node node: nodes) {
				LOGGER.console(node.toString()); 
				if(node instanceof Parent) {
					((Parent)node).getChildrenUnmodifiable().forEach((n)->{
						LOGGER.console(" - "+n.toString()+" class: "+n.getClass().getName());
					});
				}
			}
		}*/
		
		// Настраиваем поведение кишок стандартной таблицы
		Node node;
		
		node=this.lookup(".placeholder .label");
		placeholder=node instanceof Label ? (Label)node : null;
		if(placeholder!=null) {
			placeholder.setId("placeholder");  //.tx-view #placeholder.label {}
			placeholder.setText("");
		}		
		
		node=this.lookup(".content-column .label"); // .content-column свой стиль, чтобы найти его здесь
		contentLabel=node instanceof Label ? (Label)node : null;
		if(contentLabel!=null) {
			contentLabel.setCursor(Cursor.HAND); // Кликабельные элементы указаны соответствующим курсором
			contentLabel.setTooltip(new Tooltip(L10n.t("Set filter")));
		}
	
		node=this.lookup(".scroll-bar:vertical");
		verticalBar=node instanceof ScrollBar ? (ScrollBar)node : null;
		if(verticalBar!=null) {
			// FIXME: Нужно как-то включить постоянное отображение вертикального скрола
		}
		
		node=this.lookup(".scroll-bar:horizontal");
		if(node instanceof ScrollBar) {
			// FIXME: Нужно как-то выключить горизонтальный скрол совсем
			((ScrollBar) node).setMaxHeight(0);
			((ScrollBar) node).setMinHeight(0);
			((ScrollBar) node).setPrefHeight(0);
		}
		
		node=this.lookup(".thumb");
		if(node instanceof Node) {
			Tooltip.install(node, new Tooltip(L10n.t("PageUp/Down - fast scroll")));
		}	
		
		node=this.lookup(".virtual-flow");
		flow=node instanceof VirtualFlow<?> ? (VirtualFlow<?>)node : null;
		if(flow!=null) {

		}
		
		node=this.lookup(".show-hide-columns-button");
		menuButton=node instanceof StackPane ? (StackPane)node : null;
		if(menuButton!=null) {
			menuButton.addEventFilter(MouseEvent.MOUSE_PRESSED, (ev)-> { ev.consume();
				// Вырубили меню сокрытия колонок (оставив проход событий для tooltip и клика на envelope)
				// Это меню реагирует на MOUSE_PRESSED. FIXME для envelope остается MOUSE_CLICKED
			}); 
			menuButton.setMinWidth(envelope.getWidth());	
		}
		
		node=this.lookup(".show-hide-column-image");
		if(node instanceof StackPane) {
			envelope.setCursor(Cursor.HAND); envelope.setId("envelope"); // .tx-view #envelope.label {}
			envelope.setTooltip(new Tooltip(L10n.t("Content creation")));
			
			((StackPane)node).setBackground(null); 		// Убили крестик
			((StackPane)node).setMinWidth(envelope.getWidth());
			((StackPane)node).getChildren().setAll(envelope); // Своя метка  (фон прозрачный)
		}

		/* при TableView.CONSTRAINED_RESIZE_POLICY стартовые размеры колонок удавалось установить в layoutChildren() 
		   через resizeColumn и то - криво, а биндигами - заебок! */
		//tableView.prefWidthProperty().bind(this.widthProperty());
		//tableView.prefHeightProperty().bind(this.heightProperty());
		
		contentColumn.prefWidthProperty().bind( Bindings.createDoubleBinding(() -> {
			
			double contentWidth=this.getWidth()-
							    envelope.getWidth()-
							    statusColumn.getWidth()-
							    typeColumn.getWidth()-
							    dateColumn.getWidth()-
							    labelsColumn.getWidth()-
							    amountColumn.getWidth()-2;
			
			//double Z=this.getScaleX();
			double P=this.getPadding().getLeft()+this.getPadding().getRight();
			
			
			//return contentWidth+this.getWidth()*(1-Z)/Z; // Учитываем Режим вписывания промасштабированной ноды за счет padding-гов
			return contentWidth-P;
					
		}, this.widthProperty(),
		   envelope.widthProperty(), 
		   statusColumn.widthProperty(),
		   typeColumn.widthProperty(),
		   dateColumn.widthProperty(),
		   labelsColumn.widthProperty(),
		   // Ширина контента зависима от остальных
		   amountColumn.widthProperty(),
		   
		   this.scaleXProperty(),
		   this.paddingProperty()
		)); 	
		
		// Начальные комфортные ширины. FIXME - размер шрифта захардкожен
		statusColumn.setPrefWidth( Css.getTextBounds("...", 16).getWidth()*2 );
		typeColumn.setPrefWidth( Css.getTextBounds("receive", 15).getWidth() );
		dateColumn.setPrefWidth( Css.getTextBounds("2020-12-31", 15).getWidth() );
		labelsColumn.setPrefWidth( Css.getTextBounds(" PUBLIC", 13).getWidth()*2 );
		// Ширина контента зависима от остальных
		amountColumn.setPrefWidth( Css.getTextBounds("-9999999999", 12).getWidth() );
		
		envelopeAnimation=new Transition() {
			Color colorBackground;	   // Интерполируется до ALERT_BLINK_COLOR туда-сюда
			Background origBackground; // В самом начале до отображения и загрузки стилей не определено
			CornerRadii radii=new CornerRadii(2); // FIXME harcoded
			Insets insets= new Insets(0,3,0,3); // FIXME harcoded
			
            {
            	envelope.setCacheHint(CacheHint.QUALITY);
            	
                setCycleDuration(Duration.millis(1000)); setInterpolator(Interpolator.EASE_BOTH);
                setCycleCount(4); setAutoReverse(true);
                
                setOnFinished((ev)->envelope.setBackground(origBackground));   
            }
            @Override protected void interpolate(double frac) {
            	Color vColor = colorBackground.interpolate(UPDATE_COLOR, frac);
            	envelope.setBackground(new Background(new BackgroundFill(vColor, radii, insets)));
            }
            @Override public void play() {
            	if(getStatus()==Status.STOPPED) {
            		colorBackground=BACKGROUND_COLOR; // по умолчанию
            		origBackground=envelope.getBackground();
            		
            		if(origBackground!=null)
	            		try {		
	            			final String bg=origBackground.getFills().get(0).getFill().toString();
	            			
	            			final Pattern p = COLOR_STRING_PATTERN;
	            	        final Matcher m = p.matcher(bg);  
	            	        
	            	        if (m.find()) {
	            	        	final String colorStr=bg.substring(m.start(), m.end());
	            	        	colorBackground=Color.valueOf(colorStr);
	            	        }
	            		}
	            		catch(IndexOutOfBoundsException e) {
	            			colorBackground=BACKGROUND_COLOR;
	            		}
            	}
            	super.play();
            }
        };
		
        
	}
	
	private volatile EnumSet<ScrollState> scrollState=EnumSet.noneOf(ScrollState.class); // Именованные битовые поля
	enum ScrollState {PAGINATION_UP, PAGINATION_DOWN};
	
	void scrollInit() {
		if(verticalBar!=null) {
			
			verticalBar.visibleAmountProperty().addListener((obs, oldAmount, newAmount)->{ // Удерживаем бегунок в фиксированых размерах
				if(Math.abs((double)newAmount - 2.5/ROWS_MAX) > Double.MIN_NORMAL) verticalBar.setVisibleAmount(2.5/ROWS_MAX);
			});
			verticalBar.addEventFilter(MouseEvent.MOUSE_DRAGGED, (ev)->{ // Уплавняем скролл через скролбар
				EventTarget et=ev.getTarget(); // source is ScrollBar
				if(et instanceof Node && ((Node)et).getStyleClass().toString().equals("thumb")) { ev.consume();
					synchronized(this.getItems()) {
						try {
							
								final double thumbHeight=verticalBar.getVisibleAmount();
							
								double pos=ev.getY()/(verticalBar.getBoundsInLocal().getHeight()); // относительно source (0 - вверху)
							
								pos=(pos-thumbHeight)/(1-2*thumbHeight); // FIXME thumbHeight должен быть всегда меньше 0.5
							
								if(pos<0) pos=0; if(pos>1.0) pos=1.0;
								
								verticalBar.setValue(pos);
						}
						catch(ArithmeticException ignore) {}
					}
				}
			});		
			verticalBar.addEventFilter(MouseEvent.MOUSE_PRESSED, (ev)->{ // Пагинация от стрелок скроллбара
				EventTarget et=ev.getTarget();
				if(et instanceof Node) { 
					
					if( ((Node)et).getStyleClass().toString().equals("decrement-arrow") ||
						((Node)et).getStyleClass().toString().equals("decrement-button") ) { ev.consume();
						//if(scrollState.contains(ScrollState.PAGINATION_UP) || scrollState.contains(ScrollState.PAGINATION_DOWN)) return;
						
						paginationUP();
						return;
					}
					
					if( ((Node)et).getStyleClass().toString().equals("increment-arrow") ||
						((Node)et).getStyleClass().toString().equals("increment-button") ) { ev.consume();
						//if(scrollState.contains(ScrollState.PAGINATION_UP) || scrollState.contains(ScrollState.PAGINATION_DOWN)) return;
							
						paginationDOWN();
						return;
					}					
				}
	        });	
		}
		
		this.addEventFilter(KeyEvent.KEY_PRESSED,(ev)->{ // Пагинация от клавиш
			KeyCode k=ev.getCode();
			if(k==KeyCode.PAGE_UP || k==KeyCode.PAGE_DOWN || k==KeyCode.UP || k==KeyCode.DOWN) { ev.consume(); // Стандартная реакция должна быть подавлена	
			
				if(k==KeyCode.PAGE_UP) {paginationUP(); return;}
				if(k==KeyCode.PAGE_DOWN) {paginationDOWN(); return;}
				
				synchronized(this.getItems()) {
					int selected=this.getSelectionModel().getSelectedIndex();
					int size=this.getItems().size();
					if(size<=0) this.getSelectionModel().select(-1);
				
					if(k==KeyCode.UP) {
						int idx=selected>0 ? --selected : 0;
						this.getSelectionModel().select( idx );
						this.scrollTo( idx );
					}
					if(k==KeyCode.DOWN) {
						int idx=(selected<size-1 ? ++selected : size-1);
						this.getSelectionModel().select( idx );
						this.scrollTo( idx );
					}
				}				
			
				return;
			}
		});
		
	}

	
	private void paginationUP() {
		synchronized(this.getItems()) { if(controller==null) return;	
			
			if(Math.abs(verticalBar.getValue() - 0.0) > 0.5/ROWS_MAX) { // Ползунок не вверху - откручиваемся на верх без пагинации
				verticalBar.setValue(0.0); 	this.getSelectionModel().select(0);
				return;
			}
			
			if(this.getItems().size()<ROWS_MAX || (this.getItems().get(0) == null && this.getItems().get(ROWS_MAX-1) != null) ) return; // нечего пагенировать (выше ничего нет)
			
			int delta=Math.abs(txLimit-txStart);
			
			if(rowsMax<0) { // Контроллер работает вверх (txStart - низ среза)
				
				rowsMax= +ROWS_MAX; if( (txStart-=delta) < 0 ) txStart=0; 
			} 
			
			// Ползунок вверху - возможна пагинация с переводом скролла в самый низ
				
			if( (txStart+=(delta-1)) <0) txStart=0; // Оставляем одну транзакцию, чтобы попасть на нее внизу.
			
			txLimit=txStart+ROWS_MAX; //Сброс на случай быстрого скролла
	
			LOGGER.console(txStart+" transactions ago"); // TODO debug
			
			if(controller.setTransactionsSlice(txStart, rowsMax, getFilter())>0) {
					jobInfo.progress(txStart+" "+L10n.t("Transactions ago")); 
	
			  scrollState.add(ScrollState.PAGINATION_UP); // Флаг для перевода скролла вниз по завершении чтения RPC
			}
		}
	}
	private void paginationDOWN() {
		synchronized(this.getItems()) { if(controller==null) return;	
		
			if(Math.abs(verticalBar.getValue() - 1.0) > 0.5/ROWS_MAX) { // Ползунок не внизу - откручиваемся вниз без пагинации
				verticalBar.setValue(1.0); this.getSelectionModel().select(this.getItems().size()-1);
				return;
			}		
			
			if(this.getItems().size()<ROWS_MAX || (this.getItems().get(ROWS_MAX-1) == null && this.getItems().get(0) != null)) return; // нечего пагенировать (ниже ничего нет)
			
			int delta=Math.abs(txLimit-txStart);
			
			if(rowsMax>0) { // Контроллер работает вниз (txStart - верх среза)
				
				rowsMax= -ROWS_MAX; txStart+= delta;
			} 
			
			// Ползунок внизу - возможна пагинация с переводом скролла в самый верх
			
			if( (txStart+=(-delta+1)) < ROWS_MAX ) txStart=ROWS_MAX;
			
			txLimit=txStart-ROWS_MAX; //Сброс на случай быстрого скролла
			
			LOGGER.console(txStart+" transactions ago"); // TODO debug
			
			if(controller.setTransactionsSlice(txStart, rowsMax, getFilter())>0) {
					jobInfo.progress(txStart+" "+L10n.t("Transactions ago")); 
	
			  scrollState.add(ScrollState.PAGINATION_DOWN); // Флаг для перевода скролла вверх по завершении чтения RPC
			}
		} 
	}
		
	private volatile int layoutInit=-1; // Для доступа к внешнему контейнеру, когда он готов (чтобы настройки внешнего вида не во вне кодить)
	@Override protected void layoutChildren() { super.layoutChildren(); // Вызывается как минимум 1 раз при отображении
		if(layoutInit<0) {layoutInit++; // lookup в конструкторе до загрузки css не работает (а здесь уже работает)
			layoutInit();
			scrollInit();
			childFormsInit();
			
			setController(this.controller);
		}
		else if(layoutInit<1) {layoutInit++;
		}
		else if(layoutInit<2) {layoutInit++; 
		}
	}
	
	
	ContextMenu contextMenu;

	MenuItem refreshMenuItem;
	MenuItem detailsMenuItem;
	MenuItem contentMenuItem;
	SeparatorMenuItem separatorMenuItem;
	MenuItem replyMenuItem;
	MenuItem sendMenuItem;
	
	void contextMenuInit() {
		this.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
		this.getSelectionModel().setCellSelectionEnabled(false);
		
		contextMenu=new ContextMenu();
		
		refreshMenuItem = new MenuItem(L10n.t("Refresh"));
		refreshMenuItem.setOnAction( (ev) -> {
			synchronized(this.getItems()) {
				this.refresh();
			}
		});
		
		detailsMenuItem = new MenuItem(L10n.t("Details"));
		detailsMenuItem.setOnAction( (ev) -> {
			synchronized(this.getItems()) {
				TransactionModel row=this.getSelectionModel().getSelectedItem();
				if(row!=null) {
					TextViewForm textForm=new TextViewForm(this);
					textForm.setText(row.toString());
					textForm.show(); //textForm.showAndWait();
				}
			}
		});

		contentMenuItem = new MenuItem(L10n.t("Content"));
		contentMenuItem.setOnAction( (ev) -> {
			synchronized(this.getItems()) {
				TransactionModel row=this.getSelectionModel().getSelectedItem();
				if(row!=null) {
					WebViewForm webForm=new WebViewForm(this);
					webForm.setContent(row.getContent());
					webForm.show(); //webForm.showAndWait();
				}
			}
		});
		
		separatorMenuItem=new SeparatorMenuItem();
		
		replyMenuItem = new MenuItem(L10n.t("Reply"));
		replyMenuItem.setOnAction( (ev) -> {
			synchronized(this.getItems()) {
				TransactionModel row=this.getSelectionModel().getSelectedItem();
				if(row!=null) {
					String from=null, to=null;
					
					if(!row.isDataTx()) { // В финансовой транзакции для категории receive есть только адрес куда пришли мани - его берем во from
						from=row.getAddress();
						
						if(row.isWatchonly()) from=null; // При ловле транзы на наблюдаемом адрессе юзер не может брать его в качестве своего
					}
					else { // Стараемся для шифрации получить публичные ключи из транзакции помятуя, что from для ответа берется из to, а to из from
						to=row.getFromPubKey(); if(to.isBlank()) to=row.getFromAddress();
						from=row.getToPubKey(); if(from.isBlank()) from=row.getToAddress();
						
						if(row.isWatchonly()) from=null; // При ловле транзы на наблюдаемом адрессе юзер не может брать его в качестве своего
					}
					
					PostingEditorForm editor=new PostingEditorForm(this,from,to);
					//editor.setContent(null);
					editor.show();					
				}
			}
		});
		
		sendMenuItem = new MenuItem(L10n.t("Send"));
		sendMenuItem.setOnAction( (ev) -> {
			synchronized(this.getItems()) {
				TransactionModel row=this.getSelectionModel().getSelectedItem();
				if(row!=null) {
					String from=null, to=null;
					
					if(!row.isDataTx()) { // В финансовой транзакции для категории send есть только адрес to, а from не определен (списания со многих адресов)
						to=row.getAddress();
					}
					else { // Стараемся для шифрации получить публичные ключи из транзакции
						from=row.getFromPubKey(); if(from.isBlank()) from=row.getFromAddress();
						to=row.getToPubKey(); if(to.isBlank()) to=row.getToAddress();
						
						if(row.isWatchonly()) from=null;
					}
					
					PostingEditorForm editor=new PostingEditorForm(this,from,to);
					//editor.setContent(null);
					editor.show();
				}
			}
		});		
		
		
		
		contextMenu.getItems().addAll(refreshMenuItem, detailsMenuItem, contentMenuItem, separatorMenuItem, replyMenuItem, sendMenuItem);
		contextMenu.setOnShowing((ev)->{
			synchronized(this.getItems()) {
				TransactionModel row=this.getSelectionModel().getSelectedItem();
				if(row==null) {
					detailsMenuItem.setDisable(true);
					contentMenuItem.setDisable(true);
					replyMenuItem.setDisable(true);
					sendMenuItem.setDisable(true);
				}
				else {
					detailsMenuItem.setDisable(false);
					contentMenuItem.setDisable(false);
					replyMenuItem.setDisable(false);
					sendMenuItem.setDisable(false);
					
					if(!row.isDataTx()) contentMenuItem.setDisable(true);
					
					if(!row.getCategory().equals("receive")) replyMenuItem.setDisable(true);
					if(!row.getCategory().equals("send")) sendMenuItem.setDisable(true);
						
				}
			}
		});
		
		
		this.setContextMenu(contextMenu);
		this.addEventFilter(MouseEvent.MOUSE_PRESSED, (ev)-> { // Чтобы не перекрывало контекстное меню постингов
			EventTarget et=ev.getTarget();
			if(et instanceof WebView) this.setContextMenu(null);
			else this.setContextMenu(contextMenu);
		});		
	}
	
	void childFormsInit() {
		
		if(contentLabel!=null) {
			filter= new TextField();
			
			filter.prefHeightProperty().bind(contentLabel.heightProperty());
			filter.prefWidthProperty().bind(contentLabel.widthProperty());
			
			filter.setPromptText(L10n.t("Set filter"));
			
			filter.setOnKeyPressed((ev)->{
				if(ev.getCode()==KeyCode.ENTER) {
					synchronized(this.getItems()) {
					
						String s=filter.getText(); // Никогда не null
						
						if(s.isBlank()) contentLabel.setText(L10n.t("Content"));
						else contentLabel.setText(L10n.t("Content")+" ("+s+")");					
						
						contentLabel.setGraphic(null); // Выключить поле редактирования
						
						txStart=0; rowsMax=+ROWS_MAX; txLimit=txStart+ROWS_MAX;
						
						if(controller.setTransactionsSlice(txStart, rowsMax, getFilter())>0) 
							jobInfo.progress(txStart+" "+L10n.t("Transactions ago"));
					}
				}
			});
			
			
			contentLabel.setOnMousePressed((ev)->{ ev.consume();
			
				contentLabel.setGraphic(filter); // Включить поле редактирование
				
				//filter.setEditable(true);
				
				contentLabel.requestFocus();
			});
		}
		
		if(envelope!=null) envelope.setOnMouseClicked((ev)->{			
			PostingEditorForm editor=new PostingEditorForm(this);
			//editor.setContent(null);
			editor.show();
		});
	}
	
	
	
	
	/////////////////////////////////////////////////////////// Интерфейс /////////////////////////////////////////////////////

	public void envelopeBlink() {if(envelopeAnimation!=null) envelopeAnimation.play();}
	
	public TransactionsController getController() {return this.controller;} // XXX У каждого view свой НЕ разделяемый контроллер
	
	public void setController(TransactionsController controller) { this.controller=controller;
		if(controller!=null && layoutInit>=0) { // Помним, что слушатели исполняются в стандартном потоке контроллера!
			
			controller.addListener((obj)-> { if(obj instanceof Listener.Error) { // Обработчик ошибок
				
				final Listener.Error err=(Listener.Error)obj;	// final заставляет захватить локальную err до момента вызова лямбды со ссылкой на нее		
				Platform.runLater(()->{ // Platform.runLater() исполняет в потоке javaFX
					
					jobInfo.alert(err.message);
				
				});
			}});

			controller.addListener((obj)-> { if(obj instanceof Listener.Status) { // Блокчейн-информация
				
				final Listener.Status status=(Listener.Status)obj;
				Platform.runLater(()->{
					
					if(status.blocks>blcount) { // Мигание при рождение нового блока в сети
						blcount=status.blocks; envelopeBlink();
						
						LOGGER.info("New Bloks");
					}
					
					// FIXME пока не используется
					synchronization=status.synchronization; 
					connections=status.connections;
					
				});				
			}});			
	
			controller.addListener((obj)-> { if(obj instanceof Boolean) { // Индикация начала загрузки транз
				
				final Boolean update=(Boolean)obj;
				if(update) 
					Platform.runLater(()->{
						if(!jobInfo.inProgress()) jobInfo.progress("");
					});			
			}});			
			
			controller.addListener((obj)-> { if(obj instanceof Listener.SliceTx) { // Срез транзакций
				
				final Listener.SliceTx slice=((Listener.SliceTx)obj);	// В порядке от старых к новым
				final int rStart, sStart, rStop, sStop; 			    // Для захвата лямбдой
				
				// В конечном итоге в rows ( this.getItems() ) должен быть весь slice, но чтобы не было пере-рендеринга, обновлять  нужно
				// только изменившейся части, а остальные части не трогать (ну кроме полей confirmation и category)
				
			    // Ищем совпадения. Всего 4 варианта: 
				// - rows выше slice - обрезка rows вначале, наращивание в конце;
				// - rows ниже slice - обрезка rows в конце, наращивание вначале;  
				// - rows внутри slice - наращивание rows с концов;
				// - rows объемлет slice - обрезка rows с концов;
				
				// Работа над rows и последующее обновление в Platform.runLater() должны быть синхронизированы
				synchronized(this.getItems()) { // Это поток контроллера
					
					ObservableList<TransactionModel> rows = this.getItems(); // Здесь будем только читать
					
					int r_size=rows.size(); int s_size=slice.size(); int r_start=0, s_start=0; int r_stop=0, s_stop=0;
					// Фаза 1 - поиск начала совпадения
			L1:		for(r_start=0; r_start<r_size; r_start++ ) {
						final TransactionModel row=rows.get(r_start);
						for(s_start=0; s_start<s_size; s_start++) 
							if( slice.get(s_start).equals(row) ) break L1; 
					}
					// Фаза 2 - поиск конца совпадения
			L2:		for(r_stop=r_start, s_stop=s_start; r_stop<r_size && s_stop<s_size; r_stop++, s_stop++) {
						final TransactionModel row=rows.get(r_stop);
						if( ! slice.get(s_stop).equals(row) ) break L2;
					}
					
					rStart=r_start; sStart=s_start; rStop=r_stop; sStop=s_stop;
				}
				
				Platform.runLater(()->{
					
					envelopeBlink(); // Изменение списка транз

					synchronized(this.getItems()) { // Это поток JavaFX - код планируется к выполнению и к моменту выполнения listener снова может быть вызван
						
						ObservableList<TransactionModel> rows = this.getItems();
						
						int rSize=rows.size(); int sSize=slice.size();
						
						if(rows.isEmpty()) { // Здесь все просто - забиваем тем что есть
							rows.setAll(slice);
						}
						else {			
							for(int i=rStart, j=sStart; i<rStop && j<sStop; i++, j++) { // Обновляем confirmation и category и метки аккаунтов
								if(rows.get(i)!=null) {									// FIXME из-за того что они не входят в equals и hashCode
									rows.get(i).setConfirmations(slice.get(j).getConfirmations() );
									rows.get(i).setCategory( slice.get(j).getCategory() );
									
									rows.get(i).setFrom( slice.get(j).getFrom() );
									rows.get(i).setTo( slice.get(j).getTo() );
								}
							}							
							
							if(rStop<rSize) rows.subList(rStop, rSize).clear(); // Обрезали снизу старье (с конца)
							if(rStart>0) rows.subList(0, rStart).clear(); 	    // Обрезали сверху старье (с начала). После операции индексы сместились на -rStart
							
							if(sStart>0 ) { // Напихали сверху
								rows.addAll(0, slice.subList(0, sStart));
							}
							if(sStop<sSize) { // Добавили снизу
								rows.addAll(slice.subList(sStop, sSize));
							}
						}
						
						if(rows.size()<ROWS_MAX) {
							// Нужен эффект пустоты, когда при пагинации остается мало транз.
							// Чтобы был скролл и было видно куда пагенировать дальше
							if(slice.limit>slice.start) rows.addAll( 0, Collections.nCopies(ROWS_MAX-rows.size(), null) ); 
							else rows.addAll( Collections.nCopies(ROWS_MAX-rows.size(), null) ); // Размер страницы пагинации фиксирован на ROWS_MAX
							// Пристыковка пустых строк либо сверху либо снизу
						}
						
						if(scrollState.contains(ScrollState.PAGINATION_UP)) { scrollState.remove(ScrollState.PAGINATION_UP);
							verticalBar.setValue(1.0); 
							this.getSelectionModel().select(ROWS_MAX-1);
						}
						if(scrollState.contains(ScrollState.PAGINATION_DOWN)) { scrollState.remove(ScrollState.PAGINATION_DOWN);
							verticalBar.setValue(0.0); 
							this.getSelectionModel().select(0);
						}
						
						// FIXME Боремся с багом:
						// При нулячей загрузки индекс селекции сбрасывается в -1, и если нажать стрелку вверх (декремент идекса селекции) то таблица тупо очистится
						if(this.getSelectionModel().getSelectedIndex()<0) {
							this.getSelectionModel().select(ROWS_MAX-1); verticalBar.setValue(1.0);
						}
						if(this.getSelectionModel().getSelectedIndex()>ROWS_MAX-1) {
							this.getSelectionModel().select(ROWS_MAX-1); verticalBar.setValue(1.0);
						}
						
						// Если без пагинации приходит новая транзакция, то селекция не меняется
						
						// Platform.runLater - выполняется в потоке приложения UI (там где другие вызовы setTransactionsSlice)
						// поэтому доступ здесь к txStart, txFilter синхронизирован с вызовами pagnationUP/DOWN()
						// setTransactionsSlice здесь используется только для проверки что все еще происходит пагинация
						// и отключать scrollProgress не стоит (аналогично и включение по результату вызова setTransactionsSlice)
						// Если ничего не меняется, то setTransactionsSlice - холостая
						
						if(controller.setTransactionsSlice(txStart, rowsMax, getFilter())<=0) {
							//jobInfo.progress(null);
							jobInfo.popup(slice.limit+" "+L10n.t("Transactions ago")); 
						}
						
						/*txStart=slice.start;*/ txLimit=slice.limit; // В случае фильтрации txLimit выходит далеко за txStart+ROWS_MAX
						
						LOGGER.console("txStart="+txStart+" slice.limit="+slice.limit+" delta="+(slice.limit-txStart)); // TODO debug
						 					 
					}
					
					LOGGER.info("TransactionsView updated");
					
				});					
			}});				
			
			// Все открываем скролл на ROWS_MAX транзакцый
			if(this.controller.setTransactionsSlice(txStart, rowsMax, getFilter())>0) {
					jobInfo.progress(txStart+" "+L10n.t("Transactions ago")); 
			}
			
		}
	}
}
