package net.nikcain.showmehills2;

import android.app.Activity;
import android.database.Cursor;
import android.database.SQLException;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

public class HillInfo extends Activity{

    private HillDatabase myDbHelper;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.mountaininfo);

        Bundle b = getIntent().getExtras();
        int hillid = b.getInt("key", 0);

        myDbHelper = new HillDatabase(this, getString(R.string.dbname), getString(R.string.dbpath));
        myDbHelper.createDataBase();

        // if database couldn't be created then we can't do much
        if (!myDbHelper.checkDataBase()) return;
        String qu = "select * from mountains where _id = '"+hillid+"'";
        Log.d("showmehills", "query: "+qu);
        Cursor cursor = myDbHelper.getReadableDatabase().rawQuery( qu, null);
        if (cursor.moveToFirst())
        {
            TextView t = (TextView) findViewById(R.id.hillname);
            String n = cursor.getString(cursor.getColumnIndex("name"));
            t.setText(n);

            t = (TextView) findViewById(R.id.hillheight);
            int h = cursor.getInt(cursor.getColumnIndex("height"));
            if (h>0)
            {
                t.setText(""+h+" meters");
            }
            else
            {
                t.setText("");
            }

            t = (TextView) findViewById(R.id.infolink);
            String webid = cursor.getString(cursor.getColumnIndex("webid"));
            int linktype = cursor.getInt(cursor.getColumnIndex("linktype"));
            switch (linktype) {
                case 1: // DoBH first link type
                    t.setText("web: http://www.hill-bagging.co.uk/mountaindetails.php?qu=S&rf=" + webid);
                    break;
                case 2: // DoBH second link type
                    t.setText("web: http://www.hill-bagging.co.uk/googlemaps.php?qu=S&rf=" + webid);
                    break;
                case 3: // OSM
                    t.setText("web: http://www.openstreetmap.org/browse/node/" + webid);
                    break;

                default:
                    break;
            }
        }
        else
        {
            Log.d("showmehills", "zero item count.");
        }
        cursor.close();
    }

    @Override
    protected void onResume() {
        super.onResume();

        try {
            myDbHelper.checkDataBase();
        }catch(SQLException sqle){
            throw sqle;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        try {
            myDbHelper.close();
        }catch(SQLException sqle){
            throw sqle;
        }
    }

    @Override
    protected void onStop()
    {
        try {
            myDbHelper.close();
        }catch(SQLException sqle){
            throw sqle;
        }
        super.onStop();
    }
}

