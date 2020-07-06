package com.smartdevicelink.test.rpc.notifications;

import com.smartdevicelink.proxy.callbacks.InternalProxyMessage;
import com.smartdevicelink.proxy.callbacks.OnError;
import com.smartdevicelink.test.TestValues;

import junit.framework.TestCase;

import org.junit.Test;

/**
 * This is a unit test class for the SmartDeviceLink library project class : 
 * {@link com.smartdevicelink.proxy.callbacks.OnError}
 */
public class OnErrorTests extends TestCase {
		
	/**
	 * This is a unit test for the following methods : 
	 * {@link com.smartdevicelink.proxy.callbacks.OnError#OnError()}
	 * {@link com.smartdevicelink.proxy.callbacks.OnError#OnError(String, Exception)}
	 */
	@Test
	public void testValues () {		
		// Valid Tests
		OnError testOnError = new OnError();
		assertEquals(TestValues.MATCH, InternalProxyMessage.OnProxyError, testOnError.getFunctionName());
		
		Exception testE = new Exception();
		testOnError = new OnError(TestValues.GENERAL_STRING, testE);
		assertEquals(TestValues.MATCH, InternalProxyMessage.OnProxyError, testOnError.getFunctionName());
		assertEquals(TestValues.MATCH, TestValues.GENERAL_STRING, testOnError.getInfo());
		assertEquals(TestValues.MATCH, testE, testOnError.getException());
		
		// Invalid/Null Tests
		testOnError = new OnError(null, null);
		assertEquals(TestValues.MATCH, InternalProxyMessage.OnProxyError, testOnError.getFunctionName());
		assertNull(TestValues.NULL, testOnError.getInfo());
		assertNull(TestValues.NULL, testOnError.getException());
	}
}