package gui;

import static application.Main.*;

import coredrv.RPCCommanderCached;
import coredrv.RPCCommander.MapResponse;
import coredrv.RPCCommander.MapsResponse;
import coredrv.RPCCommander.StringResponse;
import util.LOGGER;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;


public class AddressBookController extends Thread {
	// Изменения в адресной книге могут производится из вне чрез консоль (поэтому опрашиваем ее постоянно)
	// Желательная реализация, чтобы контроллер ничего не знал о специфике view и отдавал данные через интерфейс или слушателей
	
	private volatile int POLLING_INTERVAL=0; // Когда 0 то вечно ждет notify() 
	static final int SPEEDUP_INTERVAL=6;	 // если принудительный форсированный опрос
	
	private final RPCCommanderCached rpc;
	
	public AddressBookController() { super(STANDART_THREADS,"AddressBookController"); 
		super.setDaemon(true); 
		// FIXME нужно определится true/false (особенно в случае крахов приложухи)
		// Лучший вариант - проверять в самом потоке а есть ли главный поток приложения...
		
		if (rpcCommander==null) throw new NullPointerException("rpcCommander is null"); // Без источника данных работать не может
		this.rpc=rpcCommander; // XXX глобальный rpcCommander создается не статически и в статическом контексте не определен 
		
		super.start();
	}
	
	/* 
	 		Метод wait() вынуждает вызывающий поток исполнения уступить монитор и перейти в состояние ожидания до тех пор, пока какой-нибудь другой поток
		исполнения не войдет в тот же монитор и не вызовет метод notify() (объекта).
			Метод notify() возобновляет исполнение потока, из которого был вызван wait() для того же самого объекта (монитора).
	    	Метод notifyAll() возобновляет исполнение всех потоков, из которых был вызван метод wait() для того же самого объекта. Одному из
		этих по­токов предоставляется доступ.
	*/	
	
	private int speedUpCnt=1; // Всегда внутри синхронизированного на одном объекте контекста (volatile не нужен)
		synchronized public void speedUp() { speedUpCnt++; this.notify(); }

	@Override public void run() { try { // Завершение опроса RPC только по interrupt()
		while(!isInterrupted()) 	    // this.interrupted() - сбрасывает флаг прерывания после true, this.isInterrupted() - не трогает его после проверки
		{	
			synchronized(this) {  			
				if(speedUpCnt<=0) this.wait(POLLING_INTERVAL); 		// Вечно ждет форсинга. в wait освобождается монитор.
				else this.wait(SPEEDUP_INTERVAL);	    			// Не спать если форсят опрос
				
				if(speedUpCnt>0) speedUpCnt--;
				
				if(speedUpCnt>0) speedUpCnt=0; // Чтобы не накапливался опрос при частом форсировании
			}			
			
			polling(); // Здесь можно жевать сколько влезет (при редкой ложной Oraclo-вской активации из wait() - polling повторится)
			
		}
		} catch (InterruptedException ignore) {} // выход или по флагу или по исключению (прерывание внутри sleep дает исключение а флаг isInterrupted остается сброшенным)
		LOGGER.info("AddressBookController Thread completed"); LOGGER.console("AddressBookController Thread completed"); // TODO debug
	}
	
	//////////////////////////////////////////////////////////// Интерфейс ///////////////////////////////////////////////////////////////////
	public interface Listener { // Интерфейсы неявно статические
		  static class Error{public int code=0; public String message=null;};
		  static class AddressBook extends ArrayList< AddressBookModel > { private static final long serialVersionUID = 1L;
		  }; 
		  
		  void update(Object obj); // (obj)-> { if(obj instanceof Listener.Error) { ... } }
	}
	final List<Listener> listeners=new ArrayList<>(); // Зарегистрированные в listeners методы выполняются в этом потоке (должны юзать Platform.runLater для обновления UI в своем потоке)
	public void addListener(Listener listener) {
		synchronized(listeners) { // Стремимся детализировать монитор синхронизации, чтобы не лочить весь класс без необходимости
			listeners.add(listener);
		}
	}
	void updateListeners(Object obj) {	  // Взаимно-синхронизировано только с addListener
		synchronized(listeners) {
			for(Listener listener: listeners) listener.update(obj);
			//for(int i=0; i<listeners.size(); i++) listeners.get(i).update(obj);
		}
	}
	
	
	public String setAccount(String addressID, String account) {
		StringResponse res=rpc.setAccount(addressID, account);
		if(!res.isOk()) {
			final Listener.Error err=new Listener.Error(); err.code=res.code; err.message=res.message;
			updateListeners(err);	// Обработка ошибок где-то во вне
			
			return null;
		}
		
		return res.data; // Строка адреса
	}
	
