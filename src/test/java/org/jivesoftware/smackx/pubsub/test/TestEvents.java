/*
 * Created on 2009-04-22
 *
 * Modified by mikeb@lshift.net:
 *  - poll() in some places rather than take, so that tests fail rather than
 *    fail to complete.
 *  - @Ignore some tests that will fail until OpenFire implements v1.12 of the spec.
 *  - Use ejabberd's /home/ structure so that tests can run against ejabberd too.
 * 
 */
package org.jivesoftware.smackx.pubsub.test;

import org.jivesoftware.smackx.pubsub.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import java.util.concurrent.TimeUnit;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.packet.PacketExtension;
import org.jivesoftware.smack.packet.XMPPError.Type;
import org.jivesoftware.smack.test.SmackTestCase;
import org.jivesoftware.smackx.pubsub.listener.ItemDeleteListener;
import org.jivesoftware.smackx.pubsub.listener.ItemEventListener;
import org.jivesoftware.smackx.pubsub.listener.NodeConfigListener;
import org.junit.Ignore;

public class TestEvents extends PubSubTestCase
{

	public TestEvents(String str)
	{
		super(str);
	}

	@Override
	protected int getMaxConnections()
	{
		return 2;
	}

	public void testCreateAndGetNode() throws Exception
	{
		String nodeId = getNodeId(0, "MyTestNode");
		PubSubManager creatorMgr = new PubSubManager(getConnection(0), getService());
		
		Node creatorNode = null;
		try
		{
			creatorNode = creatorMgr.getNode(nodeId);
		}
		catch (XMPPException e)
		{
			if (e.getXMPPError().getType() == Type.CANCEL && e.getXMPPError().getCondition().equals("item-not-found"))
				creatorNode = creatorMgr.createNode(nodeId);
			else
				throw e;
		}
		
		PubSubManager subMgr = new PubSubManager(getConnection(1), getService());
		Node subNode = subMgr.getNode(nodeId);
		
		assertNotNull(subNode);
	}

    @Ignore("Ignore until OpenFire implements XEP-0060 v1.12: http://www.igniterealtime.org/community/thread/38466.  ejabberd has the same problem.")
	public void testConfigureAndNotify() throws Exception
	{
		// Setup event source
		String nodeId = getNodeId(0, "TestNode" + System.currentTimeMillis());
		PubSubManager creatorMgr = new PubSubManager(getConnection(0), getService());
		
		Node creatorNode = getPubnode(creatorMgr, nodeId, false, true);

		BlockingQueue<NodeConfigCoordinator> queue = new ArrayBlockingQueue<NodeConfigCoordinator>(3);

		// Setup event receiver
		PubSubManager subMgr = new PubSubManager(getConnection(1), getService());
		Node subNode = subMgr.getNode(nodeId);

		NodeConfigListener sub1Handler = new NodeConfigCoordinator(queue, "sub1");
		subNode.subscribe(getConnection(1).getUser());
		subNode.addConfigurationListener(sub1Handler);
		
		ConfigureForm currentConfig = creatorNode.getNodeConfiguration(); 
		ConfigureForm form = new ConfigureForm(currentConfig.createAnswerForm());
		form.setPersistentItems(true);
		form.setDeliverPayloads(false);
		form.setNotifyConfig(true);
		creatorNode.sendConfigurationForm(form);

        NodeConfigCoordinator ncc = queue.poll(2000, TimeUnit.MILLISECONDS);
        assertNotNull("Did not receive a reply in time.", ncc);
   		ConfigurationEvent event = ncc.event;
   		assertEquals(nodeId, event.getNode());
   		assertNull(event.getConfiguration());
   		
		currentConfig = creatorNode.getNodeConfiguration(); 
		form = new ConfigureForm(currentConfig.createAnswerForm());
		form.setDeliverPayloads(true);
		creatorNode.sendConfigurationForm(form);

   		event = queue.take().event;
   		assertEquals(nodeId, event.getNode());
   		assertNotNull(event.getConfiguration());
   		assertTrue(event.getConfiguration().isPersistItems());
   		assertTrue(event.getConfiguration().isDeliverPayloads());
	}

