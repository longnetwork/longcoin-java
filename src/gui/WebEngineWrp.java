package gui;

import static util.Binary.*;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.w3c.dom.*;

import javafx.concurrent.Worker.State;
import javafx.scene.text.Font;
import javafx.scene.web.WebEngine;
import netscape.javascript.JSObject;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.concurrent.Worker;
import util.Binary;
import util.Css;
import util.LOGGER;

public class WebEngineWrp {
	
	
	/* The WebView component enables you to play video and audio content within a loaded HTML page. 
	 * The following codecs are supported:
	 * -Audio: AIFF, WAV(PCM), MP3, and AAC;
	 * -Video: VP6, H264;
	 * -Media containers: FLV, FXM, MP4, and MpegTS (HLS)
	 */
	

	static final long MAX_CONTENT_SIZE = 66*1024;
	
	static final double EMOJI_HEIGHT = Css.getTextBounds("┃⟺",16).getHeight()*1.25;
	static final double CLICKICO_HEIGHT = Css.getTextBounds("┃⟺",16).getHeight()*1.75;
	static final double POSTER_HEIGHT = Css.getTextBounds("┃⟺",16).getHeight()*11;  //13; 
	
	static final Font openmojiFont = Font.loadFont(ClassLoader.getSystemResource("res/OpenMoji-Color.ttf").toExternalForm(), 16);
	// Шрифт нужно загружать раньше всего (до первого создания webview)
	
	static {
		LOGGER.console("default Font: "+Font.getDefault().getName()); // TODO debug
	}
	
	
	// Заместители до клика и загрузки внешнего медиа
	static final String pictureSVGdata="data:image/svg+xml;base64,"+
									    Base64.getEncoder().encodeToString(InputStreamToByteArray(ClassLoader.getSystemResourceAsStream("res/picture.svg")));
	static final String musicSVGdata="data:image/svg+xml;base64,"+
										Base64.getEncoder().encodeToString(InputStreamToByteArray(ClassLoader.getSystemResourceAsStream("res/music.svg")));
	static final String sinemaSVGdata="data:image/svg+xml;base64,"+
										Base64.getEncoder().encodeToString(InputStreamToByteArray(ClassLoader.getSystemResourceAsStream("res/sinema.svg")));
	static final String iframeSVGdata="data:image/svg+xml;base64,"+
										Base64.getEncoder().encodeToString(InputStreamToByteArray(ClassLoader.getSystemResourceAsStream("res/iframe.svg")));

	
	static final String defaultStyles="data:,"
    		+"body { white-space: pre-wrap; font-family: OpenMoji, sans-serif; font-size:16px; caret-color: Black; } "
			// white-space - настройка рендеринга пробелов и переносов строк (normal, nowrap, pre, pre-line, pre-wrap, inherit)
    		+"p { margin-top: 0em; margin-bottom: 0em; } "
    		+"h1 { margin-top: 0em; margin-bottom: 0em; } "
    		+"h2 { margin-top: 0em; margin-bottom: 0em; } "
    		+"h3 { margin-top: 0em; margin-bottom: 0em; } "
    		+"h4 { margin-top: 0em; margin-bottom: 0em; } "
    		+"h5 { margin-top: 0em; margin-bottom: 0em; } "
    		+"h6 { margin-top: 0em; margin-bottom: 0em; } "
    		
    		+"audio { display: inline-block; width: 14em; padding-left: 0.1em; padding-right: 0.1em; transform: scaleY(0.8); } "
    		+"video { display: inline-block; width: auto; padding-left: 0.1em; padding-right: 0.1em; } "
    		
    		+"iframe { display: inline-block; width: auto; padding-left: 0.1em; padding-right: 0.1em; /*zoom:0.5;*/ } "
    		
    		+"img { display: inline-block; width: auto; padding-left: 0.1em; padding-right: 0.1em; vertical-align: text-bottom; } "
    		
    		+".emoji { height: "+EMOJI_HEIGHT+"; }"
    		
    		+"video.greyed { opacity: 0.5;  cursor: pointer; border: 1px; object-fit: fill; } "
    		
    		+"iframe.greyed { opacity: 0.5; cursor: pointer; border: 1px; object-fit: fill; } "
    		
			+".clickico { opacity: 0.5; cursor: pointer; }" 
			
			+".txid { font-size:0.8em; color: BlueViolet;}"
			
			+"";
	
	private final WebEngine engine;
		// Кеширование после обработки DOM
		private String processedContent; private long hashContent;   // Все переменные внутри одного синхронизированного контекста
										 private long _hashContent; // Устанавливает loadContent()

    public class JavaApp { // Для вызова java-кода из js в html (класс для расширения во вне)
    	// Список доступных методов. ( должны быть public как и сам JavaApp, иначе js не найдет )
    	// TODO Обработка txid как ссылок
    	public void onClick(String str) {
    		LOGGER.console("Default onClick: "+str); // TODO debug
    	}
    } 
    JavaApp javaApp= new JavaApp(); // js понимает только полноценный публичный класс с публичными методами
    	
    private void javaAppInit() {
    	
		JSObject window=(JSObject)this.engine.executeScript("window");
		
		if(window!=null && window.getMember("javaApp")!=javaApp) window.setMember("javaApp",javaApp);
    }
		
    static final String TMPDIR_PREFIX=WebEngineWrp.class.getSimpleName();
    static File TEMP_DIR=null;
    
	static {
		java.net.CookieHandler.setDefault(null); // Вырубаем кукисы
		
		try { Path tmp=Files.createTempDirectory(TMPDIR_PREFIX); if(tmp!=null) {TEMP_DIR=tmp.toFile(); TEMP_DIR.deleteOnExit(); } } catch (IOException ignore) {}
		// FIXME Директории с lock-файлами авто не удаляются
	}
	
	
	public WebEngineWrp() {this(null);}
	public WebEngineWrp(WebEngine engine) {
		if(engine==null) engine=new WebEngine();
		this.engine=engine;
		
		this.engine.setUserDataDirectory(TEMP_DIR);
		
		
		this.engine.setUserStyleSheetLocation(defaultStyles);

    	this.engine.setCreatePopupHandler((features)->null); // To block the popup, a handler should return null.
    	
		this.engine.setOnAlert((ev)->LOGGER.warning("WebEngine : "+ev.getData()));
		this.engine.setOnError((ev)->LOGGER.error("WebEngine : "+ev.getMessage()));
    	
		this.engine.setJavaScriptEnabled(true); 			 // Аудио и прочие медиа требуют js. Безопасность обеспечиваем парсингом на лету
		this.engine.getHistory().setMaxSize(0);				 // Лишняя хрень
	
		this.engine.getLoadWorker().stateProperty().addListener( (obs, oldState, newState) -> { synchronized(this) { 
        	if(newState == State.SUCCEEDED && _hashContent!=hashContent) {
        		processedContent = (String)this.engine.executeScript("document.documentElement.outerHTML");
        		hashContent=_hashContent; // Именно в этот момент актуализируется хэш контента (если не было State.CANCELLED),
        								  // как если бы он здесь и вычислялся (вычисляем его там где сравниваем: - по 
        								  // исходной строке, до пред-обработки)
        		
	        	return;
        	}
        }});
		// Пред-обработка на лету (фиксы, хаки, безопасность)
		this.engine.documentProperty().addListener( (obs, oldDoc, newDoc) -> { synchronized(this) {
			
        	if(newDoc!=null && _hashContent!=hashContent) { // org.w3c.dom.Document
        		// Пока не завершится этот обработчик State.SUCCEEDED не наступит, и только когда завершится,
        		// - тогда и начнется рендеринг контента, причем врубать скрипты в State.SUCCEEDED, которое возникнет
        		// по завершении рендеринга, уже поздно - нужно врубать в конце обработчика документа (здесь в конце)
        		
        		removeScripts(newDoc);
        		forbidLinks(newDoc);
        		
        		// Все порезали все запретили. Теперь индивидуальная обработка по тегам
        		
        		this.engine.setJavaScriptEnabled(true); // Теперь контент безопасный - по выходу рендеринг
        		// После этого можно внедрять скрипты поддержки
        		
        		fixLinks_A(newDoc);
        		fixLinks_IMG(newDoc);
        		
        		fixLinks_AUDIO(newDoc);
        		fixLinks_VIDEO(newDoc);
        		
        		fixLinks_IFRAME(newDoc); // внимательно с sandbox
        		
        		fixDatas_AUDIO(newDoc);  // Встроенные медиа-данные JavaFX не рендерит. Нужно кэшировать во временных файлах
        		fixDatas_VIDEO(newDoc); // mp4-анимашки
 
        	}
        	
        	if(newDoc!=null) javaAppInit(); // При обновлении документа window может теряться
        	
        }});
	}
	
