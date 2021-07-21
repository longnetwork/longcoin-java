package gui;

import static application.Main.*;

import coredrv.RPCCommanderCached;
import coredrv.RPCCommander.BoolResponse;
import coredrv.RPCCommander.MapResponse;
import coredrv.RPCCommander.MapsResponse;
import util.LOGGER;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class StatusController extends Thread {
	
	static final int POLLING_INTERVAL=2663; 	// Интервал опроса демона rpc в штаном режиме ms
	static final int SPEEDUP_INTERVAL=6;		// Форсировано
	
	private final RPCCommanderCached rpc;
	
	public StatusController() { super(STANDART_THREADS,"StatusController"); 
		super.setDaemon(true); 
		// FIXME нужно определится true/false (особенно в случае крахов приложухи)
		// Лучший вариант - проверять в самом потоке а есть ли главный поток приложения...
		
		if (rpcCommander==null) throw new NullPointerException("rpcCommander is null"); // Без источника данных работать не может
		this.rpc=rpcCommander;
		
		super.start();
	}
	
	/* 
	 		Метод wait() вынуждает вызывающий поток исполнения уступить монитор и перейти в состояние ожидания до тех пор, пока какой-нибудь другой поток
		исполнения не войдет в тот же монитор и не вызовет метод notify() (объекта).
			Метод notify() возобновляет исполнение потока, из которого был вызван wait() для того же самого объекта (монитора).
	    	Метод notifyAll() возобновляет исполнение всех потоков, из которых был вызван метод wait() для того же самого объекта. Одному из
		этих по­токов предоставляется доступ.
	*/	
	
	private volatile int speedUpCnt=1;
		synchronized public void speedUp() { speedUpCnt++; this.notify(); }

	@Override public void run() { try { // Завершение опроса RPC только по interrupt()
		while(!isInterrupted()) 	    // this.interrupted() - сбрасывает флаг прерывания после true, this.isInterrupted() - не трогает его после проверки
		{	
			synchronized(this) {  			
				if(speedUpCnt<=0) this.wait(POLLING_INTERVAL); 	// Интервал опроса демона. в wait освобождается монитор.
				else this.wait(SPEEDUP_INTERVAL);	    		// Не спать если форсят опрос
				
				if(speedUpCnt>0) speedUpCnt--;
				
				if(speedUpCnt>1) speedUpCnt=1; // Чтобы не накапливался опрос при частом форсировании
			}			
			
			polling(); // Здесь можно жевать сколько влезет (при редкой ложной Oraclo-вской активации из wait() - polling повторится)
			
		}
		} catch (InterruptedException ignore) {} // выход или по флагу или по исключению (прерывание внутри sleep дает исключение а флаг isInterrupted остается сброшенным)
		LOGGER.info("StatusController Thread completed"); LOGGER.console("StatusController Thread completed"); // TODO debug
	}
	
	//////////////////////////////////////////////////////////// Интерфейс ///////////////////////////////////////////////////////////////////
	public interface Listener { // Интерфейсы неявно статические
		  static class Error{public int code=0; public String message=null;};
		  static class Status{
			  public int connections; // Если connections==0 то blocks не актуально
			  public int blocks;
			  public double synchronization;	  
		  };
		  static class FullStatus extends Status {
			  public long balance;
			  public long unconfirmed_balance;
			  public long immature_balance;
			  
			  
			  public double networkhashps;
			  public double difficulty;
			  
			  
			  public String warnings;
			  public String errors; // Это же и для alert сообщений
			  
			  
			  public long txcount;
			  
			  public long paytxfee;
			  
			  public long unlocked_until; // 0 для зашифрованного состояния кошелька который не расшифрован (если кошелек не шифровался то этого поля нет вообще)
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
		}
	}
	
	
	///////////////////////////////////////////////// Воркер /////////////////////////////////////////////////////////////////////////////
		
	private long lastTransactionHash=-1; private int listenersCnt=-1;
	private boolean transactionsUpdated() {
		long hash=rpc.getTransactionsUpdate(); 
		synchronized(listeners) {
			if( hash!=lastTransactionHash || listeners.size()!=listenersCnt) { // Добавлены новые слушатели - нужно все  "прокрутить" для них
				
					lastTransactionHash=hash;
					listenersCnt=listeners.size();
					
					// Может быть ситуация когда контроллер "крутанулся" еще до установки обработчиков updateListeners
					// и при следующем заходе transactionsUpdated() должен дать true, чтобы не оставить транзы не считанными
					// (listeners.size()==listenersCnt означает что инициализации addListener "устаканились")
				
				return true;
			} 
			
			return false;
		}
	}
	
	void polling() { 
		// При старте демона какое-то время Server returned HTTP response code: 500
		// Затем: -28 ( Loading block index... )
		// Также для некоторых команд может быть: -13 (Please enter the wallet passphrase with walletpassphrase first.) 
		
		
		updateListeners(Boolean.valueOf(true)); // Начало загрузки
			
		MapResponse status=rpc.getStatusInfo();
		if (!status.isOk()) { // Нет подключения к RPC - ловить нечего	
			
			final Listener.Error err=new Listener.Error(); err.code=status.code; err.message=status.message;
			updateListeners(err);
			
			rpc.resetStatusCache();
			
			return;
		}
		
		Listener.FullStatus fullStatus = new Listener.FullStatus();
	
		fullStatus.connections=Integer.parseInt(status.data.getOrDefault("connections", "0"));
		fullStatus.blocks=Integer.parseInt(status.data.getOrDefault("blocks", "0"));
		fullStatus.synchronization=Double.parseDouble(status.data.getOrDefault("synchronization", "0.0"));
			
		fullStatus.balance=(long)Double.parseDouble(status.data.getOrDefault("balance", "0.0"));
		fullStatus.unconfirmed_balance=(long)Double.parseDouble(status.data.getOrDefault("unconfirmed_balance", "0.0"));
		fullStatus.immature_balance=(long)Double.parseDouble(status.data.getOrDefault("immature_balance", "0.0"));

		
		fullStatus.networkhashps=Double.parseDouble(status.data.getOrDefault("networkhashps", "0.0"));
		fullStatus.difficulty=Double.parseDouble(status.data.getOrDefault("difficulty", "0.0"));
		
		
		fullStatus.warnings=status.data.getOrDefault("warnings", "");
		fullStatus.errors=status.data.getOrDefault("errors", "");
		if(fullStatus.errors.equals(fullStatus.warnings)) fullStatus.errors=""; // Это не ошибка а предупреждение
		
		fullStatus.txcount=Long.parseLong(status.data.getOrDefault("txcount", "0"));
		
		fullStatus.paytxfee=(long)Double.parseDouble(status.data.getOrDefault("paytxfee", "1.0"));
		
		fullStatus.unlocked_until=Long.parseLong(status.data.getOrDefault("unlocked_until", "-1")); // -1 - не шифрован; 0 - не расшифрован;
		
		updateListeners(fullStatus);
		
		// Ловим pinned content
		
		if(!transactionsUpdated()) return; // Нет смысла заново перечитывать
		
		MapsResponse transactions=rpc.listTransactions("*", PIN_DEPTH, 0, true); // В порядке от старых к новым
		if (!transactions.isOk()) {
			final Listener.Error err=new Listener.Error(); err.code=transactions.code; err.message=transactions.message;
			updateListeners(err);	
			return;	// XXX если прошла ошибка то кеш сделется не валидным и следующая transactionsUpdated даст true
		}
		
		for(int i=transactions.data.size()-1; i>=0; i--) { // Первая найденная pinned транзакция уходит в pinned-окно
			final Map<String,String> tx=transactions.data.get(i);
			
			if(!tx.containsKey("decryption")) continue; 					 // Финансовая
			if(!tx.get("decryption").equals("yes")) continue;  				 // Крякозябры
			if(!tx.getOrDefault("trusted","false").equals("true")) continue; // Херь
			if(!tx.getOrDefault("address", "").startsWith("1")) continue;    // Закреп на маркетах не предусмотрен (и не имеет смысла)			
			
			String txid=tx.getOrDefault("txid", "");
			
			BoolResponse verify=rpc.verifyPinned(txid); // транзы без txid (категории move) сюда не дойдут (не имеют "decryption")
			/*if(!verify.isOk()) {
				final Listener.Error err=new Listener.Error(); err.code=verify.code; err.message=verify.message;
				updateListeners(err);	
				return;
			}*/
			
			if(verify.data) { // XXX Если несколько hexdata то закрепляем самый большой
				
				MapsResponse content=rpc.getHexData(txid,true);
				if (!content.isOk()) { // Может быть code: -13 (Error: Please enter the wallet passphrase with walletpassphrase first.)
					
					final Listener.Error err=new Listener.Error(); err.code=content.code; err.message=content.message;
					updateListeners(err);
					return;
				}		
				
				int max=Integer.MIN_VALUE;
				Map<String,String> content_tx=null;
				
				for(Map<String,String> data_tx: content.data) {
					if(data_tx.containsKey("hexdata")) {
						int size=data_tx.get("hexdata").length();
						if(size>max) { max=size; content_tx=data_tx; }
					}
				}
				
				if(content_tx!=null) { // Ура! Бабосы текут рекой!	
					updateListeners(new TransactionModel(content_tx).getContent()); // FIXME TransactionModel Только для предобработки через setContent
				}
				
				return;
			}
			
		}

	}

}




















