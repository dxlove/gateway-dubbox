package com.z.gateway.core.support;

import org.apache.commons.chain.Context;
import org.apache.commons.lang3.StringUtils;

import com.z.gateway.common.OpenApiHttpRequestBean;
import com.z.gateway.common.OpenApiRouteBean;
import com.z.gateway.common.entity.ApiServerInfo;
import com.z.gateway.common.util.CommonCodeConstants;
import com.z.gateway.core.AbstractOpenApiHandler;
import com.z.gateway.core.OpenApiHttpClientService;
import com.z.gateway.protocol.OpenApiContext;
import com.z.gateway.protocol.OpenApiHttpSessionBean;
import com.z.gateway.service.ApiServerInfoReq;
import com.z.gateway.service.ApiServerInfoService;
//import com.z.gateway.util.StringResponseUtil;
import com.z.gateway.util.UrlUtil;

public class OpenApiReqHandler extends AbstractOpenApiHandler {

    private final int maxReqDataLth = 500;

    private ApiServerInfoService apiInterfaceService;

    private OpenApiHttpClientService apiHttpClientService;

    private boolean useHystrix;

    public void setApiInterfaceService(ApiServerInfoService apiInterfaceService) {
        this.apiInterfaceService = apiInterfaceService;
    }

    public void setApiHttpClientService(OpenApiHttpClientService apiHttpClientService) {
        this.apiHttpClientService = apiHttpClientService;
    }
  
    public void setUseHystrix(boolean useHystrix) {
        this.useHystrix = useHystrix;
    }

    // step2
    @Override
    public boolean doExcuteBiz(Context context) {
        OpenApiContext openApiContext = (OpenApiContext) context;
        OpenApiHttpSessionBean httpSessionBean = (OpenApiHttpSessionBean) openApiContext.getOpenApiHttpSessionBean();
        OpenApiHttpRequestBean request = httpSessionBean.getRequest();
        long currentTime = System.currentTimeMillis();
        if (logger.isDebugEnabled()) {
            logger.info(String.format("begin run doExecuteBiz,currentTime=%d,httpSessonBean=%s", currentTime,
                    httpSessionBean));
        }
        String routeBeanKey = request.getRouteBeanKey();
        OpenApiRouteBean routeBean = (OpenApiRouteBean) openApiContext.get(routeBeanKey);

        // routeBean.setServiceRsp(doInvokeBackService(routeBean)); // 返回值
        if (useHystrix) {
            HystrixServiceCommand command = new HystrixServiceCommand(apiHttpClientService, apiInterfaceService,
                    routeBean);
            String result = command.execute();
            routeBean.setReturnContent(result);
        } else {
            routeBean.setReturnContent(doInvokeBackService(routeBean));
        }
        if (logger.isDebugEnabled()) {
            logger.info(
                    String.format("end run doExecuteBiz,currentTime=%d,elapase_time=%d milseconds,httpSessonBean=%s",
                            System.currentTimeMillis(), (System.currentTimeMillis() - currentTime), httpSessionBean));
        }
        return false;
    }

    
  
    /**
     * 根据routeBean信息，通过httpclient调用后端信息，然后将返回值构建成string
     * 
     * @param bean
     * @return
     */
    private String doInvokeBackService(OpenApiRouteBean bean) {
        logger.info("step5...");
        String serviceRspData = null; // 后端服务返回值
        String operationType = bean.getOperationType();
        String requestMethod = bean.getRequestMethod();

        if (operationType.equals(CommonCodeConstants.API_SYSERVICE_KEY)) {

        } else if (CommonCodeConstants.API_GETDATA_KEY.equals(operationType)) {

        } else if (CommonCodeConstants.API_SERVICE_KEY.equals(operationType)) {
            logger.info(String.format("{serviceId:%s ,version:%s }", bean.getApiId(), bean.getVersion()));
            ApiServerInfo apiInfo = apiInterfaceService
                    .queryApiInterfaceByApiId(new ApiServerInfoReq(bean.getApiId(), bean.getVersion()));

            if (apiInfo == null) {
//                return StringResponseUtil
//                        .encodeResp(String.format("this apiId=%s,version=%s has off line,please use another one",
//                                bean.getApiId(), bean.getVersion()).getBytes());
                return String.format("this apiId=%s,version=%s has off line,please use another one",
                        bean.getApiId(), bean.getVersion());
            }
            apiInfo.setTargetUrl(bean.getTargetUrl());
            apiInfo.setRequestMethod(bean.getRequestMethod());
            if (CommonCodeConstants.REQUEST_METHOD.GET.name().equalsIgnoreCase(requestMethod)) { // get请求
                String url = apiInfo.getUrl();
                url = UrlUtil.dealUrl(url, bean.getThdApiUrlParams());
                /*
                 * if (StringUtils.isNotBlank(bean.getQueryString())) { url +=
                 * "?" + bean.getQueryString(); // 串好url 地址 }
                 */
                logger.info(String.format("{service url:%s} ", url));
                // String contentType =
                // bean.getReqHeader().get(CONTENT_TYPE_KEY);
                if (url.startsWith(CommonCodeConstants.HTTPS)) {
                    if (bean.getServiceGetReqData() == null) {
                        return apiHttpClientService.doHttpsGet(url, bean.getReqHeader());
                    } else {
                        return apiHttpClientService.doHttpsGet(url, bean.getServiceGetReqData(), bean.getReqHeader());
                    }
                } else {
                    if (bean.getServiceGetReqData() == null) {
                        return apiHttpClientService.doGet(url, bean.getReqHeader());
                    } else {
                        return apiHttpClientService.doGet(url, bean.getServiceGetReqData(), bean.getReqHeader());
                    }
                }

            } else if (CommonCodeConstants.REQUEST_METHOD.POST.name().equalsIgnoreCase(requestMethod)) {// post请求
                String url = apiInfo.getUrl();
                logger.info(String.format("{service url:%s} ", url));
                String reqData = bean.getServiceReqData(); // 请求的json格式数据参数
                if (StringUtils.isNotBlank(reqData) && reqData.length() > this.maxReqDataLth) {
                    reqData = reqData.substring(0, this.maxReqDataLth - 1);
                }
                logger.info(String.format("{serviceId:%s ,reqData:%s }", bean.getApiId(), reqData));

                // String contentType =
                // bean.getReqHeader().get(CONTENT_TYPE_KEY);
                if (url.startsWith(CommonCodeConstants.HTTPS)) {
                	serviceRspData= apiHttpClientService.doHttpsPost(url, bean.getServiceReqData(), bean.getReqHeader());
                } else {
                	serviceRspData= apiHttpClientService.doPost(url, bean.getServiceReqData(), bean.getReqHeader());
                }
      
            }
        }
        logger.info("serviceRspData="+serviceRspData);
        return serviceRspData;
    }
}
