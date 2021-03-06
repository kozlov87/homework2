package ru.ifmo.android_2015.citycam;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.JsonReader;
import android.util.JsonToken;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import ru.ifmo.android_2015.citycam.model.City;
import ru.ifmo.android_2015.citycam.webcams.Webcams;
/**
 * Экран, показывающий веб-камеру одного выбранного города.
 * Выбранный город передается в extra параметрах.
 */
public class CityCamActivity extends AppCompatActivity {
    /**
     * Обязательный extra параметр - объект City, камеру которого надо показать.
     */
    public static final String EXTRA_CITY = "city";
    private City city;
    private ImageView camImageView;
    private ProgressBar progressView;
    private DownloadJsonTask downloadTask;
    private TextView titleTextView;
    private CamData cameraData;
    class CamData {
        Bitmap image;
        String title;
    }
    class SavedData {
        CamData webcam;
        DownloadJsonTask task;
    }
    void updateUI(CamData data) {
        progressView.setVisibility(View.INVISIBLE);
        if(data.image != null) {
            camImageView.setImageBitmap(data.image);
            titleTextView.setText(data.title);
        } else {
            titleTextView.setText("No data for this cam");
        }
    }
    @Override
    public SavedData onRetainCustomNonConfigurationInstance() {
        SavedData data = new SavedData();
        data.task = this.downloadTask;
        data.webcam = this.cameraData;
        return data;
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        city = getIntent().getParcelableExtra(EXTRA_CITY);
        if (city == null) {
            Log.w(TAG, "City object not provided in extra parameter: " + EXTRA_CITY);
            finish();
        }
        setContentView(R.layout.activity_city_cam);
        camImageView = (ImageView) findViewById(R.id.cam_image);
        progressView = (ProgressBar) findViewById(R.id.progress);
        titleTextView = (TextView) findViewById(R.id.titleTextView);
        getSupportActionBar().setTitle(city.name);
        progressView.setVisibility(View.VISIBLE);
        if (savedInstanceState != null) {
            SavedData loaded = (SavedData)getLastCustomNonConfigurationInstance();
            downloadTask = loaded.task;
            cameraData = loaded.webcam;
            updateUI(cameraData);
        }
        if (downloadTask == null) {
            downloadTask = new DownloadJsonTask(this);
            downloadTask.execute();
        } else {
            downloadTask.attachActivity(this);
        }
    }
    enum DownloadState {
        DOWNLOADING(R.string.downloading),
        DONE(R.string.done),
        ERROR(R.string.error);
        // ID строкового ресурса для заголовка окна прогресса
        final int titleResId;
        DownloadState(int titleResId) {
            this.titleResId = titleResId;
        }
    }
    private static class DownloadJsonTask extends AsyncTask<Void, Integer, DownloadState> {
        private DownloadState state = DownloadState.DOWNLOADING;
        private CityCamActivity activity;
        DownloadJsonTask(CityCamActivity activity) {
            this.activity = activity;
        }
        void attachActivity(CityCamActivity activity) {
            this.activity = activity;
        }
        @Override
        protected DownloadState doInBackground(Void... voids) {
            try {
                this.activity.downloadFile(this.activity.getApplicationContext());
                state = DownloadState.DONE;
            } catch (Exception e) {
                Log.e(TAG, "Error downloading file: " + e, e);
                state = DownloadState.ERROR;
            }
            return state;
        }
    }
    void downloadFile(Context context) throws IOException {
        File destFile = FileUtils.createTempExternalFile(context, ".json");
        DownloadUtils.downloadFile(Webcams.createNearbyUrl(this.city.latitude, this.city.longitude).toString(), destFile);
        InputStreamReader inputStreamReader = null;
        BufferedReader bufferedReader = null;
        JsonReader jsonReader = null;
        String imageUrl = null;
        String title = null;
        try {
            inputStreamReader = new InputStreamReader(new FileInputStream(destFile));
            bufferedReader = new BufferedReader(inputStreamReader);
            jsonReader = new JsonReader(bufferedReader);
            jsonReader.beginObject();
            while (jsonReader.hasNext()) {
                final String name = jsonReader.nextName();
                final boolean isNull = jsonReader.peek() == JsonToken.NULL;
                if(name.equals("webcams") && !isNull) {
                    jsonReader.beginObject();
                    while (jsonReader.hasNext()) {
                        final String nextName = jsonReader.nextName();
                        final boolean isNextNull = jsonReader.peek() == JsonToken.NULL;
                        if(nextName.equals("webcam") && !isNextNull) {
                            jsonReader.beginArray();
                            while (jsonReader.hasNext()) {
                                jsonReader.beginObject();
                                while (jsonReader.hasNext()) {
                                    final String webcamName = jsonReader.nextName();
                                    final boolean webcamNameNull = jsonReader.peek() == JsonToken.NULL;
                                    boolean skip = true;
                                    if(!webcamNameNull) {
                                        if(webcamName.equals("preview_url")) {
                                            imageUrl = jsonReader.nextString();
                                            skip = false;
                                        }
                                        if(webcamName.equals("title")) {
                                            title = jsonReader.nextString();
                                            skip = false;
                                        }
                                    }
                                    if (skip)
                                        jsonReader.skipValue();
                                }
                                jsonReader.endObject();
                            }
                            jsonReader.endArray();
                        } else jsonReader.skipValue();
                    }
                    jsonReader.endObject();
                } else jsonReader.skipValue();
            }
            jsonReader.endObject();
        }
        catch (Exception e) {
            e.printStackTrace();
            Log.e("Parsing", "Cant parse json.");
        }
        finally {
            if(bufferedReader != null)
                bufferedReader.close();
            if(jsonReader != null)
                jsonReader.close();
            if(inputStreamReader != null)
                inputStreamReader.close();
        }
        if(imageUrl != null) {
            File previewFile = FileUtils.createTempExternalFile(context, ".jpg");
            DownloadUtils.downloadFile(imageUrl, previewFile);
            InputStream bitmapInputStream;
            try {
                bitmapInputStream = new FileInputStream(previewFile);
                final Bitmap bitmap = BitmapFactory.decodeStream(bitmapInputStream);
                final CamData data = new CamData();
                data.image = bitmap;
                data.title = title;
                this.cameraData = data;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        updateUI(data);
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
                Log.e("Image Processing", "Cant decode bitmap.");
            }
        } else {
            Log.e("Parsing", "No webcams in this location.");
        }
//Что-то не получилось, пишем что не нашли камеры
        if(this.cameraData == null) {
            final CamData data = new CamData();
            this.cameraData = data;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    updateUI(data);
                }
            });
        }
    }
    private static final String TAG = "CityCam";
}