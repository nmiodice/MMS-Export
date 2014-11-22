package com.iodice.mmsexport;

import android.support.v7.app.ActionBarActivity;
import android.view.Menu;
import android.view.MenuItem;

public class BaseActionBarActivity extends ActionBarActivity {
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {

		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.mms, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();
		switch (id) {
			case R.id.action_settings:
				return true;
		    case android.R.id.home:
		        this.finish();
		        return true;
		    default:
		    	return super.onOptionsItemSelected(item);	
		}
	}
}
