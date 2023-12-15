package io.flowcore.api;

import io.flowcore.api.dto.ProviderCallContext;
import io.flowcore.api.dto.ProviderCallResult;

/**
 * Adapter interface for integrating with external providers.
 *
 * @param <RequestT>  the request type accepted by this adapter
 * @param <ResponseT> the response type returned by this adapter
 */
public interface ProviderAdapter<RequestT, ResponseT> {

    /**
     * @return the logical name of the provider (e.g. "stripe", "kafka")
     */
    String providerName();

    /**
     * @return the operation this adapter handles (e.g. "createCharge")
     */
    String operationName();

    /**
     * @return the concrete request type
     */
    Class<RequestT> requestType();

    /**
     * @return the concrete response type
     */
    Class<ResponseT> responseType();

    /**
     * Executes the provider call.
     *
     * @param ctx     the call context with correlation and deadline information
     * @param request the typed request payload
     * @return the result of the call (success or failure)
     */
    ProviderCallResult<ResponseT> execute(ProviderCallContext ctx, RequestT request);
}
