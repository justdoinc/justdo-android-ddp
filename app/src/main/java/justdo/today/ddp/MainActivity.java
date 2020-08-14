package justdo.today.ddp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import im.delight.android.ddp.Fields;
import im.delight.android.ddp.Meteor;
import im.delight.android.ddp.ResultListener;
import im.delight.android.ddp.SubscribeListener;

public class MainActivity extends AppCompatActivity {

    private final String TAG = "MainActivity";
    private final int DDP_BATCH_UPDATE_INTERVAL = 250; // 250ms
    private final String SIGNIN_EMAIL = "ryan@rwmobi.com";
    private final String SIGNIN_PASSWORD = "EssETemp0RAute!";
    private TextView mStatusTextView;
    private TextView mDateTimeTextView;
    private RecyclerView mRecyclerView;
    private TaskAdapter mTaskAdapter;
    private Handler mHandler = null;
    private boolean mIsDDPReady = false;
    private Meteor mMeteor;
    private SimpleDateFormat mDateFormat = new SimpleDateFormat("HH:mm:ss");
    private String mSubscriptionID;
    private DBManager mDBManager;

    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (action == null) {
                return;
            }

            switch (action) {
                case DDPIntents.CONNECTED:
                    // Block the auto-reconnect bug
                    if (mIsDDPReady) {
                        return;
                    }
                    mStatusTextView.setText("Signing in");
                    boolean signedInAutomatically = intent.getBooleanExtra(DDPExtras.SIGNED_IN_AUTOMATICALLY, false);
                    if (!signedInAutomatically) {
                        signIn();
                    } else {
                        initSubscriptions();
                    }

                    break;
                case DDPIntents.EXCEPTION: {
                    mStatusTextView.setText(intent.getStringExtra(DDPExtras.MESSAGE));

                    AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this, R.style.CustomizedAlertDialog);
                    builder.setMessage(intent.getStringExtra(DDPExtras.MESSAGE))
                            .setTitle("DDP Exception")
                            .setPositiveButton("GOT IT", (dialog, id) -> dialog.dismiss());
                    try {
                        builder.show();
                    } catch (WindowManager.BadTokenException ex) {
                        // Activity is no longer available so the dialog has no need to show anymore.
                    }

                    break;
                }
                case DDPIntents.SIGNIN_ERROR:
                    mStatusTextView.setText("Sign in error");

                    break;
                case DDPIntents.DISCONNECTED: {
                    mStatusTextView.setText("Disconnected");

                    AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this, R.style.CustomizedAlertDialog);
                    builder.setMessage("DDP Disconnected")
                            .setPositiveButton("GOT IT", (dialog, id) -> dialog.dismiss());
                    try {
                        builder.show();
                    } catch (WindowManager.BadTokenException ex) {
                        // Activity is no longer available so the dialog has no need to show anymore.
                    }