	static final Pattern emojiPattern = Pattern.compile(
		"[\\x{00A9}\\x{00AE}\\x{3030}\\x{303D}\\x{3297}\\x{3299}\\x{F000}\\x{F8FF}]"+		 // Редкие кодовые точки
		
		"|[\\x{0023}-\\x{0039}]\\x{FE0F}\\x{20E3}"+											 // Редкие последовательности
		"|[\\x{1F3F4}\\x{1FEF4}][\\x{E0060}-\\x{E007F}]{5,6}"+
		
		"|[\\x{1F1E6}-\\x{1F1FF}][\\x{1F1E6}-\\x{1F1FF}]"+ 									 // Эти пары встречаются только так
		
		// Основной парсер с префиксами и суффиксами оттенков кодовых точек
		"|[\\x{1F000}-\\x{1FF00}](?>[\\x{1F3FB}-\\x{1F3FF}\\x{FE0F}]?(?>\\x{200D}[\\x{1F308}-\\x{1F9BD}\\x{2605}-\\x{2B50}][\\x{1F3FB}-\\x{1F3FF}\\x{FE0F}]?){0,3})?"+
		
		   "|[\\x{2000}-\\x{2C00}](?>[\\x{1F3FB}-\\x{1F3FF}\\x{FE0F}](?>\\x{200D}[\\x{1F308}-\\x{1F9BD}\\x{2605}-\\x{2B50}]\\x{FE0F}?){0,2})?"+
		
		"|[\\x{E000}-\\x{E400}](?>\\x{200D}[\\x{2640}\\x{2642}]\\x{FE0F})?"+
		
		"");	
	static private final Map<String,String> cacheEmoji = new HashMap<>(); // CodePoints - SVG образ в Base64 для встроки в html
	
	static final Pattern emojiEmbedPattern = Pattern.compile("(?is)<img\\s+class=['\"]?emoji['\"]?\\s+id=['\"]?(?<id>[A-F0-9-]{4,47})['\"\\s].*?>");
	
	// FIXME Расширения захардкожены
	static final Pattern mediaEmbedPattern = Pattern.compile("(?is)src=['\"]?file:(?<file>[^'\"<>]+\\.(?<ext>wav|mp3|mpeg|m4a|aac|mp4))['\"]?");
	
	static final String garbageRegExp1="(?is)^\\s*<!DOCTYPE.*?>";
	static final String garbageRegExp2 = "(?is)contenteditable[\\s='\"]+(?>true|yes)['\"]?";
	
	static final String uriScriptRegExp="(?i)javascript:";
	static final Pattern uriScriptPattern=Pattern.compile(uriScriptRegExp);
	static final String styleExpressionRegExp="(?i)expression\\(";
	static final String styleExternalRegExp="(?i)url\\((?![\\s'\"]*(?>#|data:|file:))"; 					  // url не на внутренние данные
	static final String styleImportRegExp="(?i)@(?>import|document|namespace)(?![\\s'\"]*(?>#|data:|file:))"; // at-rules со ссылками 
	
	static final Set<String> scriptTags = Set.of( // Опасные теги с кодом для принудительного удаления
			"SCRIPT", "APPLET", "OBJECT", "EMBED" // embed даже пустой заставляет движок лезть в интернет за поисками плагинов
	);
	static final Set<String> linkAttributes = Set.of( // Все известные атрибуты со ссылками (внешние ссылки не начинаются на #)
			"action",
			"archive", // <object archive=url> or <object archive="url1 url2 url3"> <applet archive=url> or <applet archive=url1,url2,url3>
			"background","cite","classid","codebase","code","data","pluginspage",
			"href", // SVGs can also contain links to resources: <svg><image href="url" /></svg>
			"longdesc","profile","src","usemap",
			"formaction","icon","manifest","xmlns","poster",
			"srcset", // <img srcset="url1 resolution1, url2 resolution2"> <source srcset="url1 resolution1, url2 resolution2">
			"content", // <meta http-equiv="refresh" content="seconds; url"> (refresh - Загрузить другой документ в текущее окно браузера.)
			"lowsrc", "dynsrc"
			//, "style" // <div style="background: url(image.png)">
			//, "srcdoc" // <iframe srcdoc="<html-код>"></iframe> srcdoc проверяем по допустимым сигнатурам
	);
	static final Set<Long> srcdocSignatures = Set.of( // Только разрешенный безопасный контент для srcdoc
			1464574902791903600L // Tetris
	);
	//static final String knownEmbedRegExp="(?i)^(?>\\w{1,5}://)?(?>\\w+\\.)?(?>"+
	static final String knownEmbedRegExp="(?i)(?>\\w{1,5}://)?(?>\\w+\\.)?(?>"+
					 "youtu\\.be|"+
					 "youtube\\.com"+
										 ")(?>[^\\w\\.]|$)";
	static final Pattern knownEmbedPattern=Pattern.compile(knownEmbedRegExp);
	
	static final Pattern youtubeIdPattern = Pattern.compile("(?<=youtu\\.be\\/)\\w+\\??.*$|(?<=watch\\?v=)\\w+\\??.*$|(?<=embed\\/)\\w+\\??.*$");
	// https://youtu.be/BYz2Oq7hG5U
	// https://youtu.be/BYz2Oq7hG5U?t=2
	// https://www.youtube.com/watch?v=BYz2Oq7hG5U
	// https://www.youtube.com/embed/BYz2Oq7hG5U
	// Найденная группа паттерна это id видео с временными метками

	
										 
