package coredrv;




import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import util.LOGGER;



public class RPCCommanderCached extends RPCCommander { 
	public RPCCommanderCached() {super();}
	public RPCCommanderCached(int rpcPort, String rpcUser, String rpcPassword) {super(rpcPort, rpcUser, rpcPassword);}
	public RPCCommanderCached(String rpcAddress, int rpcPort, String rpcUser, String rpcPassword) {super(rpcAddress, rpcPort, rpcUser, rpcPassword);}
		
	
	
	private boolean updateTransactions=false;	 // Триггер, означающий что кеш транзакций не актуален
	private long lastTransactionHash=-1;		 // Хэш крайней транзакции, для обнаружения поступления транзакций любых категорий ("move" в txcount не входят)
	private long saltTransaction=0;				 // Примесь для форсированного обновления транз
	synchronized public long getTransactionsUpdate() {  // Возвращает хеш последней транзы для потоко-независимой проверки обновления транз
													    // Изменение confirmation или category идентифицируются как новые транзы
		
		//if(updateTransactions) return lastTransactionHash; // В процессе обновления кэша
		
		MapsResponse tx=super.listTransactions("*", 1, 0, true); // Что за транзакции в конце списка?
		if (tx.isOk() && tx.data.size()>0) {	
			
			// Objects.hashCode(key) ^ Objects.hashCode(value); Для суммы не важен порядок обхода
			long hash=saltTransaction; for (Map.Entry<?,?> entry : tx.data.get(0).entrySet()) hash+= 0x00000000ffffffffL & entry.hashCode(); 
																				// -1 не просто получить (удобное значение для инициализации long hash)
																											   
			if(hash!=lastTransactionHash) { lastTransactionHash=hash; updateTransactions=true; }
			// 0!=-1 - пропихнется раз при запуске и это отлично!
		}
		
		return lastTransactionHash;
	}
	
	
	private class CachedTransactions {	// Актуальность кеша управляется из вне через флаг updateTransactions 
									    // FIXME: можно также по времени внутренне вызывать getTransactionsUpdate()
		List< Map<String,String> > data;
		int count,offs;
	};
	private final Map< String, CachedTransactions > cacheTransactions=new HashMap<>();
	
	@Override	// Ядро все равно пробегает все от конца транзакций, поэтому кэш можно делать непрерывным
	synchronized public MapsResponse listTransactions(String account, int count, int offs, boolean includeWatchonly) {

		String key=account+includeWatchonly;
		
		CachedTransactions cache=cacheTransactions.get(key);
		
		if(cache==null || updateTransactions ) {
			
			MapsResponse transactions=super.listTransactions(account, count, offs, includeWatchonly);
			
			if(!transactions.isOk()) {saltTransaction+=0x0000000100000000L; return transactions;} // Ошибка запроса
			
			cache=new CachedTransactions(); cache.count=count; cache.offs=offs; cache.data=transactions.data;
			
			cacheTransactions.put(key, cache);
			
			updateTransactions=false; // Устанавливается из вне вызовом getTransactionsUpdate()
			return transactions;
		}
		else {	
			
			if( offs>=cache.offs  && ( (offs-cache.offs+count)<=cache.count || cache.count>cache.data.size() ) ) { 	// В границах кеша
				MapsResponse transactions=new MapsResponse();
					
				int from=cache.data.size()-(offs-cache.offs+count); if(from<0) from=0;
				int to=cache.data.size()-(offs-cache.offs); if(to<0) to=0;
					
				transactions.data=new ArrayList<>( cache.data.subList(from, to) ); // Исходный кеш меняется всегда - поэтому нужно делать копию
				
				if( (offs-cache.offs+count)>cache.count ) cache.count=offs-cache.offs+count;
				
				return transactions;
			}
			else { // Делаем непрерывный срез за один раз, расширяя границы
				
				if(offs>=cache.offs ) { // Добор сверху FIXME (ядро всеравно пробегает внутренне по всем от offs=0)
						
					int tx_offs=cache.offs+cache.data.size();
					int tx_count=offs-cache.offs+count-cache.data.size(); if(tx_count<0) tx_count=0;
					
					MapsResponse transactions;
					if(tx_count>0) {
						transactions=super.listTransactions(account, tx_count, tx_offs, includeWatchonly);
						if(!transactions.isOk()) {saltTransaction+=0x0000000100000000L; return transactions;} // Ошибка запроса
					}
					else transactions=new MapsResponse();
					
					if(transactions.data.size()>0) cache.data.addAll(0, transactions.data);		

					int from=0;
					int to=cache.data.size()-(offs-cache.offs); if(to<0) to=0;
					
					transactions.data=new ArrayList<>( cache.data.subList(from, to) );
					
					cache.count=offs-cache.offs+count;			
					
					return transactions;
				}
				else if(cache.offs-offs+cache.count>=count || cache.count>cache.data.size()) { // Добор снизу
					
					int tx_offs=offs;
					int tx_count=cache.offs-offs; if(tx_count<0) tx_count=0;
					
					MapsResponse transactions;
					if(tx_count>0) {
						transactions=super.listTransactions(account, tx_count, tx_offs, includeWatchonly);
						if(!transactions.isOk()) {saltTransaction+=0x0000000100000000L; return transactions;} // Ошибка запроса
					}
					else transactions=new MapsResponse();
					
					if(transactions.data.size()>0) cache.data.addAll(transactions.data);		
					
					int from=cache.data.size()-count; if(from<0) from=0;
					int to=cache.data.size();
					
					transactions.data=new ArrayList<>( cache.data.subList(from, to) );
					
					if( cache.offs-offs+cache.count<count ) cache.count=count;
					cache.offs=offs;
					
					return transactions;
				}
				else { // Остается только набрать все - от и до FIXME (примерно 512 байт на одну транзу расход оперативы)
					MapsResponse transactions=super.listTransactions(account, count, offs, includeWatchonly);
					
					if(!transactions.isOk()) {saltTransaction+=0x0000000100000000L; return transactions;} // Ошибка запроса
					
					cache=new CachedTransactions(); cache.count=count; cache.offs=offs; cache.data=transactions.data;
						
					cacheTransactions.put(key, cache);
					
					return transactions;				
				}	
			}
		}
	}
	
