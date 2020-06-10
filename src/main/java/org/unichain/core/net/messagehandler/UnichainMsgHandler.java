package org.unichain.core.net.messagehandler;

import org.unichain.core.exception.P2pException;
import org.unichain.core.net.message.UnichainMessage;
import org.unichain.core.net.peer.PeerConnection;

public interface UnichainMsgHandler {

  void processMessage(PeerConnection peer, UnichainMessage msg) throws P2pException;

}
