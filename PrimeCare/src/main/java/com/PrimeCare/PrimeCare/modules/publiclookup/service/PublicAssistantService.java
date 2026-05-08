package com.PrimeCare.PrimeCare.modules.publiclookup.service;

import com.PrimeCare.PrimeCare.modules.ai.support.OpenAiCompatibleClient;
import com.PrimeCare.PrimeCare.modules.publiclookup.dto.request.PublicAssistantMessageRequest;
import com.PrimeCare.PrimeCare.modules.publiclookup.dto.request.PublicAssistantRequest;
import com.PrimeCare.PrimeCare.modules.publiclookup.dto.response.PublicAssistantActionResponse;
import com.PrimeCare.PrimeCare.modules.publiclookup.dto.response.PublicAssistantResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static com.PrimeCare.PrimeCare.modules.publiclookup.service.PublicAssistantText.blankToNull;
import static com.PrimeCare.PrimeCare.modules.publiclookup.service.PublicAssistantText.containsAny;
import static com.PrimeCare.PrimeCare.modules.publiclookup.service.PublicAssistantText.joinOrFallback;
import static com.PrimeCare.PrimeCare.modules.publiclookup.service.PublicAssistantText.searchable;
import static com.PrimeCare.PrimeCare.modules.publiclookup.service.PublicAssistantText.tokenize;
import static com.PrimeCare.PrimeCare.modules.publiclookup.service.PublicAssistantText.trimAndTruncate;
import static com.PrimeCare.PrimeCare.modules.publiclookup.service.PublicAssistantText.truncate;

@Service
@RequiredArgsConstructor
public class PublicAssistantService {

    private static final int MAX_HISTORY_MESSAGES = 8;
    private static final int MAX_QUESTION_CHARS = 1000;
    private static final int MAX_HISTORY_MESSAGE_CHARS = 1000;
    private static final int MAX_PAGE_PATH_CHARS = 256;
    private static final int MAX_PAGE_TITLE_CHARS = 200;
    private static final int MAX_GROUNDING_SNIPPETS = 6;
    private static final int MAX_SNIPPET_CHARS = 320;

    private final OpenAiCompatibleClient openAiCompatibleClient;
    private final PublicAssistantKnowledgeProvider knowledgeProvider;
    private final PublicAssistantIntentDetector intentDetector;

    @Transactional(readOnly = true)
    public PublicAssistantResponse ask(PublicAssistantRequest request) {
        String question = trimAndTruncate(request == null ? null : request.getQuestion(), MAX_QUESTION_CHARS, "");
        String locale = normalizeLocale(request == null ? null : request.getLocale(), question);
        boolean english = locale.startsWith("en");
        String pagePath = normalizePagePath(request == null ? null : request.getPagePath());
        String pageTitle = trimAndTruncate(request == null ? null : request.getPageTitle(), MAX_PAGE_TITLE_CHARS, null);

        AssistantKnowledge knowledge = knowledgeProvider.loadKnowledge(english);
        AssistantIntent intent = intentDetector.detect(question, pagePath);
        List<KnowledgeMatch> groundingMatches = selectGroundingMatches(question, intent, pagePath, knowledge, english);
        FallbackAnswer fallback = buildFallbackAnswer(question, intent, knowledge, groundingMatches, english, pagePath, pageTitle);

        String aiAnswer = openAiCompatibleClient.generateChat(
                buildSystemPrompt(knowledge, groundingMatches, english),
                buildConversation(request == null ? null : request.getHistory(), question, english, pagePath, pageTitle, knowledge, intent, groundingMatches)
        ).map(this::postProcessAiAnswer).orElse(null);

        return PublicAssistantResponse.builder()
                .answer(aiAnswer != null && !aiAnswer.isBlank() ? aiAnswer : fallback.answer())
                .caution(fallback.caution())
                .provider(aiAnswer != null && !aiAnswer.isBlank() ? openAiCompatibleClient.providerLabel() : "GUIDED_ASSISTANT")
                .actions(fallback.actions())
                .suggestions(fallback.suggestions())
                .build();
    }

