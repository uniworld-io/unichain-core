package org.unichain.core.services.internal;

import com.google.protobuf.ByteString;
import org.unichain.core.exception.ContractExeException;

public interface ChildTokenService {
    void deposit(ByteString user, ByteString childToken, String depositData) throws ContractExeException;
    void withdraw(ByteString user, ByteString childToken, String withdrawData) throws ContractExeException;
}
