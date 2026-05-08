package com.PrimeCare.PrimeCare.modules.publiclookup.service;

import com.PrimeCare.PrimeCare.modules.publiclookup.dto.response.PublicAssistantActionResponse;

import java.util.List;

enum AssistantIntent {
    BOOKING,
    RESULT_LOOKUP,
    PREPARATION,
    SPECIALTY_GUIDANCE,
    URGENT,
    FAQ
}

record AssistantKnowledge(List<String> featuredServices,
                          List<String> specialties,
                          List<String> branches,
                          List<KnowledgeSnippet> snippets) {
}

record KnowledgeSnippet(String id,
                        String kind,
                        String title,
                        String content,
                        AssistantIntent intentHint,
                        String routeHint,
                        List<String> tags) {
}

record KnowledgeMatch(KnowledgeSnippet snippet, int score) {
}

record FallbackAnswer(String answer,
                      String caution,
                      List<PublicAssistantActionResponse> actions,
                      List<String> suggestions) {
}
