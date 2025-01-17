package de.benjaminaaron.ontoengine.suggestion;

import de.benjaminaaron.ontoengine.model.ModelController;
import de.benjaminaaron.ontoengine.model.Utils;
import de.benjaminaaron.ontoengine.routing.websocket.WebSocketRouting;
import de.benjaminaaron.ontoengine.routing.websocket.messages.WhileTypingSuggestionsMessage;
import de.benjaminaaron.ontoengine.suggestion.job.NewStatementJob;
import de.benjaminaaron.ontoengine.suggestion.job.PeriodicJob;
import de.benjaminaaron.ontoengine.suggestion.job.task.CaseSensitivityTask;
import de.benjaminaaron.ontoengine.suggestion.job.task.LocalVocabularyMatchingTask;
import de.benjaminaaron.ontoengine.suggestion.job.task.PeriodicQueryTask;
import de.benjaminaaron.ontoengine.suggestion.job.task.SynonymsTask;
import de.benjaminaaron.ontoengine.suggestion.job.task.WikidataMatchingTask;
import lombok.SneakyThrows;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static de.benjaminaaron.ontoengine.model.Utils.ResourceType.PREDICATE;

@Component
// @EnableAsync
public class SuggestionEngine {

    private final Logger logger = LogManager.getLogger(SuggestionEngine.class);
    private final Map<String, Suggestion> suggestions = new HashMap<>();

    @Autowired
    private WebSocketRouting router;

    @Autowired
    private ModelController modelController;

    private TaskSchedulingManager taskManager;
    private LocalVocabularyManager localVocabularyManager;

    private final boolean isON = true;

    @SneakyThrows
    @PostConstruct
    void init() {
        if (!isON) {
            return;
        }
        taskManager = new TaskSchedulingManager(this);
        localVocabularyManager = new LocalVocabularyManager(modelController.getVocabularySourcesModel());
        taskManager.schedulePeriodicJob("runPeriodicJob", 5, 30);
    }

    public void runPeriodicJob() {
        PeriodicJob job = new PeriodicJob(modelController);
        // job.addTask(new CaseSensitivityTask());
        // job.addTask(new GraphSimilarityTask());
        // job.addTask(new PropertyChainsTask());
        job.addTask(new PeriodicQueryTask(modelController.getMetaModel()));
        job.addTask(new SynonymsTask());
        handleNewSuggestions(job.execute());
    }

    public void runNewStatementJob(Statement statement) {
        NewStatementJob job = new NewStatementJob(statement);
        job.addTask(new LocalVocabularyMatchingTask(localVocabularyManager));
        job.addTask(new WikidataMatchingTask());
        CaseSensitivityTask caseSensitivityTask = new CaseSensitivityTask();
        caseSensitivityTask.setMainModel(modelController.getMainModel());
        job.addTask(caseSensitivityTask);
        job.getFuture().whenComplete((_suggestions, ex) -> handleNewSuggestions(_suggestions));
        taskManager.scheduleOneTimeJobNow(job);
    }

    public void handleNewSuggestions(List<Suggestion> _suggestions) {
        _suggestions.forEach(this::registerSuggestionIfNew);
        suggestions.values().stream().filter(s -> !s.getIsSent()).forEach(suggestion -> {
            router.sendSuggestion(suggestion.getMessage());
            modelController.broadcastToChangeListeners(suggestion.getMessage().toBasicString());
            suggestion.markAsSent();
        });
    }

    private void registerSuggestionIfNew(Suggestion suggestion) {
        if (suggestions.containsValue(suggestion)) {
            // more refined logic required to decide when a new suggestion should replace an old one TODO
            return;
        }
        String id = Utils.generateRandomId();
        suggestion.setId(id);
        suggestions.put(id, suggestion);
    }

    public boolean suggestionExists(String id) {
        return suggestions.containsKey(id);
    }

    public String accept(String id) {
        return suggestions.get(id).getMessage().getAchievingCommand();
    }

    public void generateWhileTypingSuggestions(WhileTypingSuggestionsMessage message) {
        String value = message.getValue().toLowerCase();
        Map<String, String> matches = new HashMap<>();
        StmtIterator iter = modelController.getMainModel().listStatements();
        while (iter.hasNext()) {
            Statement statement = iter.nextStatement();
            if (PREDICATE == message.getResourceType()) {
                checkMatch(value, statement.getPredicate(), matches);
                continue;
            }
            checkMatch(value, statement.getSubject(), matches);
            if (statement.getObject().isResource()) {
                checkMatch(value, statement.getObject().asResource(), matches);
            }
        }
        message.setMatches(matches);
    }

    private void checkMatch(String value, Resource resource, Map<String, String> matches) {
        String word = resource.getLocalName();
        if (word.toLowerCase().contains(value)) {
            matches.put(word, resource.getURI());
        }
    }
}
