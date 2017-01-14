/* Memos:
 *
 * - I previously added a couple of methods to the interface to allow components to invokeLater() and invokeAndWait() arbitrary Runnables on the peer.
 *      However I ended up removing them because they were rarely of any genuine benefit, and secondly they were open to abuse in introducing a subtle
 *      inter-thread variable visibility problem: consider nested graftable Swing components forwarding such Runnable requests up to an SWT peer. On the
 *      originating Swing component where the invokeLater() is called via the Swing EDT this is fine, since the graftable Swing component's variable reference
 *      to the peer (see GraftableSwingComponent.graftableComponentPeer) is always visible to the Swing EDT. Once the call propagates to a higher Swing parent,
 *      however, the call is then on the SWT thread. And that Swing component most likely needs to forward that call onto its own higher peer, using its peer
 *      variable reference. However this is a bit dicey since the peer variable is intended to be used on the Swing EDT. We could solve this
 *      problem by marking that variable as volatile, but given that the use cases for an arbitrary invokeLater() or invokeAndWait() on the SWT peer are all but
 *      non-existent, it seems cleaner to purge the API of those (so far) unnecessary methods. If we do reinstate them, we need to be aware of the inter-thread
 *      visibility danger.
 *
 * - An example of the above nested Swing component call chain: GraftableTextField, when nested within a combo box; it needs to be able to set the cursor on the top peer.
 */


package com.feedbactory.client.ui.component.graftable;


public interface GraftableComponentPeerController extends GraftableComponentPaintReceiver
{
   public void setPeerVisible(final boolean isVisible);
   public void requestPeerFocus();
   public void setPeerSize(final int width, final int height);
   public void setPeerCursor(final GraftableComponentCursorType cursorType);
   public void setPeerToolTipText(final String toolTipText);
}