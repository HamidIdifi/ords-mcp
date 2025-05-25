package dev.oracle.ords;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class Application {

    private static final Logger log = LoggerFactory.getLogger(Application.class);
    private static final PresentationTools presentationTools = new PresentationTools();

    public static void main(String[] args) {

        // Stdio Server Transport (Support for SSE also available)
        var transportProvider = new StdioServerTransportProvider(new ObjectMapper());
        // Sync tool specification
        var syncToolSpecification = getSyncToolSpecifications();
        // Create a server with custom configuration
        McpSyncServer syncServer = McpServer.sync(transportProvider)
                .serverInfo("javaone-mcp-server", "0.0.1")
                .capabilities(McpSchema.ServerCapabilities.builder()
                        .tools(true)
                        .logging()
                        .build())
                // Register tools, resources, and prompts
                .tools(syncToolSpecification)
                .build();

        log.info("Starting JavaOne MCP Server...");
        log.info("running ords....");
    }

    private static McpServerFeatures.SyncToolSpecification[] getSyncToolSpecifications() {
        var getAllSchema = """
                {
                  "type" : "object",
                  "id" : "urn:jsonschema:GetAllPresentations",
                  "properties" : {
                    "operation" : {
                      "type" : "string"
                    }
                  }
                }
                """;

        var getByYearSchema = """
                {
                  "type": "object",
                  "id": "urn:jsonschema:GetPresentationsByYear",
                  "properties": {
                    "year": {
                      "type": "integer"
                    }
                  },
                  "required": ["year"]
                }
                """;

        var getAllTool = new McpServerFeatures.SyncToolSpecification(
                new McpSchema.Tool("get_presentations", "Get a list of all presentations from JavaOne", getAllSchema),
                (exchange, arguments) -> {
                    List<Presentation> presentations = presentationTools.getPresentations();
                    List<McpSchema.Content> contents = new ArrayList<>();
                    for (Presentation presentation : presentations) {
                        contents.add(new McpSchema.TextContent(presentation.toString()));
                    }
                    return new McpSchema.CallToolResult(contents, false);
                }
        );

        var getByYearTool = new McpServerFeatures.SyncToolSpecification(
                new McpSchema.Tool("get_presentations_by_year", "Get presentations from JavaOne for a given year", getByYearSchema),
                (exchange, arguments) -> {
                    int year = (int) arguments.get("year");
                    log.info("Received request for presentations in year: {}", year);
                    List<Presentation> presentations = presentationTools.getPresentationsByYear(year);
                    log.info("Found {} presentations for year {}", presentations.size(), year);
                    List<McpSchema.Content> contents = new ArrayList<>();
                    for (Presentation presentation : presentations) {
                        log.debug("Presentation: {}", presentation);
                        contents.add(new McpSchema.TextContent(presentation.toString()));
                    }
                    return new McpSchema.CallToolResult(contents, false);
                }
        );
        var executeSqlQueryTool = executeSqlQueryTool();

        return new McpServerFeatures.SyncToolSpecification[]{getAllTool, getByYearTool, executeSqlQueryTool};
    }

    private static McpServerFeatures.SyncToolSpecification executeSqlQueryTool() {
        var executeSqlQuerySchema = """
                {
                  "type": "object",
                  "id": "urn:jsonschema:ExecuteSqlQuery",
                  "properties": {
                    "sql": {
                      "type": "string"
                    }
                  },
                  "required": ["sql"]
                }
                """;
        var executeSqlQueryTool = new McpServerFeatures.SyncToolSpecification(
                new McpSchema.Tool("execute_sql_query", "Execute a SQL query via ORDS", executeSqlQuerySchema),
                (exchange, arguments) -> {
                    String sql = (String) arguments.get("sql");
                    SqlQueryRunnerTool executor = new SqlQueryRunnerTool();
                    List<McpSchema.Content> contents = new ArrayList<>();
                    try {
                        String result = executor.executeSqlQuery(sql);
                        contents.add(new McpSchema.TextContent(result));
                    } catch (Exception e) {
                        contents.add(new McpSchema.TextContent("Error executing SQL: " + e.getMessage()));
                    }
                    return new McpSchema.CallToolResult(contents, false);
                }
        );
        return executeSqlQueryTool;
    }


}
