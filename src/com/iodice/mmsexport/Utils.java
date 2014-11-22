package com.iodice.mmsexport;

import android.content.Context;
import android.widget.Toast;

public class Utils {
	public static void toast(int msgID, Context context) {
		String errText;
		Toast toast;
		int duration;
		
		errText = context.getString(msgID);
		duration = Toast.LENGTH_SHORT;
		toast = Toast.makeText(context, errText, duration);
		toast.show();
	}
}