    // http://xmpp.org/extensions/xep-0060.html#publisher-publish-error-badpayload
    // �7.1.3.6
    // This is a no-payload, persistent node; ejabberd complains that a payload
    // should have been sent.
	public void testSendAndReceiveNoPayload() throws Exception
	{
		// Setup event source
		String nodeId = getNodeId(0, "TestNode" + System.currentTimeMillis());
		PubSubManager creatorMgr = new PubSubManager(getConnection(0), getService());
		Node creatorNode = getPubnode(creatorMgr, nodeId, true, false);

		BlockingQueue<ItemEventCoordinator> queue = new ArrayBlockingQueue<ItemEventCoordinator>(3);
		ItemEventCoordinator creatorHandler = new ItemEventCoordinator(queue, "creator");
		creatorNode.addItemEventListener(creatorHandler);
		
		// Setup event receiver
		PubSubManager subMgr = new PubSubManager(getConnection(1), getService());
		Node subNode = subMgr.getNode(nodeId);

		ItemEventCoordinator sub1Handler = new ItemEventCoordinator(queue, "sub1");
		subNode.addItemEventListener(sub1Handler);
		Subscription sub1 = subNode.subscribe(getConnection(1).getUser());
		
        // Send event
        String itemId = String.valueOf(System.currentTimeMillis());
        creatorNode.send(new Item(itemId));
        
        for(int i=0; i<2; i++)
        {
    		ItemEventCoordinator coord = queue.take();
        	assertEquals(1, coord.events.getItems().size());
        	assertEquals(itemId, coord.events.getItems().iterator().next().getId());
        }
	}
	
	public void testPublishAndReceiveNoPayload() throws Exception
	{
		// Setup event source
		String nodeId = getNodeId(0, "TestNode" + System.currentTimeMillis());
		PubSubManager creatorMgr = new PubSubManager(getConnection(0), getService());
		Node creatorNode = getPubnode(creatorMgr, nodeId, true, false);

		BlockingQueue<ItemEventCoordinator> queue = new ArrayBlockingQueue<ItemEventCoordinator>(3);
		
		// Setup event receiver
		PubSubManager subMgr = new PubSubManager(getConnection(1), getService());
		Node subNode = subMgr.getNode(nodeId);

		ItemEventCoordinator sub1Handler = new ItemEventCoordinator(queue, "sub1");
		subNode.addItemEventListener(sub1Handler);
		Subscription sub1 = subNode.subscribe(getConnection(1).getUser());
		
        // Send event
        String itemId = String.valueOf(System.currentTimeMillis());
        creatorNode.publish(new Item(itemId));
        
   		ItemEventCoordinator coord = queue.poll(1000, TimeUnit.MILLISECONDS);
        assertNotNull("Did not receive reply in time (probably wrong reply).", coord);
       	assertEquals(1, coord.events.getItems().size());
       	assertEquals(itemId, coord.events.getItems().get(0).getId());
	}

