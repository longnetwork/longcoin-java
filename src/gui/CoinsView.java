package gui;

import static application.Main.*;

import util.Css;
import util.L10n;
import util.LOGGER;
import util.Threads;
import gui.StatusController.Listener;
import java.text.NumberFormat;

import coredrv.RPCCommanderCached;
import coredrv.RPCCommander.StringResponse;
import javafx.application.Platform;
import javafx.geometry.HPos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.control.ToolBar;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.text.Text;

public class CoinsView extends GridPane { // Будем юзать чужие контроллеры для динамической информации если потребуется

    Label payTo=new Label(L10n.t("Pay To")+":");
        TextField addressField=new TextField();
    Label alias= new  Label(L10n.t("Alias")+":");
        TextField accountField=new TextField();
            Button storeAlias=new Button(L10n.t("Save Alias"));
    Label amount= new Label(L10n.t("Amount")+", LONG:");
        TextField amountField=new TextField();
                CheckBox subtractFee=new CheckBox(L10n.t("Subtract Fee"));
    Label shortMsg= new Label(L10n.t("Short Message")+":");
        TextField smsField=new TextField();
            Label sender= new Label(L10n.t("Sender")+":");
            TextField fromField=new TextField();

    AddressBookView addressBook=new AddressBookView();

    ToolBar toolBar=new ToolBar();
        HBox info=new HBox();       // .coins-view #payto HBox .label {} .coins-view #payto HBox Text {}
            Label feeField=new Label(L10n.t("Transaction Fee")+":"); Text feeText=new Text("1 LONG/kB");
            Label balanceField=new Label(L10n.t("Balance")+":"); Text balanceText=new Text("00000000000 LONG");

        Separator separator=new Separator();
        Button ok=new Button(L10n.t("Send Payment"));

    JobInfo jobInfo=addressBook.jobInfo;

    private final RPCCommanderCached rpc;

    public CoinsView() { super();

        if (rpcCommander==null) throw new NullPointerException("rpcCommander is null"); // Без источника данных работать не может
        this.rpc=rpcCommander; // XXX глобальный rpcCommander создается не статически и в статическом контексте не определен


              this.add(payTo,0,0);   this.add(addressField,1,0);
              this.add(alias,0,1);   this.add(accountField,1,1);   this.add(storeAlias,2,1);
             this.add(amount,0,2);    this.add(amountField,1,2);  this.add(subtractFee,2,2); this.add(sender,3,2);
           this.add(shortMsg,0,3);       this.add(smsField,1,3,2,1);                         this.add(fromField,3,3);

        this.add(addressBook,0,4,4,1);
            this.add(toolBar,0,5,4,1);

        GridPane.setHalignment(payTo, HPos.RIGHT); GridPane.setHalignment(addressField, HPos.LEFT);
        GridPane.setHalignment(alias, HPos.RIGHT); GridPane.setHalignment(storeAlias, HPos.LEFT);
        GridPane.setHalignment(amount, HPos.RIGHT); GridPane.setHalignment(subtractFee, HPos.LEFT); GridPane.setHalignment(sender, HPos.CENTER);
        GridPane.setHalignment(shortMsg, HPos.RIGHT);

        addressField.setPrefWidth(Css.getTextBounds("1GztQxGTKdEFhctBhR38wR8skjqkd4Cqt8 ", 18).getWidth());
        //fromField.setPrefWidth(Css.getTextBounds("1GztQxGTKdEFhctBhR38wR8skjqkd4Cqt8 ", 18).getWidth());

        GridPane.setHgrow(smsField, Priority.ALWAYS); smsField.setPrefWidth(Double.MAX_VALUE);
        GridPane.setVgrow(addressBook, Priority.ALWAYS); addressBook.setPrefWidth(Double.MAX_VALUE);


        HBox.setHgrow(info, Priority.ALWAYS);

        toolBar.getItems().addAll(info,separator,ok); toolBar.setId("payto"); // .coins-view #payto.tool-bar {}
            info.getChildren().addAll(feeField,feeText,balanceField,balanceText); info.setId("payto"); //.coins-view #payto HBox {}

        addressField.setPromptText(L10n.t("DragOrPasteTheAddress"));
        fromField.setPromptText(L10n.t("DragOrPasteTheAddress")); fromField.setDisable(true);
        accountField.setPromptText(L10n.t("addressAlias"));

        amountField.textProperty().addListener((obs, oldValue, newValue)->{ // Только целое число может быть введено
            if(newValue!=null) {
                if (!newValue.matches("\\d*")) {
                    int end=newValue.lastIndexOf('.'); if(end<0) end=newValue.length();
                    amountField.setText(newValue.substring(0,end).replaceAll("[^\\d]", ""));
                }
            }
        });

        dragInit();

        addressField.textProperty().addListener((obs, oldValue, newValue)->{
            if(newValue!=null) {
                AddressBookModel rec=addressBook.getItemByAddress(addressField.getText().trim());
                if(rec!=null) accountField.setText(rec.getAccount());
            }
        });

        storeAlias.setOnAction((ev)->{
            final String account=accountField.getText();
            final String address=addressField.getText();

            if(addressBookController!=null) {
                jobInfo.progress(""); storeAlias.setDisable(true);
                Threads.runNow(()->{
                    final String adr=addressBookController.setAccount( address!=null ? address.trim(): ""  , account!=null ? account: "" );
                    if(adr!=null) addressBook.autoSelectAddress(adr);

                    Platform.runLater(()->storeAlias.setDisable(false));
                });
            }
        });

        ok.setOnAction((ev)->{
            long a=0; try {a=Long.parseLong(amountField.getText());} catch(NumberFormatException ignore) {}

            final String address=addressField.getText(); final String from=fromField.getText();
            final long amount=a;
            final boolean subtractFee=this.subtractFee.isSelected();
            final String sms=smsField.getText();

            jobInfo.progress(""); ok.setDisable(true);
            Threads.runNow(()->{
                StringResponse res;
                if (sms!=null && !sms.isBlank())
                    res=rpc.sendToAddress(address!=null ? address.trim(): "", amount, sms, from!=null ? from.trim(): "");
                else
                    res=rpc.sendToAddress(address!=null ? address.trim(): "", amount, subtractFee);

                if(res.isOk()) Platform.runLater(()->jobInfo.progress(null));
                else Platform.runLater(()->{
                    jobInfo.alert(res.message);
                });

                Platform.runLater(()->ok.setDisable(false));
            });

        });

        smsField.textProperty().addListener((obs, oldValue, newValue)->{
            // TODO Для учета subtractFee когда создаем сложную транзакцию требуется интеллект внутри rpc.sendToAddress(address, amount, sms, from)
            if(newValue!=null && !newValue.isBlank()) {
                subtractFee.setDisable(true); fromField.setDisable(false);
            }
            else {
                subtractFee.setDisable(false); fromField.setDisable(true);
            }
        });

        this.getStyleClass().add("coins-view");
        // layoutInit() в layoutChildren()
    }
    private StatusController statusController=null;
    private AddressBookController addressBookController=null;
    public CoinsView(StatusController statusController, AddressBookController addressBookController) { this();
        this.statusController=statusController;
        this.addressBookController=addressBookController;
    }

