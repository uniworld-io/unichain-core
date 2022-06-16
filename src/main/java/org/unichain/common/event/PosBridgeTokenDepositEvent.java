package org.unichain.common.event;

import lombok.Builder;
import lombok.ToString;

import java.io.Serializable;

@Builder
@ToString
public class PosBridgeTokenDepositEvent implements Serializable {
    public long rootChainId;
    public long childChainId;
    public String depositor;
    public String receiver;
    public String rootToken;
    public String depositData;//hex data
}