	public void testSendAndReceiveSimplePayload() throws Exception
	{
		// Setup event source
		String nodeId = getNodeId(0, "TestNode" + System.currentTimeMillis());
		PubSubManager creatorMgr = new PubSubManager(getConnection(0), getService());
		Node creatorNode = getPubnode(creatorMgr, nodeId, true, true);

		BlockingQueue<ItemEventCoordinator> queue = new ArrayBlockingQueue<ItemEventCoordinator>(3);
		
		// Setup event receiver
		PubSubManager subMgr = new PubSubManager(getConnection(1), getService());
		Node subNode = subMgr.getNode(nodeId);

		ItemEventCoordinator sub1Handler = new ItemEventCoordinator(queue, "sub1");
		subNode.addItemEventListener(sub1Handler);
		Subscription sub1 = subNode.subscribe(getConnection(1).getUser());
		
        // Send event
        String itemId = String.valueOf(System.currentTimeMillis());
        String payloadString = "<book xmlns=\"pubsub:test:book\"><author>Sir Arthur Conan Doyle</author></book>";
        creatorNode.send(new Item(itemId, new SimplePayload("book", "pubsub:test:book", payloadString)));
        
   		ItemEventCoordinator coord = queue.take();
       	assertEquals(1, coord.events.getItems().size());
       	Item item = coord.events.getItems().get(0);
       	assertEquals(itemId, item.getId());
       	assertTrue(item.getPayload() instanceof SimplePayload);
       	assertEquals(payloadString, item.getPayload().toXML());
       	assertEquals("book", item.getPayload().getElementName());
	}

	/*
	 * For this test, the following extension needs to be added to the meta-inf/smack.providers file
	 * 
	 * 	 <extensionProvider>
	 *     	<elementName>car</elementName>
	 *      <namespace>pubsub:test:vehicle</namespace>
	 *      <className>org.jivesoftware.smackx.pubsub.CarExtensionProvider</className>
	 *   </extensionProvider>
	 */
	/* 
	public void testSendAndReceiveCarPayload() throws Exception
	{
		// Setup event source
		String nodeId = "TestNode" + System.currentTimeMillis();
		PubSubManager creatorMgr = new PubSubManager(getConnection(0), getService());
		Node creatorNode = getPubnode(creatorMgr, nodeId, true, true);

		BlockingQueue<ItemEventCoordinator> queue = new ArrayBlockingQueue<ItemEventCoordinator>(3);
		
		// Setup event receiver
		PubSubManager subMgr = new PubSubManager(getConnection(1), getService());
		Node subNode = subMgr.getNode(nodeId);

		ItemEventCoordinator sub1Handler = new ItemEventCoordinator(queue, "sub1");
		subNode.addItemEventListener(sub1Handler);
		Subscription sub1 = subNode.subscribe(getConnection(1).getUser());
		
        // Send event
        String itemId = String.valueOf(System.currentTimeMillis());
        String payloadString = "<car xmlns='pubsub:test:vehicle'><paint color='green'/><tires num='4'/></car>";
        creatorNode.send(new Item(itemId, new SimplePayload("car", "pubsub:test:vehicle", payloadString)));
        
   		ItemEventCoordinator coord = queue.take();
       	assertEquals(1, coord.events.getItems().size());
       	Item item = coord.events.getItems().get(0);
       	assertEquals(itemId, item.getId());
       	assertTrue(item.getPayload() instanceof CarExtension);

       	CarExtension car = (CarExtension)item.getPayload();
       	assertEquals("green", car.getColor());
       	assertEquals(4, car.getNumTires());
	}
	*/

	public void testSendAndReceiveMultipleSubs() throws Exception
	{
		// Setup event source
		String nodeId = getNodeId(0, "TestNode" + System.currentTimeMillis());
		PubSubManager creatorMgr = new PubSubManager(getConnection(0), getService());
		Node creatorNode = getPubnode(creatorMgr, nodeId, true, false);

		BlockingQueue<ItemEventCoordinator> queue = new ArrayBlockingQueue<ItemEventCoordinator>(3);
		ItemEventCoordinator creatorHandler = new ItemEventCoordinator(queue, "creator");
		creatorNode.addItemEventListener(creatorHandler);
		
		// Setup event receiver
		PubSubManager subMgr = new PubSubManager(getConnection(1), getService());
		Node subNode = subMgr.getNode(nodeId);

		ItemEventCoordinator sub1Handler = new ItemEventCoordinator(queue, "sub1");
		subNode.addItemEventListener(sub1Handler);
		Subscription sub1 = subNode.subscribe(getConnection(1).getUser());
		
		ItemEventCoordinator sub2Handler = new ItemEventCoordinator(queue, "sub2");
		subNode.addItemEventListener(sub2Handler);
		Subscription sub2 = subNode.subscribe(getConnection(1).getUser());

		// Send event
        String itemId = String.valueOf(System.currentTimeMillis());
        creatorNode.send(new Item(itemId));
        
        for(int i=0; i<3; i++)
        {
    		ItemEventCoordinator coord = queue.take();
        	assertEquals(1, coord.events.getItems().size());
        	assertEquals(itemId, coord.events.getItems().iterator().next().getId());
        	
        	if (coord.id.equals("sub1") || coord.id.equals("sub2"))
        	{
        		assertEquals(2, coord.events.getSubscriptions().size());
        	}
        }
	}