    private List<OpenAiCompatibleClient.ChatMessage> buildConversation(List<PublicAssistantMessageRequest> history,
                                                                       String question,
                                                                       boolean english,
                                                                       String pagePath,
                                                                       String pageTitle,
                                                                       AssistantKnowledge knowledge,
                                                                       AssistantIntent intent,
                                                                       List<KnowledgeMatch> groundingMatches) {
        List<OpenAiCompatibleClient.ChatMessage> messages = new ArrayList<>();

        if (history != null && !history.isEmpty()) {
            history.stream()
                    .filter(Objects::nonNull)
                    .filter(item -> blankToNull(item.getRole()) != null && blankToNull(item.getText()) != null)
                    .filter(item -> "user".equalsIgnoreCase(item.getRole()) || "assistant".equalsIgnoreCase(item.getRole()))
                    .skip(Math.max(0, history.size() - MAX_HISTORY_MESSAGES))
                    .forEach(item -> messages.add(new OpenAiCompatibleClient.ChatMessage(
                            item.getRole().trim().toLowerCase(Locale.ROOT),
                            trimAndTruncate(item.getText(), MAX_HISTORY_MESSAGE_CHARS, "")
                    )));
        }

        messages.add(new OpenAiCompatibleClient.ChatMessage(
                "user",
                buildContextualUserPrompt(question, english, pagePath, pageTitle, knowledge, intent, groundingMatches)
        ));
        return messages;
    }

    private FallbackAnswer buildFallbackAnswer(String question,
                                               AssistantIntent intent,
                                               AssistantKnowledge knowledge,
                                               List<KnowledgeMatch> groundingMatches,
                                               boolean english,
                                               String pagePath,
                                               String pageTitle) {
        List<PublicAssistantActionResponse> actions = buildActions(intent, groundingMatches, english, pagePath);
        List<String> suggestions = buildSuggestions(intent, groundingMatches, english, pagePath);
        String caution = buildCaution(intent, english, question);

        String groundedAnswer = groundedFallbackAnswer(intent, groundingMatches, english, pagePath, pageTitle);
        String answer = groundedAnswer != null ? groundedAnswer : defaultFallbackAnswer(intent, knowledge, english, pagePath, pageTitle);

        return new FallbackAnswer(answer, caution, actions, suggestions);
    }

    private String groundedFallbackAnswer(AssistantIntent intent,
                                          List<KnowledgeMatch> groundingMatches,
                                          boolean english,
                                          String pagePath,
                                          String pageTitle) {
        KnowledgeMatch topMatch = groundingMatches.isEmpty() ? null : groundingMatches.get(0);
        if (topMatch != null && topMatch.score() >= 16 && topMatch.snippet().content() != null) {
            return topMatch.snippet().content();
        }

        KnowledgeSnippet workflow = groundingMatches.stream()
                .map(KnowledgeMatch::snippet)
                .filter(snippet -> snippet.intentHint() == intent)
                .filter(snippet -> snippet.kind().startsWith("WORKFLOW"))
                .findFirst()
                .orElse(null);
        if (workflow != null) {
            return workflow.content();
        }

        if (intent == AssistantIntent.FAQ) {
            KnowledgeSnippet routeSnippet = groundingMatches.stream()
                    .map(KnowledgeMatch::snippet)
                    .filter(snippet -> pagePath.equals(snippet.routeHint()))
                    .findFirst()
                    .orElse(null);
            if (routeSnippet != null) {
                return routeSnippet.content();
            }
        }

        if (intent == AssistantIntent.SPECIALTY_GUIDANCE) {
            List<KnowledgeSnippet> specialtyMatches = groundingMatches.stream()
                    .map(KnowledgeMatch::snippet)
                    .filter(snippet -> "SPECIALTY".equals(snippet.kind()))
                    .limit(2)
                    .toList();
            if (!specialtyMatches.isEmpty()) {
                String connector = english ? "You can start with " : "Bạn có thể ưu tiên xem ";
                return connector + specialtyMatches.stream()
                        .map(KnowledgeSnippet::title)
                        .collect(Collectors.joining(english ? " or " : " hoặc "))
                        + (english
                        ? ", then open the doctor list or booking page to choose a suitable doctor."
                        : ", rồi vào danh sách bác sĩ hoặc trang đặt lịch để chọn bác sĩ phù hợp.");
            }
        }
        return null;
    }

