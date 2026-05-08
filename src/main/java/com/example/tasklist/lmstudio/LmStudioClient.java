package com.example.tasklist.lmstudio;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

@Service
public class LmStudioClient {

    /** Must match JSON bytes; RestClient + String body may use Latin-1 on some JVMs and corrupt Cyrillic. */
    private static final MediaType JSON_UTF8 = new MediaType(MediaType.APPLICATION_JSON, StandardCharsets.UTF_8);

    private static final String SYSTEM_PROMPT = """
            Ты помощник по планированию. Твой ответ пользователю — только готовый результат на русском языке.

            Формат ответа: короткий чеклист или нумерованные шаги (обычно 3–8 пунктов), без вступлений и без морали.
            Для наглядности можно использовать простые эмодзи (например 🛒 ✅ 📍) и символ →; не используй LaTeX вроде $\\rightarrow$ или $...$.

            Строго запрещено в ответе:
            — писать на английском или смешивать языки;
            — показывать ход рассуждений, планы анализа, «Thinking Process», «Internal Monologue», черновики;
            — описывать, как ты думаешь или какую стратегию выбираешь;
            — любые мета-фразы вроде «Analyze the Request», «Draft», «Refine», «Option A/B».

            Сначала мысленно сформулируй ответ, затем выведи только итог — так, будто пользователь сразу видит готовую памятку.""";

    private static final String CONTEXT_SYSTEM_APPEND = """

            Контекст доски и родительских задач — только фон (сценарий, зачем это делается). Пользователь ждёт совет именно по одной «текущей задаче» из запроса.

            Обязательно:
            — Пиши только про текущую задачу: критерии выбора, на что смотреть, практические шаги, типичные ошибки — в рамках этой темы.
            — Не перечисляй соседние пункты из длинного родительского заголовка и не давай по ним отдельных советов (например не разбирай каждый товар из списка покупок, если текущая задача — один товар).
            — Не воспроизводи весь родительский список в ответе и не предлагай «добавить в список» для пунктов, не совпадающих с текущей задачей.

            Если родитель выглядит как составной чеклист, это не значит, что нужно пройтись по всем пунктам — только по текущей.""";

    private final RestClient restClient;
    private final LmStudioProperties props;
    private final ObjectMapper objectMapper;
    private final Timer adviceLatency;
    private final Counter adviceFailures;

    public LmStudioClient(
            @Qualifier(LmStudioConfig.LM_STUDIO_REST_CLIENT) RestClient restClient,
            LmStudioProperties props,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry) {
        this.restClient = restClient;
        this.props = props;
        this.objectMapper = objectMapper;
        this.adviceLatency = Timer.builder("taskloop.advice.lmstudio")
                .description("Latency of LM Studio chat completions for task advice")
                .register(meterRegistry);
        this.adviceFailures = Counter.builder("taskloop.advice.lmstudio.failures")
                .description("Failed LM Studio advice calls")
                .register(meterRegistry);
    }

    public String suggestHowToComplete(String taskTitle) {
        if (props.getModel() == null || props.getModel().isBlank()) {
            throw new LmStudioException(
                    "Задайте lmstudio.model в application.yml — имя модели, как в LM Studio.",
                    HttpStatus.BAD_REQUEST,
                    null);
        }
        int maxTitle = Math.max(1, props.getMaxTaskTitleChars());
        if (taskTitle == null || taskTitle.isBlank()) {
            throw new LmStudioException("Пустой заголовок задачи.", HttpStatus.BAD_REQUEST, null);
        }
        if (taskTitle.length() > maxTitle) {
            throw new LmStudioException(
                    "Заголовок задачи длиннее допустимого для запроса к модели (" + maxTitle + " символов).",
                    HttpStatus.BAD_REQUEST,
                    null);
        }

        return executeAdviceRequest(SYSTEM_PROMPT.trim(), buildUserMessage(taskTitle));
    }

