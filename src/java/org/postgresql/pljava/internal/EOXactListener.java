/**
 * An instance of this interface reflects the current transaction.
 *
 * @author Thomas Hallgren
 */
package org.postgresql.pljava.internal;


/**
 * An instance of this class corresponds to the PosgreSQL interal
 * structure EOXactCallback.
 *
 * @author Thomas Hallgren
 */
public abstract class EOXactListener extends NativeStruct
{
	/**
	 * Callback received from the backend when a transaction has ended.
	 * @param wasCommit Set to <code>true</code> if the commit was a success
	 * and <code>false</code> if the transaction aborted.
	 */
	public abstract void onEOXact(boolean isCommit);
}
