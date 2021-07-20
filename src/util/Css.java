package util;

import java.io.IOException;
import java.util.Set;

import javafx.css.CssParser;
import javafx.css.Declaration;
import javafx.css.ParsedValue;
import javafx.css.PseudoClass;
import javafx.css.Rule;
import javafx.css.Stylesheet;
import javafx.css.converter.ColorConverter;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;

public final class Css {
	
	public static final PseudoClass NONE_PCS = PseudoClass.getPseudoClass("none"); // Действует как удаление псевдокласса
	
	
	//---------------------------------------------------------------------------------------------------
	public static final PseudoClass ALERT_PCS = PseudoClass.getPseudoClass("alert");
	public static final PseudoClass INFO_PCS = PseudoClass.getPseudoClass("info");
	
	public static final PseudoClass ENCRYPT_PCS = PseudoClass.getPseudoClass("encrypt");
	public static final PseudoClass NOENCRYPT_PCS = PseudoClass.getPseudoClass("noencrypt");
	public static final PseudoClass NOTRUSTED_PCS = PseudoClass.getPseudoClass("nodecrypt");
	public static final PseudoClass MULTISIG_PCS = PseudoClass.getPseudoClass("multisig");
	
	public static final PseudoClass POSITIVE_PCS = PseudoClass.getPseudoClass("positive");
	public static final PseudoClass NEGATIVE_PCS = PseudoClass.getPseudoClass("negative");
	
	public static final PseudoClass EMPTY_PCS = PseudoClass.getPseudoClass("empty");
	
	private static final Set<PseudoClass> customPseudoClasses=Set.of(
			NONE_PCS,
			
			ALERT_PCS, INFO_PCS, 
			ENCRYPT_PCS, NOENCRYPT_PCS, NOTRUSTED_PCS, MULTISIG_PCS,
			POSITIVE_PCS, NEGATIVE_PCS,
			
			EMPTY_PCS
	);
	//----------------------------------------------------------------------------------------------------
	
	static final CssParser parser=new CssParser();
	static Stylesheet css=null;
	static Rule rootCssRule=null;
	
	public static Color getColor(final String cssName) {
		try {
			if(css==null) css = parser.parse(ClassLoader.getSystemResource("res/application.css")); 
			if(rootCssRule==null) rootCssRule = css.getRules().get(0); // .root {} 
			
			for(final Declaration d: rootCssRule.getDeclarations()) {
				if(d.getProperty().equalsIgnoreCase(cssName)) {
					@SuppressWarnings("unchecked")
					ParsedValue<String,Color> value=d.getParsedValue();
					return ColorConverter.getInstance().convert(value, null);
				}
			}
		} catch (IOException e) {e.printStackTrace();}
		
		return Color.GAINSBORO; // modena default background
	}
	
	public static void pseudoClassStateSwitch(Node node, PseudoClass newcls) {
		node.getPseudoClassStates().forEach((PseudoClass cls)->{
			if( /*!newcls.getPseudoClassName().equals(cls.getPseudoClassName())*/ cls!=newcls  && customPseudoClasses.contains(cls)) 
				node.pseudoClassStateChanged(cls,false); // Удалили псевдо-классы, установленные до этого
		});
		
		if(newcls!=NONE_PCS) node.pseudoClassStateChanged(newcls,true);
	}
	
	public static Bounds getTextBounds(String stencil,double fontSize) { // по шаблону stencil и заданного размера шрифта
		final Text textStencil=new Text(stencil);
		textStencil.setFont(new Font(fontSize));
		return textStencil.getLayoutBounds();
	}
	
}
