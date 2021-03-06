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
package com.inclouds.hbase.test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.TreeMap;
import java.util.TreeSet;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.hbase.HColumnDescriptor;
import org.apache.hadoop.hbase.HTableDescriptor;
import org.apache.hadoop.hbase.KeyValue;
import org.apache.hadoop.hbase.client.Append;
import org.apache.hadoop.hbase.client.Delete;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.Increment;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.filter.Filter;
import org.apache.hadoop.hbase.io.TimeRange;
import org.apache.hadoop.hbase.util.Bytes;

import com.inclouds.hbase.rowcache.RConstants;
import com.inclouds.hbase.rowcache.RowCache;

import junit.framework.TestCase;

// TODO: Auto-generated Javadoc
/**
 * The Class BaseTest.
 */
public class BaseTest extends TestCase{

 	/** The Constant LOG. */
	 static final Log LOG = LogFactory.getLog(BaseTest.class);	
	/** The table a. */
	protected byte[] TABLE_A = "TABLE_A".getBytes();
	
	/** The table b. */
	protected byte[] TABLE_B = "TABLE_B".getBytes();
	
	/** The table c. */
	protected byte[] TABLE_C = "TABLE_C".getBytes();
	
	/* Families */
	/** The families. */
	protected byte[][] FAMILIES = new byte[][]
	      {"fam_a".getBytes(), "fam_b".getBytes(), "fam_c".getBytes()};
	
	
	/* Columns */
	/** The columns. */
	protected  byte[][] COLUMNS = 
	{"col_a".getBytes(), "col_b".getBytes(),  "col_c".getBytes()};
	
	/** The data. */
	static List<List<KeyValue>> data ;
	
	
	/** The versions. */
	int VERSIONS = 10;
	
	/** The cache. */
	static RowCache cache;
	
	/* All CF are cacheable */
	/** The table a. */
	static HTableDescriptor tableA;
	/* fam_c not cacheable */
	/** The table b. */
	static HTableDescriptor tableB;
	
	/* Not row cacheable */
	/** The table c. */
	static HTableDescriptor tableC;
	
	
	/**
	 * Cache row.
	 *
	 * @param table the table
	 * @param rowNum the row num
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	protected void cacheRow(HTableDescriptor table, int rowNum) throws IOException
	{
		List<KeyValue> list = data.get(rowNum);
		Get get = createGet(list.get(0).getRow(), null, null, null);
		get.setMaxVersions(Integer.MAX_VALUE);
		cache.resetRequestContext();
		cache.postGet(table, get, list);				
	}
	
	
	/**
	 * Gets the from cache.
	 *
	 * @param table the table
	 * @param row the row
	 * @param families the families
	 * @param columns the columns
	 * @return the from cache
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	protected List<KeyValue> getFromCache(HTableDescriptor table, byte[] row, List<byte[]> families, List<byte[]> columns) throws IOException
	{
		Map<byte[], NavigableSet<byte[]>> map = constructFamilyMap(families, columns);
		Get get = createGet(row, map, null, null);	
		get.setMaxVersions(Integer.MAX_VALUE);
		List<KeyValue> results = new ArrayList<KeyValue>();
		cache.preGet(table, get, results);		
		return results;
		
	}
	
	
	/**
	 * Constract family map.
	 *
	 * @param families the families
	 * @param columns the columns
	 * @return the map
	 */
	protected Map<byte[], NavigableSet<byte[]>> constructFamilyMap(List<byte[]> families, List<byte[]> columns)
	{
		Map<byte[], NavigableSet<byte[]>> map = new TreeMap<byte[], NavigableSet<byte[]>>(Bytes.BYTES_COMPARATOR);
		NavigableSet<byte[]> colSet = getColumnSet(columns);
		for(byte[] f: families){
			map.put(f, colSet);
		}
		return map;
	}
	
	/**
	 * Gets the row.
	 *
	 * @param i the i
	 * @return the row
	 */
	byte[] getRow (int i){
		return ("row"+i).getBytes();
	}
	
