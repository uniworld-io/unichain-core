package org.unichain.common.event;

import lombok.Builder;

import java.io.Serializable;

@Builder
public class PosBridgeTokenWithdrawEvent implements Serializable {
    public long child_chainid;
    public long root_chainid;
    public String child_token;
    public String burner;
    public String withdrawer;
    public String withdrawData;
}
