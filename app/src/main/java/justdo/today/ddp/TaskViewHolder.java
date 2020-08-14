package justdo.today.ddp;

import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

public class TaskViewHolder extends RecyclerView.ViewHolder {
    public final TextView itemTextView;

    public TaskViewHolder(@NonNull View itemView) {
        super(itemView);
        itemTextView = itemView.findViewById(R.id.listitem_textview);
    }
}