	public void testSendAndReceiveMultipleItems() throws Exception
	{
		// Setup event source
		String nodeId = getNodeId(0, "TestNode" + System.currentTimeMillis());
		PubSubManager creatorMgr = new PubSubManager(getConnection(0), getService());
		
		Node creatorNode = getPubnode(creatorMgr, nodeId, true, false);

		BlockingQueue<ItemEventCoordinator> queue = new ArrayBlockingQueue<ItemEventCoordinator>(3);
		ItemEventCoordinator creatorHandler = new ItemEventCoordinator(queue, "creator");
		creatorNode.addItemEventListener(creatorHandler);
		creatorNode.subscribe(getConnection(0).getUser());
		
		// Setup event receiver
		PubSubManager subMgr = new PubSubManager(getConnection(1), getService());
		Node subNode = subMgr.getNode(nodeId);

		ItemEventCoordinator sub1Handler = new ItemEventCoordinator(queue, "sub1");
		subNode.addItemEventListener(sub1Handler);
		Subscription sub1 = subNode.subscribe(getConnection(1).getUser());
		
		ItemEventCoordinator sub2Handler = new ItemEventCoordinator(queue, "sub2");
		subNode.addItemEventListener(sub2Handler);
		Subscription sub2 = subNode.subscribe(getConnection(1).getUser());
		
		assertEquals(Subscription.State.subscribed, sub1.getState());
		assertEquals(Subscription.State.subscribed, sub2.getState());

        // Send event
        String itemId = String.valueOf(System.currentTimeMillis());
        
        Collection<Item> items = new ArrayList<Item>(3);
        items.add(new Item("First-" + itemId));
        items.add(new Item("Second-" + itemId));
        items.add(new Item("Third-" + itemId));
        creatorNode.send(items);
        
        for(int i=0; i<3; i++)
        {
    		ItemEventCoordinator coord = queue.poll(2000, TimeUnit.MILLISECONDS);
            assertNotNull("Did not receive a reply in time.", coord);
        	if (coord == creatorHandler)
        		assertEquals(1, coord.events.getSubscriptions().size());
        	else
        		assertEquals(2, coord.events.getSubscriptions().size());
        	assertEquals(3, coord.events.getItems().size());
        }
	}

	public void testSendAndReceiveDelayed() throws Exception
	{
		// Setup event source
		String nodeId = getNodeId(0, "TestNode" + System.currentTimeMillis());
		PubSubManager creatorMgr = new PubSubManager(getConnection(0), getService());
		
		Node creatorNode = getPubnode(creatorMgr, nodeId, true, false);

		// Send event
        String itemId = String.valueOf("DelayId-" + System.currentTimeMillis());
        String payloadString = "<book xmlns='pubsub:test:book'><author>Sir Arthur Conan Doyle</author></book>";
        creatorNode.send(new Item(itemId, new SimplePayload("book", "pubsub:test:book", payloadString)));

        Thread.sleep(1000);

        BlockingQueue<ItemEventCoordinator> queue = new ArrayBlockingQueue<ItemEventCoordinator>(3);

		// Setup event receiver
		PubSubManager subMgr = new PubSubManager(getConnection(1), getService());
		Node subNode = subMgr.getNode(nodeId);

		ItemEventCoordinator sub1Handler = new ItemEventCoordinator(queue, "sub1");
		subNode.addItemEventListener(sub1Handler);
		Subscription sub1 = subNode.subscribe(getConnection(1).getUser());

		ItemEventCoordinator coord = queue.take();
   		assertTrue(coord.events.isDelayed());
   		assertNotNull(coord.events.getPublishedDate());
	}

