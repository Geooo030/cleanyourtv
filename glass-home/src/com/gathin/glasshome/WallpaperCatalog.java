package com.gathin.glasshome;
import android.content.Context;
import java.io.File;
import java.util.*;

final class WallpaperCatalog {
    static final class Item {
        final String id,name,detail,tag;final boolean dynamic;
        Item(String id,String name,String detail,String tag,boolean dynamic){this.id=id;this.name=name;this.detail=detail;this.tag=tag;this.dynamic=dynamic;}
        File file(Context context){return new File(context.getExternalFilesDir(null),id.equals("hawaii")?"aerial.mov":id+".mov");}
        boolean available(Context context){return !dynamic||file(context).isFile();}
    }
    static final Item[] ITEMS={
        new Item("hawaii","夏威夷海岸","海浪、瀑布与青绿山脊，\n沿着夏威夷海岸缓缓舒展。","HAWAIʻI / AERIAL",true),
        new Item("san-francisco","旧金山海湾","越过海湾与城市的轮廓，\n把熟悉的 Apple TV 航拍带到客厅。","SAN FRANCISCO / AERIAL",true),
        new Item("earth-aurora","地球 · 北美极光","从太空俯瞰夜色中的地球，\n极光沿着弧形地平线流动。","EARTH / AURORA",true),
        new Item("scotland","苏格兰高地","掠过开阔的山峦与湖泊，\n在缓慢的镜头里感受辽阔。","SCOTLAND / AERIAL",true),
        new Item("dusk","暮色山海","暮色落在群山与海面之间，\n留下一段安静的时光。","DUSK / STILL",false)
    };
    static Item find(String id){for(Item item:ITEMS)if(item.id.equals(id))return item;return ITEMS[0];}
    static List<Item> list(boolean dynamic){List<Item> list=new ArrayList<>();for(Item item:ITEMS)if(item.dynamic==dynamic)list.add(item);return list;}
    static String next(Context context,String current){List<Item> list=new ArrayList<>();for(Item item:ITEMS)if(item.dynamic&&item.available(context))list.add(item);if(list.isEmpty())return current;for(int i=0;i<list.size();i++)if(list.get(i).id.equals(current))return list.get((i+1)%list.size()).id;return list.get(0).id;}
}
