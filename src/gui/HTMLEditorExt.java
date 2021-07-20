package gui;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import org.w3c.dom.Document;
import org.w3c.dom.events.EventTarget;

import javafx.application.Platform;
import javafx.concurrent.Worker.State;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Separator;
import javafx.scene.control.ToolBar;
import javafx.scene.web.HTMLEditor;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import util.Binary;
import util.Css;
import util.LOGGER;
import util.Threads;
import util.L10n;

import static gui.WebEngineWrp.garbageRegExp1;
import static gui.WebEngineWrp.garbageRegExp2;
import static gui.WebEngineWrp.youtubeIdPattern;
import static gui.WebEngineWrp.emojiUnfix;
import static gui.WebEngineWrp.mediaUnfix;
import static util.MediaProcessing.*;


public class HTMLEditorExt extends HTMLEditor {

	static final double TOOLS_HEIGHT = Css.getTextBounds("┃⟺",18).getHeight()*1.0;
	
	static final long MAX_MEDIA_SIZE = WebEngineWrp.MAX_CONTENT_SIZE-1024*2; // 1024 - символа на сопроводительный текст
	static final double MAX_PICTURE_HEIGHT = 768;
	static final double DEF_PICTURE_HEIGHT = 768*0.375;
	static final double MIN_PICTURE_HEIGHT = 24;
	
	private  ToolBar topToolBar;
		private Button buttonSmile;
		private Button buttonMedia;
		private Button buttonLink;
		private Button buttonHtml;
	private WebViewWrp webview;
	
	JobInfo jobInfo=new JobInfo();
	
	public HTMLEditorExt() {super();	
		
		// Эти Nodes видны сразу как топ-уровень. Вложенные - видны в layoutChildren()
		topToolBar = (ToolBar)this.lookup(".top-toolbar");
		
		webview = new WebViewWrp( (WebView)this.lookup(".web-view") );

		this.getChildren().addAll(jobInfo);
		
		webview.getEngine().documentProperty().addListener( (obs, oldDoc, newDoc) -> {
			if(newDoc!=null) pasteInit(); // Навешивает обработчик "paste" на документ
		});
	}
	
