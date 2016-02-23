package com.splicemachine.derby.impl.kryo;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.google.common.base.Throwables;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.splicemachine.db.iapi.services.context.ContextService;
import com.splicemachine.db.iapi.services.loader.ClassFactory;
import com.splicemachine.db.iapi.sql.conn.LanguageConnectionContext;
import com.splicemachine.db.shared.common.udt.UDTBase;
import com.splicemachine.db.impl.sql.execute.ValueRow;
import com.splicemachine.derby.utils.EngineUtils;
import com.splicemachine.derby.utils.marshall.BareKeyHash;
import com.splicemachine.derby.utils.marshall.DataHash;
import com.splicemachine.derby.utils.marshall.KeyHashDecoder;
import com.splicemachine.derby.utils.marshall.dvd.DescriptorSerializer;
import com.splicemachine.derby.utils.marshall.dvd.UDTInputStream;
import com.splicemachine.derby.utils.marshall.dvd.VersionedSerializers;
import com.splicemachine.utils.IntArrays;
import com.splicemachine.utils.SpliceLogUtils;
import com.splicemachine.db.iapi.error.StandardException;
import com.splicemachine.db.iapi.services.io.StoredFormatIds;
import com.splicemachine.db.iapi.sql.execute.ExecRow;
import com.splicemachine.db.iapi.types.DataValueDescriptor;
import com.splicemachine.db.iapi.types.DataValueFactoryImpl;
import com.splicemachine.db.iapi.types.SQLDecimal;
import org.apache.log4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;

/**
 * @author Scott Fines
 * Created on: 10/10/13
 */
public abstract class SparkValueRowSerializer<T extends ExecRow> extends Serializer<T> {
    private static Logger LOG = Logger.getLogger(SparkValueRowSerializer.class);
    private LoadingCache<IntArray, DescriptorSerializer[]> serializersCache = CacheBuilder.newBuilder().maximumSize(10).build(
            new CacheLoader<IntArray, DescriptorSerializer[]>() {
                @Override
                public DescriptorSerializer[] load(IntArray intArray) {
                    DataValueDescriptor[] dvds = new DataValueDescriptor[intArray.array.length];
                    for (int i =0;i<intArray.array.length;i++) {
                        dvds[i] = getDVD(intArray.array[i]);
                    }
                    return VersionedSerializers.latestVersion(false).getSerializers(dvds);
                }
            }
    );

    @Override
    public void write(Kryo kryo, Output output, T object) {

        DataValueDescriptor[] dvds = object.getRowArray();
        int[] formatIds = EngineUtils.getFormatIds(dvds);
        DataHash encoder = getEncoder(dvds);
        output.writeInt(formatIds.length, true);
        try {
            for (int i = 0; i < formatIds.length; ++i) {
                int formatId = formatIds[i];
                output.writeInt(formatId, true);
                if (formatId == StoredFormatIds.SQL_USERTYPE_ID_V3) {
                    Object o = dvds[i].getObject();
                    boolean useKryo = false;
                    if (o != null && o instanceof UDTBase) {
                        // This is a UDT or UDA, do not serialize using Kryo
                        output.writeBoolean(useKryo);
                        ByteArrayOutputStream outputBuffer = new ByteArrayOutputStream();
                        ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputBuffer);
                        objectOutputStream.writeObject(o);
                        objectOutputStream.flush();
                        byte[] bytes = outputBuffer.toByteArray();
                        output.writeInt(bytes.length);
                        output.write(bytes);
                        objectOutputStream.close();

                    } else {
                        useKryo = true;
                        output.writeBoolean(useKryo);
                    }
                }
            }
        } catch(Exception e) {
            throw new RuntimeException(Throwables.getRootCause(e));
        }
        encoder.setRow(object);
        try {
            byte[] encoded = encoder.encode();
            output.writeInt(encoded.length, true);
            output.writeBytes(encoded);
        } catch (Exception e) {
            SpliceLogUtils.logAndThrowRuntime(LOG, "Exception while serializing row " + object, e);
        }
    }

    @Override
    public T read(Kryo kryo, Input input, Class<T> type) {
        int size = input.readInt(true);

        T instance = newType(size);

        int[] formatIds = new int[size];
        ExecRow execRow = new ValueRow(size);
        DataValueDescriptor[] rowTemplate = execRow.getRowArray();
        try {
            for (int i = 0; i < size; ++i) {
                formatIds[i] = input.readInt(true);
                rowTemplate[i] = getDVD(formatIds[i]);
                if (formatIds[i] == StoredFormatIds.SQL_USERTYPE_ID_V3) {
                    if (!input.readBoolean()) {
                        // This is a UDT or UDA
                        int len = input.readInt();
                        byte[] bytes = new byte[len];
                        input.read(bytes, 0, len);
                        LanguageConnectionContext lcc = (LanguageConnectionContext)
                                ContextService.getContextOrNull(LanguageConnectionContext.CONTEXT_ID);
                        ClassFactory cf = lcc.getLanguageConnectionFactory().getClassFactory();
                        ByteArrayInputStream in = new ByteArrayInputStream(bytes);
                        UDTInputStream inputStream = new UDTInputStream(in, cf);
                        Object o = inputStream.readObject();
                        rowTemplate[i].setValue(o);
                        inputStream.close();
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(Throwables.getRootCause(e));
        }

        instance.setRowArray(rowTemplate);

        KeyHashDecoder decoder = getEncoder(rowTemplate).getDecoder();
        int length = input.readInt(true);
        int position = input.position();

        try {
            if (position + length < input.limit()) {
                decoder.set(input.getBuffer(), position, length);
                decoder.decode(instance);
                input.setPosition(position + length);
            } else {
                byte[] toDecode = input.readBytes(length);
                decoder.set(toDecode, 0, length);
                decoder.decode(instance);
            }
        } catch (StandardException e) {
            SpliceLogUtils.logAndThrowRuntime(LOG, "Exception while deserializing row with template " + instance, e);
        }
        return instance;
    }

    private static DataValueDescriptor getDVD(int formatId) {
        if (formatId == StoredFormatIds.SQL_DECIMAL_ID) {
            return new SQLDecimal();
        } else {
            return DataValueFactoryImpl.getNullDVDWithUCS_BASICcollation(formatId);
        }
    }

    protected abstract T newType(int size);


    private DataHash getEncoder(DataValueDescriptor[] dvds) {
        int[] formatIds = EngineUtils.getFormatIds(dvds);
        int[] rowColumns = IntArrays.count(formatIds.length);
        DescriptorSerializer[] serializers;
        try {
            serializers = serializersCache.get(new IntArray(formatIds));
        } catch (ExecutionException e) {
            LOG.error("Error loading serializers from serializersCache", e);
            serializers = VersionedSerializers.latestVersion(false).getSerializers(dvds);
        }

        return BareKeyHash.encoder(rowColumns, null, serializers);
    }

    private static class IntArray {
        private final int[] array;

        IntArray(int[] array) {
            this.array = array;
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(array);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null) return false;
            IntArray intArray = (IntArray) o;
            if (!Arrays.equals(array, intArray.array)) return false;
            return true;
        }
    }

}