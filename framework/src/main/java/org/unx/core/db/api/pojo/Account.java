package org.unx.core.db.api.pojo;

import lombok.Data;

@Data(staticConstructor = "of")
public class Account {

  private String address;
  private String name;
  private long balance;
}
