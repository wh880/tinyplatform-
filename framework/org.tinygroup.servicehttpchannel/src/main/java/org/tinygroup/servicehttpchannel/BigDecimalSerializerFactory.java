package org.tinygroup.servicehttpchannel;

import java.math.BigDecimal;

import com.caucho.hessian.io.AbstractSerializerFactory;
import com.caucho.hessian.io.Deserializer;
import com.caucho.hessian.io.HessianProtocolException;
import com.caucho.hessian.io.Serializer;

public class BigDecimalSerializerFactory extends AbstractSerializerFactory {
	private BigDecimalSerializer bigDecimalSerializer = new BigDecimalSerializer();
	private BigDecimalDeserializer bigDecimalDeserializer = new BigDecimalDeserializer();

	@Override
	public Serializer getSerializer(Class cl) throws HessianProtocolException {
		if (BigDecimal.class.isAssignableFrom(cl)) {
			return bigDecimalSerializer;
		}
		return null;
	}

	@Override
	public Deserializer getDeserializer(Class cl)
			throws HessianProtocolException {
		if (BigDecimal.class.isAssignableFrom(cl)) {
			return bigDecimalDeserializer;
		}
		return null;
	}
}
