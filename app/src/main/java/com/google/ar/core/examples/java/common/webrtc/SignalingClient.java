package com.google.ar.core.examples.java.common.webrtc;

import android.util.Log;

import com.google.ar.core.examples.java.common.Constants;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.IceCandidate;
import org.webrtc.SessionDescription;

import java.io.IOException;
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class SignalingClient {

    private static SignalingClient instance;
    private SignalingClient(){
        init();
    }
    public static SignalingClient get() {
        if(instance == null) {
            synchronized (SignalingClient.class) {
                if(instance == null) {
                    instance = new SignalingClient();
                }
            }
        }
        return instance;
    }

    private Callback callback;
    private String localID;
    private String peerID;
    private OkHttpClient client = new OkHttpClient();
    private String url = Constants.WebRTCServerIp;
    private String result;

    private final TrustManager[] trustAll = new TrustManager[]{
            new X509TrustManager() {
                @Override
                public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {

                }

                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {

                }

                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }
            }
    };

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    private void init() {
        Request request = new Request.Builder().get().url(url + "/sign_in?mobile").build();

        client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.d("http-response", "failure");
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()){
                    result = response.body().string();
                    Log.d("http-response", result);
                    String[] clientNum = result.split("\n");
                    for (String element: clientNum){
                        String[] cur = element.split(",");
                        //Log.d("localIndex", cur[0]);
                        if (cur[0].equals("mobile")){
                            localID = cur[1];
                        }
                        else if (cur[0].equals("xlabs@DESKTOP-5VECIQN")){
                            peerID = cur[1];
                        }
                    }
                    //sendWait(peerID);
                    sendWait(localID);
                    if (peerID != null && localID != null){
                        callback.onSelfJoined();
                    }
                }
            }
        });


        /*try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAll, null);
            IO.setDefaultHostnameVerifier((hostname, session) -> true);
            IO.setDefaultSSLContext(sslContext);

            socket = IO.socket("https://192.168.1.108:8080");
            socket.connect();

            socket.emit("create or join", room);

            socket.on("created", args -> {
                Log.e("chao", "room created");
                callback.onCreateRoom();
            });
            socket.on("full", args -> {
                Log.e("chao", "room full");
            });
            socket.on("join", args -> {
                Log.e("chao", "peer joined");
                callback.onPeerJoined();
            });
            socket.on("joined", args -> {
                Log.e("chao", "self joined");
                callback.onSelfJoined();
            });
            socket.on("log", args -> {
                Log.e("chao", "log call " + Arrays.toString(args));
            });
            socket.on("bye", args -> {
                Log.e("chao", "bye " + args[0]);
                callback.onPeerLeave((String) args[0]);
            });
            socket.on("message", args -> {
                Log.e("chao", "message " + Arrays.toString(args));
                Object arg = args[0];
                if(arg instanceof String) {

                } else if(arg instanceof JSONObject) {
                    JSONObject data = (JSONObject) arg;
                    String type = data.optString("type");
                    if("offer".equals(type)) {
                        callback.onOfferReceived(data);
                    } else if("answer".equals(type)) {
                        callback.onAnswerReceived(data);
                    } else if("candidate".equals(type)) {
                        callback.onIceCandidateReceived(data);
                    }
                }
            });

        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (KeyManagementException e) {
            e.printStackTrace();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }*/
    }

    public void sendWait(String client_id){
        if (client_id != null){
            Request request = new Request.Builder().get().url(url + "/wait?peer_id=" + client_id).build();

            client.newCall(request).enqueue(new okhttp3.Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.d("http-wait-response", "failure");
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (response.isSuccessful()) {
                        result = response.body().string();
                        Log.d("http-wait-response", result);
                        try {
                            JSONObject jsonObject = new JSONObject(result);
                            //Log.d("http-wait-response-j", String.valueOf(jsonObject));
                            if (jsonObject.has("candidate")){
                                callback.onIceCandidateReceived(jsonObject);
                            }
                            else if (jsonObject.has("sdp")){
                                callback.onAnswerReceived(jsonObject);
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                }
            });
        }
    }

    public void sendSignOut(){
        if (localID != null){
            Request request = new Request.Builder().get().url(url + "/sign_in?mobile").build();

            client.newCall(request).enqueue(new okhttp3.Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.d("http-signout-response", "failure");
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (response.isSuccessful()) {
                        result = response.body().string();
                        Log.d("http-signout-response", result);
                    }
                }
            });
        }
    }

    public void sendIceCandidate(IceCandidate iceCandidate) {
        JSONObject jo = new JSONObject();
        try {
            jo.put("candidate", iceCandidate.sdp);
            jo.put("sdpMLineIndex", iceCandidate.sdpMLineIndex);
            jo.put("sdpMid", iceCandidate.sdpMid);

            RequestBody body = RequestBody.create(MediaType.parse("text/plain; charset=utf-8"), String.valueOf(jo));
            Request request = new Request.Builder().post(body).url(url + "/message?peer_id=" + localID + "&to=" + peerID).build();

            client.newCall(request).enqueue(new okhttp3.Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.d("http-IceCandidate", "failure");
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (response.isSuccessful()) {
                        result = response.body().string();
                        Log.d("http-IceCandidate", result);
                    }
                }
            });
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void sendOfferSessionDescription(SessionDescription sdp) throws JSONException {
        JSONObject jo = new JSONObject();
        jo.put("sdp", sdp.description);
        jo.put("type", "offer");
        //RequestBody body = RequestBody.create(MediaType.parse("application/json; charset=utf-8"), String.valueOf(jo));
        RequestBody body = RequestBody.create(MediaType.parse("text/plain; charset=utf-8"), String.valueOf(jo));
        Request request = new Request.Builder().post(body).url(url + "/message?peer_id=" + localID + "&to=" + peerID).build();

        client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.d("http-send-offer", "failure");
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    result = response.body().string();
                    Log.d("http-send-offer", result);
                }
            }
        });
    }

    public void sendAnswerSessionDescription(SessionDescription sdp) throws JSONException {
        JSONObject jo = new JSONObject();
        jo.put("sdp", sdp.description);
        jo.put("type", "answer");

        RequestBody body = RequestBody.create(MediaType.parse("text/plain; charset=utf-8"), String.valueOf(jo));
        Request request = new Request.Builder().post(body).url(url + "/message?peer_id=" + localID + "&to=" + peerID).build();

        client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.d("http-send-answer", "failure");
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    result = response.body().string();
                    Log.d("http-send-answer", result);
                }
            }
        });
    }

    public interface Callback {
        void onCreateRoom();
        void onPeerJoined();
        void onSelfJoined();
        void onPeerLeave(String msg);
        void onSelfLeave();
        void onOfferReceived(JSONObject data);
        void onAnswerReceived(JSONObject data);
        void onIceCandidateReceived(JSONObject data);
    }

}

