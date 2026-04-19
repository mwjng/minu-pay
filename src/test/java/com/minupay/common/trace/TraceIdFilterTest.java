package com.minupay.common.trace;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TraceIdFilter — MDC 주입/정리 및 응답 헤더 보장")
class TraceIdFilterTest {

    private final TraceIdFilter filter = new TraceIdFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    @DisplayName("X-Trace-Id 헤더가 오면 그 값을 MDC 와 응답 헤더에 싣는다")
    void incomingHeader_isPropagated() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TraceIdFilter.HEADER, "trace-abc");
        MockHttpServletResponse response = new MockHttpServletResponse();

        CapturingChain chain = new CapturingChain();
        filter.doFilter(request, response, chain);

        assertThat(chain.capturedTrace).isEqualTo("trace-abc");
        assertThat(response.getHeader(TraceIdFilter.HEADER)).isEqualTo("trace-abc");
        assertThat(MDC.get(TraceIdFilter.MDC_KEY)).isNull();
    }

    @Test
    @DisplayName("헤더가 없으면 UUID 를 생성해 MDC 와 응답 헤더에 싣는다")
    void missingHeader_generatesUuid() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        CapturingChain chain = new CapturingChain();
        filter.doFilter(request, response, chain);

        assertThat(chain.capturedTrace).isNotBlank();
        assertThat(response.getHeader(TraceIdFilter.HEADER)).isEqualTo(chain.capturedTrace);
        assertThat(MDC.get(TraceIdFilter.MDC_KEY)).isNull();
    }

    private static class CapturingChain implements FilterChain {
        String capturedTrace;

        @Override
        public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
            capturedTrace = MDC.get(TraceIdFilter.MDC_KEY);
        }
    }
}
