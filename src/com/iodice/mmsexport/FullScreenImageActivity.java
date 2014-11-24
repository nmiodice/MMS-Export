package com.iodice.mmsexport;

import java.util.List;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.view.GestureDetectorCompat;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
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
	/* string uri format used to query image */
	public static final String BITMAP_URI_FORMAT = "BITMAP_URI_FORMAT";
	/* list of string image IDs */
	public static final String BITMAP_IMAGE_ID_LIST = "BITMAP_IMAGE_ID_LIST";
	/* index into the list of the first image to be shown */
	public static final String BITMAP_IDX_TO_SHOW = "BITMAP_IDX_TO_SHOW";

	private int mCurrIdx;
	private List<String> mImgIDs;
	private String mUriFormat;
	/* detects swipes and loads new images appropriately */
	private GestureDetectorCompat mGestureDetector;
	private int mShortAnimationDuration;

	protected void onCreate(Bundle savedInstanceState) {
		Intent intent;

		super.onCreate(savedInstanceState);
		/* go full screen */
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
				WindowManager.LayoutParams.FLAG_FULLSCREEN);

		setContentView(R.layout.activity_full_screen_image);
		mGestureDetector = new GestureDetectorCompat(this, new SwipeDetector());
		mShortAnimationDuration = getResources().getInteger(
				R.integer.image_fade_duration);

		intent = getIntent();
		mCurrIdx = intent.getIntExtra(BITMAP_IDX_TO_SHOW, -1);
		mImgIDs = intent.getStringArrayListExtra(BITMAP_IMAGE_ID_LIST);
		mUriFormat = intent.getStringExtra(BITMAP_URI_FORMAT);
		loadCurrentSelection();
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		mGestureDetector.onTouchEvent(event);
		return super.onTouchEvent(event);
	}

	private void loadCurrentSelection() {
		ImageLoaderTask loaderTask;
		String bitmapUri = String.format(mUriFormat, mImgIDs.get(mCurrIdx));

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

		/**
		 * Swaps the image using a nice little animated fade
		 */
		protected void onPostExecute(Bitmap bitmap) {
			final ImageView foreground = (ImageView) mActivity
					.findViewById(R.id.full_screen_image_foreground);
			final ImageView background = (ImageView) mActivity
					.findViewById(R.id.full_screen_image_background);
			Drawable visibleImg = foreground.getDrawable();
			
			if (visibleImg != null)
				background.setImageDrawable(visibleImg);
			background.setAlpha(1f);
			background.setVisibility(View.VISIBLE);
			
			foreground.setAlpha(0f);
			foreground.setImageBitmap(bitmap);
			foreground.setVisibility(View.VISIBLE);
			
			background.animate().
				alpha(0f).
				setDuration(mShortAnimationDuration).
				setListener(new AnimatorListenerAdapter() {
	                @Override
	                public void onAnimationEnd(Animator animation) {
	                	background.setVisibility(View.GONE);
	                }
	            });
			
			foreground.animate().
				alpha(1f).
				setDuration(mShortAnimationDuration).
				setListener(null);
		}
	}

	class SwipeDetector extends GestureDetector.SimpleOnGestureListener {
		@Override
		public boolean onFling(MotionEvent e1, MotionEvent e2, float velX,
				float velyY) {
			boolean loadNewImage = false;
			int maxIdx = mImgIDs.size() - 1;

			if (Math.abs(velX) < 500)
				return false;

			if (velX < 0) {
				if (mCurrIdx < maxIdx) {
					mCurrIdx++;
					loadNewImage = true;
				}
			} else {
				if (mCurrIdx >= 1) {
					mCurrIdx--;
					loadNewImage = true;
				}
			}
			if (loadNewImage) {
				loadCurrentSelection();
				return true;
			} else
				return false;
		}
	}
}
