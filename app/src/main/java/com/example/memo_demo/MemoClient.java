package com.example.memo_demo;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.Button;

import com.example.wifi.GroupListener;
import com.example.wifi.SocketConfig;
import com.example.wifi.SocketListener;
import com.example.wifi.SocketThread;
import com.example.wifi.UdpClientThread;

import java.net.InetAddress;



public class MemoClient extends EditorActivity {
    private UdpClientThread mUdpThread;
    private SocketThread mClient;
    private boolean mConnected = false;

                private final GroupListener mGroupListener = new GroupListener() {
        @Override
        public void onGroupHostConnect(InetAddress hostAddress, String user) {
            log(String.format("group connect %s,%s", user, hostAddress.getHostAddress()));
            mConnected = true;

            if (mClient == null || mClient.isClosed()) {
                mClient = new SocketThread(hostAddress, new SocketListener() {
                    @SuppressLint("DefaultLocale")
                    @Override
                    public void onAdded(SocketThread socketThread) {
                        log(String.format("Socket add: %s %d", socketThread.getHostAddress(), socketThread.getPort()));
                    }

                    @SuppressLint("DefaultLocale")
                    @Override
                    public void onRemoved(SocketThread socketThread) {
                        if (mClient != null) {
                            mClient.write(StringProcessor.statusToByteArray("client stop relay by remove itself\n"));
                            mClient.write(StringProcessor.htmlToByteArray(getEditText()));
                        }

                        log(String.format("Socket remove: %s %d", socketThread.getHostAddress(), socketThread.getPort()));
                    }

                    @Override
                    public void onRead(final SocketThread socketThread, final byte[] message) {
                        MemoClient.this.runOnUiThread(() -> {
                            ReturnMessage ret = StringProcessor.decodeByteArray(message);
                            log("[" + ret.type + "] " + ret.data);

                            //mClient.write(StringProcessor.statusToByteArray("client gg\n"));

                            switch (ret.type) {
                                case StringProcessor.editor:
                                    updateEditText(ret.data, MEMO_SET_TYPE.MEMO_TEXT_SET);
                                    break;
                                case StringProcessor.clipResult:
                                    updateStatusText(getPrefix() + "got clip: [" + ret.data + "]\n", MEMO_SET_TYPE.MEMO_TEXT_APPEND);
                                    copyToClipBoard(ret.data);
                                    break;
                                case StringProcessor.status:
                                    //updateStatusText(ret.data, MEMO_SET_TYPE.MEMO_TEXT_APPEND);
                                    updateStatusText(getPrefix() + "got relay\n", MEMO_SET_TYPE.MEMO_TEXT_APPEND);
                                    mClient.write(StringProcessor.statusToByteArray("end relay\n"));
                                    break;

                            }

                        });
                    }
                });
                mClient.start();
                log("client started");
            }
        }

        @Override
        public void obGroupHostDisConnect(InetAddress hostAddress, String user) {
            if (!mConnected) return;

            log(String.format("group disconnect %s,%s", user, hostAddress.getHostAddress()));

            mConnected = false;
            disconnect();

            if (mClient != null) {
                mClient.close();
                mClient = null;
            }
        }

        @Override
        public void onGroupClientConnect(InetAddress clientAddress, String user) {

        }

        @Override
        public void onGroupClientDisConnect(InetAddress clientAddress, String user) {

        }

        @Override
        public void onSocketFailed() {
            MemoClient.this.runOnUiThread(new Runnable() {

                @Override
                public void run() {
                    MemoClient.this.stop();
                }
            });
        }
    };


    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        init(getIntent());
        setTitle("Memo " + mMode);

        mUdpThread = new UdpClientThread(getUser(), this);
        mUdpThread.setListener(mGroupListener);
        mUdpThread.start();

        mEditor.setOnTextChangeListener(text -> {
            String msg = getEditText();
            if (mClient != null) {
                //mClient.write(StringProcessor.statusToByteArray("client send back\n"));
                log("client send back\n");
                mClient.write(StringProcessor.htmlToByteArray(msg));
            }
        });

        final Button waitBtn = findViewById(R.id.start_relay);
        waitBtn.setOnClickListener(v -> {
            waitBtn.setText("WAITING");
            waiting();
        });
        waitBtn.setText("WAIT");
        Button stopBtn = findViewById(R.id.stop_relay);
        stopBtn.setOnClickListener(v -> stop());
        stopBtn.setText("END");
        Button writeTo = findViewById(R.id.write_to);
        writeTo.setOnClickListener(v -> {
            if (mClient != null) {
                mClient.write(StringProcessor.clipRequestToByteArray());
            }
        });
        writeTo.setText("getServerClip");

        Button showList = findViewById(R.id.show_list);
        ((ViewGroup) showList.getParent()).removeView(showList);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        stop();
    }

    private void disconnect() {
        mUdpThread.leaveGroup();
    }

    private void discover() {
        mUdpThread.joinGroup();
    }

    protected void waiting() {
        updateStatusText(getPrefix() + "wait relay\n", MEMO_SET_TYPE.MEMO_TEXT_APPEND);

//        mFirstMsg = true;
        discover();
        log("start finished");

    }

    protected void stop() {
//        mFirstMsg = true;
        updateStatusText(getPrefix() + "stop relay\n", MEMO_SET_TYPE.MEMO_TEXT_APPEND);
        if (mClient != null) {
            mClient.write(StringProcessor.statusToByteArray("client stop relay\n"));
            mClient.write(StringProcessor.htmlToByteArray(getEditText()));
        }
        disconnect();
        if (mClient != null) {
            mClient.close();
            mClient = null;
        }
    }
}
