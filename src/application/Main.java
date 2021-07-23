package application;
	
import coredrv.*;
import util.Css;
import util.L10n;
import util.LOGGER;
import util.Threads;
import gui.TransactionsView;
import gui.StatusController.Listener;
import gui.AddressBookController;
import gui.AddressBookView;
import gui.ChangePasswordWizard;
import gui.CoinsView;
import gui.ConnectChannelWizard;
import gui.ConnectChatWizard;
import gui.CreateChannelWizard;
import gui.EncryptWalletWizard;
import gui.CreateChatWizard;
import gui.PasswordForm;
import gui.PreventionWalletWizard;
import gui.QuickReferenceWizard;
import gui.RPCConsoleForm;
import gui.TransactionsController;
import gui.StatusView;
import gui.StatusController;
import gui.DonationWizard;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.application.Preloader.StateChangeNotification;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.scene.Scene;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;



public class Main extends Application {
	////////////////////////////////////////// Global Application Constants ////////////////////////////////////////////////
	
	public static final String VERSION="LONG Network v1.0b";
	
	public static final double SCREEN_HEIGHT=Screen.getPrimary().getBounds().getHeight();
	public static final double SCREEN_WIDTH=Screen.getPrimary().getBounds().getWidth();
	
	static final double AGE21_HEIGHT = Css.getTextBounds("┃⟺",12).getHeight();
	
	// Коррекция размеров дочерних окон на уровне вложения 1, 2, 3
	public static final double H1,H2,H3,W1,W2,W3;	
	
	static {
		
		double h1=(1.0-28d/600) + (SCREEN_HEIGHT-600)*( 0.875 - (1.0-28d/800) )/(1080-600); if(h1>1.0) h1=1.0; else if(h1<0.375) h1=0.375;
		H1=h1;
		
		double h2=(1.0-28d/600) + (SCREEN_HEIGHT-600)*( 0.75 - (1.0-28d/800) )/(1080-600); if(h2>1.0) h2=1.0; else if(h2<0.375) h2=0.375;
		H2=h2;
		
		double h3=(1.0-28d/600) + (SCREEN_HEIGHT-600)*( 0.5 - (1.0-28d/800) )/(1080-600); if(h3>1.0) h3=1.0; else if(h3<0.375) h3=0.375;
		H3=h3;
		
		double w1=(1.0-28d/800) + (SCREEN_WIDTH-800)*( 0.75 - (1.0-28d/800) )/(1920-800); if(w1>1.0) w1=1.0; else if(w1<0.375) w1=0.375;
		W1=w1;
		
		double w2=(1.0-28d/800) + (SCREEN_WIDTH-800)*( 0.75 - (1.0-28d/800) )/(1920-800); if(w2>1.0) w2=1.0; else if(w2<0.375) w2=0.375;
		W2=w2;
		
		double w3=(1.0-28d/800) + (SCREEN_WIDTH-800)*( 0.5 - (1.0-28d/800) )/(1920-800); if(w3>1.0) w3=1.0; else if(w3<0.375) w3=0.375;
		W3=w3;
	}
	

	public static final double SCENE_START_HEIGTH = SCREEN_HEIGHT*H1;
	public static final double SCENE_START_WIDTH = SCREEN_WIDTH*W1;
	
	public static DaemonSupervisor longcoinDaemon; 	// Для глобального доступа
	public static RPCCommanderCached rpcCommander;	// Глобальный RPCCommander
	public static ThreadGroup STANDART_THREADS;     // Чтобы завершать все стандартные потоки одной командой interrupt()
	
	
	public static String PUBLIC_ADDRESS="1GztQxGTKdEFhctBhR38wR8skjqkd4Cqt8";
	
	public static String DONAT_LONG="1jAiYKH7yv7TWdumPNdgh6cZhuxbtGh43";
	public static String DONAT_ETH="0x6e04282bb56Dd116d40785ebc3f336b4649A5bCb";
	public static String DONAT_DOGE="DEBQKxDukNTToE3YvbVMFRkHBxTnUrUrTP";
	public static String DONAT_LTC="LafMXhkxUp3GG1TM47GFRkmHmSGLDeCvzg";
	public static String DONAT_BTC="19tAZLiVBPNVaGoxq8BTrwDqp3a41zG65b";
	
