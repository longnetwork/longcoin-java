package gui;

import static application.Main.*;

import coredrv.RPCCommanderCached;
import coredrv.RPCCommander.MapResponse;
import coredrv.RPCCommander.MapsResponse;
import util.LOGGER;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class TransactionsController extends Thread {
	
	// Желательная реализация, чтобы контроллер ничего не знал о специфике view и отдавал данные через интерфейс или слушателей
	
	static final int POLLING_INTERVAL=3323; 	// Интервал опроса демона rpc в штаном режиме ms
	static final int SPEEDUP_INTERVAL=6;		// если принудительный опрос (для скроллинга)
	
	
	private final RPCCommanderCached rpc;
	
	public TransactionsController() { super(STANDART_THREADS,"TransactionsController"); 
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
		synchronized public void speedUp() { speedUpCnt++; this.notify(); } // Ускоритель поллинга (Бля я Гений изящных и простых решений!)

	@Override public void run() { try { // Завершение опроса RPC только по interrupt()
		while(!isInterrupted()) 	    // this.interrupted() - сбрасывает флаг прерывания после true, this.isInterrupted() - не трогает его после проверки
		{	
			int _cnt=0; // FIXME только для отладки
			
			synchronized(this) {  			
				if(speedUpCnt<=0) this.wait(POLLING_INTERVAL); // Интервал опроса демона. в wait освобождается монитор.
				else this.wait(SPEEDUP_INTERVAL);	      	   // Не спать если скроллят страницы
				
				if(speedUpCnt>0) speedUpCnt--;
				
				_cnt=speedUpCnt;
				
				if(speedUpCnt>1) speedUpCnt=1; // FIXME 2 2 // Чтобы не накапливался опрос при быстрой пагинации
			}
			
			polling(); // Здесь можно жевать сколько влезет (при редкой ложной Oraclo-вской активации из wait() - polling повторится)
			
			if(_cnt>1) {
				LOGGER.warning("TransactionsController Thread is overloaded! ("+_cnt+")");
				LOGGER.console("TransactionsController Thread is overloaded! ("+_cnt+")"); // TODO debug
			}			
		}
		} catch (InterruptedException ignore) {} // выход или по флагу или по исключению (прерывание внутри sleep дает исключение а флаг isInterrupted остается сброшенным)
		LOGGER.info("TransactionsController Thread completed"); LOGGER.console("TransactionsController Thread completed"); // TODO debug
	}
	
	//////////////////////////////////////////////////////////// Интерфейс ///////////////////////////////////////////////////////////////////
	interface Listener { // Интерфейсы неявно статические
		  static class Error{public int code=0; public String message=null;};
		  static class Status{
			  public int connections; // Если connections==0 то blocks не актуально
			  public int blocks;
			  public double synchronization;
			  
		  };
		  
		  static class SliceTx extends ArrayList< TransactionModel > { private static final long serialVersionUID = 1L;
		  		public int start; // начальная позиция (offs) в listtransactions, с которой запрашиваются транзакции
		  		public int limit; // конечная позиция, когда закончился набор затребованных транзакций
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
		
	private int txStart=0;			// FIXME В ядре счетчик транзакций это integer-32bit. по 2000 транз каждые 2 мин - это всего 4 года!
	private int txSize=0;			// +,- -- направление просмотра
	private String txFilter="";		// Всегда не null
	private int sliceChangeCnt=0;
	
		synchronized public int setTransactionsSlice(int start, int size) {return setTransactionsSlice(start, size, txFilter);}
		synchronized public int setTransactionsSlice(int start, int size, String filter) { 
			// Скролл организуется так что срезы транакцый при size>0 и size<0 эквивалентны и просто набираются в разных направлениях
			// (это необходимо для скролла в условиях фильтрации транзакций)
			
			if(filter==null) filter="";
			
			if( (txStart+(txSize<0?txSize:0))!=(start+(size<0?size:0)) || Math.abs(txSize)!=Math.abs(size) || !txFilter.equals(filter)) sliceChangeCnt++;
			
			txStart=start; txSize=size; txFilter=filter;
			
			if(sliceChangeCnt>0) speedUp(); // Принудительное пробуждение polling
			
			return sliceChangeCnt;
		}
			
	///////////////////////////////////////////////// Воркер /////////////////////////////////////////////////////////////////////////////
		
		
	private volatile long lastTransactionHash=-1; private int listenersCnt=-1;
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
		// Считывать транзакции стоит тогда, когда есть новые транзакции (или изменения в последней) или кода во вне меняется срез считывания (Slice)
		// При старте демона какое-то время Server returned HTTP response code: 500
		// Затем: -28 ( Loading block index... )
		// Также для некоторых команд может быть: -13 (Please enter the wallet passphrase with walletpassphrase first.) 
		
		MapResponse info=rpc.getStatusInfo();
		if (!info.isOk()) { // Нет подключения к RPC - ловить нечего	
			
			final Listener.Error err=new Listener.Error(); err.code=info.code; err.message=info.message;
			updateListeners(err);
			
			return;
		}
		
		final Listener.Status status=new Listener.Status();
			status.connections=Integer.parseInt(info.data.getOrDefault("connections", "0"));
			status.blocks=Integer.parseInt(info.data.getOrDefault("blocks", "0"));
			status.synchronization=Double.parseDouble(info.data.getOrDefault("synchronization", "0.0"));
			updateListeners(status);
		

		boolean txUpdated=transactionsUpdated(); // Чекаем наличие новых транзакций
		
		
		int start; final int size; final String filter; int sliceChangeCnt; // Текущий срез просмотра транзакций (скролл)
		
		synchronized(this) {
			start=txStart; size=txSize; filter=txFilter; sliceChangeCnt=this.sliceChangeCnt; // Будет обработка, если sliceChangeCnt > 0
			
			
			if(this.sliceChangeCnt>0) this.sliceChangeCnt--; // Следующий скролл обработаем когда он возникнет
			
			if(this.sliceChangeCnt>1) this.sliceChangeCnt=1; // Чтобы не накапливалось при быстрой пагинации . Меньше 1-цы нельзя ставить
															 // Иначе пропустит прогруз при быстрой пагинации 
		}   
		
		if( !txUpdated && sliceChangeCnt<=0 ) return;  // В багдаде все спокойно - на выход
		
		
		updateListeners(Boolean.valueOf(true)); // Флаг начала индикации обновления
		
		
		LOGGER.console("txUpdated="+txUpdated+" slice="+sliceChangeCnt);		// TODO debug
		
		// Кароче, start, size и filter, поставляются контроллеру из вне. Его задача тупо считать все что есть в количестве size и отдать слушателю.
		
		List< Map<String,String> > slice; // Считанный срез транзакций
		int offs; int limit;
		
		int chunkCount; int chunkSize; chunkSize=Math.abs(size);
		
		do {
			slice=new ArrayList<>(); // Считанный срез транзакций
			limit=offs=start;
			MapsResponse transactions;
			
			chunkCount= -1;
			do { chunkCount++; // Чтобы не делать transactionsUpdated() если все влезло за один раз
			
				chunkSize*=3; // Чтобы не тормазить по чуть чуть в случае включенного фильтра 
							  // FIXME: Запрос всех транз из консоли с последующим grep довольно быстро проходит
							  // (нужно работать через потоки до самого конца обработки больших кусков транз исключая копирование данных)
			
				// знак size управляет направлением чтения (снизу вверх или сверх вниз списка транзакций)
				if(size<0) { offs-=chunkSize; if(offs<0) {chunkSize+=offs; offs=0;} }
				
				transactions=rpc.listTransactions("*", chunkSize, offs, true); // В порядке от старых к новым (start - индекс смещения в сторону старых)
				if (!transactions.isOk()) {
					final Listener.Error err=new Listener.Error(); err.code=transactions.code; err.message=transactions.message;
					updateListeners(err);	
					return;	// XXX если прошла ошибка то кеш сделется не валидным и следующая transactionsUpdated даст true
				}
				
				if(size<0) {
					for(int i=0; i<transactions.data.size(); i++) { limit--;
						final Map<String,String> tx=transactions.data.get(i);
							
						if(TransactionModel.filter(tx, filter)) { slice.add(tx); if(slice.size()>=Math.abs(size)) break; }
					}
					
					limit-=(chunkSize-transactions.data.size());
				}
				else {// Подвергаем фильтрации с конца, чтобы порции пристыковывались корректно
					for(int i=transactions.data.size()-1; i>=0; i--) { limit++;
						final Map<String,String> tx=transactions.data.get(i);
											
						if(TransactionModel.filter(tx, filter)) { slice.add(tx); if(slice.size()>=Math.abs(size)) break; }
					}
					
					offs+=transactions.data.size(); // Готовимся к следующей порции
				}
				
				// Сейчас slice в порядке от новых к старым, или наоборот если size<0
				
				LOGGER.console(String.format("size=%d, chunk=%d, offs=%d", slice.size(), chunkSize, offs)); // TODO debug
				
			}while( slice.size()<Math.abs(size) && (transactions.data.size()>=chunkSize || size<0) && offs>0 );
			// Считали сколько нужно или  Считали все что есть
			
		}while( (chunkCount>0) && transactionsUpdated() ); // Повторить если вклинилась новая транзакция в порции чтения FIXME: не оптимально (можно еще брать с перекрытием и исключать дубликаты)
														   
		
		
		// Осталось перебрать все транзы и там где данные - заменить на результат gethexdata
		// В пределах одной транзы данные различимы по vout и могут дублироваться в категориях send/receive (например при отсылке на общие публичные ключи)
		
		Listener.SliceTx sliceTx = new Listener.SliceTx(); 	// Итоговый срез транзакций с данными
			sliceTx.start=start; sliceTx.limit=limit;	    // В случае фильтрации позиция limit может быть ооочень далеко от start
			// Если limit > start (size>0) то start указывает на первую транзу, а limit на 1-цу больше той позиции где набор транз закончился
			// Если limit < start (size<0) то наоборот - start на 1-цу больше, а с limit начинается список набранных транз
			
			
			
		// Части одной транзакции идут подряд . Могут быть одинаковые vout при разных category (send/receive самому себе)
L1:		for(Map<String,String> tx: slice) {
	
			if(!tx.containsKey("decryption")) sliceTx.add( new TransactionModel(tx) ); // Добавляем финансовую транзу как есть
			else { // Есть данные (проверяем чтобы не засерать кеш hexdata)
				
				String txid=tx.getOrDefault("txid", ""); // Идут части одной транзакции подряд. Они-же будут раскиданы по vout в "gethexdata"
				
				// getHexData кешированная
				MapsResponse content=rpc.getHexData(txid,true);	// true - как в Listtransactions (true подсветит чужие выходы с данными)
				if (!content.isOk()) { // Может быть code: -13 (Error: Please enter the wallet passphrase with walletpassphrase first.)
					final Listener.Error err=new Listener.Error(); err.code=content.code; err.message=content.message;
					updateListeners(err);
					return;
				}
				
				String vout=tx.getOrDefault("vout", "");
				// Контент в результат добавляем по vout
				for(final Map<String,String> data_tx: content.data) {
					if(vout.equals(data_tx.get("vout"))) {
						Map<String,String> d_tx=new HashMap<>(data_tx); // Будем делать модификации и нужно не затрагивать исходный data_tx,
					    												// который еще может быть и кешированным и юзаться в другом потоке

						// XXX Добавляем некоторые поля, которые не предоставляет команда "gethexdata"
						d_tx.put("involvesWatchonly", tx.getOrDefault("involvesWatchonly","false"));
						
						d_tx.put("trusted", tx.getOrDefault("trusted","false"));
						d_tx.put("amount", tx.getOrDefault("amount","0.0"));
						if(tx.containsKey("fee")) d_tx.put("fee", tx.get("fee")); // Сведения о комиссии за данные (есть только для send-категории)
						else  					  d_tx.remove("fee");
						// Транзакции различимы в модели по паре amount и fee, поэтому fee в категории receive не должно быть
						
						// Категорию наследуем для gethexdata из listtransactions чтобы откорректировалась категория дубликата
						d_tx.put("category", tx.getOrDefault("category",""));
						
						// FIXME а также то изменяемое чтобы перекрыть кеш hexdata
						d_tx.put("confirmations", tx.getOrDefault("confirmations","0"));
						d_tx.put("from", tx.getOrDefault("from","")); 	// Псевдонимы адресной книги
						d_tx.put("to", tx.getOrDefault("to",""));		// Псевдонимы адресной книги
						
						sliceTx.add( new TransactionModel(d_tx) ); 
						
						continue L1;						
					}
				}
				
				// Сюда не должен дойти код, но на всякий случай...
				sliceTx.add( new TransactionModel(tx) );
			}
		}
		
		if(--sliceChangeCnt>1) {
			LOGGER.warning("Slice Changes is overflow! ("+sliceChangeCnt+")");
			LOGGER.console("Slice Changes is overflow! ("+sliceChangeCnt+")"); // TODO debug
		}		
		
		if(size<0);
		else Collections.reverse(sliceTx);
		updateListeners(sliceTx); // В порядке от старых к новым и сразу в виде моделей для строк таблицы
	}

}




















