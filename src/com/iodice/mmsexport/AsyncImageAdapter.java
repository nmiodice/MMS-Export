package com.iodice.mmsexport;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;

/**
 * This adapter class handles loading images asynchronously from the mms content
 * provider at "content://mms/part" where each image is located at
 * "content://mms/part/xxx".Images are loaded asynchronously, and they are
 * stored in an LRU cache
 * 
 * Code was borrowed heavily from examples from the Google Android resources:
 * http://developer.android.com/training/displaying-bitmaps/index.html
 * http://developer.android.com/training/displaying-bitmaps/cache-bitmap.html
 * 
 * @author Nicholas M. Iodice
 * 
 */
@SuppressLint("InflateParams")
public class AsyncImageAdapter extends BaseAdapter {
	/**
	 * {@link AsyncImageAdapter#mContext} An array of image IDs to load
	 */
	private ArrayList<String> mImgIDs;

	/**
	 * {@link AsyncImageAdapter#mContext} A context that hosts this adapter
	 */
	private Context mContext;

	/**
	 * {@link AsyncImageAdapter#MMS_PART_URI} Identifies the table to query for
	 * all mms message parts
	 */
	private static final String MMS_PART_URI = "content://mms/part";

	/**
	 * {@link AsyncImageAdapter#mPlaceHolderBitmap} A placeholder bitmap used
	 * before images are loaded asynchronously
	 */
	private Bitmap mPlaceHolderBitmap;

	/**
	 * {@link AsyncImageAdapter#mImageGridView} A handle to the grid view
	 * holding these images. Used primarially to figure out how big the images
	 * should be
	 */
	private GridView mImageGridView;

	/**
	 * {@link AsyncImageAdapter#mMemoryCache} An image cache. Images are
	 * accessed by a string representation of the key at which they can be found
	 * from the {@link AsyncImageAdapter#MMS_PART_URI} content provider. The
	 * cache is static so that the cache persists whenever the activity is
	 * destroyed
	 */
	private static LruCache<String, Bitmap> mMemoryCache = null;

	/**
	 * {@link AsyncImageAdapter#mAskedToBuffer} A set that tracks which images
	 * have been requested to buffer before being viewed. There are no repeat
	 * requests, otherwise we may overload the OS with duplicate buffer requests
	 */
	private static Set<String> mAskedToBuffer = new HashSet<String>();

	/**
	 * {@link AsyncImageAdapter#mIsSelected} A boolean array that tracks whether
	 * or not each image is selected
	 */
	private boolean[] mIsSelected;

	/**
	 * {@link AsyncImageAdapter#SELECT_ALL} A constant used for the hosting
	 * class to indicate it would like all views to be selected
	 */
	public int SELECT_ALL = -1;

	/**
	 * {@link AsyncImageAdapter#mTouchListener} A click listener that detects
	 * long clicks, double clicks, and single clicks, and responds appropriatley
	 */
	MultiTouchDetectorListener mTouchListener;

	public AsyncImageAdapter() {
		final int maxMemory;
		final int cacheSize;

		mTouchListener = new MultiTouchDetectorListener();
		if (mMemoryCache == null) {
			/*
			 * Get max available VM memory, exceeding this amount will throw an
			 * OutOfMemory exception. Stored in kilobytes as LruCache takes an
			 * int in its constructor.
			 */
			maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
			cacheSize = maxMemory / 8;
			mMemoryCache = new LruCache<String, Bitmap>(cacheSize) {
				@Override
				protected int sizeOf(String key, Bitmap bitmap) {
					/*
					 * The cache size will be measured in kilobytes rather than
					 * number of items.
					 */
					return bitmap.getByteCount() / 1024;
				}
			};
		}
	}

	public void addBitmapToMemoryCache(String key, Bitmap bitmap) {
		if (getBitmapFromMemCache(key) == null) {
			mMemoryCache.put(key, bitmap);
		}
	}

	public Bitmap getBitmapFromMemCache(String key) {
		return mMemoryCache.get(key);
	}

	public void setImageIDs(ArrayList<String> imgIDs) {
		this.mImgIDs = imgIDs;
		mIsSelected = new boolean[mImgIDs.size()];
	}

	public void setPlaceholderBitmap(Bitmap bitmap) {
		this.mPlaceHolderBitmap = bitmap;
	}

	public void setContext(Context context) {
		this.mContext = context;
	}

	public void setGridView(GridView gridView) {
		this.mImageGridView = gridView;
	}

