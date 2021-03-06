package com.bihe0832.android.test.module.request.basic;


import com.bihe0832.android.http.common.HttpRequest;
import com.bihe0832.android.http.common.HttpResponseHandler;
import com.bihe0832.android.test.module.request.Constants;

public class BasicGetRequest extends HttpRequest {

    private String mPara = "";

	private HttpResponseHandler mResponseHandlerHandler;

	public BasicGetRequest(String para, HttpResponseHandler handler) {
        this.mPara = para;
        this.mResponseHandlerHandler = handler;
    }

	@Override
	public String getUrl() {
        StringBuilder builder = new StringBuilder();
        builder.append(Constants.PARA_PARA + HTTP_REQ_ENTITY_MERGE + mPara);
        return getBaseUrl()+"?"+builder.toString();
	}

	private String getBaseUrl(){
        return Constants.HTTP_DOMAIN + Constants.PATH_GET;
    }

    @Override
    protected void onResponse(int statusCode, String result) {
        this.mResponseHandlerHandler.onResponse(statusCode,result);
    }
}