	static private Map<Long,Path> cacheMedia=new HashMap<>(); // Чтобы не плодить кучу временных файлов при ререндерингах
	//static final String TMPFILE_PREFIX="longJavaTmp";
	static final String TMPFILE_PREFIX=WebEngineWrp.class.getSimpleName();

	static final Pattern uriDataPattern= Pattern.compile("(?i)data:(?<mime>\\w+/(?<ext>\\w+));base64,(?<data>[a-zA-Z0-9+/=]{4,})");
	
	static final Pattern txidPattern= Pattern.compile("(?i)^\\s*#?(?<txid>[0-9a-f]{64})(?>-[0-9]{0,3})?\\s*$");
	
	/////////////////////////////////////////                 Интерфейс          //////////////////////////////////////////////////////////
	
	public synchronized void setJavaApp(JavaApp javaApp) { // из js можно вызывать методы JavaApp
		if(this.javaApp!=javaApp) {
			this.javaApp=javaApp;
			
			javaAppInit(); // вызывается также при обновлении документа
		}
	}
	
	public synchronized void loadContent(String html) {
		if(html==null) {
			engine.loadContent(""); // null - не очищает контент. "" - очищает сразу с прекращением предзагрузок и прочего в фоне
		}
		else {
			
    		// Вырубаем скрипты до пред-обработки. Если этого не делать, то они триггернутся 
    		// еще до завершения пред-обработки и начнут выполнение!
    		// При подгрузки из кеша вырубать нельзя, так как потом не работают скрипты поддержки рендеринга
			
			long hash=Binary.longHash(html);
			if(hash!=hashContent) { engine.setJavaScriptEnabled(false); // Врубятся после завершения DOM-оработки
				
				// hashContent вычисляется по html, но соответствует processedContent. Поэтому устанавливается 
				// из _hashContent когда фактически новый контент загружен и processedContent установлен после пред-обрабтоки
			
				// А эмоджи могут появится только в текстовых частях тегов, там где замена на картинку будет корректной!
			
				_hashContent=hash; engine.loadContent( emojiEmbed(html.replace('\uFFFD', '\u200B').replaceFirst(garbageRegExp1, "").replaceFirst(garbageRegExp2, "")) );
																				// \uFFFD - символ замены unicode от говно-копи-пастов 
			}
			else { engine.setJavaScriptEnabled(true); // Уже обработанный контент - безопасен
			
				 // DOM-обработка уже обработаного контена не произайдет
				_hashContent=hash; engine.loadContent(processedContent); // Из кеша уже со всеми предобработками для ре-рендеринга
			}
		}
	}
	
	public Worker<Void> getLoadWorker() {return engine.getLoadWorker();}
	public ReadOnlyObjectProperty<Document> documentProperty() {return engine.documentProperty();}
	public Object executeScript(String script) {return engine.executeScript(script);}
	
	protected synchronized boolean contentScheduled() { // Только до наступления State.SUCCEEDED можно обнаружить что планируется загрузка нового контента
		return this.engine.getLoadWorker().getState()==State.SCHEDULED && hashContent!=_hashContent;
	}
	public synchronized void reload() {
		if(hashContent==_hashContent) engine.loadContent(processedContent); 
	}
	public synchronized String readContent() { 
		return (String)this.engine.executeScript("document.documentElement.outerHTML");
		// LoadWorker работает только при явной загрузке через loadContent() и все. Чтобы извлечь текущий актуальный контент, полученный
		// как редактированием, так и вставкой, нужно обращение непосредственно к документу
	}	
	public synchronized String readBody() {
		return (String)this.engine.executeScript("document.body.innerHTML");
	}
	
	public final Document getDocument() {return engine.getDocument();}
	
	public synchronized void insertHtmlAtCursor(String html) { if(html==null) return;
		
		/* Необходима замена слеша для работы insertHtmlAtCursor в Windows так как там в путях '\' а скрипт считает это экранирующим символом 
		   и поэтому чтобы указать, что это обычный символ его нужно заэкранировать им-же, а для сторк java такае-же херня - и поэтому,
		   чтобы создать литерал java с ним-же - нужно снова экранировать. 
		   Вот жесть (4 черты, чтобы потом в скрипте осталось 2 и интерпретатор понял одну как разделитель в пути файла) */
		
       final String script = String.format(
                "(function(html) { "
                + "  var sel, range; "
                + "  if (window.getSelection) { "
                + "    sel = window.getSelection(); "
                + "    if (sel.getRangeAt && sel.rangeCount) { "
                + "      range = sel.getRangeAt(0); "
                + "      range.deleteContents(); "
                + "      var el = document.createElement('div'); "
                + "      el.innerHTML = html; "
                + "      var frag = document.createDocumentFragment(), node, lastNode; "
                + "      while ((node = el.firstChild)) { "
                + "        lastNode = frag.appendChild(node); "
                + "      } "
                + "      range.insertNode(frag); "
                + "      if (lastNode) { "
                + "        range = range.cloneRange(); "
                + "        range.setStartAfter(lastNode); "
                + "        range.collapse(true); "
                + "        sel.removeAllRanges(); "
                + "        sel.addRange(range); "
                + "      } "
                + "    } "
                + "  } "
                + "})('%s'); ", escape_script(html)); 
        
       this.executeScript(script);
    }
	
	
	
	////////////////////////////////////////              Статические утилиты          ///////////////////////////////////////////////////	
	
	public static void fixDatas_AUDIO(Document doc) {
		NodeList tags=doc.getElementsByTagName("AUDIO");
		if(tags!=null) for(int i=0; i<tags.getLength(); i++) fixData_MEDIA((Element)tags.item(i));
	}
	public static void fixDatas_VIDEO(Document doc) {	// Короткие анимашки mp4
		NodeList tags=doc.getElementsByTagName("VIDEO");
		if(tags!=null) for(int i=0; i<tags.getLength(); i++) fixData_MEDIA((Element)tags.item(i));
	}
	private static class MediaFile {String mime; Path path;}
	public static void fixData_MEDIA(final Element tag) {
		// Будем делать временный файл для src='data:media/ext;base64,...' с соответствующим расширением и устанавливать src на этот файл
		String src;
		
		src=tag.getAttribute("src");
		if(src!=null && !src.isBlank()) {
			
			MediaFile tmpFile=createTempMedia(src);
			if(tmpFile!=null) {
				tag.setAttribute("src","file:"+tmpFile.path.toAbsolutePath().toString());
				String type=tag.getAttribute("type");
				if(type==null || type.isBlank()) tag.setAttribute("type", tmpFile.mime);
			}
			
		}
		
		NodeList sources=tag.getElementsByTagName("SOURCE"); // Медиа-альтернативы
		if(sources!=null && sources.getLength()>0) {
			for(int i=0; i<sources.getLength(); i++) {
				Element source=(Element)sources.item(i);
				src=source.getAttribute("src");
				if(src!=null && !src.isBlank()) {
					MediaFile tmpFile=createTempMedia(src);
					if(tmpFile!=null) {
						source.setAttribute("src","file:"+tmpFile.path.toAbsolutePath().toString());
						String type=source.getAttribute("type");
						if(type==null || type.isBlank()) source.setAttribute("type", tmpFile.mime);
					}				
				}
				
			}
		}
	}
	
	
	public static String mediaUnfix(String text) throws Exception { // Обратная fixDatas_AUDIO и fixData_MEDIA перед отправкой
		final StringBuilder result=new StringBuilder();
		final Matcher matcher=mediaEmbedPattern.matcher( text );
		while(matcher.find()) {
			
			// Если паттерн совпал то группы есть
			final String fileName = matcher.group("file");
			String ext = matcher.group("ext").toLowerCase(); if(ext.equals("mp3")) ext="mpeg";
			String mime="audio"; if(ext.equals("mp4")) mime="video";
			
			Path file=Paths.get(fileName);
			if(Files.size(file)>MAX_CONTENT_SIZE) throw new RuntimeException("Media size is too big");
			
			// FIXME без кеша файлов (не для пулеметных вызовов на одних и техже данных)
			
			matcher.appendReplacement(result,"src='data:"+mime+"/"+ext+";base64,"+Base64.getEncoder().encodeToString(Files.readAllBytes(file))+"'");
		}
		matcher.appendTail(result);
		
		return result.toString();
	}
	