    private String defaultFallbackAnswer(AssistantIntent intent,
                                         AssistantKnowledge knowledge,
                                         boolean english,
                                         String pagePath,
                                         String pageTitle) {
        return switch (intent) {
            case BOOKING -> english
                    ? "On the PrimeCare website, booking follows four steps: choose branch, specialty and doctor; choose date, session and an available slot; fill in patient information; then confirm and save the appointment code."
                    : "Trên website PrimeCare, đặt lịch đi theo 4 bước: chọn cơ sở, chuyên khoa và bác sĩ; chọn ngày khám, buổi khám và khung giờ còn trống; điền thông tin bệnh nhân; sau đó xác nhận và lưu mã lịch hẹn.";
            case RESULT_LOOKUP -> english
                    ? "To look up public results, go to the lookup page, enter the appointment or encounter code, request OTP to the registered email, verify the 6-digit OTP, then open the PDF after the doctor has completed the visit."
                    : "Để tra cứu kết quả công khai, bạn vào trang tra cứu, nhập mã lịch hẹn hoặc mã hồ sơ, gửi OTP về email đã đăng ký, xác thực OTP 6 số rồi mở PDF sau khi bác sĩ đã hoàn tất lần khám và các kết quả đã được xác minh.";
            case PREPARATION -> english
                    ? "Preparation depends on the service. Blood tests may require fasting, abdominal ultrasound may require drinking water, and endoscopy usually needs a dedicated checklist from PrimeCare before the appointment."
                    : "Chuẩn bị trước khám phụ thuộc từng dịch vụ. Xét nghiệm máu có thể cần nhịn ăn, siêu âm bụng có thể cần uống nước, còn nội soi thường cần checklist riêng từ PrimeCare trước buổi hẹn.";
            case SPECIALTY_GUIDANCE -> english
                    ? "You can choose the specialty that is closest to the main symptom, then review doctors and available slots before booking."
                    : "Bạn có thể chọn chuyên khoa gần nhất với triệu chứng chính, sau đó xem danh sách bác sĩ và lịch trống trước khi đặt lịch.";
            case FAQ -> buildFaqSummary(knowledge, pagePath, pageTitle, english);
            case URGENT -> english
                    ? "If symptoms sound urgent, please seek immediate in-person medical care or the nearest emergency department instead of relying on online guidance."
                    : "Nếu triệu chứng có vẻ khẩn cấp, bạn nên đến cơ sở y tế gần nhất hoặc cấp cứu ngay thay vì chỉ dựa vào hướng dẫn online.";
        };
    }

    private String buildFaqSummary(AssistantKnowledge knowledge, String pagePath, String pageTitle, boolean english) {
        String routeHint = english
                ? "You are currently on " + readablePageName(pagePath, pageTitle, true) + ". "
                : "Bạn đang ở " + readablePageName(pagePath, pageTitle, false) + ". ";
        String specialties = knowledge.specialties().isEmpty()
                ? (english ? "multiple specialties" : "nhiều chuyên khoa")
                : String.join(", ", knowledge.specialties().stream().limit(5).toList());
        String services = knowledge.featuredServices().isEmpty()
                ? (english ? "consultation and diagnostic services" : "khám và cận lâm sàng")
                : String.join(", ", knowledge.featuredServices().stream().limit(5).toList());
        return english
                ? routeHint + "PrimeCare currently highlights specialties such as " + specialties + ", with public services like " + services + ". I can guide you to booking, availability or secure lookup next."
                : routeHint + "PrimeCare hiện có các chuyên khoa nổi bật như " + specialties + ", cùng các dịch vụ công khai như " + services + ". Tôi có thể hướng dẫn bạn tiếp sang đặt lịch, xem lịch trống hoặc tra cứu an toàn.";
    }

