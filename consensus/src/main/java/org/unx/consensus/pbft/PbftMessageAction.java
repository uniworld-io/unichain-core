package org.unx.consensus.pbft;

import com.google.protobuf.ByteString;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.unx.consensus.pbft.message.PbftMessage;
import org.unx.core.ChainBaseManager;
import org.unx.core.capsule.PbftSignCapsule;
import org.unx.protos.Protocol.PBFTMessage.Raw;

@Slf4j(topic = "pbft")
@Component
public class PbftMessageAction {

  @Autowired
  private ChainBaseManager chainBaseManager;

  public void action(PbftMessage message, List<ByteString> dataSignList) {
    switch (message.getDataType()) {
      case BLOCK: {
        long blockNum = message.getNumber();
        chainBaseManager.getCommonDataBase().saveLatestPbftBlockNum(blockNum);
        Raw raw = message.getPbftMessage().getRawData();
        chainBaseManager.getPbftSignDataStore()
            .putBlockSignData(blockNum, new PbftSignCapsule(raw.toByteString(), dataSignList));
        logger.info("commit msg block num is:{}", blockNum);
      }
      break;
      case SRL: {
        try {
          Raw raw = message.getPbftMessage().getRawData();
          chainBaseManager.getPbftSignDataStore().putSrSignData(message.getEpoch(),
              new PbftSignCapsule(raw.toByteString(), dataSignList));
          logger.info("sr commit msg :{}, epoch:{}", message.getNumber(), message.getEpoch());
        } catch (Exception e) {
          logger.error("process the sr list error!", e);
        }
      }
      break;
      default:
        break;
    }
  }

}