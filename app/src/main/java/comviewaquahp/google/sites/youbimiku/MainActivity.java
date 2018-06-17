package comviewaquahp.google.sites.youbimiku;

import android.content.Intent;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutCompat;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.EditText;

import com.github.bassaer.chatmessageview.model.Message;
import com.github.bassaer.chatmessageview.view.ChatView;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import static android.R.id.button1;
import static android.R.id.edit;
import static android.R.id.list;
import static android.R.id.message;
import static comviewaquahp.google.sites.youbimiku.R.string.defaultText;


public class MainActivity extends AppCompatActivity {

    private ChatView mChatView;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //User id
        int masterId = 0;
        //User icon
        //not using
        Bitmap masterIcon = null;
        //User name
        String masterName = "Master";

        int mikuId = 1;
        Bitmap mikuIcon = BitmapFactory.decodeResource(getResources(), R.drawable.normal);
        String mikuName = "Miku";

        final User master = new User(masterId, masterName, masterIcon);
        final User miku = new User(mikuId, mikuName, mikuIcon);

        mChatView = (ChatView) findViewById(R.id.chat_view);

        Message init = new Message.Builder()
                .setUser(miku)
                .setRight(false) // This message Will be shown left side.
                .setText("こんにちは!")//Message contents
                .build();

        mChatView.receive(init);// Will be shown left side

        mChatView.setOnClickSendButtonListener(new View.OnClickListener() {

            @Override
            public void onClick(View view) {

                MikuTalk mikuTalk = new MikuTalk();
                MikuFace mikuFace = new MikuFace();

                Message m1 = new Message.Builder()
                        .setUser(master) // Sender
                        .setRight(true) // This message Will be shown right side.
                        .setText(mChatView.getInputText()) //Message contents
                        .hideIcon(true)
                        .build();

                mChatView.send(m1); // Will be shown right side
                mChatView.setInputText("");

                miku.setIcon(mikuFace.face(m1.getText(), getResources(),getApplicationContext()));

                Message m2 = new Message.Builder()
                        .setUser(miku)
                        .setRight(false) // This message Will be shown left side.
                        .setText(mikuTalk.talk(m1.getText(),getApplicationContext()))//Message contents
                        .build();

                mChatView.receive(m2);// Will be shown left side
            }

        });

    }
}