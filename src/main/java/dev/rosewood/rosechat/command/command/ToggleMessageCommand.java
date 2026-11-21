package dev.rosewood.rosechat.command.command;

import dev.rosewood.rosechat.chat.DmPrivacy;
import dev.rosewood.rosechat.chat.PlayerData;
import dev.rosewood.rosechat.command.RoseChatCommand;
import dev.rosewood.rosechat.message.RosePlayer;
import dev.rosewood.rosegarden.RosePlugin;
import dev.rosewood.rosegarden.command.framework.Argument;
import dev.rosewood.rosegarden.command.framework.ArgumentHandler;
import dev.rosewood.rosegarden.command.framework.ArgumentsDefinition;
import dev.rosewood.rosegarden.command.framework.CommandContext;
import dev.rosewood.rosegarden.command.framework.CommandInfo;
import dev.rosewood.rosegarden.command.framework.InputIterator;
import dev.rosewood.rosegarden.command.framework.annotation.RoseExecutable;
import java.util.List;
import java.util.Locale;

public class ToggleMessageCommand extends RoseChatCommand {

    public ToggleMessageCommand(RosePlugin rosePlugin) {
        super(rosePlugin);
    }

    @Override
    protected CommandInfo createCommandInfo() {
        return CommandInfo.builder("togglemessage")
                .aliases("togglemessages", "togglepm", "togglepms", "togglemsg", "togglemsgs")
                .descriptionKey("command-togglemessage-description")
                .permission("rosechat.togglemessage")
                .playerOnly(true)
                .arguments(ArgumentsDefinition.builder()
                        .optional("mode", new ArgumentHandler<String>(String.class) {
                            @Override
                            public String handle(CommandContext context, Argument argument, InputIterator inputIterator) {
                                return inputIterator.next();
                            }

                            @Override
                            public List<String> suggest(CommandContext context, Argument argument, String[] args) {
                                return List.of("everyone", "friends", "nobody");
                            }
                        })
                        .build())
                .build();
    }

    @Override
    protected boolean hasPriority() {
        return true;
    }

    @RoseExecutable
    public void execute(CommandContext context) {
        RosePlayer player = new RosePlayer(context.getSender());
        PlayerData data = player.getPlayerData();
        if (data == null)
            return;

        DmPrivacy next = switch (data.getDmPrivacy()) {
            case EVERYONE -> DmPrivacy.FRIENDS;
            case FRIENDS -> DmPrivacy.NOBODY;
            case NOBODY -> DmPrivacy.EVERYONE;
        };

        this.applyPrivacy(player, data, next);
    }

    @RoseExecutable
    public void execute(CommandContext context, String rawMode) {
        RosePlayer player = new RosePlayer(context.getSender());
        PlayerData data = player.getPlayerData();
        if (data == null)
            return;

        DmPrivacy next = switch (rawMode.trim().toLowerCase(Locale.ROOT)) {
            case "everyone", "all", "on", "true" -> DmPrivacy.EVERYONE;
            case "friends", "friend" -> DmPrivacy.FRIENDS;
            case "nobody", "none", "off", "false" -> DmPrivacy.NOBODY;
            default -> {
                player.sendLocaleMessage("command-togglemessage-invalid-mode");
                yield null;
            }
        };

        if (next != null)
            this.applyPrivacy(player, data, next);
    }

    private void applyPrivacy(RosePlayer player, PlayerData data, DmPrivacy mode) {
        data.setDmPrivacy(mode);
        data.save();

        switch (mode) {
            case EVERYONE -> player.sendLocaleMessage("command-togglemessage-enabled");
            case FRIENDS -> player.sendLocaleMessage("command-togglemessage-friends");
            case NOBODY -> player.sendLocaleMessage("command-togglemessage-disabled");
        }
    }

}
