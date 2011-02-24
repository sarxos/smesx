package com.sarxos.smesx.examples;

import com.sarxos.smesx.SmesXException;
import com.sarxos.smesx.SmesXProvider;
import com.sarxos.smesx.v22.SmesXExecutionStatus;
import com.sarxos.smesx.v22.SmesXResponse;
import com.sarxos.smesx.v22.SmesXSMSSend;


public class SendSMS {

	public static void main(String[] args) throws SmesXException {

		String user = ""; // smesx user - need to be specified
		String password = ""; // smesx password - need to be specified

		String recipient = "mobile.number"; // mobile +48XXXXXXXXX
		String body = "Test";

		SmesXSMSSend send = new SmesXSMSSend();
		send.setMSISDN(recipient);
		send.setBody(body);

		SmesXProvider provider = new SmesXProvider(user, password);
		SmesXResponse response = provider.execute(send);

		System.out.println(response.getExecutionStatus() == SmesXExecutionStatus.SUCCESS);
	}
}
