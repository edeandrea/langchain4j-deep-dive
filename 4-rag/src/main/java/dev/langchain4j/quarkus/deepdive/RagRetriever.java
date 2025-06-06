package dev.langchain4j.quarkus.deepdive;

import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.scoring.ScoringModel;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.aggregator.ContentAggregator;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.content.retriever.WebSearchContentRetriever;
import dev.langchain4j.rag.query.router.LanguageModelQueryRouter;
import dev.langchain4j.rag.query.router.QueryRouter;
import dev.langchain4j.rag.query.transformer.CompressingQueryTransformer;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.web.search.WebSearchEngine;

public class RagRetriever {

    @Produces
    @ApplicationScoped
    public RetrievalAugmentor create(
                  EmbeddingStore store,
                  EmbeddingModel model,
                  ChatModel chatLanguageModel,
                  WebSearchEngine webSearchEngine,
                  ScoringModel scoringModel) {

        var embeddingStoreContentRetriever = EmbeddingStoreContentRetriever.builder()
                .embeddingModel(model)
                .embeddingStore(store)
                .maxResults(3)
                .build();

        var webSearchContentRetriever = WebSearchContentRetriever.builder()
          .webSearchEngine(webSearchEngine)
          .build();

        return DefaultRetrievalAugmentor.builder()
                .queryRouter(getQueryRouter(chatLanguageModel, embeddingStoreContentRetriever, webSearchContentRetriever))
                .queryTransformer(new CompressingQueryTransformer(chatLanguageModel))
                .contentAggregator(getContentAggregator(scoringModel))
                .contentInjector((list, chatMessage) -> {
                    var prompt = new StringBuilder((chatMessage instanceof UserMessage um) ? um.singleText() : "");
                    prompt.append("\n\nPlease, only use the following information:\n\n");

                    list.stream()
                      .map(content -> "%s\n\n".formatted(content.textSegment().text()))
                      .forEach(prompt::append);

                    return new UserMessage(prompt.toString());
                })
                .build();
    }

    private QueryRouter getQueryRouter(ChatModel chatLanguageModel, ContentRetriever embeddingStoreContentRetriever, ContentRetriever webSearchContentRetriever) {
      return LanguageModelQueryRouter.builder()
                                     .chatModel(chatLanguageModel)
                                     .fallbackStrategy(LanguageModelQueryRouter.FallbackStrategy.ROUTE_TO_ALL)
                                     .retrieverToDescription(
            Map.of(
              embeddingStoreContentRetriever, "Local documents",
              webSearchContentRetriever, "Search of quarkus.io"
            )
          )
                                     .build();

//      return new DefaultQueryRouter(embeddingStoreContentRetriever, webSearchContentRetriever);
//      return new DefaultQueryRouter(embeddingStoreContentRetriever);
    }

    private ContentAggregator getContentAggregator(ScoringModel scoringModel) {
      return null;

//      return ReRankingContentAggregator.builder()
//          .scoringModel(scoringModel)
//          .querySelector(ReRankingContentAggregator.DEFAULT_QUERY_SELECTOR)
//          .minScore(0.6)
//          .build();
//
//      return new CustomReRankingContentAggregator(scoringModel, 0.6);
    }
}
