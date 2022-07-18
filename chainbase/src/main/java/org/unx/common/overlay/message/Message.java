package org.unx.common.overlay.message;

import static org.unx.core.exception.P2pException.TypeEnum.PROTOBUF_ERROR;

import com.google.protobuf.CodedInputStream;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.lang.reflect.Field;
import java.util.Arrays;
import lombok.Setter;
import org.apache.commons.lang3.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.ReflectionUtils;
import org.unx.common.parameter.CommonParameter;
import org.unx.common.utils.Sha256Hash;
import org.unx.core.exception.P2pException;
import org.unx.core.net.message.MessageTypes;
import org.unx.core.store.DynamicPropertiesStore;

public abstract class Message {

  protected static final Logger logger = LoggerFactory.getLogger("Message");
  // https://developers.google.com/protocol-buffers/docs/proto3#unknowns
  // https://github.com/protocolbuffers/protobuf/issues/272
  private static final Field field = ReflectionUtils
      .findField(CodedInputStream.class, "shouldDiscardUnknownFields");
  @Setter
  private static DynamicPropertiesStore dynamicPropertiesStore;

  static {
    ReflectionUtils.makeAccessible(field);
  }

  protected byte[] data;
  protected byte type;

  public Message() {
  }

  public Message(byte[] packed) {
    this.data = packed;
  }

  public Message(byte type, byte[] packed) {
    this.type = type;
    this.data = packed;
  }

  public static void compareBytes(byte[] src, byte[] dest) throws P2pException {
    if (src.length != dest.length) {
      throw new P2pException(PROTOBUF_ERROR, PROTOBUF_ERROR.getDesc());
    }
  }

  public static CodedInputStream getCodedInputStream(byte[] data) {
    CodedInputStream codedInputStream = CodedInputStream.newInstance(data);
    if (isFilter()) {
      ReflectionUtils.setField(field, codedInputStream, true);
    }
    return codedInputStream;
  }

  public static boolean isFilter() {
    return dynamicPropertiesStore.getAllowProtoFilterNum() == 1;
  }

  public ByteBuf getSendData() {
    return Unpooled.wrappedBuffer(ArrayUtils.add(this.getData(), 0, type));
  }

  public Sha256Hash getMessageId() {
    return Sha256Hash.of(CommonParameter.getInstance().isECKeyCryptoEngine(),
        getData());
  }

  public byte[] getData() {
    return this.data;
  }

  public MessageTypes getType() {
    return MessageTypes.fromByte(this.type);
  }

  public abstract Class<?> getAnswerMessage();

  @Override
  public String toString() {
    return "type: " + getType() + "\n";
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(data);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof Message)) {
      return false;
    }
    Message message = (Message) o;
    return Arrays.equals(data, message.data);
  }

}