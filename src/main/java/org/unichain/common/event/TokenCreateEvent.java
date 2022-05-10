package org.unichain.common.event;

import lombok.Builder;

import java.io.Serializable;

@Builder
public class TokenCreateEvent implements Serializable {
    public String owner_address;
    public String name;
    public long max_supply;
    public long total_supply;
    public String address;
}