	/**
	 * Returns a List<Integer> of the indices into mImgIDs corresponding to
	 * selected images
	 * 
	 * @return A list of indices into mImgIDs of selected images
	 */
	public List<Integer> getSelectedIndices() {
		ArrayList<Integer> selected = new ArrayList<Integer>();
		int n = mIsSelected.length;
		for (int i = 0; i < n; i++) {
			if (mIsSelected[i])
				selected.add(i);
		}
		return selected;
	}

	/**
	 * Returns true if the item located at 'index' is selected
	 * 
	 * @param index
	 *            The position in the array adapter to query for selection
	 *            status
	 * @return
	 */
	public boolean isSelected(int index) {
		return mIsSelected[index];
	}

	/**
	 * Add images too the buffer that may not be visible yet
	 * 
	 * @param numToBuffer
	 *            The number of images that are not yet visible which will be
	 *            added to the buffer
	 */
	public void bufferAhead(int numToBuffer) {
		int lastVisible;
		int lastIndex;
		BitmapBufferTask cacheTask = new BitmapBufferTask();
		ArrayList<String> toBuffer = new ArrayList<String>();

		if (numToBuffer < 1)
			throw new IllegalArgumentException("Argument 'numToBuffer' must "
					+ "be > 0!");
		lastVisible = mImageGridView.getLastVisiblePosition();
		lastIndex = getCount() - 1;
		if (lastIndex - lastVisible < numToBuffer)
			numToBuffer = lastIndex - lastVisible;

		/* make sure to not repeat buffer requests by checking mAskedToBuffer */
		for (int i = lastVisible + 1; i < lastVisible + numToBuffer; i++) {
			if (mAskedToBuffer.contains(mImgIDs.get(i)) == true)
				continue;
			if (getBitmapFromMemCache(mImgIDs.get(i)) == null) {
				toBuffer.add(mImgIDs.get(i));
				mAskedToBuffer.add(mImgIDs.get(i));
			}
		}
		if (toBuffer.size() > 0)
			cacheTask.execute(toBuffer.toArray(new String[toBuffer.size()]));
	}

	/**
	 * Changes the visible state of selected images
	 * 
	 * @param img
	 */
	private void setSelectionState(ImageView img) {
		boolean isSelected = mIsSelected[(Integer) img.getTag()];
		float alpha = isSelected ? (float) 0.5 : (float) 1;
		img.setAlpha(alpha);
	}

	@Override
	public View getView(int position, View convertView, ViewGroup root) {
		ImageView imageView;
		LayoutInflater inflater;

		if (convertView == null) {
			inflater = (LayoutInflater) mContext
					.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			convertView = inflater.inflate(R.layout.image_grid_item, null);
			imageView = (ImageView) convertView.findViewById(R.id.image);
			imageView.setOnTouchListener(mTouchListener);
		}

		imageView = (ImageView) convertView.findViewById(R.id.image);
		loadBitmap(mImgIDs.get(position), imageView, position);
		imageView.setTag(position);
		setSelectionState(imageView);
		return convertView;
	}

	@Override
	public int getCount() {
		return mImgIDs.size();
	}

	@Override
	public Object getItem(int position) {
		return null;
	}

	@Override
	public long getItemId(int position) {
		return 0;
	}

	/**
	 * Used to update the selection of one or all of the items in the adapter.
	 * This method operates as you would expect, however, if all items are
	 * selected before invocation, they will all become unslected. This is used
	 * so that an easy 'select all' behavior can be obtained
	 * 
	 * @param item
	 *            The position to operate on, or SELECT_ALL to operate on all
	 * @param selected
	 *            True to set selected, false to set deselected
	 */
	public void setSelection(int item, boolean selected) {
		boolean everyItemWasSelected;
		if (item == SELECT_ALL) {
			everyItemWasSelected = true;
			for (int i = 0; i < mIsSelected.length; i++) {
				if (mIsSelected[i] == false)
					everyItemWasSelected = false;
				mIsSelected[i] = selected;
			}

			if (everyItemWasSelected)
				mIsSelected = new boolean[mIsSelected.length];
		} else
			mIsSelected[item] = selected;
		notifyDataSetChanged();
	}