	/**
	 * Gets the value.
	 *
	 * @param i the i
	 * @return the value
	 */
	byte[] getValue (int i){
		return ("value"+i).getBytes();
	}
	
	
	/**
	 * Generate row data.
	 *
	 * @param i the i
	 * @return the list
	 */
	List<KeyValue> generateRowData(int i){
		byte[] row = getRow(i);
		byte[] value = getValue(i);
		long startTime = System.currentTimeMillis();
		ArrayList<KeyValue> list = new ArrayList<KeyValue>();
		int count = 0;
		for(byte[] f: FAMILIES){
			for(byte[] c: COLUMNS){
				count = 0;
				for(; count < VERSIONS; count++){
					KeyValue kv = new KeyValue(row, f, c, startTime + (count),  value);	
					list.add(kv);
				}
			}
		}
		
		Collections.sort(list, KeyValue.COMPARATOR);
		
		return list;
	}
	
	
	/**
	 * Generate data.
	 *
	 * @param n the n
	 * @return the list
	 */
	List<List<KeyValue>> generateData(int n)
	{
		List<List<KeyValue>> data = new ArrayList<List<KeyValue>>();
		for(int i=0; i < n; i++){
			data.add(generateRowData(i));
		}
		return data;
	}
	
	/**
	 * Creates the tables.
	 *
	 * @param versions the versions
	 */
	protected void createTables(int versions) {
		
		HColumnDescriptor famA = new HColumnDescriptor(FAMILIES[0]);
		famA.setValue(RConstants.ROWCACHE, "true".getBytes());
		famA.setMaxVersions(versions);
		
		HColumnDescriptor famB = new HColumnDescriptor(FAMILIES[1]);
		famB.setValue(RConstants.ROWCACHE, "true".getBytes());
		famB.setMaxVersions(versions);		
		HColumnDescriptor famC = new HColumnDescriptor(FAMILIES[2]);
		famC.setValue(RConstants.ROWCACHE, "true".getBytes());
		famC.setMaxVersions(versions);	
		
		tableA = new HTableDescriptor(TABLE_A);
		tableA.addFamily(famA);
		tableA.addFamily(famB);
		tableA.addFamily(famC);
		
		famA = new HColumnDescriptor(FAMILIES[0]);
		famA.setValue(RConstants.ROWCACHE, "true".getBytes());
		famA.setMaxVersions(versions);
		
		famB = new HColumnDescriptor(FAMILIES[1]);
		famB.setValue(RConstants.ROWCACHE, "true".getBytes());
		famB.setMaxVersions(versions);
		
		famC = new HColumnDescriptor(FAMILIES[2]);
		famC.setMaxVersions(versions);
		
		tableB = new HTableDescriptor(TABLE_B);
		tableB.addFamily(famA);
		tableB.addFamily(famB);
		tableB.addFamily(famC);	
		
		famA = new HColumnDescriptor(FAMILIES[0]);
		famA.setMaxVersions(versions);
		famB = new HColumnDescriptor(FAMILIES[1]);
		famB.setMaxVersions(versions);
		famC = new HColumnDescriptor(FAMILIES[2]);
		famC.setMaxVersions(versions);
		tableC = new HTableDescriptor(TABLE_C);
		tableC.addFamily(famA);
		tableC.addFamily(famB);
		tableC.addFamily(famC);		
		
		
	}
	
 	
 	/**
	 * Creates the get.
	 *
	 * @param row the row
	 * @param familyMap the family map
	 * @param tr the tr
	 * @param f the f
	 * @return the gets the
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	protected Get createGet(byte[] row, Map<byte[], NavigableSet<byte[]>> familyMap, TimeRange tr, Filter f ) throws IOException
	{
		Get get = new Get(row);
		if(tr != null){
			get.setTimeRange(tr.getMin(), tr.getMax());
		}
		if(f != null) get.setFilter(f);
		
		if(familyMap != null){
			for(byte[] fam: familyMap.keySet())
			{
				NavigableSet<byte[]> cols = familyMap.get(fam);
				if( cols == null || cols.size() == 0){
					get.addFamily(fam);
				} else{
					for(byte[] col: cols)
					{
						get.addColumn(fam, col);
					}
				}
			}
		}
		return get;
	}
	
	/**
	 * Creates the put.
	 *
	 * @param values the values
	 * @return the put
	 */
	protected Put createPut(List<KeyValue> values)
	{
		Put put = new Put(values.get(0).getRow());
		for(KeyValue kv: values)
		{
			put.add(kv.getFamily(), kv.getQualifier(), kv.getTimestamp(), kv.getValue());
		}
		return put;
	}
	
	
	/**
	 * Creates the increment.
	 *
	 * @param row the row
	 * @param familyMap the family map
	 * @param tr the tr
	 * @param value the value
	 * @return the increment
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	protected Increment createIncrement(byte[] row, Map<byte[], NavigableSet<byte[]>> familyMap, TimeRange tr, long value) 
	throws IOException
	{
		Increment incr = new Increment(row);
		if(tr != null){
			incr.setTimeRange(tr.getMin(), tr.getMax());
		}

		
		if(familyMap != null){
			for(byte[] fam: familyMap.keySet())
			{
				NavigableSet<byte[]> cols = familyMap.get(fam);

					for(byte[] col: cols)
					{
						incr.addColumn(fam, col, value);
					}
				
			}
		}
		return incr;		
	}
	
	/**
	 * Creates the append.
	 *
	 * @param row the row
	 * @param families the families
	 * @param columns the columns
	 * @param value the value
	 * @return the append
	 */
	protected Append createAppend(byte[] row, List<byte[]> families, List<byte[]> columns, byte[] value){
		
		Append op = new Append(row);
		
		for(byte[] f: families){
			for(byte[] c: columns){
				op.add(f, c, value);
			}
		}
		return op;
	}
	/**
	 * Creates the delete.
	 *
	 * @param values the values
	 * @return the delete
	 */
	protected Delete createDelete(List<KeyValue> values)
	{
		Delete del = new Delete(values.get(0).getRow());
		for(KeyValue kv: values)
		{
			del.deleteColumns(kv.getFamily(), kv.getQualifier());
		}
		return del;
	}
	
