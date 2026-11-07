package dev.rosewood.rosechat.command.argument;

import dev.rosewood.rosechat.api.RoseChatAPI;
import dev.rosewood.rosechat.hook.channel.rosechat.GroupChannel;
import dev.rosewood.rosegarden.command.framework.Argument;
import dev.rosewood.rosegarden.command.framework.ArgumentHandler;
import dev.rosewood.rosegarden.command.framework.CommandContext;
import dev.rosewood.rosegarden.command.framework.InputIterator;
import java.util.List;

public class OfflineGroupArgumentHandler extends ArgumentHandler<String> {

    public OfflineGroupArgumentHandler() {
        super(String.class);
    }

    @Override
    public String handle(CommandContext context, Argument argument, InputIterator inputIterator) throws HandledArgumentException {
        String input = inputIterator.next();
        GroupChannel loaded = RoseChatAPI.getInstance().getGroupChatById(input);
        if (loaded != null)
            return loaded.getId();

        for (String existingId : RoseChatAPI.getInstance().getGroupChatIDs()) {
            if (existingId.equalsIgnoreCase(input))
                return existingId;
        }

        throw new HandledArgumentException("argument-handler-group");
    }

    @Override
    public List<String> suggest(CommandContext context, Argument argument, String[] args) {
        return RoseChatAPI.getInstance().getGroupChatIDs();
    }

}
