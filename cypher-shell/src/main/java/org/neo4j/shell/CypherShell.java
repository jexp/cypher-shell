package org.neo4j.shell;

import org.neo4j.shell.commands.Command;
import org.neo4j.shell.commands.CommandExecutable;
import org.neo4j.shell.commands.CommandHelper;
import org.neo4j.shell.exception.CommandException;
import org.neo4j.shell.exception.ExitException;
import org.neo4j.shell.log.Logger;
import org.neo4j.shell.prettyprint.CypherVariablesFormatter;
import org.neo4j.shell.prettyprint.PrettyPrinter;
import org.neo4j.shell.state.BoltResult;
import org.neo4j.shell.state.BoltStateHandler;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A possibly interactive shell for evaluating cypher statements.
 */
public class CypherShell implements StatementExecuter, Connector, TransactionHandler, VariableHolder {
    // Final space to catch newline
    protected static final Pattern cmdNamePattern = Pattern.compile("^\\s*(?<name>[^\\s]+)\\b(?<args>.*)\\s*$");
    protected final Map<String, Object> queryParams = new HashMap<>();
    private final Logger logger;
    private final BoltStateHandler boltStateHandler;
    private final PrettyPrinter prettyPrinter;
    protected CommandHelper commandHelper;

    public CypherShell(@Nonnull Logger logger) {
        this(logger, new BoltStateHandler(), new PrettyPrinter(logger.getFormat(), logger.getWidth(), logger.getWrap()));
    }

    protected CypherShell(@Nonnull Logger logger,
                          @Nonnull BoltStateHandler boltStateHandler,
                          @Nonnull PrettyPrinter prettyPrinter) {
        this.logger = logger;
        this.boltStateHandler = boltStateHandler;
        this.prettyPrinter = prettyPrinter;
        addRuntimeHookToResetShell();
    }

    /**
     * @param text to trim
     * @return text without trailing semicolons
     */
    static String stripTrailingSemicolons(@Nonnull String text) {
        int end = text.length();
        while (end > 0 && text.substring(0, end).endsWith(";")) {
            end -= 1;
        }
        return text.substring(0, end);
    }

    @Override
    public void execute(@Nonnull final String cmdString) throws ExitException, CommandException {
        // See if it's a shell command
        final Optional<CommandExecutable> cmd = getCommandExecutable(cmdString);
        if (cmd.isPresent()) {
            executeCmd(cmd.get());
            return;
        }

        // Else it will be parsed as Cypher, but for that we need to be connected
        if (!isConnected()) {
            throw new CommandException("Not connected to Neo4j");
        }

        executeCypher(cmdString);
    }

    /**
     * Executes a piece of text as if it were Cypher. By default, all of the cypher is executed in single statement
     * (with an implicit transaction).
     *
     * @param cypher non-empty cypher text to executeLine
     */
    protected void executeCypher(@Nonnull final String cypher) throws CommandException {
        final Optional<BoltResult> result = boltStateHandler.runCypher(cypher, queryParams);
        result.ifPresent(boltResult -> prettyPrinter.format(boltResult, printer()));
    }

    private Consumer<String> printer() {
        return (text) -> {if (text!=null && !text.trim().isEmpty()) logger.printOut(text);};
    }

    @Override
    public boolean isConnected() {
        return boltStateHandler.isConnected();
    }

    @Nonnull
    protected Optional<CommandExecutable> getCommandExecutable(@Nonnull final String line) {
        Matcher m = cmdNamePattern.matcher(line);
        if (commandHelper == null || !m.matches()) {
            return Optional.empty();
        }

        String name = m.group("name");
        String args = m.group("args");

        Command cmd = commandHelper.getCommand(name);

        if (cmd == null) {
            return Optional.empty();
        }

        return Optional.of(() -> cmd.execute(stripTrailingSemicolons(args)));
    }

    protected void executeCmd(@Nonnull final CommandExecutable cmdExe) throws ExitException, CommandException {
        cmdExe.execute();
    }

    /**
     * Open a session to Neo4j
     *
     * @param connectionConfig
     */
    @Override
    public void connect(@Nonnull ConnectionConfig connectionConfig) throws CommandException {
        boltStateHandler.connect(connectionConfig);
    }

    @Nonnull
    @Override
    public String getServerVersion() {
        return boltStateHandler.getServerVersion();
    }

    @Override
    public void beginTransaction() throws CommandException {
        boltStateHandler.beginTransaction();
    }

    @Override
    public Optional<List<BoltResult>> commitTransaction() throws CommandException {
        Optional<List<BoltResult>> results = boltStateHandler.commitTransaction();
        results.ifPresent(boltResult -> boltResult.forEach(result -> prettyPrinter.format(result, printer())));
        return results;
    }

    @Override
    public void rollbackTransaction() throws CommandException {
        boltStateHandler.rollbackTransaction();
    }

    @Override
    public boolean isTransactionOpen() {
        return boltStateHandler.isTransactionOpen();
    }

    @Override
    @Nonnull
    public Optional set(@Nonnull String name, @Nonnull String valueString) throws CommandException {
        final BoltResult result = setParamsAndValidate(name, valueString);
        String parameterName = CypherVariablesFormatter.unescapedCypherVariable(name);
        final Object value = result.getRecords().get(0).get(parameterName).asObject();
        queryParams.put(parameterName, value);
        return Optional.ofNullable(value);
    }

    private BoltResult setParamsAndValidate(@Nonnull String name, @Nonnull String valueString) throws CommandException {
        String cypher = "RETURN " + valueString + " as " + name;
        final Optional<BoltResult> result = boltStateHandler.runCypher(cypher, queryParams);
        if (!result.isPresent() || result.get().getRecords().isEmpty()) {
            throw new CommandException("Failed to set value of parameter");
        }
        return result.get();
    }

    @Override
    @Nonnull
    public Map<String, Object> getAll() {
        return queryParams;
    }

    public void setCommandHelper(@Nonnull CommandHelper commandHelper) {
        this.commandHelper = commandHelper;
    }

    @Override
    public void reset() {
        boltStateHandler.reset();
    }

    protected void addRuntimeHookToResetShell() {
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                reset();
            }
        });
    }

}