	/**
	 * Creates the delete.
	 *
	 * @param row the row
	 * @return the delete
	 */
	protected Delete createDelete(byte[] row)
	{
		Delete del = new Delete(row);
		return del;
	}
	
	/**
	 * Creates the delete.
	 *
	 * @param row the row
	 * @param families the families
	 * @return the delete
	 */
	protected Delete createDelete(byte[] row, List<byte[]> families)
	{
		Delete del = new Delete(row);
		for(byte[] f: families)
		{
			del.deleteFamily(f);
		}
		return del;
	}

	/**
	 * Equals.
	 *
	 * @param list1 the list1
	 * @param list2 the list2
	 * @return true, if successful
	 */
	protected boolean equals(List<KeyValue> list1, List<KeyValue> list2)
	{
		if(list1.size() != list2.size()) return false;
		Collections.sort(list1, KeyValue.COMPARATOR);
		Collections.sort(list2, KeyValue.COMPARATOR);	
		for(int i=0; i < list1.size(); i++){
		  KeyValue first = list1.get(i);
		  KeyValue second = list2.get(i);
			if(first.equals(second) == false) return false;
		}
		return true;
	}
	
	 protected boolean equalsNoTS(List<KeyValue> list1, List<KeyValue> list2)
	  {
	    if(list1.size() != list2.size()) return false;
	    Collections.sort(list1, KeyValue.COMPARATOR);
	    Collections.sort(list2, KeyValue.COMPARATOR); 
	    for(int i=0; i < list1.size(); i++){
	      KeyValue first = list1.get(i);
	      KeyValue second = list2.get(i);
	      //LOG.info(i +" ["+ new String(first.getRow())+"]"+ "["+new String(second.getRow())+"]");
	      int r1 = Bytes.compareTo(first.getRow(), second.getRow());
	      if(r1 != 0) return false;
	      // LOG.info(i+ " ["+ new String(first.getFamily())+"]"+ "["+new String(second.getFamily())+"]");
	      int r2 = Bytes.compareTo(first.getFamily(), second.getFamily());
	      if(r2 != 0) return false;
	      // LOG.info(i+" ["+ new String(first.getQualifier())+"]"+ "["+new String(second.getQualifier())+"]");

	      int r3 = Bytes.compareTo(first.getQualifier(), second.getQualifier());
	      if(r3 != 0) return false;
	      // LOG.info(i +" ["+ new String(first.getValue())+"]"+ "["+new String(second.getValue())+"]");

	      int r4 = Bytes.compareTo(first.getValue(), second.getValue());
	      if(r4 != 0) {
	        //LOG.info(i +" ["+ new String(first.getValue())+"]"+ "["+new String(second.getValue())+"] ***************");
	        return false;
	      } 
	    }
	    return true;
	  }
	/**
	 * Sub list.
	 *
	 * @param list the list
	 * @param family the family
	 * @return the list
	 */
	protected List<KeyValue> subList(List<KeyValue> list, byte[] family){
		List<KeyValue> result = new ArrayList<KeyValue>();
		for(KeyValue kv : list){
			if(Bytes.equals(family, kv.getFamily())){
				result.add(kv);
			}
		}
		return result;
	}
	