	public static void unfixData_MEDIA(final Element tag) throws Exception { // Обратная операция для подготовки контента к отправки
		String src; String type=tag.getAttribute("type"); String mime,ext=null;
		
		if(type==null || type.isBlank()) { mime=tag.getTagName().toLowerCase(); } // audio или video
		else { String[] t=type.split("/",2); mime=t[0]; if(t.length>1) ext=t[1]; }
		
		
		src=tag.getAttribute("src");
		if(src!=null && src.startsWith("file:")) {
			if(ext==null) // Берем из файла 
				ext=src.substring(src.lastIndexOf('.') + 1).toLowerCase(); 
			if(ext.isBlank() || ext.equals("mp3")) ext="mpeg"; // mpeg подходит под все вроде
				
			Path file=Paths.get(src.substring("file:".length()));
			if(Files.size(file)>MAX_CONTENT_SIZE) throw new RuntimeException("Media size is too big");
			tag.setAttribute("src", "data:"+mime+"/"+ext+";base64,"+Base64.getEncoder().encodeToString(Files.readAllBytes(file)));
		}
		
		NodeList sources=tag.getElementsByTagName("SOURCE"); // Медиа-альтернативы
		if(sources!=null && sources.getLength()>0) {
			for(int i=0; i<sources.getLength(); i++) {
				Element source=(Element)sources.item(i);
				
				src=source.getAttribute("src");
				if(src!=null && src.startsWith("file:")) {
					if(ext==null) // Берем из файла 
						ext=src.substring(src.lastIndexOf('.') + 1).toLowerCase(); 
					if(ext.isBlank() || ext.equals("mp3")) ext="mpeg"; // mpeg подходит под все вроде
					
					Path file=Paths.get(src.substring("file:".length()));
					if(Files.size(file)>MAX_CONTENT_SIZE) throw new RuntimeException("Media size is too big");
					source.setAttribute("src", "data:"+mime+"/"+ext+";base64,"+Base64.getEncoder().encodeToString(Files.readAllBytes(file)));			
				}	
			}
		}
	}
	public static void unfixDatas_AUDIO(Document doc) throws Exception {
		NodeList tags=doc.getElementsByTagName("AUDIO");
		if(tags!=null) for(int i=0; i<tags.getLength(); i++) unfixData_MEDIA((Element)tags.item(i));
	}
	public static void unfixDatas_VIDEO(Document doc) throws Exception {
		NodeList tags=doc.getElementsByTagName("VIDEO");
		if(tags!=null) for(int i=0; i<tags.getLength(); i++) unfixData_MEDIA((Element)tags.item(i));
	}
	
	private static MediaFile createTempMedia(String src) { if(src==null) return null;
		// Создание временного файла для 'data:media/ext;base64,...' или возврат уже созданного из кеша или null
		final Matcher matcher=uriDataPattern.matcher( src );
		if(!matcher.find()) return null;
		
		String mime=matcher.group("mime"); String ext=matcher.group("ext"); String data=matcher.group("data");
		if(mime==null || data==null) return null; // если есть mime то есть и ext (такое регулярное выражение)
		
		long hash=Binary.longHash(data);
		
		Path path=cacheMedia.get(hash); // если не null, то юзаем уже созданный файл
		
		byte[] raw=null; if(path==null) raw = Base64.getDecoder().decode(data); // Будем создавать новый файл
		
		try {
			if(raw!=null) {
				path = Files.createTempFile(TMPFILE_PREFIX,"."+ext); // TMPFILE_PREFIXУникальныйКодПоВыборуСистемы.расширение
				path.toFile().deleteOnExit();
					Files.write(path, raw);  // Файл создается/перезаписывается здесь
				//path.toFile().deleteOnExit();
				// TODO Проверить автоудаление в WINDOWS ! 
				
				cacheMedia.put(hash, path); LOGGER.console("media tmp file: "+path.toAbsolutePath().toString()); // TODO debug
			}
			
			if(path!=null) {
				MediaFile ret=new MediaFile(); ret.mime=mime; ret.path=path;
				return ret;
			}
			else return null;
		  
		} catch (IOException e) { LOGGER.error(e.toString()); // XXX e.getMessage() не информативно
			return null;
		}
	}
	
	public static void fixLinks_VIDEO(Document doc) {
		NodeList tags=doc.getElementsByTagName("VIDEO");
		
		if(tags!=null) for(int i=0; i<tags.getLength(); i++) fixLink_VIDEO((Element)tags.item(i));
	}
	
