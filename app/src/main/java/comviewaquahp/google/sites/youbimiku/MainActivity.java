package comviewaquahp.google.sites.youbimiku;

import android.content.Intent;
import android.content.res.AssetManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutCompat;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.EditText;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import static android.R.id.button1;
import static android.R.id.edit;
import static android.R.id.list;
import static comviewaquahp.google.sites.youbimiku.R.string.defaultText;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private TextView textView;
    private EditText editText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button ok = (Button) findViewById(R.id.ok_Button);
        Button resetButton = (Button) findViewById(R.id.reset_Button);

        ok.setOnClickListener (this);
        resetButton.setOnClickListener (this);

    }

    @Override
    public void onClick(View v){
        EditText editText = (EditText) findViewById(R.id.yourText);
        TextView textView = (TextView)findViewById(R.id.MikuTalk);
        ImageView imageView = (ImageView)findViewById(R.id.face);
        String miku_talk;
        String your_talk = editText.getText().toString();
        editText.setText("");

        talkReader t = new talkReader();
        t.reader(getApplicationContext());
        ListData data = new ListData();

        for(int i=1;i<t.talk.size();i++) {
            data = t.talk.get(i);
            miku_talk = data.getMiku();
            if(your_talk.equals("")){
                textView.setText("You > \nMiku > 何か用かな?");
                imageView.setImageResource(R.drawable.normal);
                break;
            }
            else if (your_talk.contains(data.getYou())) {
                textView.setText("You > " + your_talk + "\nMiku > " + miku_talk);
                if(data.getFace().equals("N")){
                imageView.setImageResource(R.drawable.normal);
                }
                else if(data.getFace().equals("G")){
                    imageView.setImageResource(R.drawable.glad);
                }
                else if(data.getFace().equals("A")){
                    imageView.setImageResource(R.drawable.angry);
                }
                else if(data.getFace().equals("S")){
                    imageView.setImageResource(R.drawable.sad);
                }
                break;
            } else {
                textView.setText("You > " + your_talk + "\nMiku > 「" + your_talk + "」という言葉はまだ分かりません。ごめんね。");
                imageView.setImageResource(R.drawable.normal);
            }
        }

        if(v.getId()==R.id.reset_Button) {
            textView.setText(defaultText);
            editText.setText("");
            imageView.setImageResource(R.drawable.normal);
        }

    }


}