	/**
	 * Sub list.
	 *
	 * @param list the list
	 * @param family the family
	 * @param cols the cols
	 * @return the list
	 */
	protected List<KeyValue> subList(List<KeyValue> list, byte[] family, List<byte[]> cols){
		List<KeyValue> result = new ArrayList<KeyValue>();
		for(KeyValue kv : list){
			if(Bytes.equals(family, kv.getFamily())){
				byte[] col = kv.getQualifier();
				for(byte[] c: cols){					
					if(Bytes.equals(col, c)){
						result.add(kv); break;
					}
				}
				
			}
		}
		return result;
	}
	
	/**
	 * Sub list.
	 *
	 * @param list the list
	 * @param families the families
	 * @param cols the cols
	 * @return the list
	 */
	protected List<KeyValue> subList(List<KeyValue> list, List<byte[]> families, List<byte[]> cols){
		List<KeyValue> result = new ArrayList<KeyValue>();
		for(KeyValue kv : list){
			for(byte[] family: families){				
				if(Bytes.equals(family, kv.getFamily())){
					byte[] col = kv.getQualifier();
					for(byte[] c: cols){					
						if(Bytes.equals(col, c)){
							result.add(kv); break;
						}
					}				
				}
			}
		}
		return result;
	}
	
	
	/**
	 * Sub list.
	 *
	 * @param list the list
	 * @param families the families
	 * @param cols the cols
	 * @param max the max
	 * @return the list
	 */
	protected List<KeyValue> subList(List<KeyValue> list, List<byte[]> families, List<byte[]> cols, int max){
		List<KeyValue> result = new ArrayList<KeyValue>();
		for(KeyValue kv : list){
			for(byte[] family: families){				
				if(Bytes.equals(family, kv.getFamily())){
					byte[] col = kv.getQualifier();
					for(byte[] c: cols){					
						if(Bytes.equals(col, c)){
							result.add(kv); break;
						}
					}				
				}
			}
		}
		
		int current = 0;
		byte[] f = result.get(0).getFamily();
		byte[] c = result.get(0).getQualifier();
		
		List<KeyValue> ret = new ArrayList<KeyValue>();
		
		for(KeyValue kv : result ){
			byte[] fam = kv.getFamily();
			byte[] col = kv.getQualifier();
			if(Bytes.equals(f, fam) ){
				if( Bytes.equals(c, col)){
					if( current < max){
						ret.add(kv);
					}
					current++;
				} else{
					c = col; current = 1;
					ret.add(kv);
				}
			} else {
				f = fam; c = col; current = 1;
				ret.add(kv);
			}
		}
		return ret;
	}
	/**
	 * Gets the column set.
	 *
	 * @param cols the cols
	 * @return the column set
	 */
	protected NavigableSet<byte[]> getColumnSet(List<byte[]> cols)
	{
		if(cols == null) return null;
		TreeSet<byte[]> set = new TreeSet<byte[]>(Bytes.BYTES_COMPARATOR);
		for(byte[] c: cols){
			set.add(c);
		}
		return set;
	}
	
	/**
	 * Dump.
	 *
	 * @param list the list
	 */
	protected void dump(List<KeyValue> list)
	{
		for( KeyValue kv : list){
			dump(kv);
		}
	}
	
	/**
	 * Dump.
	 *
	 * @param kv the kv
	 */
	protected void dump(KeyValue kv)
	{
		LOG.info("row="+new String(kv.getRow())+" family="+ new String(kv.getFamily())+
				" column="+new String(kv.getQualifier()) + " ts="+ kv.getTimestamp());
	}
	
	/**
	 * Patch row.
	 *
	 * @param kv the kv
	 * @param patch the patch
	 */
	protected void patchRow(KeyValue kv, byte[] patch)
	{
		int off = kv.getRowOffset();
		System.arraycopy(patch, 0, kv.getBuffer(), off, patch.length);
	}
	
}