    private List<PublicAssistantActionResponse> buildActions(AssistantIntent intent,
                                                             List<KnowledgeMatch> groundingMatches,
                                                             boolean english,
                                                             String pagePath) {
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<PublicAssistantActionResponse> actions = new ArrayList<>();

        boolean contactRelated = groundingMatches.stream()
                .map(KnowledgeMatch::snippet)
                .anyMatch(snippet -> "CONTACT".equals(snippet.kind()));

        switch (intent) {
            case BOOKING, SPECIALTY_GUIDANCE -> {
                addAction(actions, seen, action(english ? "Book appointment" : "Đặt lịch khám", "BOOK_APPOINTMENT", "/booking"));
                addAction(actions, seen, action(english ? "See availability" : "Xem lịch trống", "NAVIGATE", "/availability"));
                addAction(actions, seen, action(english ? "Find doctors" : "Xem bác sĩ", "NAVIGATE", "/doctors"));
            }
            case RESULT_LOOKUP -> {
                addAction(actions, seen, action(english ? "Lookup result" : "Tra cứu kết quả", "LOOKUP_RESULT", "/appointments/lookup"));
                addAction(actions, seen, action(english ? "Lookup appointment" : "Tra cứu phiếu hẹn", "LOOKUP_APPOINTMENT", "/appointments/lookup"));
            }
            case PREPARATION -> {
                addAction(actions, seen, action(english ? "View services" : "Xem dịch vụ", "NAVIGATE", "/medical-services"));
                addAction(actions, seen, action(english ? "Book appointment" : "Đặt lịch khám", "BOOK_APPOINTMENT", "/booking"));
            }
            case URGENT -> {
                addAction(actions, seen, action(english ? "Find branches" : "Xem cơ sở", "NAVIGATE", "/branches"));
                addAction(actions, seen, action(english ? "Contact PrimeCare" : "Liên hệ PrimeCare", "NAVIGATE", "/contact"));
            }
            case FAQ -> {
                if (contactRelated) {
                    addAction(actions, seen, action(english ? "Contact PrimeCare" : "Liên hệ PrimeCare", "NAVIGATE", "/contact"));
                }
                addAction(actions, seen, action(english ? "Book appointment" : "Đặt lịch khám", "BOOK_APPOINTMENT", "/booking"));
                addAction(actions, seen, action(english ? "View FAQ" : "Xem FAQ", "NAVIGATE", "/faq"));
                addAction(actions, seen, action(english ? "Lookup result" : "Tra cứu kết quả", "LOOKUP_RESULT", "/appointments/lookup"));
            }
        }

        if ("/booking".equals(pagePath)) {
            addAction(actions, seen, action(english ? "See availability" : "Xem lịch trống", "NAVIGATE", "/availability"));
        }
        return actions.stream().limit(3).toList();
    }

