// package vn.edu.uet.chatbot.controller;

// import org.junit.jupiter.api.Test;
// import org.springframework.beans.factory.annotation.Autowired;
// import
// org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
// import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
// import org.springframework.context.annotation.Import;
// import org.springframework.http.MediaType;
// import
// org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
// import org.springframework.security.core.Authentication;
// import org.springframework.security.core.authority.SimpleGrantedAuthority;
// import org.springframework.test.context.bean.override.mockito.MockitoBean;
// import org.springframework.test.web.servlet.MockMvc;
// import vn.edu.uet.chatbot.dto.ChatResponse;
// import vn.edu.uet.chatbot.dto.ChatSource;
// import vn.edu.uet.chatbot.exception.ApiExceptionHandler;
// import vn.edu.uet.chatbot.security.CustomUserDetailsService;
// import vn.edu.uet.chatbot.security.JwtUtil;
// import vn.edu.uet.chatbot.service.ChatService;

// import java.util.List;

// import static org.mockito.ArgumentMatchers.anyString;
// import static org.mockito.Mockito.verify;
// import static org.mockito.Mockito.when;
// import static
// org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
// import static
// org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
// import static
// org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// @WebMvcTest(ChatController.class)
// @AutoConfigureMockMvc(addFilters = false)
// @Import(ApiExceptionHandler.class)
// class ChatControllerTest {

// private static final Authentication USER = auth("alice", "ROLE_USER");

// @Autowired
// private MockMvc mockMvc;

// @MockitoBean
// private ChatService chatService;

// @MockitoBean
// private JwtUtil jwtUtil;

// @MockitoBean
// private CustomUserDetailsService customUserDetailsService;

// @Test
// void should_return_chat_response_for_valid_request() throws Exception {
// when(chatService.chat(anyString(), anyString())).thenReturn(new
// ChatResponse("ok", List.of()));

// mockMvc.perform(post("/api/chat")
// .contentType(MediaType.APPLICATION_JSON)
// .content("{\"message\":\"Xin chao\"}")
// .principal(USER))
// .andExpect(status().isOk())
// .andExpect(jsonPath("$.answer").value("ok"));

// verify(chatService).chat("Xin chao", "alice");
// }

// @Test
// void should_accept_prompt_alias_for_message() throws Exception {
// when(chatService.chat(anyString(), anyString())).thenReturn(new
// ChatResponse("ok", List.of()));

// mockMvc.perform(post("/api/chat")
// .contentType(MediaType.APPLICATION_JSON)
// .content("{\"prompt\":\"Xin chao\"}")
// .principal(USER))
// .andExpect(status().isOk())
// .andExpect(jsonPath("$.answer").value("ok"));

// verify(chatService).chat("Xin chao", "alice");
// }

// @Test
// void should_return_400_for_blank_message() throws Exception {
// mockMvc.perform(post("/api/chat")
// .contentType(MediaType.APPLICATION_JSON)
// .content("{\"message\":\" \"}")
// .principal(USER))
// .andExpect(status().isBadRequest())
// .andExpect(jsonPath("$.title").value("Validation failed"));
// }

// @Test
// void should_return_retrieved_sources_for_inspection_endpoint() throws
// Exception {
// when(chatService.inspect(anyString(), anyString())).thenReturn(List.of(
// new ChatSource("Tiếng Việt Dễ Dàng", 0, 1, 0.91, "Mỗi bài học được chia
// thành...")));

// mockMvc.perform(post("/api/chat/retrieve")
// .contentType(MediaType.APPLICATION_JSON)
// .content("{\"message\":\"Mỗi bài học được chia thành các phần như thế
// nào?\"}")
// .principal(USER))
// .andExpect(status().isOk())
// .andExpect(jsonPath("$.sources[0].title").value("Tiếng Việt Dễ Dàng"))
// .andExpect(jsonPath("$.sources[0].chunkIndex").value(0))
// .andExpect(jsonPath("$.sources[0].pageNumber").value(1));

// verify(chatService).inspect("Mỗi bài học được chia thành các phần như thế
// nào?", "alice");
// }

// @Test
// void should_return_503_when_chat_backend_is_unavailable() throws Exception {
// when(chatService.chat(anyString(), anyString()))
// .thenThrow(new org.springframework.web.client.RestClientException("Ollama
// down"));

// mockMvc.perform(post("/api/chat")
// .contentType(MediaType.APPLICATION_JSON)
// .content("{\"message\":\"Xin chao\"}")
// .principal(USER))
// .andExpect(status().isServiceUnavailable())
// .andExpect(jsonPath("$.title").value("Service unavailable"))
// .andExpect(jsonPath("$.detail").value("Chat backend is unavailable. Cause:
// Ollama down"));
// }

// private static Authentication auth(String username, String... roles) {
// List<SimpleGrantedAuthority> authorities = new java.util.ArrayList<>();
// for (String role : roles) {
// authorities.add(new SimpleGrantedAuthority(role));
// }
// return new UsernamePasswordAuthenticationToken(username, "n/a", authorities);
// }
// }
