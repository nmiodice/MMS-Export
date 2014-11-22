package com.iodice.mmsexport;

import java.util.ArrayList;

import android.app.Fragment;
import android.app.LoaderManager;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.SearchView;
import android.widget.SearchView.OnQueryTextListener;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;

public class MmsFragment extends Fragment implements
		LoaderManager.LoaderCallbacks<Cursor>, OnItemClickListener,
		OnQueryTextListener {
	/**
	 * An interface used to control formatting within the ThreadCursorAdapter
	 * class. It is responsible for two functionalities: 1. Providing a method
	 * to format a given string 2. Providing an ID of the TextView that holds
	 * the string to be formatted
	 * 
	 * @author Nicholas M. Iodice
	 * 
	 */
	private static interface FormatHelper {
		/**
		 * Format a string
		 * 
		 * @param s
		 */
		public String format(String s);

		/**
		 * @return returns the ID of the view that holds the to-be-formatted
		 *         string. This is typically the ID of a TextView object, but
		 *         this depends on the caller
		 */
		public int getID();
	}

	/**
	 * {@link MmsFragment#THREAD_LOADER} Identifies the loader used to query all
	 * mms/sms threads
	 */
	private static final int THREAD_LOADER = 0;

	/**
	 * {@link MmsFragment#SMS_MMS_THREAD_URI} Identifies the table to query for
	 * all mms/sms threads
	 */
	private static final String SMS_MMS_THREAD_URI = "content://mms-sms/conversations/?simple=true";

	/**
	 * {@link MmsFragment#SMS_MMS_THREAD_URI} Identifies the table to query for
	 * all mms/sms threads
	 */
	private static final String SMS_MMS_THREAD_WITH_ID_URI = "content://mms-sms/conversations";

	/**
	 * {@link MmsFragment#MMS_PART_URI} Identifies the table to query for all
	 * mms message parts
	 */
	private static final String MMS_PART_URI = "content://mms/part";

	/**
	 * {@link MmsFragment#mThreadAdapter} A handle to the thread list adapter
	 */
	private SimpleCursorAdapter mThreadAdapter;

	/**
	 * {@link MmsFragment#mThreadListView} A handle to the thread ListView
	 */
	private ListView mThreadListView;

	/**
	 * {@link MmsFragment#mCurrFilter} A filter on thread contact names, passed
	 * to the content provider that serves SMS/MMS info
	 */
	private String mCurrFilter = "";

	/**
	 * {@link MmsFragment#FILTER_TAG} A tag used to store or retrieve the
	 * current filter text
	 */
	private final String FILTER_TAG = "FILTER_TAG";

	public MmsFragment() {
	}

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setHasOptionsMenu(true);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		String savedFilter;

		/*
		 * the filter value may have been active if this fragment is being
		 * recreated right now
		 */
		if (savedInstanceState != null
				&& (savedFilter = savedInstanceState.getString(FILTER_TAG)) != null)
			mCurrFilter = savedFilter;

		/*
		 * Initializes the CursorLoader. The THREAD_LOADER value is eventually
		 * passed to onCreateLoader().
		 */
		getLoaderManager().initLoader(THREAD_LOADER, null, this);
		View rootView = inflater.inflate(R.layout.fragment_mms, container,
				false);
		mThreadAdapter = getThreadAdapter();
		mThreadListView = (ListView) rootView
				.findViewById(R.id.sms_mms_thread_list);
		mThreadListView.setAdapter(mThreadAdapter);
		mThreadListView.setOnItemClickListener(this);
		mThreadListView.setFastScrollEnabled(true);
		return rootView;
	}

	public void onResume() {
		super.onResume();
		/*
		 * the list view may have been turned 'dark' if, when the activity was
		 * paused, images were being loaded by an async task
		 */
		ListView lv = (ListView) getView().findViewById(
				R.id.sms_mms_thread_list);
		lv.setBackgroundColor(getResources().getColor(R.color.white));
	}

	/**
	 * Saves fragment state if needed
	 */
	public void onSaveInstanceState(Bundle outState) {
		outState.putString(FILTER_TAG, mCurrFilter);
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		MenuItem searchItem;
		SearchView searchView;
		inflater.inflate(R.menu.mms_sms_menu, menu);
		searchItem = menu.findItem(R.id.search);
		searchView = (SearchView) searchItem.getActionView();
		searchView.setOnQueryTextListener(this);

		/* achieves UI consistency during screen rotation, etc... */
		if (mCurrFilter.equals("") == false) {
			searchView.setQuery(mCurrFilter, false);
			searchView.setIconified(false);
		}

		super.onCreateOptionsMenu(menu, inflater);
	}

	/**
	 * Called when the action bar search text has changed. Update the search
	 * filter, and restart the loader to do a new query with this filter.
	 */
	@Override
	public boolean onQueryTextChange(String newText) {
		newText = newText.replaceAll("\\s+", "");
		if (mCurrFilter.equals(newText) == false) {
			mCurrFilter = newText;
			getLoaderManager().restartLoader(0, null, this);
		}
		return true;
	}

	@Override
	public boolean onQueryTextSubmit(String arg0) {
		return false;
	}

	/**
	 * Returns a handle to the thread list adapter
	 * 
	 * @return an adapter of type ThreadCursorAdapter
	 */
	private ThreadCursorAdapter getThreadAdapter() {
		final String[] cols = getThreadFromColumns();
		final int[] to = getThreadToFields();
		ThreadCursorAdapter adapter = new ThreadCursorAdapter(getActivity(),
				R.layout.sms_mms_thread_list_row, null, cols, to, 0);
		/*
		 * this formatter object is used in order to replace some ugly
		 * formatting the cursor returns when there are multiple contacts for a
		 * thread. I.E., a group message is returned with person1;person2
		 */
		FormatHelper formatContainer = new FormatHelper() {
			public String format(String s) {
				return s.replaceAll(";", ", ");
			}

			public int getID() {
				int i = 0;
				while (i < cols.length && cols[i].equals("name") == false)
					i++;
				return to[i];
			}
		};
		adapter.addFormatter(formatContainer);
		return adapter;
	}

	/**
	 * Defines the columns for sms/mms thread data
	 * 
	 * @return
	 */
	private String[] getThreadFromColumns() {
		return new String[] { "name", "snippet", "_id", };
	}

	/**
	 * Defines the fields for sms/mms thread data to be put in
	 */
	private int[] getThreadToFields() {
		return new int[] { R.id.thread_author, R.id.thread_snippet,
				R.id.thread_id, };
	}

	/**
	 * The method invoked whenever a loader is initialized. Selects the correct
	 * loader handler method to invoke
	 */
	@Override
	public Loader<Cursor> onCreateLoader(int loaderID, Bundle bundle) {
		CursorLoader cl;

		switch (loaderID) {
		/* Loads every SMS/MMS thread available on the device */
		case THREAD_LOADER:
			final String[] projection = new String[] { "*" };
			final String selection = (mCurrFilter.equals("") == true) ? null
					: new String("name LIKE '%" + mCurrFilter + "%'");
			final Uri uri = Uri.parse(SMS_MMS_THREAD_URI);
			cl = new CursorLoader(getActivity(), uri, projection, selection,
					null, null);
			return cl;
		default:
			/* an invalid ID was passed in */
			return null;
		}
	}

	/**
	 * Logic to handle data returned by background query executed via
	 * CursorLoader
	 */
	@Override
	public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
		switch (loader.getId()) {
		case THREAD_LOADER:
			mThreadAdapter.changeCursor(cursor);
		default:
			/* an invalid ID was passed in */
			return;
		}

	}

	@Override
	public void onLoaderReset(Loader<Cursor> loader) {
	}

	/**
	 * Detects clicks in {@link MmsFragment#mThreadListView mThreadListView} and
	 * launches an async task to process images in a background thread
	 */
	@Override
	public void onItemClick(AdapterView<?> parent, View view, int position,
			long id) {
		TextView MmsIDTextView = (TextView) view.findViewById(R.id.thread_id);
		Integer MmsID = Integer.decode(MmsIDTextView.getText().toString());
		new ThreadProcessTask().execute(MmsID);
	}

	private void startImageGridActivity(ArrayList<String> bitmapIDs) {
		if (getActivity() == null)
			return;
		Intent intent = new Intent(getActivity(), ImageGridActivity.class);
		intent.putStringArrayListExtra(ImageGridActivity.BITMAP_INTENT_ID,
				bitmapIDs);
		startActivity(intent);
	}

	/**
	 * A trivial extension of a SimpleCursorAdapter that can optionally handle
	 * text re-formatting when a view is displayed.
	 */
	private class ThreadCursorAdapter extends SimpleCursorAdapter {

		/**
		 * Holds formatting objects, which are used to format elements of the
		 * view whenever getView is called
		 */
		ArrayList<FormatHelper> formatters;

		/**
		 * The data is passed directly to the extended class
		 * 
		 * @param context
		 * @param layout
		 * @param c
		 * @param from
		 * @param to
		 * @param flags
		 */
		public ThreadCursorAdapter(Context context, int layout, Cursor c,
				String[] from, int[] to, int flags) {
			super(context, layout, c, from, to, flags);
			formatters = new ArrayList<FormatHelper>();
		}

		/**
		 * Adds an instance of a FormatContainer, which holds information used
		 * to format view elements. The formatter getID method must return the
		 * value of an ID of a TextView object. It is an unchecked runtime error
		 * to not abide by this requirement
		 * 
		 * @param formatContainer
		 */
		public void addFormatter(FormatHelper formatter) {
			formatters.add(formatter);
		}

		/**
		 * A standard implementation of getView, however, if there have been
		 * formatting parameters supplied they will invoked here
		 */
		public View getView(int position, View convertView, ViewGroup parent) {
			View v = super.getView(position, convertView, parent);

			/* string conversion, as per any defined formatter objects */
			TextView textContainer;
			String formattedStr;
			for (FormatHelper formatter : formatters) {
				textContainer = (TextView) v.findViewById(formatter.getID());
				formattedStr = formatter.format(textContainer.getText()
						.toString());
				textContainer.setText(formattedStr);
			}
			return v;
		}
	}

	/**
	 * An AsyncTask that processes SMS/MMS conversations and identifies any
	 * image parts in the conversation
	 * 
	 * @author Nicholas M. Iodice
	 * 
	 */
	private class ThreadProcessTask extends
			AsyncTask<Integer, Integer, ArrayList<String>> {
		private ProgressBar mProgressBar;
		private View mProgressContainer;
		private ListView mListView;
		private TextView mTextView;
		private View mRootView;

		private void initViewHandles() {
			mRootView = getView();
			if (mRootView == null) {
				mProgressBar = null;
				mProgressContainer = null;
			} else {
				mProgressBar = (ProgressBar) mRootView
						.findViewById(R.id.sms_mms_query_progress);
				mProgressContainer = mRootView
						.findViewById(R.id.sms_mms_progress_container);
				mListView = (ListView) mRootView
						.findViewById(R.id.sms_mms_thread_list);
				mTextView = (TextView) mRootView
						.findViewById(R.id.sms_mms_query_progress_text);
			}
		}

		/**
		 * Enables a progress bar, and disables click events in the fragment's
		 * list view
		 */
		private void enableProgressBar() {
			if (mRootView != null) {
				mRootView.findViewById(R.id.sms_mms_thread_list).setEnabled(
						false);
			}
			if (mProgressBar != null) {
				mProgressBar.setProgress(0);
				mProgressBar.setMax(100);
			}
			if (mProgressContainer != null)
				mProgressContainer.setVisibility(View.VISIBLE);
			if (mListView != null)
				mListView.setBackgroundColor(getResources().getColor(
						R.color.dark));
			if (mTextView != null)
				mTextView.setText(getResources().getText(
						R.string.sms_mms_query_convos));
		}

		/**
		 * Disables a progress bar, and enables click events in the fragment's
		 * list view
		 * 
		 * @param brightenScreen
		 *            brightens the list view if true, or not if false
		 */
		private void disableProgressBar(boolean brightenScreen) {
			if (mRootView != null)
				mRootView.findViewById(R.id.sms_mms_thread_list).setEnabled(
						true);
			if (mProgressContainer != null)
				mProgressContainer.setVisibility(View.GONE);
			if (mListView != null && brightenScreen == true)
				mListView.setBackgroundColor(getResources().getColor(
						R.color.white));

		}

		/**
		 * Updates the progress bar to show that there is start + 100*(curr/max)
		 * work done
		 * 
		 * @param start
		 *            The base progress for which to add current progress to
		 * @param curr
		 *            The current amount of work done (curr <= max)
		 * @param max
		 *            The max amount of work to do
		 */
		private void updateProgressBar(int start, int curr, int max) {
			int currProgress;
			currProgress = start
					+ (int) ((float) .5 * (100 * (float) curr / (float) max));
			publishProgress(currProgress);
		}

		@Override
		protected void onPreExecute() {
			initViewHandles();
			enableProgressBar();
			super.onPreExecute();
		}

		/**
		 * Processes each thread and finds all image part IDs in those threads
		 */
		protected ArrayList<String> doInBackground(Integer... threadIDs) {
			ArrayList<String> bitmapIDs = new ArrayList<String>();
			ArrayList<String> msgIDs = new ArrayList<String>();
			/*
			 * The general sequence of steps is as follows: 1. For each
			 * threadID, get a list of SMS/MMS message IDs 2. For each SMS/MMS,
			 * query for MMS parts
			 */
			msgIDs = getSmsMmsIDsFromThreadIDs(threadIDs[0]);
			mRootView.post(new Runnable() {
				@Override
				public void run() {
					mTextView.setText(getResources().getText(
							R.string.sms_mms_query_images));
				}
			});
			bitmapIDs = getImagePartIDs(msgIDs);
			return bitmapIDs;
		}

		/**
		 * Given a list of Thread ID values, return every SMS/MMS message in
		 * those threads
		 * 
		 * @param threadIDs
		 *            A thread ID for which SMS/MMS IDs are requested
		 * @return A list of all SMS/MMS IDs
		 */
		private ArrayList<String> getSmsMmsIDsFromThreadIDs(int threadID) {
			ArrayList<String> msgIDs = new ArrayList<String>();
			final String IDCol = "_id";
			final String[] projection = new String[] { IDCol, "ct_t" };
			Cursor cursor;
			String msgID;
			String msgType;
			Uri uri;

			/* some variables used to track the overall progress */
			int startProgress = mProgressBar.getProgress();
			int messagesProcessed = 0;
			int totalMessages;

			uri = Uri.parse(SMS_MMS_THREAD_WITH_ID_URI + "/" + threadID);
			cursor = getActivity().getContentResolver().query(uri, projection,
					null, null, null);
			if (cursor.moveToFirst()) {
				totalMessages = cursor.getCount();
				do {
					msgType = cursor.getString(cursor.getColumnIndex("ct_t"));
					/* this is an MMS */
					if ("application/vnd.wap.multipart.related".equals(msgType)) {
						msgID = cursor.getString(cursor.getColumnIndex(IDCol));
						msgIDs.add(msgID);
					}
					messagesProcessed++;
					updateProgressBar(startProgress, messagesProcessed,
							totalMessages);
				} while (cursor.moveToNext());
				cursor.close();
			}
			return msgIDs;
		}

		/**
		 * Returns a list of all image part IDs from the input list of messages
		 * 
		 * @param msgIDs
		 *            A list of message IDs to query for images
		 * @return
		 */
		private ArrayList<String> getImagePartIDs(ArrayList<String> msgIDs) {
			ArrayList<String> bitmapIDs = new ArrayList<String>();
			String[] projection = new String[] { "_id", "ct", };
			String selection;
			String partType;
			String partID;
			Cursor cursor;
			Uri uri = Uri.parse(MMS_PART_URI);

			/* some variables used to track the overall progress */
			int startProgress = mProgressBar.getProgress();
			int totalMessages = msgIDs.size();
			int messagesProcessed = 0;

			for (String ID : msgIDs) {
				if (isCancelled())
					break;

				selection = "mid = " + ID;
				/* wrap in try catch, in case the hosting activity has gone away */
				try {
					cursor = getActivity().getContentResolver().query(uri,
							projection, selection, null, null);

				} catch (NullPointerException npe) {
					return bitmapIDs;
				}
				if (cursor.moveToFirst()) {
					do {
						/*
						 * detect cancellation whenever we can in order to break
						 * early
						 */
						if (isCancelled())
							break;

						partID = cursor.getString(cursor.getColumnIndex("_id"));
						partType = cursor
								.getString(cursor.getColumnIndex("ct"));
						if (partType == null)
							continue;

						if (partType.equals("image/jpeg")
								|| partType.equals("image/jpg")
								|| partType.equals("image/bmp")
								|| partType.equals("image/gif")
								|| partType.equals("image/png")) {
							bitmapIDs.add(partID);
						}

					} while (cursor.moveToNext());
				}
				cursor.close();
				messagesProcessed++;
				updateProgressBar(startProgress, messagesProcessed,
						totalMessages);
			}
			return bitmapIDs;
		}

		protected void onProgressUpdate(Integer... progress) {
			int prog = progress[0];
			if (mProgressBar != null)
				mProgressBar.setProgress(prog);
		}

		/**
		 * Passes off any image IDs found in the background query to the
		 * appropriate handler method. If no IDs are found, a Toast message is
		 * displayed to the user
		 */
		protected void onPostExecute(ArrayList<String> bitmapIDs) {
			boolean foundMessages = true;

			if (bitmapIDs.size() > 0)
				startImageGridActivity(bitmapIDs);
			else {
				Utils.toast(R.string.mms_no_images_found, getActivity());
				foundMessages = false;
			}
			disableProgressBar(!foundMessages);
		}
	}
}
