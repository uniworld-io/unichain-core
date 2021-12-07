package org.unichain.common.application;

import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.unichain.common.overlay.discover.DiscoverServer;
import org.unichain.common.overlay.discover.node.NodeManager;
import org.unichain.common.overlay.server.ChannelManager;
import org.unichain.core.db.Manager;

public class UnichainApplicationContext extends AnnotationConfigApplicationContext {

  public UnichainApplicationContext() {
  }

  public UnichainApplicationContext(DefaultListableBeanFactory beanFactory) {
    super(beanFactory);
  }

  public UnichainApplicationContext(Class<?>... annotatedClasses) {
    super(annotatedClasses);
  }

  public UnichainApplicationContext(String... basePackages) {
    super(basePackages);
  }

  @Override
  public void destroy() {

    Application appT = ApplicationFactory.create(this);
    appT.shutdownServices();
    appT.shutdown();

    DiscoverServer discoverServer = getBean(DiscoverServer.class);
    discoverServer.close();
    ChannelManager channelManager = getBean(ChannelManager.class);
    channelManager.close();
    NodeManager nodeManager = getBean(NodeManager.class);
    nodeManager.close();

    Manager dbManager = getBean(Manager.class);
    dbManager.stopRePushThread();
    dbManager.stopRePushTriggerThread();
    super.destroy();
  }
}