	public static String SITE="https://longnetwork.github.io/downloads.html";
	
	
	
	public static String PIN_ADDRESS="1EwDScmGTT4vup5owJJrxpEnEGpj2wqXUF";
	public static double PIN_COST=10000.0;   // Цена рекламы
	public static int PIN_DEPTH=720/4;       // Глубина просмотра транз для обнаружения закрепленного контента (сутки/4 если 1 транза в 2 минуты)
	
	// Глобальные контроллеры, доступные для шаринга (действуют в одну сторону - только поставляют информацию)
	public static AddressBookController addressBookController;
	public static StatusController statusController;
	
	
	public static Main application;
	
	public static void daemonShutDown() throws Exception {
		if(longcoinDaemon!=null && longcoinDaemon.isAlive()) {
			
			if(longcoinDaemon.getOsType()==DaemonSupervisor.OSType.WINDOWS) { // в Windows нет SIGTERM - поэтому через жопу
				
				if(rpcCommander!=null) {
					LOGGER.info(longcoinDaemon.getDaemonName()+" shutdown ..."); 
					LOGGER.console(longcoinDaemon.getDaemonName()+" shutdown ..."); // TODO debug
					
					long waitForTime= System.currentTimeMillis()+120*1000;
					do {
						rpcCommander.stop(); Thread.sleep(5000);	// FIXME При абъюзе RPC будет фактически долго ждать перед завершением
					}
					while(longcoinDaemon.isAlive() && System.currentTimeMillis()-waitForTime<0);
					
					if(longcoinDaemon.isAlive()) { // Жоска дропаем
						LOGGER.warning(longcoinDaemon.getDaemonName()+" can't soft stop - forced termination ...");
						LOGGER.console(longcoinDaemon.getDaemonName()+" can't soft stop - forced termination ..."); // TODO debug
						longcoinDaemon.destroyForcibly();
						if(!longcoinDaemon.waitFor(120*1000)) {
							LOGGER.error(longcoinDaemon.getDaemonName()+" did not termination ..."); 
							throw new RuntimeException(longcoinDaemon.getDaemonName()+" did not termination ...");
						}
					}
					
				}
				else longcoinDaemon.shutdown(); // ну тогда остается только жостко дропнуть (не должно быть)
			}
			else longcoinDaemon.shutdown();
		}
	}
	public static void daemonStart(String ...additionOptions) throws Exception {
		// FIXME - инвалидация (очистка) всех кешей
		
		if(longcoinDaemon!=null) longcoinDaemon.start(additionOptions);
	}
	
	////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	
	static final int MAX_TABS=8;
	
	public static void main(String[] args) {
		System.setProperty("javafx.preloader", Splash.class.getCanonicalName());  Application.launch(Main.class,args);
	}
	
	@Override public void init() throws Exception { LOGGER.console("𝕝𝕠𝕟𝕘𝕔𝕠𝕚𝕟-𝕛𝕒𝕧𝕒");
	
		STANDART_THREADS = new ThreadGroup("Standart Threads");
		
		(longcoinDaemon = new DaemonSupervisor()).start(); // Помнить, что демон стартует какое-то время и запросы по RPC начинает принимать не сразу
		rpcCommander = new RPCCommanderCached( longcoinDaemon.getPort(),longcoinDaemon.getUser(),longcoinDaemon.getPassword() );
		
		addressBookController=new AddressBookController();
		statusController= new StatusController();
		
		application=this;
	}	
	
		
	BorderPane root;
	
	private volatile boolean unlockProgress=false;
	
	Fading fadingScreen;
	
