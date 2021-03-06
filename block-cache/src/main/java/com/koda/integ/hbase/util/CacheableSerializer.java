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
package com.koda.integ.hbase.util;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import com.koda.integ.hbase.blockcache.OffHeapBlockCache;
import com.koda.integ.hbase.stub.ByteArrayCacheable;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.hbase.io.hfile.Cacheable;
import org.apache.hadoop.hbase.io.hfile.CacheableDeserializer;
import org.apache.hadoop.hbase.io.hfile.HFileBlock;

import com.koda.io.serde.Serializer;

// TODO: Auto-generated Javadoc
/**
 * The Class CacheableSerializer.
 */
public class CacheableSerializer implements Serializer<Cacheable> {
	  /** The Constant LOG. */
	  static final Log LOG = LogFactory.getLog(CacheableSerializer.class);
	/** The id. */
	private static int ID = START_ID - 4;
	
	/** The deserializer. */
	private static AtomicReference<CacheableDeserializer<Cacheable>> deserializer = 
		new AtomicReference<CacheableDeserializer<Cacheable>>(null); 
	
	/* (non-Javadoc)
	 * @see com.koda.io.serde.Serializer#getClassID(java.lang.Class)
	 */
	@Override
	public int getClassID(Class<?> clz) {
		return CacheableSerializer.ID;
	}

	/* (non-Javadoc)
	 * @see com.koda.io.serde.Serializer#getClassNamesAsList()
	 */
	@Override
	public List<String> getClassNamesAsList() {
		return Arrays.asList(new String[]{ org.apache.hadoop.hbase.io.hfile.HFileBlock.class.getName(),
				ByteArrayCacheable.class.getName() });
	}

	/* (non-Javadoc)
	 * @see com.koda.io.serde.Serializer#getClassesAsList()
	 */
	@Override
	public List<Class<?>> getClassesAsList() {
		return Arrays.asList(new Class<?>[]{ org.apache.hadoop.hbase.io.hfile.HFileBlock.class,
				ByteArrayCacheable.class});
	}

	/* (non-Javadoc)
	 * @see com.koda.io.serde.Serializer#init()
	 */
	@Override
	public void init() {
		// TODO Auto-generated method stub
		
	}

	/* (non-Javadoc)
	 * @see com.koda.io.serde.Serializer#read(java.nio.ByteBuffer)
	 */
	@Override
	public Cacheable read(ByteBuffer buf) throws IOException {
		if(deserializer == null || deserializer.get() == null){
			return null;
		}
		ByteBuffer bbuf = buf.slice();
		bbuf.order(buf.order());
		return deserializer.get().deserialize(bbuf);				
	}

	/* (non-Javadoc)
	 * @see com.koda.io.serde.Serializer#read(java.nio.ByteBuffer, java.lang.Object)
	 */
	@Override
	public Cacheable read(ByteBuffer buf, Cacheable value) throws IOException {
		return read(buf);
	}

	/* (non-Javadoc)
	 * @see com.koda.io.serde.Serializer#write(java.nio.ByteBuffer, java.lang.Object)
	 */
	@Override
	public void write(ByteBuffer buf, Cacheable obj) throws IOException {
		if( deserializer.get() == null){
			CacheableDeserializer<Cacheable> des = obj.getDeserializer();		
			deserializer.compareAndSet(null, des);
		}
		// Serializer does not honor current buffer position
		int len = obj.getSerializedLength();
		int pos = buf.position();
		obj.serialize(buf);		
		buf.limit(len + pos);
		buf.position(len+pos);
	}
	
	public static CacheableDeserializer<Cacheable> getDeserializer()
	{
		return deserializer.get();
	}
	
	public static void setSerializer(CacheableDeserializer<Cacheable> ser)
	{
		deserializer.set(ser);
	}
	
	@SuppressWarnings("unchecked")
	public static void setHFileDeserializer()
	{
        Field field = getProtectedField(HFileBlock.class, "blockDeserializer");

        if (field == null){
            LOG.error("Could not get access to HFileBlock.blockDeserializer");
        	return;
        }

        try
        {
        	CacheableDeserializer<Cacheable> serde = (CacheableDeserializer<Cacheable>) field.get(null);
        	if(serde != null){
        		deserializer.set(serde);
        	} else{
        		LOG.warn("HFileBlock.blockDeserializer is null");
        	}
        }
        catch (Exception e)
        {
            LOG.warn("unable to read HFileBlock.blockDeserializer");
        }
        
	}
	
    @SuppressWarnings("unchecked")
    private static Field getProtectedField(Class klass, String fieldName)
    {
        Field field;

        try
        {
            field = klass.getDeclaredField(fieldName);
            field.setAccessible(true);
        }
        catch (Exception e)
        {
            throw new AssertionError(e);
        }

        return field;
    }

}
