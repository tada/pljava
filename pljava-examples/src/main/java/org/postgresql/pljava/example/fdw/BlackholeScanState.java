package org.postgresql.pljava.example.fdw;

import org.postgresql.pljava.fdw.FDWScanState;
import org.postgresql.pljava.fdw.FDWUser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * A ForeignTable scan state. (Temporary)
 */
public class BlackholeScanState implements FDWScanState {
	private static final Logger LOG = Logger.getLogger(BlackholeScanState.class.getName());

	private final FDWUser user;
	private final List<Map<String, Object>> data;

	private Iterator<Map<String, Object>> iter;
	private boolean isOpen = false;

	public BlackholeScanState(FDWUser user, List<Map<String, Object>> data) {
		LOG.info("constructor()");

		this.user = user;

		// create copy
		this.data = new ArrayList<Map<String, Object>> (data);
		this.iter = null;
	}

	@Override
	public void open(boolean explainOnly) {
		LOG.info("open()");
		if (this.isOpen) {
			LOG.info("unexpected state!");
		}
		else if (!isAuthorizedUser())
		{
			LOG.info("unauthorized user");
		}
		else if (!explainOnly)
		{
			// open file, read it, ...
			this.iter = this.data.iterator();
			this.isOpen = true;
		}
	}

	@Override
	public Map<String, Object> next() {
		LOG.info("next()");
		if (this.isOpen) {
			if (iter.hasNext()) {
				return iter.next();
			} else {
				return Collections.emptyMap();
			}
		} else {
			// what about 'explain only?'
			LOG.info("unexpected state!");
		}
	}

	@Override
	public void reset() {
		LOG.info("reset()");
		if (this.isOpen) {
			this.iter = this.data.iterator();
		} else {
			// what about 'explain only?'
			LOG.info("unexpected state!");
		}
	}

	@Override
	public void close() {
		LOG.info("close()");
		if (!this.isOpen) {
			// don't do anything else...
			LOG.info("unexpected state!");
		}

		this.iter = null;
		this.isOpen = false;
	}
}