	@Override public void start(Stage primaryStage) {
		try {
			
			primaryStage.setTitle(VERSION);
			primaryStage.getIcons().add(new Image("res/longcoin_128x128.png"));
			
			layoutInit(); menuInit();
			
			
			statusController.addListener((obj)-> { if(obj instanceof Listener.FullStatus) { // Запрос пароля зашифрованного кошелька
				
				final Listener.FullStatus status=(Listener.FullStatus)obj;
				
				
				if(status.unlocked_until==0 && !unlockProgress) { // XXX после ввода правильного пароля unlocked_until>0 и сюда больше не должно заходить
					unlockProgress=true; // Для предотвращения повторных вызовов формы пароля
					
					Platform.runLater(()->{
							LOGGER.info("Passphrase request"); LOGGER.console("Passphrase request"); // TODO debug

							PasswordForm password=new PasswordForm(root); // Установка родительского окна нужна для наследования CSS
							password.showAndWait();
							
							if(password.getResult()!=null) { 
								if(rpcCommander.walletPassphrase(password.getResult()).isOk()) {
									addressBookController.speedUp(); statusController.speedUp();
								}
								else unlockProgress=false; // Повторить форму
							}
							// Выход через закрытие а не через OK больше не будет показывать форму ввода пароля (до перезагрузки приложения)
							// Но ее можно вызвать, через меню
					});
					
				}
			}});
			
			
			Scene scene = new Scene(root,SCENE_START_WIDTH,SCENE_START_HEIGTH);
			
			scene.getStylesheets().add(ClassLoader.getSystemResource("res/theme.css").toExternalForm());
			scene.getStylesheets().add(ClassLoader.getSystemResource("res/application.css").toExternalForm());
			
			primaryStage.setScene(scene);
			
			primaryStage.setOnShown((ev)->{
				notifyPreloader(new StateChangeNotification(StateChangeNotification.Type.BEFORE_START));
			});
			
			primaryStage.show();
			
			
			primaryStage.setOnCloseRequest((ev)->{ ev.consume(); // Для контролируемого выхода после завершения демона
				
				fadingScreen=new Fading("Waiting for Core shutdown..."); // Нужно сохранить ссылку чтобы gc не подчистил
				
				primaryStage.close(); // Скрывает и дочерние через initOwner() формы
				
				Threads.runNow(()->{
					
					try { 
						Main.this.stop(); 
					} catch (Exception e) {
						LOGGER.error(e.toString());
					}
					
					
					Platform.runLater(()->fadingScreen.close());
					// This method may be called from any thread.
					Platform.exit(); // XXX Повторит вызов Main.this.stop() 
				});
		        
			});
			
			
			
			
			primaryStage.addEventHandler(KeyEvent.KEY_PRESSED, (ev)-> { // TODO debug ctrl+D
				if(ev.isControlDown() && ev.getCode()==KeyCode.D) {

				}
			});			

			
		} catch(Exception e) {
			e.printStackTrace(); // Не должно быть никогда в рабочей программе
		}
	}
	
	@Override public void stop() throws Exception {
		//Thread.getAllStackTraces().keySet().forEach((t)->LOGGER.console(t.toString()+" "+t.getState().toString())); // TODO debug
		STANDART_THREADS.interrupt();
		
		daemonShutDown();
		
		LOGGER.info("longcoin-java Application exit");
		LOGGER.console("longcoin-java Application exit");		
	}
	
