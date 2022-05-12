package org.unichain.common.event;

import lombok.Builder;

import java.io.Serializable;

@Builder
public class PosBridgeTokenWithdrawExecEvent implements Serializable {
    public String owner_address;
    public long root_chainid;
    public String root_token;
    public long child_chainid;
    public String child_token;
    public int type;
    public String receive_address;
    public long data;
}
