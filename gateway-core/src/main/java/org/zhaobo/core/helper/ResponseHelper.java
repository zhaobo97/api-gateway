package org.zhaobo.core.helper;

import org.zhaobo.core.context.GatewayContext;
import org.zhaobo.common.constants.BasicConst;
import org.zhaobo.common.enums.ResponseCode;
import org.zhaobo.core.response.GatewayResponse;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.codec.http.*;

import java.util.Objects;

/**
 * 响应的辅助类
 */
public class ResponseHelper {

	/**
	 * 获取请求在过滤器处理阶段的异常响应
	 * @param responseCode
	 * @return
	 */
	public static FullHttpResponse getProcessFailHttpResponse(ResponseCode responseCode) {
		GatewayResponse gatewayResponse = GatewayResponse.buildOnFailure(responseCode);
		DefaultFullHttpResponse httpResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, 
				responseCode.getHttpResponseStatus(),
				Unpooled.wrappedBuffer(gatewayResponse.getContent().getBytes()));
		
		httpResponse.headers().set(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON + ";charset=utf-8");
		httpResponse.headers().set(HttpHeaderNames.CONTENT_LENGTH, httpResponse.content().readableBytes());
		return httpResponse;
	}

	/**
	 * 获取请求在具体后端反馈的成功/异常响应
	 * @param gatewayResponse
	 * @return
	 */
	private static FullHttpResponse getHttpResponse(GatewayResponse gatewayResponse) {
		ByteBuf content;
		if(Objects.nonNull(gatewayResponse.getFutureResponse())) {
			content = Unpooled.wrappedBuffer(gatewayResponse.getFutureResponse()
					.getResponseBodyAsByteBuffer());
		}
		else if(gatewayResponse.getContent() != null) {
			content = Unpooled.wrappedBuffer(gatewayResponse.getContent().getBytes());
		}
		else {
			content = Unpooled.wrappedBuffer(BasicConst.BLANK_SEPARATOR_1.getBytes());
		}

		if(Objects.isNull(gatewayResponse.getFutureResponse())) {
			DefaultFullHttpResponse httpResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
					gatewayResponse.getHttpResponseStatus(),
					content);
			httpResponse.headers().add(gatewayResponse.getResponseHeaders());
			httpResponse.headers().add(gatewayResponse.getExtraResponseHeaders());
			httpResponse.headers().set(HttpHeaderNames.CONTENT_LENGTH, httpResponse.content().readableBytes());
			return httpResponse;
		} else {
			gatewayResponse.getFutureResponse().getHeaders().add(gatewayResponse.getExtraResponseHeaders());

			DefaultFullHttpResponse httpResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
					HttpResponseStatus.valueOf(gatewayResponse.getFutureResponse().getStatusCode()),
					content);
			httpResponse.headers().add(gatewayResponse.getFutureResponse().getHeaders());
			return httpResponse;
		}
	}


	/**
	 * 写回响应信息方法
	 */
	public static void writeResponse(GatewayContext context) {
		if(context.isWritten()) {
			//构建响应对象
			FullHttpResponse httpResponse = ResponseHelper.getHttpResponse(context.getResponse());

			if(!context.isKeepalive()) {
				context.getNettyCtx().writeAndFlush(httpResponse).addListener(ChannelFutureListener.CLOSE);
			}
			else {
				httpResponse.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
				context.getNettyCtx().writeAndFlush(httpResponse).addListener(ChannelFutureListener.CLOSE);
			}

			context.setCompleted();
		} else if(context.isCompleted()){
			context.invokeCompletedCallback();
		}
		
	}
	
}
