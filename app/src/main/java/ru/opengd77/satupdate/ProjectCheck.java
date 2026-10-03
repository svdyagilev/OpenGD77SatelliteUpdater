package ru.opengd77.satupdate;

import java.util.*;

/** Non-destructive audit. An empty zone or duplicate contact can be intentional. */
final class ProjectCheck {
    static final class Report {
        final List<String> errors=new ArrayList<>(),warnings=new ArrayList<>();
        String text(){StringBuilder out=new StringBuilder();for(String x:errors)out.append("Ошибка: ").append(x).append('\n');for(String x:warnings)out.append("Предупреждение: ").append(x).append('\n');return out.length()==0?"Проблем не найдено.":out.toString().trim();}
    }
    static Report inspect(CodeplugProject project,CodeplugSnapshot image){
        Report report=new Report();CodeplugModel model=OpenGd77CodeplugDecoder.decode(image);
        try{CodeplugIntegrity.masks(project);}catch(IllegalArgumentException e){report.errors.add(e.getMessage());}
        Set<String> missing=new LinkedHashSet<>(),existing=new HashSet<>();
        for(CodeplugIntegrity.Edge edge:CodeplugIntegrity.edges(project.original))if(edge.id>CodeplugRecords.limit(edge.kind)||!CodeplugRecords.occupied(project.original,edge.kind,edge.id))existing.add(edge.key());
        for(CodeplugIntegrity.Edge edge:CodeplugIntegrity.edges(image))if(edge.id>CodeplugRecords.limit(edge.kind)||!CodeplugRecords.occupied(image,edge.kind,edge.id))
        {
            String message=edge.owner+": отсутствует "+ProjectDiff.kindName(edge.kind)+" #"+edge.id;
            if(existing.contains(edge.key()))report.warnings.add("Ссылка уже отсутствовала при чтении: "+message);else missing.add(message);
        }
        report.errors.addAll(missing);
        for(CodeplugModel.Zone zone:model.zones)if(zone.channelIndices.isEmpty())report.warnings.add("Пустая зона #"+zone.index+" · "+zone.name);
        Map<Long,List<String>> ids=new LinkedHashMap<>();
        for(CodeplugModel.Contact contact:model.contacts){if(!ids.containsKey(contact.number))ids.put(contact.number,new ArrayList<>());ids.get(contact.number).add("#"+contact.index+" "+contact.name);}
        for(Map.Entry<Long,List<String>> entry:ids.entrySet())if(entry.getValue().size()>1)report.warnings.add("Повторяющийся DMR ID/TG "+entry.getKey()+": "+entry.getValue());
        return report;
    }
}