	public String importPrivKey(String privkey, String account) {
		StringResponse res=rpc.importPrivKey(privkey, account);
		if(!res.isOk()) {
			final Listener.Error err=new Listener.Error(); err.code=res.code; err.message=res.message;
			updateListeners(err);	// Обработка ошибок где-то во вне
			
			return null;
		}
		
		return res.data;
	}
	public String importPubKey(String pubkey, String account) {
		StringResponse res=rpc.importPubKey(pubkey, account);
		if(!res.isOk()) {
			final Listener.Error err=new Listener.Error(); err.code=res.code; err.message=res.message;
			updateListeners(err);	// Обработка ошибок где-то во вне
			
			return null;
		}
		
		return res.data;
	}	
	public String importAddress(String address, String account) {
		StringResponse res=rpc.importAddress(address, account);
		if(!res.isOk()) {
			final Listener.Error err=new Listener.Error(); err.code=res.code; err.message=res.message;
			updateListeners(err);	// Обработка ошибок где-то во вне
			
			return null;
		}
		
		return res.data;
	}
	
	public String getNewAddress(String account) {
		StringResponse res=rpc.getNewAddress(account);
		if(!res.isOk()) {
			final Listener.Error err=new Listener.Error(); err.code=res.code; err.message=res.message;
			updateListeners(err);	// Обработка ошибок где-то во вне
			
			return null;
		}
		
		return res.data;
	}
	
	public String addMultisigAddress(String key, String account) {
		StringResponse res=rpc.addMultisigAddress(key,account);
		if(!res.isOk()) {
			final Listener.Error err=new Listener.Error(); err.code=res.code; err.message=res.message;
			updateListeners(err);	// Обработка ошибок где-то во вне
			
			return null;
		}
		
		return res.data;
	}
	
	public String getAddressFromPubKey(String pubkey) {
		MapResponse res=rpc.validateAddress(pubkey); // Для адреса вернет адрес
		if(!res.isOk()) {
			final Listener.Error err=new Listener.Error(); err.code=res.code; err.message=res.message;
			updateListeners(err);	// Обработка ошибок где-то во вне
			
			return null;
		}
		if(!res.data.getOrDefault("isvalid","false").equals("true")) return null;
		
		return res.data.get("address");
	}
	
	///////////////////////////////////////////////// Воркер /////////////////////////////////////////////////////////////////////////////
		
	void polling() { 
		// При старте демона какое-то время Server returned HTTP response code: 500
		// Затем: -28 ( Loading block index... )
		// Также для некоторых команд может быть: -13 (Please enter the wallet passphrase with walletpassphrase first.) 
		
		
		// keypoololdest в getwalletinfo - быстрый индикатор обновления своих и только адресов
		// (адекватно ждать действий пользователя для пробуждения потока)
		
		updateListeners(Boolean.valueOf(true)); // Начало загрузки
		
		MapsResponse ab=rpc.getAddressBook();	// FIXME Фактически это Set с уникальными не пустыми адресами в поле "address"
		if (!ab.isOk()) { // Нет подключения к RPC - ловить нечего	
			
			final Listener.Error err=new Listener.Error(); err.code=ab.code; err.message=ab.message;
			updateListeners(err);
			
			if(POLLING_INTERVAL==0) {
				POLLING_INTERVAL=5000; this.speedUp(); // FIXME Чтобы небыло вечного сна контроллера когда всеже подключение восстановится (Это нужно из-за this.wait(0);)
			}
			
			return;
		}
		POLLING_INTERVAL=0; // Потом уснет до speedUp
		
		Listener.AddressBook addressBook = new Listener.AddressBook();
	
		for(Map<String,String> rec: ab.data) addressBook.add( new AddressBookModel(rec));	
		
		updateListeners(addressBook);
		
	}

}




















