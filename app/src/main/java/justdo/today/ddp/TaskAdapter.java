package justdo.today.ddp;

import android.app.Activity;
import android.database.Cursor;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Locale;

import im.delight.android.ddp.Fields;

public class TaskAdapter extends RecyclerView.Adapter<TaskViewHolder> {
    public final static String TAG = "TaskAdapter";
    private final LayoutInflater mInflater;
    private final DBManager mDBManager;
    private ArrayList<String> mTaskIds = new ArrayList<>();

    public TaskAdapter(Activity activity) {
        mInflater = LayoutInflater.from(activity);
        mDBManager = DBManager.getsInstance(activity);
    }

    @NonNull
    @Override
    public TaskViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = mInflater.inflate(R.layout.listitem, parent, false);
        return new TaskViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TaskViewHolder holder, int position) {
        String taskId = mTaskIds.get(position);
        Fields fields = mDBManager.getDocumentFields("tasks", taskId);

        if (fields == null) {
            Log.e(TAG, String.format(Locale.US, "Failed to retrieve task %s", taskId));
            return;
        }

        try {
            // For demonstration simply print out the task seqId and name
            String taskName = (String) fields.get("title");
            int seqId = (int) fields.get("seqId");
            holder.itemTextView.setText(String.format(Locale.US, "[%d] %s", seqId, taskName));
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public void updateDataSet() {
        Cursor cursor = null;
        try {
            cursor = mDBManager.getRawCursor("SELECT `documentId` FROM `ddpdefault` WHERE `collectionName` = ?", new String[]{"tasks"});

            mTaskIds.clear();

            while (cursor.moveToNext()) {
                try {
                    mTaskIds.add(cursor.getString(0));
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        } finally {
            if (cursor != null) {
                cursor.close();
            }
            notifyDataSetChanged();
        }
    }

    public void clear() {
        mDBManager.execSQL("DELETE FROM `ddpdefault` WHERE `collectionName` = 'tasks'");
        updateDataSet();
    }

    @Override
    public int getItemCount() {
        return mTaskIds.size();
    }
}
