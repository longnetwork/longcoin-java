package gui;

import static javafx.scene.control.ProgressIndicator.INDETERMINATE_PROGRESS;

import java.util.Timer;
import java.util.TimerTask;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.geometry.Bounds;

import javafx.scene.CacheHint;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import util.Css;
import util.LOGGER;


public class JobInfo extends Group {
		
	static final long DEFAULT_ALERT_TIME=6000;
	static final long DEFAULT_POPUP_TIME=3000;
	
	
	ProgressIndicator progress;
	Label info;
	
	private volatile Parent parent=null;
	
	public JobInfo() {this(false);}
	public JobInfo(boolean initialVisibility) { super();
	
		this.getStyleClass().add("job-info"); //.job-info {}
		this.setManaged(false);
		this.setFocusTraversable(false);
		this.setMouseTransparent(true);
		
		this.needsLayoutProperty().addListener((obs, oldBoolean, newBoolean)->{ // parent сами отлавливаем (для то во внешнем коде подцепят через getChildren().add)
			if(this.getParent()!=null && this.getParent()!=parent) { // Новый parent		
				
				parent=this.getParent();			
				
				info.prefWidthProperty().unbind();
				
				info.prefWidthProperty().bind( Bindings.createDoubleBinding(() -> {			
					Bounds parentBounds=parent.getLayoutBounds();
					return parentBounds.getWidth();
				}, parent.layoutBoundsProperty()) );
						
				
				this.translateYProperty().unbind();
				this.translateYProperty().bind( Bindings.createDoubleBinding(() -> {			
					Bounds parentBounds=parent.getLayoutBounds();
					return parentBounds.getHeight()/2;
				}, parent.layoutBoundsProperty()) );
		
				
				double order=Double.MAX_VALUE;
					for(Node node: parent.getChildrenUnmodifiable()) if(node.getViewOrder()<order) order=node.getViewOrder();
				order=order-1d;
				
				this.setViewOrder(order); // Прорисовывется в последнюю очередь (поверх всех)	
				
			}
			
		});
		
		
		progress=new ProgressIndicator(INDETERMINATE_PROGRESS); // .job-info .progress-indicator {}
			progress.setCacheHint(CacheHint.QUALITY);
			progress.setFocusTraversable(false);
			progress.setMouseTransparent(true);
				
			
		info=new Label();	// .job-info .label {}
			info.setFocusTraversable(false);
			info.setMouseTransparent(true);
			
			
		progress.translateXProperty().bind(info.widthProperty().divide(2d).subtract(progress.widthProperty().divide(2d))); // Центрирование
		info.translateYProperty().bind(progress.heightProperty().add(2d)); // Смещение ниже индикатора прогресса
		
			
		this.getChildren().addAll(progress,info);
		
		this.setVisible(initialVisibility);
		
		
		this.visibleProperty().addListener((obs,oldBoolean,newBoolean)->{
			if(!newBoolean && parent!=null) parent.requestLayout(); // FIXME JobInfo вообще нужно как-то исключить из системы обработки фокуса 
		});
	}
	
	private Timer timer=new Timer(true);
		private TimerTask visibleEndTask=null;
				
	//////////////////////////////////////// Интерфейс ////////////////////////////////////////////////////
	
	// setVisible() - наследуется
	
	public void alert(String info) { if(visibleEndTask!=null) visibleEndTask.cancel();
		if(info==null) { 
			this.setVisible(false);
		}
		else {
			
			LOGGER.warning(info); LOGGER.console(info); // TODO debug
			
			this.progress.setVisible(false);
			this.info.setText(info); Css.pseudoClassStateSwitch(this.info, Css.ALERT_PCS);
			
			this.setVisible(true);
			
			visibleEndTask= new TimerTask() { @Override public void run() { // Здесь this ссылается на TimerTask	
				Platform.runLater(()->JobInfo.this.setVisible(false));
				
				timer.purge(); // Для подчистки прошлых завершенных задач (чтоб не копились)
			}};
			
			timer.schedule(visibleEndTask, DEFAULT_ALERT_TIME);
		}
	}
	
	public void progress(String info) { if(visibleEndTask!=null) visibleEndTask.cancel();
		if(info==null) {
			this.setVisible(false);
		}
		else {
			
			this.progress.setVisible(true);
			this.info.setText(info); Css.pseudoClassStateSwitch(this.info, Css.NONE_PCS);
			
			this.setVisible(true);
		}
	}
	public boolean inProgress() {return this.isVisible()&&this.progress.isVisible();}
	public boolean inAlert() {return this.isVisible()&&!this.progress.isVisible();}
	
	public void popup(String info) { if(visibleEndTask!=null) visibleEndTask.cancel();
	if(info==null) { 
		this.setVisible(false);
	}
	else {
		
		this.progress.setVisible(true);
		this.info.setText(info); Css.pseudoClassStateSwitch(this.info, Css.NONE_PCS);
		
		this.setVisible(true);
		
		visibleEndTask= new TimerTask() { @Override public void run() { // Здесь this ссылается на TimerTask	
			Platform.runLater(()->JobInfo.this.setVisible(false));
			
			timer.purge(); // Для подчистки прошлых завершенных задач (чтоб не копились)
		}};
		
		timer.schedule(visibleEndTask, DEFAULT_POPUP_TIME);
	}
}
	
}