	void toolBarInit() {
		if (topToolBar!=null) {
			
			Node buttonPaste=this.lookup(".html-editor-paste");
			
			Separator separator = new Separator(Orientation.VERTICAL);
			buttonSmile = new Button("", new ImageView(new Image("res/smile_96x96.png",TOOLS_HEIGHT*1.0,TOOLS_HEIGHT,false,true)));
			buttonMedia = new Button("", new ImageView(new Image("res/media_96x96.png",TOOLS_HEIGHT*1.0,TOOLS_HEIGHT,false,true)));
			buttonLink = new Button("", new ImageView(new Image("res/link_96x96.png",TOOLS_HEIGHT*1.0,TOOLS_HEIGHT,false,true)));
			buttonHtml = new Button("", new ImageView(new Image("res/html_96x96.png",TOOLS_HEIGHT*1.0,TOOLS_HEIGHT,false,true)));
			
			
			if(buttonPaste!=null) { // Привязка активности кнопок адекватна по кнопке Paste ( без курсора она не активна )
			
				buttonSmile.disableProperty().bind(buttonPaste.disableProperty());
				buttonMedia.disableProperty().bind(buttonPaste.disableProperty());
				buttonLink.disableProperty().bind(buttonPaste.disableProperty());
				buttonHtml.disableProperty().bind(buttonPaste.disableProperty());
			}
			
		/*	buttonSmile.setDisable(true); buttonMedia.setDisable(true); buttonLink.setDisable(true); buttonHtml.setDisable(true);
			webview.focusedProperty().addListener((obs)->{ // Чтобы включались как и все остальные кнопки панели при установки курсора
				buttonSmile.setDisable(false); buttonLink.setDisable(false); buttonMedia.setDisable(false); buttonHtml.setDisable(false);
			}); */
			
			topToolBar.getItems().addAll(separator,buttonSmile,buttonMedia,buttonLink,buttonHtml);

			buttonSmile.setOnAction( (ev)->{
				
				EmojiSelectForm emojiSelect=new EmojiSelectForm(this);
				emojiSelect.showAndWait();
				
				if(emojiSelect.getResult()!=null && !emojiSelect.getResult().isBlank()) {
					webview.getEngine().insertHtmlAtCursor(WebEngineWrp.emojiEmbed(emojiSelect.getResult()));
				}
				
				webview.requestFocus();
			});
			buttonMedia.setOnAction( (ev)->{
				
				FileChooser fileChooser = new FileChooser();
				fileChooser.setTitle(L10n.t("Select Media"));
				fileChooser.getExtensionFilters().addAll( // XXX для конверсии можем брать все что поддерживает ffmpeg
						new ExtensionFilter(L10n.t("Pictures"), "*.png", "*.jpg", "*.jpeg", "*.bmp", "*.svg"),
						new ExtensionFilter(L10n.t("Animations"), "*.gif", "*.mp4", "*.mkv", "*.mov", "*.avi", "*.flv"), // Взял на h.264
						new ExtensionFilter(L10n.t("Voices"), "*.mp3", "*.m4a", "*.mpeg", "*.wav", "*.aac", "*.ogg")
														);
			    File mediaFile = fileChooser.showOpenDialog(this.getScene().getWindow());

			    if(mediaFile!=null) {
			    	
			    	if(mediaFile.getName().toLowerCase().endsWith(".svg")) { // FIXME в svg могут быть скриптовые теги
			    		
			    		SliderSizeForm sliderSize=new SliderSizeForm(this,MIN_PICTURE_HEIGHT,MAX_PICTURE_HEIGHT,DEF_PICTURE_HEIGHT);
						sliderSize.showAndWait();
						if(sliderSize.getResult()!=null) {
					    	try { webview.getEngine().insertHtmlAtCursor(WebEngineWrp.pictureEmbed(mediaFile,sliderSize.getResult().intValue())); } 
					    	catch (Exception e) { LOGGER.error("Picture insertion error ("+e.toString()+")"); }
						}
			    	}
			    	else if(mediaFile.getName().toLowerCase().endsWith(".png")  ||
			    			mediaFile.getName().toLowerCase().endsWith(".jpg")  ||
			    			mediaFile.getName().toLowerCase().endsWith(".jpeg") || 
			    			mediaFile.getName().toLowerCase().endsWith(".bmp")   ) 
			    	{
			    		
			    		final File pictureSrc=mediaFile;
			    		jobInfo.progress(""); 
			    		Threads.runNow(()->{
			    			
				    		try {
				    			final File pictureFile=ImageCompressing(pictureSrc,MAX_MEDIA_SIZE);
				    			
				    			Platform.runLater(()->{

						    		SliderSizeForm sliderSize=new SliderSizeForm(this,MIN_PICTURE_HEIGHT,MAX_PICTURE_HEIGHT,DEF_PICTURE_HEIGHT);
									sliderSize.showAndWait();
									if(sliderSize.getResult()!=null) {
								    	try { webview.getEngine().insertHtmlAtCursor(WebEngineWrp.pictureEmbed(pictureFile,sliderSize.getResult().intValue())); } 
								    	catch (Exception e) { LOGGER.error("Picture insertion error ("+e.toString()+")"); }
									}
				    			});
				    			
				    			Platform.runLater(()->jobInfo.progress(null));
						    	
							} catch (Exception e) { LOGGER.error("Picture compression error ("+e.toString()+")");
							
								Platform.runLater(()->jobInfo.alert("Picture compression error ("+e.getMessage()+")"));
							}
			    			
			    		});
			    		
			    	}
			    	else if(mediaFile.getName().toLowerCase().endsWith(".gif") || 
			    			mediaFile.getName().toLowerCase().endsWith(".mp4") ||
			    			mediaFile.getName().toLowerCase().endsWith(".mkv") ||
			    			mediaFile.getName().toLowerCase().endsWith(".mov") ||
			    			mediaFile.getName().toLowerCase().endsWith(".avi") ||
			    			mediaFile.getName().toLowerCase().endsWith(".flv")  )	// XXX Все пережимается ffmpeg в mp4 для внедрения
			    	{
			    		
			    		final File animationSrc=mediaFile;
			    		jobInfo.progress(""); 
			    		Threads.runNow(()->{

				    		try {
				    			final File animationFile=AnimationCompressing(animationSrc,MAX_MEDIA_SIZE);
				    			
				    			Platform.runLater(()->{

						    		SliderSizeForm sliderSize=new SliderSizeForm(this,MIN_PICTURE_HEIGHT,MAX_PICTURE_HEIGHT,DEF_PICTURE_HEIGHT);
									sliderSize.showAndWait();
									if(sliderSize.getResult()!=null) {
										webview.getEngine().insertHtmlAtCursor(WebEngineWrp.animationEmbed(animationFile,sliderSize.getResult().intValue()));
									}
				    			});
				    			
				    			Platform.runLater(()->jobInfo.progress(null));
						    	
							} catch (Exception e) { LOGGER.error("Animation compression error ("+e.toString()+")");
							
								Platform.runLater(()->jobInfo.alert("Animation compression error ("+e.getMessage()+")"));
							}
				    		
			    		});
			    		
			    	}
			    	else if(mediaFile.getName().toLowerCase().endsWith(".mp3")   || 
			    			mediaFile.getName().toLowerCase().endsWith(".m4a")   ||
			    			mediaFile.getName().toLowerCase().endsWith(".mpeg")  ||
			    			mediaFile.getName().toLowerCase().endsWith(".wav")   ||
			    			mediaFile.getName().toLowerCase().endsWith(".aac")   ||
			    			mediaFile.getName().toLowerCase().endsWith(".ogg")    )	// XXX Все пережимается ffmpeg в mp3 для внедрения
			    	{
			    		
			    		final File voiceSrc=mediaFile;
			    		jobInfo.progress(""); 
			    		Threads.runNow(()->{
			    			
				    		try {
				    			final File voiceFile=voiceCompressing(voiceSrc,MAX_MEDIA_SIZE);
				    			
				    			Platform.runLater(()->{
				    				
							    	webview.getEngine().insertHtmlAtCursor(WebEngineWrp.voiceEmbed(voiceFile));
							    	
				    			});
						    	
				    			Platform.runLater(()->jobInfo.progress(null));
				    			
							} catch (Exception e) { LOGGER.error("Voice compression error ("+e.toString()+")");
								
								Platform.runLater(()->jobInfo.alert("Voice compression error ("+e.getMessage()+")"));
							}

			    		});	    		
			    		
			    	}
			    	
			    }
			    
			    webview.requestFocus();
			});
			buttonLink.setOnAction( (ev)->{
				TextFieldsForm linkEdit=new TextFieldsForm(this,
						L10n.t("Web link or txid"),
						L10n.t("Description") 										    );
				linkEdit.showAndWait();
				
				if(linkEdit.getResult()!=null) {
					
					String link=linkEdit.getResult()[0]; link=link.trim();
					String desc=linkEdit.getResult()[1]; 
					
					String ext=link.substring(link.lastIndexOf('.') + 1).toLowerCase();		
					
					if(link.startsWith("image=") || ext.equals("jpg")  || 
							                        ext.equals("jpeg") || 
							                        ext.equals("png")  || 
							                        ext.equals("gif")  || 
							                        ext.equals("svg")  || 
							                        ext.equals("bmp")   )    
					{
					
						SliderSizeForm sliderSize=new SliderSizeForm(this,MIN_PICTURE_HEIGHT,MAX_PICTURE_HEIGHT,DEF_PICTURE_HEIGHT);
						sliderSize.showAndWait();
						if(sliderSize.getResult()!=null) {
							webview.getEngine().insertHtmlAtCursor(WebEngineWrp.linkEmbed(link,desc,sliderSize.getResult().intValue()));
						}
					}
					else if(link.startsWith("video=") || ext.equals("mp4")  ) // XXX Это все что может воспроизвести WebView пока
					{
						SliderSizeForm sliderSize=new SliderSizeForm(this,MIN_PICTURE_HEIGHT,MAX_PICTURE_HEIGHT,DEF_PICTURE_HEIGHT);
						sliderSize.showAndWait();
						if(sliderSize.getResult()!=null) {
							webview.getEngine().insertHtmlAtCursor(WebEngineWrp.linkEmbed(link,desc,sliderSize.getResult().intValue()));
						}						
					}
					else if(!link.startsWith("link=") && youtubeIdPattern.matcher(link).find())
					{
						SliderSizeForm sliderSize=new SliderSizeForm(this,MIN_PICTURE_HEIGHT,MAX_PICTURE_HEIGHT,DEF_PICTURE_HEIGHT);
						sliderSize.showAndWait();
						if(sliderSize.getResult()!=null) {
							webview.getEngine().insertHtmlAtCursor(WebEngineWrp.linkEmbed(link,desc,sliderSize.getResult().intValue()));
						}						
					}
					else {
						webview.getEngine().insertHtmlAtCursor(WebEngineWrp.linkEmbed(link,desc)); // ссылка или audio-ссылка (не нужен выбор размера)
					}
				}
				
				webview.requestFocus();
			});
			buttonHtml.setOnAction( (ev)->{
				
				FileChooser fileChooser = new FileChooser();
				fileChooser.setTitle(L10n.t("Select HTML Template"));
				fileChooser.getExtensionFilters().addAll(new ExtensionFilter(L10n.t("HTML Files"), "*.html"));
				
			    File htmlFile = fileChooser.showOpenDialog(this.getScene().getWindow());
				
			    if(htmlFile!=null) {
			    	try {
						String html=Files.readString(htmlFile.toPath());
						
						this.setHtmlText(html); // Прогружает через worker и documentProperty со всеми предобработками
						
					} catch (IOException e) { LOGGER.error("HTML template load error ("+e.toString()+")"); 
					
						Platform.runLater(()->jobInfo.alert("HTML template load error ("+e.getMessage()+")"));
					}
			    }
				
			    webview.requestFocus();
			});

			
			//webview.requestFocus();
			//topToolBar.requestFocus(); // Стартовое состояние панели инструментов - отключенное, пока не появится курсор в поле редактирования
		}
	}
	
