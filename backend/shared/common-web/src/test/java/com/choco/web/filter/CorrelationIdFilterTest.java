package com.choco.web.filter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    void generates_id_when_header_absent_and_echoes_it() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/products");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        String generated = response.getHeader(CorrelationIdFilter.HEADER);
        assertThat(generated).isNotBlank();
        assertThat(request.getAttribute(CorrelationIdFilter.ATTRIBUTE)).isEqualTo(generated);
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void reuses_safe_inbound_id() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/products");
        request.addHeader(CorrelationIdFilter.HEADER, "abc-123_XYZ");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader(CorrelationIdFilter.HEADER)).isEqualTo("abc-123_XYZ");
    }

    @Test
    void rejects_inbound_id_containing_crlf_injection() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/products");
        request.addHeader(CorrelationIdFilter.HEADER, "abc\r\nX-Injected: 1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        String echoed = response.getHeader(CorrelationIdFilter.HEADER);
        assertThat(echoed).isNotNull().doesNotContain("\r").doesNotContain("\n");
        assertThat(echoed).isNotEqualTo("abc\r\nX-Injected: 1");
    }
}