	public void testDeleteItemAndNotify() throws Exception
	{
		// Setup event source
		String nodeId = getNodeId(0, "TestNode" + System.currentTimeMillis());
		PubSubManager creatorMgr = new PubSubManager(getConnection(0), getService());
		
		Node creatorNode = getPubnode(creatorMgr, nodeId, true, false);

		BlockingQueue<ItemDeleteCoordinator> queue = new ArrayBlockingQueue<ItemDeleteCoordinator>(3);
		
		// Setup event receiver
		PubSubManager subMgr = new PubSubManager(getConnection(1), getService());
		Node subNode = subMgr.getNode(nodeId);

		ItemDeleteCoordinator sub1Handler = new ItemDeleteCoordinator(queue, "sub1");
		subNode.addItemDeleteListener(sub1Handler);
		subNode.subscribe(getConnection(1).getUser());

		// Send event
        String itemId = String.valueOf(System.currentTimeMillis());
        
        Collection<Item> items = new ArrayList<Item>(3);
        String id1 = "First-" + itemId;
        String id2 = "Second-" + itemId;
        String id3 = "Third-" + itemId;
        items.add(new Item(id1));
        items.add(new Item(id2));
        items.add(new Item(id3));
        creatorNode.send(items);
        
        creatorNode.deleteItem(id1);
        
   		ItemDeleteCoordinator coord = queue.take();
   		assertEquals(1, coord.event.getItemIds().size());
   		assertEquals(id1, coord.event.getItemIds().get(0));

   		creatorNode.deleteItem(Arrays.asList(id2, id3));

   		coord = queue.take();
   		assertEquals(2, coord.event.getItemIds().size());
   		assertTrue(coord.event.getItemIds().contains(id2));
   		assertTrue(coord.event.getItemIds().contains(id3));
	}

	public void testPurgeAndNotify() throws Exception
	{
		// Setup event source
		String nodeId = getNodeId(0, "TestNode" + System.currentTimeMillis());
		PubSubManager creatorMgr = new PubSubManager(getConnection(0), getService());
		
		Node creatorNode = getPubnode(creatorMgr, nodeId, true, false);

		BlockingQueue<ItemDeleteCoordinator> queue = new ArrayBlockingQueue<ItemDeleteCoordinator>(3);
		
		// Setup event receiver
		PubSubManager subMgr = new PubSubManager(getConnection(1), getService());
		Node subNode = subMgr.getNode(nodeId);

		ItemDeleteCoordinator sub1Handler = new ItemDeleteCoordinator(queue, "sub1");
		subNode.addItemDeleteListener(sub1Handler);
		subNode.subscribe(getConnection(1).getUser());

		// Send event
        String itemId = String.valueOf(System.currentTimeMillis());
        
        Collection<Item> items = new ArrayList<Item>(3);
        String id1 = "First-" + itemId;
        String id2 = "Second-" + itemId;
        String id3 = "Third-" + itemId;
        items.add(new Item(id1));
        items.add(new Item(id2));
        items.add(new Item(id3));
        creatorNode.send(items);
        
        creatorNode.deleteAllItems();
        
   		ItemDeleteCoordinator coord = queue.poll(2000, TimeUnit.MILLISECONDS);
        assertNotNull("Did not receive a reply in time.", coord);
   		assertNull(nodeId, coord.event);
	}

