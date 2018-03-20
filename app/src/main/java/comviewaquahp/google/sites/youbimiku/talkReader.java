package comviewaquahp.google.sites.youbimiku;

import android.content.Context;
import android.content.res.AssetManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by Lenovo on 2018/01/24.
 */

public class talkReader {
    List<ListData> talk = new ArrayList<ListData>();

    public void reader(Context context) {
        AssetManager assetManager = context.getResources().getAssets();
        try {
            // CSVファイルの読み込み
            InputStream inputStream = assetManager.open("talks.csv");
            InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
            BufferedReader bufferReader = new BufferedReader(inputStreamReader);
            String line;
            while ((line = bufferReader.readLine()) != null) {

                //カンマ区切りで１つづつ配列に入れる
                ListData data = new ListData();
                String[] RowData = line.split(",");

                //CSVの左([0]番目)から順番にセット
                data.setYou(RowData[0]);
                data.setMiku(RowData[1]);
                data.setFace(RowData[2]);

                talk.add(data);
            }
            bufferReader.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}