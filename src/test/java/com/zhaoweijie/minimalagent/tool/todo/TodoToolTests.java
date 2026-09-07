package com.zhaoweijie.minimalagent.tool.todo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zhaoweijie.minimalagent.tool.ToolExecutionContext;
import com.zhaoweijie.minimalagent.tool.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class TodoToolTests {

    /** 用于构造参数并序列化 TodoItem 的 Jackson 对象映射器。 */
    private ObjectMapper objectMapper;

    /** 被测试的待办工具。 */
    private TodoTool todoTool;

    /** 默认测试用户的第一个 Session。 */
    private ToolExecutionContext firstSession;

    @BeforeEach
    void setUp() {
        objectMapper = JsonMapper.builder().findAndAddModules().build();
        todoTool = new TodoTool(new TodoRepository(), objectMapper);
        firstSession = new ToolExecutionContext("user-1", "session-1");
    }

    @Test
    void supportsCompleteTodoLifecycle() {
        ToolResult added = todoTool.execute(firstSession, arguments("add", "write tests", null));
        String todoId = added.data().path("todo").path("id").textValue();

        assertThat(added.success()).isTrue();
        assertThat(added.data().path("todo").path("status").textValue()).isEqualTo("PENDING");

        ToolResult listed = todoTool.execute(firstSession, arguments("list", null, null));
        assertThat(listed.success()).isTrue();
        assertThat(listed.data().path("todos")).hasSize(1);
        assertThat(listed.data().path("todos").get(0).path("id").textValue()).isEqualTo(todoId);

        ToolResult completed = todoTool.execute(
                firstSession,
                arguments("complete", null, todoId)
        );
        assertThat(completed.success()).isTrue();
        assertThat(completed.data().path("todo").path("status").textValue())
                .isEqualTo("COMPLETED");
        assertThat(completed.data().path("todo").hasNonNull("completedAt")).isTrue();

        ToolResult deleted = todoTool.execute(firstSession, arguments("delete", null, todoId));
        assertThat(deleted.success()).isTrue();
        assertThat(deleted.data().path("deleted").booleanValue()).isTrue();

        ToolResult emptyList = todoTool.execute(firstSession, arguments("list", null, null));
        assertThat(emptyList.data().path("todos")).isEmpty();
    }

    @Test
    void isolatesTodosBetweenSessionsOfSameUser() {
        ToolExecutionContext secondSession = new ToolExecutionContext("user-1", "session-2");
        ToolResult added = todoTool.execute(firstSession, arguments("add", "private task", null));
        String todoId = added.data().path("todo").path("id").textValue();

        ToolResult secondSessionList = todoTool.execute(
                secondSession,
                arguments("list", null, null)
        );
        ToolResult crossSessionComplete = todoTool.execute(
                secondSession,
                arguments("complete", null, todoId)
        );
        ToolResult crossSessionDelete = todoTool.execute(
                secondSession,
                arguments("delete", null, todoId)
        );

        assertThat(secondSessionList.data().path("todos")).isEmpty();
        assertThat(crossSessionComplete.success()).isFalse();
        assertThat(crossSessionDelete.success()).isFalse();

        ToolResult firstSessionList = todoTool.execute(firstSession, arguments("list", null, null));
        assertThat(firstSessionList.data().path("todos")).hasSize(1);
        assertThat(firstSessionList.data().path("todos").get(0).path("status").textValue())
                .isEqualTo("PENDING");
    }

    @Test
    void repositorySupportsConcurrentAdds() {
        TodoRepository repository = new TodoRepository();

        Set<String> ids = IntStream.range(0, 100)
                .parallel()
                .mapToObj(index -> repository.add("user-1", "session-1", "todo-" + index).id())
                .collect(Collectors.toSet());

        assertThat(ids).hasSize(100);
        assertThat(repository.list("user-1", "session-1")).hasSize(100);
    }

    @Test
    void rejectsMissingAction() {
        ToolResult result = todoTool.execute(firstSession, objectMapper.createObjectNode());

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isEqualTo("Missing required parameter: action");
    }

    @Test
    void exposesTodoActionSchema() {
        var schema = todoTool.parameterSchema();

        assertThat(schema.path("type").textValue()).isEqualTo("object");
        assertThat(schema.path("properties").path("action").path("enum"))
                .extracting(node -> node.textValue())
                .containsExactly("add", "list", "complete", "delete");
        assertThat(schema.path("properties").path("content").path("type").textValue())
                .isEqualTo("string");
        assertThat(schema.path("properties").path("todoId").path("type").textValue())
                .isEqualTo("string");
        assertThat(schema.path("required").get(0).textValue()).isEqualTo("action");
    }

    /**
     * 创建 TodoTool 动作参数，仅写入当前动作需要的可选字段。
     */
    private ObjectNode arguments(String action, String content, String todoId) {
        ObjectNode arguments = objectMapper.createObjectNode().put("action", action);
        if (content != null) {
            arguments.put("content", content);
        }
        if (todoId != null) {
            arguments.put("todoId", todoId);
        }
        return arguments;
    }
}
