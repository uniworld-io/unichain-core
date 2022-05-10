package org.unichain.common.event;

import lombok.Builder;

import java.io.Serializable;

@Builder
public class NftCreateEvent implements Serializable {
    public String owner_address;
    public String name;
    public long total_supply;
    public String minter;
    public String address;
}
