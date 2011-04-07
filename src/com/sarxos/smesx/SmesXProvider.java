package com.sarxos.smesx;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;

import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.message.BasicNameValuePair;

import com.sarxos.smesx.http.NaiveSSLClient;
import com.sarxos.smesx.http.NaiveSSLFactory;
import com.sarxos.smesx.v22.SmesXOperation;
import com.sarxos.smesx.v22.SmesXRequest;
import com.sarxos.smesx.v22.SmesXResponse;


/**
 * 
 * @see http://www.howardism.org/Technical/Java/SelfSignedCerts.html
 * @author Bartosz Firyn (SarXos)
 */
public class SmesXProvider {

	/**
	 * Default SmesX endpoint.
	 */
	public static final String DEFAULT_ENDPOINT = "smesx1.smeskom.pl";

	/**
	 * Default SmesX port.
	 */
	public static final int DEFAULT_PORT = 2200;

	/**
	 * Current SmesX user.
	 */
	private String user = null;

	/**
	 * Current SmesX password.
	 */
	private String password = null;

	/**
	 * Current SmesX endpoint.
	 */
	private String endpoint = DEFAULT_ENDPOINT;

	/**
	 * Current SmesX port.
	 */
	private int port = DEFAULT_PORT;

	/**
	 * JAXB marshaller used to marshall SmesX entities.
	 */
	private Marshaller marshaller = null;

	/**
	 * JAXB unmarshaller used to unmarshall SmesX entities.
	 */
	private Unmarshaller unmarshaller = null;

	/**
	 * Apache HTPP client used to send HTTP requests.
	 */
	private NaiveSSLClient client = null;

	/**
	 * Default user agent header value.
	 */
	private String userAgent = "SarXos GPW Notifier";

	/**
	 * Log file for XML requests/responses.
	 */
	protected File log = new File("log/smesx.log");

	public SmesXProvider(String user, String password) {
		this(user, password, DEFAULT_ENDPOINT, DEFAULT_PORT);
	}

	public SmesXProvider(String user, String password, String endpoint, int port) {

		this.user = user;
		this.password = password;

		try {
			String pckg = SmesXRequest.class.getPackage().getName();
			JAXBContext jc = JAXBContext.newInstance(pckg);

			marshaller = jc.createMarshaller();
			marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);

			unmarshaller = jc.createUnmarshaller();

		} catch (JAXBException e) {
			throw new RuntimeException("Cannot create JAXB context", e);
		}

		client = NaiveSSLClient.getInstance();

		// add naive HTTPS schema for port 2200
		SSLSocketFactory factory = NaiveSSLFactory.createNaiveSSLSocketFactory();
		ClientConnectionManager manager = client.getConnectionManager();
		SchemeRegistry registry = manager.getSchemeRegistry();

		// check if schema is already registered
		Scheme scheme = registry.getScheme(new HttpHost(endpoint, port, "https"));
		// schema for https port 443 also work fine with port 2200
		if (scheme == null) {
			registry.register(new Scheme("https", 2200, factory));
		}
	}

	public SmesXResponse execute(SmesXOperation operation) throws SmesXException {

		if (operation == null) {
			throw new IllegalArgumentException("SmesX operation cannot be null");
		}

		SmesXRequest request = new SmesXRequest();
		request.setOperation(operation);
		request.setUser(user);
		request.setPassword(password);

		return execute(request);
	}

	public SmesXResponse execute(SmesXRequest request) throws SmesXException {

		byte[] bytes = marshall(request);

		// log
		try {
			FileOutputStream fos = new FileOutputStream(log, true);
			fos.write(bytes);
			fos.write('\n');
			fos.close();
		} catch (Exception e) {
			e.printStackTrace();
		}

		String xml = new String(bytes);
		List<NameValuePair> nvps = new ArrayList<NameValuePair>();
		nvps.add(new BasicNameValuePair("xml", xml));

		UrlEncodedFormEntity entity = null;
		try {
			entity = new UrlEncodedFormEntity(nvps);
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}

		String url = "https://" + endpoint + ":" + port + "/smesx";
		HttpPost post = client.createPost(url);
		post.setHeader("User-Agent", userAgent);
		post.setHeader("Content-Type", "application/x-www-form-urlencoded");
		post.setEntity(entity);

		ByteArrayOutputStream baos = new ByteArrayOutputStream();

		synchronized (client) {

			HttpEntity rentity = null;
			try {
				HttpResponse response = client.execute(post);
				rentity = response.getEntity();
				rentity.writeTo(baos);

				if (baos.size() == 0) {
					throw new SmesXException("SmesX response is empty!", response.getAllHeaders());
				}

			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				if (rentity != null) {
					try {
						rentity.getContent().close();
					} catch (Exception e) {
						e.printStackTrace();
					}
				}

			}
		}

		bytes = baos.toByteArray();
		baos.reset();

		// log 2
		FileOutputStream fos = null;
		try {
			fos = new FileOutputStream(log, true);
			fos.write(bytes);
			fos.write('\n');
			fos.write('\n');
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (fos != null) {
				try {
					fos.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}

		ByteArrayInputStream bais = new ByteArrayInputStream(bytes);

		try {
			return (SmesXResponse) unmarshaller.unmarshal(bais);
		} catch (JAXBException e) {
			e.printStackTrace();
		}

		return null;
	}

	protected byte[] marshall(SmesXRequest request) {

		if (request == null) {
			throw new IllegalArgumentException("SmesX Request cannot be null");
		}

		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try {
			marshaller.marshal(request, baos);
		} catch (JAXBException e) {
			throw new RuntimeException("Cannot marshall object", e);
		}

		return baos.toByteArray();
	}
}
