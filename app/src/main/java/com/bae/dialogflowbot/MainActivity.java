package com.bae.dialogflowbot;

import android.app.Dialog;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;

import com.bae.dialogflowbot.adapters.ChatAdapter;
import com.bae.dialogflowbot.helpers.SendMessageInBg;
import com.bae.dialogflowbot.interfaces.BotReply;
import com.bae.dialogflowbot.models.Message;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.dialogflow.v2.DetectIntentResponse;
import com.google.cloud.dialogflow.v2.QueryInput;
import com.google.cloud.dialogflow.v2.SessionName;
import com.google.cloud.dialogflow.v2.SessionsClient;
import com.google.cloud.dialogflow.v2.SessionsSettings;
import com.google.cloud.dialogflow.v2.TextInput;
import com.google.common.collect.Lists;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

import pl.droidsonroids.gif.GifImageView;


public class MainActivity extends AppCompatActivity implements BotReply {

    RecyclerView chatView;
    ChatAdapter chatAdapter;
    List<Message> messageList = new ArrayList<>();
    EditText editMessage;
    ImageButton btnSend;
    private TextToSpeech mTTS;
    private SpeechRecognizer mSpeechRecognizer;

    private Intent mSpeechRecognizerIntent;
    public static Dialog actionDialog;
    public static TextView actionDialogText;
    public static GifImageView actionDiologGif;

    //dialogFlow
    private SessionsClient sessionsClient;
    private SessionName sessionName;
    private String uuid = UUID.randomUUID().toString();
    private String TAG = "mainactivity";


    private DatabaseReference Speaking, Ulteasonic;
    private FirebaseDatabase database;
    private Boolean isbusy;

    private AudioManager audioManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        isbusy = false;
        chatView = findViewById(R.id.chatView);
        editMessage = findViewById(R.id.editMessage);
        btnSend = findViewById(R.id.btnSend);

