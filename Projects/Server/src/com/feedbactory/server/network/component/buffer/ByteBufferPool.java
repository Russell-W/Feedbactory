/* Memos:
 * - I previously had the class mutable - core pool size, allocation size, and even the direct attribute was configurable by the client after construction.
 *   This would allow a load-monitoring client to adapt the pool as necessary. However at this point I'm doubtful that the functionality is required.
 *   How variable will the load be at different times? Also once the buffer pool attributes are mutable, it introduces the dilemma of what to do when the client
 *   tries to return a buffer to the pool which no longer matches the new profile. Return it anyway, since the pool - a FIFO - will eventually be purged of them?
 *   Discard and reallocate? Put the onus on the client to purge and top up the pool with the newer profile buffers?
 */

package com.feedbactory.server.network.component.buffer;


import java.nio.ByteBuffer;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;


final public class ByteBufferPool
{
   final private Queue<ByteBuffer> bufferPool;

   final private int capacity;
   final private int bufferAllocationSize;
   final private boolean isDirect;

   final private boolean isTrackingMetrics;
   final private AtomicInteger pooledTakeRequests;
   final private AtomicInteger allocatedTakeRequests;
   final private AtomicInteger acceptedReclamations;
   final private AtomicInteger rejectedReclamations;


   public ByteBufferPool(final int capacity, final int bufferAllocationSize, final boolean isDirect)
   {
      this(capacity, bufferAllocationSize, isDirect, true);
   }


   public ByteBufferPool(final int capacity, final int bufferAllocationSize, final boolean isDirect, final boolean trackMetrics)
   {
      validate(capacity, bufferAllocationSize);

      this.capacity = capacity;
      this.bufferAllocationSize = bufferAllocationSize;
      this.isDirect = isDirect;

      this.isTrackingMetrics = trackMetrics;

      bufferPool = new LinkedBlockingQueue<>(capacity);

      if (isTrackingMetrics)
      {
         pooledTakeRequests = new AtomicInteger();
         allocatedTakeRequests = new AtomicInteger();
         acceptedReclamations = new AtomicInteger();
         rejectedReclamations = new AtomicInteger();
      }
      else
      {
         pooledTakeRequests = null;
         allocatedTakeRequests = null;
         acceptedReclamations = null;
         rejectedReclamations = null;
      }

      initialise();
   }


   private void validate(final int capacity, final int bufferAllocationSize)
   {
      validateCorePoolSize(capacity);
      validateBufferAllocationSize(bufferAllocationSize);
   }


   private void validateCorePoolSize(final int capacity)
   {
      if (capacity < 0)
         throw new IllegalArgumentException("Byte buffer pool capacity cannot be less than zero.");
   }


   private void validateBufferAllocationSize(final int bufferAllocationSize)
   {
      if (bufferAllocationSize <= 0)
         throw new IllegalArgumentException("Byte buffer pool allocation size cannot be empty.");
   }


   private void initialise()
   {
      topUpToCorePoolSize();
   }


   private void topUpToCorePoolSize()
   {
      for (int bufferNumber = 0; bufferNumber < capacity; bufferNumber ++)
         bufferPool.offer(allocateBuffer());
   }


   /****************************************************************************
    * 
    ***************************************************************************/


   private ByteBuffer allocateBuffer()
   {
      return (isDirect ? ByteBuffer.allocateDirect(bufferAllocationSize) : ByteBuffer.allocate(bufferAllocationSize));
   }


   /****************************************************************************
    * 
    ***************************************************************************/


   private ByteBuffer handleTake()
   {
      final ByteBuffer buffer = bufferPool.poll();

      if (buffer != null)
      {
         if (isTrackingMetrics)
            pooledTakeRequests.incrementAndGet();

         return buffer;
      }
      else
      {
         if (isTrackingMetrics)
            allocatedTakeRequests.incrementAndGet();

         return allocateBuffer();
      }
   }


   private boolean handleReclaim(final ByteBuffer byteBuffer)
   {
      if ((byteBuffer != null) &&
          (byteBuffer.capacity() == bufferAllocationSize) &&
          (byteBuffer.isDirect() == isDirect))
      {
         byteBuffer.clear();

         // Offer will return false rather than throw an exception if the queue capacity has been reached.
         if (bufferPool.offer(byteBuffer))
         {
            if (isTrackingMetrics)
               acceptedReclamations.incrementAndGet();

            return true;
         }
      }

      if (isTrackingMetrics)
         rejectedReclamations.incrementAndGet();

      return false;
   }


   /****************************************************************************
    * 
    ***************************************************************************/


   final public int getCapacity()
   {
      return capacity;
   }


   final public int getAllocationSize()
   {
      return bufferAllocationSize;
   }


   final public boolean isDirect()
   {
      return isDirect;
   }


   final public int getBuffersAvailable()
   {
      return bufferPool.size();
   }


   final public boolean isTrackingMetrics()
   {
      return isTrackingMetrics;
   }


   final public int getPooledTakeRequests()
   {
      if (! isTrackingMetrics)
         throw new UnsupportedOperationException("Metrics are not being tracked by this byte buffer pool instance.");

      return pooledTakeRequests.get();
   }


   final public int getAllocatedTakeRequests()
   {
      if (! isTrackingMetrics)
         throw new UnsupportedOperationException("Metrics are not being tracked by this byte buffer pool instance.");

      return allocatedTakeRequests.get();
   }


   final public int getAcceptedReclamations()
   {
      if (! isTrackingMetrics)
         throw new UnsupportedOperationException("Metrics are not being tracked by this byte buffer pool instance.");

      return acceptedReclamations.get();
   }


   final public int getRejectedReclamations()
   {
      if (! isTrackingMetrics)
         throw new UnsupportedOperationException("Metrics are not being tracked by this byte buffer pool instance.");

      return rejectedReclamations.get();
   }


   final public ByteBuffer take()
   {
      return handleTake();
   }


   final public boolean reclaim(final ByteBuffer byteBuffer)
   {
      return handleReclaim(byteBuffer);
   }
}