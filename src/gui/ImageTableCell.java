package gui;

import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.util.Callback;


public class ImageTableCell<S> extends TableCell<S, String> { // String item - путь загрузки (когда ячейка не видна, то желательная сборка мусора)
	
	// Value Factory - внутренне биндится на модель S по именам свойств. И ее задача тупо добывать значение типа T из данного свойства
	// Далее именно это значение подается в updateItem, которая уже занимается отображением
	
	public static <S> Callback<TableColumn<S,String>, TableCell<S,String>> forTableColumn() {
        return (TableColumn<S, String> param) -> new ImageTableCell<S>();
    }
	
	private final ImageView imageView;
    
    public ImageTableCell() {
        this.getStyleClass().add("image-view-table-cell");

        imageView=new ImageView();
        	imageView.setPreserveRatio(true);        
	        imageView.fitWidthProperty().bind(this.widthProperty().subtract(12d)); // FIXME hardcoded
        
        this.itemProperty().addListener( (obs, oldItem, newItem) -> { // Можно и в updateItem, но здесь логичней
        	
        	//LOGGER.console("ImageTableCell itemProperty="+newItem); // TODO debug
        	
    		if(newItem!=null && !newItem.isBlank()) imageView.setImage(new Image(newItem,false)); // false - не создает адовую кучу потоков 
    		else {
    			imageView.setImage(null); // Суммарно png-шки занимают где-то 6Mb всего
    		}
    		
        });
    }

    @Override public void updateItem(String item, boolean empty) {
        super.updateItem(item, empty);
        
        if (item==null || empty) {
            setGraphic(null);
        } else {        	
        	setGraphic(imageView);
        }
    }
}