        chatAdapter = new ChatAdapter(messageList, this);
        chatView.setAdapter(chatAdapter);
        actionDialog = new Dialog(MainActivity.this);
        actionDialog.setContentView(R.layout.action_dialog);
        actionDialog.setCancelable(false);
        actionDialog.getWindow().setLayout(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actionDialog.getWindow().setBackgroundDrawable(getDrawable(R.drawable.border_background));
        actionDialogText = actionDialog.findViewById(R.id.action_dialog_text);
        actionDiologGif = actionDialog.findViewById(R.id.action_diolog_gif);
        mTTS = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status == TextToSpeech.SUCCESS) {
                    int result = mTTS.setLanguage(Locale.ENGLISH);
                    if (result == TextToSpeech.LANG_MISSING_DATA
                            || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        Log.e("TTS", "Language not supported");
                    } else {
                    }
                } else {
                    Log.e("TTS", "Initialization failed");
                }
            }
        });
        float pitch = (float) 0.4;
        if (pitch < 0.1) pitch = 0.1f;
        float speed = (float) 0.8;
        if (speed < 0.1) speed = 0.1f;
        mTTS.setPitch(pitch);
        mTTS.setSpeechRate(speed);
        mTTS.setLanguage(Locale.ENGLISH);
        mSpeechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        mSpeechRecognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        mSpeechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        mSpeechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE,
                Locale.getDefault());


        mSpeechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle bundle) {


            }

            @Override
            public void onBeginningOfSpeech() {
                isbusy = true;
            }

            @Override
            public void onRmsChanged(float v) {

            }

            @Override
            public void onBufferReceived(byte[] bytes) {

            }

            @Override
            public void onEndOfSpeech() {
                mSpeechRecognizer.stopListening();
                actionDialogText.setText("Thinking....");

            }

            @Override
            public void onError(int error) {
                actionDialog.dismiss();

                checkError(error);
                isbusy = false;

            }

            @Override
            public void onResults(Bundle bundle) {
                //getting all the matches
                ArrayList<String> matches = bundle
                        .getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);

                //displaying the first match
                if (matches != null) {
                    String message = matches.get(0);

                    messageList.add(new Message(message, false));
                    editMessage.setText("");
                    sendMessageToBot(message);
                    Objects.requireNonNull(chatView.getAdapter()).notifyDataSetChanged();
                    Objects.requireNonNull(chatView.getLayoutManager())
                            .scrollToPosition(messageList.size() - 1);
                    actionDialog.dismiss();

                } else {
                    mSpeechRecognizer.stopListening();
                    actionDialog.dismiss();
                }

                isbusy = false;

            }

            @Override
            public void onPartialResults(Bundle bundle) {

            }

            @Override
            public void onEvent(int i, Bundle bundle) {

            }
        });

        btnSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mSpeechRecognizer.startListening(mSpeechRecognizerIntent);

                actionDialog.show();
                actionDialogText.setText("Listening....");
            }
        });

        setUpBot();

        audioManager = (AudioManager) getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
        for (int x = 1; x <= 15; x++) {
            audioManager.adjustVolume(AudioManager.ADJUST_RAISE, AudioManager.FLAG_PLAY_SOUND);
        }
        // Write a message to the database
        database = FirebaseDatabase.getInstance();
        Speaking = database.getReference("Signal/Speaking");
        Ulteasonic = database.getReference("Signal/Ultrasonic");

        Ulteasonic.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                int value = Integer.parseInt(String.valueOf(dataSnapshot.getValue()));
                if (value == 1 && !isbusy) {
                    mSpeechRecognizer.startListening(mSpeechRecognizerIntent);

                    actionDialog.show();
                    actionDialogText.setText("Listening....");
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });
    }


    private void setUpBot() {

        try {
            InputStream stream = this.getResources().openRawResource(R.raw.credencial);
            GoogleCredentials credentials = GoogleCredentials.fromStream(stream)
                    .createScoped(Lists.newArrayList("https://www.googleapis.com/auth/cloud-platform"));
            String projectId = ((ServiceAccountCredentials) credentials).getProjectId();

            SessionsSettings.Builder settingsBuilder = SessionsSettings.newBuilder();
            SessionsSettings sessionsSettings = settingsBuilder.setCredentialsProvider(
                    FixedCredentialsProvider.create(credentials)).build();
            sessionsClient = SessionsClient.create(sessionsSettings);
            sessionName = SessionName.of(projectId, uuid);

        } catch (Exception e) {
            speak(e.getMessage());
        }
    }


    private void
    sendMessageToBot(String message) {
        QueryInput input = QueryInput.newBuilder()
                .setText(TextInput.newBuilder().setText(message).setLanguageCode("en-US")).build();
        new SendMessageInBg(this, sessionName, sessionsClient, input).execute();
    }

    @Override
    public void callback(DetectIntentResponse returnResponse) {
        if (returnResponse != null) {
            String botReply = returnResponse.getQueryResult().getFulfillmentText();
            speak(botReply);
            if (!botReply.isEmpty()) {
                messageList.add(new Message(botReply, true));
                chatAdapter.notifyDataSetChanged();
                Objects.requireNonNull(chatView.getLayoutManager()).scrollToPosition(messageList.size() - 1);
            } else {

                speak("something went wrong");
            }
        } else {
            speak("failed to connect!, Please check Internet connection and restart me.");
        }
    }

    private void speak(String reply) {

        if (reply != null) {
            mTTS.speak(reply, TextToSpeech.QUEUE_FLUSH, null);
        }

    }


    private void checkError(int error) {
        switch (error) {
            case SpeechRecognizer.ERROR_AUDIO:
                speak("ERROR AUDIO");
                break;
            case SpeechRecognizer.ERROR_CLIENT:
                speak("Please check Internet connection and try again.");

                break;
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:

                speak("ERROR INSUFFICIENT PERMISSIONS");
                break;
            case SpeechRecognizer.ERROR_NETWORK:

                speak("Please check Internet connection and try again.");
                break;
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                speak("Please check Internet connection and try again.");

                break;
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                Toast.makeText(MainActivity.this, ">>> ERROR_RECOGNIZER_BUSY", Toast.LENGTH_LONG).show();
                break;
            case SpeechRecognizer.ERROR_SERVER:
                speak("There is a problem in server please try again.");

                break;
            case SpeechRecognizer.ERROR_NO_MATCH:
                speak("I could not hear please try again.");

                break;
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                speak("ERROR SPEECH TIMEOUT");

                break;
            default:
                speak("ERROR UNKOWN");

        }
    }


    @Override
    protected void onDestroy() {
        if (mTTS != null) {
            mTTS.stop();
            mTTS.shutdown();
        }
        super.onDestroy();
    }


}