	private final WebEngineWrp pasteEngine=new WebEngineWrp();	// не визуальный объект javaFX для предобработки контента
	private volatile Boolean pasteProgress=false;
	
	void pasteInit() { synchronized(pasteProgress) {
		// FIXME - возможно это лишнее если будет перед отправкой превью
		// Пропускаем внешний контент только через предобработку (чтобы сразу видеть как будет в блокчейне)
		
		final Document doc=webview.getEngine().getDocument(); //  реализует интерфейсы HTMLDocument ,  EventTarget
		
		((EventTarget)doc).addEventListener("paste", (ev)-> {
			
			synchronized(pasteProgress) {
				if(pasteProgress) { ev.stopPropagation(); ev.preventDefault(); return; }	
				// Новые вставки пока не завершили обработку текущей - игнорируются
				// 	Планируем предобработку контента в буфере обмена:		 
				//	- Чтобы из буфера обмена не вставляли это всегда представлено как html и/или string
				//	- даже списки файлов имеют представление string и также вставляются в webengine
				//	- Когда вставляется чистая картинка (например из графического редактора), то она заходит только как image и игнорируется engine
				//	- Картинки из браузеров заходят так или иначе через html, а из файловых менеджеров как string пути расположения
				// 	- (перетаскиваемый контент из любого источника заходит как сырой string и в перехвате не нуждается)
				//	- ulr тоже имеет сразу представление как String так и html FIXME Проверить это все в windows ! 
					
					
				Clipboard clipboard = Clipboard.getSystemClipboard();
	
				if(clipboard.hasHtml()) { // тогда предобработка					
					pasteProgress=true; ev.stopPropagation(); ev.preventDefault();
					pasteEngine.loadContent(clipboard.getHtml());
					return;
				} 
				if(clipboard.hasString()) { // emoji будут заменены на <img class='emoji' id='1F1E7-1F1F2' src='data:image/svg+xml;base64,...'> 
					pasteProgress=true; ev.stopPropagation(); ev.preventDefault();
					pasteEngine.loadContent(clipboard.getString()); // Дальше работает LoadWorker engine
					return;
				}
				
				// Штатная вставка того, что не html
			}
				
		},true); // true - стадия погружения события (bubbling фаза не захватывается)
		
		pasteEngine.getLoadWorker().stateProperty().addListener((obs, oldState, newState)->{
			synchronized(pasteProgress) {
				
				if(newState==State.SUCCEEDED) {
					webview.getEngine().insertHtmlAtCursor(pasteEngine.readBody()); pasteProgress=false;
					//webview.getEngine().insertHtmlAtCursor(pasteEngine.readContent()); pasteProgress=false;
					return;
				}
				
				if(newState==State.FAILED || newState==State.CANCELLED) {
					pasteProgress=false;
					return;
				}
				
			}
		});
	
		pasteProgress=false;
	}};
	
