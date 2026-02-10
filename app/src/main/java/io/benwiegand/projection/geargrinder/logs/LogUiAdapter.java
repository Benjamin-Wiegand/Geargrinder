package io.benwiegand.projection.geargrinder.logs;

import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Arrays;

public class LogUiAdapter extends RecyclerView.Adapter<LogUiAdapter.LineViewHolder> implements LogcatReader.UiLogListener {
    public static final int HISTORY_SIZE = 2000;  // lines

    private record Line(int color, String text) {}

    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Line[] lineBuffer = new Line[HISTORY_SIZE];
    private int lineIndex = 0;
    private int lineNumber = 0;

    public LogUiAdapter() {
        Arrays.fill(lineBuffer, new Line(Color.WHITE, "---"));
    }

    private Line getLine(int lineNumber) {
        synchronized (lineBuffer) {
            int reverseIndex = this.lineNumber - lineNumber - 1;
            int index = lineIndex - reverseIndex - 1;
            if (index < 0)
                index = lineBuffer.length + index;

            if (index < 0) return new Line(Color.GRAY, "~");

            return lineBuffer[index];
        }
    }

    @Override
    public void onLog(String level, String text) {
        int color = switch (level) {
            case "F", "E" -> Color.RED;
            case "W" -> Color.YELLOW;
            case "I" -> Color.CYAN;
            case "V" -> Color.GREEN;
            case "D" -> Color.MAGENTA;
            case null, default -> Color.WHITE;
        };

        Line line = new Line(color, text);

        handler.post(() -> {
            synchronized (lineBuffer) {
                lineBuffer[lineIndex++] = line;
                if (lineIndex >= HISTORY_SIZE) lineIndex = 0;
                int thisLineNumber = lineNumber++;

                notifyItemInserted(thisLineNumber);
            }
        });
    }

    @NonNull
    @Override
    public LineViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        TextView lineView = new TextView(parent.getContext());
        lineView.setTypeface(Typeface.MONOSPACE);
        lineView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 7);
        return new LineViewHolder(lineView);
    }

    @Override
    public void onBindViewHolder(@NonNull LineViewHolder holder, int position) {
        TextView lineView = holder.getLineView();
        Line line = getLine(position);

        lineView.setText(line.text());
        lineView.setTextColor(line.color());
    }

    @Override
    public int getItemCount() {
        return lineNumber;
    }

    public static class LineViewHolder extends RecyclerView.ViewHolder {
        private final TextView lineView;

        public LineViewHolder(@NonNull TextView lineView) {
            super(lineView);
            this.lineView = lineView;
        }

        public TextView getLineView() {
            return lineView;
        }
    }
}