	public static void fixLink_VIDEO(final Element tag) { // По аналогии с fixLink_AUDIO, только для заместителя есть атрибут poster

		final String src=tag.getAttribute("data-src"); NodeList sources=tag.getElementsByTagName("SOURCE"); // TODO sources list
		
		if( (src!=null && !src.isBlank()) || (sources!=null && sources.getLength()>0) ) {	                // TODO Списки альтернатив рассматриваем все как внешние ссылки

			MediaSize mediaSize=getMediaSize(tag);

			// Если нет preload то width=auto маленькая получается как если она не установлена, но после play - она подстроится
			
			tag.setAttribute("height", mediaSize.heightStr); tag.setAttribute("width", mediaSize.widthStr);
			tag.setAttribute("poster", sinemaSVGdata);
			
			String greyed=tag.getAttribute("class"); 
				   greyed= (greyed==null || greyed.isBlank()) ? "greyed" : greyed+" greyed"; // стили в атрибуте class разделяются пробелами
			tag.setAttribute("class",greyed);	
		
			if( (src!=null && !src.isBlank()) )	
				tag.setAttribute("onclick",""+ // Клик на poster активирует внешнюю ссылку.
										   "this.classList.remove('greyed');"+
										   "this.removeAttribute('poster');"+
										   "this.setAttribute('src',this.getAttribute('data-src'));"+
										   "this.play();"+
										   "this.removeAttribute('onclick');"+
										   "");
			else // Смотрим в теги <source> (это не список проигрывания - это тупо альтернативы, браузер использует первый какой поймет)
				tag.setAttribute("onclick",""+
						   				   "this.classList.remove('greyed');"+
						   				   "this.removeAttribute('poster');"+
										   "const sources=this.getElementsByTagName('SOURCE');"+ // Не null не undefined всегда
										   "	for(const source of sources) {"+
										   "		if(!source.getAttribute('src')) source.setAttribute('src',source.getAttribute('data-src')||'');"+
										   "	}"+
										   "this.play();"+
										   "this.removeAttribute('onclick');"+
										   "");
		}
	}
	
	
	public static void fixLinks_IFRAME(Document doc) {
		NodeList tags=doc.getElementsByTagName("IFRAME");
		
		if(tags!=null) for(int i=0; i<tags.getLength(); i++) fixLink_IFRAME((Element)tags.item(i));
	}
	public static void fixLink_IFRAME(final Element tag) {
		
		final String srcdoc=tag.getAttribute("srcdoc"); // Если атрибут пропустили сюда, то значит допустимая сигнатура контента и src - игнорится
		if(srcdoc!=null && !srcdoc.isBlank()) {
			
			MediaSize mediaSize=getMediaSize(tag);
			tag.setAttribute("height", mediaSize.heightStr); tag.setAttribute("width", mediaSize.widthStr);
			
			tag.setAttribute("sandbox","allow-forms allow-same-origin allow-scripts"); // allow-top-navigation
			tag.setAttribute("allow", "autoplay; picture-in-picture; "+//encrypted-media; "+
					  "layout-animations; legacy-image-formats; oversized-images");
			
			// Все - фрейм пойдет рендерится сразу со всеми скриптами и ссылками
			return;
		}
		
		
		
		final String src=tag.getAttribute("data-src");
		
		if( src!=null && !src.isBlank() ) {
			
			MediaSize mediaSize=getMediaSize(tag);
			
			tag.setAttribute("height", mediaSize.heightStr); tag.setAttribute("width", mediaSize.widthStr);
			
			final Element iframeIco=tag.getOwnerDocument().createElement("IMG"); 
				iframeIco.setAttribute("class","clickico"); iframeIco.setAttribute("border","1px"); // заместитель до клика
				iframeIco.setAttribute("height",mediaSize.heightStr); iframeIco.setAttribute("width",mediaSize.widthStr);
				iframeIco.setAttribute("src",iframeSVGdata);
			tag.getParentNode().insertBefore(iframeIco, tag); tag.setAttribute("hidden","true"); 		
			
			// Это принудительные настройки безопасности IFRAME после активации ссылки
				
			if(knownEmbedPattern.matcher(src).find())
			{
				tag.setAttribute("sandbox","allow-forms allow-same-origin allow-scripts");
				tag.setAttribute("allow", "autoplay; picture-in-picture; encrypted-media; clipboard-write; "+
										  "layout-animations; legacy-image-formats; oversized-images");
			}
			else {
				tag.setAttribute("sandbox","allow-forms allow-same-origin"); 					// главное не допускать allow-scripts
				tag.setAttribute("allow", "autoplay; picture-in-picture; encrypted-media; "+  	// clipboard-write; "+
										  "layout-animations; legacy-image-formats; oversized-images");
			}
			
			// FIXME чтобы проигрывание ссылки на видеофайл включилось нужен двойной клик на строчке запуска
			//		 (но этот вариант не корректный - должна быть ссылка на html-документ)
			//       также zoom вложенного во фрэйм контента не работает (идет zoom окружающего контента)
			//       но если width=100% (привязка фрейма к ширине объемлющего контейнера) то норм
			
			iframeIco.setAttribute("onclick",""+
					   "this.setAttribute('hidden','true');"+ // Заместитель - скрыт
					   "const next=this.nextElementSibling;"+ // Тег iframe сразу следующий (скрыт)
					   "	next.setAttribute('src',next.getAttribute('data-src'));"+
					   "	next.removeAttribute('hidden');"+
					   "this.removeAttribute('onclick');"+
					   "");
		}
	}
	
