package gui;

import java.util.ArrayList;
import java.util.List;

import java.lang.reflect.Method;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ObservableValue;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableColumn.CellDataFeatures;
import javafx.scene.layout.VBox;
import javafx.util.Callback;

import util.LOGGER;

public class LabelsBoxTableCell<S> extends TableCell<S, String[]> {
	
	// Value Factory - внутренне биндится на модель S по именам свойств. И ее задача тупо добывать значение типа T из данного свойства
	// Далее именно это значение подается в updateItem, которая уже занимается отображением
	
	public static <S> Callback<TableColumn<S,String[]>, TableCell<S,String[]>> forTableColumn() {
        return (TableColumn<S, String[]> param) -> new LabelsBoxTableCell<S>();
    }
	
	
	public static <S> Callback<CellDataFeatures<S, String[]>, ObservableValue<String[]>>  forValueFactory(String... props) { 
		// props - Имена нужных строковых свойств в модели, которые будут объединены в VBox
		
		return new CallbackValueFactory<S>(props);
	}
	
	private static class CallbackValueFactory<S> implements Callback<CellDataFeatures<S, String[]>, ObservableValue<String[]>> {
		private String[] propNames; 		// Сохраняем для назначения id css
		private StringProperty[] propRefs;	// и биндингов
		
		CallbackValueFactory(String... props) {
			propNames=props;
			propRefs=new StringProperty[props.length]; // Зарезервировали место
		}
		
		public ObservableValue<String[]> call(CellDataFeatures<S, String[]> param) {
			// после изучения исходников javafx.scene.control.cell.PropertyValueFactory ( return getCellDataReflectively() )
			
			final S rowData=param.getValue(); if (rowData == null) return null;
			
			final List<String> propValues = new ArrayList<>();
			
			for(int i=0; i<propNames.length; i++) {
				
					Method propertyRef=null;
					try {
						propertyRef = rowData.getClass().getMethod(propNames[i]+"Property");
						
						if (propertyRef != null) {
							
							final String value = (propRefs[i]=(StringProperty)propertyRef.invoke(rowData)).getValue();
			                propValues.add( value);
			            }
					}
					catch(Exception e) {
						LOGGER.error(""+
								"Can not retrieve String property '" + propNames[i] +
								"' in static Property Value Factory from: " + LabelsBoxTableCell.class +
								" with provided class type: " + rowData.getClass() + 
								"\n"+ e +	
						"");
					}
			}
			
			final String[] value=propValues.toArray(new String[propValues.size()]);
			
			return new ReadOnlyObjectWrapper<String[]>(value); // Это без биндингов (однократно при table.refresh())
	    }		
	}
	
	
    private final VBox vbox;
    	private Label[] labels=null;
    
    public LabelsBoxTableCell() {
        this.getStyleClass().add("labels-box-table-cell");

        vbox = new VBox();
        
        this.itemProperty().addListener( (obs, oldItem, newItem) -> { // Можно и в updateItem, но здесь логичней
    		if(newItem!=null)
    		{
    			final CallbackValueFactory<S> vf=(CallbackValueFactory<S>)this.getTableColumn().getCellValueFactory();
    			
    			String[] propNames= vf.propNames;
    			StringProperty[] propRefs= vf.propRefs;
    			
    			labels=new Label[newItem.length]; 
	    		for(int i=0; i<newItem.length; i++) {
	    			
	    			 labels[i]=new Label(newItem[i]);
	    			 
	    			 if(propRefs[i]!=null) { // Пля писец я вшарил в "кишки" tableView...
	    				 labels[i].textProperty().unbind(); labels[i].textProperty().bind(propRefs[i]); // Устанавливаем наблюдаемую связь с моделью
	    			 }						   

	    			 if(propNames!=null && propNames[i]!=null) { // Назначаем CSS Id
	    				 labels[i].setId(propNames[i]);	// .labels-box-table-cell #<property name>.label {}
	    			 }
	    		}
    		}
    		else labels=null;
        });
    }

    @Override public void updateItem(String[] item, boolean empty) {
        super.updateItem(item, empty);

        if (item==null || empty) {
            setGraphic(null);

        } else {        	
        	vbox.getChildren().setAll(labels); // перезапись
        	setGraphic(vbox);
        }
    }
}
