package com.diary.security;

import com.diary.model.entity.Session;
import com.diary.repository.SessionRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SessionFilterTest {

    @Mock
    private SessionRepository sessionRepository;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain chain;

    @InjectMocks
    private SessionFilter sessionFilter;

    @BeforeEach
    void setUp() {
        when(request.getRequestURI()).thenReturn("/api/v1/entries");
    }

    @Test
    void should_allow_public_paths() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/auth/login");

        sessionFilter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void should_allow_config_endpoint() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/config");

        sessionFilter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void should_return_401_when_no_session_cookie() throws Exception {
        when(request.getCookies()).thenReturn(null);

        StringWriter stringWriter = new StringWriter();
        PrintWriter writer = new PrintWriter(stringWriter);
        when(response.getWriter()).thenReturn(writer);

        sessionFilter.doFilter(request, response, chain);

        verify(response).setStatus(401);
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void should_return_401_when_session_expired() throws Exception {
        String sessionId = UUID.randomUUID().toString();
        Cookie cookie = new Cookie("session_id", sessionId);
        when(request.getCookies()).thenReturn(new Cookie[]{cookie});

        Session expiredSession = new Session(
                sessionId, "user-id", Instant.now().minusSeconds(1000), Instant.now().minusSeconds(100)
        );
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(expiredSession));

        StringWriter stringWriter = new StringWriter();
        PrintWriter writer = new PrintWriter(stringWriter);
        when(response.getWriter()).thenReturn(writer);

        sessionFilter.doFilter(request, response, chain);

        verify(response).setStatus(401);
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void should_allow_valid_session() throws Exception {
        String sessionId = UUID.randomUUID().toString();
        Cookie cookie = new Cookie("session_id", sessionId);
        when(request.getCookies()).thenReturn(new Cookie[]{cookie});

        Session validSession = new Session(
                sessionId, "user-id", Instant.now(), Instant.now().plusSeconds(3600)
        );
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(validSession));

        sessionFilter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(request).setAttribute(eq("userId"), anyString());
    }
}
