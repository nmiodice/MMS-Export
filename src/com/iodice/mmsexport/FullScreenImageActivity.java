package com.iodice.mmsexport;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;

/**
 * A simple activity that shows an image full screen. A string URI path to image
 * must be passed in through an intent using the BITMAP_URI flag
 * 
 * @author Nicholas M. Iodice
 * 
 */
public class FullScreenImageActivity extends Activity {
	public static final String BITMAP_URI = "BITMAP_URI";

	protected void onCreate(Bundle savedInstanceState) {
		Intent intent;
		String bitmapUri;
		ImageLoaderTask loaderTask;

		super.onCreate(savedInstanceState);
		/* go full screen */
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
				WindowManager.LayoutParams.FLAG_FULLSCREEN);

		setContentView(R.layout.activity_full_screen_image);
		intent = getIntent();
		bitmapUri = intent.getStringExtra(BITMAP_URI);
		loaderTask = new ImageLoaderTask();
		loaderTask.mActivity = this;
		loaderTask.execute(bitmapUri);
	}

	/**
	 * Given a string URI path to an image, load the full resolution content and
	 * display it in the activity's ImageView
	 * 
	 * @author Nicholas M. Iodice
	 * 
	 */
	class ImageLoaderTask extends AsyncTask<String, Void, Bitmap> {
		public Activity mActivity;

		@Override
		protected Bitmap doInBackground(String... args) {
			String bitmapUri = args[0];
			return ImageUtils.getImageFromContentProvider(bitmapUri, false, 0,
					mActivity);
		}

		protected void onPostExecute(Bitmap bitmap) {
			ImageView imageView = (ImageView) mActivity
					.findViewById(R.id.full_screen_image);
			imageView.setImageBitmap(bitmap);
		}
	}
}
