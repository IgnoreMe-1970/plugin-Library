/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.client;

import plugins.Library.serial.LiveArchiver;
import plugins.Library.serial.TaskAbortException;
import plugins.Library.serial.SimpleProgress;
import plugins.Library.io.ObjectStreamReader;
import plugins.Library.io.ObjectStreamWriter;

import com.db4o.ObjectContainer;

import freenet.client.HighLevelSimpleClient;
import freenet.client.ClientMetadata;
import freenet.client.InsertBlock;
//import freenet.client.InsertContext;
import freenet.client.InsertException;
//import freenet.client.PutWaiter;
//import freenet.client.async.ClientPutter;
import freenet.client.async.ClientContext;
import freenet.client.events.ClientEventListener;
import freenet.client.events.ClientEvent;
import freenet.client.events.SplitfileProgressEvent;
import freenet.keys.FreenetURI;
import freenet.node.RequestStarter;
import freenet.node.NodeClientCore;
import freenet.support.Logger;
import freenet.support.api.Bucket;
import freenet.support.io.Closer;

import java.io.OutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

/**
** @author infinity0
*/
public class FreenetArchiver<T extends Map<String, ? extends Object>> // TODO maybe get rid of type restriction
implements LiveArchiver<T, SimpleProgress> {

	final protected NodeClientCore core;
	final protected ObjectStreamReader reader;
	final protected ObjectStreamWriter writer;

	final protected String default_mime;
	final protected int expected_bytes;

	public FreenetArchiver(NodeClientCore c, ObjectStreamReader r, ObjectStreamWriter w, String mime, int size) {
		if (c == null) {
			throw new IllegalArgumentException("Can't create a FreenetArchiver with a null NodeClientCore!");
		}
		core = c;
		reader = r;
		writer = w;
		default_mime = mime;
		expected_bytes = size;
	}

	public <S extends ObjectStreamWriter & ObjectStreamReader> FreenetArchiver(NodeClientCore c, S rw, String mime, int size) {
		this(c, rw, rw, mime, size);
	}

	@Override public void pullLive(PullTask<T> task, final SimpleProgress progress) throws TaskAbortException {
		throw new UnsupportedOperationException("not implemented");
	}

	@Override public void pushLive(PushTask<T> task, final SimpleProgress progress) throws TaskAbortException {
		HighLevelSimpleClient hlsc = core.makeClient(RequestStarter.INTERACTIVE_PRIORITY_CLASS);
		Bucket tempB = null; OutputStream os = null;

		try {
			try {
				tempB = core.tempBucketFactory.makeBucket(expected_bytes, 2);
				os = tempB.getOutputStream();
				writer.writeObject(task.data, os);
				os.close(); os = null;
				tempB.setReadOnly();

				// PRIORITY do the USK_base next-suggested-edition thing. source in plugins.WoT.IdentityInserter
				FreenetURI target = FreenetURI.EMPTY_CHK_URI;
				InsertBlock ib = new InsertBlock(tempB, new ClientMetadata(default_mime), target);
				tempB = null; // let GC know we don't need to reference this again from this scope

				if (progress != null) {
					final int[] splitfile_blocks = new int[2];
					hlsc.addEventHook(new ClientEventListener() {
						@Override public void onRemoveEventProducer(ObjectContainer container) { }
						@Override public void receive(ClientEvent ce, ObjectContainer maybeContainer, ClientContext context) {
							progress.setStatus(ce.getDescription());
							if (!(ce instanceof SplitfileProgressEvent)) { return; }

							// update the progress "parts" counters
							SplitfileProgressEvent evt = (SplitfileProgressEvent)ce;
							synchronized (splitfile_blocks) {
								int old_succeeded = splitfile_blocks[0];
								int old_total = splitfile_blocks[1];
								try {
									progress.addPartKnown(evt.totalBlocks - old_total, evt.finalizedTotal); // throws IllegalArgumentException
									int n = evt.succeedBlocks - old_succeeded;
									if (n == 1) {
										progress.addPartDone();
									} else if (n != 0) {
										Logger.normal(this, "Received SplitfileProgressEvent out-of-order: " + evt.getDescription());
										for (int i=0; i<n; ++i) {
											progress.addPartDone();
										}
									}
								} catch (IllegalArgumentException e) {
									Logger.normal(this, "Received SplitfileProgressEvent out-of-order: " + evt.getDescription(), e);
								}
								splitfile_blocks[0] = evt.succeedBlocks;
								splitfile_blocks[1] = evt.totalBlocks;
							}
						}
					});
				}

				// code for async insert - maybe be useful elsewhere
				//ClientContext cctx = core.clientContext;
				//InsertContext ictx = hlsc.getInsertContext(true);
				//PutWaiter pw = new PutWaiter();
				//ClientPutter pu = hlsc.insert(ib, false, null, false, ictx, pw);
				//pu.setPriorityClass(RequestStarter.INTERACTIVE_PRIORITY_CLASS, cctx, null);
				//FreenetURI uri = pw.waitForCompletion();

				FreenetURI uri;

				// bookkeeping. detects bugs in the SplitfileProgressEvent handler
				if (progress != null) {
					int partsdiff_old = progress.partsTotal() - progress.partsDone();
					uri = hlsc.insert(ib, false, null);
					int partsdiff_new = progress.partsTotal() - progress.partsDone();
					if (partsdiff_old != partsdiff_new) {
						// TODO if it turns out this happens a lot, maybe make it "continue anyway"
						throw new TaskAbortException("Inconsistency when tracking split file progress", null, true);
					}
					progress.addPartKnown(0, true);
				} else {
					uri = hlsc.insert(ib, false, null);
				}

				task.meta = uri;

			} catch (InsertException e) {
				throw new TaskAbortException("Failed to insert content", e, true);

			} catch (IOException e) {
				throw new TaskAbortException("Failed to write content to local tempbucket", e, true);

			} catch (RuntimeException e) {
				throw new TaskAbortException("Failed to complete task: ", e);

			}
		} catch (TaskAbortException e) {
			if (progress != null) { progress.abort(e); }
			throw e;

		} finally {
			Closer.close(os);
			Closer.close(tempB);
		}
	}

	@Override public void pull(PullTask<T> task) throws TaskAbortException {
		pullLive(task, null);
	}

	@Override public void push(PushTask<T> task) throws TaskAbortException {
		pushLive(task, null);
	}

}