	public static void fixLinks_AUDIO(Document doc) {
		NodeList tags=doc.getElementsByTagName("AUDIO");
		
		if(tags!=null) for(int i=0; i<tags.getLength(); i++) fixLink_AUDIO((Element)tags.item(i));
	}
	public static void fixLink_AUDIO(final Element tag) { // src может быть как в тэге <audio> так и с суб-тегах <source> (браузер использует первый какой поймет)
		final String src=tag.getAttribute("data-src"); NodeList sources=tag.getElementsByTagName("SOURCE"); // TODO sources list
		
		if( (src!=null && !src.isBlank()) || (sources!=null && sources.getLength()>0) ) {					// TODO Списки альтернатив рассматриваем все как внешние ссылки
	
			// Добавляем иконку разблокировки кликом внешней ссылки
			final Element musicIco=tag.getOwnerDocument().createElement("IMG"); 
				musicIco.setAttribute("class","clickico");
			 	musicIco.setAttribute("height",String.format("%.0f",CLICKICO_HEIGHT));
			 	musicIco.setAttribute("src",musicSVGdata);
			tag.getParentNode().insertBefore(musicIco, tag);
			
			String greyed=tag.getAttribute("class"); 
				   greyed= (greyed==null || greyed.isBlank()) ? "greyed" : greyed+" greyed"; // стили в атрибуте class разделяются пробелами
			tag.setAttribute("class",greyed);	
		
			if( (src!=null && !src.isBlank()) )	
				musicIco.setAttribute("onclick",""+ // Клик на иконке активирует внешнюю ссылку.
												"const next=this.nextElementSibling;"+ // Тег audio сразу следующий
												"	next.classList.remove('greyed');"+
												"	next.setAttribute('src',next.getAttribute('data-src'));"+
												"	next.play();"+
												"this.removeAttribute('onclick');"+
												"");
			else // Смотрим в теги <source> (это не список проигрывания - это тупо альтернативы)
				musicIco.setAttribute("onclick",""+
												"const next=this.nextElementSibling;"+
												"	next.classList.remove('greyed');"+
												"	const sources=next.getElementsByTagName('SOURCE');"+ // Не null не undefined всегда
												"	for(const source of sources) {"+
												"		if(!source.getAttribute('src')) source.setAttribute('src',source.getAttribute('data-src')||'');"+
												"	}"+
												"	next.play();"+
												"this.removeAttribute('onclick');"+
												"");
		}
	}
	
	
	public static void fixLinks_IMG(Document doc) {
		NodeList tags=doc.getElementsByTagName("IMG"); 
		
		if(tags!=null) for(int i=0; i<tags.getLength(); i++) fixLink_IMG((Element)tags.item(i));
	}
	public static void fixLink_IMG(final Element tag) {
		// Если есть атрибут data-src, значит была внешняя ссылка (usemap вроде всегда начинается с # и не подпадает под forbid)
		final String src=tag.getAttribute("data-src"); 
		
		if(src!=null && !src.isBlank()) { // После forbid внешних ссылок		
			
			
			MediaSize mediaSize=getMediaSize(tag);
			
			tag.setAttribute("src", pictureSVGdata); tag.setAttribute("height", mediaSize.heightStr); // Заместитель до клика 
			
			String clickico=tag.getAttribute("class"); 
				   clickico= (clickico==null || clickico.isBlank()) ? "clickico" : clickico+" clickico"; // стили в атрибуте class разделяются пробелами
			tag.setAttribute("class",clickico); //tag.setAttribute("border","1px");
							
			// Обработчик удаления стиля clickico и восстановления внешней ссылки
			tag.setAttribute("onclick","this.classList.remove('clickico'); this.setAttribute('src',this.getAttribute('data-src'));");      
		}
	}	
	
	
	public static void fixLinks_A(Document doc) {
		NodeList tags=doc.getElementsByTagName("A"); 
		
		if(tags!=null) for(int i=0; i<tags.getLength(); i++) fixLink_A((Element)tags.item(i));
	}
	public static void fixLink_A(Element tag) {
		// Если есть атрибут data-href, значит была внешняя ссылка и нужно вырубать обработку клика по ней
		String href=tag.getAttribute("data-href"); 
		
		if(href!=null && !href.isBlank()) { // После forbid внешних ссылок		
			
			tag.setAttribute("href", href); // Вернули внешнюю ссылку на место
			
			tag.setAttribute("onclick","event.preventDefault()");      
			// Содержимое документа кешируется в виде строки html, поэтому предотвращать кликабельность нужно на уровне html
			// В противном случает после перезагрузки документа из кеша связь с обработчиком, устанавливаемым из java через 
			// ((EventTarget)tag).addEventListener("click", ... , будет утеряна
		}
		
		href=tag.getAttribute("href");
		if(href==null || href.isBlank()) { // Иногда ссылки используют как оформление без атрибута href
			tag.setAttribute("target", "_blank"); // Чтобы предотвратить открытие пустоты
		}
		else { // Устанавливаем внешний java-обработчик для открытия txid из блокчейна ( window.setMember("app",...) )
			final Matcher matcher=txidPattern.matcher(href);
			if(matcher.find()) {
				href=matcher.group("txid"); // не может быть null (группа заложена в совпадение паттерна)
				tag.setAttribute("class", "txid"); tag.setAttribute("onclick","javaApp.onClick('"+href+"')");
			}
			// FIXME: В одной txid может быть много выходов с данными (можно через установку фильтра показывать)
		}
		
		String text=tag.getTextContent();
		if(text==null || text.replaceAll("[\\p{Cf}]", "").isBlank()) { 
			// \p{Z} or \p{Separator}: any kind of whitespace or invisible separator. 
			// \p{Zs} or \p{Space_Separator}: a whitespace character that is invisible, but does take up space. 
			// \p{C} or \p{Other}: invisible control characters and unused code points. 
			// \p{Cf} or \p{Format}: invisible formatting indicator. 
			
			
			// В телеге картинки для предзагрузки оформляют через тег <a> без содержания
			if(href!=null && !href.isBlank()) {
				String ext=href.substring(href.lastIndexOf('.') + 1).toLowerCase();
				
				if(ext.equals("jpg") || ext.equals("jpeg") || ext.equals("png") || ext.equals("gif") || ext.equals("svg") || ext.equals("bmp")) {
					// Всовываем в пустое содержание ссылки картинку как в fixLink_IMG
					Element img=tag.getOwnerDocument().createElement("IMG");
					
					String defaultHeight=String.valueOf((int)Math.round(POSTER_HEIGHT));
					
					img.setAttribute("data-src", href); img.setAttribute("height", defaultHeight);
					
					fixLink_IMG(img);
					
					tag.appendChild(img);
				}
			}
			else {
				tag.setTextContent("link"); // Чтобы подсветить скрытые ссылки
			}
		}
		
	}
	
	public static void forbidLinks(Document doc) { 
		NodeList tags=doc.getElementsByTagName("*");
		for(int i=0; i<tags.getLength(); i++) { 	// Все теги перечисляет вне зависимости от вложенности
			Element tag = (Element)tags.item(i);	// Это должны быть все типа Element
			
			String tagName=tag.getTagName().toUpperCase();
			
			if(tagName.equals("STYLE")) { 
				// Тег где могут быть url в контенте:
				// @import "/style/print.css";
				// @import url("style/header.css");
				// background: url("bg.png");
				
				String tagContent=tag.getTextContent();
				if(tagContent!=null) 
					tag.setTextContent(
							tagContent.replaceAll(styleExternalRegExp,"disabled(").replaceAll(styleImportRegExp,"@disabled")
							// Обычные стили останутся не тронутыми
					); 
			}
			
			if(tag.hasAttributes()) {
				NamedNodeMap attrs = tag.getAttributes();
				for(int j=0; j<attrs.getLength(); j++) {
					Attr attr =(Attr)attrs.item(j);	// Это должны быть все типа Attr
					
					String attrName=attr.getName().toLowerCase();
					if(linkAttributes.contains(attrName)) { // исходные data-* игнорируются (пользовательские атрибуты самостоятельно движком не обрабатываются)
						String attrValue=attr.getValue(); 
						if(attrValue==null || attrValue.isBlank()) continue;
						
						if(attrValue.startsWith("#")||attrValue.startsWith("file:")||attrValue.startsWith("data:")) continue; // внутренние ссылки (без утечки траффика)
						
						// Сохраняем ссылку приводящую к утечке трафика в пользовательском атрибуте data-attrName для исключения прогруза,
						// но c возможностью дальнейшей обработки
						attr.setValue(""); // Исходный атрибут обнуляем
						tag.setAttribute("data-"+attrName,attrValue);
					}
					else if("style".equals(attrName) ) { // url() на внешние данные
						String attrValue=attr.getValue();
						if(attrValue!=null) 
							attr.setValue( attrValue.replaceAll(styleExternalRegExp, "disabled(") );
					}
				}
			}
		}
	}
	
	
	public static void removeScripts(Document doc) { 
		// Для удаления script и inline-скриптов в атрибутах событий и uri-схемах (псевдо-протоколах) в ссылках
		
		NodeList tags=doc.getElementsByTagName("*");
		for(int i=0; i<tags.getLength(); i++) { 	// Все теги перечисляет вне зависимости от вложенности
			Element tag = (Element)tags.item(i);	// Это должны быть все типа Element
			
			String tagName=tag.getTagName().toUpperCase();
			
			if(scriptTags.contains(tagName)) { 		// equals у строк делает посимвольное сравнение
				Node parent=tag.getParentNode();
				
				if(parent!=null) {parent.removeChild(tag); i--;} // i-- работаем по живому документу (перебор смещается)
				else LOGGER.error("Parent tag for "+tagName+" deletion not found"); 

				continue;
			}		
	
			if(tagName.equals("STYLE")) { // Тег где может быть url со встроенным скриптом ( background: url(javascript:...) )
				String tagContent=tag.getTextContent();
				if(tagContent!=null) 
					tag.setTextContent( // Заменит "javascript:" на "disabled:" и "expression(" на "disabled(" а остальное в стилях не тронит
							tagContent.replaceAll(uriScriptRegExp,"disabled:").replaceAll(styleExpressionRegExp,"disabled(")
					); 
				// атрибуты STYLE проверятся ниже (могут быть глобальные аттрибуты - обработчики событий)
			}
			
			// Опасные атрибуты
			if(tag.hasAttributes()) {
				NamedNodeMap attrs = tag.getAttributes();
				for(int j=0; j<attrs.getLength(); j++) {
					Attr attr =(Attr)attrs.item(j);
					
					String attrName=attr.getName().toLowerCase();
					// Все атрибуты обработчиков событий начинаются на "on" (action в формах это ссылка на обработчик сервера)
					if(attrName.startsWith("on")) {
						tag.removeAttributeNode(attr); j--;
						continue;
					}
					
					if( linkAttributes.contains(attrName) || attrName.startsWith("data-") ) { // ссылки в uri-схеме javascript:
						String attrValue=attr.getValue();
						if( attrValue!=null && uriScriptPattern.matcher(attrValue).find() ) {
							tag.removeAttributeNode(attr); j--; // Упомимание скрипта в атрибуте убьет весь атрибут
																// (мы сами в data-attr скрипты не засовываем)
						}
					}
					else if("style".equals(attrName)) {
						String attrValue=attr.getValue();
						if( attrValue!=null )
							attr.setValue( 
									attrValue.replaceAll(uriScriptRegExp,"disabled:").replaceAll(styleExpressionRegExp,"disabled(")
							);
					}
					else if("srcdoc".equals(attrName)) { // если есть srcdoc то src игнорируется
						// Пропускаем только встроенный контент с известной сигнатурой в iframe
						// Движок его рендерит как есть, - сразу в обход DocuentProperty и LoadWorker
						// Разрешения - в атрибутах sandbox и allow тега iframe
						String content=attr.getValue();
						if(content!=null) {
							Long signature=Binary.longHash(content.replaceAll("[\\s'\"]", "")); // Без учета пробельных и кавычек
							//LOGGER.console("signature: "+signature); // TODO debug
							if(!srcdocSignatures.contains(signature)) {
								tag.removeAttributeNode(attr); j--; // Не церемонимся с неизвестными srcdoc
							}
						}
					}
				}
			}
		}	
	}
	
	
	public static String emojiUnfix(String text) { // Обратная emojiEmbed перед отправкой
		final StringBuilder result=new StringBuilder();
		final Matcher matcher=emojiEmbedPattern.matcher( text );
		while(matcher.find()) {
			
			final String id = matcher.group("id");
			
			matcher.appendReplacement(result,hexToCodePoints(id));
		}
		matcher.appendTail(result);
		
		return result.toString();
	}
	
