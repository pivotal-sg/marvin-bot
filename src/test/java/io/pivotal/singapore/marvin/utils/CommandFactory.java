package io.pivotal.singapore.marvin.utils;

import io.pivotal.singapore.marvin.commands.SubCommand;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.ArrayList;
import java.util.List;

public class CommandFactory {

    public static SubCommand createSubCommand(String name) {
        SubCommand subCommand = new SubCommand();
        subCommand.setName(name);
        subCommand.setMethod(RequestMethod.POST);
        subCommand.setEndpoint("http://example.com/hello");
        subCommand.setDefaultResponseFailure("Shucks... something went wrong.");
        subCommand.setDefaultResponseSuccess("w00t!");
        return subCommand;
    }

    public static List<SubCommand> createSubCommands(){
        return new ArrayList<SubCommand>() {{
            add(createSubCommand("in"));
        }};
    }
}
