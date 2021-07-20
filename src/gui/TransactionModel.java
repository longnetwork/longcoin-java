package gui;

import util.Css;

import static util.Binary.*;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;


public class TransactionModel {
	
	static final String TXID_WEB_COLOR=Css.getColor("-fx-txid-color").toString().replaceFirst("0x|0X", "#");
	static final int MIN_MEDIA_SIZE=186; // Чтобы сигнатуру определят для реальных видимых/слышимых данных (png8x8 - 186 bytes, png16x16 - 268 bytes )
	static final Pattern mediaPattern= Pattern.compile("(?is)"+
            "(?<hex>\\b(?>[0-9A-Fa-f]{2}\\s{0,2}){" + MIN_MEDIA_SIZE + ",}+)"+    // Захватит в конце 1-2 пробельных
            "|"+
            "(?<data>data:[^;,<>]{0,24};base64,[a-zA-Z0-9+/=\\s]{4,})"+		 // Могут быть разрывы данных пробельными символами
            ""); 
	
	
	///////////////////////////////////////////////////// Это свойства для View ///////////////////////////////////////////////////////////
	// Записывать в свойства стоит только если есть изменения, иначе это постоянная перерисовка (для биндингов)
	// авто-биндинг свойств работаете через рефлексию (ебучию). При создании указывать строку с именем свойства, 
	// и создавать метод с суффиксом ...Property (также при наличии рефлексии вроде можно в стилях сослаться на имена свойств)
	
	private DoubleProperty confirmations;
		public DoubleProperty confirmationsProperty() { return confirmations; } 		  // Суффикс ...Property
	 	public void setConfirmations(Double val) { confirmations.set(val); }
	    public Double getConfirmations() { return confirmations.get(); }
		    public void setConfirmations(final Map<String,String> tx) { // tx - Распарсеный JSON-объект
		    	String category=tx.getOrDefault("category","");
		    	double confirmations=Double.parseDouble(tx.getOrDefault("confirmations","0"));
		    	switch (category){
		    		case "send":
		    		case "receive":
		    			this.setConfirmations(confirmations/6);
		    			break;
		    		case "generate":
		    		case "immature":
		    		case "orphan":
		    			this.setConfirmations(confirmations/30); 
		    			break;
		    		case "move":
		    			this.setConfirmations(1.0);
		    			break;
		    		default:
		    			this.setConfirmations(0.0);
		    			break;
		    	}
		    }
		    
    private StringProperty category;
	    public StringProperty categoryProperty() { return category; }
	    public void setCategory(String str) { category.set(str); }
	    public String getCategory() { return category.get(); }
		    public void setCategory(final Map<String,String> tx) {
		    	this.setCategory(tx.getOrDefault("category",""));
		    }
		    
	private ObjectProperty<LocalDateTime> time;
		public ObjectProperty<LocalDateTime> timeProperty() { return time; }
	    public void setTime(LocalDateTime val) { time.set(val); }
	    public LocalDateTime getTime() { return time.get(); }
			public void setTime(final Map<String,String> tx) {
				this.setTime(LocalDateTime.ofEpochSecond(Long.parseLong(tx.getOrDefault("time","0")),0,OffsetDateTime.now().getOffset()));
			}
	