	public static String emojiEmbed(String text) { // Перед отправкой <img></img> -> hexToCodePoints(id)
		
		final StringBuilder result=new StringBuilder();
		final Matcher matcher=emojiPattern.matcher( text );
		while(matcher.find()) {
			final String emoji = matcher.group(); // Группа 0 всегда есть - это совпадение с шаблоном
			
			if(!cacheEmoji.containsKey(emoji)) { // Подгружаем в кэшь
				
				String nameSvg=codePointsToHex(emoji);
				
				InputStream in = ClassLoader.getSystemResourceAsStream("res/emoji/"+nameSvg+".svg");
				if (in==null) {
					LOGGER.warning("No emoji resource on file path: "+"res/emoji/"+nameSvg+".svg");
					
					// Ближайший родственник
					if(nameSvg.endsWith("-FE0F")) nameSvg=nameSvg.substring(0,nameSvg.length()-"-FE0F".length());
					else nameSvg=nameSvg+"-FE0F";
					
					in = ClassLoader.getSystemResourceAsStream("res/emoji/"+nameSvg+".svg");
					if(in==null) {
						LOGGER.warning("└ No emoji resource on file path: "+"res/emoji/"+nameSvg+".svg");						
					}
					else {
						LOGGER.warning("└ using: "+"res/emoji/"+nameSvg+".svg");					
					}
				}
				
				
				if(in==null) {
					cacheEmoji.put(emoji, null); // В кэше маркер не поддерживаемого emoji
				}
				else { // Грузим образ в кеш
					final byte[] raw=InputStreamToByteArray(in); // autocloseable in InputStreamToByteArray
					if (raw.length<=0) cacheEmoji.put(emoji, null);
					else {
						cacheEmoji.put(emoji,"<img class='emoji' id='"+nameSvg+"' src='data:image/svg+xml;base64,"+Base64.getEncoder().encodeToString( raw )+"'>");
					}
				}
			}
			// Уже обработка этого символа была - берем из кеша	
			
			final String svg=cacheEmoji.get(emoji);
			if(svg!=null) { // Файл ресурсов с прорисовкой был (образ в кешэ)
				matcher.appendReplacement(result,svg);
			}
			else matcher.appendReplacement(result,emoji); // Если образа svg нет, то оставляем как есть
		}
		matcher.appendTail(result);
		
		return result.toString();
	}
	
	public static String pictureEmbed(final File file, int height) throws Exception  {
		
		String path=file.getAbsolutePath();
		String ext=path.substring(path.lastIndexOf('.') + 1).toLowerCase(); 
		
		if(ext.isBlank() || ext.equals("jpg")) ext="jpeg";
		else if(ext.equals("svg")) ext="svg+xml";
		
		StringBuilder html=new StringBuilder();
		
		if(height<0) html.append("&nbsp;<img src='data:image/"+ext+";base64,");
		else 		 html.append("&nbsp;<img height="+height+" src='data:image/"+ext+";base64,");	
		html.append( Base64.getEncoder().encodeToString( Files.readAllBytes(file.toPath()) ) ).append("'>&nbsp;");
		
		return html.toString();
	}
	public static String animationEmbed(final File file, int height) { // Перед отправкой file: -> data: 
		
		String path=file.getAbsolutePath();
		String ext=path.substring(path.lastIndexOf('.') + 1).toLowerCase(); 
		
		if(ext.isBlank()) ext="mp4";
		
		StringBuilder html=new StringBuilder();
		
		if(height<0) html.append("&nbsp;<video autoplay loop muted type='video/"+ext+"' src='");
		else 		 html.append("&nbsp;<video autoplay loop muted type='video/"+ext+"' height="+height+" src='");
		html.append("file:"+path).append("'></video>&nbsp;");
		
		return html.toString();
	}
	public static String voiceEmbed(final File file) { // Перед отправкой file: -> data:
		
		String path=file.getAbsolutePath();
		String ext=path.substring(path.lastIndexOf('.') + 1).toLowerCase(); 
		
		if(ext.isBlank() || ext.equals("mp3")) ext="mpeg";
		
		StringBuilder html=new StringBuilder();
		
		html.append("&nbsp;<audio controls type='audio/"+ext+"' src='");
		html.append("file:"+path).append("'></audio>&nbsp;");
		
		return html.toString();
	}	
	
