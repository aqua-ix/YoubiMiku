package comviewaquahp.google.sites.youbimiku;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import com.github.bassaer.chatmessageview.model.Message;
import com.github.bassaer.chatmessageview.view.ChatView;

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
        String masterName = "あなた";

        int mikuId = 1;
        Bitmap mikuIcon = BitmapFactory.decodeResource(getResources(), R.drawable.normal);
        String mikuName = "初音ミク";

        final User master = new User(masterId, masterName, masterIcon);
        final User miku = new User(mikuId, mikuName, mikuIcon);

        mChatView = (ChatView) findViewById(R.id.chat_view);

        Message init = new Message.Builder()
                .setUser(miku)
                .setRight(false) // This message Will be shown left side.
                .setText("こんにちは!まだ開発中なので上手く答えられないかもしれません。その時はごめんなさい...")//Message contents
                .build();

        mChatView.receive(init);

        Message init2 = new Message.Builder()
                .setUser(miku)
                .setRight(false) // This message Will be shown left side.
                .setText("ご意見・ご要望は[aquapinfo@gmail.com]まで！")//Message contents
                .build();

        mChatView.receive(init2);

        Message init3 = new Message.Builder()
                .setUser(miku)
                .setRight(false) // This message Will be shown left side.
                .setText("下の入力欄に何か入れてね。\nひらがなだと上手く反応できます。\nボカロPさんの名前もわかるかも...?")//Message contents
                .build();

        mChatView.receive(init3);

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