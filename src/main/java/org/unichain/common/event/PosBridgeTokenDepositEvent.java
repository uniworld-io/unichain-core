package org.unichain.common.event;

import lombok.Builder;

import java.io.Serializable;

@Builder
public class PosBridgeTokenDepositEvent implements Serializable {
    public long root_chainid;
    public long child_chainid;
    public String depositor;
    public String receiver;
    public String root_token;
    public String depositData;
}