	public void testListenerMultipleNodes() throws Exception
	{
		// Setup event source
		String nodeId1 = getNodeId(0, "Node-1-" + System.currentTimeMillis());
		PubSubManager creatorMgr = new PubSubManager(getConnection(0), getService());
		String nodeId2 = getNodeId(0, "Node-2-" + System.currentTimeMillis());
		
		Node creatorNode1 = getPubnode(creatorMgr, nodeId1, true, false);
		Node creatorNode2 = getPubnode(creatorMgr, nodeId2, true, false);

		BlockingQueue<ItemEventCoordinator> queue = new ArrayBlockingQueue<ItemEventCoordinator>(3);
		
		PubSubManager subMgr = new PubSubManager(getConnection(1), getService());
		Node subNode1 = subMgr.getNode(nodeId1);
		Node subNode2 = subMgr.getNode(nodeId2);
		
		subNode1.addItemEventListener(new ItemEventCoordinator(queue, "sub1"));
		subNode2.addItemEventListener(new ItemEventCoordinator(queue, "sub2"));
		
		subNode1.subscribe(getConnection(1).getUser());
		subNode2.subscribe(getConnection(1).getUser());
		
		creatorNode1.send(new Item<PacketExtension>("item1"));
		creatorNode2.send(new Item<PacketExtension>("item2"));
		boolean check1 = false;
		boolean check2 = false;
		
		for (int i=0; i<2; i++)
		{
			ItemEventCoordinator event = queue.poll(2000, TimeUnit.MILLISECONDS);
            assertNotNull("Did not receive a reply in time.", event);
			
			if (event.id.equals("sub1"))
			{
				assertEquals(event.events.getNodeId(), nodeId1);
				check1 = true;
			}
			else
			{
				assertEquals(event.events.getNodeId(), nodeId2);
				check2 = true;
			}
		}
		assertTrue(check1);
		assertTrue(check2);
	}
	
	class ItemEventCoordinator implements ItemEventListener
	{
		private BlockingQueue<ItemEventCoordinator> theQueue;
		private ItemPublishEvent events;
		private String id;
		
		ItemEventCoordinator(BlockingQueue<ItemEventCoordinator> queue, String id)
		{
			theQueue = queue;
			this.id = id;
		}

		public void handlePublishedItems(ItemPublishEvent items)
		{
			events = items;
			theQueue.add(this);
		}

		@Override
		public String toString()
		{
			return "ItemEventCoordinator: " + id;
		}
		
	}
	
	class NodeConfigCoordinator implements NodeConfigListener
	{
		private BlockingQueue<NodeConfigCoordinator> theQueue;
		private String id;
		private ConfigurationEvent event;
		
		NodeConfigCoordinator(BlockingQueue<NodeConfigCoordinator> queue, String id)
		{
			theQueue = queue;
			this.id = id;
		}

		public void handleNodeConfiguration(ConfigurationEvent config)
		{
			event = config;
			theQueue.add(this);
		}

		@Override
		public String toString()
		{
			return "NodeConfigCoordinator: " + id;
		}

	}

	class ItemDeleteCoordinator implements ItemDeleteListener
	{
		private BlockingQueue<ItemDeleteCoordinator> theQueue;
		private String id;
		private ItemDeleteEvent event;
		
		ItemDeleteCoordinator(BlockingQueue<ItemDeleteCoordinator> queue, String id)
		{
			theQueue = queue;
			this.id = id;
		}

		public void handleDeletedItems(ItemDeleteEvent delEvent)
		{
			event = delEvent;
			theQueue.add(this);
		}


		public void handlePurge()
		{
			event = null;
			theQueue.add(this);
		}

		@Override
		public String toString()
		{
			return "ItemDeleteCoordinator: " + id;
		}
	}

	static private Node getPubnode(PubSubManager manager, String id, boolean persistItems, boolean deliverPayload) 
		throws XMPPException
	{
		ConfigureForm form = new ConfigureForm(FormType.submit);
		form.setPersistentItems(persistItems);
		form.setDeliverPayloads(deliverPayload);
		form.setAccessModel(AccessModel.open);
		return manager.createNode(id, form);
	}

}
