package comviewaquahp.google.sites.youbimiku;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

/**
 * Created by Lenovo on 2018/06/17.
 */

class MikuFace {

    public MikuFace(){
    }

    public Bitmap face(String s, Resources resources, Context context){

        Bitmap icon = BitmapFactory.decodeResource(resources, R.drawable.normal);

        talkReader t = new talkReader();
        t.reader(context);
        ListData data = new ListData();

        String miku_talk;
        String your_talk = s;

        for(int i=1;i<t.talk.size();i++) {
            data = t.talk.get(i);
            miku_talk = data.getMiku();
            if (your_talk.contains(data.getYou())) {
                if (data.getFace().equals("N")) {
                    icon = BitmapFactory.decodeResource(resources, R.drawable.normal);
                    break;
                } else if (data.getFace().equals("G")) {
                    icon = BitmapFactory.decodeResource(resources, R.drawable.glad);
                    break;
                } else if (data.getFace().equals("A")) {
                    icon = BitmapFactory.decodeResource(resources, R.drawable.angry);
                    break;
                } else if (data.getFace().equals("S")) {
                    icon = BitmapFactory.decodeResource(resources, R.drawable.sad);
                    break;
                }
            }
        }
        return icon;
    }
}
