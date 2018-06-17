package comviewaquahp.google.sites.youbimiku;

import android.content.Context;

/**
 * Created by Lenovo on 2018/06/17.
 */

class MikuTalk {

    public MikuTalk(){
    }

    public String talk(String s, Context context) {

        talkReader t = new talkReader();
        t.reader(context);
        ListData data = new ListData();

        String miku_talk;
        String your_talk = s;
        String message = "何か用ですか?";

        for(int i=1;i<t.talk.size();i++) {
            data = t.talk.get(i);
            miku_talk = data.getMiku();
            if (your_talk.equals("")) {
                message = "何か用ですか?";
                break;
            } else if (your_talk.contains(data.getYou())) {
                message = miku_talk;
                break;
            }
        }

        return message;
    }
}
