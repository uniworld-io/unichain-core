package stest.unx.wallet.common.client;

public interface Parameter {

  interface CommonConstant {
    byte ADD_PRE_FIX_BYTE = (byte) 0x82;   //a0 + address  ,a0 is version
    String ADD_PRE_FIX_STRING = "82";
    int ADDRESS_SIZE = 21;
    int BASE58CHECK_ADDRESS_SIZE = 35;
    byte ADD_PRE_FIX_BYTE_MAINNET = (byte) 0x44;   //44 + address
    byte ADD_PRE_FIX_BYTE_TESTNET = (byte) 0x82;   //82 + address
  }
}