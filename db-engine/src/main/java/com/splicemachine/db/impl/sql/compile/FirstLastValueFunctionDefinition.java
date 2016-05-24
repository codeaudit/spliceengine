package com.splicemachine.db.impl.sql.compile;

import com.splicemachine.db.iapi.error.StandardException;
import com.splicemachine.db.iapi.reference.ClassName;
import com.splicemachine.db.iapi.services.context.ContextService;
import com.splicemachine.db.iapi.sql.compile.AggregateDefinition;
import com.splicemachine.db.iapi.sql.conn.LanguageConnectionContext;
import com.splicemachine.db.iapi.types.DataTypeDescriptor;
import com.splicemachine.db.iapi.types.TypeId;

/**
 * @author Jeff Cunningham
 *         Date: 9/30/15
 */
public class FirstLastValueFunctionDefinition implements AggregateDefinition {

    /** */
    public static final String IGNORE_NULLS = "IGNORE_NULLS";

    @Override
    public DataTypeDescriptor getAggregator(DataTypeDescriptor inputType, StringBuffer aggregatorClassName) throws StandardException {
        aggregatorClassName.append(ClassName.FirstLastValueFunction);
        LanguageConnectionContext lcc = (LanguageConnectionContext)
            ContextService.getContext(LanguageConnectionContext.CONTEXT_ID);

        /*
        ** First/LastValue returns same as its input
        */
        DataTypeDescriptor dts = inputType.getNullabilityType(true);
        TypeId compType = dts.getTypeId();

		/*
		** If the class implements orderable, then we
		** are in business.  Return type is same as input
		** type.
		*/
        if (compType.orderable(lcc.getLanguageConnectionFactory().getClassFactory())) {

            return dts;
        }
        return null;
    }
}