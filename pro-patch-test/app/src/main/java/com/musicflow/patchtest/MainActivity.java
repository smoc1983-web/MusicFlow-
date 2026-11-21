package com.musicflow.patchtest;

import android.app.Activity;
import android.content.ContentValues;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;

public final class MainActivity extends Activity {
    // Deliberately isolated from the MusicFlow release package and update feed.
    private static final String PATCH_ROOT =
        "https://raw.githubusercontent.com/smoc1983-web/MusicFlow-/" +
        "feature/musicflow-pro-demo-patch/pro-patch-test/patch/";
    private static final String EXPECTED_PAYLOAD = "musicflow-pro-test:v1:enable_download";
    private static final String TEST_RSA_PUBLIC_KEY =
        "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA2bbyZszazpsLFwwjrzyWN34gEzHpDjkCq67T7bS4z/fWZFSCSkw1esf2HliAxdHa/ftxTNh3JHAFDDwFcSV56RLtnQrl7DazaRF6sMbjjvE9gSQz8l3ATblUDmhp7U49zoWffTyNLPRyuj+hPJBcDh2fg+nncgKGApDXjpRt5nUM0m6gwlS6u5qvNFRVWnc5UOVbugrJIQP4mJtrg8eiJMnq7VQUEJZvqvAIKu6xUIkEon7IVvw2l0kkqaflIK+Nla9joUJRLtc7qFPWByjkLOGVDvgkCOjEnH0hyvOoU7zbNczInAMQCg2ZFYpMraYBaFK9T2vAySV+PK6qeArTpQIDAQAB";

