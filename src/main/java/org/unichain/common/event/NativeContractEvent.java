package org.unichain.common.event;

import lombok.Builder;
import lombok.ToString;

@Builder
@ToString
public class NativeContractEvent {
    public String topic;
    public Object rawData;
    public long index; //sorted index of event in one transaction
}
