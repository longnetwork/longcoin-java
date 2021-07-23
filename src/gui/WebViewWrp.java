package gui;

import static application.Main.*;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.effect.BlendMode;
import javafx.scene.input.KeyCode;
import javafx.scene.input.ScrollEvent;
import javafx.scene.web.WebView;
import javafx.stage.Window;
import javafx.stage.WindowEvent;
import util.L10n;


public class WebViewWrp implements AutoCloseable {
	
	static final double HEIGHT_MAX = SCREEN_HEIGHT*H1;
	
	private final WebView webview;
	private final WebEngineWrp engine;

	public WebViewWrp() {this(null);}
	public WebViewWrp(WebView webview) {
		if(webview==null) webview=new WebView();
		this.webview=webview; this.webview.setBlendMode(BlendMode.SRC_OVER);
		
		this.engine=new WebEngineWrp(this.webview.getEngine());
		
		this.webview.addEventFilter(ScrollEvent.SCROLL, (ev)->{
        	if(ev.isControlDown()) { ev.consume();
        		double zoom=this.webview.getZoom();
        		double deltaY = ev.getDeltaY();

        		zoom=zoom*(1d+deltaY/HEIGHT_MAX);
        		//if(deltaY>0) zoom=zoom*1.05d;
        		//if(deltaY<0) zoom=zoom*0.95d;
        		this.webview.setZoom(zoom);
        	}
        });
		
		this.webview.setFocusTraversable(true);

		// ctrl+U стандартное сочетание для просмотра html
		this.webview.setOnKeyPressed((ev)->{ // Контент уже после пост-обработки
			if(ev.isControlDown() && ev.getCode()==KeyCode.U) { ev.consume();
				TextViewForm textView=new TextViewForm(this.webview);
				textView.setText(engine.readContent());
				textView.show(); //textView.showAndWait();
			}
		});
		
		this.webview.setContextMenuEnabled(true); // Создается динамически движком, поэтому доступно только через жопу
		
		this.webview.setOnContextMenuRequested((ev)->{	// Ищем окна созданные движком динамически при запросе контекстного меню
    		final ObservableList<Window> windows = Window.getWindows();
    		for (Window window : windows) {
    			if (window instanceof ContextMenu) {
    				ContextMenu menu=(ContextMenu)window;
    				if(menu.getOwnerWindow()==this.webview.getScene().getWindow()) { // Нашли меню
    					
    					// FIXME Добавить новые пункты невозможно (касты на обработчики вывалятся в исключения)
    					// из-за своей внутренней реализации контекстного меню движком, - и реакций на клики пунктов.
    					// ContextMenu и MenuItem используются движком только чтобы отображать его в окнах javaFX.
    					//  ( com.sun.javafx.webkit.theme.ContextMenuImpl )
    					// Поэтому делаем все через жопу (через лайвхаки)
    					

	    				ObservableList<MenuItem> menuItems=menu.getItems();
	    				for(int i=0; i<menuItems.size(); i++) { // Вот список пунктов меню
	    					
	    					MenuItem item=menuItems.get(i);
	    					
	    					String itemStr=item.getText(); if(itemStr==null) continue;
	    					
	    					/*if(itemStr.endsWith("in New Window")) { // замена на открытие во внешнем браузере 
	    						itemStr="Open in external browser";
	    						item.setOnAction((aev)->{ aev.consume();
	    						});
	    					}
	    					else*/
	    					if(itemStr.startsWith("Open")) { // Другие Open - режим
    							menuItems.remove(i); i--;
    							continue;
	    					}
	    					else
	    					if(itemStr.startsWith("Reload") || itemStr.startsWith("Stop")) {	// Свой метод обновления страницы (из кэша)
	    						item.setOnAction((aev)->{ aev.consume();
	    							engine.reload();
	    						});
	    					}
	    					
	    					item.setText(L10n.t(itemStr)); // Локализация
	    				}
	    				
	    				break;
    				}
    			}
    		}
    	});
		
				
		this.webview.needsLayoutProperty().addListener((obs, oldBoolean, newBoolean)->{ // Ловим возможность доп инициализаций для каждого объекта webview
			if(layoutInit<0) { layoutInit++;
				this.webview.getScene().getWindow().addEventFilter(WindowEvent.WINDOW_CLOSE_REQUEST, (ev)->{
					// FIXME Необходима принудительная очистка через жопу медиа контента иначе jvm сегфолтится
					if(layoutInit<1) { layoutInit++; // fireEvent от каждого WebView запланируется только 1 раз 
						final WindowEvent closeEvent =(WindowEvent)ev.clone(); ev.consume();
						
						//LOGGER.console("WindowEvent.WINDOW_CLOSE_REQUEST"); // TODO debug
						
						this.close(); 
						
						Platform.runLater(()->{
							WindowEvent.fireEvent(closeEvent.getTarget(), closeEvent);
						});
					}
				});
			}
		});
		
		
	}
	private volatile int layoutInit=-1;
	
	@Override public void close() {
		this.engine.loadContent(null);
	}
	@Override public void finalize() {
		this.close();
	}
	
	/////////////////////////////////////////           Интерфейс          ///////////////////////////////////////////////////////////////
	
	
	
	public void setZoom(double value) {webview.setZoom(value);}
	public Node getNode() {return webview;}
	public void setPrefHeight(double value) {webview.setPrefHeight(value);}
	public double getHeight() {return webview.getHeight();}
	//public ReadOnlyDoubleProperty heightProperty() {return webview.heightProperty();}
	//public DoubleProperty prefHeightProperty() {return webview.prefHeightProperty();}
	//public void setMaxHeight(double value) {webview.setMaxHeight(value);}
	public WebEngineWrp getEngine() {return engine;}
	public void setMaxWidth(double value) {webview.setMaxWidth(value);}
	//public final void setBlendMode(BlendMode value) {webview.setBlendMode(value);}
	public void requestFocus() {webview.requestFocus();}
	//public final ReadOnlyBooleanProperty focusedProperty() {return webview.focusedProperty();}

}
