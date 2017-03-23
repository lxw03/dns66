package org.jak_linux.dns66.db;

import android.app.ProgressDialog;
import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import org.jak_linux.dns66.Configuration;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * Created by jak on 23/03/17.
 */

public class DatabaseUpdateTask extends AsyncTask<Configuration, String, Void> {

    private static final String TAG = "DatabaseUpdateTask";
    RuleDatabase database;
    Context context;
    ProgressDialog progressDialog;

    public DatabaseUpdateTask(Context context, RuleDatabase database) {
        this.database = database;
        this.context = context;
    }

    @Override
    protected Void doInBackground(Configuration... configurations) {
        long priority = -1;
        for (Configuration.Item hostfile : configurations[0].hosts.items) {
            priority++;
            publishProgress(hostfile.location);

            if (hostfile.state == Configuration.Item.STATE_IGNORE)
                continue;
            database.database.beginTransactionNonExclusive();

            try {
                database.database.delete("ruleset", "id = ?", new String[]{Long.toString(hostfile.id)});
                if (!database.createOrUpdateItem(hostfile, priority))
                    throw new IOException("Cannot create hostfile");
                if (!hostfile.location.contains("/")) {
                    database.addHost(hostfile, hostfile.location);
                    database.database.setTransactionSuccessful();
                } else {

                    URL url = new URL(hostfile.location);
                    if (database.loadReader(hostfile, new InputStreamReader(url.openStream())))
                        database.database.setTransactionSuccessful();
                }
            } catch (MalformedURLException e) {
                Log.w(TAG, "doInBackground: Could not update", e);
            } catch (InterruptedException e) {
                Log.d(TAG, "doInBackground: Interrupted", e);
                break;
            } catch (IOException e) {
                Log.w(TAG, "doInBackground: Could not update", e);
            } finally {
                database.database.endTransaction();
            }
        }
        return null;
    }

    @Override
    protected void onProgressUpdate(String... values) {
        super.onProgressUpdate(values);

        if (progressDialog != null)
            progressDialog.setMessage("Updating " + values[0]);
    }

    @Override
    protected void onPostExecute(Void aVoid) {
        super.onPostExecute(aVoid);
        if (progressDialog != null)
            progressDialog.dismiss();
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
        if (context.getApplicationContext() != context)
            progressDialog = ProgressDialog.show(context, "Updating host file", "Doing stuff. Please wait...", true);
    }
}
