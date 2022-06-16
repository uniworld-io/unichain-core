package org.unichain.core.actuator.posbridge.ext;

import com.google.protobuf.ByteString;
import org.unichain.core.exception.ContractExeException;
import org.unichain.core.exception.ContractValidateException;

public interface ChildToken {
    void deposit(ByteString user, ByteString childToken, String depositData) throws ContractExeException, ContractValidateException;
    void withdraw(ByteString user, ByteString childToken, String withdrawData) throws ContractExeException, ContractValidateException;
}
