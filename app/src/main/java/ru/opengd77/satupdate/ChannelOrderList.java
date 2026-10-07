package ru.opengd77.satupdate;

import android.content.Context;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Zone-local selection and long-press dragging; edits remain in the dialog draft. */
final class ChannelOrderList extends ListView {
    final List<Integer> order;
    final Set<Integer> selected=new LinkedHashSet<>();
    private final List<String> labels=new ArrayList<>();
    private final Map<Integer,String> names=new HashMap<>();
    private final ArrayAdapter<String> rows;
    private List<Integer> dragBefore;
    private boolean dragging;
    private int draggedId;
    private float touchY;
    private final Runnable changed;
    private final Runnable scroll=new Runnable(){public void run(){
        if(!dragging)return;
        int edge=(int)(48*getResources().getDisplayMetrics().density);
        int distance=touchY<edge?-edge:touchY>getHeight()-edge?edge:0;
        if(distance!=0){smoothScrollBy(distance,80);moveAt(touchY);}
        postDelayed(this,70);
    }};
    ChannelOrderList(Context context,List<Integer> order,CodeplugModel model,Runnable changed){
        super(context);this.order=order;this.changed=changed;
        for(CodeplugModel.Channel channel:model.channels)names.put(channel.index,channel.name);
        setChoiceMode(CHOICE_MODE_MULTIPLE);
        rows=new ArrayAdapter<String>(context,android.R.layout.simple_list_item_multiple_choice,labels){
            @Override public View getView(int position,View view,android.view.ViewGroup parent){
                View row=super.getView(position,view,parent);
                row.setAlpha(dragging&&order.get(position)==draggedId?0.45f:1f);return row;
            }
        };setAdapter(rows);refresh();
        setOnItemClickListener((parent,view,position,id)->{
            int channel=order.get(position);if(!selected.add(channel))selected.remove(channel);refresh();
        });
        setOnItemLongClickListener((parent,view,position,id)->{
            draggedId=order.get(position);dragBefore=new ArrayList<>(order);dragging=true;
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            getParent().requestDisallowInterceptTouchEvent(true);refresh();removeCallbacks(scroll);post(scroll);return true;
        });
        setOnTouchListener((view,event)->{
            touchY=event.getY();if(!dragging)return false;
            switch(event.getActionMasked()){
                case MotionEvent.ACTION_MOVE:moveAt(touchY);return true;
                case MotionEvent.ACTION_CANCEL:order.clear();order.addAll(dragBefore);finishDrag();return true;
                case MotionEvent.ACTION_UP:moveAt(touchY);finishDrag();return true;
                default:return true;
            }
        });
    }
    private void moveAt(float y){
        int target=pointToPosition(getWidth()/2,(int)Math.max(0,Math.min(getHeight()-1,y)));
        if(target==INVALID_POSITION)target=y<getHeight()/2?getFirstVisiblePosition():getLastVisiblePosition();
        if(target<0||target>=order.size())return;
        int source=order.indexOf(draggedId);
        List<Integer> previous=new ArrayList<>(order);
        ChannelOrder.move(order,selected,source,target);if(!previous.equals(order))refresh();
    }
    private void finishDrag(){
        dragging=false;removeCallbacks(scroll);getParent().requestDisallowInterceptTouchEvent(false);
        long now=android.os.SystemClock.uptimeMillis();MotionEvent cancel=MotionEvent.obtain(now,now,MotionEvent.ACTION_CANCEL,0,0,0);super.onTouchEvent(cancel);cancel.recycle();refresh();
    }
    void refresh(){
        labels.clear();for(int id:order)labels.add("#"+id+" · "+(names.containsKey(id)?names.get(id):"Канал"));
        rows.notifyDataSetChanged();clearChoices();for(int i=0;i<order.size();i++)setItemChecked(i,selected.contains(order.get(i)));
        changed.run();
    }
    @Override protected void onDetachedFromWindow(){removeCallbacks(scroll);super.onDetachedFromWindow();}
}
