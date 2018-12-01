package comviewaquahp.google.sites.youbimiku;

/**
 * Created by Soichi Ikebe on 2018/06/17.
 */

import android.graphics.Bitmap;

import com.github.bassaer.chatmessageview.model.IChatUser;

public class User implements IChatUser {
    Integer id;
    String name;
    Bitmap icon;

    public User(int id, String name, Bitmap icon) {
        this.id = id;
        this.name = name;
        this.icon = icon;
    }

    @Override
    public String getId() {
        return this.id.toString();
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public Bitmap getIcon() {
        return this.icon;
    }

    @Override
    public void setIcon(Bitmap icon) {
        this.icon = icon;
    }
}