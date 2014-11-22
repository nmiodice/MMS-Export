package com.iodice.mmsexport;

import java.util.ArrayList;

import android.app.ActionBar;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;

/**
 * Displays images from MMS messages, pulled directly from the
 * content://mms/part/<id> content provider
 * 
 * @author Nicholas M. Iodice
 * 
 */
public class ImageGridActivity extends BaseActionBarActivity {
	/**
	 * {@link ImageGridActivity#BITMAP_INTENT_ID} The ID used to retrieve an
	 * {@code ArrayList<String>} of bitmap IDs from the {@code Intent} that
	 * starts this activity. The IDs must correspond to images from the
	 * content://mms/part/<id> content provider
	 */
	public static String BITMAP_INTENT_ID = "bitmapID";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		ActionBar actionBar;
		ArrayList<String> imgIDs;
		Intent intent;
		ImageGridFragment imageGridFrag;

		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_image_grid);

		/* sets up ability to pass back to main activity */
		actionBar = getActionBar();
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
			actionBar.setHomeButtonEnabled(true);
		}

		/* receive the intent containing a bunch of image IDs */
		intent = getIntent();
		imgIDs = intent.getStringArrayListExtra(BITMAP_INTENT_ID);
		if (imgIDs == null)
			throw new NullPointerException("No image IDs supplied. These IDs "
					+ "must be supplied in the intent using the "
					+ "ImageGridActivity.BITMAP_INTENT_ID key");

		if (savedInstanceState == null) {
			imageGridFrag = new ImageGridFragment();
			imageGridFrag.supplyImageIDs(imgIDs);
			getFragmentManager().beginTransaction()
					.add(R.id.image_grid_container, imageGridFrag).commit();
		}
	}
}