	public static String linkEmbed(String link, String desc) {return linkEmbed(link, desc, -1);}
	public static String linkEmbed(String link, String desc, int height) {

		StringBuilder html=new StringBuilder();
		
		
		if( link==null || link.isBlank() || uriScriptPattern.matcher(link).find() ) { // Скрипты в ссылках режутся
			if(desc!=null && !desc.isBlank()) html.append("&nbsp;<a href='' target='_blank'>"+desc+"</a>&nbsp;"); // Просто оформление как ссылка
			else return null;
		}
		else {
			
			final Matcher matcher=txidPattern.matcher(link);
			if(matcher.find()) {
				link=matcher.group("txid"); // не может быть null (группа заложена в совпадение паттерна)
				
				if(desc==null || desc.isBlank()) desc=link;
				
				html.append("&nbsp;<a class='txid' onclick=\"javaApp.onClick('"+link+"');\" href='#"+link+"'>"+desc+"</a>&nbsp;"); // Маркируем решеткой внутреннюю ссылку
			}
			else {
				
				if(link.startsWith("#")||link.startsWith("file:")||link.startsWith("data:"))  {// внутренние ссылки (без утечки траффика)
					
					if(desc==null || desc.isBlank()) desc=link;
					
					html.append("&nbsp;<a href='"+link+"'>"+desc+"</a>&nbsp;");
				}
				else {
					// Обработка медиа ссылок 
					// XXX Не документированная возможность принудительно указать типа ссылки в начале строки:
					// audio=<ссылка>
					// video=<ссылка>
					// image=<ссылка>
					// link=<ссылка>
					
					String mime="";
					
					if(link.startsWith("audio=")) { mime="audio"; link=link.substring(6).trim(); }
					if(link.startsWith("video=")) { mime="video"; link=link.substring(6).trim(); }
					if(link.startsWith("image=")) { mime="image"; link=link.substring(6).trim(); }
					
					String ext=link.substring(link.lastIndexOf('.') + 1).toLowerCase();
					
					// FIXME в svg могут быть скрипты
					if(mime.equals("image") || ext.equals("jpg")  || 
							                   ext.equals("jpeg") || 
							                   ext.equals("png")  || 
							                   ext.equals("gif")  || 
							                   ext.equals("svg")  || 
							                   ext.equals("bmp")   )    
					{ 	// Внешняя картинка - Вставляем с прогрузом чтобы было видно как будет и чтобы постеры не слались вместо линков				
						if(desc==null || desc.isBlank()) desc=link;
						
						if(height<0) html.append("&nbsp;<img src='");
						else         html.append("&nbsp;<img height="+height+" src='");
						html.append(link).append("' alt='"+desc+"'>&nbsp;");
							
					}
					else if(mime.equals("audio") || ext.equals("mp3")  || 
							                        ext.equals("m4a")  || 
							                        ext.equals("mpeg") || 
							                        ext.equals("wav")  ||
							                        ext.equals("aac")  	)	// XXX Только то что может воспроизводить WebEngine
					{ 					
						if(desc==null || desc.isBlank()) desc=link;
						
						html.append("&nbsp;<audio controls preload='metadata' src='"+link+"'>"+desc+"</audio>&nbsp;");
						
					}
					else if( (mime.equals("video") && !youtubeIdPattern.matcher(link).find()) || ext.equals("mp4") ) 	// XXX Только то что может воспроизводить WebEngine
					{
						if(desc==null || desc.isBlank()) desc=link;
						
						if(height<0) html.append("&nbsp;<video controls preload='metadata' src='");
						else         html.append("&nbsp;<video controls preload='metadata' height="+height+" src='");
						html.append(link).append("'>"+(desc)+"</video>&nbsp;");
					}
					else {
						 if(!link.startsWith("link=") && knownEmbedPattern.matcher(link).find()) {
								final Matcher idMatcher = youtubeIdPattern.matcher(link);
								if(idMatcher.find()) { 
									String id=idMatcher.group();
									if(id!=null && !id.isBlank()) { // Все - точно внедрение iframe
										
										if(desc==null || desc.isBlank()) desc=link;
										
										link="https://www.youtube.com/embed/"+id;
										
										if(height<0) html.append("&nbsp;<iframe allow='autoplay; picture-in-picture; encrypted-media; clipboard-write' src='");
										else  {
											String width=String.valueOf((int)Math.round(height*1.7777777777777777d));
											html.append("&nbsp;<iframe height="+height+" width="+width+" allow='autoplay; picture-in-picture; encrypted-media; clipboard-write' src='");
										}
										html.append(link).append("'>"+desc+"</iframe>&nbsp;"); // frameborder=0
									
										return html.toString();
									}
								}
						 }
						
						if(link.startsWith("link=")) link=link.substring(5).trim();
						 
						if(desc==null || desc.isBlank()) desc=link;
						 
						html.append("&nbsp;<a href='"+link+"' onclick='event.preventDefault()'>"+desc+"</a>&nbsp;"); // Клик на прогруз страницы задавален сразу
						
					}
				}
			}
		}
		
		return html.toString();
	}
	
	private static class MediaSize {String widthStr; String heightStr;}
	private static MediaSize getMediaSize(final Element tag) {
		String widthStr=tag.getAttribute("width"); String heightStr=tag.getAttribute("height"); // Может быть и процентная запись от размера родителя
		boolean hasWidth = (widthStr!=null && !widthStr.isBlank());
		boolean hasHeight = (heightStr!=null && !heightStr.isBlank());
		MediaSize result=new MediaSize();
		
		if(hasHeight && !hasWidth) { 
			try {
				result.heightStr=heightStr;
				if(heightStr.endsWith("%"))
					result.widthStr= String.format("%.0f%%",1.7777777777777777d*Integer.parseInt(heightStr.substring(0, heightStr.length()-1)));
				else
					result.widthStr= String.format("%.0f",1.7777777777777777d*Integer.parseInt(heightStr));
			}
			catch(NumberFormatException e) { // Если в атрибуте height белеберда или auto
				result.widthStr= String.format("%.0f",1.7777777777777777d*POSTER_HEIGHT);
				if(!heightStr.toLowerCase().equals("auto")) result.heightStr= String.format("%.0f",POSTER_HEIGHT);
			}
			return result;
		}
		
		if(!hasHeight && hasWidth) { 
			try {
				result.widthStr=widthStr;
				if(widthStr.endsWith("%"))
					result.heightStr= String.format("%.0f%%",0.5625d*Integer.parseInt(widthStr.substring(0, widthStr.length()-1)));
				else
					result.heightStr= String.format("%.0f",0.5625d*Integer.parseInt(widthStr));
			}
			catch(NumberFormatException e) { // Если в атрибуте width белеберда или auto
				result.heightStr= String.format("%.0f",POSTER_HEIGHT);
				if(!widthStr.toLowerCase().equals("auto")) result.widthStr= String.format("%.0f",1.7777777777777777d*POSTER_HEIGHT);
			}
			return result;
		}
		
		if(!hasHeight && !hasWidth) {
			result.heightStr= String.format("%.0f",POSTER_HEIGHT);
			result.widthStr= String.format("%.0f",1.7777777777777777d*POSTER_HEIGHT);
			return result;
		}
		
		// FIXME Белебирду в атрибутах продублирует на выход
		result.heightStr=heightStr;
		result.widthStr=widthStr;
		
		return result;
	}
	
}
