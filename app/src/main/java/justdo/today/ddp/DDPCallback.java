package justdo.today.ddp;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import im.delight.android.ddp.MeteorCallback;

/**
 * Delight-im DDP event callback handler
 * <p>
 * This call back has no side effects.
 * It just listen to DDP events, and fire respective internal App event broadcasts.
 * Each activity can choose what event broadcast to listen and act upon.
 * <p>
 * Created by Ryan Wong on 21/10/2016.
 */

public class DDPCallback implements MeteorCallback {

    private static DDPCallback sInstance = null;
    private final String TAG = "DDPCallBack";
    private final DBManager mDBManager;
    private final Context mContext;

    public DDPCallback(Context context) {
        mContext = context;
        mDBManager = DBManager.getsInstance(context);
    }

    /**
     * WebSocket connection established. If not signed in then we need to do so.
     */
    public void onConnect(boolean signedInAutomatically) {
        Log.d(TAG, "onConnect");

        // Broadcast the event
        Intent intent = new Intent(DDPIntents.CONNECTED);
        intent.putExtra(DDPExtras.SIGNED_IN_AUTOMATICALLY, signedInAutomatically);
        LocalBroadcastManager.getInstance(mContext).sendBroadcast(intent);
    }

    /**
     * WebSocket Disconnected. We need to either auto-resign in or back to sign in screen
     */
    public void onDisconnect() {
        Log.d(TAG, "onDisconnect");

        // Broadcast the event
        Intent intent = new Intent(DDPIntents.DISCONNECTED);
        LocalBroadcastManager.getInstance(mContext).sendBroadcast(intent);
    }

    /**
     * onDataAdded - Unmerged publications (tasks) does not call onDataAdded - all goes to onDataChanged
     *
     * @param collectionName the name of the collection that the document is added to
     * @param documentId
     * @param newValuesJson  the new fields of the document as a JSON string
     */
    public void onDataAdded(String collectionName, String documentId, String newValuesJson) {
        // Log.d(TAG, "onDataAdded: " + collectionName);
        mDBManager.onDataAdded(collectionName, documentId, newValuesJson);
    }

    /**
     * onDataChanged - not for unmerged publications (tasks) we need to check if onDataAdded
     *
     * @param collectionName    the name of the collection that the document is changed in
     * @param documentId
     * @param updatedValuesJson the modified fields of the document as a JSON string
     * @param removedValuesJson the deleted fields of the document as a JSON string
     */
    public void onDataChanged(String collectionName, String documentId, String updatedValuesJson, String removedValuesJson) {
        // Log.d(TAG, "onDataChanged: " + collectionName + updatedValuesJson);
        mDBManager.onDataChanged(collectionName, documentId, updatedValuesJson, removedValuesJson);
    }

    public void onDataRemoved(String collectionName, String documentId) {
        Log.d(TAG, "onDataRemoved");
        mDBManager.onDataRemoved(collectionName, documentId);
    }

    public void onException(Exception ex) {
        ex.printStackTrace();

        // Broadcast the event
        Log.e("DDPCallback", "exception received. Broadcasting EXCEPTION");
        Intent intent = new Intent(DDPIntents.EXCEPTION);
        intent.putExtra(DDPExtras.MESSAGE, ex.getMessage());
        intent.putExtra(DDPExtras.EXCEPTION, ex.toString());
        LocalBroadcastManager.getInstance(mContext).sendBroadcast(intent);
    }
}