	// Если финансовая транза то берем account/label, если данные - то from/to
	private StringProperty from;
		public StringProperty fromProperty() { return from; }
	    public void setFrom(String str) { from.set(str); }
	    public String getFrom() { return from.get(); }	
	    	public void setFrom(final Map<String,String> tx) {
	    		
	    		String from="";
	    		
	    		if( tx.containsKey("hexdata") ) { // данные
	    			// Вне зависимости с каких адресов прошло списание комиссии, поля from/to для данных - отдельная песня
	    			// - запаковываются в сами данные
	    			from=tx.getOrDefault("from","");
	    			if(from.isBlank()) { // Пробельные имена аккаунтов не видимы. isBlank для них такое-же как и для ""
	    				from=tx.getOrDefault("fromaddress","");
	    				if(from.isBlank()) {
	    					from=tx.getOrDefault("frompubkey","");
	    				}
	    			}
	    		}
	    		else { // финансы
	    			String category=tx.getOrDefault("category","");
	    			switch (category){
			    		case "send":
			    			from=tx.getOrDefault("account",""); 
			    			// account - это всегда свои адреса при любой category
			    			// account - ссылается всегда на свои адреса вне зависимости от направления транзакции 
			    			// (есть отсылка с аккаунта на адрес и не важно с каких адресов были взяты входы)
			    			break;
			    		case "move":
			    			double amount=Double.parseDouble(tx.getOrDefault("amount","0"));
			    			if(amount<0) from=tx.getOrDefault("account","");
			    			if(amount>0) from=tx.getOrDefault("otheraccount","");
			    			break;
			    		default:
			    			from=""; 
			    			// Все остальные категории это входящие транзакции и from в общем случае собирается из 
			    			// множества транзакций для списания (множества адресов) - то есть from не определен
			    			break;
	    			}
	    		}
	    		this.setFrom(from);
	    	}		
    private StringProperty to;
		public StringProperty toProperty() { return to; }
	    public void setTo(String str) { to.set(str); }
	    public String getTo() { return to.get(); }	
	    	public void setTo(final Map<String,String> tx) {
	    		
	    		String to="";
	    		
	    		if( tx.containsKey("hexdata") ) { // данные 
	    			// Вне зависимости с каких адресов прошло списание комиссии, поля from/to для данных - отдельная песня
	    			// - запаковываются в сами данные
	    			to=tx.getOrDefault("to","");
	    			if(to.isBlank()) { // Пробельные имена аккаунтов не видимы. Берем первое что видимо
	    				to=tx.getOrDefault("toaddress","");
	    				if(to.isBlank()) {
	    					to=tx.getOrDefault("topubkey","");
	    				}
	    			}
	    		}
	    		else { // финансы
	    			String category=tx.getOrDefault("category","");
	    			switch (category){
			    		case "send":
			    			to=tx.getOrDefault("label","");
			    			if(to.isBlank()) to=tx.getOrDefault("address","");
			    			// label - для категории send ссылается на чужой адрес (туда куда переводятся деньги), для receive - всегда на свой
			    			// для send, address - куда переводятся деньги, для receive - куда принялись (отправляется то с множества входов и на один адрес)
			    			break;
			    		case "move":
			    			double amount=Double.parseDouble(tx.getOrDefault("amount","0"));
			    			if(amount<0) to=tx.getOrDefault("otheraccount","");
			    			if(amount>0) to=tx.getOrDefault("account","");
			    			break;
			    		default:
			    			// Все остальные категории это входящие транзакции (account, label и address ссылаются на свои адреса)
			    			to=tx.getOrDefault("account","");
			    			if(to.isBlank()) {
			    				to=tx.getOrDefault("label","");
			    				if (to.isBlank()) {
			    					to=tx.getOrDefault("address","");
			    				}
			    			}
			    			break;
			    			
	    			}
	    		}
	    		this.setTo(to);
	    	}		    	   
	
    private DoubleProperty amount;
    	public DoubleProperty amountProperty() { return amount; } 
	    public void setAmount(Double val) { amount.set(val); }
	    public Double getAmount() { return amount.get(); }
	    	public void setAmount(final Map<String,String> tx) {
		    	double amount=Double.parseDouble(tx.getOrDefault("amount","0"));
		    	double fee=Double.parseDouble(tx.getOrDefault("fee","0"));
	    		// amount и fee одного отрицательного знака для категории send, и fee нету (0) во всех остальных типах транзакций
	    		this.setAmount(amount+fee);
	    	}
	    	
