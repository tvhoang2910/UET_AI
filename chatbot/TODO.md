# TODO - Production Improvements

## [x] Step 1: Query Condensation (RAG multi-turn)
- [x] Add `buildQueryCondensePrompt(List<Message> history, String currentQuestion)` to:
  - `chatbot/src/main/java/vn/edu/uet/chatbot/prompt/ChatPromptBuilder.java`
- [x] Update session chat flow in:
  - `chatbot/src/main/java/vn/edu/uet/chatbot/service/ChatService.java`
  - Use condensation result as the retrieval query (call `retrieve(standaloneQuestion, ...)`)

## [x] Step 2: Privilege Escalation Fix
- [x] Lock default role in registration:
  - `chatbot/src/main/java/vn/edu/uet/chatbot/service/AuthService.java`
  - `register` must always set `ROLE_STUDENT` (ignore client-provided role)

## [x] Step 3: Cascade Session Deletion
- [x] Add repository delete method:
  - `chatbot/src/main/java/vn/edu/uet/chatbot/repository/ChatMessageRepository.java`
  - `deleteBySessionId(UUID sessionId)` (with @Modifying @Query)
- [x] Add API endpoint for deleting a session (and its messages):
  - `chatbot/src/main/java/vn/edu/uet/chatbot/controller/ChatSessionController.java`
  - `DELETE /api/chat/sessions/{sessionId}`
  - Verify ownership via `authentication.getName()`

## [ ] Step 4: Verify
- [ ] Run unit/integration tests
  - `cd chatbot && mvn -q test`
- [ ] Ensure compilation passes