    private List<String> buildSuggestions(AssistantIntent intent,
                                          List<KnowledgeMatch> groundingMatches,
                                          boolean english,
                                          String pagePath) {
        List<String> suggestions = new ArrayList<>();
        boolean contactRelated = groundingMatches.stream()
                .map(KnowledgeMatch::snippet)
                .anyMatch(snippet -> "CONTACT".equals(snippet.kind()));

        switch (intent) {
            case BOOKING -> {
                suggestions.add(english ? "I want to see available slots first" : "Tôi muốn xem lịch trống trước");
                suggestions.add(english ? "What patient information do I need to enter?" : "Tôi cần nhập những thông tin bệnh nhân nào?");
                suggestions.add(english ? "Can I look up the appointment later?" : "Sau này tôi tra cứu phiếu hẹn thế nào?");
            }
            case RESULT_LOOKUP -> {
                suggestions.add(english ? "Why can't I see the result yet?" : "Vì sao tôi chưa xem được kết quả?");
                suggestions.add(english ? "How do I get the OTP?" : "Tôi lấy OTP như thế nào?");
                suggestions.add(english ? "Can I look up the appointment slip too?" : "Tôi có thể tra cứu luôn phiếu hẹn không?");
            }
            case PREPARATION -> {
                suggestions.add(english ? "Do blood tests require fasting?" : "Xét nghiệm máu có cần nhịn ăn không?");
                suggestions.add(english ? "How should I prepare for ultrasound?" : "Siêu âm cần chuẩn bị như thế nào?");
                suggestions.add(english ? "Where do I view services?" : "Tôi xem dịch vụ ở đâu?");
            }
            case SPECIALTY_GUIDANCE -> {
                suggestions.add(english ? "I have stomach pain, which specialty fits?" : "Tôi bị đau bụng thì nên đi khoa nào?");
                suggestions.add(english ? "Can you help me choose a doctor next?" : "Bạn gợi ý giúp tôi chọn bác sĩ tiếp theo được không?");
                suggestions.add(english ? "Where do I see available slots?" : "Tôi xem lịch trống ở đâu?");
            }
            case URGENT -> {
                suggestions.add(english ? "Where is the nearest branch?" : "Cơ sở gần nhất ở đâu?");
                suggestions.add(english ? "How can I contact PrimeCare quickly?" : "Tôi liên hệ PrimeCare nhanh bằng cách nào?");
            }
            case FAQ -> {
                if (contactRelated) {
                    suggestions.add(english ? "Show me the contact page" : "Mở giúp tôi trang liên hệ");
                }
                suggestions.add(english ? "How do I book an appointment?" : "Làm sao để đặt lịch khám?");
                suggestions.add(english ? "Can I reschedule the appointment?" : "Tôi có thể đổi lịch hẹn không?");
                suggestions.add(english ? "How do I look up the result PDF?" : "Tra cứu PDF kết quả như thế nào?");
            }
        }

        if ("/appointments/lookup".equals(pagePath)) {
            suggestions.add(english ? "What does OTP verification mean?" : "Xác thực OTP nghĩa là gì?");
        }

        return suggestions.stream().distinct().limit(3).toList();
    }

    private String buildCaution(AssistantIntent intent, boolean english, String question) {
        String searchableQuestion = searchable(question);
        if (intent == AssistantIntent.URGENT) {
            return english
                    ? "This assistant cannot assess emergencies. Please seek in-person medical care immediately."
                    : "Trợ lý này không thể đánh giá cấp cứu. Bạn nên đi khám trực tiếp ngay khi có dấu hiệu khẩn cấp.";
        }
        if (containsAny(searchableQuestion, "mang thai", "pregnan", "tre so sinh", "newborn", "dau nguc", "chest pain")) {
            return english
                    ? "For high-risk symptoms or vulnerable groups, please follow direct clinician guidance instead of relying only on the chatbot."
                    : "Với triệu chứng nguy cơ cao hoặc nhóm bệnh nhân đặc biệt, bạn nên ưu tiên hướng dẫn trực tiếp từ nhân viên y tế thay vì chỉ dựa vào chatbot.";
        }
        return english
                ? "PrimeCare AI supports booking guidance and public information only. Final diagnosis and treatment decisions must come from a clinician."
                : "PrimeCare AI chỉ hỗ trợ hướng dẫn đặt lịch và thông tin công khai. Chẩn đoán và quyết định điều trị cuối cùng phải do nhân viên y tế phụ trách.";
    }