	private volatile int layoutInit=-1; // Для инициализаций, которые не возможны без внешнего представления
	@Override public void layoutChildren() { super.layoutChildren();
		if(layoutInit<0) {layoutInit++; 
			toolBarInit();
		}
		else if(layoutInit<1) {layoutInit++;
		}
		else if(layoutInit<2) {layoutInit++; 
		}
	}
	
	
	////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	
	@Override public void setHtmlText(String htmlText) {
		webview.getEngine().loadContent(htmlText); // Для всех предобработок
	}
	
	private long hashContent=-1; private String unfixedContent=null;
	@Override synchronized public String getHtmlText() { // Может часто запрашиваться для вычисления текущего размера, поэтому кешик не повредит
		try {
			
			//String content=webview.getEngine().readBody();  // FIXME надеимся что движок тоже кеширует (надеимся на интеллек javaFX)
			String content=webview.getEngine().readContent(); // Захватит стили
			
			long hash=Binary.longHash(content);
			
			if(hash!=hashContent) { hashContent=hash;
				unfixedContent=mediaUnfix(emojiUnfix(content.replace('\uFFFD', '\u200B')).replaceFirst(garbageRegExp1, "").replaceFirst(garbageRegExp2, "")); 
																													// без атрибута contenteditable=true
			}
			
			return unfixedContent;
		}
		catch (Exception e) { LOGGER.error("Media embedding error ("+e.toString()+")"); // XXX e.getMessage() не информативно
			Platform.runLater(()->jobInfo.alert("Media embedding error ("+e.getMessage()+")"));
			
			return null;
		}
	}
	
}