                    break;
                }
                case DDPIntents.SIGNIN_SUCCESS:
                    mStatusTextView.setText("Signed in");
                    initSubscriptions();

                    break;
                case DDPIntents.DATA_ADDED:
                case DDPIntents.DATA_CHANGED:
                case DDPIntents.DATA_REMOVED:
                    requestIntervalUpdate();

                    break;
                case DDPIntents.READY:
                    mIsDDPReady = true;
                    requestIntervalUpdate();
                    break;
            }
        }
    };

    public MainActivity() {
    }

    private IntentFilter getIntentFilter() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(DDPIntents.CONNECTED);
        intentFilter.addAction(DDPIntents.DISCONNECTED);
        intentFilter.addAction(DDPIntents.RECONNECTING);
        intentFilter.addAction(DDPIntents.SIGNIN_SUCCESS);
        intentFilter.addAction(DDPIntents.EXCEPTION);
        intentFilter.addAction(DDPIntents.READY);
        intentFilter.addAction(DDPIntents.DATA_ADDED);
        intentFilter.addAction(DDPIntents.DATA_CHANGED);
        intentFilter.addAction(DDPIntents.DATA_REMOVED);
        return intentFilter;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        mStatusTextView = findViewById(R.id.main_textview);

        mTaskAdapter = new TaskAdapter(this);
        mRecyclerView = findViewById(R.id.main_recyclerview);
        mRecyclerView.setAdapter(mTaskAdapter);
        mRecyclerView.setHasFixedSize(true);
        mRecyclerView.addItemDecoration(new RecyclerViewItemDecoration.Builder(this)
                .setHeight(R.dimen.default_divider_height)
                .setPadding(R.dimen.default_divider_padding)
                .setColorResource(R.color.defaultDividerColor)
                .build());
        mRecyclerView.setLayoutManager(new LinearLayoutManager(this));

        mDateTimeTextView = findViewById(R.id.main_datetime);
        updateClock();

        mDBManager = DBManager.getsInstance(this);
        try {
            mDBManager.initDataBase();
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        mMeteor = new Meteor(this, "wss://app-alpha.justdo.com/websocket");
        mMeteor.addCallback(new DDPCallback(this));
        connect();
    }

    @Override
    protected void onResume() {
        super.onResume();
        try {
            LocalBroadcastManager.getInstance(this).registerReceiver(mBroadcastReceiver, getIntentFilter());
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    @Override
    public void onPause() {
        try {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(mBroadcastReceiver);
        } catch (IllegalArgumentException ex) {
            ex.printStackTrace();
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (mMeteor.isConnected() && mMeteor.isLoggedIn()) {
            try {
                mMeteor.logout();
                mMeteor.disconnect();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        if (id == R.id.action_reload) {
            if (mMeteor == null) {
                return true;
            }

            mIsDDPReady = false;
            if (mMeteor.isConnected()) {
                if (mMeteor.isLoggedIn()) {
                    if (mSubscriptionID != null) {
                        // Ensure if we reuse existing session we start with a new subscription
                        mMeteor.unsubscribe(mSubscriptionID);
                    }
                    subTasksGridUM();
                } else {
                    signIn();
                }
            } else {
                mMeteor.reconnect();
            }
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void connect() {
        if (!mMeteor.isConnected()) {
            try {
                mStatusTextView.setText("Connecting");
                mMeteor.connect();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        } else {
            // If already connected, proceed to sign in
            Intent intent = new Intent(DDPIntents.CONNECTED);
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
        }
    }

    private void signIn() {
        final Map<String, Object> userData = new HashMap<>();
        userData.put("email", SIGNIN_EMAIL);

        final Map<String, Object> authData = new HashMap<>();
        authData.put("user", userData);
        authData.put("password", SIGNIN_PASSWORD);

        mMeteor.call("login", new Object[]{authData}, new ResultListener() {
            @Override
            public void onSuccess(String result) {
                // Broadcast the event
                Intent intent = new Intent(DDPIntents.SIGNIN_SUCCESS);
                intent.putExtra(DDPExtras.SIGNIN_RESULT, result);
                LocalBroadcastManager.getInstance(MainActivity.this).sendBroadcast(intent);
            }

            @Override
            public void onError(String error, String reason, String details) {
                mStatusTextView.setText(String.format(Locale.US, "Sign in error: %s", reason));
            }
        });
    }

    /**
     * Avoid DDP events calling refresh() directly.
     */
    private void requestIntervalUpdate() {
        // If there is already a scheduled UI refresh queued, we can safely wait for that.
        // Otherwise we schedule one.
        if (mHandler == null) {
            mHandler = new Handler();
            mHandler.postDelayed(() -> {
                refresh();
                mHandler = null;
            }, DDP_BATCH_UPDATE_INTERVAL);
        }
    }

    private void updateClock() {
        new Handler().postDelayed(() -> {
            mDateTimeTextView.setText(mDateFormat.format(System.currentTimeMillis()));
            updateClock();
        }, 1000);
    }

    private void initSubscriptions() {
        mDBManager.beginTransaction();
        mMeteor.subscribe("meteor.loginServiceConfiguration");
        mMeteor.subscribe("jdcSubscribedUnreadChannelsCount", new Object[]{null});
        mMeteor.subscribe("jdcBotsInfo", new Object[]{null});
        mMeteor.subscribe("userProjects", new Object[]{true});
        mMeteor.subscribe("userProjects", new Object[]{false}, new SubscribeListener() {
            @Override
            public void onSuccess() {
                mDBManager.endCommitTransaction();
                subTasksGridUM();
            }

            @Override
            public void onError(String error, String reason, String details) {
                mDBManager.endRollbackTransaction();
            }
        });
    }

    private void subTasksGridUM() {
        mStatusTextView.setText("Subscribing JustDo");

        Fields configuration = new Fields();
        configuration.put("project_id", "DgMB9enPQsCJEmupc"); // DgMB9enPQsCJEmupc = 23.5k tasks
        configuration.put("get_parents_as_string", true);  // v1.15

        Fields publicationOptions = new Fields();
        publicationOptions.put("custom_col_name", "tasks"); // Workaround

        // Clear tasks first
        mTaskAdapter.clear();

        mDBManager.beginTransaction();
        mSubscriptionID = mMeteor.subscribe("tasks_grid_um", new Fields[]{configuration, publicationOptions}, new SubscribeListener() {
            @Override
            public void onSuccess() {
                mDBManager.endCommitTransaction();
                Intent intent = new Intent(DDPIntents.READY);
                LocalBroadcastManager.getInstance(MainActivity.this).sendBroadcast(intent);
            }

            @Override
            public void onError(String error, String reason, String details) {
                mDBManager.endRollbackTransaction();
                mStatusTextView.setText(reason);
            }
        });
    }

    /**
     * Updates triggered by DDP events should call requestIntervalUpdate() instead
     */
    private void refresh() {
        // update status bar
        mTaskAdapter.updateDataSet();

        if (mIsDDPReady) {
            mStatusTextView.setText(String.format(Locale.US, "Ready. Loaded %d tasks.", mTaskAdapter.getItemCount()));
        } else {
            mStatusTextView.setText(String.format(Locale.US, "Loading %d tasks", mTaskAdapter.getItemCount()));
        }
    }
}