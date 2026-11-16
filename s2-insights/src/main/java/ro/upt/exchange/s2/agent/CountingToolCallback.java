package ro.upt.exchange.s2.agent;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** Wraps an MCP tool so the run cannot exceed the tool-call budget, and remembers whether it was called. */
final class CountingToolCallback implements ToolCallback {

    private final ToolCallback delegate;
    private final AtomicInteger counter;
    private final int max;
    private volatile boolean called;

    CountingToolCallback(ToolCallback delegate, AtomicInteger counter, int max) {
        this.delegate = delegate;
        this.counter = counter;
        this.max = max;
    }

    static boolean wasCalled(List<ToolCallback> tools, String name) {
        return tools.stream().anyMatch(t -> t instanceof CountingToolCallback c
                && c.called && c.getToolDefinition().name().equals(name));
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    @Override
    public String call(String toolInput) {
        return call(toolInput, null);
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        if (counter.incrementAndGet() > max) {
            return "{\"error\":\"tool call budget of " + max + " exhausted; finish the report now\"}";
        }
        called = true;
        return toolContext == null ? delegate.call(toolInput) : delegate.call(toolInput, toolContext);
    }
}