    void layoutInit() {

    }

    private void dragInit() {
        addressField.setOnDragOver((ev)->{
             if (ev.getGestureSource() != addressField && ev.getDragboard().hasString())
                 ev.acceptTransferModes(TransferMode.COPY_OR_MOVE);
             ev.consume();
        });
        fromField.setOnDragOver((ev)->{
             if (ev.getGestureSource() != fromField && ev.getDragboard().hasString())
                 ev.acceptTransferModes(TransferMode.COPY_OR_MOVE);
             ev.consume();
        });

        addressField.setOnDragDropped((ev)->{
            Dragboard db = ev.getDragboard();
            boolean success = false;
            if (db.hasString()) { addressField.setText(db.getString()); success = true; }
            ev.setDropCompleted(success);

            ev.consume();
            Platform.runLater(()->{this.toFront(); this.requestFocus();});
        });
        fromField.setOnDragDropped((ev)->{
            Dragboard db = ev.getDragboard();
            boolean success = false;
            if (db.hasString()) { fromField.setText(db.getString()); success = true; }
            ev.setDropCompleted(success);

            ev.consume();
            Platform.runLater(()->{this.toFront(); this.requestFocus();});
        });

        addressField.setOnDragEntered((ev)->{
            addressField.requestFocus();
            ev.consume();
        });
        fromField.setOnDragEntered((ev)->{
            fromField.requestFocus();
            ev.consume();
        });


    }

    private volatile int layoutInit=-1;
    @Override protected void layoutChildren() { super.layoutChildren();
        if(layoutInit<0) {layoutInit++;
            layoutInit();

            setControllers(this.statusController,this.addressBookController);
        }
        else if(layoutInit<1) {layoutInit++;
        }
        else if(layoutInit<2) {layoutInit++;
        }
    }

    public void setControllers(StatusController statusController, AddressBookController addressBookController) {
        this.statusController=statusController;
        this.addressBookController=addressBookController;

        addressBook.setController(addressBookController);

        if(statusController!=null && layoutInit>=0) {

            statusController.addListener((obj)-> { if(obj instanceof Listener.Error) {

                final Listener.Error err=(Listener.Error)obj;
                Platform.runLater(()->{
                    jobInfo.alert(err.message);
                });
            }});

            statusController.addListener((obj)-> { if(obj instanceof Listener.FullStatus) {

                final Listener.FullStatus status=(Listener.FullStatus)obj;

                Platform.runLater(()->{
                    feeText.setText( NumberFormat.getInstance().format(status.paytxfee)+" LONG/kB" );
                    balanceText.setText( NumberFormat.getInstance().format(status.balance)+" LONG" );
                });
            }});


            this.statusController.speedUp();
        }

    }

}