	private StringProperty content;
		public StringProperty contentProperty() { return content; }
		public void setContent(String str) { content.set(str); }
		public String getContent() { return content.get(); }	
		public void setContent(Map<String,String> tx) {
			if( !tx.containsKey("hexdata") ) { // Финансовая транза - тупо ее id выводим
				this.setContent("<span style='color: " + TXID_WEB_COLOR + "; font-size: medium;'><b>txid:</b> <code>" + tx.getOrDefault("txid","") + "</code></span>"); 
			}
			else {
		    	// Это в виде hexstr закодирована последовательность байт - они и должны быть взяты в качестве сырых данных и преобразованы снова в String
		    	// Для обратной совместимости протестим некоторые типы данных, которые не html, а сразу запихнуты как сырые байты,
		    	// причем могли запихнуться как hexstring от hexstring для строкового отображения в стандартном клиенте
		    	// Желательно не распознанные данные не рендрить, так как VebWiew на них тормозит, пытаясь безуспешно распознать
				final byte[] raw=hexStringToByteArray( tx.getOrDefault("hexdata","") ); // not null
				final StringBuilder body=new StringBuilder(); 
				
				//body.append("<html><body>");
				
			    if (isPNG(raw)) 						  body.append("<img src='data:image/png;base64,")
			    		                    			      .append(Base64.getEncoder().encodeToString( raw ))
			    		                    				  .append("'>");
	    		else if (isGIF(raw))		 			  body.append("<img src='data:image/gif;base64,")
                											  .append(Base64.getEncoder().encodeToString( raw ))
                										      .append("'>");
	    		else if(isJPG(raw)) 					  body.append("<img src='data:image/jpeg;base64,")
															  .append(Base64.getEncoder().encodeToString( raw ))
															  .append("'>");
	    		else if(isMP3(raw)||isMPEG(raw))          body.append("<audio controls src='data:audio/mpeg;base64,")
				  											  .append(Base64.getEncoder().encodeToString( raw ))
				  											  .append("'></audio>");
	    		else if(isMP4(raw))          			  body.append("<video autoplay loop muted src='data:video/mp4;base64,")
				  											  .append(Base64.getEncoder().encodeToString( raw ))
				  											  .append("'></video>");
	    		else {
	    			// Когда небыло html-рендеринга слали медиа-данные как строки из hex-символов (иногда разбитые виндузятными пробельными символами)
	    			// Нужно по границам таких строк вставлять эквивалентный html-код
	    			
	    			final Matcher matcher=mediaPattern.matcher( utf8BytesToString(raw) );
	    			while(matcher.find()) {
	    				String group;
	    				group=matcher.group("hex"); // Словит только длинные hex-строки
	    				if(group!=null) { group=group.replaceAll("\\s",""); // разрывы нах
	    				
			    			if(isPNG(group)) 						matcher.appendReplacement(body, "<img src='data:image/png;base64,"
			    			                                                   						   +Base64.getEncoder().encodeToString( hexStringToByteArray(group) )
			    			                                                   						   +"'> ");
							else if(isGIF(group)) 					matcher.appendReplacement(body, "<img src='data:image/gif;base64,"
																									   +Base64.getEncoder().encodeToString( hexStringToByteArray(group) )
																									   +"'> ");
							else if(isJPG(group)) 					matcher.appendReplacement(body, "<img src='data:image/jpeg;base64,"
																					                   +Base64.getEncoder().encodeToString( hexStringToByteArray(group) )
																					                   +"'> "); 
							else if(isMP3(group)||isMPEG(group)) 	matcher.appendReplacement(body, "<audio controls src='data:audio/mpeg;base64,"
																									   +Base64.getEncoder().encodeToString( hexStringToByteArray(group) )
																									   +"'></audio> ");    		  
							else if(isMP4(group)) 					matcher.appendReplacement(body, "<video autoplay loop muted src='data:video/mp4;base64,"
									  																   +Base64.getEncoder().encodeToString( hexStringToByteArray(group) )
									  																   +"'></video> ");	    				
							else 									matcher.appendReplacement(body, "<a style='font-size: small' target='_blank' href='data:application/octet-stream;base64,"
	    			    			   																   +Base64.getEncoder().encodeToString( hexStringToByteArray(group) )
									   																   +"'>signature</a> "); // Не распознанная херь
	    			    }
	    				group=matcher.group("data");
	    				if(group!=null) { group=group.replaceAll("\\s",""); // Нужно убирать разрывы в данных
	    					matcher.appendReplacement(body,group);
	    				}
	    			}
	    			matcher.appendTail(body);
	    		}
	    	
		    	//body.append("</body></html>");
		    	
		    	this.setContent(body.toString());
			}
		}
	    	
	
	public TransactionModel() {
		confirmations= new SimpleDoubleProperty(this,"confirmations"); // Рефлексия на имя поля свойства в экземпляре объекта
		category=new SimpleStringProperty(this,"category");			   // Эти поля могут менятся в транзакциях
		
		time = new SimpleObjectProperty<LocalDateTime>();
		from= new SimpleStringProperty(this,"from"); // Экспорт имен свойств для доступа
		to= new SimpleStringProperty(this,"to");	 // через PropertyReference<>()
		amount = new SimpleDoubleProperty();
			
		content = new SimpleStringProperty();
	}
	private Map<String,String> tx=null;
	public TransactionModel(final Map<String,String> tx) { this(); this.tx=tx; 
		this.setConfirmations(tx);
		this.setCategory(tx);
		this.setTime(tx);
		this.setFrom(tx);
		this.setTo(tx);
		this.setAmount(tx);
	
		this.setContent(tx); 
	}
	public boolean isDataTx() { return this.tx!=null && this.tx.containsKey("hexdata"); }
	public boolean isEncryption() { return this.tx!=null && ! this.tx.getOrDefault("encryption","no").equals("no"); }
	public boolean isDecryption() { return this.tx!=null && ! this.tx.getOrDefault("decryption","no").equals("no"); }
	public boolean isTrusted() { return this.tx!=null && ! this.tx.getOrDefault("trusted","false").equals("false"); }
	public String getTxId() { return this.tx!=null ? this.tx.getOrDefault("txid","") : ""; }
	
