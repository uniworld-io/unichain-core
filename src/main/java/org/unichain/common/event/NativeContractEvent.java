package org.unichain.common.event;

import lombok.Builder;

@Builder
public class NativeContractEvent {
    public String name;
    public Object rawData;
    public long index; //sorted index of event in one transaction
}
