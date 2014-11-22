package com.iodice.mmsexport;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.GridView;
import android.widget.ProgressBar;
import android.widget.TextView;

public class ImageGridFragment extends Fragment implements OnClickListener {

	/**
	 * {@link ImageGridFragment#mThreadAdapter} A handle to the thread list
	 * adapter
	 */
	private AsyncImageAdapter mImageAdapter;

	/**
	 * {@link ImageGridFragment#mImageGridView} A handle to the thread ListView
	 */
	private GridView mImageGridView = null;

	/**
	 * {@link ImageGridFragment#mImageIDs} A handle to the list of image IDs to
	 * be processed
	 */
	private ArrayList<String> mImageIDs = null;

	/**
	 * {@link ImageGridFragment#MMS_PART_URI} Identifies the table to query for
	 * all mms message parts
	 */
	private static final String MMS_PART_URI = "content://mms/part";

	/**
	 * {@link ImageGridFragment#BUTTON_TAG} A tag used to identify the 'share
	 * images' button in the onClick(View v) method
	 */
	private final int BUTTON_TAG = 1;

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setHasOptionsMenu(true);
	}

	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		Button button;
		View rootView = inflater.inflate(R.layout.fragment_images, container,
				false);
		this.setRetainInstance(true);
		initGridView(rootView);
		initGridAdapter();
		/*
		 * the caller may have supplied image IDs before adding the fragment to
		 * the UI
		 */
		if (mImageIDs != null)
			supplyImageIDs(mImageIDs);

		/*
		 * the button will divert to the hosted activity if set up in the xml
		 * resources, so we need to set the onlick listener here
		 */
		button = (Button) rootView.findViewById(R.id.image_share_button);
		button.setTag(BUTTON_TAG);
		button.setOnClickListener(this);
		return rootView;
	}

	public void onResume() {
		super.onResume();
		/*
		 * it is very likely that onResume was called because of a background
		 * image processing task, which will likely call out of the activity
		 * when it completes. During this task, the view may have become
		 * disabled, so we need to re-enable it here. See ImageShareTask
		 */
		getView().setBackgroundColor(getResources().getColor(R.color.white));
		getView().findViewById(R.id.image_grid).setEnabled(true);
	}

	/**
	 * Sets up the image grid to the point where it can be linked with an
	 * adapter
	 * 
	 * @param rootView
	 */
	private void initGridView(View rootView) {
		int numCols;
		Resources res = getResources();
		int orientation = res.getConfiguration().orientation;
		int portrait = android.content.res.Configuration.ORIENTATION_PORTRAIT;

		mImageGridView = (GridView) rootView.findViewById(R.id.image_grid);
		mImageGridView.setFastScrollEnabled(true);

		/* the number of colums is dynamically set depending on orientation */
		if (orientation == portrait)
			numCols = res.getInteger(R.integer.image_grid_cols_for_portrait);
		else
			numCols = res.getInteger(R.integer.image_grid_cols_for_landscape);
		mImageGridView.setNumColumns(numCols);
	}

	/**
	 * Sets up the image adapter and links it with a gridview. Assumes that
	 * mImageGridView is set up prior to invocation
	 */
	private void initGridAdapter() {
		Bitmap placeholder;
		Resources res = getResources();

		mImageAdapter = new AsyncImageAdapter();
		mImageAdapter.setContext(getActivity());
		mImageAdapter.setGridView(mImageGridView);
		placeholder = BitmapFactory.decodeResource(res, R.drawable.placeholder);
		mImageAdapter.setPlaceholderBitmap(placeholder);
	}

	/**
	 * Sets up the image adapter and gridview, given a list of image IDs
	 * 
	 * @param imgIDs
	 */
	public void supplyImageIDs(ArrayList<String> imgIDs) {
		/*
		 * the fragment may not be visible yet, so it is important to check the
		 * existence of the mImageAdapter and mImageGridView objects. If these
		 * are null, the fragment has not yet been added to the UI and this
		 * method will be re-run after the objects are created. See onCreateView
		 */
		mImageIDs = imgIDs;
		if (mImageAdapter != null)
			mImageAdapter.setImageIDs(imgIDs);
		if (mImageGridView != null)
			mImageGridView.setAdapter(mImageAdapter);
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		inflater.inflate(R.menu.image_grid_menu, menu);
		super.onCreateOptionsMenu(menu, inflater);
	}

	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.img_select_all:
			mImageAdapter.setSelection(mImageAdapter.SELECT_ALL, true);
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	@Override
	public void onClick(View v) {
		int tag = (Integer) v.getTag();

		switch (tag) {
		case BUTTON_TAG:
			ImageShareTask task = new ImageShareTask();
			task.execute();
			break;
		default:
			throw new UnsupportedOperationException();
		}
	}

	/**
	 * An async task that handels background image processing, then presents the
	 * results by launching an intent with a handle to the zipped images. No
	 * input parameters are required: The task will query its parent's class
	 * members to identify which images are currently selected, and will
	 * automatically operate on those images
	 * 
	 * @author Nicholas M. Iodice
	 * 
	 */
	class ImageShareTask extends AsyncTask<Void, Integer, File> implements
			ProgressUpdateCallback {

		/**
		 * {@link ImageShareTask#urisToQuery} A list of URI strings
		 * corresponding to selected images, which will be queried by this task
		 */
		private ArrayList<String> urisToQuery;
		private View mRootView;
		private View mProgressContainer;
		private ProgressBar mProgressBar;
		private TextView mProgressText;
		private Button mButton;
		
		protected void onPreExecute() {
			urisToQuery = new ArrayList<String>();
			List<Integer> selected = mImageAdapter.getSelectedIndices();
			for (Integer i : selected) {
				urisToQuery.add(MMS_PART_URI + '/' + mImageIDs.get(i));
			}

			/* back out early if nothing selected */
			if (urisToQuery.size() == 0) {
				Utils.toast(R.string.no_images_selected, getActivity());
				cancel(true);
				return;
			}

			/* disable parent view during task */
			mRootView = getView();
			mProgressBar = (ProgressBar) mRootView.findViewById(R.id.image_query_progress);
			mProgressText = (TextView) mRootView.findViewById(R.id.image_query_progress_text);
			mButton = (Button) mRootView.findViewById(R.id.image_share_button);
			
			mRootView.setBackgroundColor(getResources().getColor(R.color.dark));
			mRootView.findViewById(R.id.image_grid).setEnabled(false);
			mProgressContainer = mRootView.findViewById(R.id.image_progress_container);
			mProgressBar.setProgress(0);
			mProgressContainer.setVisibility(View.VISIBLE);
			mButton.setEnabled(false);
		}

		/**
		 * Queries images in the background. Returns a File object corresponding
		 * to the newly created zip file, or null if null if there was an error
		 * creating that file
		 */
		@Override
		protected File doInBackground(Void... params) {
			File zipFile;
			if (isCancelled())
				return null;
			try {
				/*
				 * ImageUtils.zipFiles takes an optional callback, which we use
				 * to update progress for the UI
				 */
				zipFile = ImageUtils.zipFiles(urisToQuery,
						Bitmap.CompressFormat.JPEG, getActivity(), this);
			} catch (IOException e) {
				e.printStackTrace();
				zipFile = null;
			}
			return zipFile;
		}

		/**
		 * Process a file so that it can be shared to a different application.
		 * The file parameter will be shared with another app via an intent
		 */
		protected void onPostExecute(File zipFile) {
			Intent shareIntent;
			Uri zipUri;
			Context context;
			List<ResolveInfo> resInfoList;
			String packageName;
			String shareMessage;
			context = getActivity();
			int totalImages;
			
			/* context may be null if the back button was pressed */
			if (context == null)
				return;
			
			/* it looks better to auto unselect all the images */
			if (mImageAdapter != null) {
				totalImages = mImageAdapter.getCount();
				for (int i = 0; i < totalImages; i++)
					mImageAdapter.setSelection(i, false);
			}
			
			if (mButton != null)
				mButton.setEnabled(true);
				
			/* something has gone awry */
			if (isCancelled() == true) {
				if (mRootView != null) {
					mRootView.setBackgroundColor(getResources().getColor(R.color.white));
					mRootView.findViewById(R.id.image_grid).setEnabled(true);
				}
				if (mProgressContainer != null)
					mProgressContainer.setVisibility(View.GONE);
				return;
			}
			
			/* there was a problem with the image processing */
			if (zipFile == null) {
				Utils.toast(R.string.error_writing_image_data, getActivity());
				getView().findViewById(R.id.image_grid).setEnabled(true);
				return;
			}

			/* at this point, its safe to share the content with another app */
			shareIntent = new Intent(Intent.ACTION_SEND);
			zipUri = ZipFileProvider.getUriForFile(context,
					"com.iodice.mmsexport.ZipFileProvider", zipFile);
			ZipFileProvider.setFilesDir(context.getFilesDir().getPath());

			/* grant permisions for all apps that can handle given intent */
			resInfoList = context.getPackageManager().queryIntentActivities(
					shareIntent, PackageManager.MATCH_DEFAULT_ONLY);
			for (ResolveInfo resolveInfo : resInfoList) {
				packageName = resolveInfo.activityInfo.packageName;
				context.grantUriPermission(packageName, zipUri,
						Intent.FLAG_GRANT_READ_URI_PERMISSION);
			}

			shareIntent.putExtra(Intent.EXTRA_STREAM, zipUri);
			shareIntent.setType("application/zip");
			shareMessage = getString(R.string.zip_image_share_message);
			startActivity(Intent.createChooser(shareIntent, shareMessage));
			
			if (mProgressContainer != null)
				mProgressContainer.setVisibility(View.GONE);
		}
		
		protected void onProgressUpdate(Integer... progress) {
			int complete = progress[0];
			int total = progress[1];

			String fmtTxt = getString(R.string.zip_image_update_progress_text_format);
			String updateMsg = String.format(fmtTxt, complete, total);
			int prog = (int)(100*(float)complete/(float)total);
			if (mProgressBar != null)
				mProgressBar.setProgress(prog);
			if (mProgressText != null)
				mProgressText.setText(updateMsg);
		}

		/**
		 * Invoked from the image zipping function
		 */
		@Override
		public boolean respondToProgressUpdate(int complete, int total) {
			Context context = getActivity();
			if (context == null) {
				cancel(true);
				return false;
			}
			publishProgress(new Integer[] {complete, total});
			return true;
		}

	}

}
