package miniclient;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.httpclient.DefaultHttpMethodRetryHandler;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethodBase;
import org.apache.commons.httpclient.NameValuePair;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.RequestEntity;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.apache.commons.httpclient.params.HttpMethodParams;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import franz.exceptions.SoftException;

public class Request {
	
//	private static void enc(StringBuffer buf, String key, Object value) {
//		if (buf.length() > 0)
//			buf.append("&");
//		try {
//			// NOT SURE ABOUT THE 'toString':
//			buf.append(URLEncoder.encode(key, "UTF-8")).append("=").append(URLEncoder.encode(value.toString(), "UTF-8"));
//		} catch (UnsupportedEncodingException ex) {throw new SoftException(ex);}
//	}
//	
//	private static void encval(StringBuffer buf, String key, Object value) {
//		if ((value == null) || "".equals(value)) return;
//		if (value == Boolean.TRUE) enc(buf, key, "true");
//		else if (value == Boolean.FALSE) enc(buf, key, "false");
//		else if (value instanceof List) {
//			for (Object v : (List)value) enc(buf, key, v);
//		}
//		else enc(buf, key, value);
//	}
//	
//	protected static String urlenc(JSONObject options) {
//		StringBuffer buf = new StringBuffer();
//		for (Iterator it = options.keys(); it.hasNext();) {
//			String key = (String)it.next();
//			try {
//				Object value = options.get(key);
//				encval(buf, key, value);
//			} catch (JSONException ex) {throw new RuntimeException("JSON Exception that shouldn't occur", ex);}
//		}
//		return buf.toString();
//	}

	public static class RequestError extends RuntimeException {
		
		private int status;
		private String message;
		
		public RequestError(int status, String message) {
			this.status = status;
			this.message = message;
		}
		
		public String getMessage() {
			return "Server returned " + this.status + ": " + this.message; 
		}
	}

	private static void raiseError (int status, String message) {
		throw new RequestError(status, message);
	}
	
	private static HttpMethodBase makeGetMethod(String url, JSONObject options) {
		GetMethod method = new GetMethod(url);
		if (options != null) {
			List<NameValuePair> pairs = new ArrayList<NameValuePair>();
			for (Iterator it = options.keys(); it.hasNext();) {
				String key = (String)it.next();
				try {
					Object value = options.get(key);
					if (value instanceof List) {
						for (Object v : (List)value) {
							NameValuePair nvp= new NameValuePair(key, (v == null) ? "null" : v.toString());
							pairs.add(nvp);
						}
					} else {
						NameValuePair nvp= new NameValuePair(key, (value == null) ? "null" : value.toString());
						pairs.add(nvp);
					}
				} catch (JSONException ex) {throw new RuntimeException("JSON Exception that shouldn't occur", ex);}
			}
			NameValuePair[] morePairs = new NameValuePair[pairs.size()];
			for (int i = 0; i < pairs.size(); i++) morePairs[i] = pairs.get(i);
			method.setQueryString(morePairs);
		}
		//method.addRequestHeader(USER_AGENT, "foo");
		// Provide custom retry handler if necessary
		method.getParams().setParameter(HttpMethodParams.RETRY_HANDLER,
										new DefaultHttpMethodRetryHandler(3, false));
		return method;
	}

	private static HttpMethodBase makePostMethod(String url, JSONObject options) {
		PostMethod method = new PostMethod(url);
		if (options == null) return method;
		for (Iterator it = options.keys(); it.hasNext();) {
			String key = (String)it.next();
			try {
				Object value = options.get(key);
				if ("body".equals(key)) {
					RequestEntity body = new StringRequestEntity((value == null) ? "null" : value.toString());
					method.setRequestEntity(body);
				} else if (value instanceof List) {
					for (Object v : (List)value)  method.addParameter(key, (v == null) ? "null" : v.toString());
				} else {
					method.addParameter(key, value.toString());
				}
			} catch (JSONException ex) {throw new RuntimeException("JSON Exception that shouldn't occur", ex);}
		}
		return method;
	}
	
	public static boolean TRACE_IT = false;

