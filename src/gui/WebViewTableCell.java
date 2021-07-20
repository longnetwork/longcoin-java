package gui;

import static application.Main.*;

import javafx.concurrent.Worker.State;
import javafx.scene.Node;
import javafx.scene.control.IndexedCell;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.control.skin.VirtualFlow;
import javafx.util.Callback;
import util.Css;
import util.L10n;


public class WebViewTableCell<S> extends TableCell<S, String> {

	static final double HEIGHT_MAX = SCREEN_HEIGHT*H1;
	static final double PADDING_BOTTOM = Css.getTextBounds("┃⟺",14).getHeight()*2; // Для скроллбара запас
	
    public static <S> Callback<TableColumn<S,String>, TableCell<S,String>> forTableColumn() {
        return (TableColumn<S, String> param) -> new WebViewTableCell<S>();
    }
        
    private final WebViewWrp webview=new WebViewWrp(); private Node getNode() {return webview.getNode();}
    //private final WebView webview=new WebView(); private Node getNode() {return webview;}
	
    public WebViewTableCell() {
        this.getStyleClass().add("web-view-table-cell");

    	webview.setMaxWidth(Double.MAX_VALUE);
    	
		webview.setPrefHeight(PADDING_BOTTOM); // Размер пустого контента
		this.setPrefHeight(PADDING_BOTTOM);
    	
    	this.setTooltip(new Tooltip(L10n.t("Ctrl + Mouse Wheel to Zoom")));
        
        this.itemProperty().addListener( (obs, oldContent, newContent) -> { // вызывается когда требуется обновление связанное с ObservableValue
        		
        		webview.getEngine().loadContent(newContent);  // Новый контент грузится асинхронно (null - очистка)
        });
        
        webview.getEngine().getLoadWorker().stateProperty().addListener( (obs, oldState, newState) -> {
        	if(newState == State.SCHEDULED) { 
        		//LOGGER.console("WebViewCell Sheduled Content: "+this.getIndex());  // TODO debug

        		if(webview.getEngine().contentScheduled()) webview.setZoom(1.0); // Для ререндерингов zoom сохранится последний
        		
   				webview.setPrefHeight(USE_COMPUTED_SIZE); // Обязательная предустановка для динамического вычисления высоты контента
   				this.setPrefHeight(USE_COMPUTED_SIZE);
   				
       			return;
        	}
        	if(newState == State.SUCCEEDED ) { // Динамическое вычисление высоты webview по контенту
        		//LOGGER.console("WebViewCell Loaded Content: "+this.getIndex()); // TODO debug
        		
        		// Высота вычисляема вне зависимости от установки setGraphic() в updateItem
        		if (flow!=null) synchronized(flow) { 	// TODO Ебаная javaFX всеравно иногда фейлит высоту ячейки !!!!!!!!!!
	        							 				// flow один для всех - можно синхронизироваться
	        			
		    			double h = PADDING_BOTTOM + 
		    					   (Integer)webview.getEngine().executeScript("" +
		    							   "const body = document.body," +
		                                   "      html = document.documentElement;" +
		                                   "Math.min( body.scrollHeight, body.offsetHeight, " +
		                                   "          html.clientHeight, html.scrollHeight, html.offsetHeight);" +
		    							   									  "");
		    			
		    			final double height = h < HEIGHT_MAX ? h : HEIGHT_MAX;
		    			
		    			webview.setPrefHeight(height); //webview.requestFocus(); //webview.requestLayout(); 
		    			this.setPrefHeight(height); //this.requestLayout();
	        	}
        		
	        	return;
        	}
        });
        
        this.indexProperty().addListener((obs, oldIndex, newIndex)->{ //The location of this cell in the virtualized control

    	});
    }
    