    private String buildSystemPrompt(AssistantKnowledge knowledge,
                                     List<KnowledgeMatch> groundingMatches,
                                     boolean english) {
        String languageInstruction = english
                ? "Reply in natural, concise English."
                : "Tra loi bang tieng Viet tu nhien, gon, de hieu.";
        String groundingRules = english
                ? "You must ground every operational answer in the provided PrimeCare workflow facts and knowledge snippets. Do not invent phone numbers, prices, service preparation rules, doctor details, turnaround times or website steps that are not supported by the snippets. When the user asks how to do something on the website, answer with the exact web flow in numbered steps. If the snippets are insufficient, say what the current website publicly supports and guide the user to booking, availability, lookup or contact."
                : "Ban phai bam sat cac thong tin workflow va knowledge snippets duoc cung cap. Khong duoc tu y bịa so dien thoai, gia tien, huong dan chuan bi, thong tin bac si, thoi gian tra ket qua hoac cac buoc thao tac neu snippets khong ho tro. Khi nguoi dung hoi cach thao tac tren website, hay tra loi bang cac buoc cu the dung flow web. Neu snippets chua du, hay noi ro website hien ho tro gi cong khai va huong dan sang dat lich, xem lich trong, tra cuu hoac lien he.";
        String matchedTopics = groundingMatches.isEmpty()
                ? (english ? "No strongly matched snippets yet." : "Chua co snippet khop manh.")
                : groundingMatches.stream().map(match -> match.snippet().title()).distinct().limit(4).collect(Collectors.joining(", "));
        return "You are PrimeCare AI, a modern public-facing hospital website assistant. "
                + languageInstruction
                + " Your main jobs are booking flow guidance, specialty guidance, branch/service discovery, visit preparation, OTP flow and public result lookup. "
                + groundingRules
                + " Stay operational and helpful, but do not provide final diagnosis, prescriptions, or treatment plans. "
                + "If the situation sounds urgent, instruct the user to seek immediate in-person medical care. "
                + "Public result lookup only works after the doctor has concluded and completed the encounter. "
                + "Known public branches: " + joinOrFallback(knowledge.branches(), english ? "PrimeCare branches" : "cac co so PrimeCare") + ". "
                + "Known public specialties: " + joinOrFallback(knowledge.specialties(), english ? "multiple specialties" : "nhieu chuyen khoa") + ". "
                + "Featured public services: " + joinOrFallback(knowledge.featuredServices(), english ? "consultation and diagnostic services" : "kham va can lam sang") + ". "
                + (english ? "Strongly matched snippet topics: " : "Cac snippet khop manh: ")
                + matchedTopics + ".";
    }

