/*
 * Created on 2009-04-09
 */
package org.jivesoftware.smackx.pubsub.test;
 
import org.jivesoftware.smackx.pubsub.*;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smackx.FormField;
import org.jivesoftware.smackx.pubsub.test.SingleUserTestCase;

public class SubscriberUseCases extends SingleUserTestCase
{
	public void testSubscribe() throws Exception
	{
		Node node = getPubnode(false, false);
		Subscription sub = node.subscribe(getJid());
		
		assertEquals(getJid(), sub.getJid());
		assertNotNull(sub.getId());
		assertEquals(node.getId(), sub.getNode());
		assertEquals(Subscription.State.subscribed, sub.getState());
	}

	public void testSubscribeBadJid() throws Exception
	{
		Node node = getPubnode(false, false);
		
		try
		{
			node.subscribe("this@over.here");
			fail();
		}
		catch (XMPPException e)
		{
		}
	}

	public void testSubscribeWithOptions() throws Exception
	{
		SubscribeForm form = new SubscribeForm(FormType.submit);
		form.setDeliverOn(true);
		Calendar expire = Calendar.getInstance();
		expire.set(2020, 1, 1);
		form.setExpiry(expire.getTime());
		Node node = getPubnode(false, false);
		node.subscribe(getJid(), form);
	}
	
	public void testSubscribeConfigRequired() throws Exception
	{
		ConfigureForm form = new ConfigureForm(FormType.submit);
		form.setAccessModel(AccessModel.open);
		
		// Openfire specific field - nothing in the spec yet
		FormField required = new FormField("pubsub#subscription_required");
		required.setType(FormField.TYPE_BOOLEAN);
		form.addField(required);
		form.setAnswer("pubsub#subscription_required", true);
		Node node = getManager().createNode("Pubnode" + System.currentTimeMillis(), form);

		Subscription sub = node.subscribe(getJid());
		
		assertEquals(getJid(), sub.getJid());
		assertNotNull(sub.getId());
		assertEquals(node.getId(), sub.getNode());
		assertEquals(true, sub.isConfigRequired());
	}
	
	public void testUnsubscribe() throws Exception
	{
		Node node = getPubnode(false, false);
		node.subscribe(getJid());
		Collection<Subscription> subs = node.getSubscriptions();
		
		node.unsubscribe(getJid());
		Collection<Subscription> afterSubs = node.getSubscriptions();
		assertTrue(afterSubs.size() == subs.size()-1);
	}
	
	public void testUunsubscribeWithMultipleNoSubId() throws Exception
	{
		Node node = getPubnode(false, false);
		node.subscribe(getBareJID(0));
		node.subscribe(getBareJID(0));
		node.subscribe(getBareJID(0));
		
		try
		{
			node.unsubscribe(getBareJID(0));
			fail("Unsubscribe with no subid should fail");
		}
		catch (XMPPException e)
		{
		}
	}
	
	public void testUnsubscribeWithMultipleWithSubId() throws Exception
	{
		Node node = getPubnode(false, false);
		node.subscribe(getJid());
		Subscription sub = node.subscribe(getJid());
		node.subscribe(getJid());
		node.unsubscribe(getJid(), sub.getId());
	}
	
	public void testGetOptions() throws Exception
	{
		Node node = getPubnode(false, false);
		Subscription sub = node.subscribe(getJid());
		SubscribeForm form = node.getSubscriptionOptions(getJid(), sub.getId());
		assertNotNull(form);
	}
	
	public void testSubscribeWithConfig() throws Exception
	{		
		Node node = getPubnode(false, false);

//		SubscribeForm form = new SubscribeForm()
		Subscription sub = node.subscribe(getBareJID(0));
		
		assertEquals(getBareJID(0), sub.getJid());
		assertNotNull(sub.getId());
		assertEquals(node.getId(), sub.getNode());
		assertEquals(true, sub.isConfigRequired());
	}
	