	public String getAddress() { return this.tx!=null ? this.tx.getOrDefault("address","") : ""; }
	
	public String getFromPubKey() { return this.tx!=null ? this.tx.getOrDefault("frompubkey","") : ""; }
	public String getFromAddress() { return this.tx!=null ? this.tx.getOrDefault("fromaddress","") : ""; }
	public String getToPubKey() { return this.tx!=null ? this.tx.getOrDefault("topubkey","") : ""; }
	public String getToAddress() { return this.tx!=null ? this.tx.getOrDefault("toaddress","") : ""; }
	
	public boolean isWatchonly() { return this.tx!=null && ! this.tx.getOrDefault("involvesWatchonly","false").equals("false"); }
	
	@Override public boolean equals(Object obj) { // Переопределяется всегда в паре с hashCode
		if (obj == this) return true;
		if (obj == null || obj.getClass() != this.getClass()) return false;
		TransactionModel model = (TransactionModel) obj;
		
		// txid может быть одинаково в нескольких транзакциях при listtransactions и его не достаточно для проверки на равенство
		// и напротив - поле confirmations всегда меняется и не должно сравниваться (вероятно и category тоже)
		// address может быть одинаковым для send и receive транзакций - не информативен для сличения, если не сличаются category
		// amount - иформативен в знаке, так как позволяет различить части одной транзы самому себе, но при сообщении самому себе
		// amount равено 0 и для различение частей нужно смотреть в fee (оно есть только в категории send)
		// null != "" , поэтому сравнение пустого свойства с tx гарантировано вернет false
		// Строки нельзя стравнивать через != ==, так как это объекты и контент не сравнивается этими операторами
		
		if(this.tx==null && model.tx==null) return true; if(this.tx==null) return false; if(model.tx==null) return false;
			
		if( ! this.tx.getOrDefault("txid","").equals(model.tx.getOrDefault("txid","")) ) return false; 
		if( ! this.tx.getOrDefault("vout","").equals(model.tx.getOrDefault("vout","")) ) return false;
		
		if( ! this.tx.getOrDefault("amount","").equals(model.tx.getOrDefault("amount","")) ) return false;
		if( ! this.tx.getOrDefault("fee","").equals(model.tx.getOrDefault("fee","")) ) return false;
		// Части одной транзы самому себе различимы (дублирование отображения send/receive)
		
		// для категории move "".equals(""), поэтому доп проверка для транзакций типа "move" по времени (в ней нет txid и vout и fee)
		// FIXME: Повторы move не должны быть чаще минимального дискрета time
		if( ! this.tx.getOrDefault("time","").equals(model.tx.getOrDefault("time","")) ) return false;
		
		return true; // Это одна и та-же часть транзакции или транзакция по listtransactions
	}
	@Override public int hashCode() { 
		// для equals объектов hashCode обязан давать одно и тоже значение, и максимально вероятное разное значения для не equals объектов
		// теоретически hashCode должно быть много быстрее equals, и только когда hashCode равны прибегают к equals для уточнения
		// то есть hashCode - быстрая проверка на НЕ равенство объектов, если hashCode НЕ равны, то объекты точно НЕ равны, а 
		// если hashCode равны, то нужно уточнить через equals
		
	    final int prime = 31; int result = 0;
	    if(this.tx==null) return result;
	    
	    result=result*prime + this.tx.getOrDefault("txid","").hashCode(); // "".hashCode==0 , " ".hashCode==32 
	    result=result*prime + this.tx.getOrDefault("vout","").hashCode();
	    
	    result=result*prime + this.tx.getOrDefault("amount","").hashCode();
	    result=result*prime + this.tx.getOrDefault("fee","").hashCode();
	    // Части одной транзы самому себе различимы (дублирование отображения send/receive)
	    
	    result=result*prime + this.tx.getOrDefault("time","").hashCode();
	    
	    return result;
    }
	