    private String buildContextualUserPrompt(String question,
                                             boolean english,
                                             String pagePath,
                                             String pageTitle,
                                             AssistantKnowledge knowledge,
                                             AssistantIntent intent,
                                             List<KnowledgeMatch> groundingMatches) {
        StringBuilder builder = new StringBuilder();
        builder.append(english ? "Current page: " : "Trang hien tai: ")
                .append(readablePageName(pagePath, pageTitle, english))
                .append('\n');
        builder.append(english ? "Detected intent: " : "Nhom nhu cau: ")
                .append(intent.name())
                .append('\n');
        builder.append(english ? "Public branches: " : "Cac co so cong khai: ")
                .append(joinOrFallback(knowledge.branches(), english ? "Available on website" : "Dang hien tren website"))
                .append('\n');
        builder.append(english ? "Public specialties: " : "Cac chuyen khoa cong khai: ")
                .append(joinOrFallback(knowledge.specialties(), english ? "Available specialties" : "Cac chuyen khoa hien co"))
                .append('\n');
        builder.append(english ? "Featured services: " : "Dich vu noi bat: ")
                .append(joinOrFallback(knowledge.featuredServices(), english ? "Featured services" : "Dich vu noi bat"))
                .append('\n');
        builder.append(english ? "Grounding snippets:" : "Grounding snippets:")
                .append('\n');
        if (groundingMatches.isEmpty()) {
            builder.append(english ? "- No strong match found. Use the public workflow facts above only.\n" : "- Chua co match manh. Chi duoc dua vao workflow cong khai o tren.\n");
        } else {
            int index = 1;
            for (KnowledgeMatch match : groundingMatches) {
                builder.append(index++)
                        .append(". [")
                        .append(match.snippet().kind())
                        .append("] ")
                        .append(match.snippet().title())
                        .append(": ")
                        .append(truncate(match.snippet().content(), MAX_SNIPPET_CHARS))
                        .append('\n');
            }
        }
        builder.append(english ? "User question: " : "Cau hoi nguoi dung: ")
                .append(question)
                .append('\n');
        builder.append(english
                ? "Answer naturally, keep it aligned with the actual PrimeCare website flow, and prefer the strongest snippets over generic wording."
                : "Tra loi tu nhien, bam sat dung flow website PrimeCare, uu tien snippets khop manh hon la noi chung chung.");
        return builder.toString();
    }

    private List<KnowledgeMatch> selectGroundingMatches(String question,
                                                        AssistantIntent intent,
                                                        String pagePath,
                                                        AssistantKnowledge knowledge,
                                                        boolean english) {
        String searchableQuestion = searchable(question);
        Set<String> tokens = tokenize(question + " " + pagePath + " " + intent.name());

        List<KnowledgeMatch> matches = knowledge.snippets().stream()
                .map(snippet -> new KnowledgeMatch(snippet, scoreSnippet(snippet, searchableQuestion, tokens, intent, pagePath)))
                .filter(match -> match.score() > 0)
                .sorted(Comparator.comparingInt(KnowledgeMatch::score).reversed())
                .limit(MAX_GROUNDING_SNIPPETS)
                .collect(Collectors.toCollection(ArrayList::new));

        maybeAddWorkflowSnippet(matches, knowledge.snippets(), intent, pagePath);
        if (matches.isEmpty()) {
            maybeAddDefaultPageSnippet(matches, knowledge.snippets(), pagePath);
            maybeAddWorkflowSnippet(matches, knowledge.snippets(), intent, pagePath);
        }

        return matches.stream()
                .distinct()
                .limit(MAX_GROUNDING_SNIPPETS)
                .toList();
    }

    private int scoreSnippet(KnowledgeSnippet snippet,
                             String searchableQuestion,
                             Set<String> tokens,
                             AssistantIntent intent,
                             String pagePath) {
        int score = 0;
        String title = searchable(snippet.title());
        String content = searchable(snippet.content());
        String tags = searchable(String.join(" ", snippet.tags()));

        if (snippet.intentHint() == intent) {
            score += 10;
        }
        if (pagePath.equals(snippet.routeHint())) {
            score += 8;
        }
        if (snippet.kind().startsWith("WORKFLOW")) {
            score += 2;
        }

        for (String token : tokens) {
            if (token.isBlank()) {
                continue;
            }
            if (title.contains(token)) {
                score += 5;
            }
            if (tags.contains(token)) {
                score += 4;
            }
            if (content.contains(token)) {
                score += 2;
            }
        }

        if (!title.isBlank() && searchableQuestion.contains(title)) {
            score += 8;
        }
        if (containsAny(searchableQuestion, "otp") && content.contains("otp")) {
            score += 4;
        }
        if (containsAny(searchableQuestion, "gia", "chi phi", "price") && content.contains("gia")) {
            score += 3;
        }
        if (containsAny(searchableQuestion, "hotline", "lien he", "contact") && "CONTACT".equals(snippet.kind())) {
            score += 8;
        }
        if (containsAny(searchableQuestion, "huy lich", "doi lich", "reschedule", "cancel") && "FAQ".equals(snippet.kind())) {
            score += 5;
        }
        return score;
    }

