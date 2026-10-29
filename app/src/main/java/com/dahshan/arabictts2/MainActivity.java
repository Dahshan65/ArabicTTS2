package com.dahshan.arabictts2;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private TextToSpeech tts;
    private boolean ready = false;

    private EditText etText;
    private TextView tvStatus, tvRate, tvPitch;
    private SeekBar sbRate, sbPitch;

    private float rate = 1.0f;
    private float pitch = 1.0f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        etText = findViewById(R.id.etText);
        tvStatus = findViewById(R.id.tvStatus);
        tvRate = findViewById(R.id.tvRate);
        tvPitch = findViewById(R.id.tvPitch);
        sbRate = findViewById(R.id.sbRate);
        sbPitch = findViewById(R.id.sbPitch);
        Button btnPlay = findViewById(R.id.btnPlay);
        Button btnStop = findViewById(R.id.btnStop);
        Button btnSave = findViewById(R.id.btnSave);

        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int r = tts.setLanguage(new Locale("ar"));
                if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tvStatus.setText("الصوت العربي غير مثبّت. ثبّته من إعدادات تحويل النص إلى كلام.");
                } else {
                    ready = true;
                    tvStatus.setText("جاهز ✅");
                }
            } else {
                tvStatus.setText("تعذر تشغيل محرك الصوت.");
            }
        });

        sbRate.setOnSeekBarChangeListener(new SimpleSeek() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean u) {
                rate = 0.5f + p * 0.1f;
                tvRate.setText("السرعة: " + String.format(Locale.US, "%.1f", rate));
            }
        });

        sbPitch.setOnSeekBarChangeListener(new SimpleSeek() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean u) {
                pitch = 0.5f + p * 0.1f;
                tvPitch.setText("درجة الصوت: " + String.format(Locale.US, "%.1f", pitch));
            }
        });

        btnPlay.setOnClickListener(v -> {
            String text = etText.getText().toString().trim();
            if (text.isEmpty()) { tvStatus.setText("اكتب نصًا أولًا."); return; }
            if (!ready) { tvStatus.setText("المحرك غير جاهز بعد."); return; }
            tts.setSpeechRate(rate);
            tts.setPitch(pitch);
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "play");
            tvStatus.setText("جارٍ القراءة...");
        });

        btnStop.setOnClickListener(v -> {
            if (tts != null) tts.stop();
            tvStatus.setText("تم الإيقاف.");
        });

        btnSave.setOnClickListener(v -> saveToFile());
    }

    private void saveToFile() {
        String text = etText.getText().toString().trim();
        if (text.isEmpty()) { tvStatus.setText("اكتب نصًا أولًا."); return; }
        if (!ready) { tvStatus.setText("المحرك غير جاهز بعد."); return; }

        tts.setSpeechRate(rate);
        tts.setPitch(pitch);

        final File temp = new File(getCacheDir(), "tts_temp.wav");
        if (temp.exists()) temp.delete();

        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String id) { }

            @Override
            public void onDone(String id) {
                if (!"save".equals(id)) return;
                final String result = copyToMusic(temp);
                runOnUiThread(() -> tvStatus.setText(result));
            }

            @Override
            public void onError(String id) {
                runOnUiThread(() -> tvStatus.setText("فشل إنشاء الملف الصوتي."));
            }
        });

        tvStatus.setText("جارٍ إنشاء الملف...");
        int res = tts.synthesizeToFile(text, null, temp, "save");
        if (res != TextToSpeech.SUCCESS) {
            tvStatus.setText("فشل بدء إنشاء الملف.");
        }
    }

    private String copyToMusic(File src) {
        try {
            String name = "arabic_tts_" + System.currentTimeMillis() + ".wav";
            ContentValues values = new ContentValues();
            values.put(MediaStore.Audio.Media.DISPLAY_NAME, name);
            values.put(MediaStore.Audio.Media.MIME_TYPE, "audio/wav");
            values.put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/ArabicTTS");

            ContentResolver resolver = getContentResolver();
            Uri uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values);
            if (uri == null) return "تعذر إنشاء الملف في الهاتف.";

            try (InputStream in = new FileInputStream(src);
                 OutputStream out = resolver.openOutputStream(uri)) {
                if (out == null) return "تعذر فتح الملف للكتابة.";
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            }
            return "تم الحفظ ✅ في مجلد الموسيقى ← ArabicTTS\n" + name;
        } catch (Exception e) {
            return "خطأ أثناء الحفظ: " + e.getMessage();
        }
    }

    @Override
    protected void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.onDestroy();
    }

    private abstract static class SimpleSeek implements SeekBar.OnSeekBarChangeListener {
        @Override public void onStartTrackingTouch(SeekBar s) { }
        @Override public void onStopTrackingTouch(SeekBar s) { }
    }
}
