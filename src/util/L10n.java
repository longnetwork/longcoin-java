package util;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

public final class L10n {

	static final ResourceBundle l10nCode=ResourceBundle.getBundle("res.l10n",new Locale("")); 
	static final ResourceBundle l10nTarget=ResourceBundle.getBundle("res.l10n",Locale.getDefault());
	
	static final Map<String,String> cache=new HashMap<>(); // Сопоставление по названиям ключей
	static {
		for(final String key: l10nCode.keySet()) {
			if(l10nTarget.containsKey(key)) {
				cache.put(l10nCode.getString(key).trim(),l10nTarget.getString(key).trim());
			}
		}
	}
	
	public static String t(String s) { // Или сопоставит строки или извлечет по ключу
		
		final String str=cache.getOrDefault(s.trim(), ""); if(!str.isEmpty()) return str;
		
		if(l10nTarget.containsKey(s)) return l10nTarget.getString(s);
		
		return s; // Или вернет туже 
	}
	
}
