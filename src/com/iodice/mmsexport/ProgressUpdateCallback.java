package com.iodice.mmsexport;

/**
 * A callback to implement for classes which need to be updated as progress is
 * made on a job
 * 
 * @author Nicholas M. Iodice
 * 
 */
public interface ProgressUpdateCallback {
	/**
	 * Respond to a progress update. The caller and callee may use the boolean
	 * return as they wish
	 * 
	 * @param complete
	 * @param total
	 * @return
	 */
	boolean respondToProgressUpdate(int complete, int total);
}