    private SharedPreferences prefs;
    private TextView stateView;
    private TextView messageView;
    private Button patchButton;
    private Button downloadButton;
    private Button payButton;
    private boolean busy;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("pro_test_state", MODE_PRIVATE);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(26), dp(20), dp(20));

        TextView heading = label("MusicFlow Pro · тест", 26);
        content.addView(heading);
        content.addView(label("Отдельная установка. Платёж только имитируется, реальные деньги не списываются.", 15));
        content.addView(label("Источник патча: отдельная ветка GitHub. Подпись патча проверяется перед активацией.", 14));

        stateView = label("", 17);
        content.addView(stateView);
        messageView = label("Порядок: 1. Оплата → 2. Микро-патч → 3. Тестовый трек.", 15);
        content.addView(messageView);

        payButton = action(content, "1. Имитировать оплату Pro", v -> {
            prefs.edit().putBoolean("paid", true).apply();
            messageView.setText("Тестовая оплата подтверждена локально. Никакие деньги не списывались.");
            refresh();
        });
        patchButton = action(content, "2. Загрузить и проверить микро-патч", v -> {
            if (!prefs.getBoolean("paid", false)) return;
            runTask("Получение микро-патча GitHub...", () -> {
                byte[] body = fetch(PATCH_ROOT + "activation.json", 12_000);
                JSONObject json = new JSONObject(new String(body, StandardCharsets.UTF_8));
                String payload = json.getString("payload");
                String signature = json.getString("signature");
                if (!EXPECTED_PAYLOAD.equals(payload) || !verifySignature(payload, signature)) {
                    throw new SecurityException("Патч не прошёл проверку подлинности");
                }
                prefs.edit().putBoolean("patch", true).apply();
                return "Подлинный патч получен (" + body.length + " байт). Pro для теста активирован.";
            });
        });
        downloadButton = action(content, "3. Скачать тестовый аудиотрек WAV", v -> {
            if (!isPro()) return;
            runTask("Скачивание тестовой мелодии из GitHub...", () -> {
                byte[] encoded = fetch(PATCH_ROOT + "demo-track.wav.b64", 40_000);
                byte[] wav = Base64.decode(new String(encoded, StandardCharsets.US_ASCII).trim(), Base64.DEFAULT);
                if (wav.length < 44 || wav.length > 32_000 ||
                    !startsWith(wav, 0, "RIFF") || !startsWith(wav, 8, "WAVE")) {
                    throw new IOException("Данные WAV не прошли проверку формата");
                }
                Uri result = saveWav(wav);
                return "Тестовая мелодия сохранена: Music/MusicFlow Pro Test. " +
                    wav.length + " байт. URI: " + result;
            });
        });
        action(content, "Сбросить тестовую Pro-активацию", v -> {
            if (busy) return;
            prefs.edit().clear().apply();
            messageView.setText("Активация сброшена. Скачивание снова заблокировано.");
            refresh();
        });

        content.addView(label("ВАЖНО: тестовый файл — специально созданный сигнал, а не коммерческая песня. " +
            "Для настоящей подписки потребуется серверная проверка оплаты и прав на загрузку.", 13));

        ScrollView scroll = new ScrollView(this);
        scroll.addView(content);
        setContentView(scroll);
        refresh();
    }

    private interface Task { String run() throws Exception; }

    private void runTask(String opening, Task task) {
        if (busy) return;
        busy = true;
        messageView.setText(opening);
        refresh();
        new Thread(() -> {
            String message;
            try {
                message = task.run();
            } catch (Exception e) {
                message = "Ошибка: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            }
            String finalMessage = message;
            runOnUiThread(() -> {
                busy = false;
                messageView.setText(finalMessage);
                refresh();
            });
        }, "MusicFlowProDemoIO").start();
    }

    private boolean isPro() {
        return prefs.getBoolean("paid", false) && prefs.getBoolean("patch", false);
    }

    private void refresh() {
        boolean paid = prefs.getBoolean("paid", false);
        boolean patch = prefs.getBoolean("patch", false);
        stateView.setText("Оплата: " + (paid ? "тест подтверждён" : "нет") +
            "\nПодписанный патч: " + (patch ? "получен" : "нет") +
            "\nPro: " + (isPro() ? "активен" : "недоступен"));
        payButton.setEnabled(!busy);
        patchButton.setEnabled(!busy && paid);
        downloadButton.setEnabled(!busy && isPro());
    }

    private static boolean verifySignature(String payload, String encodedSignature) throws Exception {
        byte[] pubBytes = Base64.decode(TEST_RSA_PUBLIC_KEY, Base64.DEFAULT);
        PublicKey key = KeyFactory.getInstance("RSA")
            .generatePublic(new X509EncodedKeySpec(pubBytes));
        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(key);
        verifier.update(payload.getBytes(StandardCharsets.UTF_8));
        return verifier.verify(Base64.decode(encodedSignature, Base64.DEFAULT));
    }

    private static byte[] fetch(String address, int maximum) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(address).openConnection();
        conn.setInstanceFollowRedirects(false);
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(15000);
        conn.setRequestProperty("Accept", "application/octet-stream, application/json, text/plain");
        try {
            if (conn.getResponseCode() != 200) {
                throw new IOException("GitHub ответил HTTP " + conn.getResponseCode());
            }
            try (InputStream stream = conn.getInputStream();
                 ByteArrayOutputStream result = new ByteArrayOutputStream()) {
                byte[] chunk = new byte[4096];
                int n;
                while ((n = stream.read(chunk)) != -1) {
                    if (result.size() + n > maximum) throw new IOException("Превышен лимит ответа");
                    result.write(chunk, 0, n);
                }
                return result.toByteArray();
            }
        } finally {
            conn.disconnect();
        }
    }

    private Uri saveWav(byte[] content) throws IOException {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Audio.Media.DISPLAY_NAME, "MusicFlow_Pro_Test.wav");
        values.put(MediaStore.Audio.Media.MIME_TYPE, "audio/wav");
        values.put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/MusicFlow Pro Test");
        values.put(MediaStore.Audio.Media.IS_PENDING, 1);
        Uri saved = getContentResolver().insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values);
        if (saved == null) throw new IOException("Android не создал файл");
        boolean success = false;
        try {
            try (OutputStream stream = getContentResolver().openOutputStream(saved)) {
                if (stream == null) throw new IOException("Не удалось открыть файл для записи");
                stream.write(content);
                stream.flush();
            }
            ContentValues ready = new ContentValues();
            ready.put(MediaStore.Audio.Media.IS_PENDING, 0);
            if (getContentResolver().update(saved, ready, null, null) != 1) {
                throw new IOException("Не удалось завершить сохранение");
            }
            success = true;
            return saved;
        } finally {
            if (!success) getContentResolver().delete(saved, null, null);
        }
    }

    private static boolean startsWith(byte[] bytes, int offset, String prefix) {
        for (int i = 0; i < prefix.length(); i++) {
            if ((bytes[offset + i] & 255) != prefix.charAt(i)) return false;
        }
        return true;
    }

    private TextView label(String text, int sp) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(sp);
        view.setPadding(0, dp(7), 0, dp(9));
        return view;
    }

    private Button action(LinearLayout parent, String title, View.OnClickListener click) {
        Button button = new Button(this);
        button.setText(title);
        button.setAllCaps(false);
        parent.addView(button);
        button.setOnClickListener(click);
        return button;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