    private void maybeAddWorkflowSnippet(List<KnowledgeMatch> matches,
                                         List<KnowledgeSnippet> snippets,
                                         AssistantIntent intent,
                                         String pagePath) {
        boolean alreadyHasIntentWorkflow = matches.stream()
                .map(KnowledgeMatch::snippet)
                .anyMatch(snippet -> snippet.intentHint() == intent && snippet.kind().startsWith("WORKFLOW"));
        if (alreadyHasIntentWorkflow) {
            return;
        }
        snippets.stream()
                .filter(snippet -> snippet.intentHint() == intent && snippet.kind().startsWith("WORKFLOW"))
                .findFirst()
                .ifPresent(snippet -> matches.add(new KnowledgeMatch(snippet, 12)));
        if (matches.size() > MAX_GROUNDING_SNIPPETS) {
            matches.remove(matches.size() - 1);
        }
    }

    private void maybeAddDefaultPageSnippet(List<KnowledgeMatch> matches,
                                            List<KnowledgeSnippet> snippets,
                                            String pagePath) {
        snippets.stream()
                .filter(snippet -> pagePath.equals(snippet.routeHint()))
                .findFirst()
                .ifPresent(snippet -> matches.add(new KnowledgeMatch(snippet, 10)));
    }

    private String readablePageName(String pagePath, String pageTitle, boolean english) {
        if (pageTitle != null && !pageTitle.isBlank()) {
            return pageTitle.trim();
        }
        return switch (pagePath) {
            case "/booking" -> english ? "the booking page" : "trang đặt lịch";
            case "/availability" -> english ? "the availability page" : "trang lịch trống";
            case "/appointments/lookup" -> english ? "the lookup page" : "trang tra cứu";
            case "/specialties" -> english ? "the specialties page" : "trang chuyên khoa";
            case "/doctors" -> english ? "the doctors page" : "trang bác sĩ";
            case "/branches" -> english ? "the branches page" : "trang cơ sở";
            case "/medical-services" -> english ? "the medical services page" : "trang dịch vụ";
            case "/faq" -> english ? "the FAQ page" : "trang FAQ";
            case "/contact" -> english ? "the contact page" : "trang liên hệ";
            default -> english ? "the PrimeCare website" : "website PrimeCare";
        };
    }

    private void addAction(List<PublicAssistantActionResponse> actions,
                           Set<String> seen,
                           PublicAssistantActionResponse action) {
        String key = action.getType() + "::" + action.getValue();
        if (seen.add(key)) {
            actions.add(action);
        }
    }

    private PublicAssistantActionResponse action(String label, String type, String value) {
        return PublicAssistantActionResponse.builder()
                .label(label)
                .type(type)
                .value(value)
                .build();
    }

    private String normalizeLocale(String locale, String question) {
        String normalized = locale == null ? "" : locale.trim().toLowerCase(Locale.ROOT);
        if (!normalized.isBlank()) {
            return normalized;
        }
        return intentDetector.looksEnglish(question) ? "en" : "vi";
    }

    private String normalizePagePath(String pagePath) {
        if (pagePath == null || pagePath.isBlank()) {
            return "/";
        }
        String trimmed = pagePath.trim();
        int queryIndex = trimmed.indexOf('?');
        String pathOnly = queryIndex >= 0 ? trimmed.substring(0, queryIndex) : trimmed;
        return truncate(pathOnly, MAX_PAGE_PATH_CHARS);
    }

    private String postProcessAiAnswer(String answer) {
        if (answer == null) {
            return null;
        }
        String cleaned = answer.trim();
        if (cleaned.length() > 1800) {
            cleaned = cleaned.substring(0, 1800).trim();
        }
        return cleaned.replaceAll("\\n{3,}", "\n\n");
    }

}