	static public boolean filter(Map<String,String> tx, final String flt) { // Способ фильтрации транзакций определяет модель
		if(flt==null || flt.isEmpty()) return true; // Если фильтр не установлен, то проходят все транзакции
		
		final String[] flist= flt.split("\n"); 		// Объединение фильтров через разделитель строк
		
		for(String f: flist) { // После split всегда как минимум есть 1 элемент даже для пустой строки на входе
			
			if(f.isEmpty()) continue;
			
			if( tx.getOrDefault("address","").startsWith(f) || tx.getOrDefault("address","").endsWith(f) ) return true;
			if( tx.getOrDefault("fromaddress","").startsWith(f) || tx.getOrDefault("fromaddress","").endsWith(f) ) return true;
			if( tx.getOrDefault("toaddress","").startsWith(f) || tx.getOrDefault("toaddress","").endsWith(f) ) return true;
			
			if( tx.getOrDefault("frompubkey","").startsWith(f) || tx.getOrDefault("frompubkey","").endsWith(f) ) return true;
			if( tx.getOrDefault("topubkey","").startsWith(f) || tx.getOrDefault("topubkey","").endsWith(f) ) return true;
			
			if( tx.getOrDefault("txid","").startsWith(f) || tx.getOrDefault("txid","").endsWith(f)) return true;
			
			f=f.toLowerCase();
			
			if(tx.getOrDefault("account","").toLowerCase().indexOf(f)>=0) return true;
			if(tx.getOrDefault("label","").toLowerCase().indexOf(f)>=0) return true;
			if(tx.getOrDefault("otheraccount","").toLowerCase().indexOf(f)>=0) return true;
			if(tx.getOrDefault("from","").toLowerCase().indexOf(f)>=0) return true;
			if(tx.getOrDefault("to","").toLowerCase().indexOf(f)>=0) return true;
			
			// FIXME: Условия левой (не расшифрованой, в крякозябрах) транзакции?
		}
		
		return false; // Ничего под установленный фильтр не подошло
	}
	
	@Override public String toString() {
		
		final StringBuilder sb=new StringBuilder();
		
		sb.append("trusted: ").append(isTrusted()).append("\n");
		
		String category=getCategory();
		
		sb.append("category: ").append(category).append("\n");
		
		Double confirmations=getConfirmations();
		
		switch (category) {
			case "send":
			case "receive":
				sb.append("confirmations: ").append((int)Math.round((confirmations*6))).append("\n");
				break;
			case "generate":
			case "immature":
			case "orphan":
				sb.append("confirmations: ").append((int)Math.round((confirmations*30))).append("\n");
				break;
			default:
				sb.append("confirmations: ").append("").append("\n");
				break;
		}
		
		sb.append("txid: ").append(getTxId()).append("\n");
		
		sb.append("payaddress: ").append(getAddress()).append("\n");
		sb.append("involvesWatchonly: ").append((tx!=null)?tx.getOrDefault("involvesWatchonly", ""):"").append("\n");
		sb.append("amount: ").append((tx!=null)?tx.getOrDefault("amount", ""):"").append("\n");
		sb.append("fee: ").append((tx!=null)?tx.getOrDefault("fee", ""):"").append("\n");
		sb.append("comment: ").append((tx!=null)?tx.getOrDefault("comment", ""):"").append("\n");
		
		sb.append("abandoned: ").append((tx!=null)?tx.getOrDefault("abandoned", ""):"").append("\n");
		sb.append("walletconflicts: ").append((tx!=null)?tx.getOrDefault("walletconflicts", ""):"").append("\n");
		
		sb.append("time: ").append((tx!=null)?tx.getOrDefault("time", ""):"").append("\n");
		sb.append("timereceived: ").append((tx!=null)?tx.getOrDefault("timereceived", ""):"").append("\n");
		sb.append("blocktime: ").append((tx!=null)?tx.getOrDefault("blocktime", ""):"").append("\n");
		sb.append("blockhash: ").append((tx!=null)?tx.getOrDefault("blockhash", ""):"").append("\n");
		
		sb.append("from: ").append(getFrom()).append("\n");
		sb.append("fromaddress: ").append(getFromAddress()).append("\n");
		sb.append("frompubkey: ").append(getFromPubKey()).append("\n");
		
		sb.append("to: ").append(getTo()).append("\n");
		sb.append("toaddress: ").append(getToAddress()).append("\n");
		sb.append("topubkey: ").append(getToPubKey()).append("\n");
		
		sb.append("decryption: ").append(isDecryption()).append("\n");
		sb.append("encryption: ").append(isEncryption()).append("\n");
		
		sb.append("content:\n");
		if(tx.containsKey("hexdata")) sb.append(utf8BytesToString(hexStringToByteArray(tx.getOrDefault("hexdata",""))));
		
	/*	
		if(tx!=null) {
			for (Map.Entry<String, String> entry : tx.entrySet()) {
				String key=entry.getKey(), value=entry.getValue(); value= (value!=null) ? value: "";
				if(!"hexdata".equals(key)) {
					sb.append(key+": "+value+"\n");
				}
			}
			sb.append("content:\n");
			
			if(tx.containsKey("hexdata")) sb.append(utf8BytesToString(hexStringToByteArray(tx.getOrDefault("hexdata",""))));
		}
	*/
		
		return sb.toString();
	}
}
