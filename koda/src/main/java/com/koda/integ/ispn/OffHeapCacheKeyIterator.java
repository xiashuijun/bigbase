/*******************************************************************************
* Copyright (c) 2013 Vladimir Rodionov. All Rights Reserved
*
* This code is released under the GNU Affero General Public License.
*
* See: http://www.fsf.org/licensing/licenses/agpl-3.0.html
*
* VLADIMIR RODIONOV MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE SUITABILITY
* OF THE SOFTWARE, EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE
* IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, OR
* NON-INFRINGEMENT. Vladimir Rodionov SHALL NOT BE LIABLE FOR ANY DAMAGES SUFFERED
* BY LICENSEE AS A RESULT OF USING, MODIFYING OR DISTRIBUTING THIS SOFTWARE OR
* ITS DERIVATIVES.
*
* Author: Vladimir Rodionov
*
*******************************************************************************/
package com.koda.integ.ispn;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.NoSuchElementException;

import com.koda.cache.CacheScanner;
import com.koda.cache.OffHeapCache;
import com.koda.io.serde.SerDe;

// TODO: Auto-generated Javadoc
/**
 * The Class OffHeapCacheKeyIterator.
 */
public class OffHeapCacheKeyIterator implements Iterator<Object>{

	/** The m cache. */
	private OffHeapCache mCache;
	
	/** The m scanner. */
	private CacheScanner mScanner;
	
	/** The m last returned. */
	Object mLastReturned;
	
	/**
	 * Instantiates a new off heap cache key iterator.
	 *
	 * @param cache the cache
	 */
	OffHeapCacheKeyIterator(OffHeapCache cache)
	{
		this.mCache = cache;
		mScanner = mCache.getScanner(0, 1);
	}
	
	/* (non-Javadoc)
	 * @see java.util.Iterator#hasNext()
	 */
	@Override
	public boolean hasNext() {
		boolean v = mScanner.hasNext();
		if(!v) {
			mScanner.close();
		}
		return v;
	}

	/* (non-Javadoc)
	 * @see java.util.Iterator#next()
	 */
	@Override
	public Object next() {
		
		SerDe serde = mCache.getSerDe();
		try {
			ByteBuffer buf = mScanner.nextRecord();
			if(buf == null) {
				mLastReturned = null;
				close();
				throw new NoSuchElementException();
			}
			buf.position(8);
			mLastReturned = serde.readCompressed(buf/*, mCache.getCompressionCodec()*/);
			return mLastReturned;
		} catch (Exception e) {			
			throw new RuntimeException(e);
		} 

	}

	/* (non-Javadoc)
	 * @see java.util.Iterator#remove()
	 */
	@Override
	public void remove() {
		
		if(mLastReturned == null) 
			throw new IllegalStateException();
		
		Object key = mLastReturned;
		try {
			mCache.remove(key);
		} catch (Exception e) {
			throw new RuntimeException(e);
		} finally{
			mLastReturned = null;
		}
	}

	
	/**
	 * Close.
	 */
	private void close()
	{
		if(mScanner != null){
			try{
				mScanner.close();
			}catch(Exception e){
				// Do nothing	
			}
		}
	}
}

