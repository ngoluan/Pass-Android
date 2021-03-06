package luan.com.flippit.widget;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import java.util.ArrayList;

import luan.com.flippit.HistoryItem;
import luan.com.flippit.MyActivity;
import luan.com.flippit.R;

/**
 * Created by Luan on 2014-11-10.
 */
public class CustomWidgetService extends RemoteViewsService {
    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return (new ListViewFactory(this.getApplicationContext(), intent));
    }
}

class ListViewFactory implements RemoteViewsService.RemoteViewsFactory {
    static ArrayList<HistoryItem> historyItems = new ArrayList<HistoryItem>();
    private Context mContext = null;
    private int appWidgetId;

    public ListViewFactory(Context ctxt, Intent intent) {
        appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID);
        this.mContext = ctxt;
    }

    @Override
    public void onCreate() {
        Log.i(MyActivity.TAG, getClass().getName() + ": ListViewFactory created.");
        historyItems = WidgetProvider.historyItems;
        int[] appWidgetIds = new int[]{appWidgetId};
        if (historyItems.size() == 0) {
            WidgetProvider.mContext = mContext;
            WidgetProvider.getData(appWidgetIds);
        }
    }

    @Override
    public void onDataSetChanged() {
        historyItems = WidgetProvider.historyItems;
    }

    @Override
    public void onDestroy() {

    }

    @Override
    public int getCount() {
        return (historyItems.size());
    }

    @Override
    public RemoteViews getViewAt(int i) {
        RemoteViews row = new RemoteViews(mContext.getPackageName(), R.layout.row_history);
        String message = "";
        row.setTextViewText(R.id.dateTime, historyItems.get(i).dateTime);

        Log.i(MyActivity.TAG, getClass().getName() + ": " + "Drawing row " + i);
        if (historyItems.get(i).type.equals("text")) {
            message = historyItems.get(i).message;
            row.setViewVisibility(R.id.open, View.GONE);
            row.setViewVisibility(R.id.copy, View.VISIBLE);
        } else {
            if (historyItems.get(i).type.equals("file")) {
                message = "File transfer: " + historyItems.get(i).fileName;
            } else if (historyItems.get(i).type.equals("image")) {
                if (historyItems.get(i).bitmap == null) {
                    row.setTextViewText(R.id.message, "Image transfer: " + historyItems.get(i).fileName + "\nImage not available on device. Tap to download.");
                }
            }
            if (!historyItems.get(i).message.equals("")) {//attaches message to file or image transfer if a message exist
                message = message + "\n" + historyItems.get(i).message;
            }
            if (message.indexOf("\n") == 0) {//trims the new line character if message begins one. could happen if image posted without warning that you need to download it
                message = message.substring(1);
            }
            row.setViewVisibility(R.id.copy, View.GONE);
            row.setViewVisibility(R.id.open, View.VISIBLE);
        }
        row.setTextViewText(R.id.message, message);

        row.setImageViewBitmap(R.id.imageView, historyItems.get(i).bitmap);

        Bundle copyExtras = new Bundle();
        copyExtras.putInt("position", i);
        copyExtras.putString("action", "copy");
        Intent copyFillInIntent = new Intent();
        copyFillInIntent.putExtras(copyExtras);
        row.setOnClickFillInIntent(R.id.copy, copyFillInIntent);

        Bundle openExtras = new Bundle();
        openExtras.putInt("position", i);
        openExtras.putString("action", "open");
        Intent openFillInIntent = new Intent();
        openFillInIntent.putExtras(openExtras);
        row.setOnClickFillInIntent(R.id.open, openFillInIntent);

        Bundle shareExtras = new Bundle();
        shareExtras.putInt("position", i);
        shareExtras.putString("action", "share");
        Intent shareFillInIntent = new Intent();
        shareFillInIntent.putExtras(shareExtras);
        row.setOnClickFillInIntent(R.id.share, shareFillInIntent);

        Bundle deleteExtras = new Bundle();
        deleteExtras.putInt("position", i);
        deleteExtras.putString("action", "delete");
        Intent deleteFillInIntent = new Intent();
        deleteFillInIntent.putExtras(deleteExtras);
        row.setOnClickFillInIntent(R.id.delete, deleteFillInIntent);

        Bundle sendExtras = new Bundle();
        sendExtras.putInt("position", i);
        sendExtras.putString("action", "send");
        Intent sendFillInIntent = new Intent();
        sendFillInIntent.putExtras(sendExtras);
        row.setOnClickFillInIntent(R.id.send, sendFillInIntent);
        return row;
    }

    @Override
    public RemoteViews getLoadingView() {
        return null;
    }

    @Override
    public int getViewTypeCount() {
        return 1;
    }

    @Override
    public long getItemId(int i) {
        return i;
    }

    @Override
    public boolean hasStableIds() {
        return false;
    }

}
