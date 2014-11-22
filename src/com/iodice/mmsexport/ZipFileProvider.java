package com.iodice.mmsexport;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Locale;

import android.annotation.SuppressLint;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.support.v4.content.FileProvider;

/**
 * A simple file provider that allows the caller to specify the directory to
 * look in for a file. The caller MUST set the file path using
 * {@code ZipFileProvider#setFilesDir(String)} prior to serving content using
 * the {@code ZipFileProvider#openFile(Uri, String)} method
 * 
 * @author Nicholas M. Iodice
 * 
 */
public class ZipFileProvider extends FileProvider {

	static String filesDir;

	/**
	 * Set the file path used to look for a file in. The file path should not
	 * contiain a trailing '/'
	 * 
	 * @param fp The file path as a string
	 */
	public static void setFilesDir(String fp) {
		filesDir = fp;
	}

	/**
	 * The caller MUST set the file path using
	 * {@code ZipFileProvider#setFilesDir(String)} prior to serving content
	 * using this method
	 * 
	 * @param uri
	 *            The uri of the file to open
	 * @param mode
	 *            Must be read only. passing in 'r' is sufficient
	 * @return A {@code ParcelFileDescriptor} pointing to the file requested
	 * @throws FileNotFoundException
	 */
	@SuppressLint("DefaultLocale")
	@Override
	public ParcelFileDescriptor openFile(Uri uri, String mode)
			throws FileNotFoundException {
		String uriPath = uri.toString();
		String fileName = uriPath.substring(uriPath.lastIndexOf('/') + 1);
		File file = new File(filesDir + "/" + fileName);

		if (mode.toLowerCase(Locale.US).contains("r"))
			return ParcelFileDescriptor.open(file,
					ParcelFileDescriptor.MODE_READ_ONLY);
		else
			return super.openFile(uri, mode);
	}
}
