package gui;

import static application.Main.*;

import static application.Main.rpcCommander;

import java.util.Arrays;

import coredrv.RPCCommander;
import coredrv.RPCCommander.Response;
import javafx.geometry.Bounds;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import util.Css;

public class RPCConsoleForm extends Stage {

	static final double OFFSET = Css.getTextBounds("┃⟺",18).getHeight();
	
	private Scene scene;
		private TextArea text;
	
	
	private final RPCCommander rpc;
		
	private void initSize() {this.setHeight(SCREEN_HEIGHT*H1*H2); this.setWidth(SCREEN_WIDTH*W1*W2);}
	
	public RPCConsoleForm() { super();
		this.initStyle(StageStyle.UTILITY); this.setResizable(true); initSize();
	
		if (rpcCommander==null) throw new NullPointerException("rpcCommander is null"); // Без источника данных работать не может
		this.rpc=rpcCommander; // XXX глобальный rpcCommander создается не статически и в статическом контексте не определен 
		
		
		text = new TextArea(">> "); text.getStyleClass().add("console");
		
		
		scene = new Scene(text);
		
		this.setScene(scene);
		
		this.setOnShown((ev)->{ initSize(); this.requestFocus(); }); 
		
		this.addEventHandler(KeyEvent.KEY_PRESSED, (ev)->{
			if(ev.getCode()==KeyCode.ESCAPE) {
				this.close();
			}
		});
		this.setOnCloseRequest((ev)->{
		});
		
		
		text.requestFocus(); text.end();
		
		
		text.addEventFilter(KeyEvent.KEY_PRESSED, (ev)->{ 
			if(ev.getCode()==KeyCode.ENTER) { ev.consume();
			
				String txt=text.getText();
				String lastLine=txt.substring(txt.lastIndexOf('\n')+1).
						            replaceFirst("[> ]*", "").		// Минус консольное приглашение
						            replaceAll(" +", " ").			// Минус лишние пробелы между параметрами
						            replaceAll("(?<=[:,]) ","").    // Минус пробелы внутри json объектов и массивов (чтобы split(" ") по параметрам не баговал)
						            replaceAll("'","");             // Минус кавычки из справки (это примеры для bash, здесь одинарные кавычки не нужны)
				
				if(!lastLine.isBlank()) {
				
					String[] parts=lastLine.split(" ");
					
					String command=parts[0]; 
					String[] params={}; if(parts.length>1) params=Arrays.copyOfRange(parts, 1, parts.length);
					
					//LOGGER.console("command: "+command); // TODO debug
					//LOGGER.console("params: "+Arrays.toString(params)); // TODO debug
					
					text.end(); text.appendText("\n");
					
					Response res=rpc.sendCommand(command, params);
					if(res.data!=null) {
						String out=rpc.convert(res.data); if(out==null || out.equals("null")) out="OK";
						text.appendText(out);
					}
					else text.appendText("error "+res.code+" ("+res.message+")");
				}
						
				text.appendText("\n>> "); text.end(); //text.appendText("");
				
				text.setScrollTop(Double.MAX_VALUE); // FIXME первый autoscroll не срабатывает (пока вручную вниз не открутишь разок)

				
				//text.selectPositionCaret(text.getLength()); 
				//text.deselect(); 
				
				
			/*	Node node=text.lookup(".scroll-bar:vertical");
				if(node!=null) {
					LOGGER.console(".scroll-bar:vertical"); // TODO debug
					
					ScrollBar scrollBar = (ScrollBar)node;
					scrollBar.setValue(scrollBar.getMax());
				} */
 
				return;
			}
			
		});
		
	}
	
	private Parent owner;
	public RPCConsoleForm(Parent owner) { this(); this.owner=owner;
		if(owner!=null) {
			
			this.initOwner(owner.getScene().getWindow()); this.initModality(Modality.WINDOW_MODAL);  // Если родитель есть, то MODAL-mode
			scene.getStylesheets().setAll(owner.getScene().getStylesheets()); // Наследование всех стилей от родителя
			
			Bounds bounds=owner.localToScreen(owner.getBoundsInLocal());
			
			this.setX(bounds.getMinX()+OFFSET);
			this.setY(bounds.getMinY()+OFFSET);
		}
		
	}
	
	@Override public void hide() { // Вызывается всегда вне зависимости от способа закрытия
		
		text.setText(null);
		
		super.hide(); if(owner!=null) owner.requestFocus();
	}
	
	/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	

	// show(); showAndWait(); - Наследуются
	
}