	public void testGetItems() throws XMPPException
	{
		Node node = getPubnode(true, false);
		
		node.send((Item)null);
		node.send((Item)null);
		node.send((Item)null);
		node.send((Item)null);
		node.send((Item)null);
		
		Collection<Item> items = node.getItems();
		assertTrue(items.size() == 5);
		
		long curTime = System.currentTimeMillis();
		node.send(new Item("1-" + curTime));
		node.send(new Item("2-" + curTime));
		node.send(new Item("3-" + curTime));
		node.send(new Item("4-" + curTime));
		node.send(new Item("5-" + curTime));
		
		items = node.getItems();
		assertTrue(items.size() == 10);

		Node payloadNode = getPubnode(true, true);

		Map<String , String> idPayload = new HashMap<String, String>();
		idPayload.put("6-" + curTime, "<a/>");
		idPayload.put("7-" + curTime, "<a href=\"/up/here\"/>");
		idPayload.put("8-" + curTime, "<entity>text<inner></inner></entity>");
		idPayload.put("9-" + curTime, "<entity><inner><text></text></inner></entity>");
		
		for (Map.Entry<String, String> payload : idPayload.entrySet())
		{
			payloadNode.send(new Item<SimplePayload>(payload.getKey(), new SimplePayload("a", "pubsub:test", payload.getValue())));
		}
		
		payloadNode.send(new Item<SimplePayload>("6-" + curTime, new SimplePayload("a", "pubsub:test", "<a xmlns='pubsub:test'/>")));
		payloadNode.send(new Item<SimplePayload>("7-" + curTime, new SimplePayload("a", "pubsub:test", "<a xmlns='pubsub:test' href=\"/up/here\"/>")));
		payloadNode.send(new Item<SimplePayload>("8-" + curTime, new SimplePayload("entity", "pubsub:test", "<entity xmlns='pubsub:test'>text<inner></inner></entity>")));
		payloadNode.send(new Item<SimplePayload>("9-" + curTime, new SimplePayload("entity", "pubsub:test", "<entity xmlns='pubsub:test'><inner><text></text></inner></entity>")));
		
		Collection<Item> payloadItems = payloadNode.getItems();
		assertTrue(payloadItems.size() == 4);
	}

	public void getSpecifiedItems() throws XMPPException
	{
		Node node = getPubnode(true, true);
		
		node.send(new Item<SimplePayload>("1", new SimplePayload("a", "pubsub:test", "<a xmlns='pubsub:test' href='/1'/>")));
		node.send(new Item<SimplePayload>("2", new SimplePayload("a", "pubsub:test", "<a xmlns='pubsub:test' href='/2'/>")));
		node.send(new Item<SimplePayload>("3", new SimplePayload("a", "pubsub:test", "<a xmlns='pubsub:test' href='/3'/>")));
		node.send(new Item<SimplePayload>("4", new SimplePayload("a", "pubsub:test", "<a xmlns='pubsub:test' href='/4'/>")));
		node.send(new Item<SimplePayload>("5", new SimplePayload("a", "pubsub:test", "<a xmlns='pubsub:test' href='/5'/>")));
		
		Collection<String> ids = new ArrayList<String>(3);
		ids.add("1");
		ids.add("3");
		ids.add("4");

		List<Item> items = node.getItems(ids);
		assertEquals(3, items.size());
		assertEquals(items.get(0).getId(), "1");
		assertEquals(items.get(1).getId(), "3");
		assertEquals(items.get(2).getId(), "5");
	}

	public void testGetLastNItems() throws XMPPException
	{
		Node node = getPubnode(true, false);
		
		node.send(new Item("1"));
		node.send(new Item("2"));
		node.send(new Item("3"));
		node.send(new Item("4"));
		node.send(new Item("5"));
		
		List<Item> items = node.getItems(2);
		assertEquals(2, items.size());
		assertEquals(items.get(0).getId(), "4");
		assertEquals(items.get(1).getId(), "5");
	}

	private String getJid()
	{
		return getConnection(0).getUser();
	}

}