	/**
	 * Launches a background task to load an image from disk. Initially the view
	 * is populated with a placeholder, however, an async task will load up the
	 * image or pull it from a cache
	 * 
	 * @param imgID
	 * @param imageView
	 */
	private void loadBitmap(String imgID, ImageView imageView, int position) {
		final BitmapWorkerTask task;
		final AsyncDrawable asyncDrawable;
		final Bitmap bitmap = getBitmapFromMemCache(imgID);

		cancelIfBusyOnOtherWork(imgID, imageView);
		if (bitmap != null) {
			imageView.setImageBitmap(bitmap);
		} else {
			task = new BitmapWorkerTask(imageView, position);
			asyncDrawable = new AsyncDrawable(mContext.getResources(),
					mPlaceHolderBitmap, task);
			imageView.setImageDrawable(asyncDrawable);
			task.execute(imgID);
		}
	}

	/**
	 * Cancels an ImageView's associated work, if there is any, to avoid any in
	 * progress work that is occuring in the case the view is recycled
	 * 
	 * @param imgID
	 * @param imageView
	 */
	public void cancelIfBusyOnOtherWork(String imgID, ImageView imageView) {
		final BitmapWorkerTask bitmapWorkerTask = getBitmapWorkerTask(imageView);
		final String bitmapImgID;

		/* work is being done */
		if (bitmapWorkerTask != null) {
			bitmapImgID = bitmapWorkerTask.mImgID;
			/* work is being done on another image -- cancel it! */
			if (imgID.equals(bitmapImgID) == false)
				bitmapWorkerTask.cancel(true);
		}
	}

	class AsyncDrawable extends BitmapDrawable {
		private final WeakReference<BitmapWorkerTask> bitmapWorkerTaskReference;

		public AsyncDrawable(Resources res, Bitmap bitmap,
				BitmapWorkerTask bitmapWorkerTask) {
			super(res, bitmap);
			bitmapWorkerTaskReference = new WeakReference<BitmapWorkerTask>(
					bitmapWorkerTask);
		}

		public BitmapWorkerTask getBitmapWorkerTask() {
			return bitmapWorkerTaskReference.get();
		}
	}

	private BitmapWorkerTask getBitmapWorkerTask(ImageView imageView) {
		if (imageView != null) {
			final Drawable drawable = imageView.getDrawable();
			if (drawable instanceof AsyncDrawable) {
				final AsyncDrawable asyncDrawable = (AsyncDrawable) drawable;
				return asyncDrawable.getBitmapWorkerTask();
			}
		}
		return null;
	}

	/**
	 * Loads an image asyncronously and populates an image view with the data if
	 * it is still around and the work hasn't been canceled yet. Regardless of
	 * whether or not the image view is around, the bitmap will be added to the
	 * LRU cache
	 * 
	 * @author Nicholas M. Iodice
	 * 
	 */
	class BitmapWorkerTask extends AsyncTask<String, Void, Bitmap> {
		private final WeakReference<ImageView> imageViewReference;
		private String mImgID;
		private int mPosition;

		public BitmapWorkerTask(ImageView imageView, int position) {
			/*
			 * Use a WeakReference to ensure the ImageView can be garbage
			 * collected
			 */
			imageViewReference = new WeakReference<ImageView>(imageView);
			mPosition = position;
		}

		/**
		 * Decode image in background.
		 */
		@Override
		protected Bitmap doInBackground(String... params) {
			Bitmap bmp;
			if (isCancelled())
				return null;
			mImgID = params[0];
			synchronized (mMemoryCache) {
				bmp = getBitmapFromMemCache(mImgID);
			}
			if (bmp != null)
				return bmp;
			return ImageUtils.getImageFromContentProvider(MMS_PART_URI + "/"
					+ mImgID, true, mImageGridView.getNumColumns(), mContext);
		}

		@Override
		protected void onPostExecute(Bitmap bitmap) {
			TransitionDrawable transitionDrawable;
			Drawable[] layers;
			final ImageView imageView;
			Resources res = mContext.getResources();
			int transDuration = res.getInteger(R.integer.image_fade_duration);

			addBitmapToMemoryCache(mImgID, bitmap);
			/* Once complete, see if ImageView is still around and set bitmap. */
			if (imageViewReference != null && bitmap != null) {

				imageView = imageViewReference.get();
				if (imageView != null) {
					/* a simple image fade animation */
					layers = new Drawable[2];
					layers[0] = new BitmapDrawable(res, mPlaceHolderBitmap);
					layers[1] = new BitmapDrawable(res, bitmap);

					transitionDrawable = new TransitionDrawable(layers);
					imageView.setImageDrawable(transitionDrawable);
					transitionDrawable.startTransition(transDuration);
				}
			}
			/*
			 * to fill the cache for other requests. this is somewhat imprecise,
			 * so we allow a range of values to request the buffer ahead. the
			 * buffer ahead requests do their best to avoid duplicate work
			 */
			if (mImageGridView.getLastVisiblePosition() - mPosition < 10)
				bufferAhead(25);
		}
	}