	/**
	 * Execute an HTTP request, and return the status and response in an array.
	 */
	private static Object[] makeRequest(String method, String url, JSONObject options, 
			String accept, String contentType, Object callback, Object errCallback) {
		//String body = urlenc(options);
		if ((accept == null) || "".equals(accept)) accept = "*/*";
		boolean doPost = ("POST".equals(method) || "PUT".equals(method));
		HttpMethodBase httpMethod = doPost ? makePostMethod(url, options) : makeGetMethod(url, options);
		httpMethod.addRequestHeader("accept", accept);
		httpMethod.addRequestHeader("connection", "Keep-Alive");
		if (doPost && (contentType != null))
			httpMethod.addRequestHeader("Content-Type", contentType);
		HttpClient client = new HttpClient();
		try {
			if (TRACE_IT) {
				System.out.println("SEND REQUEST " + httpMethod.getName() + " " + httpMethod.getURI());
			}
			int statusCode = client.executeMethod(httpMethod);
            InputStream responseStream = httpMethod.getResponseBodyAsStream();	
            InputStreamReader bufferedReader = new InputStreamReader(responseStream);
        	char c[] = new char[8192];
        	int byteCount = bufferedReader.read(c);
        	String jsonString = new String(c, 0, byteCount);
        	bufferedReader.close();
			return new Object[]{new Integer(statusCode), URLDecoder.decode(jsonString,"UTF-8")};
		} catch (Exception e) {
			if (e instanceof RuntimeException) throw (RuntimeException)e;
			else throw new SoftException("Error executing request " + url + " because: ", e);
		} finally {
			// NOT SURE ABOUT THIS:
			if (false) {
				System.out.println("Releasing http connection.");
				httpMethod.releaseConnection();
			}
		}
	}

		
/***********

	    if callback:
	        status = [None]
	        error = []
	        def headerfunc(string):
	            if status[0] is None:
	                status[0] = locale.atoi(string.split(" ")[1])
	            return len(string)
	        def writefunc(string):
	            if status[0] == 200: callback(string)
	            else: error.append(string.decode("utf-8"))
	        curl.setopt(pycurl.WRITEFUNCTION, writefunc)
	        curl.setopt(pycurl.HEADERFUNCTION, headerfunc)
	        curl.perform()
	        if status[0] != 200:
	            errCallback(curl.getinfo(pycurl.RESPONSE_CODE), "".join(error))
	    else:
	        buf = StringIO.StringIO()
	        curl.setopt(pycurl.WRITEFUNCTION, buf.write)
	        curl.perform()
	        response = buf.getvalue().decode("utf-8")
	        buf.close()
	        return (curl.getinfo(pycurl.RESPONSE_CODE), response)


*********************/
	
	/**
	 * Decode 'value', which has already been converted from a JSON string into
	 * a JSON object of some sort.
	 */
	private static Object helpDecodeJSONResponse(Object value) throws JSONException {
		if (value instanceof String) return value;
		else if (value instanceof JSONArray) {
			JSONArray list = (JSONArray)value;
			List values = new ArrayList();
			for (int i = 0; i < list.length(); i++) {
				values.add(helpDecodeJSONResponse(list.get(i)));
			}
			return values;
		} else if (value instanceof JSONObject) {
			JSONObject dict = (JSONObject)value;
			Map map = new HashMap();			
			for (Iterator it = dict.keys(); it.hasNext();) {
				String key = (String)it.next();
				map.put(key, helpDecodeJSONResponse(dict.get(key)));
			}
			return map;
		} else {
			throw new SoftException("Don't know how to decode '" + value + "'");
		}
	}
	
	/**
	 * Convert JSON string into JSON objects, and then into Maps, Lists, and 
	 * strings.  Not sure about ints and booleans.
	 */
	private static Object decodeJSONResponse(String json) {
		if (TRACE_IT) System.out.println("HERE IS THE RESPONSE: \n   " + json);
		if (json.length() == 0) return null;
		char c = json.charAt(0);
		try {
			if (c == '{') {
				JSONObject dict = new JSONObject(json);
				return helpDecodeJSONResponse(dict);
			} else if (c == '[') {
				JSONArray list = new JSONArray(json);
				return helpDecodeJSONResponse(list);
			} else {
				// NOT SURE IF THIS IS EVER AN INTEGER OR BOOLEAN:
				return json;
			}
		} catch (JSONException ex) {throw new SoftException(ex);}
	}
		
	public static Object jsonRequest(String method, String url, JSONObject options, 
			String contentType, Object callback) {
		if (callback == null) {
	        Object[] statusAndBody = makeRequest(method, url, options, "application/json", 
	        		contentType, callback, null);
	        int status = (int)(Integer)statusAndBody[0];
	        String body = (String)statusAndBody[1];
	        if (status == 200) {
	        	return decodeJSONResponse(body);
	        } else {
	        	raiseError(status, body);
	        }
		} else {
			// CALLBACKS NOT YET IMPLEMENTED:
//			RowReader rowreader = new RowReader(callback);
//	        makeRequest(method, url, body, "application/json", contentType, 
//	        		callback=rowreader.process, errCallback=raiseErr)
		}
		return null;
    }
		
	public static void nullRequest(String method, String url, JSONObject options, String contentType) {
		if (contentType == null) contentType = "application/x-www-form-urlencoded";
		Object[] statusAndBody = makeRequest(method, url, options, "application/json", contentType, null, null);
		int status = (int)(Integer)statusAndBody[0];
		String body = (String)statusAndBody[1];
		if (status != 200) {
			raiseError(status, body);
		}
	}

}