	final int STATUS_MAX_AGE=4001;
	private class CachedStatus { 
		Map<String,String> data=null;
		long expiry=0;
	};
	private final CachedStatus cacheStatus=new CachedStatus();

	@Override synchronized public MapResponse getStatusInfo() {
		long time= System.currentTimeMillis();
		
		if(cacheStatus.data==null || (time-cacheStatus.expiry)>0) { // Обновление кеша
			
			MapResponse status=super.getStatusInfo();
			if(!status.isOk()) return status; // Ошибка запроса
			
			// Сохраняем в кеше
			cacheStatus.data=status.data;
			cacheStatus.expiry=time+STATUS_MAX_AGE;
				
			return status;
		}
		else { // Берем из кеша
			MapResponse status= new MapResponse();
			status.data=cacheStatus.data;
					
			return status;
		}
	}
	synchronized public void resetStatusCache() {cacheStatus.data=null;};
	// Команды которые списывают мани должны инвалидировать статус (чтобы не ждать долго его обновления в кеше)
	@Override synchronized public StringResponse sendToAddress(String address, long amount, boolean subtractFee) {
		StringResponse res=super.sendToAddress(address,amount,subtractFee);
		if(res.isOk()) cacheStatus.data=null;
		return res;
	}
	@Override synchronized public StringResponse sendContent(String from, String to, String content, boolean open) {
		StringResponse res=super.sendContent(from, to, content, open);
		if(res.isOk()) cacheStatus.data=null;
		return res;
	}
	@Override synchronized public StringResponse pinContent(String from, String to, String content, boolean open) {
		StringResponse res=super.pinContent(from, to, content, open);
		if(res.isOk()) cacheStatus.data=null;
		return res;		
	}
	@Override synchronized public StringResponse sendMessage(String from, String to, String message) {
		StringResponse res=super.sendMessage(from, to, message);
		if(res.isOk()) cacheStatus.data=null;
		return res;			
	}
	
	
	final int ADDRESSBOOK_MAX_AGE=14983;
	private class CachedAddressBook { 
		volatile List< Map<String,String> > data=null;
		long expiry=0;
	};
	
	private final CachedAddressBook cacheAddressBook=  new CachedAddressBook();
	@Override synchronized public MapsResponse getAddressBook() {
		long time= System.currentTimeMillis();		
		
		if(cacheAddressBook.data==null || (time-cacheAddressBook.expiry)>0) {
			
			MapsResponse ab=super.getAddressBook();
			if(!ab.isOk()) return ab;
			
			cacheAddressBook.data=ab.data;
			cacheAddressBook.expiry=time+ADDRESSBOOK_MAX_AGE;
			
			return ab;
		}
		else {
			MapsResponse ab= new MapsResponse();
			ab.data=cacheAddressBook.data;
			
			return ab;
		}
	}
	
	
	// Генерация новых адресов не изменяет список транз, а то что меняет листинг транз должно инвалидировать кеш
	
	@Override synchronized public StringResponse setAccount(String addressID, String account) {
		StringResponse res=super.setAccount(addressID, account);
		if(res.isOk()) {saltTransaction+=0x0000000100000000L; cacheAddressBook.data=null;}// Если меняются имена аккаунтов, то транзы нужно перечитать
		
		return res;
	}
	
