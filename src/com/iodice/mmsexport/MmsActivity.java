package com.iodice.mmsexport;

import android.os.Bundle;

public class MmsActivity extends BaseActionBarActivity {
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_mms);

		if (savedInstanceState == null)
			getFragmentManager().beginTransaction()
					.add(R.id.mms_container, new MmsFragment()).commit();
	}
}
