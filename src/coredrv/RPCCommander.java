package coredrv;

import static application.Main.*;

import static util.Binary.stringToUtf8Bytes;
import static util.Binary.unescape;
import static util.Binary.utf8BytesToString;
import static util.Binary.hexStringToByteArray;
import static util.Binary.byteArrayToHexString;
import static util.Binary.escape_json;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class RPCCommander extends RPCParser { // Создает запросы
	public RPCCommander() {super();}
	public RPCCommander(int rpcPort, String rpcUser, String rpcPassword) {super(rpcPort, rpcUser, rpcPassword);}
	public RPCCommander(String rpcAddress, int rpcPort, String rpcUser, String rpcPassword) {super(rpcAddress, rpcPort, rpcUser, rpcPassword);}
	// Поля потомка инициализируются после конструкторов предка и до своих конструкторов (id уже есть)
	private final String cmdFormat="{\"jsonrpc\": \"1.0\", \"id\":\"longjava-"+id+"\", \"method\": \"%s\", \"params\": [%s] }";
	private final ByteBuffer keyError=ByteBuffer.wrap( stringToUtf8Bytes("\"error\"") );
	private final ByteBuffer keyMessage=ByteBuffer.wrap( stringToUtf8Bytes("\"message\"") );
	private final ByteBuffer keyCode=ByteBuffer.wrap( stringToUtf8Bytes("\"code\"") );
	private final ByteBuffer keyResult=ByteBuffer.wrap( stringToUtf8Bytes("\"result\"") );
	private final ByteBuffer valNull=ByteBuffer.wrap( stringToUtf8Bytes("null") ); // null без кавычек возвращается (js)
	
	
	static public class Response { 		
		public ByteBuffer data=null; public int code=0; public String message=null; 
		// коды http, отрицательные - RPC, 0 и 200 - нет ошибок
		// message != null и data==null только в случае ошибок
	} 
		
	public Response sendCommand(String command, String ...params) { // Возвращает сразу вложение в "result: ..."
		
		Response ret=new Response();
		
		StringBuilder sb=new StringBuilder(); for(String p: params) sb.append(p+","); if(params.length>0) sb.deleteCharAt(sb.length()-1);
		
		String strRequest=String.format(cmdFormat, command, sb);
		
		RPCHandler.Response res =rawRequest(  stringToUtf8Bytes(strRequest)  ); ret.code=res.code;
			// {"result":...,"error":null,"id":"longjava-1"}
			// {"result":null,"error":{"code":-28,"message":"..."},"id":"longjava-1"}
		// HttpURLConnection.HTTP_OK (200) - при ок; HttpURLConnection.HTTP_INTERNAL_ERROR (500) - при наличие подключения но не верной команде или занятости блокчейна;
		
		if(res.data==null || res.data.length==0) { // Нет подключения или еще хз чего (смотреть по коду)
			ret.message="RPC Connection problem";
			return ret; // ret.data==null
		}
		
		// Ответ есть палюбасу
		Map<ByteBuffer,ByteBuffer> responseObject=rawParseObject(res.data);
		//responseObject.forEach((k,v)->LOGGER.console(convert(k)+":"+convert(v))); //TODO debug
		ByteBuffer valError=responseObject.get(keyError);
		if( valError!=null && !valError.equals(valNull) ) { // Ошибка RPC и "error" содержит объект с доп. информацией
			Map<ByteBuffer,ByteBuffer> error=rawParseObject(valError);
			
			ByteBuffer valCode=error.get(keyCode); ByteBuffer valMessage=error.get(keyMessage);
			
			if(valCode!=null) try{ ret.code=Integer.parseInt(convert(valCode)); } catch(NumberFormatException ignore){} // или останется что есть после ret.code=res.code;
			
			ret.message="RPC Handle problem"; // или хз что
			// На национальных языках RPC возвращает сообщение об ошибке в unicode escaped: "\\uXXXX ..."
			if(valMessage!=null) ret.message=  convert(valMessage) ;
			
			return ret; // ret.data==null
		}
		// Все прошло гладко - в "result": ... - ответ
		ret.data=responseObject.get(keyResult);
		return ret; // ret.message==null;
	}
	public String convert(ByteBuffer buf) { // С убоем ковычек в начале и конце, если есть.
		final byte[] in=buf.array(); final int start=buf.position(); final int stop=buf.limit();
		
		int left=start; while(left<stop) { if(in[left]==' ') {left++; continue;}  if(in[left]=='"') {left++; break;}  break; }
		int right=stop; while(right>start) { right--; if(in[right]==' ') continue; if(in[right]=='"') break; right++; break; }

		if(right>=left) return  unescape ( new String(in,left,right-left,StandardCharsets.UTF_8) ); // unicode escaped: "\\uXXXX ..."
		else return null;
	}
	public Map<String,String> convert( Map<ByteBuffer,ByteBuffer> map){ 
		Map<String,String> ret=new HashMap<>();
		if(!map.isEmpty()) map.forEach( (k, v) -> ret.put(convert(k),convert(v)) );
		return ret;
	}
	public List<String> convert( List<ByteBuffer> list){
		List<String> ret=new ArrayList<>();
		if(!list.isEmpty()) list.forEach( (v) -> ret.add(convert(v)) );
		return ret;
	}
	
	// ------------------------------------------------------- Высокоуровневые RPC команды ----------------------------------------------------------------------------
	// Рассчитываем на то, что для исполнительной среды возвращение каждого нового объекта и обновление полей существующего примерно эквивалентно по производительности
	
	public static final int HTTP_OK=200;
	public static final int RPC_OK=0;
																   // rpcprotocol.h in core:
	public static final int	RPC_WALLET_UNLOCK_NEEDED        = -13; //! Enter the wallet passphrase with walletpassphrase first
	public static final int RPC_WALLET_PASSPHRASE_INCORRECT = -14; //! The wallet passphrase entered was incorrect
	public static final int RPC_WALLET_WRONG_ENC_STATE      = -15; //! Command given in wrong wallet encryption state (encrypting an encrypted wallet etc.)
	public static final int RPC_WALLET_ENCRYPTION_FAILED    = -16; //! Failed to encrypt the wallet
	public static final int RPC_WALLET_ALREADY_UNLOCKED     = -17; //! Wallet is already unlocked
	
	public static boolean codeIsOk(int code) {return code==HTTP_OK || code==RPC_OK;}
	
	private static String s(String s) {return '"'+escape_json(s)+'"';}	// quoted string FIXME: Следить за тем чтобы в params не ескапились кавычки полей JSON
	private static String n(int n) {return String.valueOf(n);}	        // number				escape только для unicode-строк на национальных языка для
	private static String n(long n) {return String.valueOf(n);}
	private static String n(double n) {return String.valueOf(n);}
	private static String b(boolean b) {return String.valueOf(b);}      // boolean flag			текстовых параметров типа названия меток аккаунтов
	
	
	public static class MapResponse {
		public Map<String,String> data=new HashMap<>();
		public int code=0;
		public String message=null;
		public boolean isOk() {return codeIsOk(code);}
	}
	
	
	final int POW_TARGET_SPACING=2*60;				// Для прикидки синхронизации без подключения к пирам
	final int POW_TARGET_TIMESPAN=60*60;
	
	public MapResponse getStatusInfo(){		// XXX В зашифрованном кошельке есть поле "unlocked_until":0
		MapResponse ret=new MapResponse();
		
		// FIXME при последовательном вызове могут вклиниваться вызовы от других потоков
		
		Response res1=sendCommand("getmininginfo",""); ret.code=res1.code; ret.message=res1.message;
		if(res1.data!=null) {
			ret.data.putAll( convert( rawParseObject(res1.data) ) );
			
			Response res2=sendCommand("getblockchaininfo",""); ret.code=res2.code; ret.message=res2.message;
			if(res2.data!=null) {
				ret.data.putAll( convert( rawParseObject(res2.data) ) );
				
				Response res3=sendCommand("getnetworkinfo",""); ret.code=res3.code; ret.message=res3.message;
				if(res3.data!=null) {
					ret.data.putAll( convert( rawParseObject(res3.data) ) );	
					
					Response res4=sendCommand("getwalletinfo",""); ret.code=res4.code; ret.message=res4.message;
					if(res4.data!=null) {
						ret.data.putAll( convert( rawParseObject(res4.data) ) );
						
						
						 //int connections=Integer.parseInt(ret.data.getOrDefault("connections", "0")); // Если connections==0 то blocks не актуально
						 int blocks=Integer.parseInt(ret.data.getOrDefault("blocks", "0"));
						 int headers=Integer.parseInt(ret.data.getOrDefault("headers", "0"));
						 double verificationprogress=Double.parseDouble(ret.data.getOrDefault("verificationprogress", "0.0"));
						 long mediantime=Long.parseLong(ret.data.getOrDefault("mediantime", "0")); // отстает от текущего майнинга (может и на час)
						
						 
						 //if(connections!=0) headers=(headers>0) ? headers: Integer.MAX_VALUE;
						 //else {
							 long time = System.currentTimeMillis() / 1000;
							 
							 long age=time-(mediantime+POW_TARGET_TIMESPAN);
							 if(age<0) age=0;							 
								 
							 headers+=(age/POW_TARGET_SPACING);
							 if(headers<=0) headers=Integer.MAX_VALUE; 
	
						 //}
						 
						 double synchronization=(double)blocks/headers;
						 if(verificationprogress<synchronization) synchronization=verificationprogress;
						 synchronization+=Double.MIN_NORMAL; // В долях
						 
						 ret.data.put("synchronization", String.valueOf(synchronization));
						 
						 //LOGGER.console("synchronization="+synchronization); // TODO debug
						 
						 
						 // FIXME обрануживаем залоченность кошелька по фейковой команде dumppubkey так как по "unlocked_until" не надежно
						 if(ret.data.containsKey("unlocked_until") && Long.parseLong(ret.data.get("unlocked_until"))>0) {
							 
							 Response test=sendCommand("dumppubkey",s(""));
							 
							 if(test.code==RPC_WALLET_UNLOCK_NEEDED) ret.data.put("unlocked_until", "0");
							 
						 }
					}
				}
			}
		}
		
		
		//ret.data.forEach((k,v)->LOGGER.console(k+":"+v)); //TODO debug
		
		return ret;
	}
	
	
	
	public static class MapsResponse {
		public List< Map<String,String> > data=new ArrayList<>();
		public int code=0; 
		public String message=null;
		public boolean isOk() {return codeIsOk(code);}
	}
	
	
	// "*" - со всех аккаунтов единым списком. from - отсчитывается от последней транзакции (from==0) и count штук предыдущих, выводя в порядке от старых к новым
	public MapsResponse listTransactions(String account, int count, int offs, boolean includeWatchonly){
		MapsResponse ret=new MapsResponse();
		
		Response res=sendCommand("listtransactions",s(account),n(count),n(offs),b(includeWatchonly));
			ret.code=res.code; ret.message=res.message;
		
		if(res.data!=null) { // Macсив объектов
			List<ByteBuffer> objects=rawParseArray(res.data);
			objects.forEach(  (e) -> ret.data.add( convert(rawParseObject(e)) )  );	
		}
		
		
		// Дополняем транзы флагом trusted, стараясь избегать излишнего кеширования
		
		for(Map<String,String> tx: ret.data) {
			if(!tx.containsKey("decryption")) {tx.put("trusted", "true"); continue;} // Финансовая
			
			if(!tx.get("decryption").equals("yes")) {tx.put("trusted", "false"); continue;} // Крякозябры
						
			
			if(!tx.getOrDefault("address", "").startsWith("3")) {tx.put("trusted", "true"); continue;} // Обычные данные
			
			BoolResponse verify=verifySignature(tx.getOrDefault("txid", "")); //ret.code=verify.code; ret.message=verify.message; if(!verify.isOk()) return ret;
			
			if(verify.data==true) tx.put("trusted", "true");
			else tx.put("trusted", "false");
		}
		
		
		return ret;
		// Пока идет сканирование блоков, то "bip125-replaceable": "unknown" а "confirmations": 0
	}
	
	
	public static class BoolResponse {
		public Boolean data=false;
		public int code=0; 
		public String message=null;
		public boolean isOk() {return codeIsOk(code);}
	}
	final int SIGNATURE_HEX_LENGTH=176; 
	protected BoolResponse verifySignature(String txid) { 
		// Вся информация есть в кешированной getHexData.

		
		BoolResponse ret=new BoolResponse();
		
		// Транзакции, содержащие сигнатуру, влияют на флаг "trusted", который может быть задействован для быстрой фильтрации.
		// В списке транзакций это транзакция где в vout0 - данные, в vout1 - сигнатура, созданная адресом в fromaddress. 
		// Транзакции с одним txid и разными vout идут подряд, fromaddress образующий для p2sh адреса в поле toaddress
		
		// FIXME fundrawtransactions может изменять vout. поэтому vout с сигнатурой нужно искать по размеру сигнатуры
		
		
		MapsResponse hexdata=getHexData(txid,true); ret.code=hexdata.code; ret.message=hexdata.message; if(!hexdata.isOk()) return ret;
		
		if(hexdata.data.size()<2) return ret; // Финансовая транза или обычные данные (приватный или групповой чат)
		
		
		Map<String,String> vout0=hexdata.data.get(0);
		Map<String,String> vout1=hexdata.data.get(1);
		
		
		String toaddress=vout0.getOrDefault("toaddress", "");
		if(!toaddress.startsWith("3") || !toaddress.equals(vout1.getOrDefault("toaddress", ""))) return ret; // Не канальный адрес
		
		String frompubkey=vout0.getOrDefault("frompubkey", "");
		if(frompubkey.isBlank() || !frompubkey.equals(vout1.getOrDefault("frompubkey", ""))) return ret;
		
		if(!vout0.getOrDefault("decryption", "no").equals("yes") || !vout1.getOrDefault("decryption", "no").equals("yes")) return ret;
		
		StringResponse msig=getMultisig1(frompubkey); ret.code=msig.code; ret.message=msig.message; if(!msig.isOk()) return ret;
		if(!toaddress.equals(msig.data)) return ret; // from не образующий для маркета адрес, а левый
		
		String fromaddress=vout0.getOrDefault("fromaddress", "");
		
		String hex0=vout0.getOrDefault("hexdata", "");
		String hex1=vout1.getOrDefault("hexdata", "");
		
		String message, signature;
		
		// На всякий случай проверим порядок vout и если возможно сменим
		if(hex1.length()==SIGNATURE_HEX_LENGTH) {
			message=utf8BytesToString(hexStringToByteArray(hex0));
			signature=utf8BytesToString(hexStringToByteArray(hex1));
		}
		else if(hex0.length()==SIGNATURE_HEX_LENGTH) {
			message=utf8BytesToString(hexStringToByteArray(hex1));
			signature=utf8BytesToString(hexStringToByteArray(hex0));
		}
		else { // Сигнатуры нет точно
			return ret;
		}

		BoolResponse verify=verifyMessage(fromaddress, signature, message);
				
		return verify;
	}
	
	public BoolResponse verifyPinned(String txid) {
		BoolResponse ret=new BoolResponse();
		
		StringResponse rawtx=getRawTransaction(txid,true); ret.code=rawtx.code; ret.message=rawtx.message; if(!rawtx.isOk()) return ret;
		
		MapsResponse tx=decodeRawTransaction(rawtx.data); ret.code=tx.code; ret.message=tx.message; if(!tx.isOk()) return ret;
		
		boolean opReturn=false, pinAddress=false;
		
		for( Map<String,String> v: tx.data) { 
			
			if(!v.containsKey("n")) continue; // Нас интересуют только выходы
			
			String scriptPubKey=v.getOrDefault("scriptPubKey", "");
			double cost=Double.parseDouble(v.getOrDefault("value", "0.0"));
			
			if(!opReturn && scriptPubKey.contains("OP_RETURN")) { // Должны быть закрепляемые данные
				opReturn=true;
				continue;
			}
			
			if(!pinAddress && scriptPubKey.contains("\"addresses\":[\""+PIN_ADDRESS+"\"]") && cost >= PIN_COST-Double.MIN_NORMAL) { // Должна быть плата за закреп
				pinAddress=true;
				continue;
			}	
		}
		
		ret.data= opReturn && pinAddress;
		
		
		return ret;
	}
	private MapsResponse decodeRawTransaction(String hex) {
		MapsResponse ret=new MapsResponse();
		// Список подряд объектов vin и vout, различимых по ключам (именам присутствующих ключей. см. longcoin-cli help decoderawtransaction)
		
		Response res=sendCommand("decoderawtransaction",s(hex)); // Один объект {} с ключами "vin", "vout" внутри которых массив объектов
			ret.code=res.code; ret.message=res.message;
		
		if(res.data!=null) {
			final ByteBuffer vinKey=ByteBuffer.wrap( stringToUtf8Bytes("\"vin\"") );
			final ByteBuffer voutKey=ByteBuffer.wrap( stringToUtf8Bytes("\"vout\"") );
			
			Map<ByteBuffer,ByteBuffer> map= rawParseObject( res.data ); // Здесь не нужная "шапка" и "vin":[...] ,"vout":[...] 
			
			ByteBuffer vinVal=map.get(vinKey);
			if(vinVal!=null) {
				List<ByteBuffer> vlist=rawParseArray(vinVal);
				vlist.forEach((obj)->{
					Map<ByteBuffer,ByteBuffer> m=rawParseObject(obj);
					ret.data.add( convert(m) );
				});
			}
			
			ByteBuffer voutVal=map.get(voutKey);
			if(voutVal!=null) {
				List<ByteBuffer> vlist=rawParseArray(voutVal);
				vlist.forEach((obj)->{
					Map<ByteBuffer,ByteBuffer> m=rawParseObject(obj);
					ret.data.add( convert(m) );
				});
			}
		}
			
		return ret;
	}
	private StringResponse getRawTransaction(String txid, boolean includeWatchonly) { // Работаем через gettransaction чтобы брать только ловимые кошельком транзы (в pruned-режиме)
		StringResponse ret=new StringResponse();
		
		Response res=sendCommand("gettransaction",s(txid),b(includeWatchonly)); ret.code=res.code; ret.message=res.message;
		if(res.data!=null) ret.data= convert(rawParseObject(res.data)).getOrDefault("hex", "");
		return ret;
	}
	
	

	public MapsResponse getHexData(String txid, boolean includeWatchonly) {
		MapsResponse ret=new MapsResponse();
		
		Response res=sendCommand("gethexdata",s(txid),b(includeWatchonly)); // Один объект {} внутри которого список объектов (в поле "details":[...])
			ret.code=res.code; ret.message=res.message;
		
		// Нужно взять все объекты в поле details и пристыковать к ним "шапку", чтобы получились единые map в стиле транзакций
		if(res.data!=null) {
			final ByteBuffer detailsKey=ByteBuffer.wrap( stringToUtf8Bytes("\"details\"") );
			Map<ByteBuffer,ByteBuffer> map= rawParseObject( res.data ); // Здесь "шапка" и "details":[...]
			ByteBuffer detailsVal=map.get(detailsKey);
			if(detailsVal!=null) {
				List<ByteBuffer> dlist=rawParseArray(detailsVal); // Вот к этим объектам нужно добавлять шапку
				map.remove(detailsKey); // Только Шапка
				dlist.forEach( (obj)->{   // А если dlist пустой (как в транзакции без данных) то вернет пустое Map
					Map<ByteBuffer,ByteBuffer> m=rawParseObject(obj);
					if(!map.isEmpty()) m.putAll(map);
					ret.data.add( convert(m) );
				});
			}
		}
		return ret;
	}
	
	public static class StringResponse {
		public String data="";
		public int code=0; 
		public String message=null;
		public boolean isOk() {return codeIsOk(code);}
	}
	public StringResponse sendContent(String from, String to, String content, boolean open) { // Возвращает id транзакции
		// from - только свой адресс; to - адресс или публичный ключ
		
		StringResponse ret=new StringResponse();
		
		Response res=sendCommand("sendhexdata",s(from),s(to),s(byteArrayToHexString(stringToUtf8Bytes(content))),s(""),b(open)); // в успешном ответе id транзакции
			ret.code=res.code; ret.message=res.message;
		
		if(res.data!=null) ret.data=convert(res.data);
			
		return ret;
	}
	
	public StringResponse pinContent(String from, String to, String content, boolean open) {
		// from - адресс, публичный ключь, приватник; to - адресс или публичный ключ
		// PIN_ADDRESS PIN_COST - жестко зашиты в глобальных константах
		
		StringResponse ret=new StringResponse();
		// Транзакция содержит два выхода:
		// - один это данные на обычный адрес (импортированный или приватной группы или паблика);
		// - второй это плата за закреп на предустановленный фиксированный адрес (комса в плату не входит)
		
		Response res1=sendCommand("createrawdata",s(from),s(to),s(byteArrayToHexString(stringToUtf8Bytes(content))),b(open));
			ret.code=res1.code; ret.message=res1.message;
		if(res1.data==null) return ret; // Ошибка запроса
		
		String raw_content=convert(res1.data);

		Response res2=sendCommand("createrawtransaction","[]","{" +"\"data\":"+s(raw_content)+","+s(PIN_ADDRESS)+":"+n(PIN_COST)+ "}");
			ret.code=res2.code; ret.message=res2.message;
		if(res2.data==null) return ret; // Ошибка запроса		
	
		String hex_tx=convert(res2.data);
		
		Response res3=sendCommand("fundrawtransaction",s(hex_tx));	// Залили списания
			ret.code=res3.code; ret.message=res3.message;
		if(res3.data==null) return ret; // Insufficient funds
	
		String fund_tx=convert(rawParseObject(res3.data)).getOrDefault("hex", "");
	
		Response res4=sendCommand("signrawtransaction",s(fund_tx)); // подпись трат (списаний)
			ret.code=res4.code; ret.message=res4.message;
		if(res4.data==null) return ret; // Ошибка запроса			
	
		String sign_tx=convert(rawParseObject(res4.data)).getOrDefault("hex", ""); // FIXME флаг "complete" не проверяем (GUI не дает абъюзить)
	
		Response res5=sendCommand("sendrawtransaction",s(sign_tx));
			ret.code=res5.code; ret.message=res5.message;
		if(res5.data==null) return ret; // Ошибка запроса	
		
		ret.data=convert(res5.data); // txid
	
		return ret;	
	}
	
	public StringResponse sendMessage(String from, String to, String message) {
		// from - только свой адресс; to - любой адресс или публичный ключ
		// В общем случае from может и не быть образующим для p2sh to адресса (он просто используется для подписи) 
		// Для маркетов и каналов (когда to образован из from) сигнатуру подписи образующим адресом проверяем при чтении транз
		
		
		StringResponse ret=new StringResponse();
		// Транзакция содержит два выхода: 
		// - один это данные на адрес p2sh, на который шифрация не возможна (из-за отсутствия у таких адресов публичного ключа);
		// - второй это сигнатура подписи данных приватным ключем from (чтобы отличить незашифрованные данные владельца)
		// такая транзакция видна в кошельках с импортированным p2sh и устанавливает автора по подписи образующим адресом
		// (способ организации каналов во владении - читают все а отвечают тет-а-тет владельцу канала)
		
		// Ядро перед генерацией подписи предваряет Message такой строкой: "Bitcoin Signed Message:\n"
		
		Response res1=sendCommand("signmessage",s(from),s(message)); // не свой адрес не сможет подписать
			ret.code=res1.code; ret.message=res1.message;
		if(res1.data==null) return ret; // Ошибка запроса
		
		String signature=convert(res1.data);
		
		Response res2=sendCommand("dumppubkey",s(from)); // у своего адреса всегда есть публичный ключ
			ret.code=res2.code; ret.message=res2.message;
		if(res2.data==null) return ret; // Ошибка запроса
		
		String pubkey=convert(res2.data);
			
		// Создаем два vout с данными
		
		Response res3=sendCommand("createrawdata",s(pubkey),s(to),s(byteArrayToHexString(stringToUtf8Bytes(message))),b(true));
			ret.code=res3.code; ret.message=res3.message;
		if(res3.data==null) return ret; // Ошибка запроса
		
		String raw_msg=convert(res3.data);
		
		Response res4=sendCommand("createrawdata",s(pubkey),s(to),s(byteArrayToHexString(stringToUtf8Bytes(signature))),b(true));
			ret.code=res4.code; ret.message=res4.message;
		if(res4.data==null) return ret; // Ошибка запроса
	
		String raw_sig=convert(res4.data);		
		
		// Создаем транзакцию
		
		Response res5=sendCommand("createrawtransaction","[]","{" +"\"dataM\":"+s(raw_msg)+","+"\"dataS\":"+s(raw_sig)+ "}");
			ret.code=res5.code; ret.message=res5.message;
		if(res5.data==null) return ret; // Ошибка запроса		
		
		String hex_tx=convert(res5.data);
		
		// Последние штрихи
		
		Response res6=sendCommand("fundrawtransaction",s(hex_tx));	// Залили списания комиссии
			ret.code=res6.code; ret.message=res6.message;
		if(res6.data==null) return ret; // Ошибка запроса			
		
		String fund_tx=convert(rawParseObject(res6.data)).getOrDefault("hex", "");
		
		Response res7=sendCommand("signrawtransaction",s(fund_tx)); // подпись траты на комиссию
			ret.code=res7.code; ret.message=res7.message;
		if(res7.data==null) return ret; // Ошибка запроса			
	
		String sign_tx=convert(rawParseObject(res7.data)).getOrDefault("hex", ""); // FIXME флаг "complete" не проверяем (GUI не дает абъюзить)
		
		Response res8=sendCommand("sendrawtransaction",s(sign_tx));
			ret.code=res8.code; ret.message=res8.message;
		if(res8.data==null) return ret; // Ошибка запроса	
		
		ret.data=convert(res8.data); // txid
		
		// Хух!
		
		return ret;
	}
	
	
	private BoolResponse verifyMessage(String address, String signature, String message) {
		BoolResponse ret= new BoolResponse();
		
		Response res=sendCommand("verifymessage", s(address), s(signature), s(message) );
			ret.code=res.code; ret.message=res.message;
		
		if(res.data!=null) ret.data=convert(res.data).equals("true");
			
		return ret;
	}
	
	private StringResponse getMultisig1(String pubkey) {
		StringResponse ret=new StringResponse();
		
		Response res=sendCommand("createmultisig",n(1),"["+s(pubkey)+"]"); // в успешном ответе адрес ассоциированный с address
			ret.code=res.code; ret.message=res.message;
		
		if(res.data!=null) ret.data=convert(rawParseObject(res.data)).getOrDefault("address", "");
			
		return ret;
	}
	
	public StringResponse setAccount(String address, String account) {
		StringResponse ret=new StringResponse();
		
		Response res=sendCommand("setaccount",s(address),s(account)); // в успешном ответе адрес асоциированный с address
			ret.code=res.code; ret.message=res.message;
		
		if(res.data!=null) ret.data=convert(res.data);
			
		return ret;
	}

	public StringResponse importPrivKey(String privkey, String account) {return importPrivKey(privkey, account, true);}
	public StringResponse importPrivKey(String privkey, String account, boolean rescan) {
		StringResponse ret=new StringResponse();
		
		Response res=sendCommand("importprivkey",s(privkey),s(account),b(rescan));
			ret.code=res.code; ret.message=res.message;
		
		if(res.data!=null) ret.data=convert(res.data); // Нода возвращает адрес
			
		return ret;
	}
	public StringResponse importPubKey(String pubkey, String account) {return importPubKey(pubkey, account, true);}
	public StringResponse importPubKey(String pubkey, String account, boolean rescan) {
		StringResponse ret=new StringResponse();
		
		Response res=sendCommand("importpubkey",s(pubkey),s(account),b(rescan));
			ret.code=res.code; ret.message=res.message;
		
		if(res.data!=null) ret.data=convert(res.data); // Нода возвращает адрес
			
		return ret;
	}
	public StringResponse importAddress(String address, String account) {return importAddress(address, account, true);}
	public StringResponse importAddress(String address, String account, boolean rescan) {
		StringResponse ret=new StringResponse();
		
		Response res=sendCommand("importaddress",s(address),s(account),b(rescan));
			ret.code=res.code; ret.message=res.message;
		
		if(res.data!=null) ret.data=convert(res.data); // Нода возвращает адрес
			
		return ret;
	}
	
	public StringResponse getNewAddress(String account) {
		StringResponse ret=new StringResponse();
		
		Response res=sendCommand("getnewaddress",s(account)); // в успешном ответе новый адрес
			ret.code=res.code; ret.message=res.message;
		
		if(res.data!=null) ret.data=convert(res.data);
			
		return ret;
	}
	
	public StringResponse addMultisigAddress(String key, String account) {
		StringResponse ret=new StringResponse();
		
		Response res=sendCommand("addmultisigaddress",n(1),"["+s(key)+"]",s(account)); // в успешном ответе p2sh адрес
			ret.code=res.code; ret.message=res.message;
		
		if(res.data!=null) ret.data=convert(res.data);

		return ret;
	}
	
	
	public MapsResponse getAddressBook() {
		MapsResponse ret=new MapsResponse();
		/*
			scriptPubKey : 76a914af7c8393c41711281c01016e75e140cd4cfa633d88ac
			address : 1GztQxGTKdEFhctBhR38wR8skjqkd4Cqt8
			iscompressed : true
			isscript : true
			ismine : true
			isvalid : true
			iswatchonly : false
			account : PUBLIC
			pubkey : 035f1d832f96ecfc92e7894daab869ea22b066db66e16dd3369081c8953582dc94
	
		    script: multisig
		    hex: 512102a2ade28aca848fc3eb91dcd6a7e9f18dee8169840de633e14d3a9f0d6b71567a51ae
		    addresses: [1FYymQyHWwwuGxNRBQs3Ba56ENX2fbXmDa]
		    sigsrequired: 1

			
			privkey : 
		*/
		
		// FIXME при последовательном вызове могут вклиниваться вызовы от других потоков
		
		Response res1=sendCommand("listaccounts",n(0),n(2)); // Все что есть
			ret.code=res1.code; ret.message=res1.message;
		if(res1.data==null) return ret; // Ошибка запроса 
			
		// интересуют только метки в том числе и ""
		
		Map<String,String> accounts= convert( rawParseObject(res1.data) );
					
		for(String account: accounts.keySet()) {	
			
			Response res2=sendCommand("getaddressesbyaccount",s(account));
				ret.code=res2.code; ret.message=res2.message;
			
			if(res2.data==null) return ret; // Ошибка запроса  
			
			List<String> addresses= convert( rawParseArray(res2.data) );
			
			for(String addr: addresses) {
				Response res3=sendCommand("validateaddress",s(addr));
					ret.code=res3.code; ret.message=res3.message;
				
				if(res3.data==null) return ret; // Ошибка запроса
					
				Map<String,String> rec= convert( rawParseObject(res3.data) );
				
				//rec.forEach((k,v)->{if(k.equals("addresses")) LOGGER.console(k+" : "+v);}); // TODO multisig
				
				// Для упрощения использования через split(",")
				if(rec.containsKey("addresses")) rec.put("addresses", rec.get("addresses").replaceAll("[\"\\[\\]]", "")); 
				
				if(rec.getOrDefault("ismine", "").equals("true") && !rec.getOrDefault("isscript", "").equals("true")) { 
					// Для своих адресов читаем еще приватные ключи
					Response res4=sendCommand("dumpprivkey",s(rec.getOrDefault("address", "")));
						ret.code=res4.code; ret.message=res4.message;
						
					if(res4.data==null) return ret; // Ошибка запроса
					
					rec.put("privkey", convert(res4.data) );
				}
				
				ret.data.add( rec );
			}
		
		}
		
		// Еще нужно найти адреса участвующие в мультиподписи и пометить ссылкой на "multisigs" (p2sh) адрес  для модели
		// (чтобы подсвечивать). Для кеша лучше здесь делать модификацию считанных данных
		
		for(Map<String,String> rec: ret.data) {
			
			if(rec.containsKey("addresses")) { // multisig-адрес. Адреса в строке в квадратных скобках через запятую (массив адресов). Ищем их все
				
				String[] addresses=rec.get("addresses").split(","); // Список адресов участников в данном адресе в rec
				
L1:				for(String addr: addresses) 
					if(!addr.isBlank())
						for(Map<String,String> r: ret.data) {
							if(addr.equals(r.get("address"))) {
								if(!r.containsKey("multisigs")) r.put("multisigs", rec.get("address"));
								else r.put("multisigs",r.get("multisigs")+","+rec.get("address")); // тоже через запятую и хуй с этим
								continue L1;
							}
						}
			}
		}
		
		
		return ret;
	}
	
	public StringResponse dumpPrivKey(String address) {
		StringResponse ret= new StringResponse();
		
		Response res=sendCommand("dumpprivkey",s(address));
			ret.code=res.code; ret.message=res.message;
			
		if(res.data!=null) ret.data= convert(res.data);
		
		return ret;
	}
	
	public MapResponse validateAddress(String address) {
		MapResponse ret=new MapResponse();
		
		Response res=sendCommand("validateaddress",s(address));
			ret.code=res.code; ret.message=res.message;
	
		if(res.data!=null) ret.data=convert( rawParseObject(res.data) );
			
		return ret;	
	}

	public StringResponse sendToAddress(String address, long amount, boolean subtractFee) { // Возвращает id транзакции
		
		StringResponse ret=new StringResponse();
		
		Response res=sendCommand("sendtoaddress",s(address),n(amount),s(""),s(""),b(subtractFee)); // в успешном ответе id транзакции
			ret.code=res.code; ret.message=res.message;
		
		if(res.data!=null) ret.data=convert(res.data);
			
		return ret;
	}
	
	public BoolResponse walletPassphrase(String password) { // Команда присутствует в ядре только когда кошелек зашифрован
		BoolResponse ret=new BoolResponse();
		
		Response res=sendCommand("walletpassphrase",s(password),n(365*24*60*60)); // Успешный ответ без результата - просто нет ошибки и все (.isOk())
			ret.code=res.code; ret.message=res.message;
			
		if(res.data!=null) ret.data=true;
		else ret.data=false;
		
		return ret;
	}
	public BoolResponse walletPassphraseChange(String oldPassword, String newPassword) { // Команда присутствует в ядре только когда кошелек зашифрован
		BoolResponse ret=new BoolResponse();
		
		Response res=sendCommand("walletpassphrasechange",s(oldPassword),s(newPassword)); // Успешный ответ без результата - просто нет ошибки и все ((.isOk())
			ret.code=res.code; ret.message=res.message;
			
			
		if(res.data!=null) {
			// FIXME Корректность команды означает, что даже если изначально кошель было залочен, то его можно разлочить сразу новым паролем
			// (сам он не разлачивается из-за неопределенности с параметром timeout)
			
																	this.walletPassphrase(newPassword); // Версия с инвалидацией кешей
			
			ret.data=true;
		}
		else {
			// FIXME при не верном исходном пароле и соотвественно ошибке -14 "walletpassphrasechange" кошелек автоматически лочится, но при этом
			// поле unlocked_until в getwalletinfo остается больше 0 (не сбрасывается в 0). Чтобы перевести его в адекватное состояние нужно
			// при ошибке -14 дат команду "walletlock"
			
																	if(res.code==RPC_WALLET_PASSPHRASE_INCORRECT) sendCommand("walletlock");
			
			ret.data=false;
		}
		
		return ret;
	}
	public BoolResponse encryptWallet(String password) { // Команда присутствует в ядре только когда кошелек НЕ зашифрован
		BoolResponse ret=new BoolResponse();
		
		Response res=sendCommand("encryptwallet",s(password)); // Успешная команда отвечает сообщением, что демон завершается и нужно потом заново запустить
			ret.code=res.code; ret.message=res.message;
	
		if(res.data!=null) ret.data=true;
		else ret.data=false;
		
		return ret;
	}
	
	public BoolResponse stop() {
		BoolResponse ret=new BoolResponse();
		
		Response res=sendCommand("stop"); // Успешная команда отвечает сообщением, что демон завершается и все
			ret.code=res.code; ret.message=res.message;
	
		if(res.data!=null) ret.data=true;
		else ret.data=false;
		
		return ret;
	}
	
}