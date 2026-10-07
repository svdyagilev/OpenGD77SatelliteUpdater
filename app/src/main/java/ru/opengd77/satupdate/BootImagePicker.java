package ru.opengd77.satupdate;
import android.app.*;
import android.graphics.*;
import android.net.Uri;
import android.widget.*;
import java.io.*;
final class BootImagePicker {
    interface Apply {void apply(byte[] payload)throws Exception;}
    static void preview(Activity activity,Uri uri,Apply apply)throws IOException {
        BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;
        try(InputStream in=activity.getContentResolver().openInputStream(uri)){BitmapFactory.decodeStream(in,null,options);}
        if(options.outWidth<1||options.outHeight<1||options.outWidth>30000||options.outHeight>30000)throw new IOException("Изображение не распознано или слишком большое");
        options.inSampleSize=1;while(options.outWidth/options.inSampleSize>1024||options.outHeight/options.inSampleSize>1024)options.inSampleSize*=2;options.inJustDecodeBounds=false;
        Bitmap source;try(InputStream in=activity.getContentResolver().openInputStream(uri)){source=BitmapFactory.decodeStream(in,null,options);}if(source==null)throw new IOException("Не удалось открыть изображение");
        Bitmap fitted=Bitmap.createBitmap(128,64,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(fitted);canvas.drawColor(Color.WHITE);
        float scale=Math.min(128f/source.getWidth(),64f/source.getHeight());float width=source.getWidth()*scale,height=source.getHeight()*scale;
        canvas.drawBitmap(source,null,new RectF((128-width)/2,(64-height)/2,(128+width)/2,(64+height)/2),new Paint(Paint.FILTER_BITMAP_FLAG));source.recycle();
        int[] pixels=new int[128*64];fitted.getPixels(pixels,0,128,0,0,128,64);fitted.recycle();byte[] payload=BootImage.pack(pixels);
        for(int y=0;y<64;y++)for(int x=0;x<128;x++)pixels[y*128+x]=(payload[x+(y/8)*128]&(1<<(y%8)))!=0?Color.BLACK:Color.WHITE;
        Bitmap mono=Bitmap.createBitmap(pixels,128,64,Bitmap.Config.ARGB_8888);ImageView image=new ImageView(activity);image.setImageBitmap(mono);image.setAdjustViewBounds(true);image.setMinimumHeight((int)(160*activity.getResources().getDisplayMetrics().density));
        new AlertDialog.Builder(activity).setTitle("Заставка 128×64").setMessage("Чёрно-белое изображение с сохранением пропорций. После применения передайте раздел «Загрузочный экран» в рацию.").setView(image).setNegativeButton("Отмена",null).setPositiveButton("Применить",(d,w)->{try{apply.apply(payload);}catch(Exception e){new AlertDialog.Builder(activity).setMessage(e.getMessage()).setPositiveButton("OK",null).show();}}).show();
    }
}