    /**
     * Advice using board title + ancestor task titles (root → parent) + current task title.
     */
    public String suggestHowToComplete(AdvicePromptContext ctx) {
        if (props.getModel() == null || props.getModel().isBlank()) {
            throw new LmStudioException(
                    "Задайте lmstudio.model в application.yml — имя модели, как в LM Studio.",
                    HttpStatus.BAD_REQUEST,
                    null);
        }
        int maxTitle = Math.max(1, props.getMaxTaskTitleChars());
        if (ctx.taskTitle() == null || ctx.taskTitle().isBlank()) {
            throw new LmStudioException("Пустой заголовок задачи.", HttpStatus.BAD_REQUEST, null);
        }
        if (ctx.taskTitle().length() > maxTitle) {
            throw new LmStudioException(
                    "Заголовок задачи длиннее допустимого для запроса к модели (" + maxTitle + " символов).",
                    HttpStatus.BAD_REQUEST,
                    null);
        }
        if (ctx.boardTitle() == null || ctx.boardTitle().isBlank()) {
            throw new LmStudioException("Пустое название доски.", HttpStatus.BAD_REQUEST, null);
        }
        String boardTitle = ctx.boardTitle().trim();
        if (boardTitle.length() > maxTitle) {
            boardTitle = boardTitle.substring(0, maxTitle);
        }
        List<String> ancestors =
                ctx.ancestorTitlesRootFirst() == null ? new ArrayList<>() : new ArrayList<>(ctx.ancestorTitlesRootFirst());
        int maxUser = Math.max(256, props.getMaxAdviceContextChars());
        String userMsg = buildContextUserMessage(boardTitle, ancestors, ctx.taskTitle().trim(), maxUser);
        return executeAdviceRequest(SYSTEM_PROMPT.trim() + CONTEXT_SYSTEM_APPEND, userMsg);
    }

    private String executeAdviceRequest(String systemPrompt, String userContent) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", props.getModel());
        root.put("temperature", 0.2);
        root.put("max_tokens", Math.max(1, props.getMaxTokens()));

        ArrayNode messages = root.putArray("messages");
        ObjectNode sys = messages.addObject();
        sys.put("role", "system");
        sys.put("content", systemPrompt);
        ObjectNode user = messages.addObject();
        user.put("role", "user");
        user.put("content", userContent);

        String bodyJson;
        try {
            bodyJson = objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            adviceFailures.increment();
            throw new LmStudioException("Не удалось сформировать запрос к модели", HttpStatus.BAD_GATEWAY, e);
        }