	@Override synchronized public StringResponse importPrivKey(String privkey, String account) {
		StringResponse res=super.importPrivKey(privkey, account);
		if(res.isOk()) {saltTransaction+=0x0000000100000000L; cacheAddressBook.data=null;} // Рестарт кэша транз так как по завершении скана добавятся новые транзы
		
		return res;
	}
	@Override synchronized public StringResponse importPubKey(String pubkey, String account) {
		StringResponse res=super.importPubKey(pubkey, account);
		if(res.isOk()) {saltTransaction+=0x0000000100000000L; cacheAddressBook.data=null;} // Инвалидация кеша
		
		return res;
	}
	@Override synchronized public StringResponse importAddress(String address, String account) {
		StringResponse res=super.importAddress(address, account);
		if(res.isOk()) {saltTransaction+=0x0000000100000000L; cacheAddressBook.data=null;} // Invalidate
		
		return res;
	}	
	@Override synchronized public StringResponse addMultisigAddress(String key, String account) {
		StringResponse res=super.addMultisigAddress(key, account);
		if(res.isOk()) {saltTransaction+=0x0000000100000000L; cacheAddressBook.data=null;} // Если повторные добавления того-же адреса но с разными аккаунтами - Invalidate
		
		return res;		
	}
	@Override synchronized public StringResponse getNewAddress(String account) {
		StringResponse res=super.getNewAddress(account);
		if(res.isOk()) {cacheAddressBook.data=null;} // Список транз не изменяет. Только адресную книгу
		
		return res;	
	}
	
	final int HEXDATA_MAX_COUNT=386; // Кеш где-то на 38Мб в ОЗУ макс
	private final Map<  String, List< Map<String,String> >  > cacheHexData=new HashMap<>(); // getHexData возвращает список по vout
	@Override synchronized public MapsResponse getHexData(String txid, boolean includeWatchonly) {
		
		String key=txid+includeWatchonly;
		
		List< Map<String,String> > cache=cacheHexData.get(key);
		
		if(cache==null) { // Новая txid (не в кеше)

			MapsResponse hexdata=super.getHexData(txid, includeWatchonly);
			if(!hexdata.isOk()) return hexdata; // Ошибка запроса
			
			
			if(cacheHexData.size()>HEXDATA_MAX_COUNT) { // проредим малек
				cacheHexData.keySet().removeIf((k)->Math.random()<0.5); // В среднем половину убиваем старых ключей
				LOGGER.console("Thinned hexdata cache, the remainder="+cacheHexData.size()); // TODO debug
			}
			
			// Сохраняем в кеше новый ключь
			cacheHexData.put(key, hexdata.data);
			
			return hexdata;
		}
		else { // Берем из кеша
			MapsResponse hexdata= new MapsResponse();
			hexdata.data=cache;
					
			return hexdata;
		}		
	}
	
	final int SIGNATURE_MAX_COUNT=1024;
	private final Map<String, Boolean> cacheSignature=new HashMap<>();
	@Override synchronized protected BoolResponse verifySignature(String txid) {
		
		String key=txid;
		
		Boolean cache=cacheSignature.get(key);

		if(cache==null) {

			BoolResponse verify=super.verifySignature(txid);
			if(!verify.isOk()) return verify; // Ошибка запроса
			
			if(cacheSignature.size()>SIGNATURE_MAX_COUNT) { // проредим малек
				cacheSignature.keySet().removeIf((k)->Math.random()<0.5); // В среднем половину убиваем старых ключей
				LOGGER.console("Thinned signature cache, the remainder="+cacheSignature.size()); // TODO debug
			}
			
			// Сохраняем в кеше новый ключь
			cacheSignature.put(key, verify.data);
			
			return verify;
		}
		else { // Берем из кеша
			BoolResponse verify= new BoolResponse();
			verify.data=cache;
					
			return verify;
		}	
	}
	
	final int PINNED_MAX_COUNT=1024;
	private final Map<String, Boolean> cachePinned=new HashMap<>();
	@Override synchronized public BoolResponse verifyPinned(String txid) {
		
		String key=txid;
		
		Boolean cache=cachePinned.get(key);

		if(cache==null) {

			BoolResponse verify=super.verifyPinned(txid);
			if(!verify.isOk()) return verify; // Ошибка запроса в кеш не попадает
			
			if(cachePinned.size()>PINNED_MAX_COUNT) { // проредим малек
				cachePinned.keySet().removeIf((k)->Math.random()<0.5); // В среднем половину убиваем старых ключей
				LOGGER.console("Thinned pinned cache, the remainder="+cachePinned.size()); // TODO debug
			}
			
			// Сохраняем в кеше новый ключь
			cachePinned.put(key, verify.data);
			
			return verify;
		}
		else { // Берем из кеша
			BoolResponse verify= new BoolResponse();
			verify.data=cache;
					
			return verify;
		}	
	}
	
	@Override synchronized public BoolResponse walletPassphrase(String password) { // В случае успеха нужно инвалидировать кэши
		
		BoolResponse result=super.walletPassphrase(password);
		
		if(result.isOk()) {
			saltTransaction+=0x0000000100000000L; cacheAddressBook.data=null;
			
			cacheStatus.data=null;
			
			cacheHexData.clear();
			cacheSignature.clear();
			cachePinned.clear();
		}
		
		return result;
	}
}
