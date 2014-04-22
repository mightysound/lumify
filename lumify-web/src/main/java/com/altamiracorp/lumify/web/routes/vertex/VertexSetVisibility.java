package com.altamiracorp.lumify.web.routes.vertex;

import com.altamiracorp.lumify.core.config.Configuration;
import com.altamiracorp.lumify.core.model.user.UserRepository;
import com.altamiracorp.lumify.core.model.workQueue.WorkQueueRepository;
import com.altamiracorp.lumify.core.security.LumifyVisibilityProperties;
import com.altamiracorp.lumify.core.security.VisibilityTranslator;
import com.altamiracorp.lumify.core.user.User;
import com.altamiracorp.lumify.core.util.GraphUtil;
import com.altamiracorp.lumify.core.util.JsonSerializer;
import com.altamiracorp.lumify.core.util.LumifyLogger;
import com.altamiracorp.lumify.core.util.LumifyLoggerFactory;
import com.altamiracorp.lumify.web.BaseRequestHandler;
import com.altamiracorp.miniweb.HandlerChain;
import com.altamiracorp.securegraph.Authorizations;
import com.altamiracorp.securegraph.Graph;
import com.altamiracorp.securegraph.Vertex;
import com.altamiracorp.securegraph.Visibility;
import com.google.inject.Inject;
import org.json.JSONObject;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class VertexSetVisibility extends BaseRequestHandler {
    private static final LumifyLogger LOGGER = LumifyLoggerFactory.getLogger(VertexSetVisibility.class);
    private final Graph graph;
    private final VisibilityTranslator visibilityTranslator;
    private final WorkQueueRepository workQueueRepository;

    @Inject
    public VertexSetVisibility(
            final Graph graph,
            final UserRepository userRepository,
            final Configuration configuration,
            final VisibilityTranslator visibilityTranslator,
            final WorkQueueRepository workQueueRepository) {
        super(userRepository, configuration);
        this.graph = graph;
        this.visibilityTranslator = visibilityTranslator;
        this.workQueueRepository = workQueueRepository;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, HandlerChain chain) throws Exception {
        final String graphVertexId = getAttributeString(request, "graphVertexId");
        final String visibilitySource = getRequiredParameter(request, "visibilitySource");

        User user = getUser(request);
        Authorizations authorizations = getAuthorizations(request, user);
        String workspaceId = getActiveWorkspaceId(request);

        Vertex graphVertex = graph.getVertex(graphVertexId, authorizations);
        if (graphVertex == null) {
            respondWithNotFound(response);
            return;
        }

        if (!graph.isVisibilityValid(new Visibility(visibilitySource), authorizations)) {
            LOGGER.warn("%s is not a valid visibility for %s user", visibilitySource, user.getDisplayName());
            respondWithBadRequest(response, "visibilitySource", STRINGS.getString("visibility.invalid"));
            chain.next(request, response);
            return;
        }
        LOGGER.info("changing vertex (%s) visibility source to %s", graphVertex.getId().toString(), visibilitySource);

        GraphUtil.updateElementVisibilitySource(visibilityTranslator, graphVertex, GraphUtil.getSandboxStatus(graphVertex, workspaceId), visibilitySource, workspaceId);

        this.graph.flush();

        this.workQueueRepository.pushGraphPropertyQueue(graphVertex, null, LumifyVisibilityProperties.VISIBILITY_JSON_PROPERTY.getKey());

        JSONObject json = JsonSerializer.toJson(graphVertex, workspaceId);
        respondWithJson(response, json);
    }
}