	/**
	 * A subclass of BitmapWorkerTask that is better suited to handle buffer
	 * requests. Some of the code is shared, so it is easier to subclass it than
	 * re invent the wheel
	 * 
	 * @author Nicholas M. Iodice
	 * 
	 */
	class BitmapBufferTask extends BitmapWorkerTask {

		public BitmapBufferTask() {
			super(null, -1);
		}

		protected Bitmap doInBackground(String... IDsToBuffer) {
			Bitmap bmp;
			for (String id : IDsToBuffer) {
				bmp = super.doInBackground(new String[] { id });
				addBitmapToMemoryCache(id, bmp);
			}
			return null;
		}

		protected void onPostExecute(Bitmap bitmap) {
		}
	}

	/**
	 * An implementation of View.OnTouchListener that toggles selection for a
	 * single tap, or displays a full screen image for long taps or double taps
	 * 
	 * @author Nicholas M. Iodice
	 * 
	 */
	class MultiTouchDetectorListener implements View.OnTouchListener {

		long lastUpPress = -1;
		long DOUBLE_CLICK_THRESH = 200;
		long LONG_CLICK_THRESH = 750;
		long lastDownPress = -1;
		int lastPosClicked = -1;
		boolean isLastClickSingle;
		boolean isDepressed;

		private void onSingleClick(ImageView img) {
			int pos = (Integer) img.getTag();
			if (mIsSelected[pos] == true)
				mIsSelected[pos] = false;
			else
				mIsSelected[pos] = true;
			setSelectionState(img);
		}

		private void onDoubleClick(ImageView img) {
			Intent intent;
			int pos = (Integer) img.getTag();

			intent = new Intent(mContext, FullScreenImageActivity.class);
			intent.putExtra(FullScreenImageActivity.BITMAP_URI, MMS_PART_URI
					+ "/" + mImgIDs.get(pos));
			mContext.startActivity(intent);
		}

		@SuppressLint("ClickableViewAccessibility")
		@Override
		public boolean onTouch(View v, MotionEvent event) {
			final ImageView img = (ImageView) v;
			int pos = (Integer) img.getTag();
			boolean isSingleClick = true;

			if (MotionEvent.ACTION_DOWN == event.getAction()) {
				/* detect double tap */
				if (lastPosClicked == pos
						&& System.currentTimeMillis() - lastDownPress <= DOUBLE_CLICK_THRESH) {
					isSingleClick = false;
				}
				lastDownPress = System.currentTimeMillis();
				isDepressed = true;
			} else if (MotionEvent.ACTION_UP == event.getAction()) {
				/* detect long tap */
				if (System.currentTimeMillis() - lastDownPress >= LONG_CLICK_THRESH) {
					isSingleClick = false;
				}
				isDepressed = false;
			}
			lastPosClicked = pos;

			/* responds to the event appropriately */
			if (isSingleClick && MotionEvent.ACTION_DOWN == event.getAction()) {
				isLastClickSingle = true;
				/*
				 * This is a bit tricky. Here is what's going on:
				 * 	1. A new thread is started that essentially waits for the 
				 * 		duration of DOUBLE_CLICK_THRESH. This is not done in the
				 * 		UI thread, because if it was, it would block the UI and
				 * 		prevent proper detection of double clicks 
				 *  2. If, after waiting the duration of DOUBLE_CLICK_THRESH, it
				 *  	is determined that the click is a real single click,
				 *  	the onSingleClick logic is invoked on the UI thread,
				 *  	because it cannot be invoked in the background thread
				 */
				new Thread(new Runnable() {
					public void run() {
						try {
							Thread.sleep(DOUBLE_CLICK_THRESH);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
						/* if these hold, then it is a real single click */
						if (isLastClickSingle == true && isDepressed == false)
							new Handler(Looper.getMainLooper())
									.post(new Runnable() {
										public void run() {
											onSingleClick(img);
										}
									});
					}
				}).start();
			} else if (!isSingleClick) {
				onDoubleClick(img);
				isLastClickSingle = false;
			}
			return true;
		}
	}
}
