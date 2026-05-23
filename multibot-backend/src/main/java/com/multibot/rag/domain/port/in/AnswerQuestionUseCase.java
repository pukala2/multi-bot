package com.multibot.rag.domain.port.in;

import com.multibot.rag.domain.model.RagAnswer;
import com.multibot.rag.domain.model.RagQuery;

public interface AnswerQuestionUseCase {

    RagAnswer answer(RagQuery query);
}
