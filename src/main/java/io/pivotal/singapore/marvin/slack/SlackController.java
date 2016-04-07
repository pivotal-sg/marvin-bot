package io.pivotal.singapore.marvin.slack;

import com.google.common.collect.ImmutableMap;
import io.pivotal.singapore.marvin.commands.Command;
import io.pivotal.singapore.marvin.commands.CommandRepository;
import io.pivotal.singapore.marvin.commands.ICommand;
import io.pivotal.singapore.marvin.commands.arguments.Argument;
import io.pivotal.singapore.marvin.commands.arguments.ArgumentParseException;
import io.pivotal.singapore.marvin.core.CommandParserService;
import io.pivotal.singapore.marvin.core.MessageType;
import io.pivotal.singapore.marvin.core.RemoteApiService;
import io.pivotal.singapore.marvin.core.RemoteApiServiceResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.composed.web.rest.json.GetJson;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
class SlackController {
    @Autowired
    CommandRepository commandRepository;

    @Autowired
    RemoteApiService remoteApiService;

    @Autowired
    CommandParserService commandParserService;

    @Value("${api.slack.token}")
    private String SLACK_TOKEN;

    private Clock clock = Clock.systemUTC();

    @GetJson("/")
    Map<String, String> index(@RequestParam HashMap<String, String> params) throws Exception {

        // Figuring out if request is valid (ie. from Slack)
        String token = params.get("token");
        if (token == null || !token.equals(SLACK_TOKEN)) {
            throw new UnrecognizedApiToken();
        }

        // Validates that 'text' is there
        String commandText = params.get("text");
        if (commandText == null || commandText.isEmpty()) {
            return defaultResponse();
        }

        // Parses into command, sub-command, args as token strings
        HashMap<String, String> parsedCommand = commandParserService.parse(commandText);

        // Checks if Command exists
        Optional<Command> commandOptional = getCommand(parsedCommand.get("command"));
        if (!commandOptional.isPresent()) {
            return defaultResponse();
        }

        // Makes remote API calls
        RemoteApiServiceResponse response;
        Optional<ICommand> subCommandOptional = commandOptional.get().findSubCommand(parsedCommand.get("sub_command"));

        if( !subCommandOptional.isPresent() && !commandOptional.get().getSubCommands().isEmpty()) {
            return defaultResponse(String.format("This sub command doesn't exist for %s", parsedCommand.get("command")));
        }

        Map _params = remoteServiceParams(params);

        // FIXME: Only fallback to command if there are no subcommands
        ICommand cmd = subCommandOptional.orElse(commandOptional.get());

        try {
            Map args = cmd.getArguments().parse(parsedCommand.get("arguments"));
            _params.putAll(args);
        } catch (ArgumentParseException ex) {
            Argument argument = ((Argument) ex.getThrower());
            String text = String.format("`%s` is not found in your command.", argument.getName());

            return ImmutableMap.of("text", text, "response_type", getSlackResponseType(MessageType.user));
        }

        response = remoteApiService.call(cmd, _params);

        // Compiles final response to Slack
        return textResponse(response.getMessageType(), response.getMessage());
    }

    private HashMap<String, Object> remoteServiceParams(HashMap<String, String> params) {
        HashMap<String, Object> serviceParams = new HashMap<>();
        serviceParams.put("username", String.format("%s@pivotal.io", params.get("user_name")));
        serviceParams.put("channel", params.get("channel_name"));
        serviceParams.put("received_at", ZonedDateTime.now(clock).format(DateTimeFormatter.ISO_ZONED_DATE_TIME));
        serviceParams.put("command", params.get("text"));

        return serviceParams;
    }

    private Optional<Command> getCommand(String commandName) {
        return commandRepository.findOneByName(commandName);
    }

    HashMap<String, String> textResponse(Optional<MessageType> messageType, String text) {
        String responseType = getSlackResponseType(messageType.orElse(MessageType.user));

        HashMap<String, String> response = new HashMap<>();
        response.put("response_type", responseType);
        response.put("text", text);

        return response;
    }

    private String getSlackResponseType(MessageType messageType) {
        switch (messageType) {
            case user:
                return "ephemeral";
            case channel:
                return "in_channel";
            default:
                throw new IllegalArgumentException(
                    String.format("MessageType '%s' is not configured for Slack", messageType.toString())
                );
        }
    }

    private HashMap<String, String> defaultResponse() {
        return defaultResponse("This will all end in tears.");
    }

    private HashMap<String, String> defaultResponse(String message) {
        return textResponse(Optional.of(MessageType.user), message);
    }
}

@ResponseStatus(value = HttpStatus.BAD_REQUEST, reason = "Unrecognized token")
class UnrecognizedApiToken extends Exception {
}