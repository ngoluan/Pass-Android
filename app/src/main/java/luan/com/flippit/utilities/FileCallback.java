package luan.com.flippit.utilities;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import java.util.ArrayList;

import luan.com.flippit.GeneralUtilities;
import luan.com.flippit.HistoryItem;
import luan.com.flippit.MyActivity;
import luan.com.flippit.R;
import luan.com.flippit.UpdateHistory;

/**
 * Created by Luan on 2014-11-13.
 */
public class FileCallback extends DownloadInterface {
    public FileCallback(Context context) {
        super(context);
    }

    public void callBackProgress(int progress) {
        mBuilder.setProgress(100, progress, false);
        mNotificationManager.notify(1, mBuilder.build());
    }

    public void callBackFinish(Bundle extras) {
        Bitmap bitmap = null;
        String fileName = extras.getString("filename");
        String savedPath = extras.getString("savedPath");

        Log.i(MyActivity.TAG, context.getClass().getName() + ": " + "Readying notification.");

        String mimeType = GeneralUtilities.getMimeType(fileName);
        Log.i(MyActivity.TAG, context.getClass().getName() + ": " + "Mime type: " + mimeType);

        if (mimeType.contains("image")) {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inPreferredConfig = Bitmap.Config.RGB_565;
            bitmap = DecodeSampledBitmapFromPath.decodeSampledBitmapFromPath(savedPath, 1000, 1000, options);
        }


        Uri hacked_uri = Uri.parse("file://" + Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS) + "/" + fileName);

        Log.i(MyActivity.TAG, context.getClass().getName() + ": " + "Hacked URI: " + hacked_uri.toString());

        MediaScannerConnection.scanFile(context, new String[]{savedPath}, null, null);

        Intent intentOpen = new Intent();
        intentOpen.setAction(Intent.ACTION_VIEW);
        intentOpen.setDataAndType(hacked_uri, mimeType);
        PendingIntent pendingOpen = PendingIntent.getActivity(context, 0, intentOpen, PendingIntent.FLAG_UPDATE_CURRENT);

        Intent intentShare = new Intent();
        intentShare.setAction(Intent.ACTION_SEND);
        intentShare.putExtra(Intent.EXTRA_STREAM, hacked_uri);
        intentShare.setType(mimeType);
        PendingIntent pendingShare = PendingIntent.getActivity(context, 0, intentShare, PendingIntent.FLAG_UPDATE_CURRENT);

        Intent intentFolder = new Intent(Intent.ACTION_GET_CONTENT);

        intentFolder.setDataAndType(hacked_uri, "text/csv");
        PendingIntent pendingFolder = PendingIntent.getActivity(context, 0, intentFolder, PendingIntent.FLAG_UPDATE_CURRENT);

        Log.i(GeneralUtilities.TAG, getClass().getName() + ": " + "Firing notification.");

        mBuilder.setContentText("Download complete")
                .addAction(R.drawable.folder_white, "Folder", pendingFolder)
                .addAction(R.drawable.share_white, "Share", pendingShare)
                .addAction(R.drawable.open_white, "Open", pendingOpen)
                .setProgress(0, 0, false)
                .setTicker("Download complete")
                .setContentText("Download complete");

        if (bitmap != null) {
            mBuilder.setLargeIcon(bitmap)
                    .setStyle(new NotificationCompat.BigPictureStyle()
                            .bigPicture(bitmap));
        }

        mNotificationManager.cancel(1);
        mNotificationManager.notify(1, mBuilder.build());

        Log.i(MyActivity.TAG, getClass().getName() + ": " + "Updating history. ");
        UpdateHistory updateHistory = new UpdateHistory();
        updateHistory.updateHistory(context);

    }

    @Override
    public void callBackFinish(ArrayList<HistoryItem> historyItems) {

    }
}
