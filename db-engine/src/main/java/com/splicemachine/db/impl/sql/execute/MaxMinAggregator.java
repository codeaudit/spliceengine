/*
 * Apache Derby is a subproject of the Apache DB project, and is licensed under
 * the Apache License, Version 2.0 (the "License"); you may not use these files
 * except in compliance with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * Splice Machine, Inc. has modified this file.
 *
 * All Splice Machine modifications are Copyright 2012 - 2016 Splice Machine, Inc.,
 * and are licensed to you under the License; you may not use this file except in
 * compliance with the License.
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 */

package com.splicemachine.db.impl.sql.execute;

import com.splicemachine.db.iapi.error.StandardException;
import com.splicemachine.db.iapi.services.io.StoredFormatIds;
import com.splicemachine.db.iapi.services.loader.ClassFactory;
import com.splicemachine.db.iapi.sql.execute.ExecAggregator;
import com.splicemachine.db.iapi.types.DataTypeDescriptor;
import com.splicemachine.db.iapi.types.DataValueDescriptor;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

/**
 * Aggregator for MAX()/MIN().  Defers most of its work
 * to OrderableAggregator.
 *
 * @see OrderableAggregator
 *
 */
public final class MaxMinAggregator extends OrderableAggregator {
	private boolean isMax; // true for max, false for min
	/**
	 */
	public ExecAggregator setup( ClassFactory cf, String aggregateName, DataTypeDescriptor returnType ) {
			super.setup( cf, aggregateName, returnType );
			isMax = aggregateName.equals("MAX");
			return this;
	}

	/**
	 * Accumulate
 	 *
	 * @param addend	value to be added in
	 *
	 * @exception StandardException on error
	 *
	 */
	protected void accumulate(DataValueDescriptor addend) throws StandardException {
		if (value == null)
			value = addend.cloneValue(false);			
		else {
			int compare = value.compare(addend);
			if ( (isMax && compare <0) || (!isMax && compare >0)) {
				value = addend.cloneValue(false);
			}
		}
	}

	/**
	 * @return ExecAggregator the new aggregator
	 */
	public ExecAggregator newAggregator()
	{
		MaxMinAggregator ma = new MaxMinAggregator();
		ma.isMax = isMax;
		return ma;
	}

	/////////////////////////////////////////////////////////////
	// 
	// FORMATABLE INTERFACE
	// 
	// Formatable implementations usually invoke the super()
	// version of readExternal or writeExternal first, then
	// do the additional actions here. However, since the
	// superclass of this class requires that its externalized
	// data must be the last data in the external stream, we
	// invoke the superclass's read/writeExternal method
	// last, not first. See DERBY-3219 for more discussion.
	/////////////////////////////////////////////////////////////
	public void writeExternal(ObjectOutput out) throws IOException
	{
		out.writeBoolean(isMax);
		super.writeExternal(out);
	}

	/** 
	 * @see java.io.Externalizable#readExternal 
	 *
	 * @exception IOException on error
	 * @exception ClassNotFoundException on error
	 */
	public void readExternal(ObjectInput in) 
		throws IOException, ClassNotFoundException {
		isMax = in.readBoolean();
		super.readExternal(in);
	}
	/**
	 * Get the formatID which corresponds to this class.
	 *
	 *	@return	the formatID of this class
	 */
	public	int	getTypeFormatId()	{ return StoredFormatIds.AGG_MAX_MIN_V01_ID; }
    public String toString() {
    	if (isMax)
    		return "Max (" + (value !=null?value:"NULL") + ")";
		return "Min (" + (value !=null?value:"NULL") + ")";
    }
}