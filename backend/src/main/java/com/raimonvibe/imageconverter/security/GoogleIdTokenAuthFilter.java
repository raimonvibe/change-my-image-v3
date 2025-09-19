package com.raimonvibe.imageconverter.security;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.raimonvibe.imageconverter.user.UserService;
import java.io.IOException;
import java.util.Collections;
import java.util.Optional;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class GoogleIdTokenAuthFilter extends OncePerRequestFilter {

  private final UserService userService;

  @Value("${app.auth.googleClientId:}")
  private String googleClientId;

  public GoogleIdTokenAuthFilter(UserService userService) {
    this.userService = userService;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String header = request.getHeader("Authorization");
    if (header != null && header.startsWith("Bearer ")) {
      String token = header.substring(7);
      try {
        GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
            .setAudience(Collections.singletonList(googleClientId))
            .build();
        GoogleIdToken idToken = verifier.verify(token);
        if (idToken != null) {
          String email = Optional.ofNullable(idToken.getPayload().getEmail()).orElse(null);
          if (email != null) {
            var user = userService.ensureUserByEmail(email);
            var auth = new UsernamePasswordAuthenticationToken(user.getEmail(), null, Collections.singleton(new SimpleGrantedAuthority("USER")));
            SecurityContextHolder.getContext().setAuthentication(auth);
          }
        }
      } catch (Exception e) {
      }
    }
    chain.doFilter(request, response);
  }
}
