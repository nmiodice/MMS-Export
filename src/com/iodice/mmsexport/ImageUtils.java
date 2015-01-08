package com.iodice.mmsexport;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.net.Uri;
import android.view.Display;
import android.view.WindowManager;

public class ImageUtils {

	/**
	 * Loads a Bitmap from a specified URI
	 * 
	 * @param uriString
	 *            The URI to query for the image
	 * @param cropToScreen
	 *            True if the image should be cropped
	 * @param numToFitOnScreen
	 *            If cropToScreen is true, this parameter specifies aproximately
	 *            how many images will fit on the screen (in the horizontal)
	 *            direction. This value is used as a scaing parameter
	 * @param context
	 *            The context to use when querying the content provider
	 * @return The Bitmap specified by uriString
	 */
	public static Bitmap getImageFromContentProvider(String uriString,
			boolean cropToScreen, int numToFitOnScreen, Context context) {
		Uri partURI = Uri.parse(uriString);
		InputStream is = null;
		Bitmap bitmap = null;
		BitmapFactory.Options opts;
		BitmapFactory.Options opts2;
		int maxSize;
		Display display;
		Point size;
		WindowManager wm;

		try {
			is = context.getContentResolver().openInputStream(partURI);
			if (cropToScreen == false) {
				bitmap = BitmapFactory.decodeStream(is);
				/*
				 * cropping in order to fit an appropriate number of images on
				 * the screen, depending on the container that will hold them
				 */
			} else {
				/* get device screen info */
				wm = (WindowManager) context
						.getSystemService(Context.WINDOW_SERVICE);
				display = wm.getDefaultDisplay();
				size = new Point();
				display.getSize(size);
				maxSize = Math.min(size.x, size.y) / numToFitOnScreen;

				/* Decode image size */
				opts = new BitmapFactory.Options();
				opts.inJustDecodeBounds = true;

				BitmapFactory.decodeStream(is, null, opts);
				is.close();

				int scale = 1;
				/* convert to the closest size that is a power of 2 */
				if (opts.outHeight > maxSize || opts.outWidth > maxSize) {
					scale = (int) Math.pow(
							2,
							(int) Math.ceil(Math.log(maxSize
									/ (double) Math.max(opts.outHeight,
											opts.outWidth))
									/ Math.log(0.5)));
				}

				/* Decode with inSampleSize */
				opts2 = new BitmapFactory.Options();
				opts2.inSampleSize = scale;
				is = context.getContentResolver().openInputStream(partURI);
				bitmap = BitmapFactory.decodeStream(is, null, opts2);
				is.close();

				/* crop to a square */
				if (bitmap.getWidth() >= bitmap.getHeight()) {
					bitmap = Bitmap.createBitmap(bitmap, bitmap.getWidth() / 2
							- bitmap.getHeight() / 2, 0, bitmap.getHeight(),
							bitmap.getHeight());
				} else {
					bitmap = Bitmap.createBitmap(bitmap, 0, bitmap.getHeight()
							/ 2 - bitmap.getWidth() / 2, bitmap.getWidth(),
							bitmap.getWidth());
				}
			}
		} catch (IOException e) {
		} catch (NullPointerException npe) {
			/*
			 * if the calling context is no longer here, its not a problem that
			 * we get an NPE. Otherwise, there is some type of programming error
			 */
			if (context != null)
				bitmap = null;
		} finally {
			if (is != null) {
				try {
					is.close();
				} catch (IOException e) {
				}
			}
		}
		return bitmap;
	}

	/**
	 * Retrieves images specified by uriStrings and puts them into a zip file
	 * specified by filename. The caller is responsible for properly handeling
	 * any exceptions thrown
	 * 
	 * @param uriStrings
	 *            String representations of URIs to the images
	 * @param format
	 *            The desired compression format to use. PNG is slow, JPEG is
	 *            fast, but there is a quality tradeoff
	 * @param callback
	 *            An optional parameter. If not null, the onProgressUpdate
	 *            callback will be invoked as items are added to the zipped
	 *            object. If the callback returns false, the image query will
	 *            stop processing images
	 * @return a File object pointing to the newly created zip file
	 * @throws IOException
	 *             If reading or writing to disk fails
	 */
	public static File zipFiles(ArrayList<String> uriStrings,
			Bitmap.CompressFormat format, Context context,
			ProgressUpdateCallback callback) throws IOException {
		/* file formatting variables */
		String imageFileName = context
				.getString(R.string.zip_image_individual_file_format);
		String zipFileName = context.getString(R.string.zip_image_file_name);
		String aFile;
		String uri;
		int n = uriStrings.size();
		/* file handeling variables */
		ByteArrayOutputStream stream;
		FileOutputStream outputStream;
		ZipOutputStream zos;
		byte[] byteArray;
		Bitmap bmp = null;
		ZipEntry entry;
		File returnFile;

		switch (format) {
		case JPEG:
			imageFileName += ".jpeg";
			break;
		case PNG:
			imageFileName += ".png";
			break;
		case WEBP:
			imageFileName += ".webp";
			break;
		default:
			imageFileName += ".unknown";
			break;
		}

		/*
		 * General method to zip files: 1. Open a file stream, create a
		 * ZipOutputStream object 2. Load each bitmap, and create a new zip
		 * entry for each. Zip entries are essentially file info. The bitmap
		 * will be written into the ZipOutputStream as a byte array, so no image
		 * caching is necessary Documentation:
		 * http://developer.android.com/reference
		 * /java/util/zip/ZipOutputStream.html
		 */
		outputStream = context
				.openFileOutput(zipFileName, Context.MODE_PRIVATE);
		zos = new ZipOutputStream(new BufferedOutputStream(outputStream));

		for (int i = 0; i < n; i++) {
			uri = uriStrings.get(i);
			bmp = getImageFromContentProvider(uri, false, 0, context);
			aFile = String.format(imageFileName, i);
			stream = new ByteArrayOutputStream();
			bmp.compress(format, 100, stream);
			byteArray = stream.toByteArray();
			entry = new ZipEntry(aFile);
			zos.putNextEntry(entry);
			zos.write(byteArray);
			zos.closeEntry();

			if (callback != null) {
				if (callback.respondToProgressUpdate(i + 1, n) == false)
					break;
			}
		}

		zos.close();
		outputStream.flush();
		outputStream.close();

		returnFile = new File(context.getFilesDir(), "files");
		returnFile = new File(returnFile, zipFileName);
		return returnFile;
	}
}