    private boolean dirty= false;	// Для борьбы с артифактами скроллинга
    	VirtualFlow<?> flow=null;
    void layoutInit() {
    	TableView<S> table=this.getTableView();
    	if(table!=null) {
    		flow = (VirtualFlow<?>) table.lookup(".virtual-flow");
    		if(flow!=null) {
    						
    			flow.positionProperty().addListener((obs, oldValue, newValue) -> {
    				
    				// FIXME Каждая ячека зарегистрирует этот обработчик со своим this, но вызываться он будет только для тех которые есть во flow
    				// (flow работает с виртуальными ячейками, которых может быть меньше, чем фактических, переназначая фактические на виртуальные
    				//  по мере надобности отображения в видимой части скролла - как-то так, - за счет установки своего индекса во flow)
    				
    				int index=this.getIndex(); 	// -1 если не во flow. Еще такая ячейка есть всегда и там всегда this.getItem()==null
    				//IndexedCell<?> visibleCell=flow.getVisibleCell(index);  // null -  может и во flow но всеравно не видна
    				//int visibleIndex=(visibleCell!=null) ? visibleCell.getIndex() : -1; // Для видимых ячеек index==visibleIndex

    				IndexedCell<?> firstVisibleCell=flow.getFirstVisibleCell();
    				IndexedCell<?> lastVisibleCell=flow.getLastVisibleCell();
    				
    				int firstVisibleIndex= (firstVisibleCell!=null) ? firstVisibleCell.getIndex() : flow.getCellCount();
    				int lastVisibleIndex= (lastVisibleCell!=null) ? lastVisibleCell.getIndex() : -1;
    				
    				//LOGGER.console("positionProperty: "+index+"/"+visibleIndex+" "+dirty+"  "+firstVisibleIndex+" - "+lastVisibleIndex); // TODO debug
    		    			    	
    		    	if( (index<firstVisibleIndex || index>lastVisibleIndex) /*|| visibleCell==null*/) { // Вне видимой части скроллинга - готовим будущую перерисовку
    		    		
    		    		dirty=true;
    		    		// webview.getEngine().loadContent(null);				// force Refresh 
    		    		// (Лучше не сбрасывать контент, так как loadContent всеравно сделает refresh
    		    		// а также это важно для кеширования в webview)
    		    		
    		    		return;
    		    	} 
    				
    				if(dirty) {	// Перерисовка
    					
    					//webview.getEngine().loadContent(this.getItem());		// force Refresh
    					webview.getEngine().reload();
    					
    					dirty=false;
    					
    					return;
    				}		
    		    		
    			});
    		} 
    	}
    	
    }

    void focusInit() {
    	this.selectedProperty().addListener((obs, oldBoolean, newBoolean)->{ 
        	// Для возможности принимать события без предварительного клика мышкой в режиме setCellSelectionEnabled(true);
        	if(newBoolean) webview.requestFocus();
        });
    	TableRow<S> row=this.getTableRow();
    	if(row!=null) {
    		row.selectedProperty().addListener((obs, oldBoolean, newBoolean)->{ 
    			// Для возможности принимать события без предварительного клика мышкой в режиме setCellSelectionEnabled(false);
    			if(newBoolean) webview.requestFocus();
    		});
    		
    		// FIXME при первой загрузки таблицы уже селекция может быть установлена еще до установки selectedProperty().addListener()
        	// и соответственно первый нужный webview.requestFocus() не пройдет
    		if(row.isSelected()) webview.requestFocus();
    	} 
    	if(this.isSelected()) webview.requestFocus();
    }
    
    // Вызывается после itemProperty Listener и никак не связан с webview (тот сам себя рендерит после загрузки)
    // Порядок вызовов вроде такой: 
    // - itemProperty Listener;
    // - LoadWorker stateProperty SCHEDULED;
    // - updateItem();
    // - LoadWorker stateProperty SUCCEEDED;
    @Override public void updateItem(String item, boolean empty) { super.updateItem(item, empty);   
	       if (item==null || empty) {     	   
	    	   setGraphic(null); 
	       }
	       else {  
	    	   setGraphic(this.getNode()); 
	       }
	       // FIXME: Потом помониторить updateItem на предмет исключения лишних ререндеригов одних и тех же данных
		   // Это вообще важно для всего не только для временных файлов...
    }
	
    private volatile int layoutInit=-1; // Для инициализаций, которые не возможны без внешнего представления
	@Override public void requestLayout() { super.requestLayout();
		if(layoutInit<0) {layoutInit++;
			layoutInit();
		}
		else if(layoutInit<1) {layoutInit++;
		}
		else if(layoutInit<2) {layoutInit++;
			focusInit();
		}	
	}
}
