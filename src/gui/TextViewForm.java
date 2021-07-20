package gui;

import static application.Main.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javafx.geometry.Bounds;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.web.WebView;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import util.Css;

public class TextViewForm extends Stage {

	static final double OFFSET = Css.getTextBounds("┃⟺",18).getHeight();
	
	private Scene scene;
		private WebView text; // Это супер быстро по сравнению с TextArea !
	
	static final String TMPDIR_PREFIX=WebView.class.getSimpleName();
    static File TEMP_DIR=null;	
		
	static {
		java.net.CookieHandler.setDefault(null); // Вырубаем кукисы
		
		try { Path tmp=Files.createTempDirectory(TMPDIR_PREFIX); if(tmp!=null) {TEMP_DIR=tmp.toFile(); TEMP_DIR.deleteOnExit(); } } catch (IOException ignore) {}
		// FIXME Директории с lock-файлами авто не удаляются
	}
	
	private void initSize() {this.setHeight(SCREEN_HEIGHT*H1*H2); this.setWidth(SCREEN_WIDTH*W1*W2);}
	
	public TextViewForm() { super();
		this.initStyle(StageStyle.UTILITY); this.setResizable(true); initSize();
		
		text = new WebView(); text.getStyleClass().add("text-view"); // .text-view {} // FIXME WebEngine перекрывает стили своими
		
			text.getEngine().setUserDataDirectory(TEMP_DIR);
		
			text.getEngine().setCreatePopupHandler((features)->null); // To block the popup, a handler should return null. 
			text.getEngine().setJavaScriptEnabled(false);
			text.getEngine().getHistory().setMaxSize(0);
		
		text.getEngine().setUserStyleSheetLocation("data:,"+
				"* { background-color: transparent; }"+ 
		"");
			
		
		scene = new Scene(text);
		
		this.setScene(scene);
		
		this.setOnShown((ev)-> { initSize(); this.requestFocus(); }); 
		
		this.addEventHandler(KeyEvent.KEY_PRESSED, (ev)->{
			if(ev.getCode()==KeyCode.ESCAPE) {
				this.close();
			}
		});
		this.setOnCloseRequest((ev)->{
		});
	}
	
	private Parent owner;
	TextViewForm(Parent owner) { this(); this.owner=owner;
		if(owner!=null) {
			
			this.initOwner(owner.getScene().getWindow()); this.initModality(Modality.WINDOW_MODAL);  // Если родитель есть, то MODAL-mode
			scene.getStylesheets().setAll(owner.getScene().getStylesheets()); // Наследование всех стилей от родителя
			
			Bounds bounds=owner.localToScreen(owner.getBoundsInLocal());
			
			this.setX(bounds.getMinX()+OFFSET);
			this.setY(bounds.getMinY()+OFFSET);
		}
		
	}
	
	@Override public void hide() { // Вызывается всегда вне зависимости от способа закрытия
		
		this.setText(null);
		
		super.hide(); if(owner!=null) owner.requestFocus();
	}
	
	/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	
	public void setText(String str) {
		text.getEngine().loadContent(str,"text/plain"); // "text/plain" - в этом режиме wordwrap автоматический
	}

	// show(); showAndWait(); - Наследуются
	
}
