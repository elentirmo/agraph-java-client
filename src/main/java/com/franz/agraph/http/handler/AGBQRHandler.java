/******************************************************************************
 ** See the file LICENSE for the full license governing this code.
 ******************************************************************************/

package com.franz.agraph.http.handler;

import com.franz.agraph.http.exception.AGHttpException;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.eclipse.rdf4j.query.resultio.BooleanQueryResultFormat;
import org.eclipse.rdf4j.query.resultio.BooleanQueryResultParser;
import org.eclipse.rdf4j.query.resultio.QueryResultIO;
import org.eclipse.rdf4j.query.resultio.QueryResultParseException;

import java.io.IOException;
import java.io.InputStream;

// BQR == BooleanQueryResult
public class AGBQRHandler extends AGResponseHandler {

    private boolean result;

    public AGBQRHandler() {
        super(BooleanQueryResultFormat.TEXT.getDefaultMIMEType());
    }

    @Override
    public void handleResponse(HttpResponse httpResponse, HttpUriRequest httpUriRequest) throws IOException, AGHttpException {
        String mimeType = getResponseMIMEType(httpResponse);
        if (!mimeType.equals(getRequestMIMEType())) {
            throw new AGHttpException("unexpected response MIME type: " + mimeType);
        }
        InputStream response = getInputStream(httpResponse);
        try {
            BooleanQueryResultFormat format = BooleanQueryResultFormat.TEXT;
            BooleanQueryResultParser parser = QueryResultIO.createBooleanParser(format);
            result = parser.parse(response);
        } catch (QueryResultParseException e) {
            throw new AGHttpException(e);
        }
    }

    public boolean getResult() {
        return result;
    }

}