        long start = System.nanoTime();
        try {
            byte[] bodyUtf8 = bodyJson.getBytes(StandardCharsets.UTF_8);
            String responseJson = restClient.post()
                    .uri("/chat/completions")
                    .contentType(JSON_UTF8)
                    .headers(headers -> {
                        if (props.getApiKey() != null && !props.getApiKey().isBlank()) {
                            headers.setBearerAuth(props.getApiKey());
                        }
                    })
                    .body(new ByteArrayResource(bodyUtf8))
                    .retrieve()
                    .body(String.class);
            try {
                String parsed = parseAssistantContent(responseJson);
                String cleaned = sanitizeThinkingModelOutput(parsed);
                String out = cleaned.isBlank() ? parsed : cleaned;
                out = normalizeAdviceFormatting(out);
                adviceLatency.record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
                return out;
            } catch (LmStudioException e) {
                adviceFailures.increment();
                throw e;
            }
        } catch (RestClientException e) {
            adviceFailures.increment();
            throw mapRestException(e);
        }
    }

    private static String buildContextUserMessage(
            String boardTitle, List<String> ancestorsRootFirst, String taskTitle, int maxChars) {
        List<String> ancestors = ancestorsRootFirst;
        for (int guard = 0; guard < 500; guard++) {
            String body = formatAdviceContextBody(boardTitle, ancestors, taskTitle);
            if (body.length() <= maxChars) {
                return body;
            }
            if (!ancestors.isEmpty()) {
                ancestors.remove(0);
                continue;
            }
            String truncatedTask = taskTitle.length() > 80 ? taskTitle.substring(0, 77) + "…" : taskTitle;
            body = formatAdviceContextBody(boardTitle, ancestors, truncatedTask);
            if (body.length() <= maxChars) {
                return body;
            }
            String boardShort = boardTitle.length() > 40 ? boardTitle.substring(0, 37) + "…" : boardTitle;
            body = formatAdviceContextBody(boardShort, ancestors, truncatedTask);
            if (body.length() <= maxChars) {
                return body;
            }
            return body.substring(0, maxChars);
        }
        return formatAdviceContextBody(boardTitle, ancestors, taskTitle);
    }

    private static String formatAdviceContextBody(String boardTitle, List<String> ancestorsRootFirst, String taskTitle) {
        StringBuilder sb = new StringBuilder();
        sb.append("Доска (фон сценария): \"").append(boardTitle).append("\".\n");
        if (!ancestorsRootFirst.isEmpty()) {
            sb.append("Родительские задачи (фон; не разбирай их пункты по отдельности, если это один составной заголовок): ");
            sb.append(String.join(" → ", ancestorsRootFirst));
            sb.append(".\n");
        }
        sb.append("Текущая задача — единственная тема ответа: \"").append(taskTitle).append("\".\n\n");
        sb.append("Дай 3–8 коротких практичных пунктов только по текущей задаче на русском: критерии выбора, на что смотреть, порядок действий. ");
        sb.append("Согласуй тон с доской и сценарием, но не включай советы по другим товарам/пунктам из родителей. ");
        sb.append("Не пиши процесс размышления — только итоговая памятка.");
        return sb.toString();
    }

    private LmStudioException mapRestException(RestClientException e) {
        if (e instanceof ResourceAccessException) {
            Throwable cause = e.getCause();
            if (cause instanceof SocketTimeoutException) {
                return new LmStudioException(
                        "Таймаут ответа LM Studio. Увеличьте lmstudio.timeout-seconds или проверьте нагрузку на модель.",
                        HttpStatus.GATEWAY_TIMEOUT,
                        e);
            }
            if (cause instanceof ConnectException) {
                return new LmStudioException(
                        "Не удалось подключиться к LM Studio. Запущен ли Local Server и верный ли lmstudio.base-url?",
                        HttpStatus.SERVICE_UNAVAILABLE,
                        e);
            }
            return new LmStudioException(
                    "LM Studio недоступна (сетевая ошибка). " + e.getMessage(),
                    HttpStatus.SERVICE_UNAVAILABLE,
                    e);
        }
        if (e instanceof RestClientResponseException rre) {
            return new LmStudioException(describeLmStudioHttpError(rre), HttpStatus.BAD_GATEWAY, e);
        }
        return new LmStudioException(
                "LM Studio недоступна или вернула ошибку. " + e.getMessage(),
                HttpStatus.BAD_GATEWAY,
                e);
    }

    /**
     * LM Studio / OpenAI-compatible API often returns JSON {@code {"error":{"message":"..."}}}.
     */
    private String describeLmStudioHttpError(RestClientResponseException rre) {
        int code = rre.getStatusCode().value();
        String reason = rre.getStatusText();
        String body = rre.getResponseBodyAsString(StandardCharsets.UTF_8);
        String parsed = tryParseUpstreamErrorMessage(body);
        StringBuilder sb = new StringBuilder("LM Studio вернула HTTP ");
        sb.append(code);
        if (reason != null && !reason.isBlank()) {
            sb.append(" ").append(reason.trim());
        }
        if (parsed != null && !parsed.isBlank()) {
            sb.append(": ").append(parsed.trim());
        } else if (body != null && !body.isBlank() && body.length() <= 800) {
            sb.append(": ").append(body.trim());
        }
        return sb.toString();
    }

    private String tryParseUpstreamErrorMessage(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode error = root.get("error");
            if (error == null || error.isNull()) {
                return null;
            }
            if (error.isTextual()) {
                return error.asText();
            }
            JsonNode message = error.get("message");
            if (message != null && message.isTextual()) {
                return message.asText();
            }
        } catch (Exception ignored) {
            // fall through
        }
        return null;
    }

    private static String buildUserMessage(String taskTitle) {
        return "Задача: \"" + taskTitle + "\".\n\n"
                + "Дай только краткий практичный чеклист или шаги по-русски. "
                + "Не пиши процесс размышления, не дублируй формулировку задачи списком покупок — только что делать дальше.";
    }

    private String parseAssistantContent(String responseJson) {
        if (responseJson == null || responseJson.isBlank()) {
            throw new LmStudioException("Пустой ответ от LM Studio.", HttpStatus.BAD_GATEWAY, null);
        }
        int maxChars = Math.max(1024, props.getMaxResponseChars());
        if (responseJson.length() > maxChars) {
            throw new LmStudioException(
                    "Ответ LM Studio слишком большой (" + responseJson.length() + " символов).",
                    HttpStatus.BAD_GATEWAY,
                    null);
        }
        try {
            JsonNode tree = objectMapper.readTree(responseJson);
            JsonNode choice0 = tree.path("choices").path(0);
            if (choice0.isMissingNode() || choice0.isNull()) {
                throw new LmStudioException("В ответе LM Studio нет choices[0].", HttpStatus.BAD_GATEWAY, null);
            }
            JsonNode message = choice0.path("message");
            String text = extractAssistantText(message);
            if (!text.isEmpty()) {
                return text;
            }
            // legacy non-chat completion shape
            JsonNode legacyText = choice0.path("text");
            if (legacyText.isTextual() && !legacyText.asText("").isBlank()) {
                return legacyText.asText("").trim();
            }
            throw emptyAssistantResponse(choice0);
        } catch (LmStudioException ex) {
            throw ex;
        } catch (Exception e) {
            throw new LmStudioException("Не удалось разобрать ответ LM Studio.", HttpStatus.BAD_GATEWAY, e);
        }
    }

    /**
     * OpenAI chat: {@code content} is usually a string; some servers use an array of parts
     * {@code [{"type":"text","text":"..."}]}. Thinking-style models may put text in {@code reasoning_content}.
     */
    private static String extractAssistantText(JsonNode messageNode) {
        if (messageNode == null || messageNode.isMissingNode() || messageNode.isNull()) {
            return "";
        }
        String fromContent = extractFromContentNode(messageNode.path("content"));
        if (!fromContent.isBlank()) {
            return fromContent.trim();
        }
        for (String field : new String[] {"reasoning_content", "reasoning"}) {
            JsonNode n = messageNode.get(field);
            if (n != null && n.isTextual()) {
                String t = n.asText("").trim();
                if (!t.isEmpty()) {
                    return t;
                }
            }
        }
        return "";
    }

    private static String extractFromContentNode(JsonNode content) {
        if (content == null || content.isMissingNode() || content.isNull()) {
            return "";
        }
        if (content.isTextual()) {
            return content.asText("");
        }
        if (content.isArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode part : content) {
                if (part == null || part.isNull()) {
                    continue;
                }
                if (part.hasNonNull("text")) {
                    sb.append(part.get("text").asText(""));
                } else if (part.isTextual()) {
                    sb.append(part.asText(""));
                }
            }
            return sb.toString();
        }
        return "";
    }

    private static LmStudioException emptyAssistantResponse(JsonNode choice0) {
        String finish = choice0.path("finish_reason").asText("");
        StringBuilder detail = new StringBuilder(
                "Модель вернула пустой текст (нет content / reasoning). Откройте в LM Studio лог запроса chat/completions и посмотрите сырой JSON.");
        if ("length".equals(finish)) {
            detail.append(" finish_reason=length — ответ обрезан лимитом токенов; увеличьте lmstudio.max-tokens.");
        } else if (!finish.isBlank()) {
            detail.append(" finish_reason=").append(finish).append('.');
        } else {
            detail.append(" Попробуйте другую модель или снизьте temperature в LM Studio.");
        }
        return new LmStudioException(detail.toString(), HttpStatus.BAD_GATEWAY, null);
    }

    /**
     * Thinking / reasoning models often still emit structured sections. Keep only the user-facing block.
     */
    private String sanitizeThinkingModelOutput(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String t = scrubBrokenChannelFragments(raw).trim();

        int refine = lastIndexOfIgnoreCase(t, "## 🚀 Refine");
        if (refine < 0) {
            refine = lastIndexOfIgnoreCase(t, "## 🚀");
        }
        if (refine >= 0) {
            return trimOrphanOpenParen(dropHeadingAndMetaIntro(t.substring(refine)));
        }

        int shop = lastIndexOfIgnoreCase(t, "### 🛒");
        if (shop >= 0) {
            return trimOrphanOpenParen(t.substring(shop).trim());
        }

        int draft = lastIndexOfIgnoreCase(t, "## ✅ Draft");
        if (draft >= 0) {
            return trimOrphanOpenParen(dropHeadingAndMetaIntro(t.substring(draft)));
        }

        int hr = t.lastIndexOf("\n---\n");
        if (hr > 80 && hr < t.length() - 40) {
            String tail = t.substring(hr + 5).trim();
            String head = tail.length() > 20 ? tail.substring(0, Math.min(80, tail.length())) : tail;
            if (!startsWithIgnoreCase(head.replace("#", "").trim(), "## 🧠")) {
                return trimOrphanOpenParen(tail);
            }
        }

        return trimOrphanOpenParen(t);
    }

    /**
     * Models often emit LaTeX-style arrows; replace with Unicode so the UI shows arrows without MathJax.
     */
    private static String normalizeAdviceFormatting(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        String t = text;
        t = t.replace("$\\rightarrow$", "→");
        t = t.replace("$\\Rightarrow$", "⇒");
        t = t.replace("$\\leftarrow$", "←");
        t = t.replace("$\\to$", "→");
        t = t.replace("$\\implies$", "⇒");
        return t;
    }

    private static String scrubBrokenChannelFragments(String s) {
        String out = s;
        int safety = 0;
        while (safety++ < 20) {
            int ch = out.indexOf("<channel");
            if (ch < 0) {
                break;
            }
            int gt = out.indexOf('>', ch);
            if (gt > ch) {
                out = out.substring(0, ch) + out.substring(gt + 1);
            } else {
                out = out.substring(0, ch);
                break;
            }
        }
        return out.replace("<channel|", "");
    }

    private static String dropHeadingAndMetaIntro(String section) {
        int nl = section.indexOf('\n');
        if (nl < 0) {
            return "";
        }
        String rest = section.substring(nl + 1).trim();
        rest = stripLeadingParenMetaLines(rest);
        return rest;
    }

    /**
     * Drops lines like "(Используем … тона.)" sometimes glued to "**Заголовок".
     */
    private static String stripLeadingParenMetaLines(String rest) {
        String t = rest.trim();
        int guard = 0;
        while (guard++ < 5 && !t.isEmpty()) {
            if (t.startsWith("(")) {
                int rp = t.indexOf(')');
                if (rp > 0 && rp < 400) {
                    t = t.substring(rp + 1).trim();
                    continue;
                }
            }
            int star = t.indexOf("**");
            if (star >= 0 && star < 6) {
                break;
            }
            int nl = t.indexOf('\n');
            if (nl > 0 && t.substring(0, nl).length() < 220
                    && (t.substring(0, nl).contains("Используем") || t.substring(0, nl).contains("дружелюб"))) {
                t = t.substring(nl + 1).trim();
                continue;
            }
            break;
        }
        return t;
    }

    private static String trimOrphanOpenParen(String s) {
        if (s == null || s.isBlank()) {
            return "";
        }
        String t = s.trim();
        int open = 0;
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c == '(') {
                open++;
            } else if (c == ')') {
                open--;
            }
        }
        if (open > 0) {
            int lastNl = t.lastIndexOf('\n');
            if (lastNl > t.length() / 3) {
                String last = t.substring(lastNl + 1);
                if (last.contains("(") && !last.contains(")")) {
                    t = t.substring(0, lastNl).trim();
                }
            }
        }
        return t;
    }

    private static int lastIndexOfIgnoreCase(String haystack, String needle) {
        return haystack.toLowerCase(Locale.ROOT).lastIndexOf(needle.toLowerCase(Locale.ROOT));
    }

    private static boolean startsWithIgnoreCase(String str, String prefix) {
        return str.length() >= prefix.length()
                && str.substring(0, Math.min(str.length(), prefix.length()))
                        .toLowerCase(Locale.ROOT)
                        .startsWith(prefix.toLowerCase(Locale.ROOT));
    }
}
