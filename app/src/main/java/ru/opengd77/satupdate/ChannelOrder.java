package ru.opengd77.satupdate;

import java.util.*;

/** Moves a channel or selected block without changing physical channel IDs. */
final class ChannelOrder {
    static void move(List<Integer> order,Set<Integer> selected,int source,int target){
        if(source<0||target<0||source>=order.size()||target>=order.size()||source==target)return;
        Set<Integer> moving=selected.contains(order.get(source))?selected:Collections.singleton(order.get(source));
        int anchor=order.get(target);if(moving.contains(anchor))return;
        List<Integer> block=new ArrayList<>();for(int id:order)if(moving.contains(id))block.add(id);
        boolean forward=target>source;order.removeAll(block);
        int position=order.indexOf(anchor)+(forward?1:0);order.addAll(position,block);
    }
}
