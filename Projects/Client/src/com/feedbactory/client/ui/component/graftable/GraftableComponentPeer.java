
package com.feedbactory.client.ui.component.graftable;


import java.util.Set;


public interface GraftableComponentPeer extends GraftableComponentPeerController
{
   public void attachComponent(final GraftableComponent graftableComponent);
   public void detachComponent(final GraftableComponent graftableComponent);

   public void registerEventListener(final GraftableComponent graftableComponent, final GraftableComponentEventType eventType);
   public void registerEventListeners(final GraftableComponent graftableComponent, final Set<GraftableComponentEventType> eventTypes);

   public void deregisterEventListener(final GraftableComponent graftableComponent, final GraftableComponentEventType eventType);
   public void deregisterEventListeners(final GraftableComponent graftableComponent, final Set<GraftableComponentEventType> eventTypes);
}