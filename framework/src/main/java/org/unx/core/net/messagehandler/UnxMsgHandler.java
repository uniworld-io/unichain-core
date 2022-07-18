package org.unx.core.net.messagehandler;

import org.unx.core.exception.P2pException;
import org.unx.core.net.message.UnxMessage;
import org.unx.core.net.peer.PeerConnection;

public interface UnxMsgHandler {

  void processMessage(PeerConnection peer, UnxMessage msg) throws P2pException;

}
