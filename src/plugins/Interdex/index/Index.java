/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Interdex.index;

import java.util.Map;
import java.util.SortedSet;
import java.util.SortedMap;
import java.util.TreeSet;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Date;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import freenet.keys.FreenetURI;

import plugins.Interdex.util.SkeletonPrefixTreeMap;
import plugins.Interdex.util.SkeletonMap;
import plugins.Interdex.util.DataNotLoadedException;

/**
** Represents the data for an index.
**
** PRIORITY make this into an interface and put the actual implementation in
** ProtoIndex.java
**
** PRIORITY work out locking
**
** @author infinity0
*/
public class Index {

	final public static int TKTAB_MAX = 4096;
	final public static int UTAB_MAX = 4096;

	/**
	** Magic number to guide serialisation.
	*/
	final public static long MAGIC = 0xf82a9084681e5ba6L;

	/**
	** Freenet ID for this index
	*/
	protected FreenetURI id;

	/**
	** Name for this index.
	*/
	protected String name;

	/**
	** Last time this index was modified.
	*/
	protected Date modified;

	/**
	** Extra configuration options for the index.
	*/
	final protected Map<String, Object> extra;

	/**
	** Whether this index is writeable.
	*/
	final public transient boolean writeable;

	/* *
	** Filter table. Used to make checks quicker.
	** TODO implement
	*/
	//protected SkeletonMap<Token, TokenFilter> filtab;

	/**
	** Token table. Provides information related to a Token-URI pairing.
	*/
	protected SkeletonMap<Token, SortedSet<TokenEntry>> tktab;

	/**
	** URI table. Provides information related to a URI.
	*/
	protected SkeletonMap<URIKey, SortedMap<FreenetURI, URIEntry>> utab;

	/**
	** Read-write lock. PRIORITY: actually use this.
	*/
	final private transient ReadWriteLock lock = new ReentrantReadWriteLock();

	public Index(FreenetURI i, String n) {
		id = i;
		name = (n == null)? "": n;
		writeable = true;
		modified = new Date();
		extra = new HashMap<String, Object>();

		utab = new SkeletonPrefixTreeMap<URIKey, SortedMap<FreenetURI, URIEntry>>(new URIKey(), UTAB_MAX);
		tktab = new SkeletonPrefixTreeMap<Token, SortedSet<TokenEntry>>(new Token(), TKTAB_MAX);
		//filtab = new SkeletonPrefixTreeMap<Token, TokenFilter>(new Token(), TKTAB_MAX);
	}

	/**
	** This constructor is used by the {@link IndexFileSerialiser.IndexSerialiser.IndexTranslator
	** translator} to create a skeleton index.
	*/
	protected Index(FreenetURI i, String n, Date m, Map<String, Object> x,
		SkeletonMap<URIKey, SortedMap<FreenetURI, URIEntry>> u,
		SkeletonMap<Token, SortedSet<TokenEntry>> t/*,
		SkeletonMap<Token, TokenFilter> f*/
		) {
		id = i;
		name = (n == null)? "": n;
		writeable = false;
		modified = m;
		extra = x;

		//filtab = f;
		tktab = t;
		utab = u;
	}

	public long getMagic() {
		return MAGIC;
	}

	/**
	** DOCUMENT
	**
	** TODO make Index extend a Skeleton class or something.
	*/
	public boolean isLive() {
		return utab.isLive() && tktab.isLive()/* && filtab.isLive()*/;
	}

	public boolean isBare() {
		return utab.isBare() && tktab.isBare()/* && filtab.isBare()*/;
	}

	public void inflate() {
		//filtab.inflate();
		tktab.inflate();
		utab.inflate();
	}

	public void deflate() {
		utab.deflate();
		tktab.deflate();
		//filtab.deflate();
	}

	/*
	** Search the index for a given keyword.
	**
	** PRIORITY make this support getting a subset of the entries.
	* /
	public synchronized SortedSet<TokenEntry> searchIndex(String keyword, int numberOfResults);
	*/

	/**
	** Fetch the TokenEntries associated with a given term.
	**
	** @param term The term to fetch the entries for
	** @param auto Whether to catch and handle {@link DataNotLoadedException}
	** @return The fetched entries
	** @throws DataNotLoadedException
	**         if the TokenEntries have not been loaded
	*/
	public synchronized SortedSet<TokenEntry> fetchTokenEntries(String term, boolean auto) {
		// TODO make this use the bloom filter
		if (auto) {
			// PRIORITY implement this using GhostTreeSet instead of TreeSet so we retrieve
			// a SkeletonSet rather than the whole map, and then run the autoload
			// algorithm on that again; see todo.txt for more details
			SortedSet<TokenEntry> entries;
			for (;;) {
				try {
					entries = tktab.get(new Token(term));
					break;
				} catch (DataNotLoadedException e) {
					// URGENT refactor DataNotLoadedException so that this cast is unnecessary
					((SkeletonMap)e.getParent()).deflate(e.getKey());
				}
			}
			return entries;
		} else {
			return tktab.get(new Token(term));
		}
	}

	/**
	** Clear all TokenEntries associated with a given term, and remove traces
	** of this term from any associated URIEntries.
	**
	** @param term The term to clear the entries for
	** @param auto Whether to catch and handle {@link DataNotLoadedException}
	** @return The cleared entries
	** @throws DataNotLoadedException
	**         if the TokenEntries or URIEntries have not been loaded
	*/
	public synchronized SortedSet<TokenEntry> clearTokenEntries(String term, boolean auto) {
		if (!writeable) { throw new IllegalStateException("Index is not writeable: " + id); }
		// for all TokenURIEntries in the set, remove the term from the
		// corresponding URIEntry's "terms" field
		throw new UnsupportedOperationException("Not implemented.");
	}

