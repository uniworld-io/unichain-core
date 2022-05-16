package org.unichain.core.services.internal;

import com.google.protobuf.ByteString;
import org.unichain.core.exception.ContractExeException;
import org.unichain.core.exception.ContractValidateException;

public interface ChildTokenService {
    void deposit(ByteString user, ByteString childToken, String depositData) throws ContractExeException, ContractValidateException;
    void withdraw(ByteString user, ByteString childToken, String withdrawData) throws ContractExeException, ContractValidateException;
}
