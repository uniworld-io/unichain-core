package org.unx.core.zen.note;

import static org.unx.core.utils.ZenChainParams.ZC_JUBJUB_POINT_SIZE;
import static org.unx.core.utils.ZenChainParams.ZC_JUBJUB_SCALAR_SIZE;
import static org.unx.core.utils.ZenChainParams.ZC_OUTPLAINTEXT_SIZE;

import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.unx.core.exception.ZksnarkException;

@AllArgsConstructor
public class OutgoingPlaintext {

  @Getter
  @Setter
  private byte[] pkD;
  @Getter
  @Setter
  private byte[] esk;

  private static OutgoingPlaintext decode(NoteEncryption.Encryption.OutPlaintext outPlaintext) {
    byte[] data = outPlaintext.getData();
    OutgoingPlaintext ret = new OutgoingPlaintext(new byte[ZC_JUBJUB_SCALAR_SIZE],
        new byte[ZC_JUBJUB_POINT_SIZE]);
    System.arraycopy(data, 0, ret.pkD, 0, ZC_JUBJUB_SCALAR_SIZE);
    System.arraycopy(data, ZC_JUBJUB_SCALAR_SIZE, ret.esk, 0, ZC_JUBJUB_POINT_SIZE);
    return ret;
  }

  public static Optional<OutgoingPlaintext> decrypt(NoteEncryption.Encryption.OutCiphertext ciphertext, byte[] ovk,
                                                    byte[] cv, byte[] cm, byte[] epk) throws ZksnarkException {
    Optional<NoteEncryption.Encryption.OutPlaintext> pt = NoteEncryption.Encryption
        .attemptOutDecryption(ciphertext, ovk, cv, cm, epk);
    if (!pt.isPresent()) {
      return Optional.empty();
    }
    OutgoingPlaintext ret = OutgoingPlaintext.decode(pt.get());
    return Optional.of(ret);
  }

  private NoteEncryption.Encryption.OutPlaintext encode() {
    NoteEncryption.Encryption.OutPlaintext ret = new NoteEncryption.Encryption.OutPlaintext();
    ret.setData(new byte[ZC_OUTPLAINTEXT_SIZE]);
    // ZC_OUTPLAINTEXT_SIZE = (ZC_JUBJUB_POINT_SIZE + ZC_JUBJUB_SCALAR_SIZE)
    System.arraycopy(pkD, 0, ret.getData(), 0, ZC_JUBJUB_SCALAR_SIZE);
    System.arraycopy(esk, 0, ret.getData(), ZC_JUBJUB_SCALAR_SIZE, ZC_JUBJUB_POINT_SIZE);
    return ret;
  }

  /**
   * encrypt plain_out with ock to c_out, use NoteEncryption.epk
   */
  public NoteEncryption.Encryption.OutCiphertext encrypt(byte[] ovk, byte[] cv, byte[] cm, NoteEncryption enc)
      throws ZksnarkException {
    NoteEncryption.Encryption.OutPlaintext pt = this.encode();
    return enc.encryptToOurselves(ovk, cv, cm, pt);
  }
}