	void layoutInit() {
		BorderPane root = new BorderPane();
			TabPane tabPane = new TabPane(); tabPane.setFocusTraversable(false);
				Tab tabStatus = new Tab(L10n.t("Status"), new StatusView(statusController)); 
					tabStatus.setClosable(false);
				Tab tabAddressBook = new Tab(L10n.t("AddressBook"), new AddressBookView(addressBookController)); 
					tabAddressBook.setClosable(false);
				Tab tabSendCoins = new Tab(L10n.t("Coins"), new CoinsView(statusController,addressBookController)); 
					tabSendCoins.setClosable(false);
					
				// FIXME Помнить что TransactionsController НЕ разделяемый из-за обратной связи (внешней подстройки через view)
				Tab tabTransactions = new Tab(L10n.t("Transactions"), new TransactionsView(new TransactionsController()));
					tabTransactions.setClosable(false);
				Tab tabPlus = new Tab("+"); tabPlus.getStyleClass().add("tab-plus"); // .tab-pane .tab-plus .tab-labe {}
					tabPlus.setClosable(false);
				
					
			tabPane.getTabs().addAll(tabStatus,tabAddressBook,tabSendCoins,tabTransactions,tabPlus);
		root.setCenter(tabPane);		


		tabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab)->{
			if(newTab!=null && newTab.getContent()!=null) {
				Platform.runLater(()->newTab.getContent().requestFocus());
			}
			
			if(newTab==tabPlus) {
				int idx=tabPane.getTabs().indexOf(tabPlus);
				
				final Tab tabTx=new Tab(L10n.t("Transactions"), new TransactionsView(new TransactionsController()));
					tabTx.setClosable(true);
					tabTx.setOnCloseRequest((ev)->{
						TransactionsView txview=(TransactionsView)tabTx.getContent();
						
						if(txview!=null && txview.getController()!=null) txview.getController().interrupt(); 
						
						tabTx.setContent(null);
					});
				tabPane.getTabs().add(idx, tabTx);
				
				tabPane.getSelectionModel().select(tabTx);
	
				//Platform.runLater(()->tabTx.getContent().requestFocus());
			}
			
			if(tabPane.getTabs().size()>=MAX_TABS) tabPlus.setDisable(true); else tabPlus.setDisable(false);
			
		});
		
		this.root=root;
	}
	
	void menuInit() {
		MenuBar menu = new MenuBar();
	        Menu connectionMenu = new Menu(L10n.t("Connection Wizards"));
	        
	        	// .menu-bar #idName > .label {}

			    MenuItem menuItemCreateChat = new MenuItem(L10n.t("СreatePrivateGroup")); menuItemCreateChat.setId("create-chat");
		    		menuItemCreateChat.setOnAction((ev)->{ CreateChatWizard wizard=new CreateChatWizard(root); wizard.showAndWait(); });
			    MenuItem menuItemConnectChat = new MenuItem(L10n.t("СonnectToPrivateGroup")); menuItemConnectChat.setId("connect-chat");
			    	menuItemConnectChat.setOnAction((ev)->{ ConnectChatWizard wizard=new ConnectChatWizard(root); wizard.showAndWait(); });
		    	
			    SeparatorMenuItem separator1 = new SeparatorMenuItem();
			    
				MenuItem menuItemCreateChannel = new MenuItem(L10n.t("СreateChannel")); menuItemCreateChannel.setId("create-channel");
			    	menuItemCreateChannel.setOnAction((ev)->{ CreateChannelWizard wizard=new CreateChannelWizard(root); wizard.showAndWait(); });
		    	MenuItem menuItemConnectChannel = new MenuItem(L10n.t("ConnectToChannel")); menuItemConnectChannel.setId("connect-channel");
		    		menuItemConnectChannel.setOnAction((ev)->{ ConnectChannelWizard wizard=new ConnectChannelWizard(root); wizard.showAndWait(); });
	
			    connectionMenu.getItems().addAll(	menuItemCreateChat,menuItemConnectChat,
			    									separator1,
			    									menuItemCreateChannel,menuItemConnectChannel    );
	        
	        Menu toolsMenu = new Menu(L10n.t("Tools"));
	        	MenuItem menuItemUnlockWallet = new MenuItem(L10n.t("Unlock Wallet")); menuItemUnlockWallet.setId("unlock-wallet");
	        		menuItemUnlockWallet.setOnAction((ev)->{ 
	        			unlockProgress=false; // Разрешение всплытия формы пароля 
	        			statusController.speedUp();
	        		}); 
	        		menuItemUnlockWallet.setDisable(true);
	        	MenuItem menuItemEncryptWallet = new MenuItem(L10n.t("Encrypt Wallet")); menuItemEncryptWallet.setId("encrypt-wallet");
		        	menuItemEncryptWallet.setOnAction((ev)->{ 
		        		unlockProgress=true;
		        		
		        		menuItemEncryptWallet.setDisable(true); // После успешной шифрации это меню вырубится навсегда (unlocked_until>=0)
		        		
		        		EncryptWalletWizard wizard=new EncryptWalletWizard(root); 
		        		wizard.showAndWait();
		        		
		        		unlockProgress=false; // Был рестарт ноды и должна всплыть форма запроса пароля (после того как она начнет отвечать)
		        		statusController.speedUp();
		        	});
	        		menuItemEncryptWallet.setDisable(true);
	        	MenuItem menuItemChangePassword = new MenuItem(L10n.t("Change Password")); menuItemChangePassword.setId("change-password");
	        		menuItemChangePassword.setOnAction((ev)->{ 
	        			unlockProgress=true;
	        			
	        			ChangePasswordWizard wizard=new ChangePasswordWizard(root); 
	        			wizard.showAndWait(); 
	        			
	        			// После успешной ChangePasswordWizard кошелек уже расшифрован
	        			statusController.speedUp();
	        		});
	        		menuItemChangePassword.setDisable(true);
	        	
	        	SeparatorMenuItem separator2 = new SeparatorMenuItem();
	        	
	        	MenuItem menuItemWalletPrevention = new MenuItem(L10n.t("Wallet Prevention")); menuItemWalletPrevention.setId("wallet-prevention"); 
	        		menuItemWalletPrevention.setOnAction((ev)->{ 
	        			unlockProgress=true;
	        			
		        		PreventionWalletWizard wizard=new PreventionWalletWizard(root); 
		        		wizard.showAndWait();
		        		
		        		unlockProgress=false; // Был рестарт ноды и должна всплыть форма запроса пароля (после того как она начнет отвечать)
		        		statusController.speedUp();
	        		});
	        		menuItemWalletPrevention.setDisable(true);
	        		
	        	MenuItem menuItemRPCConsole = new MenuItem(L10n.t("RPC-Console")); menuItemRPCConsole.setId("rpc-console");
	        		menuItemRPCConsole.setOnAction((ev)->{ RPCConsoleForm console=new RPCConsoleForm(root); console.showAndWait(); });	
	        
	        	toolsMenu.getItems().addAll(	menuItemUnlockWallet,menuItemEncryptWallet,menuItemChangePassword,
													separator2,
												menuItemWalletPrevention,menuItemRPCConsole
													    );
	        	
	        
	        Menu helpMenu = new Menu(L10n.t("Help")); 
	        	MenuItem menuItemReference = new MenuItem(L10n.t("Quick Reference")); menuItemReference.setId("quick-reference");
	        	menuItemReference.setOnAction((ev)->{ QuickReferenceWizard wizard=new QuickReferenceWizard(root); wizard.showAndWait(); });
	        
	        	MenuItem menuItemInfo = new MenuItem(L10n.t("Mobile Version")); menuItemInfo.setId("mobile-version");
	        	menuItemInfo.setOnAction((ev)->{ DonationWizard wizard=new DonationWizard(root); wizard.showAndWait(); });
	        	
	        	helpMenu.getItems().addAll(menuItemReference,menuItemInfo);
	        	
	        
	        menu.getMenus().addAll(connectionMenu, toolsMenu, helpMenu);
		   
	        
	    toolsMenu.setOnShowing((ev)->{ // Профилактика кошелька возможна только если мы управляем демоном
	    	menuItemWalletPrevention.setDisable(!longcoinDaemon.isAlive());
	    });   
	     
		statusController.addListener((obj)-> { if(obj instanceof Listener.FullStatus) { // состояние меню шифровки кошелька зависит от состояния кошелька
			
			final Listener.FullStatus status=(Listener.FullStatus)obj;
			
			Platform.runLater(()->{
				if(status.unlocked_until>=0) { // Кошелек уже зашифрован - можно только менять пароль
					menuItemEncryptWallet.setDisable(true);
					menuItemUnlockWallet.setDisable(status.unlocked_until>0);
					menuItemChangePassword.setDisable(false);
				}
				else { // Кошелек не зашифрован - можно только зашифровать, при условии что мы управляем демоном (он рестартится)
					
					menuItemEncryptWallet.setDisable(!longcoinDaemon.isAlive());
					menuItemUnlockWallet.setDisable(true);
					menuItemChangePassword.setDisable(true);					
				}
			});
					
		}});
		
		// FIXME через жопу впихнуть картинку в зону MenuBar (чтобы базовые стили были одинаковые по всей длине в topBox и картинка хорошо вписалась)
		
		ImageView age21img=new ImageView("res/age21.png");
		age21img.setFitWidth(AGE21_HEIGHT*1.777777777777d); age21img.setFitHeight(AGE21_HEIGHT);
		age21img.setSmooth(true);
		
		MenuBar age21bar=new MenuBar(new Menu(null,age21img)); 
		
		HBox topBox=new HBox(menu,age21bar); HBox.setHgrow(menu, Priority.ALWAYS); 
		
	    
		root.setTop(topBox);
	}
}
