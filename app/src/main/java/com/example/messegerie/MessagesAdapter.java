package com.example.messegerie;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;
public class MessagesAdapter extends RecyclerView.Adapter<MessagesAdapter.MessageViewHolder> {
    private List<Message> messages;
    private String myName;
    public MessagesAdapter(List<Message> messages, String myName) {
        this.messages = messages;
        this.myName = myName;
    }
    @NonNull
    @Override
    public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.message_item, parent, false);
        return new MessageViewHolder(view);
    }
    @Override
    public void onBindViewHolder(@NonNull MessageViewHolder holder, int position) {
        Message message = messages.get(position);
        if (message.isSent()) {
            holder.layoutSent.setVisibility(View.VISIBLE);
            holder.layoutReceived.setVisibility(View.GONE);
            holder.tvMessageSent.setText(message.getText());
            holder.tvTimeSent.setText(message.getTime());
        } else {
            holder.layoutSent.setVisibility(View.GONE);
            holder.layoutReceived.setVisibility(View.VISIBLE);
            holder.tvSender.setText(message.getSender());
            holder.tvMessageReceived.setText(message.getText());
            holder.tvTimeReceived.setText(message.getTime());
        }
    }
    @Override
    public int getItemCount() {
        return messages.size();
    }
    static class MessageViewHolder extends RecyclerView.ViewHolder {
        View layoutReceived, layoutSent;
        TextView tvSender, tvMessageReceived, tvTimeReceived;
        TextView tvMessageSent, tvTimeSent;
        public MessageViewHolder(@NonNull View itemView) {
            super(itemView);
            layoutReceived = itemView.findViewById(R.id.layoutReceived);
            layoutSent = itemView.findViewById(R.id.layoutSent);
            tvSender = itemView.findViewById(R.id.tvSender);
            tvMessageReceived = itemView.findViewById(R.id.tvMessageReceived);
            tvTimeReceived = itemView.findViewById(R.id.tvTimeReceived);
            tvMessageSent = itemView.findViewById(R.id.tvMessageSent);
            tvTimeSent = itemView.findViewById(R.id.tvTimeSent);
        }
    }
}