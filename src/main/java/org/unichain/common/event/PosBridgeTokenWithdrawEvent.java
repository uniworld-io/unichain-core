package org.unichain.common.event;

import lombok.Builder;
import lombok.ToString;

import java.io.Serializable;

@Builder
@ToString
public class PosBridgeTokenWithdrawEvent implements Serializable {
    public long childChainId;
    public long rootChainId;
    public String childToken;
    public String burner;
    public String withdrawer;
    public String withdrawData;//hex data
}
