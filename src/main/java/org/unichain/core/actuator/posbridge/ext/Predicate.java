package org.unichain.core.actuator.posbridge.ext;

import com.google.protobuf.ByteString;
import org.unichain.core.exception.ContractExeException;
import org.unichain.core.exception.ContractValidateException;

public interface Predicate {
    void lockTokens(ByteString depositor, ByteString rootToken, String depositData) throws ContractExeException, ContractValidateException;//hex param
    void unlockTokens(ByteString withdrawer, ByteString rootToken, String withdrawData) throws ContractExeException, ContractValidateException;//hex param
}
