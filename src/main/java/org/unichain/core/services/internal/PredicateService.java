package org.unichain.core.services.internal;

import com.google.protobuf.ByteString;
import org.unichain.core.exception.ContractExeException;

public interface PredicateService {
    void lockTokens(ByteString depositor, ByteString rootToken, String depositData) throws ContractExeException;//hex param
    void unlockTokens(ByteString withdrawer, ByteString rootToken, String withdrawData)throws ContractExeException;//hex param
}