	/**
	** Insert a TokenEntry into the index, and (TODO) update the filter. If
	** appropriate, insert this term into the associated URIEntry.
	**
	** @param entry The entry to insert
	** @param auto Whether to catch and handle {@link DataNotLoadedException}
	** @return The previous entry
	** @throws DataNotLoadedException
	**         if the term's TokenEntries have not been loaded,
	**         or if the filter or URIEntry has not been loaded
	*/
	public synchronized TokenEntry insertTokenEntry(TokenEntry entry, boolean auto) {
		if (!writeable) { throw new IllegalStateException("Index is not writeable: " + id); }
		// if it's a TokenURIEntry, its URIEntry must already be in the table
		// in which case insert its subject to the URIEntry's "terms" field

		// insert entry into the tktab
		// insert entry into the filtab
		throw new UnsupportedOperationException("Not implemented.");
	}

	/**
	** Remove a TokenEntry from the index, and (TODO) updated the filter. If
	** appropriate, remove this term from the associated URIEntry.
	**
	** @param entry The entry to remove
	** @param auto Whether to catch and handle {@link DataNotLoadedException}.
	** @return The removed entry
	** @throws DataNotLoadedException
	**         if the term's TokenEntries have not been loaded,
	**         or if the filter or URIEntry has not been loaded
	*/
	public synchronized TokenEntry removeTokenEntry(TokenEntry entry, boolean auto) {
		if (!writeable) { throw new IllegalStateException("Index is not writeable: " + id); }
		// if it's a TokenURIEntry, its URIEntry must already be in the table
		// in which case remove its subject from that URIEntry's "terms" field

		// remove entry from the tktab
		// remove entry from the filtab
		throw new UnsupportedOperationException("Not implemented.");
	}

	/**
	** Fetch a URIEntry from the index by its FreenetURI.
	**
	** @param uri The URI to fetch the entry for
	** @param auto Whether to catch and handle {@link DataNotLoadedException}
	** @return The fetched entry
	** @throws DataNotLoadedException
	**         if the URIEntry has not been loaded
	*/
	public synchronized URIEntry fetchURIEntry(FreenetURI uri, boolean auto) {
		if (auto) {
			// PRIORITY implement this using GhostTreeMap instead of TreeMap so we retrieve
			// a SkeletonMap rather than the whole map, and then run the autoload
			// algorithm on that again; see todo.txt for more details
			SortedMap<FreenetURI, URIEntry> entries;
			for (;;) {
				try {
					entries = utab.get(new URIKey(uri));
					break;
				} catch (DataNotLoadedException e) {
					// URGENT refactor DataNotLoadedException so that this cast is unnecessary
					((SkeletonMap)e.getParent()).deflate(e.getKey());
				}
			}
			return entries.get(uri);
		} else {
			return utab.get(new URIKey(uri)).get(uri);
		}
	}

	/**
	** Clear the URIEntry associated with a given FreenetURI, and remove any
	** TokenURIEntries that point to this entry.
	**
	** @param uri The URI to clear the entry for
	** @param auto Whether to catch and handle {@link DataNotLoadedException}
	** @return The cleared entry
	** @throws DataNotLoadedException
	**         if the URIEntry has not been loaded,
	**         or if the TokenEntries for any term has not been loaded
	*/
	public synchronized URIEntry clearURIEntry(FreenetURI uri, boolean auto) {
		if (!writeable) { throw new IllegalStateException("Index is not writeable: " + id); }
		// go through the "terms" and remove the TokenEntries associated with
		// them from the index. this is expensive but it is hoped that this
		// operation won't be called so often.
		throw new UnsupportedOperationException("Not implemented.");
	}

	/**
	** Insert a URIEntry into the index.
	**
	** @param entry The entry to insert
	** @param auto Whether to catch and handle {@link DataNotLoadedException}
	** @return The previous entry
	** @throws DataNotLoadedException
	**         if the URIEntry has not been loaded
	*/
	public synchronized URIEntry insertURIEntry(URIEntry entry, boolean auto) {
		if (!writeable) { throw new IllegalStateException("Index is not writeable: " + id); }
		// make sure "terms" is empty. if the index is consistent (which we
		// assume it is, in between method calls) then it is impossible for
		// "terms" to be non-empty due to the restrictions on insertTokenEntry
		throw new UnsupportedOperationException("Not implemented.");
	}

	/**
	** Remove a URIEntry from the index, and all TokenEntries associated with
	** it.
	**
	** @param entry The entry to remove
	** @param auto Whether to catch and handle {@link DataNotLoadedException}
	** @return The removed entry
	** @throws DataNotLoadedException
	**         if the URIEntry has not been loaded,
	**         or if the TokenEntries for any term has not been loaded
	*/
	public synchronized URIEntry removeURIEntry(URIEntry entry, boolean auto) {
		if (!writeable) { throw new IllegalStateException("Index is not writeable: " + id); }
		// go through the "terms" and remove the TokenEntries associated with
		// them from the index. this is expensive but it is hoped that this
		// operation won't be called so often.
		throw new UnsupportedOperationException("Not implemented.");
	}


}
