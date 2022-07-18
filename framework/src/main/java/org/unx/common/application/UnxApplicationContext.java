package org.unx.common.application;

import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.unx.common.overlay.discover.DiscoverServer;
import org.unx.common.overlay.discover.node.NodeManager;
import org.unx.common.overlay.server.ChannelManager;
import org.unx.core.db.Manager;

public class UnxApplicationContext extends AnnotationConfigApplicationContext {

  public UnxApplicationContext() {
  }

  public UnxApplicationContext(DefaultListableBeanFactory beanFactory) {
    super(beanFactory);
  }

  public UnxApplicationContext(Class<?>... annotatedClasses) {
    super(annotatedClasses);
  }

  public UnxApplicationContext(String... basePackages) {
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
    dbManager.stopFilterProcessThread();
    super.destroy();
  }
}
