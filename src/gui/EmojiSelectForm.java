package gui;

import static application.Main.*;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.geometry.Bounds;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import util.Css;
import util.LOGGER;
import static util.Binary.hexToCodePoints;

public class EmojiSelectForm extends Stage {

	static final double OFFSET = Css.getTextBounds("┃⟺",18).getHeight();
	static final double EMOJI_SIZE = Css.getTextBounds("┃⟺",18).getWidth()*1.25;
	static int COLUMNS = (int)(SCREEN_WIDTH*W1*W2 / EMOJI_SIZE);
	static final String EMOJI_RESOURCE="res/emoji/png/";
	static final String EMOJI_EXT="png";
	
	private Scene scene;
		TableView<String[]> tableView; // В любом случае нужна модель для удержания данных в строке таблицы (без пропертей разрулим в ValueFactory)
	
	private String resultCodepoint=null;
		
	private boolean focusInit=false;
	
	private void initSize() {this.setHeight(SCREEN_HEIGHT*H1*H2); this.setWidth(SCREEN_WIDTH*W1*W2);}
	
	public EmojiSelectForm() { super();
		this.initStyle(StageStyle.UTILITY); this.setResizable(true); initSize();
	
		tableView = new TableView<>(); tableView.getStyleClass().add("image-view-table"); // .image-view-table .column-header-background {}
			for(int i=0; i<COLUMNS; i++) {
				TableColumn<String[], String> column = new TableColumn<>(); // String - путь к картинке
				column.setResizable(false); //column.setReorderable(false); 
				column.prefWidthProperty().bind(this.widthProperty().divide(COLUMNS).subtract(2d)); // FIXME hardcoded
				column.setMinWidth(EMOJI_SIZE);
				
				column.setCellFactory(ImageTableCell.forTableColumn());
				final int index=i; column.setCellValueFactory( (cellData) -> { // index - в лямбда-захвате
					String[] row=cellData.getValue(); if(row==null || row.length<=index) return null;
					return new ReadOnlyStringWrapper(row[index]);
				});
				
				tableView.getColumns().add(column);
			}
		tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
				
		tableView.getSelectionModel().setCellSelectionEnabled(true);
		
		tableView.getFocusModel().focusedCellProperty().addListener((obs, oldPos, newPos)->{
			if(!focusInit) { focusInit=true; 
				// При открытии окна фокус устанавливается на нулевую строку - нужно сбросить, 
			    // чтобы клик на первой cell был отлавливаемым через фокус
				if(newPos.getRow()>=0) {
					tableView.getFocusModel().focus(-1);
					return;
				}
			}
			
			if(newPos.getRow()>=0) { // emoji выбрана
				String png= (String)newPos.getTableColumn().getCellData( newPos.getRow() );
				if(png==null) resultCodepoint=null;
				else {
					String hex=png.substring(EMOJI_RESOURCE.length(), png.length()-EMOJI_EXT.length()-1);
					resultCodepoint=hexToCodePoints(hex);
					LOGGER.console("emoji "+resultCodepoint); // TODO debug
					
					this.close(); //tableView=null;
				}
			}
		});
		
	
		tableView.setItems(FXCollections.observableArrayList()); // Доступ через getItems() (not null)
		
		
		Path emojisDir; FileSystem fileSystem=null;
		try { // Способ читать список файлов в каталоге res и на диске и в jar-файле
			URI uri = ClassLoader.getSystemResource(EMOJI_RESOURCE).toURI();
			
			//LOGGER.console(uri.toString()); // TODO debug
			
			if (uri.getScheme().equals("jar")) {
	            fileSystem = FileSystems.newFileSystem(uri, Collections.<String, Object>emptyMap());
	            emojisDir = fileSystem.getPath(EMOJI_RESOURCE);
	        } else {
	        	emojisDir = Paths.get(uri);
	        }
			
			//LOGGER.console(emojisDir.toString()); TODO debug
			
			final List<String> emojis=new ArrayList<>();
			
			Files.walkFileTree(emojisDir, new SimpleFileVisitor<Path>() { 
				@Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
					//LOGGER.console( file.getName(file.getNameCount()-1).toString() ); // TODO debug
					
					
					String emoji=file.getName(file.getNameCount()-1).toString(); 
					if(emoji.toLowerCase().endsWith(EMOJI_EXT)) emojis.add(emoji);
					
					return FileVisitResult.CONTINUE;                   		 
				}
			});

			if(emojis.size()>0) {
				
				emojis.sort((String a,String b)->{
					
					if(a.length()<b.length()) return -1;
					if(a.length()>b.length()) return +1;
					
					return a.compareToIgnoreCase(b);
				});
				
				for(int i=0; i<emojis.size(); i+=COLUMNS) {
					String[] row=new String[COLUMNS];
					for(int j=0; j<COLUMNS; j++) {
						if(i+j < emojis.size()) row[j]=EMOJI_RESOURCE+emojis.get(i+j); else row[j]=null;	
					}

					tableView.getItems().add(row);
				}
			}	
					
			
		} catch (Exception e) { 
			e.printStackTrace(); // Не должно быть никогда в рабочей программе
		}
		finally {
			// Закрывать обязательно! иначе при следующем создании FileSystemAlreadyExistsException
			if(fileSystem!=null) try {fileSystem.close();} catch (IOException ignore) {}  
		}
		
		scene = new Scene(tableView);
		
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
	public EmojiSelectForm(Parent owner) { this(); this.owner=owner;
		if(owner!=null) {
			
			this.initOwner(owner.getScene().getWindow()); this.initModality(Modality.WINDOW_MODAL);  // Если родитель есть, то MODAL-mode
			scene.getStylesheets().setAll(owner.getScene().getStylesheets()); // Наследование всех стилей от родителя
			
			Bounds bounds=owner.localToScreen(owner.getBoundsInLocal());
			
			this.setX(bounds.getMinX()+OFFSET);
			this.setY(bounds.getMinY()+OFFSET);
		}
	}
	
	@Override public void hide() { // Вызывается всегда вне зависимости от способа закрытия
		
		//tableView=null;
		
		super.hide(); if(owner!=null) owner.requestFocus();
		
		System.gc();
	}
	
	///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	
	public String getResult() {return resultCodepoint;}
	// show(); showAndWait(); - Наследуются. После юзать getResult()
	
}
