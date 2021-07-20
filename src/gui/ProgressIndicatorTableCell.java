package gui;

import javafx.beans.value.ObservableValue;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.util.Callback;

public class ProgressIndicatorTableCell<S> extends TableCell<S, Double> {

    public static <S> Callback<TableColumn<S,Double>, TableCell<S,Double>> forTableColumn() {
        return (TableColumn<S, Double> param) -> new ProgressIndicatorTableCell<S>();
    }
    
    private final ProgressIndicator progressIndicator;
    
    private ObservableValue<Double> observable;

    public ProgressIndicatorTableCell() {
        this.getStyleClass().add("progress-indicator-table-cell"); // .tx-view .progress-indicator-table-cell {}

        this.progressIndicator = new ProgressIndicator(0);
        this.progressIndicator.setMaxWidth(Double.MAX_VALUE);
    }

    @Override public void updateItem(Double item, boolean empty) {
        super.updateItem(item, empty);

        if (empty || item==null) {
            setGraphic(null);
        } else {
            progressIndicator.progressProperty().unbind();

            final TableColumn<S,Double> column = getTableColumn();
            observable = column == null ? null : column.getCellObservableValue(getIndex());

            if (observable != null) {
                progressIndicator.progressProperty().bind(observable);
            } else {
                progressIndicator.setProgress(item);
            }

            setGraphic(progressIndicator);
        }
    }
}
